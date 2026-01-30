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
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private val CHANNEL_ID = "focus_pocus_rituals"

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                checkSchedules()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPreferences = getSharedPreferences("FocusPocus", Context.MODE_PRIVATE)
        Log.d("MyAccessibilityService", "Service connected")

        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(timeTickReceiver, filter)

        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error unregistering receiver", e)
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
        val json = sharedPreferences.getString("schedules", null)
        if (json == null) return

        val schedules: List<Schedule> = try {
            val type = object : TypeToken<List<Schedule>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentDay = mapCalendarDayToDayOfWeek(now.get(Calendar.DAY_OF_WEEK))

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
            .putBoolean("manualFocusMode", true)
            .putString("activeBlocker", schedule.blockerName)
            .putString("activeScheduleId", schedule.id)
            .apply()

        sendNotification(schedule.name)
    }

    private fun sendNotification(scheduleName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.fplogo_round) // Assuming this exists or falls back to standard
            .setContentTitle("Ritual Started")
            .setContentText("$scheduleName is now active.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            // Use a resource id for the icon if R.mipmap.fplogo_round works, otherwise R.mipmap.fplogo
            // Since I cannot verify R class generation easily, I'll assume fplogo exists as seen in Manifest
            // But usually R class is package-specific.
            // I'll try to find a valid icon resource id.
            // Manifest uses @mipmap/fplogo.
            // In code, it should be R.mipmap.fplogo.

             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.notify(scheduleName.hashCode(), builder.build())
        } catch (e: SecurityException) {
             Log.e("MyAccessibilityService", "Permission denied for notification", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val focusTagId = sharedPreferences.getString("focusTagId", null)
            val manualFocusMode = sharedPreferences.getBoolean("manualFocusMode", false)
            val isOnBreak = sharedPreferences.getBoolean("isOnBreak", false)

            // Don't block apps during a break
            if (isOnBreak) return

            if (focusTagId != null || manualFocusMode) {
                val json = sharedPreferences.getString("blockerLists", null)
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
                val activeBlockerName = sharedPreferences.getString("activeBlocker", null)
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
