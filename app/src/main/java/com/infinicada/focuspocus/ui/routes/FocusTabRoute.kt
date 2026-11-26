package com.infinicada.focuspocus.ui.routes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.ui.screens.BlockerSelectionDialog
import com.infinicada.focuspocus.ui.screens.FocusScreen
import com.infinicada.focuspocus.viewmodel.ProgressionViewModel
import com.infinicada.focuspocus.viewmodel.SessionViewModel
import com.infinicada.focuspocus.viewmodel.SettingsViewModel
import com.infinicada.focuspocus.viewmodel.SpellbookViewModel

@Composable
fun FocusTabRoute(
    sessionVM: SessionViewModel,
    spellbookVM: SpellbookViewModel,
    settingsVM: SettingsViewModel,
    progressionVM: ProgressionViewModel,
    onCreateEnchantment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val focusMode by sessionVM.manualFocusMode.collectAsStateWithLifecycle()
    val activeBlockerNames by sessionVM.activeBlockerNames.collectAsStateWithLifecycle()
    val activeScheduleId by sessionVM.activeScheduleId.collectAsStateWithLifecycle()
    val focusDurationMinutes by sessionVM.focusDurationMinutes.collectAsStateWithLifecycle()
    val focusTimeRemaining by sessionVM.focusTimeRemaining.collectAsStateWithLifecycle()
    val sessionBreaksEnabled by sessionVM.sessionBreaksEnabled.collectAsStateWithLifecycle()
    val manualBlockerNames by sessionVM.manualBlockerNames.collectAsStateWithLifecycle()
    val manualDurationMinutes by sessionVM.manualDurationMinutes.collectAsStateWithLifecycle()
    val manualBreaksEnabled by sessionVM.manualBreaksEnabled.collectAsStateWithLifecycle()
    val isOnBreak by sessionVM.isOnBreak.collectAsStateWithLifecycle()
    val breaksUsedThisSession by sessionVM.breaksUsedThisSession.collectAsStateWithLifecycle()
    val breakTimeRemaining by sessionVM.breakTimeRemaining.collectAsStateWithLifecycle()
    val lastEmergencyBreakMillis by sessionVM.lastEmergencyBreakMillis.collectAsStateWithLifecycle()
    val activeTalismanId by sessionVM.activeTalismanId.collectAsStateWithLifecycle()
    val sessionElapsedSeconds by sessionVM.sessionElapsedSeconds.collectAsStateWithLifecycle()

    val blockerLists by spellbookVM.blockerLists.collectAsStateWithLifecycle()
    val schedules by spellbookVM.schedules.collectAsStateWithLifecycle()
    val talismans by spellbookVM.talismans.collectAsStateWithLifecycle()

    val breakDurationMinutes by settingsVM.breakDurationMinutes.collectAsStateWithLifecycle()
    val maxBreaksPerSession by settingsVM.maxBreaksPerSession.collectAsStateWithLifecycle()
    val emergencyBreakCadenceWeeks by settingsVM.emergencyBreakCadenceWeeks.collectAsStateWithLifecycle()
    val hideStopButton by settingsVM.hideStopButton.collectAsStateWithLifecycle()
    val nfcLockMode by settingsVM.nfcLockMode.collectAsStateWithLifecycle()
    val progressionEnabled by settingsVM.progressionEnabled.collectAsStateWithLifecycle()

    val manaBalance by progressionVM.balance.collectAsStateWithLifecycle()
    val extraBreakTokens by progressionVM.extraBreakTokens.collectAsStateWithLifecycle()

    var showBlockerSelectionDialog by remember { mutableStateOf(false) }

    val activeManualBlockers = remember(activeBlockerNames, blockerLists) {
        blockerLists.filter { it.name in activeBlockerNames }
    }
    val selectedManualBlockers = remember(manualBlockerNames, blockerLists) {
        blockerLists.filter { it.name in manualBlockerNames }
    }
    val activeSchedule = remember(activeScheduleId, schedules) {
        schedules.find { it.id == activeScheduleId }
    }

    if (showBlockerSelectionDialog) {
        BlockerSelectionDialog(
            blockerLists = blockerLists,
            onBlockerSelected = { blocker ->
                sessionVM.toggleBlocker(blocker)
                showBlockerSelectionDialog = false
            },
            onDismissRequest = { showBlockerSelectionDialog = false }
        )
    }

    val breaksAllowed = activeSchedule?.breaksEnabled ?: sessionBreaksEnabled
    val effectiveBreakDuration = activeSchedule?.breakDurationMinutes?.coerceAtLeast(1) ?: breakDurationMinutes
    val effectiveMaxBreaks = (activeSchedule?.maxBreaksPerSession?.coerceAtLeast(1) ?: maxBreaksPerSession) +
        extraBreakTokens
    val now = System.currentTimeMillis()
    val cadenceMillis = emergencyBreakCadenceWeeks * 7L * 24 * 60 * 60 * 1000
    val emergencyBreakAvailable = now >= lastEmergencyBreakMillis + cadenceMillis
    val emergencyBreakDaysRemaining = if (!emergencyBreakAvailable) {
        val nextAvailable = lastEmergencyBreakMillis + cadenceMillis
        ((nextAvailable - now) / (24 * 60 * 60 * 1000) + 1).toInt()
    } else 0

    FocusScreen(
        focusMode = focusMode,
        activeTalismanId = activeTalismanId,
        talismans = talismans,
        activeBlockers = if (focusMode) activeManualBlockers else selectedManualBlockers,
        activeSchedule = activeSchedule,
        blockerLists = blockerLists,
        focusDurationMinutes = if (focusMode) focusDurationMinutes else manualDurationMinutes,
        focusTimeRemaining = focusTimeRemaining,
        isOnBreak = isOnBreak,
        breakTimeRemaining = breakTimeRemaining,
        breakTotalSeconds = effectiveBreakDuration * 60,
        sessionElapsedSeconds = sessionElapsedSeconds,
        breaksUsedThisSession = breaksUsedThisSession,
        maxBreaksPerSession = effectiveMaxBreaks,
        breaksAllowed = breaksAllowed,
        sessionBreaksEnabled = if (focusMode) sessionBreaksEnabled else manualBreaksEnabled,
        hideStopButton = hideStopButton,
        nfcLockMode = nfcLockMode,
        emergencyBreakAvailable = emergencyBreakAvailable,
        emergencyBreakDaysRemaining = emergencyBreakDaysRemaining,
        progressionEnabled = progressionEnabled,
        canAffordExtraBreak = manaBalance >= Perk.EXTRA_BREAK.costMana,
        onBlockerToggled = { blocker -> sessionVM.toggleBlocker(blocker) },
        onDurationSelected = { duration -> sessionVM.selectDuration(duration) },
        onSessionBreaksToggled = { enabled -> sessionVM.toggleSessionBreaks(enabled) },
        onStartClicked = {
            if (focusMode) {
                if (activeSchedule != null) {
                    if (activeSchedule.unbindingTalismanId == null) {
                        sessionVM.dispelSchedule()
                        progressionVM.refresh()
                    }
                } else if (talismans.none { it.id == activeTalismanId && it.locksSession }) {
                    sessionVM.stopSession()
                    progressionVM.refresh()
                }
            } else {
                if (selectedManualBlockers.isNotEmpty()) {
                    sessionVM.startSession(selectedManualBlockers.map { it.name })
                } else if (blockerLists.isNotEmpty()) {
                    showBlockerSelectionDialog = true
                }
            }
        },
        onBlockerSelectorClicked = {
            if (activeSchedule == null) {
                showBlockerSelectionDialog = true
            }
        },
        onTakeBreak = {
            if (breaksAllowed && breaksUsedThisSession < effectiveMaxBreaks && !isOnBreak) {
                sessionVM.takeBreak(effectiveBreakDuration)
            }
        },
        onEndBreak = { sessionVM.endBreak() },
        onEmergencyStop = {
            sessionVM.emergencyStop()
            progressionVM.refresh()
            Toast.makeText(context, resources.getString(R.string.toast_emergency_stop), Toast.LENGTH_SHORT).show()
        },
        onBuyExtraBreak = { progressionVM.redeemPerk(Perk.EXTRA_BREAK) },
        onCreateEnchantment = onCreateEnchantment,
        modifier = modifier
    )
}
