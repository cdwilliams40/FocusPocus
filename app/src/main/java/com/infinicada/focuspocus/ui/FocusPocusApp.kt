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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardRow
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.navigation.AppDestinations
import com.infinicada.focuspocus.navigation.PactsRoute
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.components.trialTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.infinicada.focuspocus.ui.screens.BlockerListScreen
import com.infinicada.focuspocus.ui.screens.BlockerSelectionDialog
import com.infinicada.focuspocus.ui.screens.BoonsScreen
import com.infinicada.focuspocus.ui.screens.ConditionalUnlocksScreen
import com.infinicada.focuspocus.ui.screens.CreateBlockerScreen
import com.infinicada.focuspocus.ui.screens.EditBlockerScreen
import com.infinicada.focuspocus.ui.screens.Greeting
import com.infinicada.focuspocus.ui.screens.GuardEditorScreen
import com.infinicada.focuspocus.ui.screens.OnboardingScreen
import com.infinicada.focuspocus.ui.screens.PactsHomeScreen
import com.infinicada.focuspocus.ui.screens.QuickSpellEditorScreen
import com.infinicada.focuspocus.ui.screens.QuickSpellsListScreen
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
    qrTriggerCount: Int,
    onScanQrCode: () -> Unit,
    pendingDeepLinkPreset: FocusPreset?,
    showDeepLinkConfirmation: Boolean,
    onConfirmDeepLink: () -> Unit,
    onDismissDeepLink: () -> Unit,
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
    val selectedPresetId by sessionVM.selectedPresetId.collectAsStateWithLifecycle()
    val isOnBreak by sessionVM.isOnBreak.collectAsStateWithLifecycle()
    val breaksUsedThisSession by sessionVM.breaksUsedThisSession.collectAsStateWithLifecycle()
    val breakTimeRemaining by sessionVM.breakTimeRemaining.collectAsStateWithLifecycle()
    val lastEmergencyBreakMillis by sessionVM.lastEmergencyBreakMillis.collectAsStateWithLifecycle()
    val focusTagId by sessionVM.focusTagId.collectAsStateWithLifecycle()
    val showSessionSummary by sessionVM.showSessionSummary.collectAsStateWithLifecycle()
    val sessionSummaryDuration by sessionVM.sessionSummaryDuration.collectAsStateWithLifecycle()
    val sessionSummaryBreaks by sessionVM.sessionSummaryBreaks.collectAsStateWithLifecycle()
    val sessionSummaryBlocker by sessionVM.sessionSummaryBlocker.collectAsStateWithLifecycle()
    val sessionFocusSessions by sessionVM.focusSessions.collectAsStateWithLifecycle()
    val longestStreak by sessionVM.longestStreak.collectAsStateWithLifecycle()
    val sessionElapsedSeconds by sessionVM.sessionElapsedSeconds.collectAsStateWithLifecycle()

    val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val focusPresets by spellbookVM.focusPresets.collectAsStateWithLifecycle()
    val namedTags by spellbookVM.namedTags.collectAsStateWithLifecycle()
    val appTimeLimits by spellbookVM.appTimeLimits.collectAsStateWithLifecycle()
    val appTimeLimitConfigs by spellbookVM.appTimeLimitConfigs.collectAsStateWithLifecycle()
    val conditionalUnlocks by spellbookVM.conditionalUnlocks.collectAsStateWithLifecycle()
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
    val pactGroups by spellbookVM.pactGroups.collectAsStateWithLifecycle()
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
    val wrapupEnabled by settingsVM.wrapupEnabled.collectAsStateWithLifecycle()
    val trialAlertsEnabled by settingsVM.trialAlertsEnabled.collectAsStateWithLifecycle()
    val showProgressionIntroDialog by settingsVM.showProgressionIntroDialog.collectAsStateWithLifecycle()
    val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
    val trials by progressionVM.trials.collectAsStateWithLifecycle()
    val boons by progressionVM.boons.collectAsStateWithLifecycle()
    val manaLedger by progressionVM.ledger.collectAsStateWithLifecycle()
    val unlockedSigilIds by progressionVM.unlockedSigilIds.collectAsStateWithLifecycle()
    val extraBreakTokens by progressionVM.extraBreakTokens.collectAsStateWithLifecycle()
    val manaEarnedThisWeek by progressionVM.manaEarnedThisWeek.collectAsStateWithLifecycle()
    val sessionSummaryMana by sessionVM.sessionSummaryMana.collectAsStateWithLifecycle()
    val sessionSummaryMilestoneBonus by sessionVM.sessionSummaryMilestoneBonus.collectAsStateWithLifecycle()
    val sessionSummaryStreak by sessionVM.sessionSummaryStreak.collectAsStateWithLifecycle()
    val sessionSummaryTrials by sessionVM.sessionSummaryTrials.collectAsStateWithLifecycle()
    val sessionSummarySigils by sessionVM.sessionSummarySigils.collectAsStateWithLifecycle()

    // Sync on NFC/QR external triggers. MainActivity bumps nfcTriggerCount on
    // every onResume and on session-pref changes, so this effect is also what
    // refreshes progression after service-side and trigger-driven stops.
    LaunchedEffect(nfcTriggerCount) {
        if (nfcTriggerCount > 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
            progressionVM.refresh()
        }
    }
    LaunchedEffect(qrTriggerCount) {
        if (qrTriggerCount > 0) {
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

    // Deep link confirmation dialog
    if (showDeepLinkConfirmation) {
        val preset = pendingDeepLinkPreset
        if (preset != null) {
            val isActive = manualFocusMode || focusTagId != null
            val actionDescription = when (preset.action ?: PresetAction.TOGGLE) {
                PresetAction.TEMP_ENABLE -> stringResource(R.string.main_deep_link_action_temp_enable, preset.name, preset.tempDurationMinutes ?: 30)
                PresetAction.TEMP_DISABLE -> if (isActive) stringResource(R.string.main_deep_link_action_temp_disable, preset.tempDurationMinutes ?: 30) else stringResource(R.string.main_deep_link_action_temp_disable_inactive)
                PresetAction.TOGGLE -> if (isActive) stringResource(R.string.main_deep_link_action_dispel, preset.name) else stringResource(R.string.main_deep_link_action_cast, preset.name)
            }
            AlertDialog(
                onDismissRequest = { onDismissDeepLink() },
                title = { Text(stringResource(R.string.main_deep_link_confirm_title)) },
                text = { Text(stringResource(R.string.main_deep_link_confirm_message, actionDescription)) },
                confirmButton = {
                    Button(onClick = { onConfirmDeepLink() }) {
                        Text(stringResource(R.string.main_deep_link_allow))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onDismissDeepLink() }) {
                        Text(stringResource(R.string.main_deep_link_deny))
                    }
                }
            )
        }
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

    // Accessibility service required dialog
    if (!isServiceEnabled) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.main_accessibility_title)) },
            text = { Text(stringResource(R.string.main_accessibility_desc)) },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.main_accessibility_go_to_settings))
                }
            }
        )
    }

    var showBlockerSelectionDialog by remember { mutableStateOf(false) }
    val focusMode = focusTagId != null || manualFocusMode
    // Saveable so the selected tab and settings screen survive rotation and
    // process recreation instead of snapping back to Home.
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBoons by rememberSaveable { mutableStateOf(false) }

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

    if (showSettings) {
        // Re-check on every return to the foreground, so granting notification
        // access or running the device-owner adb command is picked up as soon as
        // the user comes back to this screen.
        LifecycleResumeEffect(Unit) {
            isNotificationListenerEnabled = notificationManager.isNotificationPolicyAccessGranted
            settingsVM.refreshDeviceOwnerState()
            onPauseOrDispose { }
        }
        BackHandler { showSettings = false }
        SettingsScreen(
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
            namedTags = namedTags,
            focusMode = focusMode,
            progressionEnabled = progressionEnabled,
            onProgressionEnabledChanged = {
                settingsVM.setProgressionEnabled(it)
                progressionVM.refresh()
            },
            wrapupEnabled = wrapupEnabled,
            onWrapupEnabledChanged = { settingsVM.setWrapupEnabled(it) },
            trialAlertsEnabled = trialAlertsEnabled,
            onTrialAlertsEnabledChanged = { settingsVM.setTrialAlertsEnabled(it) },
            onNavigateBack = { showSettings = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Boons screen (same full-screen pattern as Settings; the gear only exists
    // on the main scaffold, so the two flags can't both be set)
    if (showBoons) {
        BackHandler { showBoons = false }
        // Pact-gated apps can buy sealed minutes too; GuardStatus owns the
        // explicit-config-wins precedence (mirroring resolvePactConfig).
        val pactedPackages = remember(appTimeLimitConfigs, pactGroups, blockerLists) {
            GuardStatus.pactGatedPackages(appTimeLimitConfigs, pactGroups, blockerLists)
        }
        val pactedApps = remember(installedApps, pactedPackages) {
            installedApps.filter { it.packageName in pactedPackages }
        }
        BoonsScreen(
            balance = manaBalance,
            boons = boons,
            sessionActive = focusMode,
            breaksAllowed = activeSchedule?.breaksEnabled ?: sessionBreaksEnabled,
            pactedApps = pactedApps,
            isSealedAvailableToday = { pkg -> progressionVM.isSealedMinutesAvailableToday(pkg) },
            isSealedOverDailyLimit = { pkg ->
                val limit = appTimeLimitConfigs[pkg]?.dailyLimitMinutes ?: 0
                limit > 0 && com.infinicada.focuspocus.AppTimeLimitManager
                    .getUsedMinutesToday(context, pkg) >= limit
            },
            onRedeemBoon = { boon -> progressionVM.redeemBoon(boon) },
            onSaveBoon = { boon -> progressionVM.saveBoon(boon) },
            onDeleteBoon = { boonId -> progressionVM.deleteBoon(boonId) },
            onBuyExtraBreak = { progressionVM.redeemPerk(Perk.EXTRA_BREAK) },
            onBuySealedMinutes = { pkg -> progressionVM.redeemPerk(Perk.SEALED_MINUTES, pkg) },
            onNavigateBack = { showBoons = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Back handler for spellbook sub-routes
    if (spellbookRoute !is SpellbookRoute.Overview && currentDestination == AppDestinations.SPELLBOOK) {
        BackHandler { spellbookVM.handleBack() }
    }

    // Back handler for the guard editor on the Pacts tab
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
                        // on its overview, not a stale guard editor.
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
                    // Both the dashboard and the guard editor care whether
                    // usage access is granted; re-checked on every resume.
                    var usageAccessGranted by remember {
                        mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context))
                    }
                    if (route is PactsRoute.Overview) {
                        // Live guard state refreshes on a minute tick while the
                        // dashboard is visible (the ticker dies with this branch's
                        // composition), on every return to the foreground, and on
                        // any data mutation via dataVersion. The snapshot reads
                        // prefs and usage stats, so it's taken off the main thread.
                        var guardTick by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(60_000L)
                                guardTick++
                            }
                        }
                        LifecycleResumeEffect(Unit) {
                            guardTick++
                            usageAccessGranted = UsageStatsHelper.hasUsageStatsPermission(context)
                            onPauseOrDispose { }
                        }
                        var guardLiveStates by remember {
                            mutableStateOf(emptyMap<String, GuardLiveState>())
                        }
                        var guardOpenStats by remember {
                            mutableStateOf(emptyMap<String, AppOpenStats>())
                        }
                        var guardNow by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(dataVersion, guardTick) {
                            val snapshot = withContext(Dispatchers.IO) {
                                spellbookVM.getGuardLiveState() to spellbookVM.getTodayOpenStats()
                            }
                            guardLiveStates = snapshot.first
                            guardOpenStats = snapshot.second
                            guardNow = System.currentTimeMillis()
                        }

                        PactsHomeScreen(
                            installedApps = installedApps,
                            appTimeLimitConfigs = appTimeLimitConfigs,
                            pactGroups = pactGroups,
                            blockerLists = blockerLists,
                            todayOpenStats = guardOpenStats,
                            guardLiveStates = guardLiveStates,
                            nowMillis = guardNow,
                            sessionActive = focusMode,
                            isOnBreak = isOnBreak,
                            sessionLabel = activeSchedule?.name
                                ?: activeManualBlockers.joinToString(", ") { it.name },
                            sessionTimeRemaining = focusTimeRemaining,
                            breakTimeRemaining = breakTimeRemaining,
                            progressionEnabled = progressionEnabled,
                            manaBalance = manaBalance,
                            currentStreak = currentStreak,
                            usageAccessGranted = usageAccessGranted,
                            onGrantUsageAccess = { UsageStatsHelper.openUsageAccessSettings(context) },
                            onOpenBoons = { showBoons = true },
                            onOpenFocus = { currentDestination = AppDestinations.FOCUS },
                            onMakePact = { spellbookVM.navigateToPacts(PactsRoute.CreateGuard) },
                            onGuardClick = { row ->
                                spellbookVM.navigateToPacts(
                                    when (row) {
                                        is GuardRow.App -> PactsRoute.EditGuard(row.packageName)
                                        is GuardRow.Circle -> PactsRoute.EditCircle(row.group.blockerName)
                                    }
                                )
                            },
                            onRequestTime = { pkg, minutes ->
                                spellbookVM.requestPactTime(pkg, minutes)
                            },
                            modifier = contentModifier
                        )
                    } else {
                        LifecycleResumeEffect(Unit) {
                            usageAccessGranted = UsageStatsHelper.hasUsageStatsPermission(context)
                            onPauseOrDispose { }
                        }
                        GuardEditorScreen(
                            installedApps = installedApps,
                            blockerLists = blockerLists,
                            pactGroups = pactGroups,
                            appTimeLimitConfigs = appTimeLimitConfigs,
                            editPackageName = (route as? PactsRoute.EditGuard)?.packageName,
                            editCircleName = (route as? PactsRoute.EditCircle)?.blockerName,
                            usageAccessGranted = usageAccessGranted,
                            onGrantUsageAccess = { UsageStatsHelper.openUsageAccessSettings(context) },
                            onSaveConfig = { config ->
                                spellbookVM.saveAppTimeLimitConfig(config)
                                spellbookVM.navigateToPacts(PactsRoute.Overview)
                            },
                            onDeleteConfig = { pkg ->
                                spellbookVM.deleteAppTimeLimit(pkg)
                                spellbookVM.navigateToPacts(PactsRoute.Overview)
                            },
                            onSaveGroup = { group ->
                                spellbookVM.savePactGroup(group)
                                spellbookVM.navigateToPacts(PactsRoute.Overview)
                            },
                            onDeleteGroup = { name ->
                                spellbookVM.deletePactGroup(name)
                                spellbookVM.navigateToPacts(PactsRoute.Overview)
                            },
                            onOpenEnchantment = { blocker ->
                                spellbookVM.navigateToPacts(PactsRoute.Overview)
                                spellbookVM.setSelectedBlocker(blocker)
                                spellbookVM.navigateTo(SpellbookRoute.EditEnchantment)
                                currentDestination = AppDestinations.SPELLBOOK
                            },
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
                        activeTagId = focusTagId,
                        namedTags = namedTags,
                        activeBlockers = activeManualBlockers,
                        activeSchedule = activeSchedule,
                        blockerLists = blockerLists,
                        focusPresets = focusPresets,
                        selectedPresetId = selectedPresetId,
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
                        onPresetSelected = { preset -> sessionVM.selectPreset(preset) },
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
                                } else {
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
                        onScanQrCode = onScanQrCode,
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
                        modifier = contentModifier
                    )
                }

                AppDestinations.SPELLBOOK -> {
                    when (val currentRoute = spellbookRoute) {
                        is SpellbookRoute.Overview -> SpellbookScreen(
                            blockerLists = blockerLists,
                            focusPresets = focusPresets,
                            schedules = schedules,
                            namedTags = namedTags,
                            conditionalUnlocks = conditionalUnlocks,
                            onNavigateToConditionalUnlocks = { spellbookVM.navigateTo(SpellbookRoute.ConditionalUnlocks) },
                            onNavigateToEnchantments = { spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList) },
                            onNavigateToQuickSpells = { spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList) },
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
                                hasPactCircle = pactGroups.any { group -> group.blockerName == it.name },
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

                        is SpellbookRoute.QuickSpellsList -> QuickSpellsListScreen(
                            focusPresets = focusPresets,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onEditPreset = { preset -> spellbookVM.navigateTo(SpellbookRoute.EditQuickSpell(preset)) },
                            onCreatePreset = { spellbookVM.navigateTo(SpellbookRoute.CreateQuickSpell) },
                            onDeleteFocusPreset = { spellbookVM.deleteFocusPreset(it) },
                            onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                            modifier = contentModifier
                        )
                        is SpellbookRoute.CreateQuickSpell -> QuickSpellEditorScreen(
                            presetToEdit = null,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSave = { preset ->
                                spellbookVM.saveFocusPreset(preset)
                                spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList)
                            },
                            onCancel = { spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList) },
                            modifier = contentModifier
                        )
                        is SpellbookRoute.EditQuickSpell -> QuickSpellEditorScreen(
                            presetToEdit = currentRoute.preset,
                            blockerLists = blockerLists,
                            namedTags = namedTags,
                            onSave = { preset ->
                                spellbookVM.saveFocusPreset(preset)
                                spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList)
                            },
                            onCancel = { spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList) },
                            modifier = contentModifier
                        )

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
                            onSaveTag = { name ->
                                lastScannedTagId?.let { spellbookVM.saveNamedTag(it, name) }
                            },
                            onDeleteTag = { spellbookVM.deleteNamedTag(it) },
                            onSaveQrTalisman = { spellbookVM.saveQrTalisman(it) },
                            onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                            modifier = contentModifier
                        )

                        is SpellbookRoute.ConditionalUnlocks -> ConditionalUnlocksScreen(
                            conditionalUnlocks = conditionalUnlocks,
                            installedApps = installedApps,
                            blockerLists = blockerLists,
                            appTimeLimits = appTimeLimits,
                            // Pact-gated apps carry a "(Pact)" label in the pickers;
                            // GuardStatus owns the explicit-config-wins precedence.
                            pactPackages = remember(appTimeLimitConfigs, pactGroups, blockerLists) {
                                GuardStatus.pactGatedPackages(appTimeLimitConfigs, pactGroups, blockerLists)
                            },
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
                        appTimeLimits = appTimeLimits,
                        openDailyStats = appOpenDailyStats,
                        progressionEnabled = progressionEnabled,
                        manaBalance = manaBalance,
                        manaEarnedThisWeek = manaEarnedThisWeek,
                        trials = trials,
                        ledger = manaLedger,
                        unlockedSigilIds = unlockedSigilIds,
                        onClaimTrial = { trial -> progressionVM.claimTrial(trial.id) },
                        onOpenBoons = { showBoons = true },
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
                                    stringResource(R.string.session_complete_duration, sessionSummaryDuration),
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
                                        stringResource(R.string.home_day_streak, sessionSummaryStreak),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (sessionSummaryMilestoneBonus > 0) {
                                Text(
                                    stringResource(R.string.summary_milestone_bonus, sessionSummaryMilestoneBonus),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            sessionSummaryTrials.forEach { trial ->
                                Text(
                                    stringResource(R.string.summary_trial_complete, trialTitle(trial)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Cap unlock lines so a retroactive first run
                            // celebrates without scrolling.
                            sessionSummarySigils.take(3).forEach { sigil ->
                                Text(
                                    stringResource(R.string.summary_sigil_unlocked, stringResource(sigil.titleRes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (sessionSummarySigils.size > 3) {
                                Text(
                                    stringResource(R.string.summary_more_unlocks, sessionSummarySigils.size - 3),
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
