package com.infinicada.focuspocus

import android.accessibilityservice.AccessibilityService
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
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.BlockDebounce
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.model.Schedule
import kotlinx.serialization.encodeToString
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
            sharedPreferences.edit { putString(Constants.PrefsKeys.BLOCKER_LISTS, AppJson.json.encodeToString(updated)) }
            Log.d("MyAccessibilityService", "Auto-added new app to opted-in enchantments")
        }
    }

    private fun invalidatePackageCaches() {
        launcherCacheResolved = false
        cachedLauncherPackageName = null
        cachedInputMethodPackageNames = null
        cachedSettingsPackages = null
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

    // Open counter shown on the pact overlay
    private lateinit var openReflexTracker: OpenReflexTracker

    // Which app the open bookkeeping considers foreground. Separate from
    // currentForegroundPackage, which the launcher, keyboard and system UI
    // deliberately leave alone.
    @Volatile private var openTrackingPackage: String? = null

    // Minute ticks since the service started, for the productive-app credit
    private var productiveTicks = 0

    // Block event recording
    private val pendingBlockEvents = mutableListOf<BlockEvent>()
    @Volatile private var lastBlockEventWriteTime: Long = 0

    // Cache of the persisted block-event list so each flush doesn't re-parse a
    // potentially 1000-entry JSON array on the service's main thread.
    @Volatile private var cachedBlockEventsJson: String? = null
    @Volatile private var cachedBlockEvents: List<BlockEvent> = emptyList()

    // Base settings/installer/permission packages to block in Talisman Lock Mode,
    // augmented at runtime with the resolved ACTION_SETTINGS package.
    private val BASE_SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.samsung.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.oplus.safecenter",
        "com.coloros.safecenter"
    )
    private var cachedSettingsPackages: Set<String>? = null

    private fun getSettingsPackages(): Set<String> {
        cachedSettingsPackages?.let { return it }
        val resolved = try {
            packageManager.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
        val combined = if (resolved != null) BASE_SETTINGS_PACKAGES + resolved else BASE_SETTINGS_PACKAGES
        cachedSettingsPackages = combined
        return combined
    }

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
            SessionManager.advance(this, sharedPreferences, loadSchedulesCached())
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
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val activeSchedule = activeScheduleId?.let { id -> loadSchedulesCached().find { it.id == id } }
        val due = listOfNotNull(
            SessionTransitions.nextDueMillis(sharedPreferences, now, activeSchedule),
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

        SessionManager.advance(this, sharedPreferences, loadSchedulesCached())
        checkSchedules()

        // Take apps off the Well whose 24 h removal delay has passed. Before
        // the suspension sync, so Warden greying follows.
        WellApps.applyDueRemovals(sharedPreferences)

        // Forget warnings for pacts that have since ended. (Clearing the lot
        // at midnight repeated the warning for a pact running across it.)
        val tickNow = System.currentTimeMillis()
        pactWarnedExpiries.values.removeAll { it <= tickNow }

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
                    ProductiveApps.creditToday(this, sharedPreferences)
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
        pactManager = PactManager(sharedPreferences)
        openReflexTracker = OpenReflexTracker(sharedPreferences)
        Log.d("MyAccessibilityService", "Service connected")
        ProtectionAlertNotifier.cancel(this)
        EnforcementLog.record(sharedPreferences, EnforcementLog.CAT_SERVICE, "Accessibility service connected")

        // Clean up any break or timed session that expired while the service was down
        // (e.g. after a reboot), then activate any schedule that should currently be
        // running but wasn't started. Guarded so a bad persisted state can never
        // prevent the service from finishing its startup wiring below.
        try {
            SessionManager.advance(this, sharedPreferences, loadSchedulesCached())
            checkMissedScheduleActivation()
            // Well-app removals that came due while the service was down
            // apply before the suspension sync reads the stores.
            WellApps.applyDueRemovals(sharedPreferences)
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
        if (::sharedPreferences.isInitialized) {
            flushBlockEvents()
            EnforcementLog.record(sharedPreferences, EnforcementLog.CAT_SERVICE, "Accessibility service stopped")
        }
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
            AppJson.decodeListLenient(json)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing schedules in checkMissedScheduleActivation", e)
            return
        }

        if (schedules.isEmpty()) return

        // isScheduleActiveAt carries the overnight rule: only the evening part
        // counts on the scheduled day itself, while the early-morning hours
        // belong to the previous day's session (a Friday-only 22:00–06:00
        // ritual is active Saturday 03:00, not Friday 03:00). A ritual the
        // user ended early in this window is not one that was missed.
        val now = System.currentTimeMillis()
        try {
            val blockerNames = BlockerRepository.getBlockers(sharedPreferences).mapTo(mutableSetOf()) { it.name }
            val exceptions = RitualExceptions.loadAll(sharedPreferences)
            val schedule = findRitualToResume(
                schedules = schedules.filterNotNull(),
                nowMillis = now,
                existingBlockerNames = blockerNames,
                skippedDatesFor = { exceptions[it].orEmpty() }
            ) {
                SessionManager.isRitualDismissed(sharedPreferences, it, now)
            } ?: return
            Log.d("MyAccessibilityService", "Activating missed schedule on service start: ${schedule.name}")
            activateSchedule(schedule)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error checking missed schedules", e)
        }
    }

    // Timed session expiry, break expiry, and Pomodoro auto-breaks are handled
    // uniformly by SessionManager.advance (backed by pure SessionRules).

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
            val parsed: List<Schedule> = AppJson.decodeListLenient(json)
            cachedSchedulesJson = json
            cachedSchedules = parsed
            parsed
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

        val exceptions = RitualExceptions.loadAll(sharedPreferences)
        val todayKey = TrialEngine.dateKeyOf(now.timeInMillis)

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
                    } else if (!isScheduleActiveAt(activeSchedule, now.timeInMillis, exceptions[activeSchedule.id].orEmpty())) {
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
                SessionManager.stopSession(this, sharedPreferences)
            }
        }

        // Check if a schedule should start (skip if one is already active).
        // Re-read from SharedPreferences in case deactivateSchedule() just cleared it above.
        if (currentDay == null) return
        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        for (schedule in schedules) {
            try {
                if (todayKey in exceptions[schedule.id].orEmpty()) continue
                if (schedule.effectiveDays.contains(currentDay)) {
                    val startMins = parseScheduleMinutes(schedule.effectiveStartTime)
                    if (startMins == null) {
                        Log.e("MyAccessibilityService", "Invalid schedule start time: ${schedule.effectiveStartTime}")
                    } else if (startMins == currentHour * 60 + currentMinute) {
                        activateSchedule(schedule)
                        // One ritual at a time: a second one starting this
                        // same minute would end the first on the spot.
                        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
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
            SessionManager.stopSession(this, sharedPreferences)
        }

        SessionManager.startSession(
            context = this,
            sharedPreferences = sharedPreferences,
            blockerNames = validNames,
            scheduleId = schedule.id,
            breaksEnabled = schedule.breaksEnabled,
            scheduleEndTimeMillis = computeScheduleEndMillis(schedule)
        )

        RitualNotifier.postStarted(this, schedule)
    }

    // computeScheduleEndMillis is a shared top-level function in ScheduleUtils.kt,
    // used by both this service and the RitualAlarmScheduler backstop.

    private fun deactivateSchedule(schedule: Schedule) {
        SessionManager.stopSession(this, sharedPreferences)

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
        SessionManager.advance(this, sharedPreferences, loadSchedulesCached())

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
        val focusActive = SessionManager.isFocusActive(sharedPreferences)
        val nfcLockMode = sharedPreferences.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false)

        // NFC lock mode: block settings apps when focus is active (regardless of break)
        if (focusActive && nfcLockMode && packageName in getSettingsPackages()) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "NFC Lock: Blocking settings app: $appName")
            lastAppBlockTimes[packageName] = now
            currentForegroundPackage = null
            closeApp()
            showOverlay(appName, getString(R.string.service_talisman_lock))
            return
        }

        // The session's enchantments: none of them blocks during a break.
        val blocker = BlockerRepository.sessionBlockerFor(sharedPreferences, packageName, ::isWhitelistBlockable)
        if (blocker != null) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking app: $appName")
            lastAppBlockTimes[packageName] = now
            currentForegroundPackage = null
            recordBlockEvent(packageName, blocker.name)
            val substitutePackage = WellApps.find(sharedPreferences, packageName)?.substitutePackage
                ?.takeIf { sub ->
                    isPackageInstalled(sub) &&
                        BlockerRepository.sessionBlockerFor(sharedPreferences, sub, ::isWhitelistBlockable) == null
                }
            closeApp()
            showOverlay(appName, getActiveBlockerNames().joinToString(", "), substitutePackage)
            return
        }

        // The Mana Well (always active: outside focus mode and during breaks too)
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

        val wellApp = WellApps.find(sharedPreferences, packageName) ?: return
        if (pactManager.getAllowanceExpiry(packageName, now) != null) return
        // A pact that has run out is just dropped: the app goes back behind the Well.
        pactManager.takeLapsedAllowance(packageName, now)

        val appName = AppUtils.getAppName(this, packageName)
        if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Well: $appName blocked until a pact")
        lastAppBlockTimes[packageName] = now
        currentForegroundPackage = null
        recordBlockEvent(packageName, BlockEvent.PACT_GATE_BLOCKER_NAME)
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
                AppJson.decodeListLenient<BlockEvent>(json).toMutableList()
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing block events JSON", e)
                mutableListOf()
            }
        }
        existing.addAll(eventsToWrite)
        val pruned = if (existing.size > Constants.MAX_BLOCK_EVENTS) {
            existing.drop(existing.size - Constants.MAX_BLOCK_EVENTS)
        } else existing
        val prunedJson = AppJson.json.encodeToString(pruned)
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

    private fun showOverlay(
        appName: String,
        spellName: String? = null,
        substitutePackage: String? = null
    ) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("spellName", spellName)
        if (substitutePackage != null && isPackageInstalled(substitutePackage)) {
            intent.putExtra("pactAlternativePackage", substitutePackage)
            intent.putExtra("pactAlternativeName", AppUtils.getAppName(this, substitutePackage))
        }
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
     * Open bookkeeping for the pact overlay's awareness counter. Runs before
     * the launcher/IME/system filter because going home ends the foreground
     * stay — while our own overlay, the keyboard, and system UI appearing
     * don't mean the user left the app.
     */
    private fun trackOpenTransition(packageName: String) {
        if (packageName == this.packageName || isInputMethod(packageName) || isSystemUI(packageName)) return
        if (isLauncher(packageName)) {
            openTrackingPackage = null
            return
        }
        if (packageName == openTrackingPackage) return
        if (isOpenTracked(packageName)) {
            openReflexTracker.recordOpen(packageName)
        }
        openTrackingPackage = packageName
    }

    /** Apps whose opens we count: the Well apps. */
    private fun isOpenTracked(packageName: String): Boolean =
        packageName in WellApps.packages(sharedPreferences)

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
        val lapsed = pactManager.takeLapsedAllowances(now)
        if (lapsed.isNotEmpty()) {
            PactNotifier.update(this)
        }
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }
}
