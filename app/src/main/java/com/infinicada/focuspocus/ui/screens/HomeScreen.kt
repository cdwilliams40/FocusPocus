package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.Schedule

@Composable
fun Greeting(
    focusMode: Boolean,
    activeTagId: String?,
    namedTags: List<NamedTag>,
    activeBlockers: List<Blocker>,
    activeSchedule: Schedule?,
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    selectedPresetId: String?,
    focusDurationMinutes: Int,
    focusTimeRemaining: Int,
    isOnBreak: Boolean,
    breakTimeRemaining: Int,
    breaksUsedThisSession: Int,
    maxBreaksPerSession: Int,
    breaksAllowed: Boolean,
    sessionBreaksEnabled: Boolean,
    hideStopButton: Boolean = false,
    nfcLockMode: Boolean = false,
    emergencyBreakAvailable: Boolean = false,
    emergencyBreakDaysRemaining: Int = 0,
    currentStreak: Int = 0,
    onPresetSelected: (FocusPreset) -> Unit,
    onBlockerToggled: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onBlockerSelectorClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onEmergencyStop: () -> Unit = {},
    onScanQrCode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTagName = namedTags.find { it.id == activeTagId }?.name
    val boundTalismanName = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        namedTags.find { it.id == activeSchedule.unbindingTalismanId }?.name ?: stringResource(R.string.label_unknown_talisman)
    } else null

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Magical Status Text
            Text(
                text = when {
                    isOnBreak -> stringResource(R.string.home_status_on_break)
                    focusMode -> stringResource(R.string.home_status_active)
                    else -> stringResource(R.string.home_status_ready)
                },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = when {
                    isOnBreak -> MaterialTheme.colorScheme.tertiary
                    focusMode -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // NFC Lock Mode indicator - shown prominently during active focus
            if (focusMode && nfcLockMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.home_nfc_lock_active),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Preset chip row (only when not in schedule, not in focus mode, and valid presets exist)
            val validPresets = focusPresets.filter { preset ->
                preset.effectiveBlockerNames.all { name -> blockerLists.any { it.name == name } }
            }
            if (activeSchedule == null && !focusMode && validPresets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_quick_spells),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PresetChipRow(
                    presets = validPresets,
                    selectedPresetId = selectedPresetId,
                    onPresetSelected = onPresetSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Spell Selector Dropdown (only when not in schedule and not in focus mode)
            if (activeSchedule == null) {
                SpellSelectorMultiDropdown(
                    blockerLists = blockerLists,
                    selectedBlockers = activeBlockers,
                    enabled = !focusMode,
                    onBlockerToggled = onBlockerToggled,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Duration Selector
                DurationSelectorDropdown(
                    selectedDuration = focusDurationMinutes,
                    enabled = !focusMode,
                    onDurationSelected = onDurationSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Breaks Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_allow_breaks),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = sessionBreaksEnabled,
                        onCheckedChange = onSessionBreaksToggled,
                        enabled = !focusMode
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Session Timer Display (when active and timed)
            if (focusMode && focusTimeRemaining > 0 && !isOnBreak) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes = focusTimeRemaining / 60
                        val seconds = focusTimeRemaining % 60
                        Text(
                            text = "%d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.home_remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Break timer display
            if (isOnBreak) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes = breakTimeRemaining / 60
                        val seconds = breakTimeRemaining % 60
                        Text(
                            text = "%d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.home_break_remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Active Schedule Info (when controlled by schedule)
            if (activeSchedule != null && activeBlockers.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.home_enchantment_name, activeBlockers.joinToString(", ") { it.name }), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.home_ritual_name, activeSchedule.name), style = MaterialTheme.typography.bodyMedium)
                        if (boundTalismanName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.home_unbind_with, boundTalismanName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (focusMode && breaksAllowed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.home_breaks_used, breaksUsedThisSession, maxBreaksPerSession),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Break info when in focus mode (manual mode)
            if (focusMode && activeSchedule == null && breaksAllowed) {
                Text(
                    stringResource(R.string.home_breaks_used, breaksUsedThisSession, maxBreaksPerSession),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Break button when in focus mode
            if (focusMode && breaksAllowed && !isOnBreak && breaksUsedThisSession < maxBreaksPerSession) {
                Button(
                    onClick = onTakeBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    val breaksRemaining = maxBreaksPerSession - breaksUsedThisSession
                    Text(
                        stringResource(R.string.home_take_break_with_count, breaksRemaining),
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // End break early button
            if (isOnBreak) {
                Button(
                    onClick = onEndBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.home_end_break_early))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Emergency stop button
            var showEmergencyConfirm by remember { mutableStateOf(false) }
            if (focusMode && !isOnBreak && (!breaksAllowed || breaksUsedThisSession >= maxBreaksPerSession)) {
                if (emergencyBreakAvailable) {
                    Button(
                        onClick = { showEmergencyConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.home_emergency_stop), color = MaterialTheme.colorScheme.onError)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        pluralStringResource(R.plurals.home_emergency_stop_days_remaining, emergencyBreakDaysRemaining, emergencyBreakDaysRemaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (showEmergencyConfirm) {
                AlertDialog(
                    onDismissRequest = { showEmergencyConfirm = false },
                    title = { Text(stringResource(R.string.home_emergency_stop)) },
                    text = { Text(stringResource(R.string.home_emergency_stop_confirm_text)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEmergencyConfirm = false
                                onEmergencyStop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.home_use_emergency_stop))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showEmergencyConfirm = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            // Streak display
            if (currentStreak > 0) {
                Text(
                    stringResource(R.string.home_day_streak, currentStreak),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big Start/Stop Button
            val buttonColor = if (focusMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val isButtonEnabled = activeSchedule == null || activeSchedule.unbindingTalismanId == null
            val canCast = activeBlockers.isNotEmpty()

            // Hide stop button when: NFC lock mode, or when setting is on + session is timed
            val shouldHideButton = focusMode && (
                nfcLockMode ||
                (hideStopButton && focusDurationMinutes > 0 &&
                    !(activeSchedule != null && activeSchedule.unbindingTalismanId != null))
            )

            if (!shouldHideButton) {
                Button(
                    onClick = onStartClicked,
                    modifier = Modifier.size(140.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonEnabled && (focusMode || canCast)) buttonColor else Color.Gray
                    ),
                    enabled = isButtonEnabled && !isOnBreak && (focusMode || canCast)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = if (focusMode) stringResource(R.string.home_dispel_content_desc) else stringResource(R.string.home_cast_content_desc),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (focusMode) {
                                if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) stringResource(R.string.home_button_bound) else stringResource(R.string.home_button_dispel)
                            } else stringResource(R.string.home_button_cast),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            // QR Code scan button when not in focus mode
            if (!focusMode) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onScanQrCode) {
                    Text(stringResource(R.string.home_scan_qr_code))
                }
            }

            activeTagId?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.home_triggered_by_talisman, activeTagName ?: it))
            }

            if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.home_scan_to_dispel, boundTalismanName ?: ""), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }

            // NFC lock mode indicator
            if (focusMode && nfcLockMode && activeSchedule?.unbindingTalismanId == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.home_scan_talisman_to_dispel),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // QR Code scan button when in NFC lock mode (QR can dispel too)
            if (focusMode && nfcLockMode) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onScanQrCode) {
                    Text(stringResource(R.string.home_scan_qr_code))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellSelectorMultiDropdown(
    blockerLists: List<Blocker>,
    selectedBlockers: List<Blocker>,
    enabled: Boolean,
    onBlockerToggled: (Blocker) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedNames = selectedBlockers.map { it.name }.toSet()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (selectedBlockers.isEmpty()) stringResource(R.string.home_select_enchantment)
                    else selectedBlockers.joinToString(", ") { it.name },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (selectedBlockers.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            blockerLists.forEach { blocker ->
                val isSelected = blocker.name in selectedNames
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column {
                                Text(blocker.name)
                                Text(
                                    text = if (blocker.mode == BlockerMode.BLACKLIST) stringResource(R.string.label_banish) else stringResource(R.string.label_shield),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = { onBlockerToggled(blocker) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationSelectorDropdown(
    selectedDuration: Int,
    enabled: Boolean,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val durations = listOf(
        15 to stringResource(R.string.duration_15_min),
        25 to stringResource(R.string.duration_25_min),
        45 to stringResource(R.string.duration_45_min),
        60 to stringResource(R.string.duration_1_hour),
        120 to stringResource(R.string.duration_2_hours),
        0 to stringResource(R.string.duration_unlimited)
    )

    val selectedLabel = durations.find { it.first == selectedDuration }?.second ?: stringResource(R.string.duration_select)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.duration_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            durations.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDurationSelected(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PresetChipRow(
    presets: List<FocusPreset>,
    selectedPresetId: String?,
    onPresetSelected: (FocusPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = selectedPresetId == preset.id,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.name) },
                leadingIcon = if (selectedPresetId == preset.id) {
                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        // "Custom" chip shown when no preset matches
        FilterChip(
            selected = selectedPresetId == null,
            onClick = { /* Custom is selected by modifying any setting */ },
            label = { Text(stringResource(R.string.label_custom)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}
