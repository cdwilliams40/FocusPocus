package com.infinicada.focuspocus.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.navigation.AppDestinations
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.navigation.WellRoute
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.routes.FocusTabRoute
import com.infinicada.focuspocus.ui.routes.InsightsTabRoute
import com.infinicada.focuspocus.ui.routes.ManaRoute
import com.infinicada.focuspocus.ui.routes.SessionSummaryDialog
import com.infinicada.focuspocus.ui.routes.SettingsRoute
import com.infinicada.focuspocus.ui.routes.SpellbookTabRoute
import com.infinicada.focuspocus.ui.routes.WellTabRoute
import com.infinicada.focuspocus.ui.screens.OnboardingScreen
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
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val sessionVM: SessionViewModel = viewModel()
    val spellbookVM: SpellbookViewModel = viewModel()
    val settingsVM: SettingsViewModel = viewModel()
    val insightsVM: InsightsViewModel = viewModel()
    val progressionVM: ProgressionViewModel = viewModel()

    val context = LocalContext.current

    // Minimal top-level state needed for gating, navigation, and cross-VM refreshes
    val sessionState by sessionVM.sessionState.collectAsStateWithLifecycle()
    val showSessionSummary by sessionVM.showSessionSummary.collectAsStateWithLifecycle()
    val sessionSummaryDuration by sessionVM.sessionSummaryDuration.collectAsStateWithLifecycle()
    val sessionSummaryBreaks by sessionVM.sessionSummaryBreaks.collectAsStateWithLifecycle()
    val sessionSummaryBlocker by sessionVM.sessionSummaryBlocker.collectAsStateWithLifecycle()
    val sessionSummaryMana by sessionVM.sessionSummaryMana.collectAsStateWithLifecycle()
    val sessionSummaryTrials by sessionVM.sessionSummaryTrials.collectAsStateWithLifecycle()

    val onboardingCompleted by settingsVM.onboardingCompleted.collectAsStateWithLifecycle()
    val analyticsConsent by settingsVM.analyticsConsent.collectAsStateWithLifecycle()
    val showAnalyticsConsentDialog by settingsVM.showAnalyticsConsentDialog.collectAsStateWithLifecycle()
    val showProgressionIntroDialog by settingsVM.showProgressionIntroDialog.collectAsStateWithLifecycle()
    val showPactsHomeIntroDialog by settingsVM.showPactsHomeIntroDialog.collectAsStateWithLifecycle()

    val spellbookRoute by spellbookVM.spellbookRoute.collectAsStateWithLifecycle()
    val wellRoute by spellbookVM.wellRoute.collectAsStateWithLifecycle()
    val dataVersion by spellbookVM.dataVersion.collectAsStateWithLifecycle()

    // Pause display countdown tickers while backgrounded; advance & re-sync on resume.
    LifecycleStartEffect(Unit) {
        sessionVM.onUiStarted()
        onStopOrDispose { sessionVM.onUiStopped() }
    }
    LifecycleResumeEffect(Unit) {
        sessionVM.syncFromPrefs()
        insightsVM.refresh()
        progressionVM.refresh()
        onPauseOrDispose { }
    }

    // Refresh insights & progression whenever session state transitions or spellbook data changes.
    LaunchedEffect(sessionState) {
        insightsVM.refresh()
        progressionVM.refresh()
    }
    LaunchedEffect(dataVersion) {
        if (dataVersion != 0) {
            sessionVM.syncFromPrefs()
            insightsVM.refresh()
            progressionVM.refresh()
        }
    }
    LaunchedEffect(showSessionSummary) {
        if (showSessionSummary) {
            progressionVM.refresh()
            insightsVM.refresh()
        }
    }

    // Onboarding screen
    if (!onboardingCompleted) {
        val talismans by spellbookVM.talismans.collectAsStateWithLifecycle()
        val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
        OnboardingScreen(
            talismans = talismans,
            installedApps = installedApps,
            isServiceEnabled = isServiceEnabled,
            analyticsConsent = analyticsConsent,
            onAnalyticsConsentChanged = { settingsVM.applyAnalyticsConsent(it) },
            onCreateFirstPacts = { packages -> spellbookVM.createDefaultPacts(packages) },
            onSaveTag = { name ->
                lastScannedTagId?.let { spellbookVM.saveTalisman(it, name) }
            },
            onComplete = {
                settingsVM.completeOnboarding()
                onRequestNotificationPermission()
            }
        )
        return
    }

    // One-time dialogs (queued so only one shows at a time)
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

    // Accessibility prominent disclosure + consent (Play policy)
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
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMana by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        SettingsRoute(
            settingsVM = settingsVM,
            sessionVM = sessionVM,
            spellbookVM = spellbookVM,
            progressionVM = progressionVM,
            onNavigateBack = { showSettings = false },
            modifier = modifier
        )
        return
    }

    if (showMana) {
        ManaRoute(
            sessionVM = sessionVM,
            spellbookVM = spellbookVM,
            progressionVM = progressionVM,
            onOpenProductiveApps = {
                showMana = false
                currentDestination = AppDestinations.SPELLBOOK
                spellbookVM.navigateTo(SpellbookRoute.ProductiveApps)
            },
            onNavigateBack = { showMana = false },
            modifier = modifier
        )
        return
    }

    if (spellbookRoute !is SpellbookRoute.Overview && currentDestination == AppDestinations.SPELLBOOK) {
        BackHandler { spellbookVM.handleBack() }
    }
    if (wellRoute !is WellRoute.Overview && currentDestination == AppDestinations.HOME) {
        BackHandler { spellbookVM.handleWellBack() }
    }

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
                            if (it == AppDestinations.HOME) {
                                spellbookVM.navigateToWell(WellRoute.Overview)
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
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.main_settings_content_desc)
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)
                when (currentDestination) {
                    AppDestinations.HOME -> WellTabRoute(
                        sessionVM = sessionVM,
                        spellbookVM = spellbookVM,
                        settingsVM = settingsVM,
                        progressionVM = progressionVM,
                        onOpenMana = { showMana = true },
                        onOpenFocus = { currentDestination = AppDestinations.FOCUS },
                        modifier = contentModifier
                    )

                    AppDestinations.FOCUS -> FocusTabRoute(
                        sessionVM = sessionVM,
                        spellbookVM = spellbookVM,
                        settingsVM = settingsVM,
                        progressionVM = progressionVM,
                        onCreateEnchantment = {
                            currentDestination = AppDestinations.SPELLBOOK
                            spellbookVM.navigateTo(SpellbookRoute.CreateEnchantment)
                        },
                        modifier = contentModifier
                    )

                    AppDestinations.SPELLBOOK -> SpellbookTabRoute(
                        sessionVM = sessionVM,
                        spellbookVM = spellbookVM,
                        progressionVM = progressionVM,
                        lastScannedTagId = lastScannedTagId,
                        modifier = contentModifier
                    )

                    AppDestinations.INSIGHTS -> InsightsTabRoute(
                        insightsVM = insightsVM,
                        spellbookVM = spellbookVM,
                        settingsVM = settingsVM,
                        progressionVM = progressionVM,
                        onOpenMana = { showMana = true },
                        modifier = contentModifier
                    )
                }
            }
        }
    }

    if (showSessionSummary) {
        SessionSummaryDialog(
            durationMinutes = sessionSummaryDuration,
            breaksUsed = sessionSummaryBreaks,
            blockerName = sessionSummaryBlocker,
            manaEarned = sessionSummaryMana,
            completedTrials = sessionSummaryTrials,
            onDismiss = { sessionVM.dismissSessionSummary() }
        )
    }
}
