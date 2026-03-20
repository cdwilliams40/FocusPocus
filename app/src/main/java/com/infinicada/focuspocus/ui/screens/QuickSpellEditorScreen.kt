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
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.model.PresetAction
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
        15 to stringResource(R.string.duration_15_min),
        25 to stringResource(R.string.duration_25_min),
        45 to stringResource(R.string.duration_45_min),
        60 to stringResource(R.string.duration_1_hour),
        120 to stringResource(R.string.duration_2_hours),
        240 to stringResource(R.string.duration_4_hours),
        480 to stringResource(R.string.duration_8_hours),
        0 to stringResource(R.string.duration_unlimited)
    )

    val actionLabels = mapOf(
        PresetAction.TOGGLE to stringResource(R.string.quick_spell_editor_action_toggle),
        PresetAction.TEMP_ENABLE to stringResource(R.string.quick_spell_editor_action_temp_enable),
        PresetAction.TEMP_DISABLE to stringResource(R.string.quick_spell_editor_action_temp_disable)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (presetToEdit != null) stringResource(R.string.quick_spell_editor_edit_title) else stringResource(R.string.quick_spell_editor_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
                label = { Text(stringResource(R.string.quick_spell_editor_name_label)) },
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
                    value = selectedBlocker?.name ?: stringResource(R.string.quick_spell_editor_select_enchantment),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.quick_spell_editor_enchantment_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockerDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                    value = durations.find { it.first == selectedDuration }?.second ?: stringResource(R.string.duration_select),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.duration_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                    value = selectedTalisman?.name ?: stringResource(R.string.quick_spell_editor_none_talisman),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.quick_spell_editor_bind_talisman)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = talismanDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = talismanDropdownExpanded,
                    onDismissRequest = { talismanDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.label_none)) },
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
                    value = actionLabels[selectedAction] ?: stringResource(R.string.quick_spell_editor_action_toggle),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.quick_spell_editor_action_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                    5 to stringResource(R.string.duration_5_min), 10 to stringResource(R.string.duration_10_min), 15 to stringResource(R.string.duration_15_min),
                    30 to stringResource(R.string.duration_30_min), 60 to stringResource(R.string.duration_1_hour), 120 to stringResource(R.string.duration_2_hours)
                )
                ExposedDropdownMenuBox(
                    expanded = tempDurationDropdownExpanded,
                    onExpandedChange = { tempDurationDropdownExpanded = !tempDurationDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = tempDurations.find { it.first == tempDurationMinutes }?.second ?: stringResource(R.string.format_duration_minutes, tempDurationMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.quick_spell_editor_temp_duration)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tempDurationDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                Text(stringResource(R.string.quick_spell_editor_allow_breaks))
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
                    Text(stringResource(R.string.action_cancel))
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
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
