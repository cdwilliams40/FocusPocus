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
        const val ACTIVE_BLOCKERS = "activeBlockers"
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

        // Per-app time limits
        const val APP_TIME_LIMITS = "appTimeLimits"

        // Conditional unlocks
        const val CONDITIONAL_UNLOCKS = "conditionalUnlocks"

        // Block events for statistics
        const val BLOCK_EVENTS = "blockEvents"

        // Onboarding
        const val ONBOARDING_COMPLETED = "onboardingCompleted"
        const val ONBOARDING_VERSION = "onboardingVersion"

        // Analytics consent
        const val ANALYTICS_CONSENT = "analyticsConsent"
        const val ANALYTICS_CONSENT_SHOWN = "analyticsConsentShown"
    }

    object Defaults {
        object FocusPresets {
            const val DEEP_WORK_NAME = "Deep Work"
            const val DEEP_WORK_DURATION = 240
            const val DEEP_WORK_BREAKS = true

            const val QUICK_FOCUS_NAME = "Quick Focus"
            const val QUICK_FOCUS_DURATION = 25
            const val QUICK_FOCUS_BREAKS = true

            const val SLEEP_MODE_NAME = "Sleep Mode"
            const val SLEEP_MODE_DURATION = 480
            const val SLEEP_MODE_BREAKS = false

            const val DEFAULT_BLOCKER_NAME = "Default"
        }
    }

    // List size limits
    const val MAX_NAMED_TAGS = 100
    const val MAX_BLOCKERS = 50
    const val MAX_SCHEDULES = 50
    const val MAX_PRESETS = 100
    const val MAX_APPS_PER_BLOCKER = 500
    const val MAX_WEBSITES_PER_BLOCKER = 100
    const val MAX_BLOCK_EVENTS = 1000
    const val MAX_APP_TIME_LIMITS = 100
    const val MAX_CONDITIONAL_UNLOCKS = 50

    // Notification channels
    const val RITUALS_CHANNEL_ID = "focus_pocus_rituals"
}
