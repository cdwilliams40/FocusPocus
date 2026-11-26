package com.infinicada.focuspocus.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.ui.screens.insights.InsightsScreen
import com.infinicada.focuspocus.viewmodel.InsightsViewModel
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

@Composable
fun InsightsTabRoute(
    insightsVM: InsightsViewModel,
    spellbookVM: SpellbookViewModel,
    settingsVM: SettingsViewModel,
    progressionVM: ProgressionViewModel,
    onOpenMana: () -> Unit,
    modifier: Modifier = Modifier
) {
    val installedApps by spellbookVM.installedApps.collectAsStateWithLifecycle()
    val focusSessions by insightsVM.focusSessions.collectAsStateWithLifecycle()
    val currentStreak by insightsVM.currentStreak.collectAsStateWithLifecycle()
    val longestStreak by insightsVM.longestStreak.collectAsStateWithLifecycle()
    val blockEvents by insightsVM.blockEvents.collectAsStateWithLifecycle()
    val appOpenDailyStats by insightsVM.appOpenDailyStats.collectAsStateWithLifecycle()
    val wellDays by insightsVM.wellDays.collectAsStateWithLifecycle()
    val manaDays by insightsVM.manaDays.collectAsStateWithLifecycle()
    val ledger by progressionVM.ledger.collectAsStateWithLifecycle()
    val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
    val progressionEnabled by settingsVM.progressionEnabled.collectAsStateWithLifecycle()

    InsightsScreen(
        installedApps = installedApps,
        focusSessions = focusSessions,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        blockEvents = blockEvents,
        openDailyStats = appOpenDailyStats,
        wellDays = wellDays,
        manaDays = manaDays,
        ledger = ledger,
        progressionEnabled = progressionEnabled,
        manaBalance = manaBalance,
        wellRefillIntervalMinutes = remember { spellbookVM.getWellSnapshot().refillIntervalMinutes },
        onOpenMana = onOpenMana,
        modifier = modifier
    )
}
