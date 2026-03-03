package com.infinicada.focuspocus

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FocusNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        val blockAppNotifications = prefs.getBoolean(Constants.PrefsKeys.BLOCK_APP_NOTIFICATIONS, true)
        if (!blockAppNotifications) return

        val manualFocusMode = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val focusTagId = prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val focusActive = manualFocusMode || focusTagId != null
        val isOnBreak = prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        if (!focusActive || isOnBreak) return

        val activeBlockerName = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: return
        val blocker = BlockerRepository.getBlocker(prefs, activeBlockerName) ?: return
        val pkg = sbn.packageName

        // Never cancel our own notifications or Android system notifications
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        if (blocker.shouldBlock(pkg)) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e("FocusNotifListener", "Error cancelling notification", e)
            }
        }
    }
}
