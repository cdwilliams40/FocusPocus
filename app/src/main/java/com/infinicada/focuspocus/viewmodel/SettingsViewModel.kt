package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.data.SettingsRepository
import com.infinicada.focuspocus.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: SettingsRepository =
        (application as FocusPocusApplication).container.settings

    private val _themeMode = MutableStateFlow(repo.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _breakDurationMinutes = MutableStateFlow(repo.getBreakDurationMinutes())
    val breakDurationMinutes: StateFlow<Int> = _breakDurationMinutes.asStateFlow()

    private val _maxBreaksPerSession = MutableStateFlow(repo.getMaxBreaksPerSession())
    val maxBreaksPerSession: StateFlow<Int> = _maxBreaksPerSession.asStateFlow()

    private val _emergencyBreakCadenceWeeks = MutableStateFlow(repo.getEmergencyBreakCadenceWeeks())
    val emergencyBreakCadenceWeeks: StateFlow<Int> = _emergencyBreakCadenceWeeks.asStateFlow()

    private val _autoBreakEnabled = MutableStateFlow(repo.getAutoBreakEnabled())
    val autoBreakEnabled: StateFlow<Boolean> = _autoBreakEnabled.asStateFlow()

    private val _autoBreakIntervalMinutes = MutableStateFlow(repo.getAutoBreakIntervalMinutes())
    val autoBreakIntervalMinutes: StateFlow<Int> = _autoBreakIntervalMinutes.asStateFlow()

    private val _hideStopButton = MutableStateFlow(repo.getHideStopButton())
    val hideStopButton: StateFlow<Boolean> = _hideStopButton.asStateFlow()

    private val _muteBlockedNotifications = MutableStateFlow(repo.getMuteBlockedNotifications())
    val muteBlockedNotifications: StateFlow<Boolean> = _muteBlockedNotifications.asStateFlow()

    private val _nfcLockMode = MutableStateFlow(repo.getNfcLockMode())
    val nfcLockMode: StateFlow<Boolean> = _nfcLockMode.asStateFlow()

    private val _analyticsConsent = MutableStateFlow(repo.getAnalyticsConsent())
    val analyticsConsent: StateFlow<Boolean> = _analyticsConsent.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(repo.isOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _showAnalyticsConsentDialog = MutableStateFlow(
        repo.isOnboardingCompleted() && !repo.isAnalyticsConsentShown()
    )
    val showAnalyticsConsentDialog: StateFlow<Boolean> = _showAnalyticsConsentDialog.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        repo.setThemeMode(mode)
    }

    fun setBreakDuration(minutes: Int) {
        _breakDurationMinutes.value = minutes
        repo.setBreakDurationMinutes(minutes)
    }

    fun setMaxBreaks(max: Int) {
        _maxBreaksPerSession.value = max
        repo.setMaxBreaksPerSession(max)
    }

    fun setEmergencyBreakCadence(weeks: Int) {
        _emergencyBreakCadenceWeeks.value = weeks
        repo.setEmergencyBreakCadenceWeeks(weeks)
    }

    fun setAutoBreakEnabled(enabled: Boolean) {
        _autoBreakEnabled.value = enabled
        repo.setAutoBreakEnabled(enabled)
    }

    fun setAutoBreakInterval(minutes: Int) {
        _autoBreakIntervalMinutes.value = minutes
        repo.setAutoBreakIntervalMinutes(minutes)
    }

    fun setHideStopButton(enabled: Boolean) {
        _hideStopButton.value = enabled
        repo.setHideStopButton(enabled)
    }

    fun setMuteNotifications(enabled: Boolean) {
        _muteBlockedNotifications.value = enabled
        repo.setMuteBlockedNotifications(enabled)
    }

    fun setNfcLockMode(enabled: Boolean) {
        _nfcLockMode.value = enabled
        repo.setNfcLockMode(enabled)
    }

    fun applyAnalyticsConsent(enabled: Boolean) {
        _analyticsConsent.value = enabled
        repo.setAnalyticsConsent(enabled)
        try {
            FirebaseApp.initializeApp(getApplication())
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            FirebaseAnalytics.getInstance(getApplication()).setAnalyticsCollectionEnabled(enabled)
        } catch (e: Exception) {
            // Ignore initialization issues when google-services.json is missing
            Log.e("SettingsViewModel", "Firebase initialization failed", e)
        }
    }

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        repo.setOnboardingCompleted()
    }

    fun dismissAnalyticsConsentDialog(accepted: Boolean) {
        applyAnalyticsConsent(accepted)
        repo.setAnalyticsConsentShown()
        _showAnalyticsConsentDialog.value = false
    }
}
