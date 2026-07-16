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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.TodayRollup
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.ManaChip
import com.infinicada.focuspocus.ui.formatDuration
import kotlinx.coroutines.delay

/**
 * The app's single home screen: the focus-session caster (passed in as
 * [focusSection]) up top, then a dashboard of standing protection — one card
 * per guard (a pact'd or time-limited app, or a whole pact circle) with its
 * live state: sealed, pact running, limit spent, or quiet with today's open
 * counts.
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
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    usageAccessGranted: Boolean,
    onGrantUsageAccess: () -> Unit,
    onOpenBoons: () -> Unit,
    onMakePact: () -> Unit,
    onGuardClick: (GuardRow) -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit,
    focusSection: @Composable () -> Unit,
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

        // The focus-session caster (idle) or live session controls (active) —
        // sessions and standing guards share the one home surface.
        item {
            focusSection()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Daily limits can't be tracked without usage access — surface the
        // grant affordance right where those guards are managed.
        val anyDailyLimit = rows.any { row ->
            when (row) {
                is GuardRow.App -> row.config.dailyLimitMinutes > 0
                is GuardRow.Circle -> row.group.dailyLimitMinutes > 0
            }
        }
        if (!usageAccessGranted && anyDailyLimit) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        stringResource(R.string.time_limits_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onGrantUsageAccess) {
                        Text(stringResource(R.string.time_limits_grant_usage))
                    }
                }
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
                        onClick = { onGuardClick(row) },
                        onRequestTime = onRequestTime
                    )
                    is GuardRow.Circle -> GuardCircleCard(
                        row = row,
                        names = names,
                        onClick = { onGuardClick(row) },
                        onRequestTime = onRequestTime
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
        val parts = listOfNotNull(
            if (headline.sealedCount > 0) {
                pluralStringResource(
                    R.plurals.home_guard_sealed_count, headline.sealedCount, headline.sealedCount
                )
            } else null,
            if (headline.pactActiveCount > 0) {
                pluralStringResource(
                    R.plurals.home_guard_active_count, headline.pactActiveCount, headline.pactActiveCount
                )
            } else null,
            if (headline.overLimitCount > 0) {
                pluralStringResource(
                    R.plurals.home_guard_over_count, headline.overLimitCount, headline.overLimitCount
                )
            } else null
        )
        val headlineText =
            if (parts.isEmpty()) stringResource(R.string.home_guard_all_quiet)
            else parts.joinToString(" · ")
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
//  GUARD CARDS
// ────────────────────────────────────────────────────────────

@Composable
private fun GuardAppCard(
    row: GuardRow.App,
    names: Map<String, String>,
    onClick: () -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit
) {
    val appName = names[row.packageName] ?: row.packageName
    var showRequestDialog by remember { mutableStateOf(false) }
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
        // The in-app pact gate: under Warden greying the OS refuses to open the
        // app at all, so this is where a sealed-by-default app gets its time.
        if (row.config.pactModeEnabled && row.state == GuardState.QUIET) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showRequestDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pacts_request_time))
            }
        }
    }
    if (showRequestDialog) {
        PactRequestDialog(
            targets = listOf(
                PactRequestTarget(
                    packageName = row.packageName,
                    appName = appName,
                    choicesMinutes = PactManager.choicesFor(row.config),
                    sealMinutes = row.config.cooldownMinutes
                )
            ),
            onRequest = { pkg, minutes ->
                showRequestDialog = false
                onRequestTime(pkg, minutes)
            },
            onDismiss = { showRequestDialog = false }
        )
    }
}

/** Config summary, live state, and today's counters for a pact-style row. */
@Composable
private fun PactCardBody(row: GuardRow.App, names: Map<String, String>) {
    val effectiveMax = if (row.config.pactMaxMinutes > 0) row.config.pactMaxMinutes
                       else PactManager.DEFAULT_MAX_MINUTES
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
                    else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
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
    onClick: () -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit
) {
    var showRequestDialog by remember { mutableStateOf(false) }
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
                val effectiveMax = if (row.group.pactMaxMinutes > 0) row.group.pactMaxMinutes
                                   else PactManager.DEFAULT_MAX_MINUTES
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
                row.overLimitCount > 0 -> GuardState.OVER_LIMIT
                else -> GuardState.QUIET
            }
            GuardStateChip(state = circleState)
        }
        if (row.quietMemberPackages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showRequestDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pacts_request_time))
            }
        }
    }
    if (showRequestDialog) {
        PactRequestDialog(
            targets = row.quietMemberPackages.map { pkg ->
                PactRequestTarget(
                    packageName = pkg,
                    appName = names[pkg] ?: pkg,
                    choicesMinutes = PactManager.choicesFor(row.group.toAppTimeLimit(pkg)),
                    sealMinutes = row.group.cooldownMinutes
                )
            },
            onRequest = { pkg, minutes ->
                showRequestDialog = false
                onRequestTime(pkg, minutes)
            },
            onDismiss = { showRequestDialog = false }
        )
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
//  REQUEST-TIME DIALOG
// ────────────────────────────────────────────────────────────

/** One requestable app offered by [PactRequestDialog]. */
private data class PactRequestTarget(
    val packageName: String,
    val appName: String,
    val choicesMinutes: List<Int>,
    val sealMinutes: Int
)

/**
 * The pact overlay's in-app twin. With Warden greying on, a pact-gated app is
 * OS-suspended and its launcher icon leads only to a system "app is paused"
 * dialog — so the conscious time choice happens here instead, and granting it
 * un-greys the app. Multiple targets (a pact circle) get a picker step first,
 * and the same few-second pause gates the choices so the dashboard doesn't
 * become a lower-friction side door than the overlay it mirrors.
 */
@Composable
private fun PactRequestDialog(
    targets: List<PactRequestTarget>,
    onRequest: (packageName: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(targets.singleOrNull()) }
    var remainingSeconds by remember { mutableIntStateOf(3) }
    val countdownDone = remainingSeconds <= 0

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pacts_request_time)) },
        text = {
            val target = selected
            if (target == null) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(targets) { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = candidate }
                                .padding(vertical = 8.dp)
                        ) {
                            AppIcon(
                                packageName = candidate.packageName,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(candidate.appName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Column {
                    Text(
                        stringResource(R.string.overlay_pact_prompt, target.appName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.overlay_pact_seal_desc, target.appName, target.sealMinutes
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!countdownDone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.overlay_pact_wait, remainingSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    target.choicesMinutes.forEach { minutes ->
                        OutlinedButton(
                            onClick = { onRequest(target.packageName, minutes) },
                            enabled = countdownDone,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.overlay_pact_choice, minutes))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.overlay_pact_decline))
            }
        }
    )
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
    }
}
