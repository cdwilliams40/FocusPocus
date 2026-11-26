package com.infinicada.focuspocus.ui.routes

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.BackupCodec
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.ProtectionHealth
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.ui.screens.SettingsScreen
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

@Composable
fun SettingsRoute(
    settingsVM: SettingsViewModel,
    sessionVM: SessionViewModel,
    spellbookVM: SpellbookViewModel,
    progressionVM: ProgressionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val themeMode by settingsVM.themeMode.collectAsStateWithLifecycle()
    val breakDurationMinutes by settingsVM.breakDurationMinutes.collectAsStateWithLifecycle()
    val maxBreaksPerSession by settingsVM.maxBreaksPerSession.collectAsStateWithLifecycle()
    val emergencyBreakCadenceWeeks by settingsVM.emergencyBreakCadenceWeeks.collectAsStateWithLifecycle()
    val autoBreakEnabled by settingsVM.autoBreakEnabled.collectAsStateWithLifecycle()
    val autoBreakIntervalMinutes by settingsVM.autoBreakIntervalMinutes.collectAsStateWithLifecycle()
    val hideStopButton by settingsVM.hideStopButton.collectAsStateWithLifecycle()
    val muteBlockedNotifications by settingsVM.muteBlockedNotifications.collectAsStateWithLifecycle()
    val nfcLockMode by settingsVM.nfcLockMode.collectAsStateWithLifecycle()
    val isDeviceOwner by settingsVM.isDeviceOwner.collectAsStateWithLifecycle()
    val deviceOwnerEnforcement by settingsVM.deviceOwnerEnforcement.collectAsStateWithLifecycle()
    val deviceOwnerSuspendPacts by settingsVM.deviceOwnerSuspendPacts.collectAsStateWithLifecycle()
    val wardenRemovalRequestMillis by settingsVM.wardenRemovalRequestMillis.collectAsStateWithLifecycle()
    val analyticsConsent by settingsVM.analyticsConsent.collectAsStateWithLifecycle()
    val progressionEnabled by settingsVM.progressionEnabled.collectAsStateWithLifecycle()
    val trialAlertsEnabled by settingsVM.trialAlertsEnabled.collectAsStateWithLifecycle()
    val wellSettingsState by settingsVM.wellSettingsState.collectAsStateWithLifecycle()
    val wellRefillGate by settingsVM.wellRefillGate.collectAsStateWithLifecycle()
    val autoBackupState by settingsVM.autoBackupState.collectAsStateWithLifecycle()

    val talismans by spellbookVM.talismans.collectAsStateWithLifecycle()
    val focusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()

    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    var isNotificationListenerEnabled by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    var protectionStatus by remember { mutableStateOf(ProtectionHealth.check(context)) }
    val appPrefs = remember {
        context.getSharedPreferences(com.infinicada.focuspocus.Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var enforcementLog by remember {
        mutableStateOf(com.infinicada.focuspocus.EnforcementLog.load(appPrefs))
    }

    LifecycleResumeEffect(Unit) {
        isNotificationListenerEnabled = notificationManager.isNotificationPolicyAccessGranted
        settingsVM.refreshDeviceOwnerState()
        settingsVM.refreshWellSettings()
        protectionStatus = ProtectionHealth.check(context)
        enforcementLog = com.infinicada.focuspocus.EnforcementLog.load(appPrefs)
        onPauseOrDispose { }
    }

    BackHandler(onBack = onNavigateBack)

    SettingsScreen(
        protectionStatus = protectionStatus,
        onFixAccessibility = { ProtectionHealth.openAccessibilitySettings(context) },
        onFixUsageAccess = { UsageStatsHelper.openUsageAccessSettings(context) },
        onFixNotifications = { ProtectionHealth.openNotificationSettings(context) },
        onFixBattery = { ProtectionHealth.openBatteryOptimizationSettings(context) },
        onFixExactAlarms = { ProtectionHealth.openExactAlarmSettings(context) },
        enforcementLog = enforcementLog,
        onClearEnforcementLog = {
            com.infinicada.focuspocus.EnforcementLog.clear(appPrefs)
            enforcementLog = emptyList()
        },
        themeMode = themeMode,
        onThemeModeChanged = { settingsVM.setThemeMode(it) },
        breakDurationMinutes = breakDurationMinutes,
        maxBreaksPerSession = maxBreaksPerSession,
        onBreakDurationChanged = { settingsVM.setBreakDuration(it) },
        onMaxBreaksChanged = { settingsVM.setMaxBreaks(it) },
        emergencyBreakCadenceWeeks = emergencyBreakCadenceWeeks,
        onEmergencyBreakCadenceChanged = { settingsVM.setEmergencyBreakCadence(it) },
        autoBreakEnabled = autoBreakEnabled,
        onAutoBreakEnabledChanged = { settingsVM.setAutoBreakEnabled(it) },
        autoBreakIntervalMinutes = autoBreakIntervalMinutes,
        onAutoBreakIntervalChanged = { settingsVM.setAutoBreakInterval(it) },
        hideStopButton = hideStopButton,
        onHideStopButtonChanged = { settingsVM.setHideStopButton(it) },
        muteNotifications = muteBlockedNotifications,
        isNotificationListenerEnabled = isNotificationListenerEnabled,
        onMuteNotificationsChanged = { enabled ->
            settingsVM.setMuteNotifications(enabled)
            DndController.updateDndState(context)
        },
        onOpenNotificationSettings = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        },
        nfcLockMode = nfcLockMode,
        onNfcLockModeChanged = { settingsVM.setNfcLockMode(it) },
        isDeviceOwner = isDeviceOwner,
        deviceOwnerEnforcement = deviceOwnerEnforcement,
        onDeviceOwnerEnforcementChanged = { settingsVM.setDeviceOwnerEnforcement(it) },
        deviceOwnerSuspendPacts = deviceOwnerSuspendPacts,
        onDeviceOwnerSuspendPactsChanged = { settingsVM.setDeviceOwnerSuspendPacts(it) },
        wardenRemovalRequestMillis = wardenRemovalRequestMillis,
        onRequestWardenRemoval = { settingsVM.requestWardenRemoval() },
        onCancelWardenRemoval = { settingsVM.cancelWardenRemoval() },
        onRefreshDeviceOwner = { settingsVM.refreshDeviceOwnerState() },
        onRemoveDeviceOwner = {
            if (!settingsVM.removeDeviceOwner()) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.settings_device_owner_remove_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        analyticsConsent = analyticsConsent,
        onAnalyticsConsentChanged = { settingsVM.applyAnalyticsConsent(it) },
        onExportBackup = { uri ->
            val ok = settingsVM.exportBackup(uri)
            Toast.makeText(
                context,
                resources.getString(if (ok) R.string.backup_export_done else R.string.backup_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        },
        onImportBackup = { uri ->
            when (settingsVM.importBackup(uri)) {
                is BackupCodec.ImportResult.Success -> {
                    Toast.makeText(context, resources.getString(R.string.backup_import_done), Toast.LENGTH_LONG).show()
                    settingsVM.restartApp()
                }
                is BackupCodec.ImportResult.UnsupportedVersion ->
                    Toast.makeText(context, resources.getString(R.string.backup_import_newer), Toast.LENGTH_LONG).show()
                is BackupCodec.ImportResult.SessionActive ->
                    Toast.makeText(context, resources.getString(R.string.backup_import_session_active), Toast.LENGTH_LONG).show()
                else ->
                    Toast.makeText(context, resources.getString(R.string.backup_import_invalid), Toast.LENGTH_LONG).show()
            }
        },
        talismans = talismans,
        focusMode = focusMode,
        progressionEnabled = progressionEnabled,
        onProgressionEnabledChanged = {
            settingsVM.setProgressionEnabled(it)
            progressionVM.refresh()
        },
        trialAlertsEnabled = trialAlertsEnabled,
        onTrialAlertsEnabledChanged = { settingsVM.setTrialAlertsEnabled(it) },
        wellSettingsState = wellSettingsState,
        onWellSettingsRequested = { settingsVM.requestWellSettings(it) },
        onCancelWellRevision = { settingsVM.cancelWellRevision() },
        wellRefillGate = wellRefillGate,
        onWellRefillGateChanged = { qEnabled, qStart, qEnd, mReq, mMin ->
            settingsVM.updateWellRefillGate(qEnabled, qStart, qEnd, mReq, mMin)
        },
        onCancelWellGateRevision = { settingsVM.cancelWellGateRevision() },
        autoBackupState = autoBackupState,
        onPickAutoBackupFolder = { uri ->
            settingsVM.setAutoBackupFolder(uri)
            Toast.makeText(context, resources.getString(R.string.auto_backup_folder_saved), Toast.LENGTH_SHORT).show()
        },
        onAutoBackupCadenceChanged = { settingsVM.setAutoBackupCadence(it) },
        onRunAutoBackupNow = {
            val ok = settingsVM.runAutoBackupNow()
            Toast.makeText(
                context,
                resources.getString(if (ok) R.string.backup_export_done else R.string.backup_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier.fillMaxSize()
    )
}
