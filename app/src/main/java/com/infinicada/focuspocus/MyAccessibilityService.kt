package com.infinicada.focuspocus

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.BlockDebounce
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.model.Schedule
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TRANSITION_SLACK_MS = 250L
        private const val DEBOUNCE_RECHECK_SLACK_MS = 50L
        private const val PRODUCTIVE_CREDIT_EVERY_TICKS = 5
        private val TRANSITION_KEYS = SessionTransitions.WATCHED_KEYS + setOf(
            Constants.PrefsKeys.PACT_ALLOWANCES
        )
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var receiverRegistered = false

    private var packageReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                invalidatePackageCaches()
                // Fresh installs (not updates) get added to opted-in blocklists
                if (intent?.action == Intent.ACTION_PACKAGE_ADDED &&
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) {
                    intent.data?.schemeSpecificPart?.let { autoAddNewAppToBlockers(it) }
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error handling package change", e)
            }
        }
    }

    /**
     * Adds a newly installed package to every blacklist enchantment that has opted in
     * via [Blocker.autoAddNewApps] — closing the loophole of installing a fresh
     * distraction mid-session.
     */
    private fun autoAddNewAppToBlockers(newPackageName: String) {
        if (newPackageName == packageName) return
        val blockers = BlockerRepository.getBlockers(sharedPreferences)
        val updated = blockers.map { blocker ->
            if (blocker.autoAddNewApps && blocker.mode == BlockerMode.BLACKLIST &&
                newPackageName !in blocker.effectiveApps &&
                blocker.effectiveApps.size < Constants.MAX_APPS_PER_BLOCKER
            ) {
                blocker.copy(apps = blocker.effectiveApps + newPackageName)
            } else blocker
        }
        if (updated != blockers) {
            sharedPreferences.edit { putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(updated)) }
            Log.d("MyAccessibilityService", "Auto-added new app to opted-in enchantments")
        }
    }

    private fun invalidatePackageCaches() {
        launcherCacheResolved = false
        cachedLauncherPackageName = null
        cachedInputMethodPackageNames = null
        whitelistBlockableCache.clear()
        DeviceOwnerManager.invalidatePackageCaches()
    }

    // Cache for parsed schedules to avoid re-parsing JSON every minute
    @Volatile private var cachedSchedulesJson: String? = null
    @Volatile private var cachedSchedules: List<Schedule> = emptyList()

    // Debounce for app blocking, per package — Android fires multiple window-state
    // events when one app opens, but blocking one app must not suppress checks for
    // a different app opened right after.
    private val lastAppBlockTimes = HashMap<String, Long>()

    // Most recently focused "real" app (updated on every window state change, before debounce)
    @Volatile private var currentForegroundPackage: String? = null

    // An event from an app inside its debounce window is not judged, but it may
    // be the user getting straight back into the app. This judges the app once
    // the window has passed, if it is still in front — otherwise a quick reopen
    // stayed open until the next timed check, up to a minute later.
    private var debounceRecheckPackage: String? = null
    private val debounceRecheckRunnable = Runnable {
        val packageName = debounceRecheckPackage ?: return@Runnable
        debounceRecheckPackage = null
        try {
            val now = System.currentTimeMillis()
            val lastBlock = lastAppBlockTimes[packageName] ?: return@Runnable
            // Blocked again in the meantime: that block has its own window.
            if (BlockDebounce.isRecent(lastBlock, now)) return@Runnable
            // The cheap half of the rule first, so the usage query only runs
            // when the window events still say the app is in front.
            if (currentForegroundPackage != packageName) return@Runnable
            val stillInFront = BlockDebounce.isStillInFront(
                packageName,
                trackedForeground = currentForegroundPackage,
                lastResumedPackage = UsageStatsHelper.lastResumedPackageSince(this, lastBlock)
            )
            if (stillInFront) blockIfDue(packageName, now)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error rechecking after debounce", e)
            reportNonFatal(e)
        }
    }

    private fun scheduleDebounceRecheck(packageName: String, delayMillis: Long) {
        transitionHandler.removeCallbacks(debounceRecheckRunnable)
        debounceRecheckPackage = packageName
        transitionHandler.postDelayed(debounceRecheckRunnable, delayMillis + DEBOUNCE_RECHECK_SLACK_MS)
    }

    // Pact manager — tracks running pacts (Well apps opened for a while)
    private lateinit var pactManager: PactManager

    // Pact expiry warnings already toasted, keyed by package -> allowance expiry.
    // In-memory only; a duplicate toast after a service restart is harmless.
    private val pactWarnedExpiries = HashMap<String, Long>()

    // Open/reflex counter shown on the pact overlay
    private lateinit var openReflexTracker: OpenReflexTracker

    // Which tracked-or-untracked app the open/reflex bookkeeping considers foreground,
    // and since when. Separate from currentForegroundPackage, which the launcher,
    // keyboard and system UI deliberately leave alone.
    @Volatile private var openTrackingPackage: String? = null
    @Volatile private var openTrackingSince: Long = 0L

    // Minute ticks since the service started, for the productive-app credit
    private var productiveTicks = 0

    // "yyyyMMdd" of the last minute tick, to clear the day's pact warnings at midnight
    @Volatile private var lastTickDay: String? = null

    // Block event recording
    private val pendingBlockEvents = mutableListOf<BlockEvent>()
    @Volatile private var lastBlockEventWriteTime: Long = 0

    // Cache of the persisted block-event list so each flush doesn't re-parse a
    // potentially 1000-entry JSON array on the service's main thread.
    @Volatile private var cachedBlockEventsJson: String? = null
    @Volatile private var cachedBlockEvents: List<BlockEvent> = emptyList()

    // Settings packages to block in NFC lock mode
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.samsung.android.permissioncontroller"
    )

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK -> try {
                    onMinuteTick()
                } catch (e: Exception) {
                    // The minute tick must never take the service down — blocking,
                    // schedule enforcement, and limits all depend on it staying alive.
                    Log.e("MyAccessibilityService", "Error in minute tick", e)
                    reportNonFatal(e)
                }
                Intent.ACTION_SCREEN_OFF -> try {
                    currentForegroundPackage = null
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error handling screen off", e)
                    reportNonFatal(e)
                }
            }
        }
    }

    // One-shot wakeup for the next time-driven state change — a break ending,
    // a timed session ending, an auto-break starting, a pact closing — so
    // each lands on time instead of on the following
    // minute tick. The notification, the dashboard and Warden greying all
    // follow the state written here, and the app in front is closed here if
    // the change took its access away. The minute tick remains the backstop.
    private val transitionHandler = Handler(Looper.getMainLooper())
    private val transitionRunnable = Runnable {
        try {
            enforceTimedSessionExpiry()
            maybeStartAutoBreak()
            dropLapsedPacts()
            // A pact that has just run out must also close the app the user is
            // still inside: no window event fires while they stay in it, and
            // leaving this to the minute tick let every pact run up to a minute
            // over.
            currentForegroundPackage?.let { checkWellAndBlock(it) }
            DeviceOwnerManager.syncSuspensions(this)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error in transition check", e)
            reportNonFatal(e)
        }
        scheduleNextTransitionCheck()
    }

    // Held in a field: SharedPreferences only keeps listeners weakly.
    private val transitionPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in TRANSITION_KEYS) scheduleNextTransitionCheck()
        }
    private var transitionListenerRegistered = false

    private fun scheduleNextTransitionCheck() {
        transitionHandler.removeCallbacks(transitionRunnable)
        if (!::sharedPreferences.isInitialized) return
        val now = System.currentTimeMillis()
        val due = listOfNotNull(
            SessionTransitions.nextDueMillis(sharedPreferences, now),
            pactManager.getActiveAllowances(now).values.minOrNull()
        ).minOrNull() ?: return
        // Small slack so the wakeup lands just after the due instant, never before it.
        transitionHandler.postDelayed(transitionRunnable, due - now + TRANSITION_SLACK_MS)
    }

    private fun onMinuteTick() {
        // The default launcher and enabled keyboards can change without any
        // package broadcast (the user just picks a different home app), so
        // re-resolve them once a minute rather than only on installs.
        launcherCacheResolved = false
        cachedInputMethodPackageNames = null

        enforceTimedSessionExpiry()
        checkSchedules()
        maybeStartAutoBreak()

        // Take apps off the Well whose 24 h removal delay has passed. Before
        // the suspension sync, so Warden greying follows.
        WellApps.applyDueRemovals(sharedPreferences, gson)

        val today = TrialEngine.dateKeyOf(System.currentTimeMillis())
        if (today != lastTickDay) {
            pactWarnedExpiries.clear()
            lastTickDay = today
        }

        // Warn shortly before the foreground app's pact runs out.
        currentForegroundPackage?.let { maybeWarnPactEnding(it) }

        // Drop pacts that have run out, so the dashboard and the suspension
        // sync below see them gone.
        dropLapsedPacts()

        // Pay for time in productive apps every few minutes. The usage query
        // walks the day's events, so it runs on the writer thread.
        if (++productiveTicks % PRODUCTIVE_CREDIT_EVERY_TICKS == 0) {
            blockEventWriter.execute {
                try {
                    ProductiveApps.creditToday(this, sharedPreferences, gson)
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error crediting productive apps", e)
                }
            }
        }

        // Re-judge the app in front: no window event fires while the user
        // stays inside it.
        currentForegroundPackage?.let { checkWellAndBlock(it) }

        // Device owner: catch-all reconciliation for anything the event-driven sync
        // points missed (apps installed mid-session, a missed transition).
        DeviceOwnerManager.syncSuspensions(this)

        // Re-assert the session-countdown notification (or clear a stale one):
        // heals swipe-dismissals on Android 14+ and posts lost to rate limits.
        SessionNotifier.update(this)

        // Block events recorded within a second of the previous write stay
        // pending until the next block; flush them here so they reach Insights
        // and trial judging (and survive a process kill) within a minute.
        flushBlockEvents()
    }

    /**
     * Reports a swallowed exception to Crashlytics without risking a second failure —
     * the service must stay alive even if Firebase itself is unavailable.
     */
    private fun reportNonFatal(e: Exception) {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Exception) {
            // Crashlytics unavailable — already logged to logcat.
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        pactManager = PactManager(sharedPreferences, gson)
        openReflexTracker = OpenReflexTracker(sharedPreferences, gson)
        Log.d("MyAccessibilityService", "Service connected")

        // Clean up any break or timed session that expired while the service was down
        // (e.g. after a reboot), then activate any schedule that should currently be
        // running but wasn't started. Guarded so a bad persisted state can never
        // prevent the service from finishing its startup wiring below.
        try {
            enforceTimedSessionExpiry()
            checkMissedScheduleActivation()
            // Well-app removals that came due while the service was down
            // apply before the suspension sync reads the stores.
            WellApps.applyDueRemovals(sharedPreferences, gson)
            DeviceOwnerManager.syncSuspensions(this)
            // Re-assert uninstall protection on every service start: provisioning
            // can happen while the app is already running (adb), in which case
            // Application.onCreate never sees device-owner state.
            DeviceOwnerManager.applySelfProtection(this)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error reconciling state on service start", e)
            reportNonFatal(e)
        }

        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeTickReceiver, filter)
        }
        receiverRegistered = true

        sharedPreferences.registerOnSharedPreferenceChangeListener(transitionPrefsListener)
        transitionListenerRegistered = true
        scheduleNextTransitionCheck()

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, packageFilter)
        }
        packageReceiverRegistered = true

        RitualNotifier.createChannel(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        transitionHandler.removeCallbacks(transitionRunnable)
        transitionHandler.removeCallbacks(debounceRecheckRunnable)
        if (transitionListenerRegistered) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(transitionPrefsListener)
            transitionListenerRegistered = false
        }
        if (receiverRegistered) {
            try {
                unregisterReceiver(timeTickReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error unregistering receiver", e)
            }
        }
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver)
                packageReceiverRegistered = false
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error unregistering package receiver", e)
            }
        }
        // Flush pending block events (prefs are only wired once the service
        // connected); shutdown() still runs every queued write.
        if (::sharedPreferences.isInitialized) flushBlockEvents()
        blockEventWriter.shutdown()
    }

    /**
     * Called once on service start to activate a schedule that is currently within its active
     * window but was not running (e.g. after a phone reboot or service crash mid-schedule).
     *
     * The regular [checkSchedules] tick only fires at the exact start minute, so without this
     * a service restart inside an active window would leave the schedule dormant until the
     * next day.
     *
     * Handles same-day schedules (start < end) and the overnight carry-over case where the
     * current day is the day *after* the scheduled start day.
     */
    private fun checkMissedScheduleActivation() {
        // Skip if a session is already active (manual or scheduled).
        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        if (sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)) return

        val json = sharedPreferences.getString(Constants.PrefsKeys.SCHEDULES, null) ?: return
        val schedules: List<Schedule> = try {
            val type = object : com.google.gson.reflect.TypeToken<List<Schedule>>() {}.type
            gson.fromJson(json, type) ?: return
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing schedules in checkMissedScheduleActivation", e)
            return
        }

        if (schedules.isEmpty()) return

        // isScheduleActiveAt carries the overnight rule: only the evening part
        // counts on the scheduled day itself, while the early-morning hours
        // belong to the previous day's session (a Friday-only 22:00–06:00
        // ritual is active Saturday 03:00, not Friday 03:00).
        val now = System.currentTimeMillis()
        for (schedule in schedules) {
            try {
                if (isScheduleActiveAt(schedule, now)) {
                    Log.d("MyAccessibilityService", "Activating missed schedule on service start: ${schedule.name}")
                    activateSchedule(schedule)
                    return
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error checking missed schedule: ${schedule.name}", e)
            }
        }
    }

    /**
     * Ends breaks and timed manual sessions whose wall-clock end time has passed.
     *
     * The per-second countdowns live in the UI's ViewModel and stop when the activity
     * is destroyed, leaving IS_ON_BREAK / MANUAL_FOCUS_MODE stuck in SharedPreferences.
     * The service outlives the UI, so it enforces expiry from the persisted end
     * timestamps (BREAK_END_TIME_MILLIS / FOCUS_END_TIME_MILLIS).
     */
    private fun enforceTimedSessionExpiry() {
        val now = System.currentTimeMillis()

        if (sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) {
            val breakEnd = sharedPreferences.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L)
            if (breakEnd in 1..now) {
                // The focus countdown was frozen at break start; restart its end-time
                // clock from the frozen remaining value so expiry keeps being enforced.
                val focusRemaining = sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
                sharedPreferences.edit {
                    BreakClock.markEnded(sharedPreferences, this, now)
                    putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
                    putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
                    remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
                    // A fresh focus stretch begins now that the break is over
                    putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
                    if (focusRemaining > 0) {
                        putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + focusRemaining * 1000L)
                    }
                }
                Log.d("MyAccessibilityService", "Break expired while UI was away — resuming focus mode")
                DndController.updateDndState(this)
                DeviceOwnerManager.syncSuspensions(this)
            }
            // Still on break (or just resumed this instant) — no session expiry to enforce.
            return
        }

        if (!sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)) return
        // Scheduled sessions end via checkSchedules(), not a duration timer.
        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return

        val focusEnd = sharedPreferences.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        if (focusEnd in 1..now) {
            Log.d("MyAccessibilityService", "Timed session expired while UI was away — stopping session")
            SessionManager.stopSession(this, sharedPreferences, gson)
        }
    }

    /**
     * Pomodoro auto-break: when enabled in settings, starts a break automatically once
     * the current uninterrupted focus stretch reaches the configured interval. Runs on
     * the minute tick so it works whether or not the UI is alive. Mirrors the break
     * bookkeeping in SessionRepository.writeBreakState: the focus countdown is frozen
     * (FOCUS_END_TIME_MILLIS parked as remaining seconds) for the length of the break.
     */
    private fun maybeStartAutoBreak() {
        if (!sharedPreferences.getBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, false)) return
        if (sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) return

        if (!SessionManager.isFocusActive(sharedPreferences)) return
        if (!sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)) return

        // Respect schedule-level break overrides when a ritual is running.
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val activeSchedule = if (activeScheduleId != null) loadSchedulesCached().find { it.id == activeScheduleId } else null
        if (activeSchedule?.breaksEnabled == false) return

        // Extra-break perk tokens raise the session's effective quota, exactly
        // as the UI computes it — otherwise auto-breaks stop one break early
        // for users who bought one.
        val extraTokens = sharedPreferences.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0)
        val maxBreaks = (activeSchedule?.maxBreaksPerSession
            ?: sharedPreferences.getInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 3)).coerceAtLeast(1) +
            extraTokens
        val breaksUsed = sharedPreferences.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
        if (breaksUsed >= maxBreaks) return

        val now = System.currentTimeMillis()
        val segmentStart = sharedPreferences.getLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, 0L)
        if (segmentStart <= 0L) return
        val intervalMinutes = sharedPreferences.getInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 25)
            .coerceIn(5, 60)
        if (now - segmentStart < intervalMinutes * 60_000L) return

        val breakDuration = (activeSchedule?.breakDurationMinutes
            ?: sharedPreferences.getInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 5)).coerceAtLeast(1)

        // Freeze the focus countdown for the duration of the break.
        val focusEnd = sharedPreferences.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        sharedPreferences.edit {
            BreakClock.markStarted(sharedPreferences, this, now)
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakDuration * 60)
            putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now + breakDuration * 60_000L)
            putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsed + 1)
            if (focusEnd > 0L) {
                val focusRemaining = ((focusEnd - now) / 1000L).toInt().coerceAtLeast(0)
                putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusRemaining)
                remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            }
        }

        DndController.updateDndState(this)
        DeviceOwnerManager.syncSuspensions(this)
        Log.d("MyAccessibilityService", "Auto-break started after ${intervalMinutes}m of focus")
        sendAutoBreakNotification(intervalMinutes, breakDuration)
    }

    private fun sendAutoBreakNotification(focusedMinutes: Int, breakMinutes: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, Constants.RITUALS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.fplogo_round)
            .setContentTitle(getString(R.string.auto_break_started_title))
            .setContentText(getString(R.string.auto_break_started_message, focusedMinutes, breakMinutes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            notificationManager.notify(Constants.FOCUS_SESSION_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to send auto-break notification", e)
        }
    }

    /** Parsed schedules, re-parsed only when the stored JSON changes. */
    private fun loadSchedulesCached(): List<Schedule> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.SCHEDULES, null)
        if (json == null) {
            // Clear cache if schedules were deleted
            cachedSchedulesJson = null
            cachedSchedules = emptyList()
            return emptyList()
        }
        if (json == cachedSchedulesJson) return cachedSchedules
        return try {
            val type = object : TypeToken<List<Schedule>>() {}.type
            val parsed: List<Schedule>? = gson.fromJson(json, type)
            if (parsed == null) {
                cachedSchedulesJson = null
                cachedSchedules = emptyList()
                emptyList()
            } else {
                cachedSchedulesJson = json
                cachedSchedules = parsed
                parsed
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing schedules JSON", e)
            cachedSchedulesJson = null
            cachedSchedules = emptyList()
            emptyList()
        }
    }

    private fun checkSchedules() {
        val schedules = loadSchedulesCached()
        if (schedules.isEmpty()) return

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = calendarDayToDayOfWeek(now.get(Calendar.DAY_OF_WEEK))

        // Check if an active schedule has ended (handles missed end times and overnight schedules)
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeScheduleId != null) {
            val activeSchedule = schedules.find { it.id == activeScheduleId }
            if (activeSchedule != null) {
                try {
                    if (parseScheduleMinutes(activeSchedule.effectiveStartTime) == null ||
                        parseScheduleMinutes(activeSchedule.effectiveEndTime) == null
                    ) {
                        Log.e("MyAccessibilityService", "Invalid schedule time: ${activeSchedule.effectiveStartTime}-${activeSchedule.effectiveEndTime}")
                    } else if (!isScheduleActiveAt(activeSchedule, now.timeInMillis)) {
                        // Day-aware, like the alarm backstop's reconcile: a
                        // time-of-day-only check kept a ritual running through
                        // the *next* day's window when its end was missed (phone
                        // off overnight), even on days the ritual isn't scheduled.
                        deactivateSchedule(activeSchedule)
                    }
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error parsing schedule end time", e)
                }
            } else {
                // The active schedule was deleted while the session was running
                // (e.g. orphan cleanup after its enchantment was deleted). End the
                // session: only clearing the id left MANUAL_FOCUS_MODE on as an
                // endless untimed session, stuck if the stop button is hidden.
                Log.w("MyAccessibilityService", "Active schedule $activeScheduleId no longer exists, ending its session")
                SessionManager.stopSession(this, sharedPreferences, gson)
            }
        }

        // Check if a schedule should start (skip if one is already active).
        // Re-read from SharedPreferences in case deactivateSchedule() just cleared it above.
        if (currentDay == null) return
        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        schedules.forEach { schedule ->
            try {
                if (schedule.effectiveDays.contains(currentDay)) {
                    val startMins = parseScheduleMinutes(schedule.effectiveStartTime)
                    if (startMins == null) {
                        Log.e("MyAccessibilityService", "Invalid schedule start time: ${schedule.effectiveStartTime}")
                    } else if (startMins == currentHour * 60 + currentMinute) {
                        activateSchedule(schedule)
                    }
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing schedule time", e)
            }
        }
    }

    private fun activateSchedule(schedule: Schedule) {
        // Validate that at least one of the schedule's blockers still exists
        val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
        val validNames = schedule.effectiveBlockerNames.filter { name -> blockerLists.any { it.name == name } }
        if (validNames.isEmpty()) {
            Log.e("MyAccessibilityService", "Schedule '${schedule.name}' references no valid blockers, skipping activation")
            return
        }

        // Record any manual session in progress before the ritual replaces
        // it — the bare overwrite discarded its accrued focus time.
        if (SessionManager.isSessionActive(sharedPreferences)) {
            SessionManager.stopSession(this, sharedPreferences, gson)
        }

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerNames = validNames,
            scheduleId = schedule.id,
            breaksEnabled = schedule.breaksEnabled,
            scheduleEndTimeMillis = computeScheduleEndMillis(schedule)
        )

        DndController.updateDndState(this)
        DeviceOwnerManager.syncSuspensions(this)

        RitualNotifier.postStarted(this, schedule)
    }

    // computeScheduleEndMillis is a shared top-level function in ScheduleUtils.kt,
    // used by both this service and the RitualAlarmScheduler backstop.

    private fun deactivateSchedule(schedule: Schedule) {
        SessionManager.stopSession(this, sharedPreferences, gson)

        RitualNotifier.postEnded(this, schedule)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            // The service subscribes to typeWindowStateChanged only (see
            // accessibility_service_config.xml), but the framework is free to
            // deliver other types, so this stays a guard rather than a switch.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleWindowStateChanged(event)
            }
        } catch (e: Exception) {
            // Never let a single bad event crash the service — a dead service means
            // nothing is blocked until the user manually re-enables accessibility.
            Log.e("MyAccessibilityService", "Error handling accessibility event", e)
            reportNonFatal(e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // An app the service has just closed can still send a window event a
        // moment later, after FocusPocus's block screen is already in front.
        // That is not the app coming back. Taking it for one counted an open
        // that never happened, restarted the app's continuous-use session, and
        // left the next timed check blocking it a second time while the user
        // was still reading the first block.
        if (BlockDebounce.isEcho(
                lastAppBlockTimes[packageName], System.currentTimeMillis(), OverlayActivity.isOnTop
            )
        ) return

        trackOpenTransition(packageName)

        if (packageName == this.packageName || isLauncher(packageName) || isInputMethod(packageName) || isSystemUI(packageName)) {
            // Going home (or into FocusPocus itself) leaves the previous app.
            // SystemUI and the IME stay neutral: a notification-shade pull or
            // the keyboard opening doesn't mean the user left the app beneath.
            if (packageName == this.packageName || isLauncher(packageName)) {
                currentForegroundPackage = null
            }
            return
        }

        // End expired breaks / timed sessions before reading focus state below, so
        // blocking resumes (or stops) immediately on an app switch rather than
        // waiting for the next minute tick.
        enforceTimedSessionExpiry()

        // Track which real app is currently in the foreground so the minute-tick receiver can
        // close it when its pact runs out while the user stays inside (no window-state events fire then).
        currentForegroundPackage = packageName

        // Debounce — prevent rapid re-triggering when Android fires multiple events.
        // The app is judged when the window has passed instead, in case this
        // is the user coming straight back to it.
        val now = System.currentTimeMillis()
        val lastBlock = lastAppBlockTimes[packageName]
        if (BlockDebounce.isRecent(lastBlock, now)) {
            scheduleDebounceRecheck(packageName, BlockDebounce.remainingMillis(lastBlock, now))
            return
        }

        blockIfDue(packageName, now)
    }

    /**
     * Judges an app that is in front and blocks it if anything says so: the
     * talisman lock, a focus session's enchantments, then the Mana Well.
     */
    private fun blockIfDue(packageName: String, now: Long) {
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
        val focusActive = SessionManager.isFocusActive(sharedPreferences)
        val nfcLockMode = sharedPreferences.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false)

        // NFC lock mode: block settings apps when focus is active (regardless of break)
        if (focusActive && nfcLockMode && packageName in SETTINGS_PACKAGES) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "NFC Lock: Blocking settings app: $appName")
            lastAppBlockTimes[packageName] = now
            currentForegroundPackage = null
            closeApp()
            showOverlay(appName, getString(R.string.service_talisman_lock))
            return
        }

        // Don't block apps during a break (for normal blocking)
        if (isOnBreak) {
            // The Well still holds during breaks
            checkWellAndBlock(packageName)
            return
        }

        if (focusActive) {
            val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
            val activeBlockerNames = getActiveBlockerNames()
            val activeBlockers = blockerLists.filter { it.name in activeBlockerNames }

            for (blocker in activeBlockers) {
                // A whitelist may only block apps the picker offers — otherwise
                // unselectable system surfaces (Android Auto's projection UI,
                // permission dialogs…) get blocked with no way to exempt them.
                if (blocker.mode == BlockerMode.WHITELIST && !isWhitelistBlockable(packageName)) continue
                if (blocker.shouldBlock(packageName)) {
                    val appName = AppUtils.getAppName(this, packageName)
                    if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking app: $appName")
                    lastAppBlockTimes[packageName] = now
                    currentForegroundPackage = null
                    recordBlockEvent(packageName, blocker.name)
                    closeApp()
                    showOverlay(appName, activeBlockerNames.joinToString(", "))
                    return
                }
            }
        }

        // The Mana Well (always active, even outside focus mode)
        checkWellAndBlock(packageName)
    }

    /**
     * A Well app is blocked by default: without a running pact, opening it
     * offers one, paid from the Mana Well.
     */
    private fun checkWellAndBlock(packageName: String) {
        // Debounce — prevent rapid re-triggering from the window-state and timed paths
        val now = System.currentTimeMillis()
        if (BlockDebounce.isRecent(lastAppBlockTimes[packageName], now)) return

        val wellApp = WellApps.find(sharedPreferences, gson, packageName) ?: return
        if (pactManager.getAllowanceExpiry(packageName, now) != null) return
        // A pact that has run out is just dropped: the app goes back behind the Well.
        pactManager.takeLapsedAllowance(packageName, now)

        val appName = AppUtils.getAppName(this, packageName)
        if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Well: $appName blocked until a pact")
        lastAppBlockTimes[packageName] = now
        currentForegroundPackage = null
        recordBlockEvent(packageName, "Pact Gate")
        closeApp()
        showPactOverlay(appName, packageName, wellApp.substitutePackage)
    }

    private fun getActiveBlockerNames(): List<String> =
        BlockerRepository.getActiveBlockerNames(sharedPreferences)

    private fun recordBlockEvent(packageName: String, blockerName: String) {
        val shouldFlush: Boolean
        synchronized(pendingBlockEvents) {
            pendingBlockEvents.add(BlockEvent(packageName, System.currentTimeMillis(), blockerName))
            val now = System.currentTimeMillis()
            shouldFlush = now - lastBlockEventWriteTime > 1000
        }
        if (shouldFlush) {
            flushBlockEvents()
        }
    }

    private fun flushBlockEvents() {
        val eventsToWrite: List<BlockEvent>
        synchronized(pendingBlockEvents) {
            if (pendingBlockEvents.isEmpty()) return
            eventsToWrite = pendingBlockEvents.toList()
            pendingBlockEvents.clear()
        }
        lastBlockEventWriteTime = System.currentTimeMillis()
        // Parsing and re-serializing up to MAX_BLOCK_EVENTS entries is too
        // heavy for the service's main thread (an ANR there silently disables
        // all blocking). One single-thread executor keeps writes ordered and
        // owns the block-event cache below.
        try {
            blockEventWriter.execute { writeBlockEvents(eventsToWrite) }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Executor already shut down (service destroyed): write inline.
            writeBlockEvents(eventsToWrite)
        }
    }

    private val blockEventWriter = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun writeBlockEvents(eventsToWrite: List<BlockEvent>) {
        val json = sharedPreferences.getString(Constants.PrefsKeys.BLOCK_EVENTS, null)
        val existing: MutableList<BlockEvent> = when {
            json == null -> mutableListOf()
            json == cachedBlockEventsJson -> cachedBlockEvents.toMutableList()
            else -> try {
                val type = object : TypeToken<MutableList<BlockEvent>>() {}.type
                gson.fromJson<MutableList<BlockEvent>>(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing block events JSON", e)
                mutableListOf()
            }
        }
        existing.addAll(eventsToWrite)
        val pruned = if (existing.size > Constants.MAX_BLOCK_EVENTS) {
            existing.drop(existing.size - Constants.MAX_BLOCK_EVENTS)
        } else existing
        val prunedJson = gson.toJson(pruned)
        cachedBlockEventsJson = prunedJson
        cachedBlockEvents = pruned
        sharedPreferences.edit { putString(Constants.PrefsKeys.BLOCK_EVENTS, prunedJson) }
    }

    private var cachedLauncherPackageName: String? = null
    private var launcherCacheResolved = false

    private fun isLauncher(packageName: String): Boolean {
        if (!launcherCacheResolved) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            cachedLauncherPackageName = resolveInfo?.activityInfo?.packageName
            launcherCacheResolved = true
        }
        return cachedLauncherPackageName == packageName
    }

    private var cachedInputMethodPackageNames: Set<String>? = null

    private fun isInputMethod(packageName: String): Boolean {
        if (cachedInputMethodPackageNames == null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            cachedInputMethodPackageNames = imm.enabledInputMethodList.map { it.packageName }.toSet()
        }
        return cachedInputMethodPackageNames?.contains(packageName) == true
    }

    private fun isSystemUI(packageName: String): Boolean {
        return packageName == "com.android.systemui" || packageName == "android"
    }

    // Whitelist-blockable verdicts per package (two binder calls each), cached
    // because window-state events repeat the same packages constantly. Cleared
    // on package add/remove/replace alongside the other package caches.
    private val whitelistBlockableCache = HashMap<String, Boolean>()

    private fun isWhitelistBlockable(packageName: String): Boolean =
        whitelistBlockableCache.getOrPut(packageName) {
            AppUtils.isWhitelistBlockable(this, packageName)
        }

    private fun closeApp() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun showOverlay(appName: String, spellName: String? = null) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("spellName", spellName)
        startActivity(intent)
    }

    private fun showPactOverlay(appName: String, packageName: String, substitutePackage: String?) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("pactPackageName", packageName)

        // Awareness counter: this attempt is already counted, so opens is "open #N".
        val stats = openReflexTracker.getStats(packageName)
        intent.putExtra("pactTodayOpens", stats.opens)
        intent.putExtra("pactTodayReflexOpens", stats.reflexOpens)

        // Optional healthier substitute ("Open X instead")
        if (substitutePackage != null && isPackageInstalled(substitutePackage)) {
            intent.putExtra("pactAlternativePackage", substitutePackage)
            intent.putExtra("pactAlternativeName", AppUtils.getAppName(this, substitutePackage))
        }
        startActivity(intent)
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Open/reflex bookkeeping for the pact overlay's awareness counter. Runs before
     * the launcher/IME/system filter because going home is what ends an "open" —
     * while our own overlay, the keyboard, and system UI appearing don't mean the
     * user left the app.
     */
    private fun trackOpenTransition(packageName: String) {
        if (packageName == this.packageName || isInputMethod(packageName) || isSystemUI(packageName)) return
        val now = System.currentTimeMillis()
        if (isLauncher(packageName)) {
            openTrackingPackage?.let { finishOpenTracking(it, now) }
            return
        }
        if (packageName == openTrackingPackage) return
        openTrackingPackage?.let { finishOpenTracking(it, now) }
        if (isOpenTracked(packageName)) {
            openReflexTracker.recordOpen(packageName)
        }
        openTrackingPackage = packageName
        openTrackingSince = now
    }

    private fun finishOpenTracking(packageName: String, now: Long) {
        if (isOpenTracked(packageName)) {
            openReflexTracker.recordClose(packageName, now - openTrackingSince)
        }
        openTrackingPackage = null
    }

    /** Apps whose opens/reflexes we count: the Well apps. */
    private fun isOpenTracked(packageName: String): Boolean =
        packageName in WellApps.packages(sharedPreferences, gson)

    /**
     * Fires a one-time toast when the foreground app's pact is about to run
     * out, so the end doesn't land mid-scroll without warning.
     */
    private fun maybeWarnPactEnding(packageName: String) {
        val expiry = pactManager.getAllowanceExpiry(packageName) ?: return
        val remainingMs = expiry - System.currentTimeMillis()
        if (remainingMs in 1..90_000 && pactWarnedExpiries[packageName] != expiry) {
            pactWarnedExpiries[packageName] = expiry
            val message = getString(R.string.pact_ending_no_seal_toast, AppUtils.getAppName(this, packageName))
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Drops every pact that has run out. Take-once, so racing the open-attempt
     * path is harmless; one store write for the whole sweep.
     */
    private fun dropLapsedPacts(now: Long = System.currentTimeMillis()) {
        pactManager.takeLapsedAllowances(now)
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }
}
