package com.infinicada.focuspocus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.infinicada.focuspocus.NetworkUtils
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.infinicada.focuspocus.AutoTrigger
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.DayOfWeek
import com.infinicada.focuspocus.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.Schedule
import com.infinicada.focuspocus.TriggerType
import java.util.UUID

@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit,
    onCreateClick: () -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    activeScheduleId: String? = null,
    autoTriggers: List<AutoTrigger> = emptyList(),
    focusPresets: List<FocusPreset> = emptyList(),
    onSaveAutoTrigger: (AutoTrigger) -> Unit = {},
    onDeleteAutoTrigger: (AutoTrigger) -> Unit = {}
) {
    var showAddWifiTriggerDialog by remember { mutableStateOf(false) }
    var showAddBtTriggerDialog by remember { mutableStateOf(false) }

    if (showAddWifiTriggerDialog) {
        AddWifiTriggerDialog(
            focusPresets = focusPresets,
            onSave = { trigger ->
                onSaveAutoTrigger(trigger)
                showAddWifiTriggerDialog = false
            },
            onDismiss = { showAddWifiTriggerDialog = false }
        )
    }

    if (showAddBtTriggerDialog) {
        AddBluetoothTriggerDialog(
            focusPresets = focusPresets,
            onSave = { trigger ->
                onSaveAutoTrigger(trigger)
                showAddBtTriggerDialog = false
            },
            onDismiss = { showAddBtTriggerDialog = false }
        )
    }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create new schedule")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text("Scheduled Rituals", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(schedules) { schedule ->
                    val isActive = schedule.id == activeScheduleId
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .then(
                                if (isActive) Modifier else Modifier.clickable { onScheduleClick(schedule) }
                            ),
                        colors = if (isActive) {
                            CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.elevatedCardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                                    if (isActive) {
                                        Text(
                                            "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                val isOvernight = run {
                                    val startParts = schedule.startTime.split(":")
                                    val endParts = schedule.endTime.split(":")
                                    if (startParts.size == 2 && endParts.size == 2) {
                                        val startMins = startParts[0].toIntOrNull()?.times(60)?.plus(startParts[1].toIntOrNull() ?: 0) ?: 0
                                        val endMins = endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0) ?: 0
                                        endMins <= startMins
                                    } else false
                                }
                                Text(
                                    if (isOvernight) "${schedule.startTime} - ${schedule.endTime} (overnight)"
                                    else "${schedule.startTime} - ${schedule.endTime}"
                                )
                                Text(schedule.days.joinToString { it.name.take(3) })
                                if (schedule.unbindingTalismanId != null) {
                                    Text("Bound to Talisman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (isActive) {
                                    Text(
                                        "Cannot edit while active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Button(
                                onClick = { onDeleteSchedule(schedule) },
                                enabled = !isActive,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }

            // Auto Triggers Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Auto Triggers", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Automatically activate focus when connecting to specific Wi-Fi networks or Bluetooth devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (autoTriggers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No auto triggers yet. Add one to automatically start focus when you connect to a Wi-Fi network or Bluetooth device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(autoTriggers) { trigger ->
                val preset = focusPresets.find { it.id == trigger.presetId }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (trigger.type == TriggerType.WIFI) Icons.Default.Wifi else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trigger.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (trigger.type) {
                                    TriggerType.WIFI -> "Wi-Fi: ${trigger.identifier}"
                                    TriggerType.BLUETOOTH -> "BT: ${trigger.deviceName ?: trigger.identifier}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Preset: ${preset?.name ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Switch(
                            checked = trigger.enabled,
                            onCheckedChange = { enabled ->
                                onSaveAutoTrigger(trigger.copy(enabled = enabled))
                            }
                        )
                        IconButton(onClick = { onDeleteAutoTrigger(trigger) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete trigger",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddWifiTriggerDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = focusPresets.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Wi-Fi Trigger")
                    }
                    OutlinedButton(
                        onClick = { showAddBtTriggerDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = focusPresets.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("BT Trigger")
                    }
                }
                Spacer(modifier = Modifier.height(80.dp)) // space for FAB
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWifiTriggerDialog(
    focusPresets: List<FocusPreset>,
    onSave: (AutoTrigger) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var ssid by remember {
        val currentSsid = NetworkUtils.getLegacyWifiSsid(context) ?: ""
        mutableStateOf(currentSsid)
    }
    var selectedPreset by remember { mutableStateOf(focusPresets.firstOrNull()) }
    var presetDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Wi-Fi Trigger") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text("Trigger Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = ssid,
                    onValueChange = { if (it.length <= 100) ssid = it },
                    label = { Text("Wi-Fi Network Name (SSID)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = presetDropdownExpanded,
                    onExpandedChange = { presetDropdownExpanded = !presetDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPreset?.name ?: "Select Preset",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quick Spell") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = presetDropdownExpanded,
                        onDismissRequest = { presetDropdownExpanded = false }
                    ) {
                        focusPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    selectedPreset = preset
                                    presetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedPreset?.let { preset ->
                        onSave(
                            AutoTrigger(
                                name = name.trim(),
                                type = TriggerType.WIFI,
                                identifier = ssid.trim(),
                                presetId = preset.id
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && ssid.isNotBlank() && selectedPreset != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBluetoothTriggerDialog(
    focusPresets: List<FocusPreset>,
    onSave: (AutoTrigger) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(focusPresets.firstOrNull()) }
    var presetDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<Pair<String, String>?>(null) } // address to name
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

    val pairedDevices = remember(hasBtPermission) {
        if (!hasBtPermission) return@remember emptyList()
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter ?: return@remember emptyList()
            @Suppress("MissingPermission")
            adapter.bondedDevices?.map { device ->
                @Suppress("MissingPermission")
                (device.address ?: "") to (device.name ?: device.address ?: "Unknown")
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bluetooth Trigger") },
        text = {
            Column {
                if (!hasBtPermission) {
                    Text(
                        "Bluetooth permission required. Please grant it in app settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                TextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text("Trigger Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Paired device selector
                ExposedDropdownMenuBox(
                    expanded = deviceDropdownExpanded,
                    onExpandedChange = { deviceDropdownExpanded = !deviceDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDevice?.second ?: "Select Device",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Bluetooth Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = deviceDropdownExpanded,
                        onDismissRequest = { deviceDropdownExpanded = false }
                    ) {
                        if (pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired devices found") },
                                onClick = { deviceDropdownExpanded = false }
                            )
                        }
                        pairedDevices.forEach { (address, deviceName) ->
                            DropdownMenuItem(
                                text = { Text(deviceName) },
                                onClick = {
                                    selectedDevice = address to deviceName
                                    deviceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = presetDropdownExpanded,
                    onExpandedChange = { presetDropdownExpanded = !presetDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPreset?.name ?: "Select Preset",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quick Spell") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = presetDropdownExpanded,
                        onDismissRequest = { presetDropdownExpanded = false }
                    ) {
                        focusPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    selectedPreset = preset
                                    presetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val device = selectedDevice
                    val preset = selectedPreset
                    if (device != null && preset != null) {
                        onSave(
                            AutoTrigger(
                                name = name.trim(),
                                type = TriggerType.BLUETOOTH,
                                identifier = device.first,
                                deviceName = device.second,
                                presetId = preset.id
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedDevice != null && selectedPreset != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onSaveSchedule: (Schedule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    scheduleToEdit: Schedule? = null
) {
    var name by remember { mutableStateOf(scheduleToEdit?.name ?: "") }
    var selectedBlocker by remember {
        mutableStateOf(
            if (scheduleToEdit != null) blockerLists.find { it.name == scheduleToEdit.blockerName } else null
        )
    }
    var showBlockerDialog by remember { mutableStateOf(false) }

    var selectedTalisman by remember {
        mutableStateOf(
             if (scheduleToEdit?.unbindingTalismanId != null) namedTags.find { it.id == scheduleToEdit.unbindingTalismanId } else null
        )
    }
    var showTalismanDialog by remember { mutableStateOf(false) }

    var breaksEnabled by remember { mutableStateOf(scheduleToEdit?.breaksEnabled ?: true) }
    var breakDurationMinutes by remember { mutableIntStateOf(scheduleToEdit?.breakDurationMinutes ?: 5) }
    var maxBreaksPerSession by remember { mutableIntStateOf(scheduleToEdit?.maxBreaksPerSession ?: 3) }

    var selectedDays by remember { mutableStateOf(scheduleToEdit?.days ?: emptySet()) }

    // Initial times
    val (startHour, startMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.startTime?.split(":")
         if (parts != null && parts.size >= 2) {
             (parts[0].toIntOrNull() ?: 9) to (parts[1].toIntOrNull() ?: 0)
         } else {
             9 to 0
         }
    }
    val startTimeState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)

    val (endHour, endMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.endTime?.split(":")
         if (parts != null && parts.size >= 2) {
             (parts[0].toIntOrNull() ?: 17) to (parts[1].toIntOrNull() ?: 0)
         } else {
             17 to 0
         }
    }
    val endTimeState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // Validate times - allow overnight schedules (end time before start time)
    val startTimeMinutes = startTimeState.hour * 60 + startTimeState.minute
    val endTimeMinutes = endTimeState.hour * 60 + endTimeState.minute
    val isOvernightSchedule = endTimeMinutes <= startTimeMinutes
    val isTimeValid = startTimeMinutes != endTimeMinutes  // Only invalid if times are identical
    val timeValidationError = if (!isTimeValid) "Start and end times cannot be the same" else null

    if (showBlockerDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = {
                selectedBlocker = it
                showBlockerDialog = false
             },
            onDismissRequest = { showBlockerDialog = false }
        )
    }

    if (showTalismanDialog) {
        AlertDialog(
            onDismissRequest = { showTalismanDialog = false },
            title = { Text("Select Unbinding Talisman") },
            text = {
                LazyColumn {
                    items(namedTags) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTalisman = tag
                                    showTalismanDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(tag.name, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTalisman = null
                                    showTalismanDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("None (Unbind Freely)", modifier = Modifier.padding(start = 40.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                Button(onClick = { showStartTimePicker = false }) { Text("OK") }
            },
            text = {
                TimePicker(state = startTimeState)
            }
        )
    }

    if (showEndTimePicker) {
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                Button(onClick = { showEndTimePicker = false }) { Text("OK") }
            },
            text = {
                TimePicker(state = endTimeState)
            }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(if (scheduleToEdit != null) "Refine Ritual" else "Concoct Ritual", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Ritual Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { showBlockerDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedBlocker?.name ?: "Select Enchantment")
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { showTalismanDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedTalisman?.name?.let { "Unbind with: $it" } ?: "Optional: Bind to Talisman")
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

        // Break settings (only shown when breaks are enabled)
        if (breaksEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Break Duration: $breakDurationMinutes minutes", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = breakDurationMinutes.toFloat(),
                        onValueChange = { breakDurationMinutes = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Breaks Per Session: $maxBreaksPerSession", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = maxBreaksPerSession.toFloat(),
                        onValueChange = { maxBreaksPerSession = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Days Active:")
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = {
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    },
                    label = { Text(day.name.take(1)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Start Time Picker Button
            OutlinedButton(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Start: %02d:%02d".format(startTimeState.hour, startTimeState.minute))
            }

            Spacer(modifier = Modifier.size(8.dp))

            // End Time Picker Button
            OutlinedButton(
                onClick = { showEndTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                val endTimeText = if (isOvernightSchedule) {
                    "End: %02d:%02d (next day)".format(endTimeState.hour, endTimeState.minute)
                } else {
                    "End: %02d:%02d".format(endTimeState.hour, endTimeState.minute)
                }
                Text(text = endTimeText)
            }
        }

        // Show time validation error
        if (timeValidationError != null) {
            Text(
                text = timeValidationError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = {
                selectedBlocker?.let {
                    onSaveSchedule(
                        Schedule(
                            id = scheduleToEdit?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            blockerName = it.name,
                            days = selectedDays,
                            startTime = "%02d:%02d".format(startTimeState.hour, startTimeState.minute),
                            endTime = "%02d:%02d".format(endTimeState.hour, endTimeState.minute),
                            unbindingTalismanId = selectedTalisman?.id,
                            breaksEnabled = breaksEnabled,
                            breakDurationMinutes = breakDurationMinutes,
                            maxBreaksPerSession = maxBreaksPerSession
                        )
                    )
                }
            }, enabled = name.isNotBlank() && selectedBlocker != null && selectedDays.isNotEmpty() && isTimeValid) {
                Text("Save Ritual")
            }
        }
    }
}
