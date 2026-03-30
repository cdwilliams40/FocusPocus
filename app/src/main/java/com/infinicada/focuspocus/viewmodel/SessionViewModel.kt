package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.data.SessionRepository
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Schedule
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

    private val _focusTimeRemaining = MutableStateFlow(repo.getFocusTimeRemaining())
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

    private val _breakTimeRemaining = MutableStateFlow(repo.getBreakTimeRemaining())
    val breakTimeRemaining: StateFlow<Int> = _breakTimeRemaining.asStateFlow()

    // Emergency break
    private val _lastEmergencyBreakMillis = MutableStateFlow(repo.getLastEmergencyBreakMillis())
    val lastEmergencyBreakMillis: StateFlow<Long> = _lastEmergencyBreakMillis.asStateFlow()

    // Focus tag
    private val _focusTagId = MutableStateFlow(repo.getFocusTagId())
    val focusTagId: StateFlow<String?> = _focusTagId.asStateFlow()

    // Session summary dialog
    private val _showSessionSummary = MutableStateFlow(false)
    val showSessionSummary: StateFlow<Boolean> = _showSessionSummary.asStateFlow()

    private val _sessionSummaryDuration = MutableStateFlow(0)
    val sessionSummaryDuration: StateFlow<Int> = _sessionSummaryDuration.asStateFlow()

    private val _sessionSummaryBreaks = MutableStateFlow(0)
    val sessionSummaryBreaks: StateFlow<Int> = _sessionSummaryBreaks.asStateFlow()

    private val _sessionSummaryBlocker = MutableStateFlow("")
    val sessionSummaryBlocker: StateFlow<String> = _sessionSummaryBlocker.asStateFlow()

    // Session history
    private val _focusSessions = MutableStateFlow(repo.getFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private val _longestStreak = MutableStateFlow(repo.getLongestStreak())
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    // Timer jobs
    private var focusTimerJob: Job? = null
    private var breakTimerJob: Job? = null

    init {
        startTimersIfNeeded()
    }

    private fun startTimersIfNeeded() {
        if (_isOnBreak.value && _breakTimeRemaining.value > 0) {
            startBreakTimer()
        }
        if (_manualFocusMode.value && _focusTimeRemaining.value > 0 && !_isOnBreak.value) {
            startFocusTimer()
        }
    }

    private fun startFocusTimer() {
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (_manualFocusMode.value && _focusTimeRemaining.value > 0 && !_isOnBreak.value) {
                delay(1000L)
                val remaining = _focusTimeRemaining.value - 1
                _focusTimeRemaining.value = remaining
                repo.setFocusTimeRemaining(remaining)

                if (remaining <= 0) {
                    handleTimerExpired()
                    break
                }
            }
        }
    }

    private fun startBreakTimer() {
        breakTimerJob?.cancel()
        breakTimerJob = viewModelScope.launch {
            while (_isOnBreak.value && _breakTimeRemaining.value > 0) {
                delay(1000L)
                val remaining = _breakTimeRemaining.value - 1
                _breakTimeRemaining.value = remaining
                repo.setBreakTimeRemaining(remaining)

                if (remaining <= 0) {
                    _isOnBreak.value = false
                    repo.setIsOnBreak(false)
                    DndController.updateDndState(getApplication())
                    // Resume focus timer
                    startFocusTimer()
                    break
                }
            }
        }
    }

    private fun handleTimerExpired() {
        val startTime = repo.getSessionStartTime()
        val durationMin = if (startTime > 0) ((System.currentTimeMillis() - startTime) / 60000).toInt() else 0
        _sessionSummaryDuration.value = durationMin
        _sessionSummaryBreaks.value = _breaksUsedThisSession.value
        _sessionSummaryBlocker.value = repo.getActiveBlockerNames().joinToString(", ").ifEmpty { "" }
        if (durationMin >= 1) {
            _showSessionSummary.value = true
        }

        repo.stopSession()
        syncFromPrefs()
        _manualFocusMode.value = false
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

    fun stopSessionWithSummary(activeSchedule: Schedule?) {
        val startTime = repo.getSessionStartTime()
        val durationMin = if (startTime > 0) ((System.currentTimeMillis() - startTime) / 60000).toInt() else 0
        _sessionSummaryDuration.value = durationMin
        _sessionSummaryBreaks.value = _breaksUsedThisSession.value
        _sessionSummaryBlocker.value = _activeBlockerNames.value.joinToString(", ").ifEmpty {
            activeSchedule?.effectiveBlockerNames?.joinToString(", ") ?: ""
        }
        if (durationMin >= 1) {
            _showSessionSummary.value = true
        }
    }

    fun stopSession() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        repo.stopSession()
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    fun dispelSchedule() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        _activeScheduleId.value = null
        repo.stopSession()
        syncFromPrefs()
        _manualFocusMode.value = false
    }

    fun takeBreak(effectiveBreakDuration: Int) {
        val used = _breaksUsedThisSession.value + 1
        val remaining = effectiveBreakDuration * 60
        _isOnBreak.value = true
        _breakTimeRemaining.value = remaining
        _breaksUsedThisSession.value = used
        repo.writeBreakState(isOnBreak = true, breakTimeRemaining = remaining, breaksUsed = used)
        focusTimerJob?.cancel()
        startBreakTimer()
    }

    fun endBreak() {
        breakTimerJob?.cancel()
        _isOnBreak.value = false
        _breakTimeRemaining.value = 0
        repo.writeBreakState(isOnBreak = false, breakTimeRemaining = 0, breaksUsed = _breaksUsedThisSession.value)
        startFocusTimer()
    }

    fun emergencyStop() {
        focusTimerJob?.cancel()
        breakTimerJob?.cancel()
        val now = System.currentTimeMillis()
        _lastEmergencyBreakMillis.value = now
        repo.setLastEmergencyBreakMillis(now)
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
        repo.writeFocusModeState(
            manualFocusMode = _manualFocusMode.value,
            activeBlockerNames = _activeBlockerNames.value,
            activeScheduleId = _activeScheduleId.value,
            isOnBreak = if (!_manualFocusMode.value) false else _isOnBreak.value,
            breakTimeRemaining = if (!_manualFocusMode.value) 0 else _breakTimeRemaining.value,
            breaksUsedThisSession = if (!_manualFocusMode.value) 0 else _breaksUsedThisSession.value,
            focusTimeRemaining = if (!_manualFocusMode.value) 0 else _focusTimeRemaining.value
        )
        if (!_manualFocusMode.value) {
            _breaksUsedThisSession.value = 0
            _isOnBreak.value = false
            _breakTimeRemaining.value = 0
            _focusTimeRemaining.value = 0
            focusTimerJob?.cancel()
            breakTimerJob?.cancel()
        }
    }

    fun syncFromPrefs() {
        _manualFocusMode.value = repo.getManualFocusMode()
        _activeBlockerNames.value = repo.getActiveBlockerNames()
        _focusDurationMinutes.value = repo.getFocusDurationMinutes()
        _focusTimeRemaining.value = repo.getFocusTimeRemaining()
        _sessionBreaksEnabled.value = repo.getSessionBreaksEnabled()
        _isOnBreak.value = repo.getIsOnBreak()
        _breaksUsedThisSession.value = repo.getBreaksUsedThisSession()
        _breakTimeRemaining.value = repo.getBreakTimeRemaining()
        _focusTagId.value = repo.getFocusTagId()
        _activeScheduleId.value = repo.getActiveScheduleId()
        _focusSessions.value = repo.getFocusSessions()
        _longestStreak.value = repo.getLongestStreak()
        startTimersIfNeeded()
    }
}
