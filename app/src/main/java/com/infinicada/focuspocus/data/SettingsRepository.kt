package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.ui.theme.ThemeMode

class SettingsRepository(private val prefs: SharedPreferences) {

    fun getThemeMode(): ThemeMode = try {
        ThemeMode.valueOf(
            prefs.getString(Constants.PrefsKeys.THEME_MODE, ThemeMode.SYSTEM.name)
                ?: ThemeMode.SYSTEM.name
        )
    } catch (e: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(Constants.PrefsKeys.THEME_MODE, mode.name).apply()
    }

    fun getAnalyticsConsent(): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT, false)

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

    fun getEmergencyBreakCadenceWeeks(): Int =
        prefs.getInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 2).coerceIn(2, 8)

    fun setEmergencyBreakCadenceWeeks(weeks: Int) {
        prefs.edit().putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, weeks.coerceIn(2, 8)).apply()
    }
}
