package com.infinicada.focuspocus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (focusMode) {
            // ── ACTIVE / BREAK STATE ──
            ActiveSessionContent(
                isOnBreak = isOnBreak,
                activeBlockers = activeBlockers,
                activeSchedule = activeSchedule,
                focusDurationMinutes = focusDurationMinutes,
                focusTimeRemaining = focusTimeRemaining,
                breakTimeRemaining = breakTimeRemaining,
                breaksUsedThisSession = breaksUsedThisSession,
                maxBreaksPerSession = maxBreaksPerSession,
                breaksAllowed = breaksAllowed,
                hideStopButton = hideStopButton,
                nfcLockMode = nfcLockMode,
                emergencyBreakAvailable = emergencyBreakAvailable,
                emergencyBreakDaysRemaining = emergencyBreakDaysRemaining,
                activeTagId = activeTagId,
                activeTagName = activeTagName,
                boundTalismanName = boundTalismanName,
                onStartClicked = onStartClicked,
                onTakeBreak = onTakeBreak,
                onEndBreak = onEndBreak,
                onEmergencyStop = onEmergencyStop,
                onScanQrCode = onScanQrCode
            )
        } else {
            // ── IDLE STATE ──
            IdleContent(
                activeBlockers = activeBlockers,
                activeSchedule = activeSchedule,
                blockerLists = blockerLists,
                focusPresets = focusPresets,
                selectedPresetId = selectedPresetId,
                focusDurationMinutes = focusDurationMinutes,
                sessionBreaksEnabled = sessionBreaksEnabled,
                currentStreak = currentStreak,
                onPresetSelected = onPresetSelected,
                onBlockerToggled = onBlockerToggled,
                onDurationSelected = onDurationSelected,
                onSessionBreaksToggled = onSessionBreaksToggled,
                onStartClicked = onStartClicked,
                onScanQrCode = onScanQrCode
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  IDLE STATE
// ────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    activeBlockers: List<Blocker>,
    activeSchedule: Schedule?,
    blockerLists: List<Blocker>,
    focusPresets: List<FocusPreset>,
    selectedPresetId: String?,
    focusDurationMinutes: Int,
    sessionBreaksEnabled: Boolean,
    currentStreak: Int,
    onPresetSelected: (FocusPreset) -> Unit,
    onBlockerToggled: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onScanQrCode: () -> Unit
) {
    // Top: Status + streak
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_status_ready),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (currentStreak > 0) {
            StreakBadge(currentStreak)
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Hero Cast Button
    val canCast = activeBlockers.isNotEmpty()
    Button(
        onClick = onStartClicked,
        modifier = Modifier.size(160.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canCast) MaterialTheme.colorScheme.primary else Color.Gray
        ),
        enabled = canCast
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoFixHigh,
                contentDescription = stringResource(R.string.home_cast_content_desc),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_button_cast),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Quick Spell chips (above config card when presets exist)
    val validPresets = focusPresets.filter { preset ->
        preset.effectiveBlockerNames.all { name -> blockerLists.any { it.name == name } }
    }
    if (activeSchedule == null && validPresets.isNotEmpty()) {
        Text(
            text = stringResource(R.string.home_quick_spells),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        PresetChipRow(
            presets = validPresets,
            selectedPresetId = selectedPresetId,
            onPresetSelected = onPresetSelected,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
    }

    // Configuration card
    if (activeSchedule == null) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_session_setup),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                SpellSelectorMultiDropdown(
                    blockerLists = blockerLists,
                    selectedBlockers = activeBlockers,
                    enabled = true,
                    onBlockerToggled = onBlockerToggled,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                DurationSelectorDropdown(
                    selectedDuration = focusDurationMinutes,
                    enabled = true,
                    onDurationSelected = onDurationSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_allow_breaks),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = sessionBreaksEnabled,
                        onCheckedChange = onSessionBreaksToggled
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // QR scan button
    OutlinedButton(onClick = onScanQrCode) {
        Text(stringResource(R.string.home_scan_qr_code))
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ────────────────────────────────────────────────────────────
//  ACTIVE / BREAK STATE
// ────────────────────────────────────────────────────────────

@Composable
private fun ActiveSessionContent(
    isOnBreak: Boolean,
    activeBlockers: List<Blocker>,
    activeSchedule: Schedule?,
    focusDurationMinutes: Int,
    focusTimeRemaining: Int,
    breakTimeRemaining: Int,
    breaksUsedThisSession: Int,
    maxBreaksPerSession: Int,
    breaksAllowed: Boolean,
    hideStopButton: Boolean,
    nfcLockMode: Boolean,
    emergencyBreakAvailable: Boolean,
    emergencyBreakDaysRemaining: Int,
    activeTagId: String?,
    activeTagName: String?,
    boundTalismanName: String?,
    onStartClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onEmergencyStop: () -> Unit,
    onScanQrCode: () -> Unit
) {
    // Status header
    val statusColor by animateColorAsState(
        targetValue = when {
            isOnBreak -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "statusColor"
    )

    Text(
        text = when {
            isOnBreak -> stringResource(R.string.home_status_on_break)
            else -> stringResource(R.string.home_status_active)
        },
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        color = statusColor
    )

    if (activeBlockers.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = activeBlockers.joinToString(", ") { it.name },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // NFC Lock badge
    if (nfcLockMode) {
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.home_nfc_lock_active)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Large circular timer
    if (isOnBreak) {
        CircularTimer(
            timeRemaining = breakTimeRemaining,
            totalTime = 0, // break total not tracked, show countdown only
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            label = stringResource(R.string.home_break_remaining)
        )
    } else if (focusTimeRemaining > 0) {
        CircularTimer(
            timeRemaining = focusTimeRemaining,
            totalTime = focusDurationMinutes * 60,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            label = stringResource(R.string.home_remaining)
        )
    } else {
        // Unlimited session — pulsing indicator
        UnlimitedSessionIndicator()
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Active Schedule info card
    if (activeSchedule != null && activeBlockers.isNotEmpty()) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.home_ritual_name, activeSchedule.name),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (boundTalismanName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_unbind_with, boundTalismanName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Break info
    if (breaksAllowed) {
        Text(
            stringResource(R.string.home_breaks_used, breaksUsedThisSession, maxBreaksPerSession),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Break / End break button
    if (isOnBreak) {
        Button(
            onClick = onEndBreak,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.home_end_break_early))
        }
        Spacer(modifier = Modifier.height(16.dp))
    } else if (breaksAllowed && breaksUsedThisSession < maxBreaksPerSession) {
        FilledTonalButton(onClick = onTakeBreak) {
            val breaksRemaining = maxBreaksPerSession - breaksUsedThisSession
            Text(stringResource(R.string.home_take_break_with_count, breaksRemaining))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Emergency stop
    var showEmergencyConfirm by remember { mutableStateOf(false) }
    if (!isOnBreak && (!breaksAllowed || breaksUsedThisSession >= maxBreaksPerSession)) {
        if (emergencyBreakAvailable) {
            OutlinedButton(
                onClick = { showEmergencyConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.home_emergency_stop))
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

    // Dispel button
    val isButtonEnabled = activeSchedule == null || activeSchedule.unbindingTalismanId == null
    val shouldHideButton = nfcLockMode ||
        (hideStopButton && focusDurationMinutes > 0 &&
            !(activeSchedule != null && activeSchedule.unbindingTalismanId != null))

    if (!shouldHideButton && !isOnBreak) {
        Button(
            onClick = onStartClicked,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isButtonEnabled) MaterialTheme.colorScheme.error else Color.Gray
            ),
            enabled = isButtonEnabled
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = stringResource(R.string.home_dispel_content_desc),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null)
                        stringResource(R.string.home_button_bound)
                    else stringResource(R.string.home_button_dispel),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Talisman / NFC info
    activeTagId?.let {
        Text(
            text = stringResource(R.string.home_triggered_by_talisman, activeTagName ?: it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        Text(
            stringResource(R.string.home_scan_to_dispel, boundTalismanName ?: ""),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (nfcLockMode && activeSchedule?.unbindingTalismanId == null) {
        Text(
            stringResource(R.string.home_scan_talisman_to_dispel),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // QR scan in NFC lock mode
    if (nfcLockMode) {
        OutlinedButton(onClick = onScanQrCode) {
            Text(stringResource(R.string.home_scan_qr_code))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ────────────────────────────────────────────────────────────
//  CIRCULAR TIMER
// ────────────────────────────────────────────────────────────

@Composable
private fun CircularTimer(
    timeRemaining: Int,
    totalTime: Int,
    color: Color,
    trackColor: Color,
    label: String
) {
    val progress = if (totalTime > 0) {
        (timeRemaining.toFloat() / totalTime).coerceIn(0f, 1f)
    } else {
        // No total known (e.g. break) — just show the time without an arc
        -1f
    }

    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // Track circle
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 12.dp.toPx()
            drawCircle(
                color = trackColor,
                style = Stroke(width = strokeWidth)
            )
            if (progress >= 0f) {
                val sweepAngle = 360f * progress
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(
                        (size.width - size.minDimension) / 2,
                        (size.height - size.minDimension) / 2
                    ),
                    size = Size(size.minDimension, size.minDimension)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.displayLarge,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  UNLIMITED SESSION INDICATOR
// ────────────────────────────────────────────────────────────

@Composable
private fun UnlimitedSessionIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 12.dp.toPx()
            drawCircle(
                color = primaryColor.copy(alpha = alpha * 0.4f),
                style = Stroke(width = strokeWidth)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_unlimited_session),
                style = MaterialTheme.typography.headlineMedium,
                color = primaryColor.copy(alpha = alpha)
            )
            Text(
                text = stringResource(R.string.home_status_active),
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor.copy(alpha = 0.7f)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STREAK BADGE
// ────────────────────────────────────────────────────────────

@Composable
private fun StreakBadge(streak: Int) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                stringResource(R.string.home_day_streak, streak),
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    )
}

// ────────────────────────────────────────────────────────────
//  SPELL SELECTOR (unchanged logic, same API)
// ────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────
//  DURATION SELECTOR (unchanged logic, same API)
// ────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────
//  PRESET CHIP ROW (unchanged logic, same API)
// ────────────────────────────────────────────────────────────

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
