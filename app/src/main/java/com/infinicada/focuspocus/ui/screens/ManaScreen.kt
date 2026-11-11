package com.infinicada.focuspocus.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.SectionHeader

/**
 * Full-screen mana page (Settings pattern: own Scaffold over the arcane
 * background, back arrow, early-returned from FocusPocusApp): the balance and
 * the two things mana buys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManaScreen(
    balance: Long,
    sessionActive: Boolean,
    breaksAllowed: Boolean,
    wellEnabled: Boolean,
    wellChannelCost: Long,
    wellHasRoom: Boolean,
    onChannelWell: () -> Boolean,
    onBuyExtraBreak: () -> Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ArcaneBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.mana_screen_title))
                            Text(
                                stringResource(R.string.mana_screen_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.mana_balance_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.mana_chip, balance),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.perks_header))
                Spacer(modifier = Modifier.height(12.dp))

                if (wellEnabled) {
                    PerkCard(
                        title = stringResource(R.string.perk_well_channel_title),
                        description = stringResource(R.string.perk_well_channel_desc, Perk.WELL_CHANNEL_GRANT),
                        warning = if (!wellHasRoom) {
                            stringResource(R.string.well_channel_no_room, Perk.WELL_CHANNEL_GRANT)
                        } else null,
                        cost = wellChannelCost,
                        enabled = wellHasRoom && balance >= wellChannelCost,
                        onBuy = {
                            Toast.makeText(
                                context,
                                if (onChannelWell()) {
                                    context.getString(R.string.well_channel_done, Perk.WELL_CHANNEL_GRANT)
                                } else {
                                    context.getString(R.string.well_channel_failed)
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                PerkCard(
                    title = stringResource(R.string.perk_extra_break_title),
                    description = stringResource(R.string.perk_extra_break_desc),
                    warning = when {
                        !sessionActive -> stringResource(R.string.perk_extra_break_inactive)
                        !breaksAllowed -> stringResource(R.string.perk_extra_break_no_breaks)
                        else -> null
                    },
                    cost = Perk.EXTRA_BREAK.costMana,
                    enabled = sessionActive && breaksAllowed && balance >= Perk.EXTRA_BREAK.costMana,
                    onBuy = {
                        if (!onBuyExtraBreak()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.perk_not_enough_mana),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PerkCard(
    title: String,
    description: String,
    warning: String?,
    cost: Long,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (warning != null) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onBuy, enabled = enabled) {
                Text(
                    stringResource(R.string.perk_buy) + " · " +
                        stringResource(R.string.mana_cost_format, cost)
                )
            }
        }
    }
}
