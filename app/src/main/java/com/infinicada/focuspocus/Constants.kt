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
        const val HIDE_STOP_BUTTON = "hideStopButton"

        // Emergency break keys
        const val LAST_EMERGENCY_BREAK_MILLIS = "lastEmergencyBreakMillis"
        const val EMERGENCY_BREAK_CADENCE_WEEKS = "emergencyBreakCadenceWeeks"

        // Session history keys
        const val FOCUS_SESSIONS = "focusSessions"
        const val LONGEST_STREAK = "longestStreak"
        const val SESSION_START_TIME = "sessionStartTime"

        // Data storage keys
        const val NAMED_TAGS = "namedTags"
        const val BLOCKER_LISTS = "blockerLists"
        const val SCHEDULES = "schedules"
        const val FOCUS_PRESETS = "focusPresets"

        // NFC lock mode
        const val NFC_LOCK_MODE = "nfcLockMode"

        // Notification blocking
        const val BLOCK_APP_NOTIFICATIONS = "blockAppNotifications"

        // Per-app time limits
        const val APP_TIME_LIMITS = "appTimeLimits"

        // Block events for statistics
        const val BLOCK_EVENTS = "blockEvents"

        // Auto triggers (Wi-Fi / Bluetooth)
        const val AUTO_TRIGGERS = "autoTriggers"

        // Persisted last-activated trigger IDs (survive service restart)
        const val LAST_WIFI_TRIGGER_ID = "lastWifiTriggerId"
        const val LAST_BT_TRIGGER_ID = "lastBtTriggerId"

        // Incremented by background services when focus state changes; observed by the UI
        const val SERVICES_TRIGGER_COUNT = "servicesTriggerCount"

        // Onboarding
        const val ONBOARDING_COMPLETED = "onboardingCompleted"
        const val ONBOARDING_VERSION = "onboardingVersion"
    }

    // List size limits
    const val MAX_NAMED_TAGS = 100
    const val MAX_BLOCKERS = 50
    const val MAX_SCHEDULES = 50
    const val MAX_PRESETS = 100
    const val MAX_APPS_PER_BLOCKER = 500
    const val MAX_WEBSITES_PER_BLOCKER = 100
    const val MAX_AUTO_TRIGGERS = 50
    const val MAX_BLOCK_EVENTS = 1000
    const val MAX_APP_TIME_LIMITS = 100

    // Notification channels
    const val RITUALS_CHANNEL_ID = "focus_pocus_rituals"
    const val WIFI_TRIGGER_CHANNEL_ID = "wifi_trigger_channel"
    const val WIFI_TRIGGER_NOTIFICATION_ID = 9001
}
