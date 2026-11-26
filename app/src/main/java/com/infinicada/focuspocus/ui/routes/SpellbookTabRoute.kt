package com.infinicada.focuspocus.ui.routes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.ProductiveApps
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.navigation.SpellbookRoute
import com.infinicada.focuspocus.ui.screens.BlockerListScreen
import com.infinicada.focuspocus.ui.screens.CreateBlockerScreen
import com.infinicada.focuspocus.ui.screens.EditBlockerScreen
import com.infinicada.focuspocus.ui.screens.ProductiveAppsScreen
import com.infinicada.focuspocus.ui.screens.ScheduleEditorScreen
import com.infinicada.focuspocus.ui.screens.ScheduleListScreen
import com.infinicada.focuspocus.ui.screens.SpellbookScreen
import com.infinicada.focuspocus.ui.screens.TalismansScreen
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpellbookTabRoute(
    sessionVM: SessionViewModel,
    spellbookVM: SpellbookViewModel,
    progressionVM: ProgressionViewModel,
    lastScannedTagId: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val spellbookRoute by spellbookVM.spellbookRoute.collectAsStateWithLifecycle()
    val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val talismans by spellbookVM.talismans.collectAsStateWithLifecycle()
    val productiveApps by spellbookVM.productiveApps.collectAsStateWithLifecycle()
    val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
    val selectedBlocker by spellbookVM.selectedBlocker.collectAsStateWithLifecycle()

    val focusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
    val activeBlockerNames by sessionVM.activeBlockerNames.collectAsStateWithLifecycle()
    val activeScheduleId by sessionVM.activeScheduleId.collectAsStateWithLifecycle()
    val activeTalismanId by sessionVM.activeTalismanId.collectAsStateWithLifecycle()

    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    when (val currentRoute = spellbookRoute) {
        is SpellbookRoute.Overview -> SpellbookScreen(
            blockerLists = blockerLists,
            schedules = schedules,
            talismans = talismans,
            productiveAppCount = productiveApps.size,
            onNavigateToProductiveApps = { spellbookVM.navigateTo(SpellbookRoute.ProductiveApps) },
            onNavigateToEnchantments = { spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList) },
            onNavigateToRituals = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
            onNavigateToTalismans = { spellbookVM.navigateTo(SpellbookRoute.Talismans) },
            modifier = modifier
        )

        is SpellbookRoute.EnchantmentsList -> {
            val activeBlockersForList = if (focusMode) {
                (activeSchedule?.effectiveBlockerNames.orEmpty() + activeBlockerNames).toSet()
            } else emptySet()
            BlockerListScreen(
                blockerLists = blockerLists,
                activeBlockerNames = activeBlockersForList,
                onBlockerClick = {
                    spellbookVM.setSelectedBlocker(it)
                    spellbookVM.navigateTo(SpellbookRoute.EditEnchantment)
                },
                onCreateClick = { spellbookVM.navigateTo(SpellbookRoute.CreateEnchantment) },
                modifier = modifier
            )
        }

        is SpellbookRoute.CreateEnchantment -> CreateBlockerScreen(
            onSaveBlocker = {
                spellbookVM.saveBlocker(it)
                spellbookVM.navigateTo(SpellbookRoute.EnchantmentsList)
            },
            installedApps = installedApps,
            modifier = modifier
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
                modifier = modifier
            )
        }

        is SpellbookRoute.RitualsList -> {
            val ritualExceptions by spellbookVM.ritualExceptions.collectAsStateWithLifecycle()
            ScheduleListScreen(
                schedules = schedules,
                onScheduleClick = { schedule -> spellbookVM.navigateTo(SpellbookRoute.EditRitual(schedule)) },
                onCreateClick = { spellbookVM.navigateTo(SpellbookRoute.CreateRitual) },
                onDeleteSchedule = { spellbookVM.deleteSchedule(it) },
                activeScheduleId = activeScheduleId,
                ritualExceptions = ritualExceptions,
                onRequestSkip = { schedule, dayKey -> spellbookVM.requestRitualSkip(schedule, dayKey) },
                onCancelSkip = { scheduleId, dayKey -> spellbookVM.cancelRitualSkip(scheduleId, dayKey) },
                modifier = modifier
            )
        }

        is SpellbookRoute.CreateRitual -> ScheduleEditorScreen(
            scheduleToEdit = null,
            blockerLists = blockerLists,
            talismans = talismans,
            onSaveSchedule = {
                spellbookVM.saveSchedule(it)
                spellbookVM.navigateTo(SpellbookRoute.RitualsList)
            },
            onCancel = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
            modifier = modifier,
            existingSchedules = schedules
        )

        is SpellbookRoute.EditRitual -> ScheduleEditorScreen(
            scheduleToEdit = currentRoute.schedule,
            blockerLists = blockerLists,
            talismans = talismans,
            onSaveSchedule = {
                spellbookVM.saveSchedule(it)
                spellbookVM.navigateTo(SpellbookRoute.RitualsList)
            },
            onCancel = { spellbookVM.navigateTo(SpellbookRoute.RitualsList) },
            modifier = modifier,
            existingSchedules = schedules
        )

        is SpellbookRoute.Talismans -> {
            val lockedTalismanIds = if (focusMode) {
                setOfNotNull(activeTalismanId, activeSchedule?.unbindingTalismanId)
            } else emptySet()
            TalismansScreen(
                lastScannedTagId = lastScannedTagId,
                talismans = talismans,
                blockerLists = blockerLists,
                lockedTalismanIds = lockedTalismanIds,
                onSaveTag = { name ->
                    lastScannedTagId?.let { spellbookVM.saveTalisman(it, name) }
                },
                onSaveSetup = {
                    if (it.id !in lockedTalismanIds) spellbookVM.saveTalismanSetup(it)
                },
                onDeleteTag = {
                    if (it.id !in lockedTalismanIds) spellbookVM.deleteTalisman(it)
                },
                onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                modifier = modifier
            )
        }

        is SpellbookRoute.ProductiveApps -> {
            var usageGranted by remember {
                mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context))
            }
            var productiveMinutes by remember { mutableIntStateOf(0) }
            LifecycleResumeEffect(Unit) {
                usageGranted = UsageStatsHelper.hasUsageStatsPermission(context)
                onPauseOrDispose { }
            }
            LaunchedEffect(productiveApps, usageGranted) {
                productiveMinutes = withContext(Dispatchers.IO) {
                    spellbookVM.creditProductive()
                    ProductiveApps.minutesToday(
                        context,
                        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    ) ?: 0
                }
                progressionVM.refresh()
            }
            val manaToday by progressionVM.manaToday.collectAsStateWithLifecycle()
            ProductiveAppsScreen(
                installedApps = installedApps,
                productiveApps = productiveApps,
                minutesToday = productiveMinutes,
                manaToday = manaToday.productive,
                usageAccessGranted = usageGranted,
                onGrantUsageAccess = { UsageStatsHelper.openUsageAccessSettings(context) },
                onSetApps = { spellbookVM.setProductiveApps(it) },
                onNavigateBack = { spellbookVM.navigateTo(SpellbookRoute.Overview) },
                modifier = modifier
            )
        }
    }
}
