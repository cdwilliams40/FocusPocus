package com.infinicada.focuspocus

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
            updateBrowserPackages()
        }
    }

    private fun updateBrowserPackages() {
        browserPackages = BrowserDetector(this).getBrowserPackages()
        Log.d("MyAccessibilityService", "Updated browser packages: $browserPackages")
    }

    // Cache for parsed schedules to avoid re-parsing JSON every minute
    private var cachedSchedulesJson: String? = null
    private var cachedSchedules: List<Schedule> = emptyList()

    // Debounce for website blocking to prevent rapid re-triggering
    private var lastWebsiteBlockTime: Long = 0

    // Debounce for app blocking to prevent rapid re-triggering
    private var lastAppBlockTime: Long = 0

    // Cache for time limits
    private var cachedTimeLimitsJson: String? = null
    private var cachedTimeLimits: Map<String, Int> = emptyMap()

    // Block event recording
    private var pendingBlockEvents = mutableListOf<BlockEvent>()
    private var lastBlockEventWriteTime: Long = 0

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
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        Log.d("MyAccessibilityService", "Service connected")

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
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED)
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
                val parsed: List<Schedule> = gson.fromJson(json, type)
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
                    val endParts = activeSchedule.endTime.split(":")
                    val startParts = activeSchedule.startTime.split(":")
                    if (endParts.size == 2 && startParts.size == 2) {
                        val endHour = endParts[0].toIntOrNull() ?: -1
                        val endMinute = endParts[1].toIntOrNull() ?: -1
                        val startHour = startParts[0].toIntOrNull() ?: -1
                        val startMinute = startParts[1].toIntOrNull() ?: -1
                        if (endHour !in 0..23 || endMinute !in 0..59 || startHour !in 0..23 || startMinute !in 0..59) {
                            Log.e("MyAccessibilityService", "Invalid schedule time: ${activeSchedule.startTime}-${activeSchedule.endTime}")
                        } else if (shouldDeactivateSchedule(currentHour, currentMinute, startHour, startMinute, endHour, endMinute)) {
                            deactivateSchedule(activeSchedule)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error parsing schedule end time", e)
                }
            }
        }

        // Check if a schedule should start (skip if one is already active)
        if (currentDay == null) return
        if (activeScheduleId != null) return
        schedules.forEach { schedule ->
            if (schedule.days.contains(currentDay)) {
                try {
                    val parts = schedule.startTime.split(":")
                    if (parts.size == 2) {
                        val startHour = parts[0].toIntOrNull() ?: -1
                        val startMinute = parts[1].toIntOrNull() ?: -1
                        if (startHour !in 0..23 || startMinute !in 0..59) {
                            Log.e("MyAccessibilityService", "Invalid schedule start time: ${schedule.startTime}")
                        } else if (startHour == currentHour && startMinute == currentMinute) {
                            activateSchedule(schedule)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error parsing schedule time", e)
                }
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

    /**
     * Determines if the schedule should deactivate at the current time.
     * Handles both same-day schedules (start < end) and overnight schedules (start > end).
     * Uses "at or past" logic so missed end times are caught on service restart.
     */
    internal fun shouldDeactivateSchedule(
        currentHour: Int, currentMinute: Int,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ): Boolean {
        val currentMins = currentHour * 60 + currentMinute
        val startMins = startHour * 60 + startMinute
        val endMins = endHour * 60 + endMinute

        return if (endMins > startMins) {
            // Same-day schedule: deactivate at or past end time
            currentMins >= endMins
        } else {
            // Overnight schedule: deactivate when past end time AND before start time
            // (i.e., in the "morning after" window, not the "active evening" window)
            currentMins >= endMins && currentMins < startMins
        }
    }

    private fun activateSchedule(schedule: Schedule) {
        // Validate that the schedule's blocker still exists
        val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
        if (blockerLists.none { it.name == schedule.blockerName }) {
            Log.e("MyAccessibilityService", "Schedule '${schedule.name}' references missing blocker '${schedule.blockerName}', skipping activation")
            return
        }

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerName = schedule.blockerName,
            scheduleId = schedule.id,
            breaksEnabled = schedule.breaksEnabled
        )

        sendRitualNotification(
            title = "Ritual Started",
            message = "${schedule.name} is now active.",
            scheduleId = schedule.id,
            isEndNotification = false
        )
    }

    private fun deactivateSchedule(schedule: Schedule) {
        SessionManager.stopSession(this, sharedPreferences, gson)

        sendRitualNotification(
            title = "Ritual Ended",
            message = "${schedule.name} has ended.",
            scheduleId = schedule.id,
            isEndNotification = true
        )
    }

    /**
     * Generate a stable notification ID from a schedule ID.
     * Uses absolute value to ensure positive ID, with separate ranges for start (even) and end (odd) notifications.
     */
    private fun getNotificationId(scheduleId: String, isEndNotification: Boolean): Int {
        val baseId = (scheduleId.hashCode() and 0x7FFFFFFF) / 2 * 2  // Make it even
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
            closeApp()
            showOverlay(appName, "Talisman Lock")
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
            val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
            val activeBlocker = blockerLists.find { it.name == activeBlockerName }

            activeBlocker?.let {
                if (it.shouldBlock(packageName)) {
                    val appName = AppUtils.getAppName(this, packageName)
                    if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking app: $appName")
                    lastAppBlockTime = now
                    recordBlockEvent(packageName, it.name)
                    closeApp()
                    showOverlay(appName, it.name)
                    return
                }
            }
        }

        // Per-app time limits (always active, even outside focus mode)
        checkTimeLimitAndBlock(packageName)
    }

    private fun checkTimeLimitAndBlock(packageName: String) {
        val timeLimits = getCachedTimeLimits()
        val limit = timeLimits[packageName] ?: return
        if (timeLimitChecker.shouldBlock(packageName, limit)) {
            val appName = AppUtils.getAppName(this, packageName)
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Time limit exceeded: $appName")
            lastAppBlockTime = System.currentTimeMillis()
            recordBlockEvent(packageName, "Time Limit")
            closeApp()
            showOverlay(appName, "Daily Time Limit Reached")
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
            val parsed: Map<String, Int> = gson.fromJson(json, type)
            cachedTimeLimitsJson = json
            cachedTimeLimits = parsed
            parsed
        } catch (e: Exception) {
            cachedTimeLimitsJson = null
            cachedTimeLimits = emptyMap()
            emptyMap()
        }
    }

    private fun recordBlockEvent(packageName: String, blockerName: String) {
        pendingBlockEvents.add(BlockEvent(packageName, System.currentTimeMillis(), blockerName))
        val now = System.currentTimeMillis()
        if (now - lastBlockEventWriteTime > 1000) {
            flushBlockEvents()
        }
    }

    private fun flushBlockEvents() {
        if (pendingBlockEvents.isEmpty()) return
        lastBlockEventWriteTime = System.currentTimeMillis()
        val json = sharedPreferences.getString(Constants.PrefsKeys.BLOCK_EVENTS, null)
        val existing: MutableList<BlockEvent> = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<BlockEvent>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) { mutableListOf() }
        } else mutableListOf()
        existing.addAll(pendingBlockEvents)
        pendingBlockEvents.clear()
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

        val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: return
        val blockerLists = BlockerRepository.getBlockers(sharedPreferences)
        val activeBlocker = blockerLists.find { it.name == activeBlockerName } ?: return

        val blockedWebsites = activeBlocker.websites.orEmpty()
        if (blockedWebsites.isEmpty()) return

        // Debounce — prevent rapid re-triggering
        val now = System.currentTimeMillis()
        if (now - lastWebsiteBlockTime < WEBSITE_BLOCK_DEBOUNCE_MS) return

        val url = extractUrlFromBrowser(packageName, event) ?: return
        val domain = UrlUtils.extractDomain(url) ?: return

        val matchedDomain = blockedWebsites.find { domainMatches(domain, it) }
        if (matchedDomain != null) {
            lastWebsiteBlockTime = now
            if (BuildConfig.DEBUG) Log.d("MyAccessibilityService", "Blocking restricted website")
            recordBlockEvent(matchedDomain, activeBlocker.name)
            closeApp()
            showOverlay(matchedDomain, activeBlocker.name)
        }
    }

    private fun extractUrlFromBrowser(packageName: String, event: AccessibilityEvent): String? {
        // Try known URL bar view ID first (fast, targeted lookup)
        val viewId = BROWSER_URL_BAR_IDS[packageName]
        if (viewId != null) {
            val rootNode = rootInActiveWindow ?: return null
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null && nodes.isNotEmpty()) {
                    val text = nodes[0].text?.toString()
                    nodes.forEach { it.recycle() }
                    rootNode.recycle()
                    if (text != null && UrlUtils.looksLikeUrl(text)) return text
                    return null
                }
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error finding URL bar by ID", e)
            }
            rootNode.recycle()
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
        if (navigatedDomain[offset - 1] != '.') return false

        return navigatedDomain.regionMatches(offset, blockedDomain, 0, blockedLen, ignoreCase = true)
    }

    private var cachedLauncherPackageName: String? = null

    private fun isLauncher(packageName: String): Boolean {
        if (cachedLauncherPackageName == null) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            cachedLauncherPackageName = resolveInfo?.activityInfo?.packageName
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
