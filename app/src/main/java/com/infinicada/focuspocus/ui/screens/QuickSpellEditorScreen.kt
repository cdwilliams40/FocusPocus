package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.PresetAction
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSpellEditorScreen(
    presetToEdit: FocusPreset?,
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onSave: (FocusPreset) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(presetToEdit?.name ?: "") }
    var selectedBlocker by remember {
        mutableStateOf(
            if (presetToEdit != null) blockerLists.find { it.name == presetToEdit.blockerName }
            else blockerLists.firstOrNull()
        )
    }
    var selectedDuration by remember { mutableIntStateOf(presetToEdit?.durationMinutes ?: 25) }
    var breaksEnabled by remember { mutableStateOf(presetToEdit?.breaksEnabled ?: true) }
    var selectedAction by remember { mutableStateOf(presetToEdit?.action ?: PresetAction.TOGGLE) }
    var tempDurationMinutes by remember { mutableIntStateOf(presetToEdit?.tempDurationMinutes ?: 30) }
    var selectedTalisman by remember {
        mutableStateOf(
            if (presetToEdit?.talismanId != null) namedTags.find { it.id == presetToEdit.talismanId }
            else null
        )
    }

    var blockerDropdownExpanded by remember { mutableStateOf(false) }
    var durationDropdownExpanded by remember { mutableStateOf(false) }
    var talismanDropdownExpanded by remember { mutableStateOf(false) }
    var actionDropdownExpanded by remember { mutableStateOf(false) }
    var tempDurationDropdownExpanded by remember { mutableStateOf(false) }

    val durations = listOf(
        15 to "15 minutes",
        25 to "25 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        120 to "2 hours",
        240 to "4 hours",
        480 to "8 hours",
        0 to "Unlimited"
    )

    val actionLabels = mapOf(
        PresetAction.TOGGLE to "Toggle",
        PresetAction.TEMP_ENABLE to "Temporary Enable",
        PresetAction.TEMP_DISABLE to "Temporary Disable"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (presetToEdit != null) "Edit Quick Spell" else "Create Quick Spell") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 100) name = it },
                label = { Text("Name") },
                supportingText = {
                    Text(
                        text = "${name.length}/100",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Blocker selection
            ExposedDropdownMenuBox(
                expanded = blockerDropdownExpanded,
                onExpandedChange = { blockerDropdownExpanded = !blockerDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedBlocker?.name ?: "Select Enchantment",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Enchantment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockerDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = blockerDropdownExpanded,
                    onDismissRequest = { blockerDropdownExpanded = false }
                ) {
                    blockerLists.forEach { blocker ->
                        DropdownMenuItem(
                            text = { Text(blocker.name) },
                            onClick = {
                                selectedBlocker = blocker
                                blockerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration selection
            ExposedDropdownMenuBox(
                expanded = durationDropdownExpanded,
                onExpandedChange = { durationDropdownExpanded = !durationDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = durations.find { it.first == selectedDuration }?.second ?: "Select Duration",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Duration") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = durationDropdownExpanded,
                    onDismissRequest = { durationDropdownExpanded = false }
                ) {
                    durations.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedDuration = minutes
                                durationDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Talisman binding
            ExposedDropdownMenuBox(
                expanded = talismanDropdownExpanded,
                onExpandedChange = { talismanDropdownExpanded = !talismanDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedTalisman?.name ?: "None (tap to select)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bind to Talisman") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = talismanDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = talismanDropdownExpanded,
                    onDismissRequest = { talismanDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedTalisman = null
                            talismanDropdownExpanded = false
                        }
                    )
                    namedTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag.name) },
                            onClick = {
                                selectedTalisman = tag
                                talismanDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action type
            ExposedDropdownMenuBox(
                expanded = actionDropdownExpanded,
                onExpandedChange = { actionDropdownExpanded = !actionDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = actionLabels[selectedAction] ?: "Toggle",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Action Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = actionDropdownExpanded,
                    onDismissRequest = { actionDropdownExpanded = false }
                ) {
                    PresetAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(actionLabels[action] ?: action.name) },
                            onClick = {
                                selectedAction = action
                                actionDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Temp duration (for TEMP_ENABLE and TEMP_DISABLE)
            if (selectedAction != PresetAction.TOGGLE) {
                Spacer(modifier = Modifier.height(16.dp))
                val tempDurations = listOf(
                    5 to "5 min", 10 to "10 min", 15 to "15 min",
                    30 to "30 min", 60 to "1 hour", 120 to "2 hours"
                )
                ExposedDropdownMenuBox(
                    expanded = tempDurationDropdownExpanded,
                    onExpandedChange = { tempDurationDropdownExpanded = !tempDurationDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = tempDurations.find { it.first == tempDurationMinutes }?.second ?: "$tempDurationMinutes min",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Temp Duration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tempDurationDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = tempDurationDropdownExpanded,
                        onDismissRequest = { tempDurationDropdownExpanded = false }
                    ) {
                        tempDurations.forEach { (minutes, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    tempDurationMinutes = minutes
                                    tempDurationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breaks toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Allow Breaks")
                Switch(
                    checked = breaksEnabled,
                    onCheckedChange = { breaksEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        selectedBlocker?.let { blocker ->
                            onSave(
                                FocusPreset(
                                    id = presetToEdit?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    blockerName = blocker.name,
                                    durationMinutes = selectedDuration,
                                    breaksEnabled = breaksEnabled,
                                    talismanId = selectedTalisman?.id,
                                    action = selectedAction,
                                    tempDurationMinutes = tempDurationMinutes
                                )
                            )
                        }
                    },
                    enabled = name.isNotBlank() && selectedBlocker != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
