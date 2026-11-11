package com.infinicada.focuspocus.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinicada.focuspocus.DisplayTicks
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.ProtectionHealth
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.navigation.AppDestinations
import com.infinicada.focuspocus.navigation.PactsRoute
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.components.trialTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infinicada.focuspocus.ui.screens.BlockerListScreen
import com.infinicada.focuspocus.ui.screens.BlockerSelectionDialog
import com.infinicada.focuspocus.ui.screens.ManaScreen
import com.infinicada.focuspocus.ui.screens.ConditionalUnlocksScreen
import com.infinicada.focuspocus.ui.screens.CreateBlockerScreen
import com.infinicada.focuspocus.ui.screens.EditBlockerScreen
import com.infinicada.focuspocus.ui.screens.Greeting
import com.infinicada.focuspocus.ui.screens.OnboardingScreen
import com.infinicada.focuspocus.ui.screens.WellAppsScreen
import com.infinicada.focuspocus.ui.screens.WellHomeScreen
import com.infinicada.focuspocus.ui.screens.ScheduleEditorScreen
import com.infinicada.focuspocus.ui.screens.ScheduleListScreen
import com.infinicada.focuspocus.ui.screens.SettingsScreen
import com.infinicada.focuspocus.ui.screens.SpellbookScreen
import com.infinicada.focuspocus.ui.screens.TalismansScreen
import com.infinicada.focuspocus.ui.screens.UsageStatsScreen
import com.infinicada.focuspocus.viewmodel.InsightsViewModel
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPocusApp(
    isServiceEnabled: Boolean,
    lastScannedTagId: String?,
    nfcTriggerCount: Int,
    modifier: Modifier = Modifier
) {
    val sessionVM: SessionViewModel = viewModel()
    val spellbookVM: SpellbookViewModel = viewModel()
    val settingsVM: SettingsViewModel = viewModel()
    val insightsVM: InsightsViewModel = viewModel()
    val progressionVM: ProgressionViewModel = viewModel()

    val context = LocalContext.current

    // Collect ViewModel state
    val manualFocusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
    val activeBlockerNames by sessionVM.activeBlockerNames.collectAsStateWithLifecycle()
    val activeScheduleId by sessionVM.activeScheduleId.collectAsStateWithLifecycle()
    val focusDurationMinutes by sessionVM.focusDurationMinutes.collectAsStateWithLifecycle()
    val focusTimeRemaining by sessionVM.focusTimeRemaining.collectAsStateWithLifecycle()
    val sessionBreaksEnabled by sessionVM.sessionBreaksEnabled.collectAsStateWithLifecycle()
    val isOnBreak by sessionVM.isOnBreak.collectAsStateWithLifecycle()
    val breaksUsedThisSession by sessionVM.breaksUsedThisSession.collectAsStateWithLifecycle()
    val breakTimeRemaining by sessionVM.breakTimeRemaining.collectAsStateWithLifecycle()
    val lastEmergencyBreakMillis by sessionVM.lastEmergencyBreakMillis.collectAsStateWithLifecycle()
    val activeTalismanId by sessionVM.activeTalismanId.collectAsStateWithLifecycle()
    val showSessionSummary by sessionVM.showSessionSummary.collectAsStateWithLifecycle()
    val sessionSummaryDuration by sessionVM.sessionSummaryDuration.collectAsStateWithLifecycle()
    val sessionSummaryBreaks by sessionVM.sessionSummaryBreaks.collectAsStateWithLifecycle()
    val sessionSummaryBlocker by sessionVM.sessionSummaryBlocker.collectAsStateWithLifecycle()
    val sessionFocusSessions by sessionVM.focusSessions.collectAsStateWithLifecycle()
    val longestStreak by sessionVM.longestStreak.collectAsStateWithLifecycle()
    val sessionElapsedSeconds by sessionVM.sessionElapsedSeconds.collectAsStateWithLifecycle()

    val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val namedTags by spellbookVM.namedTags.collectAsStateWithLifecycle()
    val conditionalUnlocks by spellbookVM.conditionalUnlocks.collectAsStateWithLifecycle()
    val wellAppsForRules = spellbookVM.wellApps.collectAsStateWithLifecycle().value
        .mapTo(mutableSetOf()) { it.packageName }
    val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
    val spellbookRoute by spellbookVM.spellbookRoute.collectAsStateWithLifecycle()
    val pactsRoute by spellbookVM.pactsRoute.collectAsStateWithLifecycle()
    val selectedBlocker by spellbookVM.selectedBlocker.collectAsStateWithLifecycle()
    val dataVersion by spellbookVM.dataVersion.collectAsStateWithLifecycle()

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
    val onboardingCompleted by settingsVM.onboardingCompleted.collectAsStateWithLifecycle()
    val showAnalyticsConsentDialog by settingsVM.showAnalyticsConsentDialog.collectAsStateWithLifecycle()
    val showPactsHomeIntroDialog by settingsVM.showPactsHomeIntroDialog.collectAsStateWithLifecycle()

    val blockEvents by insightsVM.blockEvents.collectAsStateWithLifecycle()
    val insightsFocusSessions by insightsVM.focusSessions.collectAsStateWithLifecycle()
    val currentStreak by insightsVM.currentStreak.collectAsStateWithLifecycle()
    val insightsLongestStreak by insightsVM.longestStreak.collectAsStateWithLifecycle()
    val appOpenDailyStats by insightsVM.appOpenDailyStats.collectAsStateWithLifecycle()

    val progressionEnabled by settingsVM.progressionEnabled.collectAsStateWithLifecycle()
    val trialAlertsEnabled by settingsVM.trialAlertsEnabled.collectAsStateWithLifecycle()
    val showProgressionIntroDialog by settingsVM.showProgressionIntroDialog.collectAsStateWithLifecycle()
    val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
    val trials by progressionVM.trials.collectAsStateWithLifecycle()
    val manaLedger by progressionVM.ledger.collectAsStateWithLifecycle()
    val extraBreakTokens by progressionVM.extraBreakTokens.collectAsStateWithLifecycle()
    val manaEarnedThisWeek by progressionVM.manaEarnedThisWeek.collectAsStateWithLifecycle()
    val wellChannelCost by progressionVM.wellChannelCost.collectAsStateWithLifecycle()
    val wellSettingsState by settingsVM.wellSettingsState.collectAsStateWithLifecycle()
    val sessionSummaryMana by sessionVM.sessionSummaryMana.collectAsStateWithLifecycle()
    val sessionSummaryStreak by sessionVM.sessionSummaryStreak.collectAsStateWithLifecycle()
    val sessionSummaryTrials by sessionVM.sessionSummaryTrials.collectAsStateWithLifecycle()

    // Pause the per-second countdown tickers while the UI can't be seen —
    // the accessibility service enforces session expiry from the persisted
    // end timestamps regardless, so backgrounded ticking is pure waste.
    LifecycleStartEffect(Unit) {
        sessionVM.onUiStarted()
        onStopOrDispose { sessionVM.onUiStopped() }
    }

    // Sync on external triggers (NFC, deep links). MainActivity bumps
    // nfcTriggerCount on every onResume and on session-pref changes, so this
    // effect is also what refreshes progression after service-side and
    // trigger-driven stops.
    LaunchedEffect(nfcTriggerCount) {
        if (nfcTriggerCount > 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
            progressionVM.refresh()
        }
    }

    // Cross-VM sync when spellbook data changes
    LaunchedEffect(dataVersion) {
        if (dataVersion != 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
            progressionVM.refresh()
        }
    }

    // Any freshly shown summary means a session just recorded — pick up the
    // new balance/trials no matter which path stopped the session.
    LaunchedEffect(showSessionSummary) {
        if (showSessionSummary) {
            progressionVM.refresh()
        }
    }

    // Write focus mode state to prefs when it changes
    LaunchedEffect(manualFocusMode, activeBlockerNames, activeScheduleId) {
        sessionVM.writeFocusModeState()
    }

    // Sync UI with external changes to activeScheduleId
    LaunchedEffect(activeScheduleId) {
        sessionVM.onActiveScheduleIdChanged(activeScheduleId)
    }

    // Onboarding screen
    if (!onboardingCompleted) {
        OnboardingScreen(
            namedTags = namedTags,
            installedApps = installedApps,
            isServiceEnabled = isServiceEnabled,
            analyticsConsent = analyticsConsent,
            onAnalyticsConsentChanged = { settingsVM.applyAnalyticsConsent(it) },
            onCreateFirstPacts = { packages -> spellbookVM.createDefaultPacts(packages) },
            onSaveTag = { name ->
                lastScannedTagId?.let { spellbookVM.saveNamedTag(it, name) }
            },
            onComplete = { settingsVM.completeOnboarding() }
        )
        return
    }

    // Analytics consent dialog for existing users
    if (showAnalyticsConsentDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.consent_dialog_title)) },
            text = { Text(stringResource(R.string.consent_dialog_message)) },
            confirmButton = {
                Button(onClick = { settingsVM.dismissAnalyticsConsentDialog(accepted = true) }) {
                    Text(stringResource(R.string.consent_dialog_accept))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { settingsVM.dismissAnalyticsConsentDialog(accepted = false) }) {
                    Text(stringResource(R.string.consent_dialog_decline))
                }
            }
        )
    }

    // One-time progression intro for users updating into the mana layer
    if (showProgressionIntroDialog && !showAnalyticsConsentDialog) {
        AlertDialog(
            onDismissRequest = { settingsVM.dismissProgressionIntroDialog() },
            title = { Text(stringResource(R.string.progression_intro_title)) },
            text = { Text(stringResource(R.string.progression_intro_message)) },
            confirmButton = {
                Button(onClick = { settingsVM.dismissProgressionIntroDialog() }) {
                    Text(stringResource(R.string.progression_intro_ok))
                }
            }
        )
    }

    // One-time "the front door moved" note for users updating into the
    // pacts-first layout; queued behind the other one-time dialogs.
    if (showPactsHomeIntroDialog && !showAnalyticsConsentDialog && !showProgressionIntroDialog) {
        AlertDialog(
            onDismissRequest = { settingsVM.dismissPactsHomeIntroDialog() },
            title = { Text(stringResource(R.string.pacts_home_intro_title)) },
            text = { Text(stringResource(R.string.pacts_home_intro_message)) },
            confirmButton = {
                Button(onClick = { settingsVM.dismissPactsHomeIntroDialog() }) {
                    Text(stringResource(R.string.pacts_home_intro_ok))
                }
            }
        )
    }

    // Accessibility prominent disclosure + consent, required by Play policy
    // before any redirect into the accessibility settings. Dismissable ("Not
    // now") — the Protection Health card in Settings remains the re-entry
    // point, so declining doesn't strand the user.
    var accessibilityDialogDismissed by rememberSaveable { mutableStateOf(false) }
    if (!isServiceEnabled && !accessibilityDialogDismissed) {
        AlertDialog(
            onDismissRequest = { accessibilityDialogDismissed = true },
            title = { Text(stringResource(R.string.main_accessibility_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.main_accessibility_desc))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.accessibility_disclosure_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.accessibility_disclosure_agree))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { accessibilityDialogDismissed = true }) {
                    Text(stringResource(R.string.accessibility_disclosure_later))
                }
            }
        )
    }

    var showBlockerSelectionDialog by remember { mutableStateOf(false) }
    val focusMode = manualFocusMode
    // Saveable so the selected tab and settings screen survive rotation and
    // process recreation instead of snapping back to Home.
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMana by rememberSaveable { mutableStateOf(false) }

    val activeManualBlockers = remember(activeBlockerNames, blockerLists) {
        blockerLists.filter { it.name in activeBlockerNames }
    }

    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    if (showBlockerSelectionDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = { blocker ->
                sessionVM.toggleBlocker(blocker)
                showBlockerSelectionDialog = false
            },
            onDismissRequest = { showBlockerSelectionDialog = false }
        )
    }

    // Settings screen
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var isNotificationListenerEnabled by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }

    // Enforcement health — recomputed on every return to the foreground of
    // the surfaces that show it (Settings card, dashboard battery banner).
    var protectionStatus by remember { mutableStateOf(ProtectionHealth.check(context)) }

    if (showSettings) {
        // Re-check on every return to the foreground, so granting notification
        // access or running the device-owner adb command is picked up as soon as
        // the user comes back to this screen.
        LifecycleResumeEffect(Unit) {
            isNotificationListenerEnabled = notificationManager.isNotificationPolicyAccessGranted
            settingsVM.refreshDeviceOwnerState()
            // A queued loosening of the Mana Well may have come due meanwhile.
            settingsVM.refreshWellSettings()
            protectionStatus = ProtectionHealth.check(context)
            onPauseOrDispose { }
        }
        BackHandler { showSettings = false }
        SettingsScreen(
            protectionStatus = protectionStatus,
            onFixAccessibility = { ProtectionHealth.openAccessibilitySettings(context) },
            onFixUsageAccess = { UsageStatsHelper.openUsageAccessSettings(context) },
            onFixNotifications = { ProtectionHealth.openNotificationSettings(context) },
            onFixBattery = { ProtectionHealth.openBatteryOptimizationSettings(context) },
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
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                context.startActivity(intent)
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
                        context.getString(R.string.settings_device_owner_remove_failed),
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
                    context.getString(
                        if (ok) R.string.backup_export_done else R.string.backup_export_failed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onImportBackup = { uri ->
                when (settingsVM.importBackup(uri)) {
                    is com.infinicada.focuspocus.BackupCodec.ImportResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_import_done),
                            Toast.LENGTH_LONG
                        ).show()
                        settingsVM.restartApp()
                    }
                    is com.infinicada.focuspocus.BackupCodec.ImportResult.UnsupportedVersion ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_import_newer),
                            Toast.LENGTH_LONG
                        ).show()
                    is com.infinicada.focuspocus.BackupCodec.ImportResult.SessionActive ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_import_session_active),
                            Toast.LENGTH_LONG
                        ).show()
                    else ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_import_invalid),
                            Toast.LENGTH_LONG
                        ).show()
                }
            },
            namedTags = namedTags,
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
            onNavigateBack = { showSettings = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Mana screen (same full-screen pattern as Settings; the gear only exists
    // on the main scaffold, so the two flags can't both be set)
    if (showMana) {
        BackHandler { showMana = false }
        // Re-read after each channel for the room check.
        var manaWell by remember { mutableStateOf(spellbookVM.getWellSnapshot()) }
        ManaScreen(
            balance = manaBalance,
            sessionActive = focusMode,
            breaksAllowed = activeSchedule?.breaksEnabled ?: sessionBreaksEnabled,
            wellEnabled = manaWell.enabled,
            wellChannelCost = wellChannelCost,
            wellHasRoom = manaWell.levelMillisAt(System.currentTimeMillis()) +
                Perk.WELL_CHANNEL_GRANT * WellMath.MINUTE_MS <=
                manaWell.capacityMinutes * WellMath.MINUTE_MS,
            onChannelWell = {
                val channeled = progressionVM.redeemPerk(Perk.WELL_CHANNEL)
                manaWell = spellbookVM.getWellSnapshot()
                channeled
            },
            onBuyExtraBreak = { progressionVM.redeemPerk(Perk.EXTRA_BREAK) },
            onNavigateBack = { showMana = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Back handler for spellbook sub-routes
    if (spellbookRoute !is SpellbookRoute.Overview && currentDestination == AppDestinations.SPELLBOOK) {
        BackHandler { spellbookVM.handleBack() }
    }

    // Back handler for the Well apps editor on the Home tab
    if (pactsRoute !is PactsRoute.Overview && currentDestination == AppDestinations.HOME) {
        BackHandler { spellbookVM.handlePactsBack() }
    }

    // One shared arcane sky behind every tab — the scaffold and top bar are
    // transparent so screens feel like they live in the same night.
    Box(modifier = modifier.fillMaxSize()) {
        ArcaneBackground()
        NavigationSuiteScaffold(
            containerColor = Color.Transparent,
            navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = stringResource(it.labelRes)) },
                    label = { Text(stringResource(it.labelRes)) },
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                        if (it == AppDestinations.SPELLBOOK) {
                            spellbookVM.navigateTo(SpellbookRoute.Overview)
                        }
                        // Same convention as Spellbook: tapping the tab lands
                        // on its overview, not a stale editor.
                        if (it == AppDestinations.HOME) {
                            spellbookVM.navigateToPacts(PactsRoute.Overview)
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentDestination.labelRes)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_content_desc))
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestinations.HOME -> {
                    val route = pactsRoute
                    val wellApps by spellbookVM.wellApps.collectAsStateWithLifecycle()
                    val pendingRemovals by spellbookVM.pendingRemovals.collectAsStateWithLifecycle()
                    if (route is PactsRoute.Overview) {
                        // Live state refreshes while the dashboard is visible
                        // (the ticker below dies with this branch's
                        // composition), on every return to the foreground, and
                        // on any data mutation via dataVersion. The snapshot
                        // reads prefs and may walk usage stats, so it's taken
                        // off the main thread.
                        var homeTick by remember { mutableIntStateOf(0) }
                        LifecycleResumeEffect(Unit) {
                            homeTick++
                            protectionStatus = ProtectionHealth.check(context)
                            onPauseOrDispose { }
                        }
                        var activePacts by remember { mutableStateOf(emptyMap<String, Long>()) }
                        var openStats by remember { mutableStateOf(emptyMap<String, AppOpenStats>()) }
                        var homeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        // The Mana Well is a few primitive prefs, so it is
                        // read up front: the card is there on the first frame.
                        var homeWell by remember { mutableStateOf(spellbookVM.getWellSnapshot()) }
                        LaunchedEffect(dataVersion, homeTick) {
                            val snapshot = withContext(Dispatchers.IO) {
                                // A removal coming due while the dashboard is
                                // open lands on this same tick.
                                spellbookVM.applyDueWellRemovals()
                                // Before the Well is read: a conditional
                                // unlock met since the last look pays in now.
                                val unlockMinutes = spellbookVM.creditWellUnlocks()
                                Triple(
                                    spellbookVM.getActivePacts(),
                                    spellbookVM.getTodayOpenStats(),
                                    spellbookVM.getWellSnapshot()
                                ) to unlockMinutes
                            }
                            activePacts = snapshot.first.first
                            openStats = snapshot.first.second
                            homeWell = snapshot.first.third
                            homeNow = System.currentTimeMillis()
                            if (snapshot.second > 0) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.well_unlock_credit_toast, snapshot.second),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        // Wake exactly when something on screen changes: a
                        // pact or removal coming due re-snapshots, a countdown
                        // rolling to its next minute only needs a new clock
                        // reading. Restarts whenever the snapshot brings new
                        // expiries.
                        LaunchedEffect(activePacts, pendingRemovals) {
                            val expiries = activePacts.values + pendingRemovals.values
                            while (true) {
                                val now = System.currentTimeMillis()
                                // Slack lets the service's own wakeup (expiry
                                // + 250 ms) or minute tick write first.
                                val nextRefresh = minOf(
                                    DisplayTicks.untilNextWallClockMinute(now) + 2_000L,
                                    expiries.filter { it > now }.minOrNull()
                                        ?.let { it - now + 1_000L } ?: Long.MAX_VALUE
                                )
                                val nextLabel = expiries
                                    .filter { it - now > 60_000L }
                                    .mapNotNull { DisplayTicks.untilCountdownMinuteChanges(it, now) }
                                    .minOrNull()
                                if (nextLabel != null && nextLabel < nextRefresh) {
                                    delay(nextLabel)
                                    homeNow = System.currentTimeMillis()
                                } else {
                                    delay(nextRefresh)
                                    homeTick++
                                }
                            }
                        }

                        WellHomeScreen(
                            installedApps = installedApps,
                            wellApps = wellApps,
                            pendingRemovals = pendingRemovals,
                            activePacts = activePacts,
                            todayOpenStats = openStats,
                            nowMillis = homeNow,
                            sessionActive = focusMode,
                            isOnBreak = isOnBreak,
                            sessionLabel = activeSchedule?.name
                                ?: activeManualBlockers.joinToString(", ") { it.name },
                            sessionTimeRemaining = focusTimeRemaining,
                            breakTimeRemaining = breakTimeRemaining,
                            progressionEnabled = progressionEnabled,
                            manaBalance = manaBalance,
                            currentStreak = currentStreak,
                            batteryUnrestricted = protectionStatus.batteryUnrestricted,
                            onFixBattery = { ProtectionHealth.openBatteryOptimizationSettings(context) },
                            onOpenMana = { showMana = true },
                            onOpenFocus = { currentDestination = AppDestinations.FOCUS },
                            onEditWellApps = { spellbookVM.navigateToPacts(PactsRoute.EditWellApps) },
                            onEndPact = { pkg ->
                                val refunded = spellbookVM.endPactNow(pkg)
                                val ended = context.resources.getQuantityString(
                                    R.plurals.home_end_pact_result, 1, 1
                                )
                                Toast.makeText(
                                    context,
                                    if (refunded > 0) {
                                        ended + " · " + context.getString(R.string.well_refund_toast, refunded)
                                    } else {
                                        ended
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRequestTime = { pkg, minutes ->
                                spellbookVM.requestPactTime(pkg, minutes)
                            },
                            well = homeWell,
                            wellChannelCost = wellChannelCost,
                            onChannelMana = {
                                val channeled = progressionVM.redeemPerk(Perk.WELL_CHANNEL)
                                Toast.makeText(
                                    context,
                                    if (channeled) {
                                        context.getString(
                                            R.string.well_channel_done, Perk.WELL_CHANNEL_GRANT
                                        )
                                    } else {
                                        context.getString(R.string.well_channel_failed)
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Re-snapshot: the level just moved.
                                homeTick++
                                channeled
                            },
                            modifier = contentModifier
                        )
                    } else {
                        var editorNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        // Re-read the clock whenever the list changes (a removal
                        // just requested counts from now) and on every minute.
                        LaunchedEffect(pendingRemovals) {
                            editorNow = System.currentTimeMillis()
                            while (true) {
                                delay(DisplayTicks.untilNextWallClockMinute(System.currentTimeMillis()) + 1_000L)
                                editorNow = System.currentTimeMillis()
                            }
                        }
                        WellAppsScreen(
                            installedApps = installedApps,
                            wellApps = wellApps,
                            pendingRemovals = pendingRemovals,
                            nowMillis = editorNow,
                            onAddApps = { spellbookVM.addWellApps(it) },
                            onSetSubstitute = { pkg, sub -> spellbookVM.setWellAppSubstitute(pkg, sub) },
                            onRequestRemoval = { spellbookVM.requestWellAppRemoval(it) },
                            onCancelRemoval = { spellbookVM.cancelWellAppRemoval(it) },
                            onNavigateBack = { spellbookVM.navigateToPacts(PactsRoute.Overview) },
                            modifier = contentModifier
                        )
                    }
                }

                AppDestinations.FOCUS -> {
                    val breaksAllowed = activeSchedule?.breaksEnabled ?: sessionBreaksEnabled
                    val effectiveBreakDuration = activeSchedule?.breakDurationMinutes?.coerceAtLeast(1) ?: breakDurationMinutes
                    // Extra-break perk tokens raise the effective max for this
                    // session, which consistently drives the take-break gate,
                    // button visibility, and the "x/y breaks" label below.
                    val effectiveMaxBreaks = (activeSchedule?.maxBreaksPerSession?.coerceAtLeast(1) ?: maxBreaksPerSession) +
                        extraBreakTokens
                    val emergencyBreakAvailable = System.currentTimeMillis() >= lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                    val emergencyBreakDaysRemaining = if (!emergencyBreakAvailable) {
                        val nextAvailable = lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                        ((nextAvailable - System.currentTimeMillis()) / (24 * 60 * 60 * 1000) + 1).toInt()
                    } else 0

                    Greeting(
                        focusMode = focusMode,
                        activeTalismanId = activeTalismanId,
                        namedTags = namedTags,
                        activeBlockers = activeManualBlockers,
                        activeSchedule = activeSchedule,
                        blockerLists = blockerLists,
                        focusDurationMinutes = focusDurationMinutes,
                        focusTimeRemaining = focusTimeRemaining,
                        isOnBreak = isOnBreak,
                        breakTimeRemaining = breakTimeRemaining,
                        breakTotalSeconds = effectiveBreakDuration * 60,
                        sessionElapsedSeconds = sessionElapsedSeconds,
                        breaksUsedThisSession = breaksUsedThisSession,
                        maxBreaksPerSession = effectiveMaxBreaks,
                        breaksAllowed = breaksAllowed,
                        sessionBreaksEnabled = sessionBreaksEnabled,
                        hideStopButton = hideStopButton,
                        nfcLockMode = nfcLockMode,
                        emergencyBreakAvailable = emergencyBreakAvailable,
                        emergencyBreakDaysRemaining = emergencyBreakDaysRemaining,
                        progressionEnabled = progressionEnabled,
                        trials = trials,
                        canAffordExtraBreak = manaBalance >= Perk.EXTRA_BREAK.costMana,
                        onBlockerToggled = { blocker -> sessionVM.toggleBlocker(blocker) },
                        onDurationSelected = { duration -> sessionVM.selectDuration(duration) },
                        onSessionBreaksToggled = { enabled -> sessionVM.toggleSessionBreaks(enabled) },
                        onStartClicked = {
                            if (focusMode) {
                                // Stops populate the summary dialog from their
                                // RecordResult, so no separate pre-capture step.
                                if (activeSchedule != null) {
                                    if (activeSchedule.unbindingTalismanId == null) {
                                        sessionVM.dispelSchedule()
                                        progressionVM.refresh()
                                    }
                                } else if (namedTags.none { it.id == activeTalismanId && it.locksSession }) {
                                    // A session a locking talisman started ends
                                    // only with that talisman.
                                    sessionVM.stopSession()
                                    progressionVM.refresh()
                                }
                            } else {
                                if (activeManualBlockers.isNotEmpty()) {
                                    sessionVM.startSession(activeManualBlockers.map { it.name })
                                } else {
                                    if (blockerLists.isNotEmpty()) {
                                        showBlockerSelectionDialog = true
                                    }
                                }
                            }
                        },
                        onBlockerSelectorClicked = {
                            if (activeSchedule == null) {
                                showBlockerSelectionDialog = true
                            }
                        },
                        onTakeBreak = {
                            if (breaksAllowed && breaksUsedThisSession < effectiveMaxBreaks && !isOnBreak) {
                                sessionVM.takeBreak(effectiveBreakDuration)
                            }
                        },
                        onEndBreak = { sessionVM.endBreak() },
                        onEmergencyStop = {
                            // Deliberately dialog-free: the first stop records
                            // (and quietly earns); the second returns empty.
                            sessionVM.emergencyStop()
                            sessionVM.dispelSchedule()
                            progressionVM.refresh()
                            Toast.makeText(context, context.getString(R.string.toast_emergency_stop), Toast.LENGTH_SHORT).show()
                        },
                        onClaimTrial = { trial -> progressionVM.claimTrial(trial.id) },
                        onBuyExtraBreak = { progressionVM.redeemPerk(Perk.EXTRA_BREAK) },
                        onCreateEnchantment = {
                            currentDestination = AppDestinations.SPELLBOOK
                            spellbookVM.navigateTo(SpellbookRoute.CreateEnchantment)
                        },
                        // Same effect as tapping the physical tag while idle:
                        // anchor a talisman session on this tag. Dispelling is
                        // not offered here — the session controls (and any
                        // talisman lock) own that.
                        modifier = contentModifier
                    )
                }

                AppDestinations.SPELLBOOK -> {
                    when (val currentRoute = spellbookRoute) {
                        is SpellbookRoute.Overview -> SpellbookScreen(
                            blockerLists = blockerLists,
                            schedules = schedules,
                            namedTags = namedTags,
                            conditionalUnlocks = conditionalUnlocks,
                            onNavigateToConditionalUnlocks = { spellbookVM.navigateTo(SpellbookRoute.ConditionalUnlocks) },
                            onNavigateToEnchantments = { spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList) },
                            onNavigateToRituals = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
                            onNavigateToTalismans = { spellbookVM.navigateTo(SpellbookRoute.Talismans) },
                            modifier = contentModifier
                        )

                        is SpellbookRoute.EnchantmentsList -> {
                            val activeBlockerForList = if (focusMode) {
                                activeSchedule?.effectiveBlockerNames?.firstOrNull() ?: activeManualBlockers.firstOrNull()?.name
                            } else null
                            BlockerListScreen(
                                blockerLists = blockerLists,
                                activeBlockerName = activeBlockerForList,
                                onBlockerClick = {
                                    spellbookVM.setSelectedBlocker(it)
                                    spellbookVM.navigateTo(SpellbookRoute.EditEnchantment)
                                },
                                onCreateClick = { spellbookVM.navigateTo(SpellbookRoute.CreateEnchantment) },
                                modifier = contentModifier
                            )
                        }
                        is SpellbookRoute.CreateEnchantment -> CreateBlockerScreen(
                            onSaveBlocker = {
                                spellbookVM.saveBlocker(it)
                                spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList)
                            },
                            installedApps = installedApps,
                            modifier = contentModifier
                        )
                        is SpellbookRoute.EditEnchantment -> selectedBlocker?.let {
                            EditBlockerScreen(
                                blocker = it,
                                onSaveBlocker = { blockerToSave ->
                                    spellbookVM.saveBlocker(blockerToSave)
                                    spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList)
                                },
                                onDeleteBlocker = { blockerToDelete ->
                                    spellbookVM.deleteBlocker(blockerToDelete)
                                    spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList)
                                },
                                installedApps = installedApps,
                                modifier = contentModifier
                            )
                        }

                        is SpellbookRoute.RitualsList -> ScheduleListScreen(
                            schedules = schedules,
                            onScheduleClick = { schedule -> spellbookVM.navigateTo(SpellbookRoute.EditRitual(schedule)) },
                            onCreateClick = { spellbookVM.navigateTo(SpellbookRoute.CreateRitual) },
                            onDeleteSchedule = { spellbookVM.deleteSchedule(it) },
                            activeScheduleId = activeScheduleId,
                            modifier = contentModifier
                        )
                        is SpellbookRoute.CreateRitual -> ScheduleEditorScreen(
                            scheduleToEdit = null,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSaveSchedule = {
                                spellbookVM.saveSchedule(it)
                                spellbookVM.navigateTo(SpellbookRoute.RitualsList)
                            },
                            onCancel = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
                            modifier = contentModifier,
                            existingSchedules = schedules
                        )
                        is SpellbookRoute.EditRitual -> ScheduleEditorScreen(
                            scheduleToEdit = currentRoute.schedule,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSaveSchedule = {
                                spellbookVM.saveSchedule(it)
                                spellbookVM.navigateTo(SpellbookRoute.RitualsList)
                            },
                            onCancel = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
                            modifier = contentModifier,
                            existingSchedules = schedules
                        )

                        is SpellbookRoute.Talismans -> TalismansScreen(
                            lastScannedTagId = lastScannedTagId,
                            namedTags = namedTags,
                            blockerLists = blockerLists,
                            onSaveTag = { name ->
                                lastScannedTagId?.let { spellbookVM.saveNamedTag(it, name) }
                            },
                            onSaveSetup = { spellbookVM.saveTalismanSetup(it) },
                            onDeleteTag = { spellbookVM.deleteNamedTag(it) },
                            onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                            modifier = contentModifier
                        )

                        is SpellbookRoute.ConditionalUnlocks -> ConditionalUnlocksScreen(
                            conditionalUnlocks = conditionalUnlocks,
                            installedApps = installedApps,
                            blockerLists = blockerLists,
                            // A rule pays into the Well for the Well apps it names.
                            appTimeLimits = wellAppsForRules.associateWith { 0 },
                            pactPackages = wellAppsForRules,
                            wellEnabled = true,
                            onSave = { spellbookVM.saveConditionalUnlock(it) },
                            onDelete = { spellbookVM.deleteConditionalUnlock(it) },
                            onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                            modifier = contentModifier
                        )
                    }
                }

                AppDestinations.INSIGHTS -> {
                    UsageStatsScreen(
                        blockerLists = blockerLists,
                        installedApps = installedApps,
                        focusSessions = insightsFocusSessions,
                        currentStreak = currentStreak,
                        longestStreak = insightsLongestStreak,
                        blockEvents = blockEvents,
                        openDailyStats = appOpenDailyStats,
                        progressionEnabled = progressionEnabled,
                        manaBalance = manaBalance,
                        manaEarnedThisWeek = manaEarnedThisWeek,
                        trials = trials,
                        ledger = manaLedger,
                        onClaimTrial = { trial -> progressionVM.claimTrial(trial.id) },
                        onOpenMana = { showMana = true },
                        modifier = contentModifier
                    )
                }
            }
        }
    }
    }

    // Session summary dialog
    if (showSessionSummary) {
        AlertDialog(
            onDismissRequest = { sessionVM.dismissSessionSummary() },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.session_complete_icon_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.session_complete_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.session_complete_great_work),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    pluralStringResource(R.plurals.session_complete_duration, sessionSummaryDuration, sessionSummaryDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Coffee,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.session_complete_breaks, sessionSummaryBreaks),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.session_complete_enchantment, sessionSummaryBlocker),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Progression lines — rendered only when something was earned,
                    // so a disabled progression layer leaves the dialog untouched.
                    if (sessionSummaryMana > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.summary_mana_earned, sessionSummaryMana),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                if (sessionSummaryStreak > 0) {
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        pluralStringResource(R.plurals.home_day_streak, sessionSummaryStreak, sessionSummaryStreak),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            sessionSummaryTrials.forEach { trial ->
                                Text(
                                    stringResource(R.string.summary_trial_complete, trialTitle(trial)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { sessionVM.dismissSessionSummary() }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}

