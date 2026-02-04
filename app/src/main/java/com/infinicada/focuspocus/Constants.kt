package com.infinicada.focuspocus

/**
 * App-wide constants to ensure consistency across components.
 */
object Constants {
    // SharedPreferences file name
    const val PREFS_NAME = "FocusPocus"

    // SharedPreferences keys
    object PrefsKeys {
        const val MANUAL_FOCUS_MODE = "manualFocusMode"
        const val ACTIVE_BLOCKER = "activeBlocker"
        const val ACTIVE_SCHEDULE_ID = "activeScheduleId"
        const val FOCUS_TAG_ID = "focusTagId"
        const val IS_ON_BREAK = "isOnBreak"
        const val BREAKS_USED_THIS_SESSION = "breaksUsedThisSession"
        const val BREAK_TIME_REMAINING = "breakTimeRemaining"
        const val FOCUS_DURATION_MINUTES = "focusDurationMinutes"
        const val FOCUS_TIME_REMAINING = "focusTimeRemaining"
        const val SESSION_BREAKS_ENABLED = "sessionBreaksEnabled"
        const val MUTE_BLOCKED_NOTIFICATIONS = "muteBlockedNotifications"
        const val BREAK_DURATION_MINUTES = "breakDurationMinutes"
        const val MAX_BREAKS_PER_SESSION = "maxBreaksPerSession"
        const val THEME_MODE = "themeMode"

        // Data storage keys
        const val NAMED_TAGS = "namedTags"
        const val BLOCKER_LISTS = "blockerLists"
        const val SCHEDULES = "schedules"
        const val FOCUS_PRESETS = "focusPresets"
    }

    // Notification channels
    const val RITUALS_CHANNEL_ID = "focus_pocus_rituals"
}
