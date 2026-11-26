package com.infinicada.focuspocus.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.screens.ManaScreen
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

@Composable
fun ManaRoute(
    sessionVM: SessionViewModel,
    spellbookVM: SpellbookViewModel,
    progressionVM: ProgressionViewModel,
    onOpenProductiveApps: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
    val activeScheduleId by sessionVM.activeScheduleId.collectAsStateWithLifecycle()
    val sessionBreaksEnabled by sessionVM.sessionBreaksEnabled.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
    val wellChannelCost by progressionVM.wellChannelCost.collectAsStateWithLifecycle()
    val manaToday by progressionVM.manaToday.collectAsStateWithLifecycle()

    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    BackHandler(onBack = onNavigateBack)

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
        manaToday = manaToday,
        onOpenProductiveApps = onOpenProductiveApps,
        onNavigateBack = onNavigateBack,
        modifier = modifier.fillMaxSize()
    )
}
