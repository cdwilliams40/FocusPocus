package com.infinicada.focuspocus

import android.app.NotificationManager
import android.content.Context
import android.util.Log

object DndController {
    private const val TAG = "DndController"

    /**
     * Updates the Do Not Disturb state based on current focus mode settings.
     * Enables DND when:
     * - Focus mode is active (manual or scheduled)
     * - User has enabled "mute notifications" setting
     * - User is NOT on a break
     */
    fun updateDndState(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager unavailable")
            return
        }

        // Check if we have permission to modify DND
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "Notification policy access not granted, skipping DND update")
            return
        }

        val sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val manualFocusMode = sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        val activeScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val muteEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        val focusModeActive = manualFocusMode || activeScheduleId != null
        val shouldEnableDnd = focusModeActive && muteEnabled && !isOnBreak

        try {
            if (shouldEnableDnd) {
                // Enable DND - Priority only mode (allows alarms and priority contacts)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Log.d(TAG, "DND enabled (priority only mode)")
            } else {
                // Disable DND - All notifications allowed
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Log.d(TAG, "DND disabled (all notifications allowed)")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when setting DND state", e)
        }
    }
}
