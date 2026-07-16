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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
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
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.ui.components.GlassCard

/**
 * Spellbook overview: the grimoire of focus-session configuration —
 * Enchantments and Rituals up top, a compact row for Talismans below.
 * Everyday guards (pacts, wards) live on the Home dashboard, not here.
 */
@Composable
fun SpellbookScreen(
    blockerLists: List<Blocker>,
    schedules: List<Schedule>,
    namedTags: List<NamedTag>,
    onNavigateToEnchantments: () -> Unit,
    onNavigateToRituals: () -> Unit,
    onNavigateToTalismans: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------------------------- Focus sessions
        SpellbookGroupHeader(stringResource(R.string.spellbook_group_focus), topSpacing = 0.dp)

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

        // ------------------------------------------------------------------ More magic
        SpellbookGroupHeader(stringResource(R.string.spellbook_group_gear))

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
private fun SpellbookGroupHeader(
    title: String,
    topSpacing: androidx.compose.ui.unit.Dp = 20.dp
) {
    Spacer(modifier = Modifier.height(topSpacing))
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
    onSeeAll: () -> Unit,
    content: @Composable () -> Unit
) {
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
                    Text(stringResource(R.string.action_see_all))
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
