package com.infinicada.focuspocus

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FocusNotificationListenerService : NotificationListenerService() {

    private val gson = Gson()
    private var cachedBlockerListsJson: String? = null
    private var cachedBlockerLists: List<Blocker> = emptyList()

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
        val blocker = getCachedBlocker(prefs, activeBlockerName) ?: return
        val pkg = sbn.packageName

        // Never cancel our own notifications or Android system notifications
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        if (shouldBlockApp(pkg, blocker)) {
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e("FocusNotifListener", "Error cancelling notification", e)
            }
        }
    }

    private fun getCachedBlocker(prefs: android.content.SharedPreferences, blockerName: String): Blocker? {
        val json = prefs.getString(Constants.PrefsKeys.BLOCKER_LISTS, null) ?: return null
        val blockers: List<Blocker> = if (json == cachedBlockerListsJson) {
            cachedBlockerLists
        } else {
            try {
                val type = object : TypeToken<List<Blocker>>() {}.type
                val parsed: List<Blocker> = gson.fromJson(json, type)
                cachedBlockerListsJson = json
                cachedBlockerLists = parsed
                parsed
            } catch (e: Exception) {
                emptyList()
            }
        }
        return blockers.find { it.name == blockerName }
    }

    private fun shouldBlockApp(packageName: String, blocker: Blocker): Boolean {
        return when (blocker.mode) {
            BlockerMode.BLACKLIST -> blocker.apps.contains(packageName)
            BlockerMode.WHITELIST -> !blocker.apps.contains(packageName)
        }
    }
}
