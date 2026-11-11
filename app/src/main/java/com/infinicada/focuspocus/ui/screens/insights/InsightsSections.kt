package com.infinicada.focuspocus.ui.screens.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.infinicada.focuspocus.AppUsage
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.insights.FocusSource
import com.infinicada.focuspocus.insights.InsightsRange
import com.infinicada.focuspocus.insights.InsightsReport
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ────────────────────────────────────────────────────────────
//  THE WELL
// ────────────────────────────────────────────────────────────

@Composable
fun WellSection(
    well: InsightsReport.Well,
    days: List<String>,
    showChart: Boolean,
    refillIntervalMinutes: Int,
    names: Map<String, String>
) {
    SectionHeader(stringResource(R.string.insights_well_header))
    Spacer(modifier = Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        if (showChart) {
            DayBarChart(
                days = days,
                values = well.minutesPerDay.map { intArrayOf(it) },
                seriesNames = listOf(stringResource(R.string.insights_well_drawn)),
                colors = listOf(MaterialTheme.colorScheme.tertiary),
                format = { minutesText(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (refillIntervalMinutes > 0) {
            Note(stringResource(R.string.insights_well_refill_per_day, 24 * 60 / refillIntervalMinutes))
        }
        Line(stringResource(R.string.insights_well_drawn), minutesText(well.totalMinutes))
        Line(stringResource(R.string.insights_well_pacts), "${well.pacts}")
        if (well.pacts > 0) Line(stringResource(R.string.insights_well_average_pact), minutesText(well.averagePactMinutes))
        Line(stringResource(R.string.insights_well_returned), minutesText(well.refundedMinutes))
        Line(stringResource(R.string.insights_well_pours), "${well.pours}")
        Line(stringResource(R.string.insights_well_dry), "${well.dryOpens}")
        if (well.perApp.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Subhead(stringResource(R.string.insights_well_by_app))
            val max = well.perApp.first().second
            well.perApp.take(5).forEach { (pkg, minutes) ->
                RankedBarRow(names[pkg] ?: pkg, minutesText(minutes), minutes, max, MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  REFLEXES
// ────────────────────────────────────────────────────────────

@Composable
fun ReflexSection(
    reflexes: InsightsReport.Reflexes,
    days: List<String>,
    showChart: Boolean,
    names: Map<String, String>
) {
    SectionHeader(stringResource(R.string.insights_reflex_header))
    Spacer(modifier = Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        val percent = if (reflexes.opens == 0) 0 else reflexes.reflexOpens * 100 / reflexes.opens
        Line(stringResource(R.string.insights_reflex_opens), "${reflexes.opens}")
        Line(stringResource(R.string.insights_reflex_reflexes), stringResource(R.string.insights_reflex_share, reflexes.reflexOpens, percent))
        Note(stringResource(R.string.insights_reflex_hint))
        if (showChart && reflexes.opens > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            DayBarChart(
                days = days,
                values = reflexes.reflexesPerDay.map { intArrayOf(it) },
                seriesNames = listOf(stringResource(R.string.insights_reflex_reflexes)),
                colors = listOf(MaterialTheme.colorScheme.primary),
                format = { "$it" }
            )
        }
        if (reflexes.perApp.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Subhead(stringResource(R.string.insights_reflex_by_app))
            val max = reflexes.perApp.first().opens
            reflexes.perApp.take(5).forEach { app ->
                RankedBarRow(
                    names[app.packageName] ?: app.packageName,
                    stringResource(R.string.insights_opens_row_count, app.opens, app.reflexOpens),
                    app.opens, max, MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  FOCUS
// ────────────────────────────────────────────────────────────

@Composable
private fun sourceName(source: FocusSource): String = stringResource(
    when (source) {
        FocusSource.MANUAL -> R.string.insights_source_manual
        FocusSource.RITUAL -> R.string.insights_source_ritual
        FocusSource.TALISMAN -> R.string.insights_source_talisman
    }
)

@Composable
fun FocusSection(
    focus: InsightsReport.Focus,
    days: List<String>,
    showChart: Boolean,
    names: Map<String, String>
) {
    val colors = seriesColors()
    val sourceNames = FocusSource.entries.map { sourceName(it) }
    SectionHeader(stringResource(R.string.insights_focus_header))
    Spacer(modifier = Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Line(stringResource(R.string.insights_focus_sessions), "${focus.sessions}")
        if (focus.sessions > 0) Line(stringResource(R.string.insights_avg_session), minutesText(focus.averageSessionMinutes))
        Spacer(modifier = Modifier.height(8.dp))
        if (showChart) {
            DayBarChart(
                days = days,
                values = focus.minutesPerDay,
                seriesNames = sourceNames,
                colors = colors,
                format = { minutesText(it) }
            )
        } else {
            FocusSource.entries.forEach { src ->
                LegendRow(colors[src.ordinal], sourceNames[src.ordinal], minutesText(focus.minutesBySource[src] ?: 0))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Line(stringResource(R.string.insights_focus_blocked), "${focus.blockedAttempts}")
        if (focus.mostBlocked.isNotEmpty()) {
            val max = focus.mostBlocked.first().second
            focus.mostBlocked.forEach { (pkg, count) ->
                RankedBarRow(names[pkg] ?: pkg, "$count", count, max, MaterialTheme.colorScheme.primary)
            }
        }
        if (focus.recentSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Subhead(stringResource(R.string.insights_session_history))
            val format = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
            focus.recentSessions.take(5).forEach { s -> SessionRow(s, format) }
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession, format: SimpleDateFormat) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.blockerName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${sourceName(InsightsReport.sourceOf(session))} · ${format.format(Date(session.endTimeMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(minutesText(session.durationMinutes), style = MaterialTheme.typography.bodyMedium)
    }
}

// ────────────────────────────────────────────────────────────
//  MANA
// ────────────────────────────────────────────────────────────

@Composable
fun ManaSection(
    mana: InsightsReport.Mana,
    days: List<String>,
    showChart: Boolean,
    balance: Long,
    onOpenMana: () -> Unit
) {
    SectionHeader(stringResource(R.string.insights_mana_header))
    Spacer(modifier = Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Line(stringResource(R.string.insights_mana_balance_label), "$balance")
        Line(stringResource(R.string.insights_mana_earned), "+${mana.earned}")
        Line(stringResource(R.string.insights_mana_spent), spentText(mana.spent))
        if (showChart && mana.earned > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            DayBarChart(
                days = days,
                values = mana.earnedPerDay.map { intArrayOf(it.toInt()) },
                seriesNames = listOf(stringResource(R.string.insights_mana_earned)),
                colors = listOf(MaterialTheme.colorScheme.tertiary),
                format = { "+$it" }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Subhead(stringResource(R.string.insights_mana_from))
        Line(stringResource(R.string.insights_mana_from_timed), "+${mana.timedSessions}")
        Line(stringResource(R.string.insights_mana_from_open_ended), "+${mana.openEndedSessions}")
        Line(stringResource(R.string.insights_mana_from_productive), "+${mana.productive}")
        Line(stringResource(R.string.insights_mana_from_trials, mana.trialsCompleted), "+${mana.trials}")
        Spacer(modifier = Modifier.height(12.dp))
        Subhead(stringResource(R.string.insights_mana_went))
        Line(stringResource(R.string.insights_mana_pours, mana.pours.size), spentText(mana.pourMana))
        Line(stringResource(R.string.insights_mana_breaks), spentText(mana.breakMana))
        if (mana.pours.isNotEmpty()) {
            val format = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
            mana.pours.take(5).forEach { pour ->
                Note("${format.format(Date(pour.timestampMillis))} · ${-pour.amount}")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenMana, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.insights_open_mana))
        }
    }
}

// ────────────────────────────────────────────────────────────
//  SCREEN TIME (collapsed by default)
// ────────────────────────────────────────────────────────────

@Composable
fun ScreenTimeSection(range: InsightsRange, names: Map<String, String>) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var granted by rememberSaveable { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    var resumes by rememberSaveable { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        granted = UsageStatsHelper.hasUsageStatsPermission(context)
        resumes++
        onPauseOrDispose { }
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.insights_screen_time), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (!expanded) return@GlassCard
        Spacer(modifier = Modifier.height(8.dp))
        if (!granted) {
            Note(stringResource(R.string.insights_usage_access_desc))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { UsageStatsHelper.openUsageAccessSettings(context) }) {
                Text(stringResource(R.string.insights_grant_usage_access))
            }
            return@GlassCard
        }
        // Full-period usage walks the event stream: off the main thread.
        val usage by produceState<List<AppUsage>?>(initialValue = null, range, resumes) {
            value = withContext(Dispatchers.IO) {
                when (range) {
                    InsightsRange.TODAY -> UsageStatsHelper.getTodayUsage(context)
                    InsightsRange.WEEK -> UsageStatsHelper.getWeeklyUsage(context)
                    InsightsRange.MONTH -> UsageStatsHelper.getMonthlyUsage(context)
                }
            }
        }
        val list = usage?.filter { it.totalTimeInForeground >= 60_000L }?.sortedByDescending { it.totalTimeInForeground }
        when {
            list == null -> Note("…")
            list.isEmpty() -> Note(stringResource(R.string.insights_no_usage_data))
            else -> {
                val total = (list.sumOf { it.totalTimeInForeground } / 60_000L).toInt()
                Line(stringResource(R.string.insights_screen_time_total), minutesText(total))
                val max = (list.first().totalTimeInForeground / 60_000L).toInt()
                list.take(15).forEach { app ->
                    val minutes = (app.totalTimeInForeground / 60_000L).toInt()
                    RankedBarRow(
                        names[app.packageName] ?: app.appName, minutesText(minutes), minutes, max,
                        MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  SMALL PIECES
// ────────────────────────────────────────────────────────────

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun spentText(amount: Long): String = if (amount == 0L) "0" else "−$amount"

@Composable
private fun Subhead(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
