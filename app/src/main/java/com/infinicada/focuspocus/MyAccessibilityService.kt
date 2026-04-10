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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.ConditionalUnlock
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
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error updating browser packages", e)
            }
        }
    }

    private fun updateBrowserPackages() {
        browserPackages = BrowserDetector(this).getBrowserPackages()
        Log.d("MyAccessibilityService", "Updated browser packages: $browserPackages")
    }

    // Cache for parsed schedules to avoid re-parsing JSON every minute
    @Volatile private var cachedSchedulesJson: String? = null
    @Volatile private var cachedSchedules: List<Schedule> = emptyList()

    // Debounce for website blocking to prevent rapid re-triggering
    @Volatile private var lastWebsiteBlockTime: Long = 0

    // Debounce for app blocking to prevent rapid re-triggering
    @Volatile private var lastAppBlockTime: Long = 0

    // Most recently focused "real" app (updated on every window state change, before debounce)
    @Volatile private var currentForegroundPackage: String? = null

    // Cache for time limits
    @Volatile private var cachedTimeLimitsJson: String? = null
    @Volatile private var cachedTimeLimits: Map<String, Int> = emptyMap()

    // Cache for conditional unlocks
    @Volatile private var cachedConditionalUnlocksJson: String? = null
    @Volatile private var cachedConditionalUnlocks: List<ConditionalUnlock> = emptyList()

    // Block event recording
    private val pendingBlockEvents = mutableListOf<BlockEvent>()
    @Volatile private var lastBlockEventWriteTime: Long = 0

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
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                checkSchedules()
                // Also enforce time limits on whichever app is currently in the foreground.
                // TYPE_WINDOW_STATE_CHANGED only fires on app switches, so without this a user
                // could stay inside an app past its daily limit indefinitely.
                currentForegroundPackage?.let { checkTimeLimitAndBlock(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        Log.d("MyAccessibilityService", "Service connected")

        // Activate any schedule that should currently be running but wasn't started
        // (e.g. the service restarted mid-schedule after a phone reboot or crash).
        checkMissedScheduleActivation()

        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
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
            val name = "Rituals"
            val descriptionText = "Notifications for scheduled rituals"
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

                val inWindow = when {
                    // Same-day schedule active today
                    schedule.effectiveDays.contains(currentDay) ->
                        isWithinScheduleWindow(currentHour, currentMinute, startHour, startMinute, endHour, endMinute)

                    // Overnight schedule that started yesterday and carries over into today
                    previousDay != null && schedule.effectiveDays.contains(previousDay) -> {
                        val startMins = startHour * 60 + startMinute
                        val endMins = endHour * 60 + endMinute
                        val currentMins = currentHour * 60 + currentMinute
                        // Overnight = end is before start (wraps past midnight).
                        // We're in the carry-over window when it's still before the end time.
                        endMins < startMins && currentMins < endMins
                    }

                    else -> false
                }

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
                Log.e("MyAccessibilityService", "Error parsing schedules JSON")
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

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerNames = validNames,
            scheduleId = schedule.id,
            breaksEnabled = schedule.breaksEnabled
        )

        DndController.updateDndState(this)

        sendRitualNotification(
            title = getString(R.string.ritual_started_title),
            message = getString(R.string.ritual_started_message, schedule.name),
            scheduleId = schedule.id,
            isEndNotification = false
        )
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

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(event)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || isLauncher(packageName) || isInputMethod(packageName) || isSystemUI(packageName)) return

        // Track which real app is currently in the foreground so the minute-tick receiver can
        // enforce time limits while the user stays inside an app (no window-state events fire then).
        currentForegroundPackage = packageName

        // Debounce — prevent rapid re-triggering when Android fires multiple events
        val now = System.currentTimeMillis()
        if (now - lastAppBlockTime < APP_BLOCK_DEBOUNCE_MS) return

        val focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
        val focusActive = focusTagId != null || manualFocusMode
        val nfcLockMode = sharedPreferences.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false)

        // NFC lock mode: block settings apps when focus is active (regardless of break)
        if (focusActive && nfcLockMode && packageName in SETTINGS_PACKAGES) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "NFC Lock: Blocking settings app: $appName")
            lastAppBlockTime = now
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
                if (blocker.shouldBlock(packageName) && !isConditionallyUnlocked(blocker.name)) {
                    val appName = AppUtils.getAppName(this, packageName)
                    if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking app: $appName")
                    lastAppBlockTime = now
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
        if (now - lastAppBlockTime < APP_BLOCK_DEBOUNCE_MS) return

        val timeLimits = getCachedTimeLimits()
        val limit = timeLimits[packageName] ?: return
        if (timeLimitChecker.shouldBlock(packageName, limit)) {
            if (isTimeLimitConditionallyUnlocked(packageName)) return
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Time limit exceeded: $appName")
            lastAppBlockTime = now
            currentForegroundPackage = null
            recordBlockEvent(packageName, "Time Limit")
            closeApp()
            showOverlay(appName, getString(R.string.service_daily_time_limit))
        }
    }

    private fun isConditionallyUnlocked(blockerName: String): Boolean {
        val rules = getCachedConditionalUnlocks()

        return rules.any { rule ->
            if (blockerName !in rule.effectiveUnlockedBlockerNames) return@any false
            if (rule.requiredMinutes <= 0) return@any false
            val usedMs = UsageStatsHelper.getPackageUsageToday(this, rule.requiredAppPackage)
            val requiredMs = rule.requiredMinutes.toLong() * 60 * 1000
            val unlocked = usedMs >= requiredMs
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService",
                "Conditional unlock check for blocker=$blockerName: ${usedMs / 60000}m / ${rule.requiredMinutes}m required in ${rule.requiredAppPackage} -> unlocked=$unlocked")
            unlocked
        }
    }

    private fun isTimeLimitConditionallyUnlocked(packageName: String): Boolean {
        val rules = getCachedConditionalUnlocks()

        return rules.any { rule ->
            if (packageName !in rule.effectiveUnlockedTimeLimitApps) return@any false
            if (rule.requiredMinutes <= 0) return@any false
            val usedMs = UsageStatsHelper.getPackageUsageToday(this, rule.requiredAppPackage)
            val requiredMs = rule.requiredMinutes.toLong() * 60 * 1000
            val unlocked = usedMs >= requiredMs
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService",
                "Conditional unlock check for time-limit app=$packageName: ${usedMs / 60000}m / ${rule.requiredMinutes}m required in ${rule.requiredAppPackage} -> unlocked=$unlocked")
            unlocked
        }
    }

    private fun getActiveBlockerNames(): List<String> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing active blockers JSON")
                // Fall back to single blocker pref
                val single = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
                if (single != null) listOf(single) else emptyList()
            }
        }
        val single = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun getCachedConditionalUnlocks(): List<ConditionalUnlock> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.CONDITIONAL_UNLOCKS, null)
        if (json == null) {
            if (cachedConditionalUnlocksJson != null) {
                cachedConditionalUnlocksJson = null
                cachedConditionalUnlocks = emptyList()
            }
            return emptyList()
        }
        if (json == cachedConditionalUnlocksJson) return cachedConditionalUnlocks
        return try {
            val type = object : TypeToken<List<ConditionalUnlock>>() {}.type
            val parsed: List<ConditionalUnlock>? = gson.fromJson(json, type)
            if (parsed == null) {
                cachedConditionalUnlocksJson = null
                cachedConditionalUnlocks = emptyList()
                emptyList()
            } else {
                cachedConditionalUnlocksJson = json
                cachedConditionalUnlocks = parsed
                parsed
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error parsing conditional unlocks JSON")
            cachedConditionalUnlocksJson = null
            cachedConditionalUnlocks = emptyList()
            emptyList()
        }
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
            cachedTimeLimitsJson = null
            cachedTimeLimits = emptyMap()
            emptyMap()
        }
    }

    private fun recordBlockEvent(packageName: String, blockerName: String) {
        synchronized(pendingBlockEvents) {
            pendingBlockEvents.add(BlockEvent(packageName, System.currentTimeMillis(), blockerName))
        }
        val now = System.currentTimeMillis()
        if (now - lastBlockEventWriteTime > 1000) {
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
        val existing: MutableList<BlockEvent> = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<BlockEvent>>() {}.type
                gson.fromJson<MutableList<BlockEvent>>(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing block events JSON")
                mutableListOf()
            }
        } else mutableListOf()
        existing.addAll(eventsToWrite)
        val pruned = if (existing.size > Constants.MAX_BLOCK_EVENTS) {
            existing.drop(existing.size - Constants.MAX_BLOCK_EVENTS)
        } else existing
        sharedPreferences.edit().putString(Constants.PrefsKeys.BLOCK_EVENTS, gson.toJson(pruned)).apply()
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Fast O(1) check — discard events from non-browser apps immediately
        if (packageName !in browserPackages) return

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
        val allBlockedWebsites = activeBlockers.flatMap { it.websites.orEmpty() }.distinct()
        if (allBlockedWebsites.isEmpty()) return

        // Debounce — prevent rapid re-triggering
        val now = System.currentTimeMillis()
        if (now - lastWebsiteBlockTime < WEBSITE_BLOCK_DEBOUNCE_MS) return

        val url = extractUrlFromBrowser(packageName, event) ?: return
        val domain = UrlUtils.extractDomain(url) ?: return

        val matchedDomain = allBlockedWebsites.find { domainMatches(domain, it) }
        if (matchedDomain != null) {
            lastWebsiteBlockTime = now
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking restricted website")
            val matchingBlockerName = activeBlockers.find { blocker ->
                blocker.websites.orEmpty().any { domainMatches(domain, it) }
            }?.name ?: activeBlockerNames.first()
            recordBlockEvent(matchedDomain, matchingBlockerName)
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

    private fun showOverlay(appName: String, spellName: String? = null) {
        val intent = Intent(this, OverlayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("appName", appName)
        intent.putExtra("spellName", spellName)
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Service interrupted")
    }
}
