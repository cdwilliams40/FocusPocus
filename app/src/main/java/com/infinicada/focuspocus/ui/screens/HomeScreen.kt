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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.Schedule

@Composable
fun Greeting(
    focusMode: Boolean,
    activeTagId: String?,
    namedTags: List<NamedTag>,
    activeBlocker: Blocker?,
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
    onBlockerSelected: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onBlockerSelectorClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onEmergencyBreak: () -> Unit = {},
    onScanQrCode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTagName = namedTags.find { it.id == activeTagId }?.name
    val boundTalismanName = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        namedTags.find { it.id == activeSchedule.unbindingTalismanId }?.name ?: "Unknown Talisman"
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
                    isOnBreak -> "On Break"
                    focusMode -> "Focus Spell Active"
                    else -> "Ready to Cast"
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

            // Preset chip row (only when not in schedule, not in focus mode, and valid presets exist)
            val validPresets = focusPresets.filter { preset ->
                blockerLists.any { it.name == preset.blockerName }
            }
            if (activeSchedule == null && !focusMode && validPresets.isNotEmpty()) {
                Text(
                    text = "Quick Spells",
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
                SpellSelectorDropdown(
                    blockerLists = blockerLists,
                    selectedBlocker = activeBlocker,
                    enabled = !focusMode,
                    onBlockerSelected = onBlockerSelected,
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
                        text = "Allow Breaks",
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
                            text = "remaining",
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
                            text = "break remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Active Schedule Info (when controlled by schedule)
            if (activeSchedule != null && activeBlocker != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "Enchantment: ${activeBlocker.name}", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ritual: ${activeSchedule.name}", style = MaterialTheme.typography.bodyMedium)
                        if (boundTalismanName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Unbind with: $boundTalismanName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (focusMode && breaksAllowed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Breaks: $breaksUsedThisSession / $maxBreaksPerSession used",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Break info when in focus mode (manual mode)
            if (focusMode && activeSchedule == null && breaksAllowed) {
                Text(
                    "Breaks: $breaksUsedThisSession / $maxBreaksPerSession used",
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
                    Text("Take a Break", color = MaterialTheme.colorScheme.onTertiary)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // End break early button
            if (isOnBreak) {
                Button(
                    onClick = onEndBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("End Break Early")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Emergency break button
            var showEmergencyConfirm by remember { mutableStateOf(false) }
            if (focusMode && breaksAllowed && !isOnBreak && breaksUsedThisSession >= maxBreaksPerSession) {
                if (emergencyBreakAvailable) {
                    Button(
                        onClick = { showEmergencyConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Emergency Break", color = MaterialTheme.colorScheme.onError)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        "Emergency break available in $emergencyBreakDaysRemaining day${if (emergencyBreakDaysRemaining != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (showEmergencyConfirm) {
                AlertDialog(
                    onDismissRequest = { showEmergencyConfirm = false },
                    title = { Text("Emergency Break") },
                    text = { Text("This uses your emergency break. You won't get another for several weeks. Are you sure?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEmergencyConfirm = false
                                onEmergencyBreak()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Use Emergency Break")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showEmergencyConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Streak display
            if (currentStreak > 0) {
                Text(
                    "$currentStreak day streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big Start/Stop Button
            val buttonColor = if (focusMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val isButtonEnabled = activeSchedule == null || activeSchedule.unbindingTalismanId == null
            val canCast = activeBlocker != null

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
                            contentDescription = if (focusMode) "Dispel spell" else "Cast spell",
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (focusMode) {
                                if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) "Bound" else "Dispel"
                            } else "Cast",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            // QR Code scan button when not in focus mode
            if (!focusMode) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onScanQrCode) {
                    Text("Scan QR Code")
                }
            }

            activeTagId?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Triggered by Talisman: ${activeTagName ?: it}")
            }

            if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scan $boundTalismanName to dispel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }

            // NFC lock mode indicator
            if (focusMode && nfcLockMode && activeSchedule?.unbindingTalismanId == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Scan your talisman to dispel",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // QR Code scan button when in NFC lock mode (QR can dispel too)
            if (focusMode && nfcLockMode) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onScanQrCode) {
                    Text("Scan QR Code")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellSelectorDropdown(
    blockerLists: List<Blocker>,
    selectedBlocker: Blocker?,
    enabled: Boolean,
    onBlockerSelected: (Blocker) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedBlocker?.name ?: "Select Enchantment",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (selectedBlocker != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            blockerLists.forEach { blocker ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column {
                                Text(blocker.name)
                                Text(
                                    text = if (blocker.mode == BlockerMode.BLACKLIST) "Banish" else "Shield",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onBlockerSelected(blocker)
                        expanded = false
                    }
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
        15 to "15 minutes",
        25 to "25 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        120 to "2 hours",
        0 to "Unlimited"
    )

    val selectedLabel = durations.find { it.first == selectedDuration }?.second ?: "Select Duration"

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
            label = { Text("Duration") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
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
            label = { Text("Custom") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}
