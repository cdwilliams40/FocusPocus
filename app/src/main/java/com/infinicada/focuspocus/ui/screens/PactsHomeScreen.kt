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
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.ManaChip
import com.infinicada.focuspocus.ui.components.SectionHeader
import com.infinicada.focuspocus.ui.components.formatClock
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
    onSealNow: (GuardRow) -> Unit,
    onGuardClick: (GuardRow) -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit,
    groupSealEnabled: Boolean,
    groupSealOpenWindowMinutes: Int,
    groupSealDurationMinutes: Int,
    onGroupOpen: () -> Unit,
    well: WellSnapshot,
    wellChannelCost: Long,
    onChannelMana: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Names come only from the installed-apps scan (the same label the user
    // sees in the launcher). A guarded package the scan hasn't reached yet
    // (still running, or the app was uninstalled) simply falls back to its raw
    // package name wherever it's displayed — no synchronous PackageManager
    // lookup on the composition thread.
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
    // No partial per-app requesting inside the group while Group Seal mode is
    // on — that's the hopping path the mode exists to close — so the group's
    // panel is the group action instead, driven by groupSealSummary below.
    // Pacts outside the group (Well-exempt ones, while the Mana Well is on)
    // keep their own request tiles.
    val requestTargets = remember(
        groupSealEnabled, well.enabled, appTimeLimitConfigs, pactGroups, blockerLists,
        guardLiveStates, todayOpenStats, names, nowMillis
    ) {
        GuardStatus.requestTargets(
            configs = appTimeLimitConfigs,
            groups = pactGroups,
            blockers = blockerLists,
            liveStates = guardLiveStates,
            openStats = todayOpenStats,
            names = names,
            now = nowMillis
        ).filter { !groupSealEnabled || !GuardStatus.inGroupSeal(it.config, well.enabled) }
    }
    val groupSealSummary = remember(
        groupSealEnabled, well.enabled, appTimeLimitConfigs, pactGroups, blockerLists,
        guardLiveStates, nowMillis
    ) {
        if (groupSealEnabled) {
            GuardStatus.groupSealSummary(
                configs = appTimeLimitConfigs,
                groups = pactGroups,
                blockers = blockerLists,
                liveStates = guardLiveStates,
                now = nowMillis,
                wellEnabled = well.enabled
            )
        } else {
            null
        }
    }
    // The dashboard's clock can trail the snapshot by a beat right after a
    // draw; never read the Well at an instant before it was taken.
    val wellNow = maxOf(nowMillis, well.takenAtMillis)
    val wellMinutes = well.availableMinutesAt(wellNow)
    val headline = remember(rows) { GuardStatus.headlineCounts(rows) }
    val rollup = remember(rows) { GuardStatus.todayRollup(rows) }
    val liveRows = remember(rows) { rows.filter { GuardStatus.isLive(it) } }
    val standingRows = remember(rows) { rows.filter { !GuardStatus.isLive(it) } }

    // The tapped tile's request flow; holds a snapshot of the target so a
    // background refresh can't swap the app mid-choice.
    var requestTarget by remember { mutableStateOf<RequestTarget?>(null) }

    // The running pact the user asked to end early, awaiting confirmation.
    var sealNowTarget by remember { mutableStateOf<GuardRow?>(null) }
    // A pact inside Group Seal's shared window has no early seal of its own:
    // the window is one draw for every app in it, so ending it is the panic
    // button's job ("seal everything"), which closes — and refunds — it whole.
    val canSealNow: (AppTimeLimit) -> Boolean = { config ->
        !groupSealEnabled || !GuardStatus.inGroupSeal(config, well.enabled)
    }

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
            // ── The Mana Well: the pool every Well-bound pact draws from ──
            if (well.enabled && GuardStatus.hasPactRows(rows)) {
                item(key = "well-panel") {
                    WellPanel(
                        well = well,
                        nowMillis = wellNow,
                        progressionEnabled = progressionEnabled,
                        manaBalance = manaBalance,
                        channelCost = wellChannelCost,
                        onChannelMana = onChannelMana
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Request time: the dashboard's primary action ──
            val showGroupPanel = groupSealEnabled && groupSealSummary != null &&
                groupSealSummary.eligibleCount > 0
            if (showGroupPanel || requestTargets.isNotEmpty()) {
                item(key = "request-header") {
                    SectionHeader(stringResource(R.string.pacts_request_time))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (showGroupPanel && groupSealSummary != null) {
                item(key = "group-seal-panel") {
                    GroupSealPanel(
                        summary = groupSealSummary,
                        // With the Well on, the shared window is one draw of
                        // at most what the Well holds.
                        openWindowMinutes = if (well.enabled) {
                            minOf(groupSealOpenWindowMinutes, wellMinutes)
                        } else {
                            groupSealOpenWindowMinutes
                        },
                        sealDurationMinutes = groupSealDurationMinutes,
                        onGroupOpen = onGroupOpen
                    )
                    Spacer(modifier = Modifier.height(if (requestTargets.isEmpty()) 16.dp else 8.dp))
                }
            }
            if (requestTargets.isNotEmpty()) {
                item(key = "request-panel") {
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
            guardCards(
                liveRows, names, pendingRevisions, nowMillis, onGuardClick,
                canSealNow = canSealNow, onSealNow = { sealNowTarget = it }
            )

            // ── Standing guards, quiet or off their hours ──
            if (standingRows.isNotEmpty()) {
                item(key = "standing-header") {
                    SectionHeader(stringResource(R.string.home_section_guards))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            guardCards(
                standingRows, names, pendingRevisions, nowMillis, onGuardClick,
                canSealNow = canSealNow, onSealNow = { sealNowTarget = it }
            )

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
                            text = {
                                Column {
                                    Text(stringResource(R.string.home_seal_all_confirm_message))
                                    if (well.enabled) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            stringResource(R.string.home_seal_now_refund_note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            },
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

    sealNowTarget?.let { target ->
        val (targetName, sealMinutes, wellBound) = when (target) {
            is GuardRow.App -> Triple(
                names[target.packageName] ?: target.packageName,
                target.config.cooldownMinutes,
                well.enabled && !target.config.wellExempt
            )
            is GuardRow.Circle -> Triple(
                target.group.blockerName,
                target.group.cooldownMinutes,
                well.enabled && !target.group.wellExempt
            )
        }
        AlertDialog(
            onDismissRequest = { sealNowTarget = null },
            title = { Text(stringResource(R.string.home_seal_now_title, targetName)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (target is GuardRow.Circle) R.string.home_seal_now_circle_message
                            else R.string.home_seal_now_message,
                            sealMinutes
                        )
                    )
                    if (wellBound) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.home_seal_now_refund_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    sealNowTarget = null
                    onSealNow(target)
                }) {
                    Text(stringResource(R.string.home_seal_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { sealNowTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    requestTarget?.let { target ->
        // A Well-bound pact can only offer what the Well holds — the same
        // limit the launcher overlay applies (OverlayActivity).
        val wellBound = well.enabled && !target.config.wellExempt
        val ladder = PactManager.choicesFor(target.config)
        PactRequestDialog(
            packageName = target.packageName,
            appName = names[target.packageName] ?: target.packageName,
            choicesMinutes = if (wellBound) WellMath.limitChoices(ladder, wellMinutes) else ladder,
            sealMinutes = target.config.cooldownMinutes,
            well = if (wellBound) well else null,
            wellNeededMinutes = ladder.min(),
            nowMillis = wellNow,
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
//  MANA WELL PANEL
// ────────────────────────────────────────────────────────────

/**
 * The Mana Well at a glance: how much pact time is left in the shared pool,
 * how fast it comes back, and how much was drawn today across every pact
 * app — the one number per-app cards can't show. Channeling mana in is the
 * card's only action; drawing happens where it always did, at the pact.
 */
@Composable
private fun WellPanel(
    well: WellSnapshot,
    nowMillis: Long,
    progressionEnabled: Boolean,
    manaBalance: Long,
    channelCost: Long,
    onChannelMana: () -> Boolean
) {
    val levelMillis = well.levelMillisAt(nowMillis)
    val minutes = (levelMillis / WellMath.MINUTE_MS).toInt()
    val capacityMillis = well.capacityMinutes * WellMath.MINUTE_MS
    val full = levelMillis >= capacityMillis
    var showChannelDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.well_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.well_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.well_level_value, minutes, well.capacityMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        LinearProgressIndicator(
            progress = { (levelMillis.toFloat() / capacityMillis).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
        )
        Text(
            text = when {
                full -> stringResource(R.string.well_full)
                well.refillIntervalMinutes <= 0 -> stringResource(R.string.well_refill_off)
                else -> stringResource(
                    R.string.well_refill_line,
                    well.refillIntervalMinutes,
                    formatDuration(
                        WellMath.wholeMinutesCeil(
                            well.millisUntilMinutes(well.capacityMinutes, nowMillis) ?: 0L
                        )
                    )
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.well_drawn_today, well.drawnTodayMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progressionEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showChannelDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.well_channel_cta, Perk.WELL_CHANNEL_GRANT, channelCost))
            }
        }
    }

    if (showChannelDialog) {
        // All or nothing, like the store itself: channeling is refused
        // unless the whole grant fits under the capacity.
        val hasRoom = levelMillis + Perk.WELL_CHANNEL_GRANT * WellMath.MINUTE_MS <= capacityMillis
        WellChannelDialog(
            cost = channelCost,
            manaBalance = manaBalance,
            hasRoom = hasRoom,
            onConfirm = {
                showChannelDialog = false
                onChannelMana()
            },
            onDismiss = { showChannelDialog = false }
        )
    }
}

/**
 * Confirms pouring mana into the Well. The price is shown before the tap
 * because it moves: every channel in a day doubles the next one.
 */
@Composable
private fun WellChannelDialog(
    cost: Long,
    manaBalance: Long,
    hasRoom: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val affordable = manaBalance >= cost
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.well_channel_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.well_channel_message, cost, Perk.WELL_CHANNEL_GRANT),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.well_channel_doubling_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val blocker = when {
                    !hasRoom -> stringResource(R.string.well_channel_no_room, Perk.WELL_CHANNEL_GRANT)
                    !affordable -> stringResource(R.string.well_channel_balance, manaBalance)
                    else -> null
                }
                if (blocker != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        blocker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = hasRoom && affordable) {
                Text(stringResource(R.string.well_channel_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ────────────────────────────────────────────────────────────
//  GROUP SEAL PANEL
// ────────────────────────────────────────────────────────────

/**
 * Group Seal mode's dashboard twin of the launcher-tap overlay: while QUIET,
 * a single CTA opens every pact-gated app together (behind the same
 * anti-reflex confirm); once OPEN or SEALED it's a status line only — there's
 * no per-app fallback to request time from here while the mode is on.
 */
@Composable
private fun GroupSealPanel(
    summary: GuardStatus.GroupSealSummary,
    openWindowMinutes: Int,
    sealDurationMinutes: Int,
    onGroupOpen: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        when (summary.phase) {
            GuardStatus.GroupSealPhase.QUIET -> {
                Text(
                    stringResource(R.string.home_group_seal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                var showConfirm by remember { mutableStateOf(false) }
                // A window of zero minutes only happens with the Mana Well
                // on and dry: the Well card above says when it refills.
                Button(
                    onClick = { showConfirm = true },
                    enabled = openWindowMinutes > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (openWindowMinutes > 0) stringResource(R.string.home_group_seal_cta)
                        else stringResource(R.string.well_dry_short)
                    )
                }
                if (showConfirm) {
                    GroupOpenConfirmDialog(
                        openWindowMinutes = openWindowMinutes,
                        sealDurationMinutes = sealDurationMinutes,
                        onConfirm = {
                            showConfirm = false
                            onGroupOpen()
                        },
                        onDismiss = { showConfirm = false }
                    )
                }
            }
            GuardStatus.GroupSealPhase.OPEN -> Text(
                stringResource(R.string.home_group_seal_open_status, formatDuration(summary.minutesLeft)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            GuardStatus.GroupSealPhase.SEALED -> Text(
                stringResource(R.string.home_group_seal_sealed_status, formatDuration(summary.minutesLeft)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Group-open's confirmation, same anti-reflex pause as [PactRequestDialog], one confirm action. */
@Composable
private fun GroupOpenConfirmDialog(
    openWindowMinutes: Int,
    sealDurationMinutes: Int,
    onConfirm: () -> Unit,
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
                Icon(
                    Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.home_group_seal_cta))
            }
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.overlay_group_pact_seal_desc, sealDurationMinutes),
                    style = MaterialTheme.typography.bodyMedium
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
                OutlinedButton(
                    onClick = onConfirm,
                    enabled = countdownDone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.overlay_group_pact_choice, openWindowMinutes))
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
    onGuardClick: (GuardRow) -> Unit,
    canSealNow: (AppTimeLimit) -> Boolean,
    onSealNow: (GuardRow) -> Unit
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
                // Only a running pact can be ended early.
                onSealNow = if (row.state == GuardState.PACT_ACTIVE && canSealNow(row.config)) {
                    { onSealNow(row) }
                } else null,
                onClick = { onGuardClick(row) }
            )
            is GuardRow.Circle -> GuardCircleCard(
                row = row,
                pendingRevision = pendingRevisions.find {
                    it.packageName == null && it.blockerName == row.group.blockerName
                },
                nowMillis = nowMillis,
                onSealNow = if (row.pactActiveCount > 0 &&
                    canSealNow(row.group.toAppTimeLimit(""))
                ) {
                    { onSealNow(row) }
                } else null,
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
    onSealNow: (() -> Unit)?,
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
                SealNowButton(onSealNow)
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
        com.infinicada.focuspocus.model.DayOfWeek.entries
            .filter { it in days }
            .joinToString(" ") { it.name.take(1) }
    }
    val hoursText = if (hasWindow) "$startTime–$endTime"
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
    onSealNow: (() -> Unit)?,
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
                SealNowButton(onSealNow)
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
 * "Seal now" on a card with a running pact — the way to be done before the
 * clock is. Null hides it (no pact running, or the pact is part of a Group
 * Seal window). Sits flush with the card's text rather than indented like a
 * dialog button.
 */
@Composable
private fun SealNowButton(onSealNow: (() -> Unit)?) {
    if (onSealNow == null) return
    TextButton(
        onClick = onSealNow,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.home_seal_now))
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
    /** The Mana Well when this pact draws from it; null when it doesn't. */
    well: WellSnapshot?,
    /** The pact's smallest regular choice — what a dry Well has to regain. */
    wellNeededMinutes: Int,
    nowMillis: Long,
    onRequest: (minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(3) }
    val countdownDone = remainingSeconds <= 0
    // Too little in the Well for even the smallest choice: nothing to grant.
    val wellDry = well != null && choicesMinutes.isEmpty()

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
                    if (wellDry) stringResource(R.string.overlay_well_dry_body, appName)
                    else stringResource(R.string.overlay_pact_prompt, appName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (well != null) {
                    Text(
                        stringResource(
                            R.string.well_level_line,
                            well.availableMinutesAt(nowMillis), well.capacityMinutes
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (wellDry && well != null) {
                    val waitMillis = well.millisUntilMinutes(wellNeededMinutes, nowMillis)
                    Text(
                        if (waitMillis != null) {
                            stringResource(
                                R.string.overlay_well_dry_eta,
                                wellNeededMinutes,
                                formatDuration(WellMath.wholeMinutesCeil(waitMillis))
                            )
                        } else {
                            stringResource(R.string.overlay_well_dry_no_refill)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                // With a dry Well there was never an offer to turn down.
                Text(
                    if (wellDry) stringResource(R.string.overlay_close)
                    else stringResource(R.string.overlay_pact_decline)
                )
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
