package com.infinicada.focuspocus

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FocusNotificationListenerService : NotificationListenerService() {

    private val gson = Gson()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        val muteBlockedNotifications = prefs.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)
        if (!muteBlockedNotifications) return

        val manualFocusMode = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val focusTagId = prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val focusActive = manualFocusMode || focusTagId != null
        val isOnBreak = prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        if (!focusActive || isOnBreak) return

        val activeBlockerNames = getActiveBlockerNames(prefs)
        if (activeBlockerNames.isEmpty()) return

        val blockerLists = BlockerRepository.getBlockers(prefs)
        val activeBlockers = blockerLists.filter { it.name in activeBlockerNames }
        val pkg = sbn.packageName

        // Never cancel our own notifications or Android system notifications
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        if (activeBlockers.any { it.shouldBlock(pkg) }) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e("FocusNotifListener", "Error cancelling notification", e)
            }
        }
    }

    private fun getActiveBlockerNames(prefs: android.content.SharedPreferences): List<String> {
        val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("FocusNotifListener", "Error parsing active blockers JSON")
                // Fall back to single blocker pref
                val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
                if (single != null) listOf(single) else emptyList()
            }
        }
        val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
        return if (single != null) listOf(single) else emptyList()
    }
}
