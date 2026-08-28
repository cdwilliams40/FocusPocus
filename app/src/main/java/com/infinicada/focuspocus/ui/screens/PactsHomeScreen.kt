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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Coffee
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.AppUtils
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardHeadline
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardRow
import com.infinicada.focuspocus.limit.GuardState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.GuardWindow
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.PendingPactRevision
import com.infinicada.focuspocus.limit.RequestTarget
import com.infinicada.focuspocus.limit.TodayRollup
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.ui.ScheduleLabels
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.ManaChip
import com.infinicada.focuspocus.ui.components.SectionHeader
import com.infinicada.focuspocus.ui.components.formatClock
import com.infinicada.focuspocus.ui.currentUiLocale
import com.infinicada.focuspocus.ui.formatClockTime
import com.infinicada.focuspocus.ui.formatDuration
import kotlinx.coroutines.delay

/** Tiles per row in the "Request time" app grid. */
private const val REQUEST_GRID_COLUMNS = 4

/**
 * The app's default screen: a dashboard of standing protection, organized by
 * what the user came to do. A "Request time" panel up top offers every
 * currently-requestable pact app as a tappable icon — one tap picks the app,
 * then the usual anti-reflex pause and minute choice. Below it, guards split
 * into "Happening now" (seals, running pacts, spent limits, with countdowns)
 * and the standing guard list; tapping any card opens its editor. The panic
 * seal and the make-a-pact CTA close the list, and a banner links to the
 * Focus tab while a session runs.
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
    pendingRevisions: List<PendingPactRevision>,
    nowMillis: Long,
    sessionActive: Boolean,
    isOnBreak: Boolean,
    sessionLabel: String,
    sessionTimeRemaining: Int,
    breakTimeRemaining: Int,
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    usageAccessGranted: Boolean,
    onGrantUsageAccess: () -> Unit,
    batteryUnrestricted: Boolean,
    onFixBattery: () -> Unit,
    onOpenBoons: () -> Unit,
    onOpenFocus: () -> Unit,
    onMakePact: () -> Unit,
    onSealAll: () -> Unit,
    onGuardClick: (GuardRow) -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Guarded apps must show their real name from the first frame — falling back
    // to the raw package name flashes "com.thing.stuff" on the cards. The scan
    // covers this once it lands (and is seeded from the cached scan before
    // that), so the direct lookup below only ever runs for the handful of
    // guarded packages a cold cache hasn't seen yet.
    val names = remember(installedApps, appTimeLimitConfigs, pactGroups, blockerLists) {
        val scanned = installedApps.associate { it.packageName to it.name }
        val unscanned = (
            appTimeLimitConfigs.keys +
                GuardStatus.pactGatedPackages(appTimeLimitConfigs, pactGroups, blockerLists)
            ).filterNot { it in scanned }
        if (unscanned.isEmpty()) scanned
        else scanned + unscanned.associateWith { AppUtils.getAppName(context, it) }
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
    val requestTargets = remember(
        appTimeLimitConfigs, pactGroups, blockerLists, guardLiveStates, todayOpenStats, names, nowMillis
    ) {
        GuardStatus.requestTargets(
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
    val liveRows = remember(rows) { rows.filter { GuardStatus.isLive(it) } }
    val standingRows = remember(rows) { rows.filter { !GuardStatus.isLive(it) } }

    // The tapped tile's request flow; holds a snapshot of the target so a
    // background refresh can't swap the app mid-choice.
    var requestTarget by remember { mutableStateOf<RequestTarget?>(null) }

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

        // OEM battery optimizers are the classic silent killer of the
        // enforcement service — warn on the dashboard as soon as any guard
        // exists to be broken. (Accessibility-off already has its own modal.)
        if (!batteryUnrestricted && rows.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        stringResource(R.string.home_battery_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onFixBattery) {
                        Text(stringResource(R.string.home_battery_warning_fix))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
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
            // ── Request time: the dashboard's primary action, one tap per app ──
            if (requestTargets.isNotEmpty()) {
                item(key = "request-panel") {
                    SectionHeader(stringResource(R.string.pacts_request_time))
                    Spacer(modifier = Modifier.height(8.dp))
                    RequestTimePanel(
                        targets = requestTargets,
                        names = names,
                        onTargetClick = { requestTarget = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Happening now: seals, running pacts, spent limits ──
            if (liveRows.isNotEmpty()) {
                item(key = "live-header") {
                    SectionHeader(stringResource(R.string.home_section_live))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            guardCards(liveRows, names, pendingRevisions, nowMillis, onGuardClick)

            // ── Standing guards, quiet or off their hours ──
            if (standingRows.isNotEmpty()) {
                item(key = "standing-header") {
                    SectionHeader(stringResource(R.string.home_section_guards))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            guardCards(standingRows, names, pendingRevisions, nowMillis, onGuardClick)

            item(key = "bottom-actions") {
                Spacer(modifier = Modifier.height(8.dp))
                // Panic button: only meaningful when some pact-gated app isn't
                // already sealed — otherwise there is nothing left to seal.
                val anySealablePact = rows.any { r ->
                    when (r) {
                        is GuardRow.App -> r.config.pactModeEnabled && r.state != GuardState.SEALED
                        is GuardRow.Circle -> r.sealedCount < r.memberPackages.size
                    }
                }
                if (anySealablePact) {
                    var showSealAllDialog by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showSealAllDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.home_seal_all))
                    }
                    if (showSealAllDialog) {
                        AlertDialog(
                            onDismissRequest = { showSealAllDialog = false },
                            title = { Text(stringResource(R.string.home_seal_all_confirm_title)) },
                            text = { Text(stringResource(R.string.home_seal_all_confirm_message)) },
                            confirmButton = {
                                Button(onClick = {
                                    showSealAllDialog = false
                                    onSealAll()
                                }) {
                                    Text(stringResource(R.string.home_seal_all_confirm_action))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSealAllDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
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

    requestTarget?.let { target ->
        PactRequestDialog(
            packageName = target.packageName,
            appName = names[target.packageName] ?: target.packageName,
            choicesMinutes = PactManager.choicesFor(target.config),
            sealMinutes = target.config.cooldownMinutes,
            onRequest = { minutes ->
                requestTarget = null
                onRequestTime(target.packageName, minutes)
            },
            onDismiss = { requestTarget = null }
        )
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
                pluralStringResource(R.plurals.home_day_streak, streak, streak),
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
//  REQUEST-TIME PANEL
// ────────────────────────────────────────────────────────────

/**
 * The fast lane: every requestable pact app as an icon tile, most-opened
 * first. One tap selects the app; the dialog then holds the same anti-reflex
 * pause as the pact overlay before the minute choice.
 */
@Composable
private fun RequestTimePanel(
    targets: List<RequestTarget>,
    names: Map<String, String>,
    onTargetClick: (RequestTarget) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            stringResource(R.string.home_request_time_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        targets.chunked(REQUEST_GRID_COLUMNS).forEachIndexed { index, rowTargets ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTargets.forEach { target ->
                    RequestTimeTile(
                        target = target,
                        appName = names[target.packageName] ?: target.packageName,
                        onClick = { onTargetClick(target) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Pad partial rows so tiles keep the same width as full rows.
                repeat(REQUEST_GRID_COLUMNS - rowTargets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RequestTimeTile(
    target: RequestTarget,
    appName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(R.string.pacts_request_time)
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        AppIcon(
            packageName = target.packageName,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ────────────────────────────────────────────────────────────
//  GUARD CARDS
// ────────────────────────────────────────────────────────────

/** Emits one compact card per row, keyed stably so re-sorting moves compositions. */
private fun androidx.compose.foundation.lazy.LazyListScope.guardCards(
    rows: List<GuardRow>,
    names: Map<String, String>,
    pendingRevisions: List<PendingPactRevision>,
    nowMillis: Long,
    onGuardClick: (GuardRow) -> Unit
) {
    items(
        rows,
        // Stable identity per card: re-sorting (a guard changing state moves
        // its card, possibly across sections) must MOVE each item's
        // composition rather than rebind reused slots to different apps —
        // rebinding is what let a slot keep the previous app's icon and
        // per-item state.
        key = { row ->
            when (row) {
                is GuardRow.App -> "app:${row.packageName}"
                is GuardRow.Circle -> "circle:${row.group.blockerName}"
            }
        }
    ) { row ->
        when (row) {
            is GuardRow.App -> GuardAppCard(
                row = row,
                names = names,
                pendingRevision = pendingRevisions.find { it.packageName == row.packageName },
                nowMillis = nowMillis,
                onClick = { onGuardClick(row) }
            )
            is GuardRow.Circle -> GuardCircleCard(
                row = row,
                pendingRevision = pendingRevisions.find {
                    it.packageName == null && it.blockerName == row.group.blockerName
                },
                nowMillis = nowMillis,
                onClick = { onGuardClick(row) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * A compact guard card: identity, one config line, live state, and the state
 * chip. Tapping opens the guard's editor, which holds the full settings
 * (substitute app, daily backstop, escalation, guard hours).
 */
@Composable
private fun GuardAppCard(
    row: GuardRow.App,
    names: Map<String, String>,
    pendingRevision: PendingPactRevision?,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val appName = names[row.packageName] ?: row.packageName
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(R.string.home_guard_edit_label)
            ),
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
                    PactCardBody(row)
                } else {
                    WardCardBody(row)
                }
                PendingRevisionLine(pendingRevision, nowMillis)
            }
            GuardStateChip(state = row.state)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

/** Config summary, live state, and today's counters for a pact-style row. */
@Composable
private fun PactCardBody(row: GuardRow.App) {
    val effectiveMax = if (row.config.pactMaxMinutes > 0) row.config.pactMaxMinutes
                       else PactManager.DEFAULT_MAX_MINUTES
    Text(
        stringResource(R.string.pacts_config_summary, effectiveMax, row.config.cooldownMinutes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GuardScheduleSummary(row.config.activeDays, row.config.activeStartTime, row.config.activeEndTime)
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
    GuardScheduleSummary(row.config.activeDays, row.config.activeStartTime, row.config.activeEndTime)
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
        GuardState.SCHEDULED_OFF -> Text(
            stringResource(R.string.home_guard_off_schedule),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> {}
    }
}

/**
 * One-line guard-hours summary ("Guard hours: M T W T F · 21:00–07:00"),
 * shown only when the guard actually carries a schedule. Day initials match
 * the editor's chips.
 */
@Composable
private fun GuardScheduleSummary(
    days: Set<com.infinicada.focuspocus.model.DayOfWeek>?,
    startTime: String?,
    endTime: String?
) {
    val hasWindow = GuardWindow.parseMinutes(startTime) != null &&
        GuardWindow.parseMinutes(endTime) != null
    if (days.isNullOrEmpty() && !hasWindow) return
    val daysText = if (days.isNullOrEmpty()) {
        stringResource(R.string.guard_schedule_every_day)
    } else {
        ScheduleLabels.narrowSummary(days, currentUiLocale())
    }
    val hoursText = if (hasWindow) "${formatClockTime(startTime)}\u2013${formatClockTime(endTime)}"
                    else stringResource(R.string.guard_schedule_all_day)
    Text(
        stringResource(R.string.guard_schedule_summary, daysText, hoursText),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GuardCircleCard(
    row: GuardRow.Circle,
    pendingRevision: PendingPactRevision?,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val circleState = GuardStatus.displayState(row)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(R.string.home_guard_edit_label)
            ),
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
                GuardScheduleSummary(
                    row.group.activeDays, row.group.activeStartTime, row.group.activeEndTime
                )
                if (circleState == GuardState.SCHEDULED_OFF) {
                    Text(
                        stringResource(R.string.home_guard_off_schedule),
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
                PendingRevisionLine(pendingRevision, nowMillis)
            }
            GuardStateChip(state = circleState)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

/**
 * One line on a guard card while a 24 h revision is queued: what happens
 * (new terms vs. the pact lifting) and how long the current terms still hold.
 */
@Composable
private fun PendingRevisionLine(revision: PendingPactRevision?, nowMillis: Long) {
    if (revision == null) return
    val minutesLeft = GuardStatus.minutesUntil(revision.appliesAtMillis, nowMillis).coerceAtLeast(1)
    Text(
        stringResource(
            if (revision.isRemoval) R.string.pact_revision_pending_removal
            else R.string.pact_revision_pending_change,
            formatDuration(minutesLeft)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
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
        GuardState.SCHEDULED_OFF -> Triple(
            R.string.home_guard_chip_off,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * The pact overlay's in-app twin. With Warden greying on, a pact-gated app is
 * OS-suspended and its launcher icon leads only to a system "app is paused"
 * dialog — so the conscious time choice happens here instead, and granting it
 * un-greys the app. The same few-second pause gates the choices so the
 * dashboard doesn't become a lower-friction side door than the overlay it
 * mirrors.
 */
@Composable
private fun PactRequestDialog(
    packageName: String,
    appName: String,
    choicesMinutes: List<Int>,
    sealMinutes: Int,
    onRequest: (minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    packageName = packageName,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.pacts_request_time))
            }
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.overlay_pact_prompt, appName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.overlay_pact_seal_desc, appName, sealMinutes),
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
                choicesMinutes.forEach { minutes ->
                    OutlinedButton(
                        onClick = { onRequest(minutes) },
                        enabled = countdownDone,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overlay_pact_choice, minutes))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_guard_empty_focus_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
