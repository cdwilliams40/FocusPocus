package com.infinicada.focuspocus.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.ui.components.AppIcon
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.SectionHeader
import com.infinicada.focuspocus.ui.components.StatTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    blockerLists: List<Blocker>,
    installedApps: List<AppInfo>,
    modifier: Modifier = Modifier,
    focusSessions: List<FocusSession> = emptyList(),
    currentStreak: Int = 0,
    longestStreak: Int = 0,
    blockEvents: List<BlockEvent> = emptyList(),
    appTimeLimits: Map<String, Int> = emptyMap(),
    openDailyStats: Map<String, Map<String, AppOpenStats>> = emptyMap()
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    val sharedPreferences = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedTabIndex by remember { mutableIntStateOf(sharedPreferences.getInt(Constants.PrefsKeys.INSIGHTS_TIME_RANGE, 0)) }
    var selectedBlockerFilter by remember { mutableStateOf<Blocker?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }

    // Time range filtering
    val timeRangeLabels = listOf(
        stringResource(R.string.insights_today),
        stringResource(R.string.insights_this_week),
        stringResource(R.string.insights_this_month),
        stringResource(R.string.insights_all_time)
    )

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val weekStart = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthStart = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val rangeStart = when (selectedTabIndex) {
        0 -> todayStart  // Today
        1 -> weekStart   // This Week
        2 -> monthStart  // This Month
        else -> 0L       // All Time
    }

    val filteredSessions = remember(focusSessions, rangeStart) {
        focusSessions.filter { it.endTimeMillis >= rangeStart }
    }

    val filteredBlockEvents = remember(blockEvents, rangeStart) {
        blockEvents.filter { it.timestamp >= rangeStart }
    }

    // App opens within the selected range, aggregated per app ("yyyyMMdd" keys
    // sort lexicographically, so string comparison against the cutoff is safe).
    val openStatsInRange = remember(openDailyStats, rangeStart) {
        val cutoff = if (rangeStart == 0L) "00000000"
        else SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(rangeStart))
        val aggregated = mutableMapOf<String, AppOpenStats>()
        openDailyStats.filterKeys { it >= cutoff }.values.forEach { day ->
            day.forEach { (pkg, stats) ->
                val current = aggregated[pkg] ?: AppOpenStats()
                aggregated[pkg] = AppOpenStats(
                    opens = current.opens + stats.opens,
                    reflexOpens = current.reflexOpens + stats.reflexOpens
                )
            }
        }
        aggregated.entries.sortedByDescending { it.value.opens }
    }

    // Focus stats
    val totalFocusMinutes = remember(filteredSessions) { filteredSessions.sumOf { it.durationMinutes } }
    val avgSessionLength = remember(filteredSessions) {
        if (filteredSessions.isNotEmpty()) (totalFocusMinutes + filteredSessions.size / 2) / filteredSessions.size else 0
    }

    // Block stats
    val totalBlocks = filteredBlockEvents.size
    val topBlockedApps = remember(filteredBlockEvents) {
        filteredBlockEvents.groupBy { it.packageName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)
    }

    var usageStats by remember { mutableStateOf<List<com.infinicada.focuspocus.AppUsage>>(emptyList()) }

    LaunchedEffect(hasPermission, selectedTabIndex) {
        if (hasPermission) {
            val stats = withContext(Dispatchers.IO) {
                when (selectedTabIndex) {
                    0 -> UsageStatsHelper.getTodayUsage(context)
                    1 -> UsageStatsHelper.getWeeklyUsage(context)
                    else -> UsageStatsHelper.getMonthlyUsage(context)
                }
            }
            usageStats = stats
        } else {
            usageStats = emptyList()
        }
    }

    val filteredStats = remember(usageStats, selectedBlockerFilter) {
        val currentFilter = selectedBlockerFilter
        if (currentFilter == null) {
            usageStats
        } else {
            val blockedPackages = currentFilter.effectiveApps
            usageStats.filter { it.packageName in blockedPackages }
        }
    }

    val totalScreenTime = remember(filteredStats) {
        filteredStats.sumOf { it.totalTimeInForeground }
    }

    val allUsedMinutes = remember(appTimeLimits, hasPermission) {
        AppTimeLimitManager.getAllUsedMinutesToday(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeRangeLabels.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            sharedPreferences.edit().putInt(Constants.PrefsKeys.INSIGHTS_TIME_RANGE, index).apply()
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Streak tiles
        if (focusSessions.isNotEmpty() || currentStreak > 0) {
            item {
                SectionHeader(stringResource(R.string.insights_focus_streaks))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        value = "$currentStreak",
                        label = stringResource(R.string.insights_current),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "$longestStreak",
                        label = stringResource(R.string.insights_best),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${focusSessions.size}",
                        label = stringResource(R.string.insights_total),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Focus stat tiles
        if (filteredSessions.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.insights_focus_stats))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val hours = totalFocusMinutes / 60
                    val mins = totalFocusMinutes % 60
                    StatTile(
                        value = if (hours > 0) stringResource(R.string.insights_hours_minutes, hours, mins)
                                else stringResource(R.string.insights_minutes_only, mins),
                        label = stringResource(R.string.insights_total_focus),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = stringResource(R.string.insights_minutes_only, avgSessionLength),
                        label = stringResource(R.string.insights_avg_session),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${filteredSessions.size}",
                        label = stringResource(R.string.insights_sessions),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Daily Focus Trend
        if (filteredSessions.isNotEmpty() && selectedTabIndex <= 1) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                        Text(
                            stringResource(R.string.insights_daily_trend),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val dailyMinutes = remember(focusSessions) {
                            val cal = Calendar.getInstance()
                            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                            (6 downTo 0).map { daysAgo ->
                                cal.timeInMillis = System.currentTimeMillis()
                                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                                cal.set(Calendar.HOUR_OF_DAY, 0)
                                cal.set(Calendar.MINUTE, 0)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                val dayStart = cal.timeInMillis
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                                val dayEnd = cal.timeInMillis
                                cal.timeInMillis = dayStart
                                val label = dayFormat.format(Date(dayStart))
                                val minutes = focusSessions
                                    .filter { it.startTimeMillis >= dayStart && it.startTimeMillis < dayEnd }
                                    .sumOf { it.durationMinutes }
                                label to minutes
                            }
                        }

                        val maxMinutes = dailyMinutes.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

                        dailyMinutes.forEachIndexed { index, (label, minutes) ->
                            val isToday = index == dailyMinutes.lastIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else null,
                                    color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(36.dp)
                                )
                                MagnitudeBar(
                                    fraction = minutes.toFloat() / maxMinutes,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                Text(
                                    "${minutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else null,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                }
            }
        }

        // Blocking Stats Card
        if (filteredBlockEvents.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                        Text(
                            stringResource(R.string.insights_blocking_stats),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.insights_total_blocks, totalBlocks),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (topBlockedApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.insights_most_blocked),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val maxBlockCount = topBlockedApps.maxOf { it.value }.coerceAtLeast(1)
                            topBlockedApps.forEach { (pkg, count) ->
                                val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    MagnitudeBar(
                                        fraction = count.toFloat() / maxBlockCount,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(28.dp)
                                    )
                                }
                            }
                        }
                }
            }
        }

        // App Opens Card: how often tracked apps were opened and how many opens
        // were under-30-second reflexes, within the selected time range.
        if (openStatsInRange.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        stringResource(R.string.insights_app_opens),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalOpens = openStatsInRange.sumOf { it.value.opens }
                    val totalReflexes = openStatsInRange.sumOf { it.value.reflexOpens }
                    val reflexPercent = if (totalOpens > 0) (totalReflexes * 100) / totalOpens else 0
                    Text(
                        stringResource(R.string.insights_total_opens, totalOpens),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.insights_reflex_summary, totalReflexes, reflexPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxOpens = openStatsInRange.first().value.opens.coerceAtLeast(1)
                    openStatsInRange.take(8).forEach { (pkg, stats) ->
                        val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                appName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.width(96.dp)
                            )
                            MagnitudeBar(
                                fraction = stats.opens.toFloat() / maxOpens,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                stringResource(
                                    R.string.insights_opens_row_count,
                                    stats.opens, stats.reflexOpens
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.insights_reflex_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // App Time Limit Status
        if (appTimeLimits.isNotEmpty() && hasPermission) {
            item {
                SectionHeader(stringResource(R.string.insights_time_limit_status))
            }
            val limitEntries = appTimeLimits.entries.toList()
            items(limitEntries) { (pkg, limit) ->
                val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                val usedMinutes = allUsedMinutes[pkg] ?: 0
                val progress = if (limit > 0) (usedMinutes.toFloat() / limit).coerceIn(0f, 1f) else 0f

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = pkg,
                            contentDescription = appName,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.insights_time_used, usedMinutes, limit),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = if (usedMinutes >= limit) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Session History
        if (filteredSessions.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.insights_session_history))
            }

            val sortedSessions = filteredSessions.sortedByDescending { it.endTimeMillis }
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

            items(sortedSessions) { session ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session.blockerName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                dateFormat.format(Date(session.endTimeMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.insights_session_duration, session.durationMinutes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (session.breaksUsed > 0) {
                                Text(
                                    pluralStringResource(R.plurals.insights_session_breaks, session.breaksUsed, session.breaksUsed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.insights_usage_access_required),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.insights_usage_access_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                UsageStatsHelper.openUsageAccessSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.insights_grant_usage_access))
                        }
                    }
                }
            }
        } else {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (selectedTabIndex == 0) stringResource(R.string.insights_today_screen_time) else stringResource(R.string.insights_screen_time),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            UsageStatsHelper.formatDuration(totalScreenTime),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val filter = selectedBlockerFilter
                        if (filter != null) {
                            Text(
                                stringResource(R.string.insights_filtered_by, filter.name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = filterExpanded,
                    onExpandedChange = { filterExpanded = !filterExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedBlockerFilter?.name ?: stringResource(R.string.insights_all_apps),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.insights_filter_by_enchantment)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.insights_all_apps)) },
                            onClick = {
                                selectedBlockerFilter = null
                                filterExpanded = false
                            }
                        )
                        blockerLists.forEach { blocker ->
                            DropdownMenuItem(
                                text = { Text(blocker.name) },
                                onClick = {
                                    selectedBlockerFilter = blocker
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.insights_app_usage))
            }

            if (filteredStats.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.insights_no_usage_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredStats) { appUsage ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(
                                packageName = appUsage.packageName,
                                contentDescription = appUsage.appName,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    appUsage.appName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    appUsage.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                UsageStatsHelper.formatDuration(appUsage.totalTimeInForeground),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A thin horizontal bar showing a magnitude as a fraction of the row maximum,
 * with fully rounded ends, a visible recessive track, and a gradient that
 * brightens toward the bar's head.
 */
@Composable
private fun MagnitudeBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.55f), color)
                        )
                    )
            )
        }
    }
}
