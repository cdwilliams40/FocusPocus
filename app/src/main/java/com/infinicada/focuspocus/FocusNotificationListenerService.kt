package com.infinicada.focuspocus

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
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

        val focusActive = SessionManager.isFocusActive(prefs)
        val isOnBreak = prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        if (!focusActive || isOnBreak) return

        val activeBlockerNames = getActiveBlockerNames(prefs)
        if (activeBlockerNames.isEmpty()) return

        val blockerLists = BlockerRepository.getBlockers(prefs)
        val activeBlockers = blockerLists.filter { it.name in activeBlockerNames }
        val pkg = sbn.packageName

        // Never cancel our own notifications or Android system notifications
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        // Never mute calls or alarms: a whitelist session blocks every app
        // not on the list, which would otherwise swallow incoming-call and
        // alarm notifications.
        val category = sbn.notification?.category
        if (category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM) return

        // A whitelist may only mute apps the picker offers — mirror the
        // app-blocking path so unselectable system apps keep notifying.
        val matched = activeBlockers.filter { blocker ->
            blocker.shouldBlock(pkg) &&
                (blocker.mode != BlockerMode.WHITELIST || AppUtils.isWhitelistBlockable(this, pkg))
        }
        if (matched.isNotEmpty()) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e("FocusNotifListener", "Error cancelling notification", e)
            }
        }
    }

    private fun getActiveBlockerNames(prefs: android.content.SharedPreferences): List<String> =
        BlockerRepository.getActiveBlockerNames(prefs)
}
