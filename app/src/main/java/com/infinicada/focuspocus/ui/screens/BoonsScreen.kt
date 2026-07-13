package com.infinicada.focuspocus.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.AppInfo
import com.infinicada.focuspocus.model.Boon
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.components.ArcaneBackground
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.SectionHeader
import java.util.UUID

/**
 * Full-screen boons manager (Settings pattern: own Scaffold over the arcane
 * background, back arrow, early-returned from FocusPocusApp). Honor-system
 * boons on top, the code-defined perk catalog at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoonsScreen(
    balance: Long,
    boons: List<Boon>,
    sessionActive: Boolean,
    breaksAllowed: Boolean,
    pactedApps: List<AppInfo>,
    isSealedAvailableToday: (String) -> Boolean,
    isSealedOverDailyLimit: (String) -> Boolean,
    onRedeemBoon: (Boon) -> Boolean,
    onSaveBoon: (Boon) -> Boolean,
    onDeleteBoon: (String) -> Unit,
    onBuyExtraBreak: () -> Boolean,
    onBuySealedMinutes: (String) -> Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingBoon by remember { mutableStateOf<Boon?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    // The editor is an in-screen sub-state: back closes it before the screen.
    BackHandler(enabled = showEditor) {
        showEditor = false
        editingBoon = null
    }

    ArcaneBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.boons_title))
                            Text(
                                stringResource(R.string.boons_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showEditor) {
                                showEditor = false
                                editingBoon = null
                            } else {
                                onNavigateBack()
                            }
                        }) {
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
            if (showEditor) {
                BoonEditor(
                    boon = editingBoon,
                    onSave = { boon ->
                        if (onSaveBoon(boon)) {
                            showEditor = false
                            editingBoon = null
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.boon_limit_reached),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDelete = { boonId ->
                        onDeleteBoon(boonId)
                        showEditor = false
                        editingBoon = null
                    },
                    onCancel = {
                        showEditor = false
                        editingBoon = null
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Balance header
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
                                stringResource(R.string.boons_balance_header),
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

                Spacer(modifier = Modifier.height(20.dp))

                // Boon list
                if (boons.isEmpty()) {
                    Text(
                        stringResource(R.string.boons_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    boons.forEach { boon ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(boon.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.boon_cost_format, boon.costMana),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    if (boon.note.isNotBlank()) {
                                        Text(
                                            boon.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Button(
                                        onClick = {
                                            if (onRedeemBoon(boon)) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.boon_redeemed_toast, boon.title),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        enabled = balance >= boon.costMana
                                    ) {
                                        Text(stringResource(R.string.boon_redeem))
                                    }
                                    TextButton(onClick = {
                                        editingBoon = boon
                                        showEditor = true
                                    }) {
                                        Text(stringResource(R.string.action_edit))
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        editingBoon = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.boon_add))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Perks — the small, deliberate in-app catalog
                SectionHeader(stringResource(R.string.perks_header))
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.perk_extra_break_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.perk_extra_break_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!sessionActive) {
                                Text(
                                    stringResource(R.string.perk_extra_break_inactive),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else if (!breaksAllowed) {
                                Text(
                                    stringResource(R.string.perk_extra_break_no_breaks),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!onBuyExtraBreak()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.perk_not_enough_mana),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = sessionActive && breaksAllowed && balance >= Perk.EXTRA_BREAK.costMana
                        ) {
                            Text(
                                stringResource(R.string.perk_buy) + " · " +
                                    stringResource(R.string.boon_cost_format, Perk.EXTRA_BREAK.costMana)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.perk_sealed_minutes_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.perk_sealed_minutes_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pactedApps.isEmpty()) {
                                Text(
                                    stringResource(R.string.perk_sealed_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showAppPicker = true },
                            enabled = pactedApps.isNotEmpty() && balance >= Perk.SEALED_MINUTES.costMana
                        ) {
                            Text(
                                stringResource(R.string.perk_pick_app) + " · " +
                                    stringResource(R.string.boon_cost_format, Perk.SEALED_MINUTES.costMana)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAppPicker) {
        com.infinicada.focuspocus.ui.components.SingleAppPickerDialog(
            installedApps = pactedApps,
            title = stringResource(R.string.perk_pick_app),
            onPick = { app ->
                showAppPicker = false
                val message = when {
                    !isSealedAvailableToday(app.packageName) ->
                        context.getString(R.string.perk_sealed_used_today)
                    isSealedOverDailyLimit(app.packageName) ->
                        context.getString(R.string.perk_sealed_over_limit)
                    !onBuySealedMinutes(app.packageName) ->
                        context.getString(R.string.perk_not_enough_mana)
                    else -> context.getString(R.string.boon_redeemed_toast, app.name)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun BoonEditor(
    boon: Boon?,
    onSave: (Boon) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(boon?.title ?: "") }
    var costText by remember { mutableStateOf(boon?.costMana?.toString() ?: "") }
    var note by remember { mutableStateOf(boon?.note ?: "") }
    val cost = costText.toLongOrNull() ?: 0L
    val valid = title.isNotBlank() && cost > 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(if (boon == null) R.string.boon_create_title else R.string.boon_edit_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.boon_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = costText,
            onValueChange = { new -> costText = new.filter { it.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.boon_cost_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.boon_note_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onSave(
                        Boon(
                            id = boon?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            costMana = cost,
                            note = note.trim()
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.boon_save))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
        if (boon != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { onDelete(boon.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.boon_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
