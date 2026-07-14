package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardHeadline
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardRow
import com.infinicada.focuspocus.limit.GuardState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.TodayRollup
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.ManaChip
import com.infinicada.focuspocus.ui.components.formatClock
import com.infinicada.focuspocus.ui.formatDuration

/**
 * The app's default screen: a dashboard of standing protection. One card per
 * guard (a pact'd or time-limited app, or a whole pact circle) with its live
 * state — sealed, pact running, limit spent, or quiet with today's open
 * counts — plus a banner linking to the Focus tab while a session runs.
 *
 * All state is hoisted; the row list is derived here from the raw snapshots
 * via [GuardStatus.buildRows] so the ordering/priority logic stays testable.
 */
@Composable
fun PactsHomeScreen(
    installedApps: List<AppInfo>,
    appTimeLimitConfigs: Map<String, AppTimeLimit>,
    pactGroups: List<PactGroup>,
    blockerLists: List<Blocker>,
    todayOpenStats: Map<String, AppOpenStats>,
    guardLiveStates: Map<String, GuardLiveState>,
    nowMillis: Long,
    sessionActive: Boolean,
    isOnBreak: Boolean,
    sessionLabel: String,
    sessionTimeRemaining: Int,
    breakTimeRemaining: Int,
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    onOpenBoons: () -> Unit,
    onOpenFocus: () -> Unit,
    onMakePact: () -> Unit,
    onGuardClick: (GuardRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val names = remember(installedApps) {
        installedApps.associate { it.packageName to it.name }
    }
    val rows = remember(
        appTimeLimitConfigs, pactGroups, blockerLists, guardLiveStates, todayOpenStats, names, nowMillis
    ) {
        GuardStatus.buildRows(
            configs = appTimeLimitConfigs,
            groups = pactGroups,
            blockers = blockerLists,
            liveStates = guardLiveStates,
            openStats = todayOpenStats,
            names = names,
            now = nowMillis
        )
    }
    val headline = remember(rows) { GuardStatus.headlineCounts(rows) }
    val rollup = remember(rows) { GuardStatus.todayRollup(rows) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        item {
            DashboardHeader(
                headline = headline,
                rollup = rollup,
                showRollup = GuardStatus.hasPactRows(rows),
                anyGuards = rows.isNotEmpty(),
                progressionEnabled = progressionEnabled,
                manaBalance = manaBalance,
                currentStreak = currentStreak,
                onOpenBoons = onOpenBoons
            )
        }

        if (sessionActive) {
            item {
                SessionBanner(
                    isOnBreak = isOnBreak,
                    sessionLabel = sessionLabel,
                    sessionTimeRemaining = sessionTimeRemaining,
                    breakTimeRemaining = breakTimeRemaining,
                    onClick = onOpenFocus
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (rows.isEmpty()) {
            item {
                GuardsEmptyState(onMakePact = onMakePact)
            }
        } else {
            items(rows) { row ->
                when (row) {
                    is GuardRow.App -> GuardAppCard(
                        row = row,
                        names = names,
                        onClick = { onGuardClick(row) }
                    )
                    is GuardRow.Circle -> GuardCircleCard(
                        row = row,
                        names = names,
                        onClick = { onGuardClick(row) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onMakePact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.home_guard_make_pact))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  HEADER: status headline, today rollup, mana/streak chips
// ────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    headline: GuardHeadline,
    rollup: TodayRollup,
    showRollup: Boolean,
    anyGuards: Boolean,
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    onOpenBoons: () -> Unit
) {
    if (anyGuards) {
        val sealedText = if (headline.sealedCount > 0) {
            pluralStringResource(
                R.plurals.home_guard_sealed_count, headline.sealedCount, headline.sealedCount
            )
        } else null
        val activeText = if (headline.pactActiveCount > 0) {
            pluralStringResource(
                R.plurals.home_guard_active_count, headline.pactActiveCount, headline.pactActiveCount
            )
        } else null
        val headlineText = when {
            sealedText != null && activeText != null ->
                stringResource(R.string.home_guard_headline_join, sealedText, activeText)
            sealedText != null -> sealedText
            activeText != null -> activeText
            else -> stringResource(R.string.home_guard_all_quiet)
        }
        Text(
            text = headlineText,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        if (showRollup) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_guard_rollup, rollup.opens, rollup.reflexOpens),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Same rule as the old Home header: chips never share a row with the
    // headline, so narrow screens don't squeeze their labels into tall bars.
    if (progressionEnabled || currentStreak > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (progressionEnabled) {
                ManaChip(balance = manaBalance, onClick = onOpenBoons)
            }
            if (currentStreak > 0) {
                StreakBadge(currentStreak)
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun StreakBadge(streak: Int) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                stringResource(R.string.home_day_streak, streak),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    )
}

// ────────────────────────────────────────────────────────────
//  ACTIVE SESSION BANNER
// ────────────────────────────────────────────────────────────

@Composable
private fun SessionBanner(
    isOnBreak: Boolean,
    sessionLabel: String,
    sessionTimeRemaining: Int,
    breakTimeRemaining: Int,
    onClick: () -> Unit
) {
    val accent = if (isOnBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOnBreak) Icons.Filled.Coffee else Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isOnBreak) stringResource(R.string.home_status_on_break)
                           else stringResource(R.string.home_status_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                val detail = when {
                    isOnBreak -> stringResource(
                        R.string.home_session_banner_time_left, formatClock(breakTimeRemaining)
                    )
                    sessionTimeRemaining > 0 && sessionLabel.isNotEmpty() -> stringResource(
                        R.string.home_session_banner_detail,
                        sessionLabel,
                        formatClock(sessionTimeRemaining)
                    )
                    sessionTimeRemaining > 0 -> stringResource(
                        R.string.home_session_banner_time_left, formatClock(sessionTimeRemaining)
                    )
                    else -> sessionLabel
                }
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.home_session_banner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  GUARD CARDS
// ────────────────────────────────────────────────────────────

@Composable
private fun GuardAppCard(
    row: GuardRow.App,
    names: Map<String, String>,
    onClick: () -> Unit
) {
    val appName = names[row.packageName] ?: row.packageName
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                AppIcon(
                    packageName = row.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                if (row.config.pactModeEnabled) {
                    PactCardBody(row, names)
                } else {
                    WardCardBody(row)
                }
            }
            GuardStateChip(state = row.state)
        }
    }
}

/** Config summary, live state, and today's counters for a pact-style row. */
@Composable
private fun PactCardBody(row: GuardRow.App, names: Map<String, String>) {
    val effectiveMax = if (row.config.pactMaxMinutes > 0) row.config.pactMaxMinutes else 15
    Text(
        stringResource(R.string.pacts_config_summary, effectiveMax, row.config.cooldownMinutes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    row.config.pactAlternativePackage?.let { altPkg ->
        Text(
            stringResource(R.string.pacts_alternative_summary, names[altPkg] ?: altPkg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (row.config.dailyLimitMinutes > 0) {
        Text(
            stringResource(R.string.pacts_backstop_summary, row.config.dailyLimitMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    GuardStateLine(row)
    Text(
        stringResource(R.string.pacts_today_stats, row.opensToday, row.reflexesToday),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary
    )
}

/** Daily-limit progress and cooldown summary for a ward (time-limit) row. */
@Composable
private fun WardCardBody(row: GuardRow.App) {
    if (row.config.dailyLimitMinutes > 0) {
        val overLimit = row.usedMinutesToday >= row.config.dailyLimitMinutes
        Text(
            stringResource(
                R.string.time_limits_used_today, row.usedMinutesToday, row.config.dailyLimitMinutes
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (overLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = {
                (row.usedMinutesToday.toFloat() / row.config.dailyLimitMinutes).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            color = if (overLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
        )
    }
    if (row.config.sessionLimitMinutes > 0) {
        Text(
            stringResource(
                R.string.time_limits_cooldown_desc,
                row.config.sessionLimitMinutes,
                row.config.cooldownMinutes
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    GuardStateLine(row)
}

/** The live countdown line, shown only while a seal or pact allowance runs. */
@Composable
private fun GuardStateLine(row: GuardRow.App) {
    when (row.state) {
        GuardState.SEALED -> Text(
            stringResource(R.string.home_guard_seal_lifts, formatDuration(row.sealMinutesLeft)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        GuardState.PACT_ACTIVE -> Text(
            stringResource(R.string.home_guard_pact_left, formatDuration(row.allowanceMinutesLeft)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        else -> {}
    }
}

@Composable
private fun GuardCircleCard(
    row: GuardRow.Circle,
    names: Map<String, String>,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            )
                        ),
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = stringResource(R.string.home_guard_circle_icon_desc),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.group.blockerName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.pacts_group_summary, row.memberPackages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val effectiveMax = if (row.group.pactMaxMinutes > 0) row.group.pactMaxMinutes else 15
                Text(
                    stringResource(R.string.pacts_config_summary, effectiveMax, row.group.cooldownMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.group.pactAlternativePackage?.let { altPkg ->
                    Text(
                        stringResource(R.string.pacts_alternative_summary, names[altPkg] ?: altPkg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (row.sealedCount > 0) {
                    Text(
                        stringResource(
                            R.string.home_guard_sealed_of, row.sealedCount, row.memberPackages.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    stringResource(R.string.pacts_today_stats, row.opensToday, row.reflexesToday),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            val circleState = when {
                row.sealedCount > 0 -> GuardState.SEALED
                row.pactActiveCount > 0 -> GuardState.PACT_ACTIVE
                else -> GuardState.QUIET
            }
            GuardStateChip(state = circleState)
        }
    }
}

/** Trailing state chip; quiet rows deliberately carry none. */
@Composable
private fun GuardStateChip(state: GuardState) {
    val (labelRes, container, content) = when (state) {
        GuardState.SEALED -> Triple(
            R.string.home_guard_chip_sealed,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        GuardState.PACT_ACTIVE -> Triple(
            R.string.home_guard_chip_active,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        GuardState.OVER_LIMIT -> Triple(
            R.string.home_guard_chip_over,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        GuardState.QUIET -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────
//  EMPTY STATE
// ────────────────────────────────────────────────────────────

@Composable
private fun GuardsEmptyState(onMakePact: () -> Unit) {
    val sigilColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            sigilColor.copy(alpha = 0.28f),
                            sigilColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = sigilColor,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.home_guard_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_guard_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onMakePact) {
            Text(stringResource(R.string.home_guard_make_pact))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_guard_empty_focus_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
