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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSnapshot
import com.infinicada.focuspocus.model.AppInfo
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
 * The app's default screen: the Mana Well and the apps behind it. The Well
 * card shows how much time is left and lets mana top it up; "Request time"
 * offers every Well app without a running pact; running pacts can be ended
 * early to pour the rest back; the app list below leads to the editor.
 */
@Composable
fun WellHomeScreen(
    installedApps: List<AppInfo>,
    wellApps: List<WellApp>,
    pendingRemovals: Map<String, Long>,
    activePacts: Map<String, Long>,
    todayOpenStats: Map<String, AppOpenStats>,
    nowMillis: Long,
    sessionActive: Boolean,
    isOnBreak: Boolean,
    sessionLabel: String,
    sessionTimeRemaining: Int,
    breakTimeRemaining: Int,
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    batteryUnrestricted: Boolean,
    onFixBattery: () -> Unit,
    onOpenMana: () -> Unit,
    onOpenFocus: () -> Unit,
    onEditWellApps: () -> Unit,
    onEndPact: (packageName: String) -> Unit,
    onRequestTime: (packageName: String, minutes: Int) -> Unit,
    well: WellSnapshot,
    wellChannelCost: Long,
    onChannelMana: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Names come only from the installed-apps scan; a package the scan hasn't
    // reached falls back to its raw name — no PackageManager call here.
    val names = remember(installedApps) { installedApps.associate { it.packageName to it.name } }
    val running = remember(wellApps, activePacts, nowMillis) {
        wellApps.map { it.packageName }.filter { (activePacts[it] ?: 0L) > nowMillis }
    }
    // Most-opened first, so the reflex apps sit where the thumb lands.
    val requestable = remember(wellApps, running, todayOpenStats) {
        wellApps.map { it.packageName }
            .filter { it !in running }
            .sortedByDescending { todayOpenStats[it]?.opens ?: 0 }
    }
    // The dashboard's clock can trail the snapshot by a beat right after a
    // draw; never read the Well at an instant before it was taken.
    val wellNow = maxOf(nowMillis, well.takenAtMillis)
    val opensToday = wellApps.sumOf { todayOpenStats[it.packageName]?.opens ?: 0 }
    val reflexesToday = wellApps.sumOf { todayOpenStats[it.packageName]?.reflexOpens ?: 0 }

    var requestTarget by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        item {
            DashboardHeader(
                runningCount = running.size,
                opensToday = opensToday,
                reflexesToday = reflexesToday,
                anyApps = wellApps.isNotEmpty(),
                progressionEnabled = progressionEnabled,
                manaBalance = manaBalance,
                currentStreak = currentStreak,
                onOpenMana = onOpenMana
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
        // enforcement service — warn as soon as there is something to break.
        if (!batteryUnrestricted && wellApps.isNotEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
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

        if (wellApps.isEmpty()) {
            item { WellEmptyState(onAddApps = onEditWellApps) }
            return@LazyColumn
        }

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

        if (running.isNotEmpty()) {
            item(key = "live-header") {
                SectionHeader(stringResource(R.string.home_section_live))
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(running, key = { "running-$it" }) { pkg ->
                RunningPactCard(
                    packageName = pkg,
                    appName = names[pkg] ?: pkg,
                    minutesLeft = GuardStatus.minutesUntil(activePacts[pkg] ?: 0L, nowMillis),
                    onEndPact = { onEndPact(pkg) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        if (requestable.isNotEmpty()) {
            item(key = "request-header") {
                SectionHeader(stringResource(R.string.pacts_request_time))
                Spacer(modifier = Modifier.height(8.dp))
            }
            item(key = "request-panel") {
                RequestTimePanel(
                    packages = requestable,
                    names = names,
                    onClick = { requestTarget = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item(key = "apps-header") {
            SectionHeader(stringResource(R.string.home_well_apps_header))
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(wellApps, key = { "app-${it.packageName}" }) { app ->
            WellAppRow(
                app = app,
                appName = names[app.packageName] ?: app.packageName,
                stats = todayOpenStats[app.packageName],
                removalAt = pendingRemovals[app.packageName],
                nowMillis = nowMillis,
                onClick = onEditWellApps
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        item(key = "bottom-actions") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onEditWellApps, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_well_edit))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    requestTarget?.let { pkg ->
        // Only what the Well holds — the same limit the launcher overlay applies.
        PactRequestDialog(
            packageName = pkg,
            appName = names[pkg] ?: pkg,
            choicesMinutes = WellMath.limitChoices(WellApps.PACT_CHOICES, well.availableMinutesAt(wellNow)),
            well = well,
            nowMillis = wellNow,
            onRequest = { minutes ->
                requestTarget = null
                onRequestTime(pkg, minutes)
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
    runningCount: Int,
    opensToday: Int,
    reflexesToday: Int,
    anyApps: Boolean,
    progressionEnabled: Boolean,
    manaBalance: Long,
    currentStreak: Int,
    onOpenMana: () -> Unit
) {
    if (anyApps) {
        Text(
            text = if (runningCount > 0) {
                pluralStringResource(R.plurals.home_guard_active_count, runningCount, runningCount)
            } else {
                stringResource(R.string.home_guard_all_quiet)
            },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_guard_rollup, opensToday, reflexesToday),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Chips never share a row with the headline, so narrow screens don't
    // squeeze their labels into tall bars.
    if (progressionEnabled || currentStreak > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (progressionEnabled) ManaChip(balance = manaBalance, onClick = onOpenMana)
            if (currentStreak > 0) StreakBadge(currentStreak)
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
 * The Mana Well at a glance: how much time is left in the shared pool, how
 * fast it comes back, and how much was drawn today. Pouring mana in is the
 * card's only action; drawing happens at the pact.
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

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.well_title), style = MaterialTheme.typography.titleSmall)
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
            OutlinedButton(onClick = { showChannelDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.well_channel_cta, Perk.WELL_CHANNEL_GRANT, channelCost))
            }
        }
    }

    if (showChannelDialog) {
        // All or nothing, like the store itself: a pour is refused unless the
        // whole grant fits under the capacity.
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
 * because it moves: every pour in a day doubles the next one.
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ────────────────────────────────────────────────────────────
//  RUNNING PACTS, REQUEST TIME, APP LIST
// ────────────────────────────────────────────────────────────

@Composable
private fun RunningPactCard(
    packageName: String,
    appName: String,
    minutesLeft: Int,
    onEndPact: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = packageName, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.home_guard_pact_left, formatDuration(minutesLeft)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            // Ending early hands the unused minutes back, and a slip costs
            // nothing but asking again, so it goes straight through.
            TextButton(onClick = onEndPact) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.home_end_pact))
            }
        }
    }
}

/**
 * The fast lane: every Well app without a running pact as an icon tile. One
 * tap selects the app; the dialog then holds the same anti-reflex pause as
 * the pact overlay before the minute choice.
 */
@Composable
private fun RequestTimePanel(
    packages: List<String>,
    names: Map<String, String>,
    onClick: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(
            stringResource(R.string.home_request_time_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        packages.chunked(REQUEST_GRID_COLUMNS).forEachIndexed { index, rowPackages ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPackages.forEach { pkg ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(
                                onClick = { onClick(pkg) },
                                onClickLabel = stringResource(R.string.pacts_request_time)
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        AppIcon(packageName = pkg, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = names[pkg] ?: pkg,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Pad partial rows so tiles keep the same width as full rows.
                repeat(REQUEST_GRID_COLUMNS - rowPackages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WellAppRow(
    app: WellApp,
    appName: String,
    stats: AppOpenStats?,
    removalAt: Long?,
    nowMillis: Long,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = stringResource(R.string.home_guard_edit_label)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = app.packageName, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.pacts_today_stats, stats?.opens ?: 0, stats?.reflexOpens ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (removalAt != null) {
                    Text(
                        stringResource(
                            R.string.well_app_removal_pending,
                            formatDuration(GuardStatus.minutesUntil(removalAt, nowMillis).coerceAtLeast(1))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  REQUEST-TIME DIALOG
// ────────────────────────────────────────────────────────────

/**
 * The pact overlay's in-app twin. With Warden greying on, a Well app is
 * OS-suspended and its launcher icon leads only to a system "app is paused"
 * dialog — so the conscious time choice happens here instead. The same
 * few-second pause gates the choices so the dashboard isn't a lower-friction
 * side door than the overlay it mirrors.
 */
@Composable
private fun PactRequestDialog(
    packageName: String,
    appName: String,
    choicesMinutes: List<Int>,
    well: WellSnapshot,
    nowMillis: Long,
    onRequest: (minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(3) }
    val countdownDone = remainingSeconds <= 0
    // Too little in the Well for even the smallest choice: nothing to grant.
    val wellDry = choicesMinutes.isEmpty()
    val neededMinutes = WellApps.PACT_CHOICES.min()

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
                AppIcon(packageName = packageName, contentDescription = null, modifier = Modifier.size(32.dp))
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
                Text(
                    stringResource(R.string.well_level_line, well.availableMinutesAt(nowMillis), well.capacityMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (wellDry) {
                    val waitMillis = well.millisUntilMinutes(neededMinutes, nowMillis)
                    Text(
                        if (waitMillis != null) {
                            stringResource(
                                R.string.overlay_well_dry_eta,
                                neededMinutes,
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
                        stringResource(R.string.overlay_pact_well_desc, appName),
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
private fun WellEmptyState(onAddApps: () -> Unit) {
    val glow = MaterialTheme.colorScheme.primary
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
                        listOf(glow.copy(alpha = 0.28f), glow.copy(alpha = 0.06f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = glow,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.home_well_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_well_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddApps) {
            Text(stringResource(R.string.home_well_add))
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
