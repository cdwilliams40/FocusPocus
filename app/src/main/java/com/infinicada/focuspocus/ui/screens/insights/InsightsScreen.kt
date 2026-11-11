package com.infinicada.focuspocus.ui.screens.insights

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.insights.InsightsRange
import com.infinicada.focuspocus.insights.InsightsReport
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.WellDay
import com.infinicada.focuspocus.ui.components.StatTile

/**
 * Insights, organised around the app's three parts — the Well, focus
 * sessions, mana — plus reflexes and screen time. One range switch (today,
 * 7 days, 30 days) drives every section. All figures come from
 * [InsightsReport], which is built from stored history only.
 */
@Composable
fun InsightsScreen(
    installedApps: List<AppInfo>,
    focusSessions: List<FocusSession>,
    currentStreak: Int,
    longestStreak: Int,
    blockEvents: List<BlockEvent>,
    openDailyStats: Map<String, Map<String, AppOpenStats>>,
    wellDays: Map<String, WellDay>,
    manaDays: Map<String, ManaDay>,
    ledger: List<ManaLedgerEntry>,
    progressionEnabled: Boolean,
    manaBalance: Long,
    wellRefillIntervalMinutes: Int,
    onOpenMana: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var rangeIndex by remember {
        // Older builds stored a fourth "all time" tab; it maps to 30 days.
        mutableIntStateOf(prefs.getInt(Constants.PrefsKeys.INSIGHTS_TIME_RANGE, 0).coerceIn(0, InsightsRange.entries.lastIndex))
    }
    val range = InsightsRange.entries[rangeIndex]
    // Re-read on every resume so the window can't go stale across midnight.
    var today by remember { mutableStateOf(DayKey.today()) }
    LifecycleResumeEffect(Unit) {
        today = DayKey.today()
        onPauseOrDispose { }
    }
    val names = remember(installedApps) { installedApps.associate { it.packageName to it.name } }
    val report = remember(range, today, wellDays, manaDays, openDailyStats, focusSessions, blockEvents, ledger) {
        InsightsReport.build(
            days = InsightsReport.daysFor(range, today),
            wellDays = wellDays,
            manaDays = manaDays,
            openStats = openDailyStats,
            sessions = focusSessions,
            blockEvents = blockEvents,
            ledger = ledger
        )
    }
    val showCharts = range != InsightsRange.TODAY

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.insights_range_today,
                    R.string.insights_range_week,
                    R.string.insights_range_month
                ).forEachIndexed { i, label ->
                    FilterChip(
                        selected = rangeIndex == i,
                        onClick = {
                            rangeIndex = i
                            prefs.edit { putInt(Constants.PrefsKeys.INSIGHTS_TIME_RANGE, i) }
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }

        item { SummaryTiles(report, currentStreak, longestStreak, progressionEnabled) }
        item { WellSection(report.well, report.days, showCharts, wellRefillIntervalMinutes, names) }
        item { ReflexSection(report.reflexes, report.days, showCharts, names) }
        item { FocusSection(report.focus, report.days, showCharts, names) }
        if (progressionEnabled) {
            item { ManaSection(report.mana, report.days, showCharts, manaBalance, onOpenMana) }
        }
        item { ScreenTimeSection(range, names) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SummaryTiles(
    report: InsightsReport,
    currentStreak: Int,
    longestStreak: Int,
    progressionEnabled: Boolean
) {
    val net = report.mana.earned - report.mana.spent
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = minutesText(report.well.totalMinutes),
                label = stringResource(R.string.insights_summary_well),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = minutesText(report.focus.totalMinutes),
                label = stringResource(R.string.insights_summary_focus),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progressionEnabled) {
                StatTile(
                    value = if (net >= 0) "+$net" else "$net",
                    label = stringResource(R.string.insights_summary_net_mana),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            StatTile(
                value = pluralStringResource(R.plurals.insights_streak_days, currentStreak, currentStreak),
                label = stringResource(R.string.insights_summary_streak, maxOf(longestStreak, currentStreak)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
