package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.model.ConditionalUnlock
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.ui.formatDuration

@Composable
fun SpellbookScreen(
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    schedules: List<Schedule>,
    namedTags: List<NamedTag>,
    appTimeLimits: Map<String, Int>,
    conditionalUnlocks: List<ConditionalUnlock>,
    installedApps: List<AppInfo>,
    onNavigateToEnchantments: () -> Unit,
    onNavigateToQuickSpells: () -> Unit,
    onNavigateToRituals: () -> Unit,
    onNavigateToTalismans: () -> Unit,
    onNavigateToTimeLimits: () -> Unit,
    onNavigateToConditionalUnlocks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.spellbook_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Enchantments Section
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

        // Quick Spells Section
        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_quick_spells),
            icon = Icons.Filled.AutoFixHigh,
            count = focusPresets.size,
            onSeeAll = onNavigateToQuickSpells
        ) {
            if (focusPresets.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_quick_spells),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                focusPresets.take(3).forEach { preset ->
                    val breaksSuffix = if (preset.breaksEnabled) stringResource(R.string.spellbook_breaks_suffix) else ""
                    Text(
                        "${preset.name} - ${formatDuration(preset.durationMinutes)}$breaksSuffix",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (focusPresets.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, focusPresets.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rituals Section
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

        Spacer(modifier = Modifier.height(12.dp))

        // Talismans Section
        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_talismans),
            icon = Icons.Filled.Nfc,
            count = namedTags.size,
            actionLabel = stringResource(R.string.action_manage),
            onSeeAll = onNavigateToTalismans
        ) {
            if (namedTags.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_talismans),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                namedTags.take(3).forEach { tag ->
                    Text(tag.name, style = MaterialTheme.typography.bodySmall)
                }
                if (namedTags.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, namedTags.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Time Limits Section
        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_time_limits),
            icon = Icons.Filled.Timer,
            count = appTimeLimits.size,
            actionLabel = stringResource(R.string.action_manage),
            onSeeAll = onNavigateToTimeLimits
        ) {
            if (appTimeLimits.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_time_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                appTimeLimits.entries.take(3).forEach { (pkg, limit) ->
                    val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                    Text(
                        stringResource(R.string.spellbook_app_time_limit, appName, limit),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (appTimeLimits.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, appTimeLimits.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conditional Unlocks Section
        SpellbookSectionCard(
            title = stringResource(R.string.spellbook_conditional_unlocks),
            icon = Icons.Filled.LockOpen,
            count = conditionalUnlocks.size,
            actionLabel = stringResource(R.string.action_manage),
            onSeeAll = onNavigateToConditionalUnlocks
        ) {
            if (conditionalUnlocks.isEmpty()) {
                Text(
                    stringResource(R.string.spellbook_no_conditional_unlocks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                conditionalUnlocks.take(3).forEach { rule ->
                    val requiredAppName = installedApps.find { it.packageName == rule.requiredAppPackage }?.name
                        ?: rule.requiredAppPackage
                    Text(
                        "${rule.name} - ${rule.requiredMinutes}m in $requiredAppName",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (conditionalUnlocks.size > 3) {
                    Text(
                        stringResource(R.string.spellbook_more_count, conditionalUnlocks.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                                color = MaterialTheme.colorScheme.primaryContainer,
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
}