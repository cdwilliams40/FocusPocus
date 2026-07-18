package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.Progression
import com.infinicada.focuspocus.data.SettingsRepository
import com.infinicada.focuspocus.model.SigilCatalog
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

    private val _sealLiftedAlertsEnabled = MutableStateFlow(repo.getSealLiftedAlertsEnabled())
    val sealLiftedAlertsEnabled: StateFlow<Boolean> = _sealLiftedAlertsEnabled.asStateFlow()

    private val _isDeviceOwner = MutableStateFlow(DeviceOwnerManager.isDeviceOwner(application))
    val isDeviceOwner: StateFlow<Boolean> = _isDeviceOwner.asStateFlow()

    private val _deviceOwnerEnforcement = MutableStateFlow(repo.getDeviceOwnerEnforcement())
    val deviceOwnerEnforcement: StateFlow<Boolean> = _deviceOwnerEnforcement.asStateFlow()

    private val _deviceOwnerSuspendPacts = MutableStateFlow(repo.getDeviceOwnerSuspendPacts())
    val deviceOwnerSuspendPacts: StateFlow<Boolean> = _deviceOwnerSuspendPacts.asStateFlow()

    // Epoch millis of the pending Warden-removal request (0 = none). Removal
    // itself only unlocks DeviceOwnerManager.REMOVAL_COOLDOWN_MS later.
    private val _wardenRemovalRequestMillis = MutableStateFlow(repo.getWardenRemovalRequestMillis())
    val wardenRemovalRequestMillis: StateFlow<Long> = _wardenRemovalRequestMillis.asStateFlow()

    private val _analyticsConsent = MutableStateFlow(repo.getAnalyticsConsent())
    val analyticsConsent: StateFlow<Boolean> = _analyticsConsent.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(repo.isOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _showAnalyticsConsentDialog = MutableStateFlow(
        repo.isOnboardingCompleted() && !repo.isAnalyticsConsentShown()
    )
    val showAnalyticsConsentDialog: StateFlow<Boolean> = _showAnalyticsConsentDialog.asStateFlow()

    private val _progressionEnabled = MutableStateFlow(repo.getProgressionEnabled())
    val progressionEnabled: StateFlow<Boolean> = _progressionEnabled.asStateFlow()

    private val _wrapupEnabled = MutableStateFlow(repo.getWrapupEnabled())
    val wrapupEnabled: StateFlow<Boolean> = _wrapupEnabled.asStateFlow()

    private val _trialAlertsEnabled = MutableStateFlow(repo.getTrialAlertsEnabled())
    val trialAlertsEnabled: StateFlow<Boolean> = _trialAlertsEnabled.asStateFlow()

    // One-time "focusing now earns mana" intro for existing users (the
    // analytics-consent dialog pattern). New users learn about it organically;
    // the flag is set on onboarding completion too.
    private val _showProgressionIntroDialog = MutableStateFlow(
        repo.isOnboardingCompleted() && !repo.isProgressionIntroShown() && repo.getProgressionEnabled()
    )
    val showProgressionIntroDialog: StateFlow<Boolean> = _showProgressionIntroDialog.asStateFlow()

    // One-time "Pacts are now your home screen" note, same pattern: only users
    // who finished onboarding before this version ever see it — fresh installs
    // land on the dashboard as the natural end of onboarding.
    private val _showPactsHomeIntroDialog = MutableStateFlow(
        repo.isOnboardingCompleted() && !repo.isPactsHomeIntroShown()
    )
    val showPactsHomeIntroDialog: StateFlow<Boolean> = _showPactsHomeIntroDialog.asStateFlow()

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

    fun setSealLiftedAlertsEnabled(enabled: Boolean) {
        _sealLiftedAlertsEnabled.value = enabled
        repo.setSealLiftedAlertsEnabled(enabled)
    }

    fun refreshDeviceOwnerState() {
        _isDeviceOwner.value = DeviceOwnerManager.isDeviceOwner(getApplication())
        // The moment we learn we're device owner (e.g. right after the adb
        // command), lock down uninstall — don't wait for the next app start.
        if (_isDeviceOwner.value) {
            DeviceOwnerManager.applySelfProtection(getApplication())
            // Provisioning happens outside the app (adb), so this recheck is
            // one of the two places the Warden sigil can be observed earned.
            val app = getApplication<Application>()
            Progression.unlockSigils(
                app.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE),
                com.google.gson.Gson(),
                listOf(SigilCatalog.WARDEN)
            )
        } else if (_wardenRemovalRequestMillis.value != 0L) {
            // Warden left some other way (test-only builds allow adb removal);
            // drop the stale request so re-provisioning starts a clean slate.
            repo.clearWardenRemovalRequest()
            _wardenRemovalRequestMillis.value = 0L
        }
    }

    /** Starts the 24-hour cooling-off period before Warden Mode can be removed. */
    fun requestWardenRemoval() {
        val now = System.currentTimeMillis()
        repo.setWardenRemovalRequestMillis(now)
        _wardenRemovalRequestMillis.value = now
    }

    /** Withdraws a pending removal request — the protective, always-available path. */
    fun cancelWardenRemoval() {
        repo.clearWardenRemovalRequest()
        _wardenRemovalRequestMillis.value = 0L
    }

    fun setDeviceOwnerEnforcement(enabled: Boolean) {
        _deviceOwnerEnforcement.value = enabled
        repo.setDeviceOwnerEnforcement(enabled)
        // Apply (or lift) suspensions immediately if a session is already running.
        DeviceOwnerManager.syncSuspensions(getApplication())
    }

    fun setDeviceOwnerSuspendPacts(enabled: Boolean) {
        _deviceOwnerSuspendPacts.value = enabled
        repo.setDeviceOwnerSuspendPacts(enabled)
        // Grey out (or release) pact-gated apps immediately.
        DeviceOwnerManager.syncSuspensions(getApplication())
    }

    /**
     * Returns false if relinquishing device-owner status failed — including when
     * the 24-hour cooling-off period hasn't been requested or hasn't elapsed yet
     * (enforced here too, not just by the UI's disabled button).
     */
    fun removeDeviceOwner(): Boolean {
        if (!DeviceOwnerManager.isRemovalUnlocked(_wardenRemovalRequestMillis.value)) return false
        val cleared = DeviceOwnerManager.clearDeviceOwner(getApplication())
        if (cleared) {
            repo.clearWardenRemovalRequest()
            _wardenRemovalRequestMillis.value = 0L
        }
        refreshDeviceOwnerState()
        return cleared
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
        // Fresh installs meet progression from the start — no intro dialog later.
        repo.setProgressionIntroShown()
        // Likewise, they were onboarded straight onto the Pacts home screen.
        repo.setPactsHomeIntroShown()
    }

    fun dismissAnalyticsConsentDialog(accepted: Boolean) {
        applyAnalyticsConsent(accepted)
        repo.setAnalyticsConsentShown()
        _showAnalyticsConsentDialog.value = false
    }

    fun setProgressionEnabled(enabled: Boolean) {
        _progressionEnabled.value = enabled
        repo.setProgressionEnabled(enabled)
    }

    fun setWrapupEnabled(enabled: Boolean) {
        _wrapupEnabled.value = enabled
        repo.setWrapupEnabled(enabled)
    }

    fun setTrialAlertsEnabled(enabled: Boolean) {
        _trialAlertsEnabled.value = enabled
        repo.setTrialAlertsEnabled(enabled)
    }

    fun dismissProgressionIntroDialog() {
        repo.setProgressionIntroShown()
        _showProgressionIntroDialog.value = false
    }

    fun dismissPactsHomeIntroDialog() {
        repo.setPactsHomeIntroShown()
        _showPactsHomeIntroDialog.value = false
    }
}
