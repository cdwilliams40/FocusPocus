package com.infinicada.focuspocus.ui.routes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.DisplayTicks
import com.infinicada.focuspocus.ProtectionHealth
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.navigation.WellRoute
import com.infinicada.focuspocus.ui.screens.WellAppsScreen
import com.infinicada.focuspocus.ui.screens.WellHomeScreen
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun WellTabRoute(
    sessionVM: SessionViewModel,
    spellbookVM: SpellbookViewModel,
    settingsVM: SettingsViewModel,
    progressionVM: ProgressionViewModel,
    onOpenMana: () -> Unit,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val wellRoute by spellbookVM.wellRoute.collectAsStateWithLifecycle()
    val wellApps by spellbookVM.wellApps.collectAsStateWithLifecycle()
    val pendingRemovals by spellbookVM.pendingRemovals.collectAsStateWithLifecycle()
    val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
    val dataVersion by spellbookVM.dataVersion.collectAsStateWithLifecycle()

    if (wellRoute is WellRoute.Overview) {
        val focusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
        val isOnBreak by sessionVM.isOnBreak.collectAsStateWithLifecycle()
        val activeBlockerNames by sessionVM.activeBlockerNames.collectAsStateWithLifecycle()
        val activeScheduleId by sessionVM.activeScheduleId.collectAsStateWithLifecycle()
        val focusTimeRemaining by sessionVM.focusTimeRemaining.collectAsStateWithLifecycle()
        val breakTimeRemaining by sessionVM.breakTimeRemaining.collectAsStateWithLifecycle()

        val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
        val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
        val progressionEnabled by settingsVM.progressionEnabled.collectAsStateWithLifecycle()
        val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
        val trials by progressionVM.trials.collectAsStateWithLifecycle()
        val wellChannelCost by progressionVM.wellChannelCost.collectAsStateWithLifecycle()

        val activeSchedule = remember(activeScheduleId, schedules) {
            schedules.find { it.id == activeScheduleId }
        }
        val activeManualBlockers = remember(activeBlockerNames, blockerLists) {
            blockerLists.filter { it.name in activeBlockerNames }
        }

        var homeTick by remember { mutableIntStateOf(0) }
        var protectionStatus by remember { mutableStateOf(ProtectionHealth.check(context)) }
        LifecycleResumeEffect(Unit) {
            homeTick++
            protectionStatus = ProtectionHealth.check(context)
            onPauseOrDispose { }
        }

        var activePacts by remember { mutableStateOf(emptyMap<String, Long>()) }
        var openStats by remember { mutableStateOf(emptyMap<String, AppOpenStats>()) }
        var homeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var homeWell by remember { mutableStateOf(spellbookVM.getWellSnapshot()) }
        var sessionBlocked by remember { mutableStateOf(emptySet<String>()) }

        LaunchedEffect(focusMode, isOnBreak, activeBlockerNames, wellApps) {
            sessionBlocked = withContext(Dispatchers.IO) { spellbookVM.getSessionBlockedWellApps() }
        }
        LaunchedEffect(dataVersion, homeTick) {
            val snapshot = withContext(Dispatchers.IO) {
                spellbookVM.applyDueWellRemovals()
                val productiveMana = spellbookVM.creditProductive()
                Triple(
                    spellbookVM.getActivePacts(),
                    spellbookVM.getTodayOpenStats(),
                    spellbookVM.getWellSnapshot()
                ) to productiveMana
            }
            activePacts = snapshot.first.first
            openStats = snapshot.first.second
            homeWell = snapshot.first.third
            homeNow = System.currentTimeMillis()
            if (snapshot.second > 0) progressionVM.refresh()
        }
        LaunchedEffect(activePacts, pendingRemovals) {
            val expiries = activePacts.values + pendingRemovals.values
            while (true) {
                val now = System.currentTimeMillis()
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
            batteryUnrestricted = protectionStatus.batteryUnrestricted,
            onFixBattery = { ProtectionHealth.openBatteryOptimizationSettings(context) },
            onOpenMana = onOpenMana,
            onOpenFocus = onOpenFocus,
            onEditWellApps = { spellbookVM.navigateToWell(WellRoute.EditWellApps) },
            onEndPact = { pkg ->
                val refunded = spellbookVM.endPactNow(pkg)
                val ended = resources.getQuantityString(
                    R.plurals.home_end_pact_result, 1, 1
                )
                Toast.makeText(
                    context,
                    if (refunded > 0) {
                        ended + " · " + resources.getString(R.string.well_refund_toast, refunded)
                    } else {
                        ended
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },
            onRequestTime = { pkg, minutes ->
                spellbookVM.requestPactTime(pkg, minutes)
            },
            sessionBlockedPackages = sessionBlocked,
            onBlockedBySession = { appName ->
                Toast.makeText(
                    context,
                    resources.getString(R.string.home_request_blocked_by_session, appName),
                    Toast.LENGTH_SHORT
                ).show()
            },
            well = homeWell,
            wellChannelCost = wellChannelCost,
            onChannelMana = {
                val channeled = progressionVM.redeemPerk(Perk.WELL_CHANNEL)
                Toast.makeText(
                    context,
                    if (channeled) {
                        resources.getString(R.string.well_channel_done, Perk.WELL_CHANNEL_GRANT)
                    } else {
                        resources.getString(R.string.well_channel_failed)
                    },
                    Toast.LENGTH_SHORT
                ).show()
                homeTick++
                channeled
            },
            trials = trials,
            onClaimTrial = { trial -> progressionVM.claimTrial(trial.id) },
            modifier = modifier
        )
    } else {
        var editorNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(pendingRemovals) {
            editorNow = System.currentTimeMillis()
            while (true) {
                delay(DisplayTicks.untilNextWallClockMinute(System.currentTimeMillis()) + 1_000L)
                editorNow = System.currentTimeMillis()
            }
        }
        val productiveApps by spellbookVM.productiveApps.collectAsStateWithLifecycle()
        WellAppsScreen(
            installedApps = installedApps,
            wellApps = wellApps,
            pendingRemovals = pendingRemovals,
            nowMillis = editorNow,
            onAddApps = { spellbookVM.addWellApps(it) },
            onSetSubstitute = { pkg, sub -> spellbookVM.setWellAppSubstitute(pkg, sub) },
            onRequestRemoval = { spellbookVM.requestWellAppRemoval(it) },
            onCancelRemoval = { spellbookVM.cancelWellAppRemoval(it) },
            onNavigateBack = { spellbookVM.navigateToWell(WellRoute.Overview) },
            productiveApps = productiveApps,
            modifier = modifier
        )
    }
}
