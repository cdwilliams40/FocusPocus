package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.DisplayTicks
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.RecordResult
import com.infinicada.focuspocus.SessionRules
import com.infinicada.focuspocus.SessionState
import com.infinicada.focuspocus.data.SessionRepository
import com.infinicada.focuspocus.model.Trial
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: SessionRepository =
        (application as FocusPocusApplication).container.session

    /** Single authoritative session state stream backed by SessionStore. */
    val sessionState: StateFlow<SessionState> = repo.state

    // Derived convenience streams for UI routes
    val manualFocusMode: StateFlow<Boolean> = sessionState
        .map { it is SessionState.Active }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionState.value is SessionState.Active)

    val activeBlockerNames: StateFlow<List<String>> = sessionState
        .map { (it as? SessionState.Active)?.blockerNames ?: emptyList() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (sessionState.value as? SessionState.Active)?.blockerNames ?: emptyList()
        )

    val activeScheduleId: StateFlow<String?> = sessionState
        .map { (it as? SessionState.Active)?.scheduleId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (sessionState.value as? SessionState.Active)?.scheduleId)

    val focusDurationMinutes: StateFlow<Int> = sessionState
        .map { (it as? SessionState.Active)?.plannedDurationMinutes ?: 0 }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (sessionState.value as? SessionState.Active)?.plannedDurationMinutes ?: 0
        )

    val sessionBreaksEnabled: StateFlow<Boolean> = sessionState
        .map { (it as? SessionState.Active)?.breaksEnabled ?: true }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (sessionState.value as? SessionState.Active)?.breaksEnabled ?: true
        )

    val isOnBreak: StateFlow<Boolean> = sessionState
        .map { (it as? SessionState.Active)?.isOnBreak == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (sessionState.value as? SessionState.Active)?.isOnBreak == true)

    val breaksUsedThisSession: StateFlow<Int> = sessionState
        .map { (it as? SessionState.Active)?.breaksUsed ?: 0 }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (sessionState.value as? SessionState.Active)?.breaksUsed ?: 0
        )

    /** The talisman that started the running session, if one did. */
    val activeTalismanId: StateFlow<String?> = sessionState
        .map { (it as? SessionState.Active)?.talismanId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (sessionState.value as? SessionState.Active)?.talismanId)

    // Display-only countdown/elapsed values derived from wall-clock timestamps
    private val _focusTimeRemaining = MutableStateFlow(repo.getEffectiveFocusTimeRemaining())
    val focusTimeRemaining: StateFlow<Int> = _focusTimeRemaining.asStateFlow()

    private val _breakTimeRemaining = MutableStateFlow(repo.getEffectiveBreakTimeRemaining())
    val breakTimeRemaining: StateFlow<Int> = _breakTimeRemaining.asStateFlow()

    private val _sessionElapsedSeconds = MutableStateFlow(0L)
    val sessionElapsedSeconds: StateFlow<Long> = _sessionElapsedSeconds.asStateFlow()

    // Remembered hand-made Focus tab setup (independent of running sessions)
    private val _manualBlockerNames = MutableStateFlow(repo.getLastManualBlockerNames())
    val manualBlockerNames: StateFlow<List<String>> = _manualBlockerNames.asStateFlow()

    private val _manualDurationMinutes = MutableStateFlow(repo.getLastManualDurationMinutes())
    val manualDurationMinutes: StateFlow<Int> = _manualDurationMinutes.asStateFlow()

    private val _manualBreaksEnabled = MutableStateFlow(repo.getLastManualBreaksEnabled())
    val manualBreaksEnabled: StateFlow<Boolean> = _manualBreaksEnabled.asStateFlow()

    // Emergency break cooldown timestamp
    private val _lastEmergencyBreakMillis = MutableStateFlow(repo.getLastEmergencyBreakMillis())
    val lastEmergencyBreakMillis: StateFlow<Long> = _lastEmergencyBreakMillis.asStateFlow()

    // Session summary dialog — populated from RecordResult so UI and history always agree.
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

    private val _sessionSummaryTrials = MutableStateFlow<List<Trial>>(emptyList())
    val sessionSummaryTrials: StateFlow<List<Trial>> = _sessionSummaryTrials.asStateFlow()

    // Session history
    private val _focusSessions = MutableStateFlow(repo.getFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private val _longestStreak = MutableStateFlow(repo.getLongestStreak())
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    // Display ticker jobs — paused while UI is not visible
    private var focusTimerJob: Job? = null
    private var breakTimerJob: Job? = null
    private var elapsedTimerJob: Job? = null
    private var uiVisible = true

    init {
        advanceAndRefresh()
        viewModelScope.launch {
            sessionState.collect { state ->
                refreshDisplayCounters(state)
                refreshHistory()
                startTimersIfNeeded(state)
            }
        }
        viewModelScope.launch {
            repo.foregroundSummaries.collect { result ->
                showSummaryFor(result)
                refreshHistory()
            }
        }
    }

    fun onUiStarted() {
        if (uiVisible) return
        uiVisible = true
        advanceAndRefresh()
        startTimersIfNeeded(sessionState.value)
    }

    fun onUiStopped() {
        uiVisible = false
        cancelTimers()
    }

    private fun cancelTimers() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        elapsedTimerJob?.cancel()
    }

    private fun advanceAndRefresh() {
        val outcome = repo.advance()
        outcome.stoppedResult?.let { showSummaryFor(it) }
        repo.refreshState()
        refreshDisplayCounters(sessionState.value)
        refreshHistory()
    }

    private fun refreshDisplayCounters(state: SessionState) {
        val now = System.currentTimeMillis()
        _focusTimeRemaining.value = SessionRules.effectiveFocusTimeRemainingSeconds(state, now)
        _breakTimeRemaining.value = SessionRules.effectiveBreakTimeRemainingSeconds(state, now)
        val active = state as? SessionState.Active
        _sessionElapsedSeconds.value = if (active != null && !active.isTimed && active.startTimeMillis > 0L) {
            ((now - active.startTimeMillis) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    private fun refreshHistory() {
        _focusSessions.value = repo.getFocusSessions()
        _longestStreak.value = repo.getLongestStreak()
        _lastEmergencyBreakMillis.value = repo.getLastEmergencyBreakMillis()
    }

    private fun startTimersIfNeeded(state: SessionState) {
        cancelTimers()
        if (!uiVisible || state !is SessionState.Active) return

        val breakState = state.breakState
        if (breakState != null) {
            startBreakTimer(breakState.endTimeMillis)
            return
        }

        if (state.scheduleId == null && state.focusEndTimeMillis > 0L) {
            startFocusTimer(state.focusEndTimeMillis)
        } else {
            startElapsedTimer(state.startTimeMillis)
        }
    }

    private fun startFocusTimer(focusEndMillis: Long) {
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remaining = DisplayTicks.countdownSeconds(focusEndMillis - now)
                _focusTimeRemaining.value = remaining
                if (remaining <= 0) {
                    val outcome = repo.advance()
                    outcome.stoppedResult?.let { showSummaryFor(it) }
                    refreshHistory()
                    break
                }
                delay(DisplayTicks.untilCountdownSecondChanges(focusEndMillis - now))
            }
        }
    }

    private fun startBreakTimer(breakEndMillis: Long) {
        breakTimerJob?.cancel()
        breakTimerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val remaining = DisplayTicks.countdownSeconds(breakEndMillis - now)
                _breakTimeRemaining.value = remaining
                if (remaining <= 0) {
                    repo.advance()
                    break
                }
                delay(DisplayTicks.untilCountdownSecondChanges(breakEndMillis - now))
            }
        }
    }

    private fun startElapsedTimer(startMillis: Long) {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _sessionElapsedSeconds.value =
                    if (startMillis > 0L) ((now - startMillis) / 1000L).coerceAtLeast(0L) else 0L
                delay(if (startMillis > 0L) DisplayTicks.untilElapsedSecondChanges(now - startMillis) else 1000L)
            }
        }
    }

    private fun showSummaryFor(result: RecordResult) {
        val recorded = result.recorded ?: return
        _sessionSummaryDuration.value = recorded.durationMinutes
        _sessionSummaryBreaks.value = recorded.breaksUsed
        _sessionSummaryBlocker.value = recorded.blockerName
        _sessionSummaryMana.value = result.manaEarned
        _sessionSummaryTrials.value = result.completedTrials
        _showSessionSummary.value = true
    }

    fun dismissSessionSummary() {
        _showSessionSummary.value = false
    }

    fun toggleBlocker(blocker: Blocker) {
        val current = _manualBlockerNames.value
        val updated = if (blocker.name in current) {
            current - blocker.name
        } else {
            current + blocker.name
        }
        _manualBlockerNames.value = updated
        repo.setLastManualBlockerNames(updated)
    }

    fun selectDuration(duration: Int) {
        _manualDurationMinutes.value = duration
        repo.setLastManualDurationMinutes(duration)
    }

    fun toggleSessionBreaks(enabled: Boolean) {
        _manualBreaksEnabled.value = enabled
        repo.setLastManualBreaksEnabled(enabled)
    }

    fun startSession(blockerNames: List<String>) {
        repo.startSession(blockerNames, _manualDurationMinutes.value, _manualBreaksEnabled.value)
    }

    fun stopSession() {
        cancelTimers()
        showSummaryFor(repo.stopSession())
        refreshHistory()
    }

    fun dispelSchedule() {
        cancelTimers()
        showSummaryFor(repo.stopSession())
        refreshHistory()
    }

    fun takeBreak(effectiveBreakDuration: Int) {
        repo.takeBreak(effectiveBreakDuration)
    }

    fun endBreak() {
        repo.endBreak()
    }

    fun emergencyStop() {
        cancelTimers()
        repo.emergencyStop()
        refreshHistory()
    }

    fun syncFromPrefs() {
        advanceAndRefresh()
        startTimersIfNeeded(sessionState.value)
    }
}
