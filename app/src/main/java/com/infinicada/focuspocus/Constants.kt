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
        const val ACTIVE_BLOCKERS = "activeBlockers"
        const val ACTIVE_SCHEDULE_ID = "activeScheduleId"
        // The talisman whose tap started the running session, if one did
        const val ACTIVE_TALISMAN_ID = "activeTalismanId"
        const val IS_ON_BREAK = "isOnBreak"
        const val BREAKS_USED_THIS_SESSION = "breaksUsedThisSession"
        const val BREAK_TIME_REMAINING = "breakTimeRemaining"
        const val FOCUS_DURATION_MINUTES = "focusDurationMinutes"
        const val FOCUS_TIME_REMAINING = "focusTimeRemaining"
        const val FOCUS_END_TIME_MILLIS = "focusEndTimeMillis"
        // Wall-clock end of the active schedule's window, snapshotted at activation.
        // Caps how much focus time a ritual session can be credited with if the
        // service was dead when the window ended (SessionRecorder.record).
        const val SCHEDULE_END_TIME_MILLIS = "scheduleEndTimeMillis"
        const val SESSION_BREAKS_ENABLED = "sessionBreaksEnabled"
        const val MUTE_BLOCKED_NOTIFICATIONS = "muteBlockedNotifications"
        const val BREAK_DURATION_MINUTES = "breakDurationMinutes"
        const val BREAK_END_TIME_MILLIS = "breakEndTimeMillis"
        // Wall-clock start of the running break, and break time already spent
        // this session — subtracted from the recorded focus duration (BreakClock).
        const val BREAK_START_TIME_MILLIS = "breakStartTimeMillis"
        const val SESSION_BREAK_MILLIS = "sessionBreakMillis"
        const val MAX_BREAKS_PER_SESSION = "maxBreaksPerSession"

        // Auto-break (Pomodoro) keys
        const val AUTO_BREAK_ENABLED = "autoBreakEnabled"
        const val AUTO_BREAK_INTERVAL_MINUTES = "autoBreakIntervalMinutes"
        // Wall-clock start of the current uninterrupted focus stretch
        // (set at session start and whenever a break ends)
        const val FOCUS_SEGMENT_START_MILLIS = "focusSegmentStartMillis"
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

        // NFC lock mode
        const val NFC_LOCK_MODE = "nfcLockMode"

        // Device owner: suspend (grey out) blocked apps during focus sessions
        const val DEVICE_OWNER_ENFORCEMENT = "deviceOwnerEnforcement"
        // Device owner: also suspend pact-gated apps whenever no allowance runs,
        // so they stay greyed out (and out of launcher suggestions) at all times
        const val DEVICE_OWNER_SUSPEND_PACTS = "deviceOwnerSuspendPacts"
        // Packages currently suspended by us, so they can be unsuspended later
        const val DEVICE_OWNER_SUSPENDED_PACKAGES = "deviceOwnerSuspendedPackages"
        // Epoch millis of the pending Warden-removal request; 0/absent = none.
        // Removal only unlocks DeviceOwnerManager.REMOVAL_COOLDOWN_MS later.
        const val WARDEN_REMOVAL_REQUEST_MILLIS = "wardenRemovalRequestMillis"

        // Active pact allowances (packageName -> allowance expiry epoch millis)
        const val PACT_ALLOWANCES = "pactAllowances"

        // Per-day open/reflex-open counters for tracked apps
        const val APP_OPEN_STATS = "appOpenStats"

        // Apps behind the Mana Well (limit/WellApps.kt), and queued removals
        // from that list (package -> epoch millis it comes off, 24 h after
        // the request)
        const val WELL_APPS = "wellApps"
        const val WELL_APP_REMOVALS = "wellAppRemovals"


        // Block events for statistics
        const val BLOCK_EVENTS = "blockEvents"

        // Onboarding
        const val ONBOARDING_COMPLETED = "onboardingCompleted"
        const val ONBOARDING_VERSION = "onboardingVersion"

        // One-time "Pacts are now your home screen" note for users updating in
        const val PACTS_HOME_INTRO_SHOWN = "pactsHomeIntroShown"

        // Analytics consent
        const val ANALYTICS_CONSENT = "analyticsConsent"
        const val ANALYTICS_CONSENT_SHOWN = "analyticsConsentShown"

        // Insights
        const val INSIGHTS_TIME_RANGE = "insightsTimeRange"

        // Stored-data schema version, advanced by Migrations
        const val SCHEMA_VERSION = "schemaVersion"

        // Progression (mana, trials)
        const val PROGRESSION_ENABLED = "progressionEnabled"
        const val PROGRESSION_INTRO_SHOWN = "progressionIntroShown"
        const val MANA_BALANCE = "manaBalance"
        const val MANA_LIFETIME_EARNED = "manaLifetimeEarned"
        const val MANA_LEDGER = "manaLedger"
        const val TRIALS = "trials"
        const val EXTRA_BREAK_TOKENS = "extraBreakTokens"
        const val TRIAL_ALERTS_ENABLED = "trialAlertsEnabled"
        // Apps whose use earns mana (JSON list of package names)
        const val PRODUCTIVE_APPS = "productiveApps"
        // Per-day history ("yyyyMMdd" -> record) of mana and of the Well
        const val MANA_HISTORY = "manaHistory"
        const val WELL_HISTORY = "wellHistory"
        // Snapshot of the usage-stats permission, refreshed whenever a Context is
        // available, so Context-free code (SessionRecorder's award path) can gate
        // usage-dependent trial templates deterministically.
        const val USAGE_PERMISSION_SNAPSHOT = "usagePermissionSnapshot"

        // Mana Well: one slowly refilling pool of minutes shared by every
        // Well app (limit/ManaWell.kt).
        const val WELL_ENABLED = "wellEnabled"
        const val WELL_CAPACITY_MINUTES = "wellCapacityMinutes"
        // Real minutes per Well minute; 0 = no passive refill.
        const val WELL_REFILL_INTERVAL_MINUTES = "wellRefillIntervalMinutes"
        // The pool itself: its level, and the instant that level was written.
        const val WELL_LEVEL_MILLIS = "wellLevelMillis"
        const val WELL_UPDATED_AT_MILLIS = "wellUpdatedAtMillis"
        // Net time drawn today ("yyyyMMdd" + millis), for the dashboard.
        const val WELL_DRAWN_DATE = "wellDrawnDate"
        const val WELL_DRAWN_MILLIS = "wellDrawnMillis"
        // A queued loosening of the Well's terms; applies-at 0/absent = none.
        const val WELL_PENDING_APPLIES_AT_MILLIS = "wellPendingAppliesAtMillis"
        const val WELL_PENDING_ENABLED = "wellPendingEnabled"
        const val WELL_PENDING_CAPACITY_MINUTES = "wellPendingCapacityMinutes"
        const val WELL_PENDING_REFILL_INTERVAL_MINUTES = "wellPendingRefillIntervalMinutes"
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
    const val MAX_APPS_PER_BLOCKER = 500
    const val MAX_BLOCK_EVENTS = 1000
    const val MAX_MANA_LEDGER = 300

    // Notification channels
    const val RITUALS_CHANNEL_ID = "focus_pocus_rituals"
    const val PROGRESSION_CHANNEL_ID = "focus_pocus_progression"
    const val FOCUS_SESSION_CHANNEL_ID = "focus_pocus_focus_session"

    // Notification IDs
    const val FOCUS_SESSION_NOTIFICATION_ID = 9001
    const val TRIAL_COMPLETION_NOTIFICATION_ID = 9002
    const val SESSION_COUNTDOWN_NOTIFICATION_ID = 9004
    // Tag-keyed by package name, so one id serves every app's seal-lifted note
}
