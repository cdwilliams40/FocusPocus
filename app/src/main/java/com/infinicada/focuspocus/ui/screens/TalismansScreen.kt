package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Talisman
import com.infinicada.focuspocus.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalismansScreen(
    lastScannedTagId: String?,
    talismans: List<Talisman>,
    blockerLists: List<Blocker>,
    onSaveTag: (String) -> Unit,
    onSaveSetup: (Talisman) -> Unit,
    onDeleteTag: (Talisman) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Talismans locking the running session; neither editable nor disenchantable. */
    lockedTalismanIds: Set<String> = emptySet()
) {
    var tagName by rememberSaveable { mutableStateOf("") }
    var editingTagId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTagId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = talismans.find { it.id == editingTagId && it.id !in lockedTalismanIds }
    val deleting = talismans.find { it.id == deletingTagId && it.id !in lockedTalismanIds }
    val scannedExisting = lastScannedTagId?.let { id -> talismans.find { it.id == id } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.talismans_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // NFC Scan Section
            item {
                val isFreshUnknownTag = lastScannedTagId != null && scannedExisting == null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isFreshUnknownTag) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.talismans_bind_new), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        lastScannedTagId?.let {
                            Text(
                                if (scannedExisting != null) {
                                    stringResource(R.string.talismans_last_scanned_known, scannedExisting.name, it)
                                } else {
                                    stringResource(R.string.talismans_last_scanned, it)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = tagName,
                                onValueChange = { if (it.length <= 100) tagName = it },
                                label = { Text(stringResource(R.string.talismans_name_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onSaveTag(tagName.trim())
                                    tagName = ""
                                },
                                enabled = tagName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.talismans_enchant))
                            }
                        } ?: Text(
                            stringResource(R.string.talismans_scan_nfc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Enchanted Items Header
            if (talismans.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.talismans_enchanted_items), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(talismans) { tag ->
                val isLocked = tag.id in lockedTalismanIds
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = if (isLocked) {
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        CardDefaults.elevatedCardColors()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                                if (isLocked) {
                                    Text(
                                        stringResource(R.string.label_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                talismanSummary(tag, blockerLists),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLocked) {
                                Text(
                                    stringResource(R.string.talismans_cannot_edit_active),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = { editingTagId = tag.id },
                                enabled = !isLocked
                            ) {
                                Text(stringResource(R.string.talisman_setup_edit))
                            }
                            Button(
                                onClick = { deletingTagId = tag.id },
                                enabled = !isLocked,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.talismans_disenchant))
                            }
                        }
                    }
                }
            }

            if (talismans.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.talismans_none_bound),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    editing?.let { talisman ->
        TalismanSetupDialog(
            talisman = talisman,
            blockerLists = blockerLists,
            onSave = {
                onSaveSetup(it)
                editingTagId = null
            },
            onDismiss = { editingTagId = null }
        )
    }

    deleting?.let { talisman ->
        AlertDialog(
            onDismissRequest = { deletingTagId = null },
            title = { Text(stringResource(R.string.talismans_delete_confirm_title)) },
            text = { Text(stringResource(R.string.talismans_delete_confirm_message, talisman.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingTagId = null
                        onDeleteTag(talisman)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.talismans_disenchant))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTagId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** "Social, Games · 60 min · only this talisman ends it" */
@Composable
private fun talismanSummary(talisman: Talisman, blockerLists: List<Blocker>): String {
    val names = talisman.enchantments.filter { name -> blockerLists.any { it.name == name } }
    if (names.isEmpty()) return stringResource(R.string.talisman_setup_none)
    val parts = mutableListOf(names.joinToString(", "))
    parts += talisman.timedMinutes?.let { stringResource(R.string.talisman_setup_minutes, it) }
        ?: stringResource(R.string.talisman_setup_open_ended)
    if (talisman.locksSession) parts += stringResource(R.string.talisman_setup_locked_short)
    return parts.joinToString(" · ")
}

private val DURATION_CHOICES = listOf(0, 15, 25, 30, 45, 60, 90, 120)

/** What a tap of [talisman] starts: enchantments, length, breaks, and whether only it can end it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalismanSetupDialog(
    talisman: Talisman,
    blockerLists: List<Blocker>,
    onSave: (Talisman) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedList by rememberSaveable(talisman.id) { mutableStateOf(talisman.enchantments) }
    val selected = selectedList.toSet()
    var minutes by rememberSaveable(talisman.id) { mutableIntStateOf(talisman.timedMinutes ?: 0) }
    var breaks by rememberSaveable(talisman.id) { mutableStateOf(talisman.breaksAllowed) }
    var locked by rememberSaveable(talisman.id) { mutableStateOf(talisman.locksSession) }
    val durationChoices = (DURATION_CHOICES + minutes).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.talisman_setup_title, talisman.name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.talisman_setup_enchantments), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                if (blockerLists.isEmpty()) {
                    Text(
                        stringResource(R.string.talisman_setup_no_enchantments),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    blockerLists.forEach { blocker ->
                        FilterChip(
                            selected = blocker.name in selected,
                            onClick = {
                                selectedList = if (blocker.name in selected) selectedList - blocker.name else selectedList + blocker.name
                            },
                            label = { Text(blocker.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.talisman_setup_length), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationChoices.forEach { choice ->
                        FilterChip(
                            selected = minutes == choice,
                            onClick = { minutes = choice },
                            label = {
                                Text(
                                    if (choice == 0) stringResource(R.string.talisman_setup_open_ended)
                                    else stringResource(R.string.talisman_setup_minutes, choice)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SetupSwitchRow(stringResource(R.string.home_allow_breaks), breaks) { breaks = it }
                SetupSwitchRow(stringResource(R.string.talisman_setup_locked), locked) { locked = it }
                Text(
                    stringResource(R.string.talisman_setup_locked_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        talisman.copy(
                            // Keep the dialog's order stable: the enchantment list's own order.
                            blockerNames = blockerLists.map { it.name }.filter { it in selected },
                            durationMinutes = minutes,
                            breaksEnabled = breaks,
                            requireToEnd = locked
                        )
                    )
                },
                enabled = selected.isNotEmpty()
            ) {
                Text(stringResource(R.string.talisman_setup_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun SetupSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
