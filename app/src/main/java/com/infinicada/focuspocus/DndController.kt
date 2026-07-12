package com.infinicada.focuspocus

import android.app.NotificationManager
import android.content.Context
import android.util.Log

object DndController {
    private const val TAG = "DndController"
    private const val PREFS_DND_ENABLED_BY_APP = "dndEnabledByApp"
    private const val PREFS_DND_PREVIOUS_FILTER = "dndPreviousFilter"

    /**
     * Updates the Do Not Disturb state based on current focus mode settings.
     * Enables DND when:
     * - Focus mode is active (manual or scheduled)
     * - User has enabled "mute notifications" setting
     * - User is NOT on a break
     *
     * When disabling DND, restores the user's previous interruption filter
     * instead of unconditionally setting INTERRUPTION_FILTER_ALL, so that
     * a manually-enabled DND is not accidentally overridden.
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
        val focusTagId = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)
        val muteEnabled = sharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)
        val isOnBreak = sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)

        // A talisman (NFC tag) session counts as focus mode, matching the
        // accessibility service and the notification listener.
        val focusModeActive = manualFocusMode || activeScheduleId != null || focusTagId != null
        val shouldEnableDnd = focusModeActive && muteEnabled && !isOnBreak
        val appEnabledDnd = sharedPreferences.getBoolean(PREFS_DND_ENABLED_BY_APP, false)

        try {
            if (shouldEnableDnd) {
                if (!appEnabledDnd) {
                    // Save the current filter so we can restore it later.
                    // Capture BEFORE the system call so we have the previous state,
                    // but only persist the "enabled by app" flag AFTER the call succeeds.
                    val previousFilter = notificationManager.currentInterruptionFilter
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    sharedPreferences.edit()
                        .putInt(PREFS_DND_PREVIOUS_FILTER, previousFilter)
                        .putBoolean(PREFS_DND_ENABLED_BY_APP, true)
                        .apply()
                } else {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
                Log.d(TAG, "DND enabled (priority only mode)")
            } else if (appEnabledDnd) {
                // Restore the user's previous DND state
                val previousFilter = sharedPreferences.getInt(
                    PREFS_DND_PREVIOUS_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                notificationManager.setInterruptionFilter(previousFilter)
                sharedPreferences.edit()
                    .remove(PREFS_DND_PREVIOUS_FILTER)
                    .putBoolean(PREFS_DND_ENABLED_BY_APP, false)
                    .apply()
                Log.d(TAG, "DND restored to previous filter: $previousFilter")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when setting DND state", e)
        }
    }
}
