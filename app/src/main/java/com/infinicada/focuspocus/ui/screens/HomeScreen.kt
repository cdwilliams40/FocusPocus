package com.infinicada.focuspocus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.ui.components.GlassCard
import com.infinicada.focuspocus.ui.components.TrialRow
import com.infinicada.focuspocus.ui.components.formatClock

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
    modifier: Modifier = Modifier,
    breakTotalSeconds: Int = 0,
    sessionElapsedSeconds: Long = 0L,
    breaksUsedThisSession: Int,
    maxBreaksPerSession: Int,
    breaksAllowed: Boolean,
    sessionBreaksEnabled: Boolean,
    hideStopButton: Boolean = false,
    nfcLockMode: Boolean = false,
    emergencyBreakAvailable: Boolean = false,
    emergencyBreakDaysRemaining: Int = 0,
    progressionEnabled: Boolean = false,
    trials: List<Trial> = emptyList(),
    canAffordExtraBreak: Boolean = false,
    onPresetSelected: (FocusPreset) -> Unit,
    onBlockerToggled: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onBlockerSelectorClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onEmergencyStop: () -> Unit = {},
    onClaimTrial: (Trial) -> Unit = {},
    onBuyExtraBreak: () -> Unit = {},
    onCreateEnchantment: () -> Unit = {},
    onActivateTalismanPreset: (FocusPreset) -> Unit = {},
    onActivateFocusTag: (NamedTag) -> Unit = {}
) {
    val activeTagName = namedTags.find { it.id == activeTagId }?.name
    val boundTalismanName = if (activeSchedule != null && activeSchedule.unbindingTalismanId != null) {
        namedTags.find { it.id == activeSchedule.unbindingTalismanId }?.name ?: stringResource(R.string.label_unknown_talisman)
    } else null

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                    breakTotalSeconds = breakTotalSeconds,
                    sessionElapsedSeconds = sessionElapsedSeconds,
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
                    progressionEnabled = progressionEnabled,
                    canAffordExtraBreak = canAffordExtraBreak,
                    onStartClicked = onStartClicked,
                    onTakeBreak = onTakeBreak,
                    onEndBreak = onEndBreak,
                    onEmergencyStop = onEmergencyStop,
                    onBuyExtraBreak = onBuyExtraBreak
                )
            } else {
                // ── IDLE STATE ──
                IdleContent(
                    activeBlockers = activeBlockers,
                    activeSchedule = activeSchedule,
                    blockerLists = blockerLists,
                    focusPresets = focusPresets,
                    namedTags = namedTags,
                    selectedPresetId = selectedPresetId,
                    focusDurationMinutes = focusDurationMinutes,
                    sessionBreaksEnabled = sessionBreaksEnabled,
                    progressionEnabled = progressionEnabled,
                    trials = trials,
                    onPresetSelected = onPresetSelected,
                    onBlockerToggled = onBlockerToggled,
                    onDurationSelected = onDurationSelected,
                    onSessionBreaksToggled = onSessionBreaksToggled,
                    onStartClicked = onStartClicked,
                    onClaimTrial = onClaimTrial,
                    onCreateEnchantment = onCreateEnchantment,
                    onActivateTalismanPreset = onActivateTalismanPreset,
                    onActivateFocusTag = onActivateFocusTag
                )
            }
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
    namedTags: List<NamedTag>,
    selectedPresetId: String?,
    focusDurationMinutes: Int,
    sessionBreaksEnabled: Boolean,
    progressionEnabled: Boolean,
    trials: List<Trial>,
    onPresetSelected: (FocusPreset) -> Unit,
    onBlockerToggled: (Blocker) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onSessionBreaksToggled: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onClaimTrial: (Trial) -> Unit,
    onCreateEnchantment: () -> Unit,
    onActivateTalismanPreset: (FocusPreset) -> Unit,
    onActivateFocusTag: (NamedTag) -> Unit
) {
    Text(
        text = stringResource(R.string.home_status_ready),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Hero Cast Button
    val canCast = activeBlockers.isNotEmpty()
    CastSpellButton(
        enabled = canCast,
        onClick = onStartClicked
    )
    if (!canCast) {
        Spacer(modifier = Modifier.height(8.dp))
        if (blockerLists.isEmpty()) {
            // Pact-first onboarding no longer creates an enchantment, so the
            // old "choose one below" hint would point at an empty dropdown.
            OutlinedButton(onClick = onCreateEnchantment) {
                Text(stringResource(R.string.home_create_enchantment))
            }
        } else {
            Text(
                text = stringResource(R.string.home_cast_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Talismans — every bound trigger can also be activated by hand, so a
    // missing phone-side NFC moment (tag at home, dead reader) never blocks
    // the ritual the talisman stands for.
    TalismansCard(
        namedTags = namedTags,
        focusPresets = focusPresets,
        blockerLists = blockerLists,
        onActivateTalismanPreset = onActivateTalismanPreset,
        onActivateFocusTag = onActivateFocusTag
    )

    // Today's trials — the daily carrot, right where the casting happens
    if (progressionEnabled && trials.isNotEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.home_trials_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(12.dp))
            trials.forEachIndexed { index, trial ->
                TrialRow(trial = trial, onClaim = onClaimTrial)
                if (index != trials.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ────────────────────────────────────────────────────────────
//  TALISMANS (activate by hand, no NFC tap needed)
// ────────────────────────────────────────────────────────────

/**
 * Lists every talisman and lets it be activated from the screen itself — the
 * exact action a physical NFC tap would trigger. A talisman bound to a Quick
 * Spell casts that spell (same [FocusPreset] toggle path as NFC and deep
 * links, including the ritual-lock gate); an unbound talisman starts a
 * talisman focus session with the currently selected enchantments. Only
 * activation is offered: dispelling stays on the session controls (or the
 * physical tag, where a lock demands it).
 */
@Composable
private fun TalismansCard(
    namedTags: List<NamedTag>,
    focusPresets: List<FocusPreset>,
    blockerLists: List<Blocker>,
    onActivateTalismanPreset: (FocusPreset) -> Unit,
    onActivateFocusTag: (NamedTag) -> Unit
) {
    // One row per talisman, resolved to the preset a physical tap would fire:
    // handleNfcTag takes the FIRST preset bound to a tag, so a tag carrying
    // stray extra bindings must not grow extra rows here. Bound spells appear
    // only while castable (same visibility rule as the Quick Spell chips); a
    // break spell stays visible but disabled, since it needs a session this
    // idle screen doesn't have.
    val boundRows = namedTags.mapNotNull { tag ->
        val preset = focusPresets.find { it.talismanId == tag.id } ?: return@mapNotNull null
        val castable = preset.effectiveBlockerNames.isNotEmpty() &&
            preset.effectiveBlockerNames.all { name -> blockerLists.any { it.name == name } }
        when (preset.action ?: PresetAction.TOGGLE) {
            PresetAction.TEMP_DISABLE -> Triple(tag, preset, false)
            else -> if (castable) Triple(tag, preset, true) else null
        }
    }
    val unboundTags = namedTags.filter { tag -> focusPresets.none { it.talismanId == tag.id } }
    if (boundRows.isEmpty() && unboundTags.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.talismans_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        boundRows.forEachIndexed { index, (tag, preset, enabled) ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            TalismanRow(
                name = tag.name,
                description = when (preset.action ?: PresetAction.TOGGLE) {
                    PresetAction.TEMP_ENABLE -> stringResource(
                        R.string.home_talisman_action_temp_enable,
                        preset.name,
                        preset.tempDurationMinutes ?: 30
                    )
                    PresetAction.TEMP_DISABLE -> stringResource(
                        R.string.home_talisman_action_temp_disable,
                        preset.tempDurationMinutes ?: 30
                    )
                    PresetAction.TOGGLE -> stringResource(
                        R.string.home_talisman_action_cast, preset.name
                    )
                },
                enabled = enabled,
                onActivate = { onActivateTalismanPreset(preset) }
            )
        }
        unboundTags.forEachIndexed { index, tag ->
            if (index > 0 || boundRows.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            TalismanRow(
                name = tag.name,
                description = stringResource(R.string.home_talisman_toggle_desc),
                enabled = true,
                onActivate = { onActivateFocusTag(tag) }
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun TalismanRow(
    name: String,
    description: String,
    enabled: Boolean,
    onActivate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Nfc,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Every row's button shows the same "Activate" label, so give each a
        // per-talisman accessible name — controls-mode TalkBack navigation
        // otherwise hears a run of indistinguishable "Activate" buttons.
        val activateDesc = stringResource(R.string.home_talisman_activate_desc, name)
        FilledTonalButton(
            onClick = onActivate,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = activateDesc }
        ) {
            Text(stringResource(R.string.home_talisman_activate))
        }
    }
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
    breakTotalSeconds: Int,
    sessionElapsedSeconds: Long,
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
    progressionEnabled: Boolean,
    canAffordExtraBreak: Boolean,
    onStartClicked: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onEmergencyStop: () -> Unit,
    onBuyExtraBreak: () -> Unit
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
            totalTime = breakTotalSeconds,
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
        // Unlimited session — pulsing ring with a live elapsed clock
        UnlimitedSessionIndicator(elapsedSeconds = sessionElapsedSeconds)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Active Schedule info card
    if (activeSchedule != null && activeBlockers.isNotEmpty()) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
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

    // Extra-break perk: when breaks are spent, mana can buy one more.
    // (A successful purchase raises the effective max, so the take-break
    // button above reappears on the next recomposition.)
    if (progressionEnabled && canAffordExtraBreak && !isOnBreak &&
        breaksAllowed && breaksUsedThisSession >= maxBreaksPerSession
    ) {
        FilledTonalButton(onClick = onBuyExtraBreak) {
            Text(stringResource(R.string.home_extra_break_button, Perk.EXTRA_BREAK.costMana))
        }
        Spacer(modifier = Modifier.height(12.dp))
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
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
}

// ────────────────────────────────────────────────────────────
//  CAST SPELL BUTTON
// ────────────────────────────────────────────────────────────

@Composable
private fun CastSpellButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val gold = scheme.tertiary
    val infiniteTransition = rememberInfiniteTransition(label = "castGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    // A faint golden spark slowly orbiting the button rim
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(216.dp)
    ) {
        if (enabled) {
            // Soft magical aura behind the button while a cast is possible
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f * glowScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.38f),
                            primary.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
            }
            // Orbiting golden ring
            Canvas(modifier = Modifier.size(184.dp)) {
                rotate(degrees = orbitAngle) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            0.9f to gold.copy(alpha = 0.9f),
                            1f to Color.Transparent
                        ),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            enabled = enabled,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = scheme.onPrimary,
                disabledContainerColor = scheme.surfaceContainerHighest,
                disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp
            )
        ) {
            // Gradient fill drawn inside the circular button so the orb reads
            // as a lit crystal rather than a flat disc.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (enabled) {
                            // Stop the blend well short of primaryContainer: the
                            // label sits low on the orb, and a full blend leaves
                            // onPrimary text below 3:1 contrast in both themes.
                            Brush.verticalGradient(
                                listOf(primary, lerp(primary, scheme.primaryContainer, 0.35f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHighest)
                            )
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = stringResource(R.string.home_cast_content_desc),
                        tint = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_button_cast),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
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
        // No total known — just show the time without an arc
        -1f
    }

    // Long sessions read as h:mm:ss instead of an ever-growing minute count.
    val clockText = formatClock(timeRemaining)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(230.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 14.dp.toPx()

            // Soft inner glow so the dial feels lit from within
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.16f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f
            )

            drawCircle(
                color = trackColor,
                style = Stroke(width = strokeWidth)
            )
            if (progress >= 0f) {
                val sweepAngle = 360f * progress
                val headStop = progress.coerceIn(0.01f, 1f)
                // Rotate so the sweep gradient starts at 12 o'clock with the arc
                rotate(degrees = -90f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to color.copy(alpha = 0.25f),
                            headStop to color,
                            1f to color.copy(alpha = 0.25f)
                        ),
                        startAngle = 0f,
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

                // Glowing bead at the head of the arc
                val angleRad = (sweepAngle - 90f) * (PI.toFloat() / 180f)
                val radius = size.minDimension / 2f
                val head = Offset(
                    center.x + radius * cos(angleRad),
                    center.y + radius * sin(angleRad)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                        center = head,
                        radius = strokeWidth * 1.4f
                    ),
                    radius = strokeWidth * 1.4f,
                    center = head
                )
                drawCircle(color = color, radius = strokeWidth / 2f, center = head)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clockText,
                style = if (clockText.length > 5) MaterialTheme.typography.displayMedium
                        else MaterialTheme.typography.displayLarge,
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
private fun UnlimitedSessionIndicator(elapsedSeconds: Long = 0L) {
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
        modifier = Modifier.size(230.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 14.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.14f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = primaryColor.copy(alpha = alpha * 0.4f),
                style = Stroke(width = strokeWidth)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (elapsedSeconds > 0) {
                val clockText = formatClock(elapsedSeconds.toInt().coerceAtLeast(0))
                Text(
                    text = clockText,
                    style = if (clockText.length > 5) MaterialTheme.typography.displayMedium
                            else MaterialTheme.typography.displayLarge,
                    color = primaryColor
                )
                Text(
                    text = stringResource(R.string.home_elapsed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryColor.copy(alpha = 0.7f)
                )
            } else {
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
