package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.ConditionalUnlock
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.ui.components.GlassCard

/**
 * Spellbook overview, organized by how often each thing is touched:
 * a Pacts hero up top (the app's signature all-day guard, with live numbers),
 * full preview cards grouped by when they apply, and compact rows for the
 * set-and-forget gear at the bottom.
 */
@Composable
fun SpellbookScreen(
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    schedules: List<Schedule>,
    namedTags: List<NamedTag>,
    appTimeLimitConfigs: Map<String, AppTimeLimit>,
    conditionalUnlocks: List<ConditionalUnlock>,
    installedApps: List<AppInfo>,
    pactOpenStats: Map<String, AppOpenStats>,
    onNavigateToPacts: () -> Unit,
    onNavigateToEnchantments: () -> Unit,
    onNavigateToQuickSpells: () -> Unit,
    onNavigateToRituals: () -> Unit,
    onNavigateToTalismans: () -> Unit,
    onNavigateToTimeLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pactConfigs = appTimeLimitConfigs.filterValues { it.pactModeEnabled }
    val plainLimits = appTimeLimitConfigs.filterValues { !it.pactModeEnabled }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------------------------------ Pacts hero
        PactsHeroCard(
            pactConfigs = pactConfigs,
            pactOpenStats = pactOpenStats,
            installedApps = installedApps,
            onSeeAll = onNavigateToPacts
        )

        // ------------------------------------------------------------- Focus sessions
        SpellbookGroupHeader(stringResource(R.string.spellbook_group_focus))

        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_enchantments),
            icon = Icons.Filled.Lock,
            count = blockerLists.size,
            onSeeAll = onNavigateToEnchantments
        ) {
            if (blockerLists.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_enchantments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blockerLists.take(3).forEach { blocker ->
                    val mode = if (blocker.mode == BlockerMode.BLACKLIST) stringResource(R.string.label_banish) else stringResource(R.string.label_shield)
                    val appCount = blocker.effectiveApps.size
                    val siteCount = blocker.effectiveWebsites.size
                    val appText = pluralStringResource(R.plurals.spellbook_app_count, appCount, appCount)
                    val siteText = if (siteCount > 0) ", " + pluralStringResource(R.plurals.spellbook_site_count, siteCount, siteCount) else ""
                    Text(
                        "${blocker.name} - $mode - $appText$siteText",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (blockerLists.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, blockerLists.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_rituals),
            icon = Icons.Filled.DateRange,
            count = schedules.size,
            onSeeAll = onNavigateToRituals
        ) {
            if (schedules.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_rituals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                schedules.take(3).forEach { schedule ->
                    val days = schedule.effectiveDays.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                    Text(
                        "${schedule.name} - ${schedule.effectiveStartTime}-${schedule.effectiveEndTime} - $days",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (schedules.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, schedules.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ------------------------------------------------------------ Everyday guards
        SpellbookGroupHeader(stringResource(R.string.spellbook_group_guards))

        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_time_limits),
            icon = Icons.Filled.Timer,
            count = plainLimits.size,
            actionLabel = stringResource(R.string.action_manage),
            onSeeAll = onNavigateToTimeLimits
        ) {
            if (plainLimits.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_time_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                plainLimits.entries.take(3).forEach { (pkg, config) ->
                    val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                    Text(
                        stringResource(R.string.spellbook_app_time_limit, appName, config.dailyLimitMinutes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (plainLimits.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, plainLimits.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (conditionalUnlocks.isNotEmpty()) {
                Text(
                    pluralStringResource(
                        R.plurals.spellbook_unlock_rule_count,
                        conditionalUnlocks.size,
                        conditionalUnlocks.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // ----------------------------------------------------------- Shortcuts & gear
        SpellbookGroupHeader(stringResource(R.string.spellbook_group_gear))

        SpellbookCompactRow(
            title = stringResource(R.string.spellbook_quick_spells),
            icon = Icons.Filled.AutoFixHigh,
            count = focusPresets.size,
            onClick = onNavigateToQuickSpells
        )

        Spacer(modifier = Modifier.height(8.dp))

        SpellbookCompactRow(
            title = stringResource(R.string.spellbook_talismans),
            icon = Icons.Filled.Nfc,
            count = namedTags.size,
            onClick = onNavigateToTalismans
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PactsHeroCard(
    pactConfigs: Map<String, AppTimeLimit>,
    pactOpenStats: Map<String, AppOpenStats>,
    installedApps: List<AppInfo>,
    onSeeAll: () -> Unit
) {
    val totalOpens = pactConfigs.keys.sumOf { pactOpenStats[it]?.opens ?: 0 }
    val totalReflexes = pactConfigs.keys.sumOf { pactOpenStats[it]?.reflexOpens ?: 0 }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeeAll),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                )
                            ),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.spellbook_pacts),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (pactConfigs.isNotEmpty()) {
                        Text(
                            pluralStringResource(
                                R.plurals.spellbook_pact_app_count,
                                pactConfigs.size,
                                pactConfigs.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.action_manage))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (pactConfigs.isEmpty()) {
            Text(
                stringResource(R.string.spellbook_pacts_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            pactConfigs.entries.take(3).forEach { (pkg, _) ->
                val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                val stats = pactOpenStats[pkg] ?: AppOpenStats()
                Text(
                    stringResource(R.string.spellbook_pact_app_line, appName, stats.opens),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (pactConfigs.size > 3) {
                Text(
                    stringResource(R.string.spellbook_more_count, pactConfigs.size - 3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.spellbook_pacts_stats, totalOpens, totalReflexes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SpellbookGroupHeader(title: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** One-line entry for the set-and-forget sections: icon, title, count, chevron. */
@Composable
private fun SpellbookCompactRow(
    title: String,
    icon: ImageVector,
    count: Int,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (count > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SpellbookSectionCard(
    title: String,
    icon: ImageVector,
    count: Int,
    actionLabel: String? = null,
    onSeeAll: () -> Unit,
    content: @Composable () -> Unit
) {
    val resolvedActionLabel = actionLabel ?: stringResource(R.string.action_see_all)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (count > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onSeeAll) {
                    Text(resolvedActionLabel)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
    }
}
