package com.infinicada.focuspocus.ui.screens

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.ui.ScheduleLabels
import com.infinicada.focuspocus.ui.currentUiLocale
import com.infinicada.focuspocus.ui.formatClockTime
import java.util.UUID

@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit,
    onCreateClick: () -> Unit,
    onDeleteSchedule: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    activeScheduleId: String? = null,
) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rituals_create_content_desc))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.rituals_title), style = MaterialTheme.typography.headlineMedium)
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
                                            stringResource(R.string.label_active),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                val isOvernight = run {
                                    val startParts = schedule.effectiveStartTime.split(":")
                                    val endParts = schedule.effectiveEndTime.split(":")
                                    if (startParts.size == 2 && endParts.size == 2) {
                                        val startMins = startParts[0].toIntOrNull()?.times(60)?.plus(startParts[1].toIntOrNull() ?: 0) ?: 0
                                        val endMins = endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0) ?: 0
                                        endMins <= startMins
                                    } else false
                                }
                                val startText = formatClockTime(schedule.effectiveStartTime)
                                val endText = formatClockTime(schedule.effectiveEndTime)
                                Text(
                                    if (isOvernight) "$startText - $endText ${stringResource(R.string.rituals_overnight_suffix)}"
                                    else "$startText - $endText"
                                )
                                Text(ScheduleLabels.shortSummary(schedule.effectiveDays, currentUiLocale()))
                                if (schedule.unbindingTalismanId != null) {
                                    Text(stringResource(R.string.rituals_bound_to_talisman), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (isActive) {
                                    Text(
                                        stringResource(R.string.rituals_cannot_edit_active),
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
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // space for FAB
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    blockerLists: List<Blocker>,
    namedTags: List<NamedTag>,
    onSaveSchedule: (Schedule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    scheduleToEdit: Schedule? = null,
    existingSchedules: List<Schedule> = emptyList()
) {
    var name by remember { mutableStateOf(scheduleToEdit?.name ?: "") }
    var selectedBlockerNames by remember {
        mutableStateOf(
            scheduleToEdit?.effectiveBlockerNames?.toSet() ?: emptySet()
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
    var breakDurationMinutes by remember { mutableIntStateOf((scheduleToEdit?.breakDurationMinutes ?: 5).coerceIn(1, 30)) }
    var maxBreaksPerSession by remember { mutableIntStateOf((scheduleToEdit?.maxBreaksPerSession ?: 3).coerceIn(1, 10)) }

    var selectedDays by remember { mutableStateOf(scheduleToEdit?.effectiveDays ?: emptySet()) }

    // Initial times
    val (startHour, startMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.effectiveStartTime?.split(":")
         if (parts != null && parts.size >= 2) {
             (parts[0].toIntOrNull() ?: 9) to (parts[1].toIntOrNull() ?: 0)
         } else {
             9 to 0
         }
    }
    val startTimeState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)

    val (endHour, endMinute) = remember(scheduleToEdit) {
         val parts = scheduleToEdit?.effectiveEndTime?.split(":")
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
    // timeValidationError removed: stringResource used inline below

    // Check for schedule conflicts with existing schedules
    val conflictingSchedule = remember(selectedDays, startTimeMinutes, endTimeMinutes, existingSchedules) {
        existingSchedules.filter { it.id != (scheduleToEdit?.id ?: "") }.find { existing ->
            // Check if any days overlap
            val daysOverlap = selectedDays.any { it in existing.effectiveDays }
            if (!daysOverlap) return@find false

            // Parse existing schedule times
            val existParts = existing.effectiveStartTime.split(":")
            val existEndParts = existing.effectiveEndTime.split(":")
            if (existParts.size != 2 || existEndParts.size != 2) return@find false
            val existStart = (existParts[0].toIntOrNull() ?: 0) * 60 + (existParts[1].toIntOrNull() ?: 0)
            val existEnd = (existEndParts[0].toIntOrNull() ?: 0) * 60 + (existEndParts[1].toIntOrNull() ?: 0)

            // Check time overlap (simplified - same-day only for now)
            if (endTimeMinutes > startTimeMinutes && existEnd > existStart) {
                // Both same-day: ranges overlap if one starts before other ends
                startTimeMinutes < existEnd && endTimeMinutes > existStart
            } else {
                // Overnight schedules involved - just warn if any time overlap possible
                true
            }
        }
    }

    if (showBlockerDialog) {
        AlertDialog(
            onDismissRequest = { showBlockerDialog = false },
            title = { Text(stringResource(R.string.rituals_select_enchantment)) },
            text = {
                LazyColumn {
                    items(blockerLists) { blocker ->
                        val isSelected = blocker.name in selectedBlockerNames
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBlockerNames = if (isSelected) {
                                        selectedBlockerNames - blocker.name
                                    } else {
                                        selectedBlockerNames + blocker.name
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedBlockerNames = if (isSelected) {
                                        selectedBlockerNames - blocker.name
                                    } else {
                                        selectedBlockerNames + blocker.name
                                    }
                                }
                            )
                            Text(blocker.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBlockerDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    if (showTalismanDialog) {
        AlertDialog(
            onDismissRequest = { showTalismanDialog = false },
            title = { Text(stringResource(R.string.rituals_select_unbinding_talisman)) },
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
                            Text(stringResource(R.string.rituals_none_unbind), modifier = Modifier.padding(start = 40.dp))
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
                Button(onClick = { showStartTimePicker = false }) { Text(stringResource(R.string.action_ok)) }
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
                Button(onClick = { showEndTimePicker = false }) { Text(stringResource(R.string.action_ok)) }
            },
            text = {
                TimePicker(state = endTimeState)
            }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(if (scheduleToEdit != null) stringResource(R.string.rituals_refine) else stringResource(R.string.rituals_concoct), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.rituals_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { showBlockerDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (selectedBlockerNames.isEmpty()) stringResource(R.string.rituals_select_enchantment)
                else selectedBlockerNames.joinToString(", ")
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { showTalismanDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedTalisman?.name?.let { stringResource(R.string.rituals_unbind_with, it) } ?: stringResource(R.string.rituals_bind_optional))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Breaks toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.rituals_allow_breaks))
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
                    Text(pluralStringResource(R.plurals.rituals_break_duration, breakDurationMinutes, breakDurationMinutes), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = breakDurationMinutes.toFloat(),
                        onValueChange = { breakDurationMinutes = it.roundToInt() },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(stringResource(R.string.rituals_breaks_per_session, maxBreaksPerSession), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = maxBreaksPerSession.toFloat(),
                        onValueChange = { maxBreaksPerSession = it.roundToInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.rituals_days_active))
        val locale = currentUiLocale()
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            ScheduleLabels.daysInWeekOrder(locale).forEach { day ->
                val dayFullName = ScheduleLabels.full(day, locale)
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = {
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    },
                    label = { Text(ScheduleLabels.narrow(day, locale)) },
                    // The narrow label is ambiguous by design (T/T, S/S in
                    // English), so the chip announces the day in full.
                    modifier = Modifier.semantics { contentDescription = dayFullName }
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
                Text(text = stringResource(R.string.rituals_start_time, formatClockTime(startTimeState.hour, startTimeState.minute)))
            }

            Spacer(modifier = Modifier.size(8.dp))

            // End Time Picker Button
            OutlinedButton(
                onClick = { showEndTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                val endTimeText = if (isOvernightSchedule) {
                    stringResource(R.string.rituals_end_time_next_day, formatClockTime(endTimeState.hour, endTimeState.minute))
                } else {
                    stringResource(R.string.rituals_end_time, formatClockTime(endTimeState.hour, endTimeState.minute))
                }
                Text(text = endTimeText)
            }
        }

        // Show time validation error
        if (!isTimeValid) {
            Text(
                text = stringResource(R.string.rituals_time_same_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (conflictingSchedule != null) {
            Text(
                text = stringResource(R.string.rituals_conflict_warning, conflictingSchedule.name),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (isOvernightSchedule && isTimeValid) {
            Text(
                text = stringResource(R.string.rituals_overnight_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = {
                onSaveSchedule(
                    Schedule(
                        id = scheduleToEdit?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        blockerNames = selectedBlockerNames.toList(),
                        days = selectedDays,
                        startTime = ScheduleLabels.storedTime(startTimeState.hour, startTimeState.minute),
                        endTime = ScheduleLabels.storedTime(endTimeState.hour, endTimeState.minute),
                        unbindingTalismanId = selectedTalisman?.id,
                        breaksEnabled = breaksEnabled,
                        breakDurationMinutes = breakDurationMinutes,
                        maxBreaksPerSession = maxBreaksPerSession
                    )
                )
            }, enabled = name.isNotBlank() && selectedBlockerNames.isNotEmpty() && selectedDays.isNotEmpty() && isTimeValid) {
                Text(stringResource(R.string.rituals_save))
            }
        }
    }
}
