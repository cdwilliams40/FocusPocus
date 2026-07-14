package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.ui.theme.ThemeMode

class SettingsRepository(private val prefs: SharedPreferences) {

    fun getThemeMode(): ThemeMode = try {
        ThemeMode.valueOf(
            prefs.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.DARK.name)
                ?: ThemeMode.DARK.name
        )
    } catch (e: IllegalArgumentException) {
        ThemeMode.DARK
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(Constants.PrefsKeys.THEME_MODE, mode.name).apply()
    }

    fun getAnalyticsConsent(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, true)

    fun setAnalyticsConsent(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, enabled).apply()
    }

    fun isAnalyticsConsentShown(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT_SHOWN, false)

    fun setAnalyticsConsentShown() {
        prefs.edit().putBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT_SHOWN, true).apply()
    }

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted() {
        prefs.edit()
            .putBoolean(Constants.PrefsKeys.ONBOARDING_COMPLETED, true)
            .putInt(Constants.PrefsKeys.ONBOARDING_VERSION, 1)
            .putBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT_SHOWN, true)
            .apply()
    }

    fun getMuteBlockedNotifications(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)

    fun setMuteBlockedNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, enabled).apply()
    }

    fun getHideStopButton(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, false)

    fun setHideStopButton(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, enabled).apply()
    }

    fun getNfcLockMode(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, false)

    fun setNfcLockMode(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.NFC_LOCK_MODE, enabled).apply()
    }

    fun getDeviceOwnerEnforcement(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT, false)

    fun setDeviceOwnerEnforcement(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.DEVICE_OWNER_ENFORCEMENT, enabled).apply()
    }

    fun getBreakDurationMinutes(): Int =
        prefs.getInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 5).coerceIn(1, 30)

    fun setBreakDurationMinutes(minutes: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, minutes.coerceIn(1, 30)).apply()
    }

    fun getMaxBreaksPerSession(): Int =
        prefs.getInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 3).coerceIn(1, 10)

    fun setMaxBreaksPerSession(max: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, max.coerceIn(1, 10)).apply()
    }

    fun getAutoBreakEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, false)

    fun setAutoBreakEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, enabled).apply()
    }

    fun getAutoBreakIntervalMinutes(): Int =
        prefs.getInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 25).coerceIn(5, 60)

    fun setAutoBreakIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, minutes.coerceIn(5, 60)).apply()
    }

    fun getEmergencyBreakCadenceWeeks(): Int =
        prefs.getInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 2).coerceIn(2, 8)

    fun setEmergencyBreakCadenceWeeks(weeks: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, weeks.coerceIn(2, 8)).apply()
    }

    // ── Progression ──

    fun getProgressionEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)

    fun setProgressionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, enabled).apply()
    }

    fun getWrapupEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.WRAPUP_ENABLED, true)

    fun setWrapupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.WRAPUP_ENABLED, enabled).apply()
    }

    fun getTrialAlertsEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.TRIAL_ALERTS_ENABLED, true)

    fun setTrialAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefsKeys.TRIAL_ALERTS_ENABLED, enabled).apply()
    }

    fun isProgressionIntroShown(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_INTRO_SHOWN, false)

    fun setProgressionIntroShown() {
        prefs.edit().putBoolean(Constants.PrefsKeys.PROGRESSION_INTRO_SHOWN, true).apply()
    }

    fun isPactsHomeIntroShown(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PACTS_HOME_INTRO_SHOWN, false)

    fun setPactsHomeIntroShown() {
        prefs.edit().putBoolean(Constants.PrefsKeys.PACTS_HOME_INTRO_SHOWN, true).apply()
    }
}
