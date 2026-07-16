package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.FrictionLevel
import com.infinicada.focuspocus.limit.GuardSchedule
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.widget.GuardWidgetProvider
import com.infinicada.focuspocus.model.AppTimeLimit
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val WEBSITE_BLOCK_DEBOUNCE_MS = 2000L
        private const val APP_BLOCK_DEBOUNCE_MS = 2000L

        private val BROWSER_URL_BAR_IDS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.chrome.dev" to "com.chrome.dev:id/url_bar",
            "com.chrome.canary" to "com.chrome.canary:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox_beta" to "org.mozilla.firefox_beta:id/url_bar_title",
            "org.mozilla.fenix" to "org.mozilla.fenix:id/url_bar_title",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar",
            "com.kiwibrowser.browser" to "com.kiwibrowser.browser:id/url_bar",
            "org.chromium.chrome" to "org.chromium.chrome:id/url_bar"
        )
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var browserPackages: Set<String> = emptySet()
    private var receiverRegistered = false

    private var packageReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                updateBrowserPackages()
                invalidatePackageCaches()
                // Fresh installs (not updates) get added to opted-in blocklists
                if (intent?.action == Intent.ACTION_PACKAGE_ADDED &&
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) {
                    intent.data?.schemeSpecificPart?.let { autoAddNewAppToBlockers(it) }
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error updating browser packages", e)
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
            sharedPreferences.edit()
                .putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(updated))
                .apply()
            Log.d("MyAccessibilityService", "Auto-added new app to opted-in enchantments")
        }
    }

    private fun updateBrowserPackages() {
        browserPackages = BrowserDetector(this).getBrowserPackages()
        Log.d("MyAccessibilityService", "Updated browser packages: $browserPackages")
    }

    private fun invalidatePackageCaches() {
        launcherCacheResolved = false
        cachedLauncherPackageName = null
        cachedInputMethodPackageNames = null
    }

    // Cache for parsed schedules to avoid re-parsing JSON every minute
    @Volatile private var cachedSchedulesJson: String? = null
    @Volatile private var cachedSchedules: List<Schedule> = emptyList()

    // Debounce for website blocking to prevent rapid re-triggering
    @Volatile private var lastWebsiteBlockTime: Long = 0

    // Debounce for app blocking, per package — Android fires multiple window-state
    // events when one app opens, but blocking one app must not suppress checks for
    // a different app opened right after.
    private val lastAppBlockTimes = HashMap<String, Long>()

    // Most recently focused "real" app (updated on every window state change, before debounce)
    @Volatile private var currentForegroundPackage: String? = null

    // Cache for time limits
    @Volatile private var cachedTimeLimitsJson: String? = null
    @Volatile private var cachedTimeLimits: Map<String, Int> = emptyMap()

    // Cache for time limit configs (includes per-session cooldown settings)
    @Volatile private var cachedTimeLimitConfigsJson: String? = null
    @Volatile private var cachedTimeLimitConfigs: Map<String, AppTimeLimit> = emptyMap()

    // Session cooldown manager — tracks per-app usage sessions and cooldown state
    private lateinit var sessionCooldownManager: SessionCooldownManager

    // Pact manager — tracks Pact Mode allowances (blocked-by-default apps)
    private lateinit var pactManager: PactManager

    // Pact expiry warnings already toasted, keyed by package -> allowance expiry.
    // In-memory only; a duplicate toast after a service restart is harmless.
    private val pactWarnedExpiries = HashMap<String, Long>()

    // Seal expiries already announced via the opt-in "seal lifted" notification,
    // keyed by package -> cooldown expiry. In-memory only: a restart mid-window
    // just skips (or repeats) one best-effort notification.
    private val sealLiftNotified = HashMap<String, Long>()

    // Open/reflex counter shown on the pact overlay
    private lateinit var openReflexTracker: OpenReflexTracker

    // Which tracked-or-untracked app the open/reflex bookkeeping considers foreground,
    // and since when. Separate from currentForegroundPackage because going home ends
    // an "open" here but deliberately does NOT end a session-limit usage session
    // (bouncing to the launcher must not reset session limits).
    @Volatile private var openTrackingPackage: String? = null
    @Volatile private var openTrackingSince: Long = 0L

    // Per-package pact configs synthesized from pact groups, recomputed only when
    // the groups or the enchantments they point at change.
    @Volatile private var pactGroupCacheKey: Pair<String?, String?>? = null
    @Volatile private var cachedPactGroupConfigs: Map<String, AppTimeLimit> = emptyMap()

    // Date string used to detect when a new day begins (format: "yyyyMMdd")
    @Volatile private var lastCooldownResetDate: String? = null

    // Pacing notifications: tracks which usage thresholds have already been toasted today.
    // Key = packageName, value = set of thresholds already shown (e.g. 50, 75, 90).
    // In-memory only — resets on service restart and at midnight.
    private val pacingNotifiedToday = mutableMapOf<String, MutableSet<Int>>()

    // Last time each package's pacing usage was actually queried, so rapid app
    // switches don't each pay for a full-day UsageStats scan on the main thread.
    private val lastPacingCheckTimes = HashMap<String, Long>()

    // Block event recording
    private val pendingBlockEvents = mutableListOf<BlockEvent>()
    @Volatile private var lastBlockEventWriteTime: Long = 0

    // Cache of the persisted block-event list so each flush doesn't re-parse a
    // potentially 1000-entry JSON array on the service's main thread.
    @Volatile private var cachedBlockEventsJson: String? = null
    @Volatile private var cachedBlockEvents: List<BlockEvent> = emptyList()

    private val timeLimitChecker by lazy { TimeLimitChecker(this) }

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
                    // Screen off ends the foreground app's continuous-use
                    // session — otherwise idle screen-off time keeps counting
                    // toward the session limit and triggers a cooldown for
                    // use that never happened.
                    currentForegroundPackage?.let { sessionCooldownManager.onAppLeft(it) }
                    currentForegroundPackage = null
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error handling screen off", e)
                    reportNonFatal(e)
                }
            }
        }
    }

    private fun onMinuteTick() {
        enforceTimedSessionExpiry()
        checkSchedules()
        maybeStartAutoBreak()

        // Reset daily counters when the calendar date rolls over. Expired cooldown
        // entries are pruned here too (not on every tick): they must survive until
        // the rollover so startCooldown can escalate repeat offences within a day.
        if (SessionCooldownManager.isNewDay(lastCooldownResetDate)) {
            sessionCooldownManager.resetDailyCooldowns()
            pacingNotifiedToday.clear()
            pactWarnedExpiries.clear()
            sealLiftNotified.clear()
            lastCooldownResetDate = SessionCooldownManager.todayString()
        }

        // Pacing notification for the app currently in the foreground.
        currentForegroundPackage?.let { pkg ->
            val limit = getCachedTimeLimits()[pkg]
            if (limit != null) checkPacingNotification(pkg, limit)
        }

        // Warn shortly before the foreground app's pact allowance lapses.
        currentForegroundPackage?.let { maybeWarnPactEnding(it) }

        // Convert lapsed pact allowances into their seal cooldowns proactively.
        // This used to happen lazily on the next open attempt, but under Warden
        // greying the OS refuses that reopen outright — and the dashboard and
        // suspension sync below both need the seal on record to stay truthful.
        sealLapsedPacts()

        // Opt-in note when a seal lifts, so nobody has to keep checking the app.
        maybeNotifySealsLifted()

        // Keep the home-screen widget's guard headline fresh (cheap, best effort).
        GuardWidgetProvider.push(this)

        // Also enforce time limits on whichever app is currently in the foreground.
        // TYPE_WINDOW_STATE_CHANGED only fires on app switches, so without this a user
        // could stay inside an app past its daily limit indefinitely.
        currentForegroundPackage?.let { checkTimeLimitAndBlock(it) }

        // Evening progression wrap-up (best effort — a persisted date guard means
        // a service restart at 21:30 still sends it on the next tick, while a
        // fully-down service just skips the day).
        maybeSendDailyWrapup()

        // Device owner: catch-all reconciliation for anything the event-driven sync
        // points missed (apps installed mid-session, missed pref writes).
        DeviceOwnerManager.syncSuspensions(this)
    }

    /**
     * Posts the "you reclaimed 2h 15m today" note once per active evening. The
     * cheap LAST_SESSION_RECORDED_DATE string comparison inside shouldSendWrapup
     * short-circuits inactive days before any JSON parsing happens, so this
     * costs the minute tick nothing on quiet days.
     */
    private fun maybeSendDailyWrapup() {
        val todayKey = SessionCooldownManager.todayString()
        val shouldSend = ProgressionMath.shouldSendWrapup(
            hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            todayKey = todayKey,
            lastWrapupDate = sharedPreferences.getString(Constants.PrefsKeys.LAST_WRAPUP_DATE, null),
            lastSessionRecordedDate = sharedPreferences.getString(Constants.PrefsKeys.LAST_SESSION_RECORDED_DATE, null),
            wrapupEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.WRAPUP_ENABLED, true),
            progressionEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)
        )
        if (!shouldSend) return

        val sessions: List<FocusSession> = PrefsHelper.load(
            sharedPreferences, gson, Constants.PrefsKeys.FOCUS_SESSIONS,
            object : com.google.gson.reflect.TypeToken<List<FocusSession>>() {}.type
        ) ?: emptyList()
        val todaySessions = sessions.filter { TrialEngine.dateKeyOf(it.endTimeMillis) == todayKey }
        if (todaySessions.isEmpty()) return
        val reclaimedMinutes = todaySessions.sumOf { it.durationMinutes }

        val ledger = Progression.loadLedger(sharedPreferences, gson)
        val manaToday = ledger.filter { it.dateKey == todayKey && it.amount > 0 }.sumOf { it.amount }

        ProgressionNotifier.postDailyWrapup(
            this,
            reclaimedMinutes = reclaimedMinutes,
            manaToday = manaToday,
            streak = calculateCurrentStreak(sessions)
        )
        sharedPreferences.edit().putString(Constants.PrefsKeys.LAST_WRAPUP_DATE, todayKey).apply()
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
        sessionCooldownManager = SessionCooldownManager(sharedPreferences, gson)
        pactManager = PactManager(sharedPreferences, gson)
        openReflexTracker = OpenReflexTracker(sharedPreferences, gson)
        lastCooldownResetDate = SessionCooldownManager.todayString()
        Log.d("MyAccessibilityService", "Service connected")

        // Clean up any break or timed session that expired while the service was down
        // (e.g. after a reboot), then activate any schedule that should currently be
        // running but wasn't started. Guarded so a bad persisted state can never
        // prevent the service from finishing its startup wiring below.
        try {
            enforceTimedSessionExpiry()
            checkMissedScheduleActivation()
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

        updateBrowserPackages()
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

        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        // Flush pending block events
        flushBlockEvents()
    }

    private fun createNotificationChannel() {
        try {
            val name = getString(R.string.rituals_channel_name)
            val descriptionText = getString(R.string.rituals_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(Constants.RITUALS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to create notification channel", e)
        }
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

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = mapCalendarDayToDayOfWeek(now.get(Calendar.DAY_OF_WEEK)) ?: return

        // Also derive yesterday's DayOfWeek for overnight carry-over checks.
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val previousDay = mapCalendarDayToDayOfWeek(yesterdayCal.get(Calendar.DAY_OF_WEEK))

        for (schedule in schedules) {
            try {
                val startParts = schedule.effectiveStartTime.split(":")
                val endParts = schedule.effectiveEndTime.split(":")
                if (startParts.size != 2 || endParts.size != 2) continue

                val startHour = startParts[0].toIntOrNull() ?: continue
                val startMinute = startParts[1].toIntOrNull() ?: continue
                val endHour = endParts[0].toIntOrNull() ?: continue
                val endMinute = endParts[1].toIntOrNull() ?: continue
                if (startHour !in 0..23 || startMinute !in 0..59 || endHour !in 0..23 || endMinute !in 0..59) continue

                val startMins = startHour * 60 + startMinute
                val endMins = endHour * 60 + endMinute
                val currentMins = currentHour * 60 + currentMinute
                // Overnight = end is before start (wraps past midnight).
                val overnight = endMins < startMins

                // A window that opened today. For overnight schedules only the
                // evening part counts on the scheduled day itself — the early
                // morning hours belong to the *previous* day's session, so a
                // Friday-only 22:00–06:00 schedule must not light up Friday at
                // 03:00.
                val activeFromToday = schedule.effectiveDays.contains(currentDay) &&
                    if (overnight) {
                        currentMins >= startMins
                    } else {
                        currentMins >= startMins && currentMins < endMins
                    }

                // An overnight window that opened yesterday and carries over
                // into this morning.
                val activeFromYesterday = overnight && previousDay != null &&
                    schedule.effectiveDays.contains(previousDay) && currentMins < endMins

                val inWindow = activeFromToday || activeFromYesterday

                if (inWindow) {
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
                val editor = sharedPreferences.edit()
                    .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
                    .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
                    .remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
                    // A fresh focus stretch begins now that the break is over
                    .putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
                if (focusRemaining > 0) {
                    editor.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + focusRemaining * 1000L)
                }
                editor.apply()
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

        val focusActive = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false) ||
            sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
        if (!focusActive) return
        if (!sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)) return

        // Respect schedule-level break overrides when a ritual is running.
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val activeSchedule = if (activeScheduleId != null) cachedSchedules.find { it.id == activeScheduleId } else null
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

        val editor = sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakDuration * 60)
            .putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now + breakDuration * 60_000L)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, breaksUsed + 1)
        // Freeze the focus countdown for the duration of the break.
        val focusEnd = sharedPreferences.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        if (focusEnd > 0L) {
            val focusRemaining = ((focusEnd - now) / 1000L).toInt().coerceAtLeast(0)
            editor.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusRemaining)
            editor.remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
        }
        editor.apply()

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

    private fun checkSchedules() {
        val json = sharedPreferences.getString(Constants.PrefsKeys.SCHEDULES, null)
        if (json == null) {
            // Clear cache if schedules were deleted
            if (cachedSchedulesJson != null) {
                cachedSchedulesJson = null
                cachedSchedules = emptyList()
            }
            return
        }

        // Use cached schedules if JSON hasn't changed
        val schedules: List<Schedule> = if (json == cachedSchedulesJson) {
            cachedSchedules
        } else {
            try {
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

        if (schedules.isEmpty()) return

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = mapCalendarDayToDayOfWeek(now.get(Calendar.DAY_OF_WEEK))

        // Check if an active schedule has ended (handles missed end times and overnight schedules)
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeScheduleId != null) {
            val activeSchedule = schedules.find { it.id == activeScheduleId }
            if (activeSchedule != null) {
                try {
                    val endParts = activeSchedule.effectiveEndTime.split(":")
                    val startParts = activeSchedule.effectiveStartTime.split(":")
                    if (endParts.size == 2 && startParts.size == 2) {
                        val endHour = endParts[0].toIntOrNull() ?: -1
                        val endMinute = endParts[1].toIntOrNull() ?: -1
                        val startHour = startParts[0].toIntOrNull() ?: -1
                        val startMinute = startParts[1].toIntOrNull() ?: -1
                        if (endHour !in 0..23 || endMinute !in 0..59 || startHour !in 0..23 || startMinute !in 0..59) {
                            Log.e("MyAccessibilityService", "Invalid schedule time: ${activeSchedule.effectiveStartTime}-${activeSchedule.effectiveEndTime}")
                        } else if (shouldDeactivateSchedule(currentHour, currentMinute, startHour, startMinute, endHour, endMinute)) {
                            deactivateSchedule(activeSchedule)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error parsing schedule end time", e)
                }
            } else {
                // The active schedule was deleted while the session was running.
                // Clear the dangling reference so focus mode can deactivate and new
                // schedules can activate again.
                Log.w("MyAccessibilityService", "Active schedule $activeScheduleId no longer exists, clearing dangling reference")
                sharedPreferences.edit().remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID).apply()
            }
        }

        // Check if a schedule should start (skip if one is already active).
        // Re-read from SharedPreferences in case deactivateSchedule() just cleared it above.
        if (currentDay == null) return
        if (sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null) return
        schedules.forEach { schedule ->
            try {
                if (schedule.effectiveDays.contains(currentDay)) {
                    val parts = schedule.effectiveStartTime.split(":")
                    if (parts.size == 2) {
                        val startHour = parts[0].toIntOrNull() ?: -1
                        val startMinute = parts[1].toIntOrNull() ?: -1
                        if (startHour !in 0..23 || startMinute !in 0..59) {
                            Log.e("MyAccessibilityService", "Invalid schedule start time: ${schedule.effectiveStartTime}")
                        } else if (startHour == currentHour && startMinute == currentMinute) {
                            activateSchedule(schedule)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing schedule time", e)
            }
        }
    }

    private fun mapCalendarDayToDayOfWeek(calendarDay: Int): DayOfWeek? {
        return when (calendarDay) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    // shouldDeactivateSchedule and isWithinScheduleWindow are top-level functions in ScheduleUtils.kt

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

        sendRitualNotification(
            title = getString(R.string.ritual_started_title),
            message = getString(R.string.ritual_started_message, schedule.name),
            scheduleId = schedule.id,
            isEndNotification = false
        )
    }

    /**
     * Wall-clock millis when [schedule]'s window ends, resolved from "now" at
     * activation: the next occurrence of the end time (tomorrow for overnight
     * schedules). Null if the schedule's end time is malformed.
     */
    private fun computeScheduleEndMillis(schedule: Schedule): Long? {
        val parts = schedule.effectiveEndTime.split(":")
        if (parts.size != 2) return null
        val endHour = parts[0].toIntOrNull() ?: return null
        val endMinute = parts[1].toIntOrNull() ?: return null
        if (endHour !in 0..23 || endMinute !in 0..59) return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun deactivateSchedule(schedule: Schedule) {
        SessionManager.stopSession(this, sharedPreferences, gson)

        sendRitualNotification(
            title = getString(R.string.ritual_ended_title),
            message = getString(R.string.ritual_ended_message, schedule.name),
            scheduleId = schedule.id,
            isEndNotification = true
        )
    }

    /**
     * Generate a stable notification ID from a schedule ID.
     * Uses a more distributed hash to reduce collision risk.
     * Multiplies by a prime and XOR-folds to spread values across the int range.
     * Separate ranges for start (even) and end (odd) notifications.
     */
    private fun getNotificationId(scheduleId: String, isEndNotification: Boolean): Int {
        val hash = scheduleId.fold(0) { acc, c -> acc * 31 + c.code }
        val baseId = (hash and 0x7FFFFFFE) // Ensure positive and even
        return if (isEndNotification) baseId + 1 else baseId
    }

    private fun sendRitualNotification(title: String, message: String, scheduleId: String, isEndNotification: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, Constants.RITUALS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.fplogo_round)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager == null) {
                Log.e("MyAccessibilityService", "NotificationManager unavailable")
                return
            }
            notificationManager.notify(getNotificationId(scheduleId, isEndNotification), builder.build())
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to send ritual notification", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(event)
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

        trackOpenTransition(packageName)

        if (packageName == this.packageName || isLauncher(packageName) || isInputMethod(packageName) || isSystemUI(packageName)) {
            // Going home (or into FocusPocus itself) ends the previous app's
            // continuous-use session — otherwise launcher time keeps counting
            // toward the session limit. SystemUI and the IME stay neutral: a
            // notification-shade pull or the keyboard opening doesn't mean
            // the user left the app beneath.
            if (packageName == this.packageName || isLauncher(packageName)) {
                currentForegroundPackage?.let { sessionCooldownManager.onAppLeft(it) }
                currentForegroundPackage = null
            }
            return
        }

        // End expired breaks / timed sessions before reading focus state below, so
        // blocking resumes (or stops) immediately on an app switch rather than
        // waiting for the next minute tick.
        enforceTimedSessionExpiry()

        // Track which real app is currently in the foreground so the minute-tick receiver can
        // enforce time limits while the user stays inside an app (no window-state events fire then).
        val previousForegroundPackage = currentForegroundPackage
        currentForegroundPackage = packageName

        // Session lifecycle tracking (not debounced — we want accurate start/end times).
        if (previousForegroundPackage != null && previousForegroundPackage != packageName) {
            sessionCooldownManager.onAppLeft(previousForegroundPackage)
        }
        val timeLimitConfigs = getCachedTimeLimitConfigs()
        if (timeLimitConfigs.containsKey(packageName)) {
            sessionCooldownManager.onAppForegrounded(packageName)
        }

        // Pacing notification on app open (not debounced — fires before blocking logic so the
        // user sees the warning even on the first open of the day at a high-usage threshold).
        val pacingLimit = getCachedTimeLimits()[packageName]
        if (pacingLimit != null) checkPacingNotification(packageName, pacingLimit)

        // Debounce — prevent rapid re-triggering when Android fires multiple events
        val now = System.currentTimeMillis()
        if (now - (lastAppBlockTimes[packageName] ?: 0L) < APP_BLOCK_DEBOUNCE_MS) return

        val focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
        val focusActive = focusTagId != null || manualFocusMode
        val nfcLockMode = sharedPreferences.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false)

        // NFC lock mode: block settings apps when focus is active (regardless of break)
        if (focusActive && nfcLockMode && packageName in SETTINGS_PACKAGES) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "NFC Lock: Blocking settings app: $appName")
            lastAppBlockTimes[packageName] = now
            sessionCooldownManager.onAppLeft(packageName)
            currentForegroundPackage = null
            closeApp()
            showOverlay(appName, getString(R.string.service_talisman_lock))
            return
        }

        // Don't block apps during a break (for normal blocking)
        if (isOnBreak) {
            // Still check time limits during breaks
            checkTimeLimitAndBlock(packageName)
            return
        }

        if (focusActive) {
            val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
            val activeBlockerNames = getActiveBlockerNames()
            val activeBlockers = blockerLists.filter { it.name in activeBlockerNames }

            for (blocker in activeBlockers) {
                if (blocker.shouldBlock(packageName)) {
                    val appName = AppUtils.getAppName(this, packageName)
                    if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking app: $appName")
                    lastAppBlockTimes[packageName] = now
                    // Blocking kicks the user out of the app, so its continuous-use
                    // session ends here. Without this the in-session start time
                    // leaks and the next open counts phantom minutes toward the
                    // session limit, tripping an instant false cooldown.
                    sessionCooldownManager.onAppLeft(packageName)
                    currentForegroundPackage = null
                    recordBlockEvent(packageName, blocker.name)
                    closeApp()
                    showOverlay(appName, activeBlockerNames.joinToString(", "))
                    return
                }
            }
        }

        // Per-app time limits (always active, even outside focus mode)
        checkTimeLimitAndBlock(packageName)
    }

    private fun checkTimeLimitAndBlock(packageName: String) {
        // Debounce — prevent rapid re-triggering from both window-state and minute-tick paths
        val now = System.currentTimeMillis()
        if (now - (lastAppBlockTimes[packageName] ?: 0L) < APP_BLOCK_DEBOUNCE_MS) return

        // Per-app entries live in the flat map; apps covered only by a pact group
        // have no entry there, so their pact config supplies the daily cap.
        val limit = getCachedTimeLimits()[packageName]
        val pactConfig = resolvePactConfig(packageName)
        if (limit == null && pactConfig == null) return

        // Active-hours gate: outside its schedule the guard is dormant — no
        // daily cap, no pact gate, no cooldown block. The governing schedule
        // follows the same precedence as the config itself (explicit wins).
        val governingConfig = getCachedTimeLimitConfigs()[packageName] ?: pactConfig
        if (governingConfig != null && !GuardSchedule.isActiveNow(governingConfig)) return

        // 1. Daily limit — always takes precedence; no cooldown interaction.
        // A limit of 0 means "no daily cap" (pacts without a daily backstop).
        val dailyLimit = limit ?: pactConfig?.dailyLimitMinutes ?: 0
        if (dailyLimit > 0 && timeLimitChecker.shouldBlock(packageName, dailyLimit)) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Time limit exceeded: $appName")
            lastAppBlockTimes[packageName] = now
            sessionCooldownManager.onAppLeft(packageName)
            currentForegroundPackage = null
            recordBlockEvent(packageName, "Time Limit")
            closeApp()
            showOverlay(appName, getString(R.string.service_daily_time_limit))
            return
        }

        // 2. Pact Mode: a lapsed allowance seals the app — start the cooldown anchored
        // at the moment the allowance expired, so returning long after the pact ended
        // doesn't restart a full-length seal (it may even have fully elapsed already).
        if (pactConfig != null) {
            val lapsedExpiry = pactManager.takeLapsedAllowance(packageName, now)
            if (lapsedExpiry != null) {
                sessionCooldownManager.startCooldown(packageName, pactConfig, lapsedExpiry)
            }
        }

        // 3. Per-session cooldown — active cooldown blocks the app with escalating friction.
        // Covers both passive session limits and the seal after a lapsed pact.
        if (sessionCooldownManager.isInCooldown(packageName, now)) {
            val cooldownState = sessionCooldownManager.getCooldownState(packageName, now) ?: return
            val newAttemptCount = sessionCooldownManager.recordAttempt(packageName, now)
            val frictionLevel = FrictionLevel.fromAttemptCount(newAttemptCount)
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService",
                "Cooldown active for $appName (attempt #$newAttemptCount, friction=$frictionLevel)")
            lastAppBlockTimes[packageName] = now
            sessionCooldownManager.onAppLeft(packageName)
            currentForegroundPackage = null
            recordBlockEvent(packageName, "Session Cooldown")
            closeApp()
            showCooldownOverlay(appName, frictionLevel, cooldownState.cooldownExpiryMillis)
            return
        }

        // 4. Pact gate: a pact-gated app (per-app config or pact group) with no active
        // allowance is blocked by default — offer to make a pact. With an active
        // allowance the app is simply allowed (the passive session limit below
        // doesn't apply; the pact IS the session limit).
        if (pactConfig != null) {
            if (pactManager.getAllowanceExpiry(packageName, now) != null) return
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Pact gate: $appName blocked by default")
            lastAppBlockTimes[packageName] = now
            sessionCooldownManager.onAppLeft(packageName)
            currentForegroundPackage = null
            recordBlockEvent(packageName, "Pact Gate")
            closeApp()
            showPactOverlay(appName, packageName, pactConfig)
            return
        }

        // 5. Session limit — if the user has been in this app too long, start a cooldown.
        val config = getCachedTimeLimitConfigs()[packageName]
        if (config != null && config.sessionLimitMinutes > 0) {
            val sessionMinutes = sessionCooldownManager.getInSessionMinutes(packageName, now)
            if (sessionMinutes >= config.sessionLimitMinutes) {
                sessionCooldownManager.startCooldown(packageName, config, now)
                val freshState = sessionCooldownManager.getCooldownState(packageName, now) ?: return
                val appName = AppUtils.getAppName(this, packageName)
                if (BuildConfig.DEBUG) Log.d("MyAccessibilityService",
                    "Session limit reached for $appName after ${sessionMinutes}m — cooldown started")
                lastAppBlockTimes[packageName] = now
                currentForegroundPackage = null
                recordBlockEvent(packageName, "Session Cooldown")
                closeApp()
                showCooldownOverlay(appName, FrictionLevel.LEVEL_1, freshState.cooldownExpiryMillis)
            }
        }
    }

    private fun getActiveBlockerNames(): List<String> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing active blockers JSON", e)
                // Fall back to single blocker pref
                val single = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
                if (single != null) listOf(single) else emptyList()
            }
        }
        val single = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }


    private fun getCachedTimeLimits(): Map<String, Int> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.APP_TIME_LIMITS, null)
        if (json == null) {
            if (cachedTimeLimitsJson != null) {
                cachedTimeLimitsJson = null
                cachedTimeLimits = emptyMap()
            }
            return emptyMap()
        }
        if (json == cachedTimeLimitsJson) {
            return cachedTimeLimits
        }
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val parsed: Map<String, Int>? = gson.fromJson(json, type)
            if (parsed == null) {
                cachedTimeLimitsJson = null
                cachedTimeLimits = emptyMap()
                emptyMap()
            } else {
                cachedTimeLimitsJson = json
                cachedTimeLimits = parsed
                timeLimitChecker.clearCache()
                parsed
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing time limits JSON", e)
            cachedTimeLimitsJson = null
            cachedTimeLimits = emptyMap()
            emptyMap()
        }
    }

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
        sharedPreferences.edit().putString(Constants.PrefsKeys.BLOCK_EVENTS, prunedJson).apply()
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Fast O(1) check — discard events from non-browser apps immediately
        if (packageName !in browserPackages) return

        // Resume website blocking promptly if a break expired while browsing.
        enforceTimedSessionExpiry()

        val focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        if (isOnBreak) return
        if (focusTagId == null && !manualFocusMode) return

        val activeBlockerNames = getActiveBlockerNames()
        if (activeBlockerNames.isEmpty()) return
        val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
        val activeBlockers = blockerLists.filter { it.name in activeBlockerNames }

        // Merge websites from all active blockers
        val allBlockedWebsites = activeBlockers.flatMap { it.effectiveWebsites }.distinct()
        if (allBlockedWebsites.isEmpty()) return

        // Debounce — prevent rapid re-triggering
        val now = System.currentTimeMillis()
        if (now - lastWebsiteBlockTime < WEBSITE_BLOCK_DEBOUNCE_MS) return

        val url = extractUrlFromBrowser(packageName, event) ?: return
        val domain = UrlUtils.extractDomain(url) ?: return

        val matchedDomain = allBlockedWebsites.find { domainMatches(domain, it) }
        if (matchedDomain != null) {
            val matchingBlocker = activeBlockers.find { blocker ->
                blocker.effectiveWebsites.any { domainMatches(domain, it) }
            } ?: return
            lastWebsiteBlockTime = now
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking restricted website")
            recordBlockEvent(matchedDomain, matchingBlocker.name)
            closeApp()
            showOverlay(matchedDomain, activeBlockerNames.joinToString(", "))
        }
    }

    private fun extractUrlFromBrowser(packageName: String, event: AccessibilityEvent): String? {
        // Try known URL bar view ID first (fast, targeted lookup)
        val viewId = BROWSER_URL_BAR_IDS[packageName]
        if (viewId != null) {
            val rootNode = rootInActiveWindow ?: return null
            var nodes: List<AccessibilityNodeInfo>? = null
            try {
                nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null && nodes.isNotEmpty()) {
                    val text = nodes[0].text?.toString()
                    if (text != null && UrlUtils.looksLikeUrl(text)) return text
                    return null
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error finding URL bar by ID", e)
            } finally {
                nodes?.forEach { it.recycle() }
                rootNode.recycle()
            }
        }

        // Fallback: walk the node tree looking for URL-like text
        val rootNode = rootInActiveWindow ?: return null
        try {
            return AccessibilityTraverser.findUrlInNodeTree(rootNode, 0)
        } finally {
            rootNode.recycle()
        }
    }

    private fun domainMatches(navigatedDomain: String, blockedDomain: String): Boolean {
        if (blockedDomain.isEmpty() || navigatedDomain.isEmpty()) return false
        if (blockedDomain.length > 255 || navigatedDomain.length > 2048) return false

        val navLen = navigatedDomain.length
        val blockedLen = blockedDomain.length

        if (navLen < blockedLen) return false

        if (navLen == blockedLen) {
            return navigatedDomain.regionMatches(0, blockedDomain, 0, blockedLen, ignoreCase = true)
        }

        // navLen > blockedLen
        // Check for ending with ".$blockedDomain"
        val offset = navLen - blockedLen
        // The character before the match must be a dot
        if (offset < 1 || navigatedDomain[offset - 1] != '.') return false

        return navigatedDomain.regionMatches(offset, blockedDomain, 0, blockedLen, ignoreCase = true)
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

    private fun closeApp() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun getCachedTimeLimitConfigs(): Map<String, AppTimeLimit> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS, null)
        if (json == null) {
            if (cachedTimeLimitConfigsJson != null) {
                cachedTimeLimitConfigsJson = null
                cachedTimeLimitConfigs = emptyMap()
            }
            return emptyMap()
        }
        if (json == cachedTimeLimitConfigsJson) return cachedTimeLimitConfigs
        return try {
            val type = object : TypeToken<Map<String, AppTimeLimit>>() {}.type
            val parsed: Map<String, AppTimeLimit>? = gson.fromJson(json, type)
            if (parsed == null) {
                cachedTimeLimitConfigsJson = null
                cachedTimeLimitConfigs = emptyMap()
                emptyMap()
            } else {
                cachedTimeLimitConfigsJson = json
                cachedTimeLimitConfigs = parsed
                parsed
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing time limit configs JSON")
            cachedTimeLimitConfigsJson = null
            cachedTimeLimitConfigs = emptyMap()
            emptyMap()
        }
    }

    private fun showOverlay(appName: String, spellName: String? = null) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("spellName", spellName)
        startActivity(intent)
    }

    /**
     * Fires a one-time toast when daily usage of [packageName] crosses 50%, 75%, or 90% of
     * [limitMinutes]. Each threshold is shown at most once per day per app.
     */
    private fun checkPacingNotification(packageName: String, limitMinutes: Int) {
        if (limitMinutes <= 0) return
        // All thresholds announced — no reason to query usage again today.
        if (pacingNotifiedToday[packageName]?.containsAll(listOf(50, 75, 90)) == true) return
        // Throttle the underlying full-day UsageStats scan (a main-thread binder
        // call) to once a minute per app; the minute tick re-checks the foreground
        // app anyway, so a threshold crossing is announced within a minute.
        val nowMs = System.currentTimeMillis()
        if (nowMs - (lastPacingCheckTimes[packageName] ?: 0L) < 60_000L) return
        lastPacingCheckTimes[packageName] = nowMs
        val usedMinutes = AppTimeLimitManager.getUsedMinutesToday(this, packageName)
        if (usedMinutes <= 0) return
        val percentUsed = (usedMinutes * 100) / limitMinutes
        val notified = pacingNotifiedToday.getOrPut(packageName) { mutableSetOf() }
        for (threshold in intArrayOf(90, 75, 50)) {
            if (percentUsed >= threshold && threshold !in notified) {
                // Crossing a threshold retires the lower ones too — otherwise
                // a jump straight past 90% replays the stale "75%" and
                // "halfway" toasts on the following minutes.
                notified.addAll(listOf(90, 75, 50).filter { it <= threshold })
                val appName = AppUtils.getAppName(this, packageName)
                val remaining = (limitMinutes - usedMinutes).coerceAtLeast(0)
                showPacingToast(appName, usedMinutes, limitMinutes, remaining, threshold)
                break // One toast at a time; higher thresholds take priority
            }
        }
    }

    private fun showPacingToast(
        appName: String,
        usedMinutes: Int,
        limitMinutes: Int,
        remainingMinutes: Int,
        threshold: Int
    ) {
        val message = when {
            threshold >= 90 -> getString(
                R.string.pacing_toast_90, appName, usedMinutes, limitMinutes
            )
            threshold >= 75 -> getString(
                R.string.pacing_toast_75, appName, usedMinutes, limitMinutes, remainingMinutes
            )
            else -> getString(
                R.string.pacing_toast_50, appName, usedMinutes, limitMinutes
            )
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCooldownOverlay(
        appName: String,
        frictionLevel: FrictionLevel,
        cooldownExpiryMillis: Long
    ) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("frictionLevel", frictionLevel.ordinal)
        intent.putExtra("cooldownExpiryMillis", cooldownExpiryMillis)
        startActivity(intent)
    }

    private fun showPactOverlay(appName: String, packageName: String, config: AppTimeLimit) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("pactPackageName", packageName)
        intent.putExtra("pactChoices", PactManager.choicesFor(config).toIntArray())
        intent.putExtra("pactSealMinutes", config.cooldownMinutes)

        // Awareness counter: this attempt is already counted, so opens is "open #N".
        val stats = openReflexTracker.getStats(packageName)
        intent.putExtra("pactTodayOpens", stats.opens)
        intent.putExtra("pactTodayReflexOpens", stats.reflexOpens)

        // Optional healthier substitute ("Open X instead")
        val alternative = config.pactAlternativePackage
        if (alternative != null && isPackageInstalled(alternative)) {
            intent.putExtra("pactAlternativePackage", alternative)
            intent.putExtra("pactAlternativeName", AppUtils.getAppName(this, alternative))
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

    /** Apps whose opens/reflexes we count: any with a config or in a pact group. */
    private fun isOpenTracked(packageName: String): Boolean =
        getCachedTimeLimitConfigs().containsKey(packageName) ||
            getPactGroupConfigs().containsKey(packageName)

    /**
     * The pact settings governing [packageName], if any. An explicit per-app config
     * wins outright: a pact config applies as itself, and a plain limit config means
     * the app is limit-managed and never group-gated. Otherwise membership in a
     * pact group's enchantment applies.
     */
    private fun resolvePactConfig(packageName: String): AppTimeLimit? {
        val config = getCachedTimeLimitConfigs()[packageName]
        if (config != null) return if (config.pactModeEnabled) config else null
        return getPactGroupConfigs()[packageName]
    }

    private fun getPactGroupConfigs(): Map<String, AppTimeLimit> {
        val groupsJson = sharedPreferences.getString(Constants.PrefsKeys.PACT_GROUPS, null)
        val blockersJson = sharedPreferences.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)
        val key = groupsJson to blockersJson
        if (key == pactGroupCacheKey) return cachedPactGroupConfigs

        val result = mutableMapOf<String, AppTimeLimit>()
        if (groupsJson != null) {
            val groups = pactManager.getGroups()
            if (groups.isNotEmpty()) {
                val blockers = BlockerRepository.getBlockers(sharedPreferences)
                for (group in groups) {
                    val blocker = blockers.find { it.name == group.blockerName } ?: continue
                    for (pkg in blocker.effectiveApps) {
                        if (pkg !in result) result[pkg] = group.toAppTimeLimit(pkg)
                    }
                }
            }
        }
        pactGroupCacheKey = key
        cachedPactGroupConfigs = result
        return result
    }

    /**
     * Fires a one-time toast when the foreground app's pact allowance is about to
     * lapse, so the seal doesn't land mid-scroll without warning.
     */
    private fun maybeWarnPactEnding(packageName: String) {
        resolvePactConfig(packageName) ?: return
        val expiry = pactManager.getAllowanceExpiry(packageName) ?: return
        val remainingMs = expiry - System.currentTimeMillis()
        if (remainingMs in 1..90_000 && pactWarnedExpiries[packageName] != expiry) {
            pactWarnedExpiries[packageName] = expiry
            val appName = AppUtils.getAppName(this, packageName)
            val message = getString(R.string.pact_ending_toast, appName)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Posts the opt-in "seal lifted" notification for seals that expired within
     * the last few minutes. Reads the cooldown store without pruning it, and
     * remembers what it announced so each lift is reported at most once.
     */
    private fun maybeNotifySealsLifted(now: Long = System.currentTimeMillis()) {
        if (!sharedPreferences.getBoolean(Constants.PrefsKeys.SEAL_LIFTED_ALERTS_ENABLED, false)) return
        val recentWindowMs = 10 * 60 * 1000L
        val lifted = sessionCooldownManager.peekAllCooldowns().filterValues { state ->
            state.cooldownExpiryMillis in (now - recentWindowMs) until now &&
                sealLiftNotified[state.packageName] != state.cooldownExpiryMillis
        }
        if (lifted.isEmpty()) return
        lifted.values.forEach { sealLiftNotified[it.packageName] = it.cooldownExpiryMillis }
        GuardNotifier.postSealsLifted(
            this, sharedPreferences,
            lifted.keys.map { AppUtils.getAppName(this, it) }.sorted()
        )
    }

    /**
     * Seals every app whose pact allowance has lapsed, anchored at the moment the
     * allowance expired (checkTimeLimitAndBlock's rule, applied proactively). An
     * app whose pact config has been removed just gets its stale allowance
     * dropped. takeLapsedAllowance is take-once, so racing the open-attempt path
     * can't double-start a cooldown.
     */
    private fun sealLapsedPacts(now: Long = System.currentTimeMillis()) {
        pactManager.getLapsedAllowances(now).keys.forEach { pkg ->
            val lapsedExpiry = pactManager.takeLapsedAllowance(pkg, now) ?: return@forEach
            val config = resolvePactConfig(pkg) ?: return@forEach
            sessionCooldownManager.startCooldown(pkg, config, lapsedExpiry)
        }
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }
}
