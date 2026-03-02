package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.AppInfo
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.Schedule
import com.infinicada.focuspocus.formatDuration

@Composable
fun SpellbookScreen(
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    schedules: List<Schedule>,
    namedTags: List<NamedTag>,
    appTimeLimits: Map<String, Int>,
    installedApps: List<AppInfo>,
    onNavigateToEnchantments: () -> Unit,
    onNavigateToQuickSpells: () -> Unit,
    onNavigateToRituals: () -> Unit,
    onNavigateToTalismans: () -> Unit,
    onNavigateToTimeLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Spellbook", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Enchantments Section
        SpellbookSectionCard(
            title = "Enchantments",
            icon = Icons.Filled.Lock,
            count = blockerLists.size,
            onSeeAll = onNavigateToEnchantments
        ) {
            if (blockerLists.isEmpty()) {
                Text(
                    "No enchantments yet. Create one to define which apps to block.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blockerLists.take(3).forEach { blocker ->
                    val mode = if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Shield"
                    val appCount = blocker.apps.size
                    val siteCount = blocker.websites.orEmpty().size
                    Text(
                        "${blocker.name} - $mode - $appCount app${if (appCount != 1) "s" else ""}${if (siteCount > 0) ", $siteCount site${if (siteCount != 1) "s" else ""}" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (blockerLists.size > 3) {
                    Text(
                        "+${blockerLists.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Spells Section
        SpellbookSectionCard(
            title = "Quick Spells",
            icon = Icons.Filled.AutoFixHigh,
            count = focusPresets.size,
            onSeeAll = onNavigateToQuickSpells
        ) {
            if (focusPresets.isEmpty()) {
                Text(
                    "No quick spells yet. Create presets for one-tap focus sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                focusPresets.take(3).forEach { preset ->
                    Text(
                        "${preset.name} - ${formatDuration(preset.durationMinutes)}${if (preset.breaksEnabled) " - Breaks" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (focusPresets.size > 3) {
                    Text(
                        "+${focusPresets.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Rituals Section
        SpellbookSectionCard(
            title = "Rituals",
            icon = Icons.Filled.DateRange,
            count = schedules.size,
            onSeeAll = onNavigateToRituals
        ) {
            if (schedules.isEmpty()) {
                Text(
                    "No rituals yet. Schedule automatic focus sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                schedules.take(3).forEach { schedule ->
                    val days = schedule.days.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                    Text(
                        "${schedule.name} - ${schedule.startTime}-${schedule.endTime} - $days",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (schedules.size > 3) {
                    Text(
                        "+${schedules.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Talismans Section
        SpellbookSectionCard(
            title = "Talismans",
            icon = Icons.Filled.Nfc,
            count = namedTags.size,
            actionLabel = "Manage",
            onSeeAll = onNavigateToTalismans
        ) {
            if (namedTags.isEmpty()) {
                Text(
                    "No talismans bound. Scan an NFC tag to get started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                namedTags.take(3).forEach { tag ->
                    Text(tag.name, style = MaterialTheme.typography.bodySmall)
                }
                if (namedTags.size > 3) {
                    Text(
                        "+${namedTags.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Time Limits Section
        SpellbookSectionCard(
            title = "Time Limits",
            icon = Icons.Filled.Timer,
            count = appTimeLimits.size,
            actionLabel = "Manage",
            onSeeAll = onNavigateToTimeLimits
        ) {
            if (appTimeLimits.isEmpty()) {
                Text(
                    "No time limits set. Restrict daily app usage even outside focus mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                appTimeLimits.entries.take(3).forEach { (pkg, limit) ->
                    val appName = installedApps.find { it.packageName == pkg }?.name ?: pkg
                    Text(
                        "$appName - $limit min/day",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (appTimeLimits.size > 3) {
                    Text(
                        "+${appTimeLimits.size - 3} more",
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
    actionLabel: String = "See All",
    onSeeAll: () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (count > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onSeeAll) {
                    Text(actionLabel)
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