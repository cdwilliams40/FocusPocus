package com.infinicada.focuspocus

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_BREAK_ENDED = "com.infinicada.focuspocus.ACTION_BREAK_ENDED"
        const val ACTION_FOCUS_SESSION_ENDED = "com.infinicada.focuspocus.ACTION_FOCUS_SESSION_ENDED"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private val CHANNEL_ID = "focus_pocus_rituals"
    private var receiverRegistered = false

    // Cache for parsed schedules to avoid re-parsing JSON every minute
    private var cachedSchedulesJson: String? = null
    private var cachedSchedules: List<Schedule> = emptyList()

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
    }

    private fun createNotificationChannel() {
        val name = "Rituals"
        val descriptionText = "Notifications for scheduled rituals"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
                // Update cache
                cachedSchedulesJson = json
                cachedSchedules = parsed
                parsed
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error parsing schedules JSON: ${e.message}", e)
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

        // Check if an active schedule has ended
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeScheduleId != null) {
            val activeSchedule = schedules.find { it.id == activeScheduleId }
            if (activeSchedule != null) {
                try {
                    val endParts = activeSchedule.endTime.split(":")
                    if (endParts.size == 2) {
                        val endHour = endParts[0].toInt()
                        val endMinute = endParts[1].toInt()

                        if (endHour == currentHour && endMinute == currentMinute) {
                            deactivateSchedule(activeSchedule)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MyAccessibilityService", "Error parsing schedule end time", e)
                }
            }
        }

        // Check if a schedule should start
        if (currentDay == null) return
        schedules.forEach { schedule ->
            if (schedule.days.contains(currentDay)) {
                try {
                    val parts = schedule.startTime.split(":")
                    if (parts.size == 2) {
                        val startHour = parts[0].toInt()
                        val startMinute = parts[1].toInt()

                        if (startHour == currentHour && startMinute == currentMinute) {
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

    private fun activateSchedule(schedule: Schedule) {
        // Activate Focus Mode
        sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, schedule.blockerName)
            .putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, schedule.id)
            .apply()

        sendNotification(schedule.name, schedule.id)
    }

    private fun deactivateSchedule(schedule: Schedule) {
        // Deactivate Focus Mode
        sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            .remove(Constants.PrefsKeys.ACTIVE_BLOCKER)
            .remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            .remove(Constants.PrefsKeys.FOCUS_TAG_ID)
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            .apply()

        sendEndNotification(schedule.name, schedule.id)
    }

    /**
     * Generate a stable notification ID from a schedule ID.
     * Uses absolute value to ensure positive ID, with separate ranges for start (even) and end (odd) notifications.
     */
    private fun getNotificationId(scheduleId: String, isEndNotification: Boolean): Int {
        val baseId = (scheduleId.hashCode() and 0x7FFFFFFF) / 2 * 2  // Make it even
        return if (isEndNotification) baseId + 1 else baseId
    }

    private fun sendEndNotification(scheduleName: String, scheduleId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val iconResId = try {
            R.mipmap.fplogo_round
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentTitle("Ritual Ended")
            .setContentText("$scheduleName has ended.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(getNotificationId(scheduleId, isEndNotification = true), builder.build())
        } catch (e: SecurityException) {
            Log.e("MyAccessibilityService", "Permission denied for notification", e)
        }
    }

    private fun sendNotification(scheduleName: String, scheduleId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val iconResId = try {
            R.mipmap.fplogo_round
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentTitle("Ritual Started")
            .setContentText("$scheduleName is now active.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(getNotificationId(scheduleId, isEndNotification = false), builder.build())
        } catch (e: SecurityException) {
            Log.e("MyAccessibilityService", "Permission denied for notification", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
            val manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

            // Don't block apps during a break
            if (isOnBreak) return

            if (focusTagId != null || manualFocusMode) {
                val json = sharedPreferences.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)
                val blockerLists: List<Blocker> = if (json != null) {
                    try {
                        val type = object : TypeToken<List<Blocker>>() {}.type
                        gson.fromJson(json, type)
                    } catch (e: Exception) {
                        Log.e("MyAccessibilityService", "Error parsing blocker lists", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Always use the activeBlocker selected in the app (Home screen), regardless of how focus was triggered (Tag or Manual)
                val activeBlockerName = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
                val activeBlocker = blockerLists.find { it.name == activeBlockerName }

                activeBlocker?.let {
                    val packageName = event.packageName?.toString()
                    if (packageName != null && packageName != this.packageName && !isLauncher(packageName) && !isInputMethod(packageName) && !isSystemUI(packageName) && shouldBlockApp(packageName, it)) {
                        val appName = getAppName(packageName)
                        Log.d("MyAccessibilityService", "Blocking app: $appName")
                        closeApp()
                        showOverlay(appName, it.name)
                    }
                }
            }
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo != null && resolveInfo.activityInfo.packageName == packageName
    }

    private fun isInputMethod(packageName: String): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isSystemUI(packageName: String): Boolean {
        return packageName == "com.android.systemui" || packageName == "android"
    }

    private fun shouldBlockApp(packageName: String, blocker: Blocker): Boolean {
        return when (blocker.mode) {
            BlockerMode.BLACKLIST -> blocker.apps.contains(packageName)
            BlockerMode.WHITELIST -> !blocker.apps.contains(packageName)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
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
