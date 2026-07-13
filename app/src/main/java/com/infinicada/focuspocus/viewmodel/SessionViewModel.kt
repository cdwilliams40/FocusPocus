package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.RecordResult
import com.infinicada.focuspocus.data.SessionRepository
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Sigil
import com.infinicada.focuspocus.model.Trial
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: SessionRepository =
        (application as FocusPocusApplication).container.session

    // Session state
    private val _manualFocusMode = MutableStateFlow(repo.getManualFocusMode())
    val manualFocusMode: StateFlow<Boolean> = _manualFocusMode.asStateFlow()

    private val _activeBlockerNames = MutableStateFlow(repo.getActiveBlockerNames())
    val activeBlockerNames: StateFlow<List<String>> = _activeBlockerNames.asStateFlow()

    private val _activeScheduleId = MutableStateFlow(repo.getActiveScheduleId())
    val activeScheduleId: StateFlow<String?> = _activeScheduleId.asStateFlow()

    private val _focusDurationMinutes = MutableStateFlow(repo.getFocusDurationMinutes())
    val focusDurationMinutes: StateFlow<Int> = _focusDurationMinutes.asStateFlow()

    private val _focusTimeRemaining = MutableStateFlow(repo.getEffectiveFocusTimeRemaining())
    val focusTimeRemaining: StateFlow<Int> = _focusTimeRemaining.asStateFlow()

    private val _sessionBreaksEnabled = MutableStateFlow(repo.getSessionBreaksEnabled())
    val sessionBreaksEnabled: StateFlow<Boolean> = _sessionBreaksEnabled.asStateFlow()

    private val _selectedPresetId = MutableStateFlow<String?>(null)
    val selectedPresetId: StateFlow<String?> = _selectedPresetId.asStateFlow()

    // Break state
    private val _isOnBreak = MutableStateFlow(repo.getIsOnBreak())
    val isOnBreak: StateFlow<Boolean> = _isOnBreak.asStateFlow()

    private val _breaksUsedThisSession = MutableStateFlow(repo.getBreaksUsedThisSession())
    val breaksUsedThisSession: StateFlow<Int> = _breaksUsedThisSession.asStateFlow()

    private val _breakTimeRemaining = MutableStateFlow(repo.getEffectiveBreakTimeRemaining())
    val breakTimeRemaining: StateFlow<Int> = _breakTimeRemaining.asStateFlow()

    // Emergency break
    private val _lastEmergencyBreakMillis = MutableStateFlow(repo.getLastEmergencyBreakMillis())
    val lastEmergencyBreakMillis: StateFlow<Long> = _lastEmergencyBreakMillis.asStateFlow()

    // Focus tag
    private val _focusTagId = MutableStateFlow(repo.getFocusTagId())
    val focusTagId: StateFlow<String?> = _focusTagId.asStateFlow()

    // Session summary dialog — populated from the RecordResult a stop returns,
    // so the dialog and the persisted record can never disagree.
    private val _showSessionSummary = MutableStateFlow(false)
    val showSessionSummary: StateFlow<Boolean> = _showSessionSummary.asStateFlow()

    private val _sessionSummaryDuration = MutableStateFlow(0)
    val sessionSummaryDuration: StateFlow<Int> = _sessionSummaryDuration.asStateFlow()

    private val _sessionSummaryBreaks = MutableStateFlow(0)
    val sessionSummaryBreaks: StateFlow<Int> = _sessionSummaryBreaks.asStateFlow()

    private val _sessionSummaryBlocker = MutableStateFlow("")
    val sessionSummaryBlocker: StateFlow<String> = _sessionSummaryBlocker.asStateFlow()

    private val _sessionSummaryMana = MutableStateFlow(0L)
    val sessionSummaryMana: StateFlow<Long> = _sessionSummaryMana.asStateFlow()

    private val _sessionSummaryMilestoneBonus = MutableStateFlow(0L)
    val sessionSummaryMilestoneBonus: StateFlow<Long> = _sessionSummaryMilestoneBonus.asStateFlow()

    private val _sessionSummaryStreak = MutableStateFlow(0)
    val sessionSummaryStreak: StateFlow<Int> = _sessionSummaryStreak.asStateFlow()

    private val _sessionSummaryTrials = MutableStateFlow<List<Trial>>(emptyList())
    val sessionSummaryTrials: StateFlow<List<Trial>> = _sessionSummaryTrials.asStateFlow()

    private val _sessionSummarySigils = MutableStateFlow<List<Sigil>>(emptyList())
    val sessionSummarySigils: StateFlow<List<Sigil>> = _sessionSummarySigils.asStateFlow()

    // Session history
    private val _focusSessions = MutableStateFlow(repo.getFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private val _longestStreak = MutableStateFlow(repo.getLongestStreak())
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    // Elapsed seconds for unlimited sessions (no end time to count down to)
    private val _sessionElapsedSeconds = MutableStateFlow(0L)
    val sessionElapsedSeconds: StateFlow<Long> = _sessionElapsedSeconds.asStateFlow()

    // Timer jobs
    private var focusTimerJob: Job? = null
    private var breakTimerJob: Job? = null
    private var elapsedTimerJob: Job? = null

    init {
        normalizePersistedState()
        startTimersIfNeeded()
    }

    /**
     * Reconciles persisted countdown state with the wall clock. The in-memory tickers
     * only run while the UI is alive, so a break or timed session may have expired
     * while the process was dead. The accessibility service also enforces this on its
     * minute tick; this covers the window before that tick and the service-disabled case.
     */
    private fun normalizePersistedState() {
        // End a break whose wall-clock end time has passed.
        if (_isOnBreak.value && _breakTimeRemaining.value <= 0) {
            _isOnBreak.value = false
            _breakTimeRemaining.value = 0
            repo.writeBreakState(
                isOnBreak = false,
                breakTimeRemaining = 0,
                breaksUsed = _breaksUsedThisSession.value,
                focusTimeRemaining = _focusTimeRemaining.value
            )
        }
        // Expire a timed session whose wall-clock end time has passed.
        if (_manualFocusMode.value && !_isOnBreak.value &&
            _focusTimeRemaining.value <= 0 && repo.getFocusEndTimeMillis() > 0
        ) {
            handleTimerExpired()
        }
    }

    private fun startTimersIfNeeded() {
        if (_isOnBreak.value && _breakTimeRemaining.value > 0) {
            startBreakTimer()
        }
        if (_manualFocusMode.value && _focusTimeRemaining.value > 0 && !_isOnBreak.value) {
            startFocusTimer()
        }
        startOrStopElapsedTimer()
    }

    // The tickers below re-derive the remaining time from the persisted wall-clock
    // end timestamps on every tick instead of decrementing a counter. This keeps the
    // display honest across process suspension, dispatcher throttling, and clock
    // changes, and avoids a SharedPreferences write per second — the end timestamp
    // written once at the state transition is the single source of truth.
    private fun startFocusTimer() {
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (_manualFocusMode.value && !_isOnBreak.value) {
                val remaining = repo.getEffectiveFocusTimeRemaining()
                _focusTimeRemaining.value = remaining
                if (remaining <= 0) {
                    if (repo.getFocusEndTimeMillis() > 0) handleTimerExpired()
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun startBreakTimer() {
        breakTimerJob?.cancel()
        breakTimerJob = viewModelScope.launch {
            while (_isOnBreak.value) {
                val remaining = repo.getEffectiveBreakTimeRemaining()
                _breakTimeRemaining.value = remaining
                if (remaining <= 0) {
                    _isOnBreak.value = false
                    // Persist break end and restart the focus end-time clock so the
                    // accessibility service can enforce expiry if the UI goes away.
                    repo.writeBreakState(
                        isOnBreak = false,
                        breakTimeRemaining = 0,
                        breaksUsed = _breaksUsedThisSession.value,
                        focusTimeRemaining = _focusTimeRemaining.value
                    )
                    // Resume focus timer
                    startFocusTimer()
                    break
                }
                delay(1000L)
            }
        }
    }

    /** Ticks elapsed time for unlimited sessions, where no countdown is running. */
    private fun startOrStopElapsedTimer() {
        elapsedTimerJob?.cancel()
        val unlimited = _manualFocusMode.value && repo.getFocusEndTimeMillis() <= 0L &&
            _focusTimeRemaining.value <= 0
        if (!unlimited) {
            _sessionElapsedSeconds.value = 0L
            return
        }
        elapsedTimerJob = viewModelScope.launch {
            while (_manualFocusMode.value) {
                val start = repo.getSessionStartTime()
                _sessionElapsedSeconds.value =
                    if (start > 0) ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L) else 0L
                delay(1000L)
            }
        }
    }

    private fun handleTimerExpired() {
        val result = repo.stopSession()
        showSummaryFor(result)
        DndController.updateDndState(getApplication())
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    /**
     * Shows the end-of-session dialog for a recorded session. Discarded
     * sessions (recorded == null, e.g. under a minute — or the empty result of
     * the second stop call on the emergency path) show nothing, which also
     * preserves the old duration >= 1 gate.
     */
    private fun showSummaryFor(result: RecordResult) {
        val recorded = result.recorded ?: return
        _sessionSummaryDuration.value = recorded.durationMinutes
        _sessionSummaryBreaks.value = recorded.breaksUsed
        _sessionSummaryBlocker.value = recorded.blockerName
        _sessionSummaryMana.value = result.manaEarned
        _sessionSummaryMilestoneBonus.value = result.milestoneBonus
        _sessionSummaryStreak.value = result.newStreak
        _sessionSummaryTrials.value = result.completedTrials
        _sessionSummarySigils.value = result.unlockedSigils
        _showSessionSummary.value = true
    }

    fun dismissSessionSummary() {
        _showSessionSummary.value = false
    }

    fun selectPreset(preset: FocusPreset) {
        _selectedPresetId.value = preset.id
        _activeBlockerNames.value = preset.effectiveBlockerNames
        _focusDurationMinutes.value = preset.durationMinutes
        _sessionBreaksEnabled.value = preset.breaksEnabled
        repo.setFocusDurationMinutes(preset.durationMinutes)
        repo.setSessionBreaksEnabled(preset.breaksEnabled)
    }

    fun selectBlocker(blocker: Blocker) {
        _activeBlockerNames.value = listOf(blocker.name)
        _selectedPresetId.value = null
    }

    fun selectBlockers(blockers: List<Blocker>) {
        _activeBlockerNames.value = blockers.map { it.name }
        _selectedPresetId.value = null
    }

    fun toggleBlocker(blocker: Blocker) {
        val current = _activeBlockerNames.value
        _activeBlockerNames.value = if (blocker.name in current) {
            current - blocker.name
        } else {
            current + blocker.name
        }
        _selectedPresetId.value = null
    }

    fun selectDuration(duration: Int) {
        _focusDurationMinutes.value = duration
        repo.setFocusDurationMinutes(duration)
        _selectedPresetId.value = null
    }

    fun toggleSessionBreaks(enabled: Boolean) {
        _sessionBreaksEnabled.value = enabled
        repo.setSessionBreaksEnabled(enabled)
        _selectedPresetId.value = null
    }

    fun startSession(blockerNames: List<String>) {
        repo.startSession(blockerNames, _focusDurationMinutes.value, _sessionBreaksEnabled.value)
        syncFromPrefs()
    }

    fun stopSession() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        showSummaryFor(repo.stopSession())
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    fun dispelSchedule() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        _activeScheduleId.value = null
        showSummaryFor(repo.stopSession())
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    fun takeBreak(effectiveBreakDuration: Int) {
        val used = _breaksUsedThisSession.value + 1
        val remaining = effectiveBreakDuration * 60
        // Freeze the focus countdown at its current wall-clock value for the length
        // of the break; the end timestamp is recomputed from it when the break ends.
        val focusRemaining = repo.getEffectiveFocusTimeRemaining()
        _isOnBreak.value = true
        _breakTimeRemaining.value = remaining
        _breaksUsedThisSession.value = used
        _focusTimeRemaining.value = focusRemaining
        repo.writeBreakState(
            isOnBreak = true,
            breakTimeRemaining = remaining,
            breaksUsed = used,
            focusTimeRemaining = focusRemaining
        )
        focusTimerJob?.cancel()
        startBreakTimer()
        DndController.updateDndState(getApplication())
    }

    fun endBreak() {
        breakTimerJob?.cancel()
        _isOnBreak.value = false
        _breakTimeRemaining.value = 0
        repo.writeBreakState(
            isOnBreak = false,
            breakTimeRemaining = 0,
            breaksUsed = _breaksUsedThisSession.value,
            focusTimeRemaining = _focusTimeRemaining.value
        )
        startFocusTimer()
        DndController.updateDndState(getApplication())
    }

    fun emergencyStop() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        val now = System.currentTimeMillis()
        _lastEmergencyBreakMillis.value = now
        repo.setLastEmergencyBreakMillis(now)
        repo.stopSession()
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    fun setFocusTagId(tagId: String?) {
        _focusTagId.value = tagId
        repo.setFocusTagId(tagId)
    }

    fun onActiveScheduleIdChanged(newId: String?) {
        _activeScheduleId.value = newId
        if (newId == null) {
            _manualFocusMode.value = repo.getManualFocusMode()
        } else {
            _manualFocusMode.value = true
        }
        startTimersIfNeeded()
    }

    fun writeFocusModeState() {
        // A talisman can hold a session open with manual mode off, so "manual
        // mode off" is not "no session" — only clear break/countdown state
        // when neither anchor is active.
        val sessionActive = _manualFocusMode.value || _focusTagId.value != null
        repo.writeFocusModeState(
            manualFocusMode = _manualFocusMode.value,
            activeBlockerNames = _activeBlockerNames.value,
            activeScheduleId = _activeScheduleId.value,
            sessionActive = sessionActive,
            isOnBreak = if (!sessionActive) false else _isOnBreak.value,
            breakTimeRemaining = if (!sessionActive) 0 else _breakTimeRemaining.value,
            breaksUsedThisSession = if (!sessionActive) 0 else _breaksUsedThisSession.value,
            focusTimeRemaining = if (!sessionActive) 0 else _focusTimeRemaining.value
        )
        if (!sessionActive) {
            _breaksUsedThisSession.value = 0
            _isOnBreak.value = false
            _breakTimeRemaining.value = 0
            _focusTimeRemaining.value = 0
            _sessionElapsedSeconds.value = 0L
            focusTimerJob?.cancel()
            breakTimerJob?.cancel()
            elapsedTimerJob?.cancel()
        }
    }

    fun syncFromPrefs() {
        _manualFocusMode.value = repo.getManualFocusMode()
        _activeBlockerNames.value = repo.getActiveBlockerNames()
        _focusDurationMinutes.value = repo.getFocusDurationMinutes()
        _focusTimeRemaining.value = repo.getEffectiveFocusTimeRemaining()
        _sessionBreaksEnabled.value = repo.getSessionBreaksEnabled()
        _isOnBreak.value = repo.getIsOnBreak()
        _breaksUsedThisSession.value = repo.getBreaksUsedThisSession()
        _breakTimeRemaining.value = repo.getEffectiveBreakTimeRemaining()
        _focusTagId.value = repo.getFocusTagId()
        _activeScheduleId.value = repo.getActiveScheduleId()
        _focusSessions.value = repo.getFocusSessions()
        _longestStreak.value = repo.getLongestStreak()
        normalizePersistedState()
        startTimersIfNeeded()
    }
}
