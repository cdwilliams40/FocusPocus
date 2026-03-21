package com.infinicada.focuspocus.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.navigation.AppDestinations
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.navigation.TopLevelRoute
import com.infinicada.focuspocus.ui.screens.BlockerListScreen
import com.infinicada.focuspocus.ui.screens.BlockerSelectionDialog
import com.infinicada.focuspocus.ui.screens.ConditionalUnlocksScreen
import com.infinicada.focuspocus.ui.screens.CreateBlockerScreen
import com.infinicada.focuspocus.ui.screens.EditBlockerScreen
import com.infinicada.focuspocus.ui.screens.Greeting
import com.infinicada.focuspocus.ui.screens.OnboardingScreen
import com.infinicada.focuspocus.ui.screens.QuickSpellEditorScreen
import com.infinicada.focuspocus.ui.screens.QuickSpellsListScreen
import com.infinicada.focuspocus.ui.screens.ScheduleEditorScreen
import com.infinicada.focuspocus.ui.screens.ScheduleListScreen
import com.infinicada.focuspocus.ui.screens.SettingsScreen
import com.infinicada.focuspocus.ui.screens.SpellbookScreen
import com.infinicada.focuspocus.ui.screens.TalismansScreen
import com.infinicada.focuspocus.ui.screens.TimeLimitsScreen
import com.infinicada.focuspocus.ui.screens.UsageStatsScreen
import com.infinicada.focuspocus.viewmodel.InsightsViewModel
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

    val context = LocalContext.current

    // Collect ViewModel state
    val manualFocusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
    val activeBlockerName by sessionVM.activeBlockerName.collectAsStateWithLifecycle()
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

    val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val focusPresets by spellbookVM.focusPresets.collectAsStateWithLifecycle()
    val namedTags by spellbookVM.namedTags.collectAsStateWithLifecycle()
    val appTimeLimits by spellbookVM.appTimeLimits.collectAsStateWithLifecycle()
    val conditionalUnlocks by spellbookVM.conditionalUnlocks.collectAsStateWithLifecycle()
    val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
    val spellbookRoute by spellbookVM.spellbookRoute.collectAsStateWithLifecycle()
    val selectedBlocker by spellbookVM.selectedBlocker.collectAsStateWithLifecycle()
    val dataVersion by spellbookVM.dataVersion.collectAsStateWithLifecycle()

    val themeMode by settingsVM.themeMode.collectAsStateWithLifecycle()
    val breakDurationMinutes by settingsVM.breakDurationMinutes.collectAsStateWithLifecycle()
    val maxBreaksPerSession by settingsVM.maxBreaksPerSession.collectAsStateWithLifecycle()
    val emergencyBreakCadenceWeeks by settingsVM.emergencyBreakCadenceWeeks.collectAsStateWithLifecycle()
    val hideStopButton by settingsVM.hideStopButton.collectAsStateWithLifecycle()
    val muteBlockedNotifications by settingsVM.muteBlockedNotifications.collectAsStateWithLifecycle()
    val nfcLockMode by settingsVM.nfcLockMode.collectAsStateWithLifecycle()
    val analyticsConsent by settingsVM.analyticsConsent.collectAsStateWithLifecycle()
    val onboardingCompleted by settingsVM.onboardingCompleted.collectAsStateWithLifecycle()
    val showAnalyticsConsentDialog by settingsVM.showAnalyticsConsentDialog.collectAsStateWithLifecycle()

    val blockEvents by insightsVM.blockEvents.collectAsStateWithLifecycle()
    val insightsFocusSessions by insightsVM.focusSessions.collectAsStateWithLifecycle()
    val currentStreak by insightsVM.currentStreak.collectAsStateWithLifecycle()
    val insightsLongestStreak by insightsVM.longestStreak.collectAsStateWithLifecycle()

    // Sync on NFC/QR external triggers
    LaunchedEffect(nfcTriggerCount) {
        if (nfcTriggerCount > 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
        }
    }
    LaunchedEffect(qrTriggerCount) {
        if (qrTriggerCount > 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
        }
    }

    // Cross-VM sync when spellbook data changes
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
        }
    }

    // Write focus mode state to prefs when it changes
    LaunchedEffect(manualFocusMode, activeBlockerName, activeScheduleId) {
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
            blockerLists = blockerLists,
            installedApps = installedApps,
            isServiceEnabled = isServiceEnabled,
            analyticsConsent = analyticsConsent,
            onAnalyticsConsentChanged = { settingsVM.applyAnalyticsConsent(it) },
            onSaveBlocker = { spellbookVM.saveBlocker(it) },
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
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    var topLevelRoute by remember { mutableStateOf<TopLevelRoute>(TopLevelRoute.Main) }

    val activeManualBlocker = remember(activeBlockerName, blockerLists) {
        blockerLists.find { it.name == activeBlockerName }
    }

    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    if (showBlockerSelectionDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = { blocker ->
                sessionVM.selectBlocker(blocker)
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

    if (topLevelRoute is TopLevelRoute.Settings) {
        LaunchedEffect(Unit) {
            isNotificationListenerEnabled = notificationManager.isNotificationPolicyAccessGranted
        }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChanged = { settingsVM.setThemeMode(it) },
            breakDurationMinutes = breakDurationMinutes,
            maxBreaksPerSession = maxBreaksPerSession,
            onBreakDurationChanged = { settingsVM.setBreakDuration(it) },
            onMaxBreaksChanged = { settingsVM.setMaxBreaks(it) },
            emergencyBreakCadenceWeeks = emergencyBreakCadenceWeeks,
            onEmergencyBreakCadenceChanged = { settingsVM.setEmergencyBreakCadence(it) },
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
            analyticsConsent = analyticsConsent,
            onAnalyticsConsentChanged = { settingsVM.applyAnalyticsConsent(it) },
            namedTags = namedTags,
            focusMode = focusMode,
            onNavigateBack = { topLevelRoute = TopLevelRoute.Main },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Back handler for spellbook sub-routes
    if (spellbookRoute !is SpellbookRoute.Overview && currentDestination == AppDestinations.SPELLBOOK) {
        BackHandler { spellbookVM.handleBack() }
    }

    NavigationSuiteScaffold(
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
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentDestination.labelRes)) },
                    actions = {
                        IconButton(onClick = { topLevelRoute = TopLevelRoute.Settings }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_content_desc))
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestinations.HOME -> {
                    val breaksAllowed = activeSchedule?.breaksEnabled ?: sessionBreaksEnabled
                    val effectiveBreakDuration = activeSchedule?.breakDurationMinutes?.coerceAtLeast(1) ?: breakDurationMinutes
                    val effectiveMaxBreaks = activeSchedule?.maxBreaksPerSession?.coerceAtLeast(1) ?: maxBreaksPerSession
                    val emergencyBreakAvailable = System.currentTimeMillis() >= lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                    val emergencyBreakDaysRemaining = if (!emergencyBreakAvailable) {
                        val nextAvailable = lastEmergencyBreakMillis + (emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000)
                        ((nextAvailable - System.currentTimeMillis()) / (24 * 60 * 60 * 1000) + 1).toInt()
                    } else 0

                    Greeting(
                        focusMode = focusMode,
                        activeTagId = focusTagId,
                        namedTags = namedTags,
                        activeBlocker = activeManualBlocker,
                        activeSchedule = activeSchedule,
                        blockerLists = blockerLists,
                        focusPresets = focusPresets,
                        selectedPresetId = selectedPresetId,
                        focusDurationMinutes = focusDurationMinutes,
                        focusTimeRemaining = focusTimeRemaining,
                        isOnBreak = isOnBreak,
                        breakTimeRemaining = breakTimeRemaining,
                        breaksUsedThisSession = breaksUsedThisSession,
                        maxBreaksPerSession = effectiveMaxBreaks,
                        breaksAllowed = breaksAllowed,
                        sessionBreaksEnabled = sessionBreaksEnabled,
                        hideStopButton = hideStopButton,
                        nfcLockMode = nfcLockMode,
                        emergencyBreakAvailable = emergencyBreakAvailable,
                        emergencyBreakDaysRemaining = emergencyBreakDaysRemaining,
                        currentStreak = currentStreak,
                        onPresetSelected = { preset -> sessionVM.selectPreset(preset) },
                        onBlockerSelected = { blocker -> sessionVM.selectBlocker(blocker) },
                        onDurationSelected = { duration -> sessionVM.selectDuration(duration) },
                        onSessionBreaksToggled = { enabled -> sessionVM.toggleSessionBreaks(enabled) },
                        onStartClicked = {
                            if (focusMode) {
                                if (activeSchedule != null) {
                                    if (activeSchedule.unbindingTalismanId == null) {
                                        sessionVM.stopSessionWithSummary(activeSchedule)
                                        sessionVM.dispelSchedule()
                                    }
                                } else {
                                    sessionVM.stopSessionWithSummary(null)
                                    sessionVM.stopSession()
                                }
                            } else {
                                val blocker = activeManualBlocker
                                if (blocker != null) {
                                    sessionVM.startSession(blocker.name)
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
                            sessionVM.emergencyStop()
                            sessionVM.dispelSchedule()
                            Toast.makeText(context, context.getString(R.string.toast_emergency_stop), Toast.LENGTH_SHORT).show()
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
                            appTimeLimits = appTimeLimits,
                            conditionalUnlocks = conditionalUnlocks,
                            installedApps = installedApps,
                            onNavigateToEnchantments = { spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList) },
                            onNavigateToQuickSpells = { spellbookVM.navigateTo(SpellbookRoute.QuickSpellsList) },
                            onNavigateToRituals = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
                            onNavigateToTalismans = { spellbookVM.navigateTo(SpellbookRoute.Talismans) },
                            onNavigateToTimeLimits = { spellbookVM.navigateTo(SpellbookRoute.TimeLimits) },
                            onNavigateToConditionalUnlocks = { spellbookVM.navigateTo(SpellbookRoute.ConditionalUnlocks) },
                            modifier = contentModifier
                        )

                        is SpellbookRoute.EnchantmentsList -> {
                            val activeBlockerForList = if (focusMode) {
                                activeSchedule?.blockerName ?: activeManualBlocker?.name
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

                        is SpellbookRoute.TimeLimits -> TimeLimitsScreen(
                            installedApps = installedApps,
                            appTimeLimits = appTimeLimits,
                            onSaveAppTimeLimit = { pkg, limit -> spellbookVM.saveAppTimeLimit(pkg, limit) },
                            onDeleteAppTimeLimit = { spellbookVM.deleteAppTimeLimit(it) },
                            onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                            modifier = contentModifier
                        )

                        is SpellbookRoute.ConditionalUnlocks -> ConditionalUnlocksScreen(
                            conditionalUnlocks = conditionalUnlocks,
                            installedApps = installedApps,
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
                        modifier = contentModifier
                    )
                }
            }
        }
    }

    // Session summary dialog
    if (showSessionSummary) {
        AlertDialog(
            onDismissRequest = { sessionVM.dismissSessionSummary() },
            title = { Text(stringResource(R.string.session_complete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.session_complete_great_work))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.session_complete_duration, sessionSummaryDuration))
                    Text(stringResource(R.string.session_complete_breaks, sessionSummaryBreaks))
                    Text(stringResource(R.string.session_complete_enchantment, sessionSummaryBlocker))
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
