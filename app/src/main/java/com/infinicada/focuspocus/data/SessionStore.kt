package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import android.util.Log
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.RecordResult
import com.infinicada.focuspocus.SessionBreakPolicy
import com.infinicada.focuspocus.SessionState
import com.infinicada.focuspocus.model.Schedule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStore(
    private val prefs: SharedPreferences
) {
    private val _state = MutableStateFlow(read(prefs))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _foregroundSummaries = MutableSharedFlow<RecordResult>(extraBufferCapacity = 4)
    val foregroundSummaries: SharedFlow<RecordResult> = _foregroundSummaries.asSharedFlow()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in SESSION_KEYS) {
            _state.value = read(prefs)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun refresh() {
        _state.value = read(prefs)
    }

    fun emitForegroundSummary(result: RecordResult) {
        if (result.recorded != null) {
            _foregroundSummaries.tryEmit(result)
        }
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        private const val TAG = "SessionStore"

        val SESSION_KEYS = setOf(
            Constants.PrefsKeys.MANUAL_FOCUS_MODE,
            Constants.PrefsKeys.ACTIVE_BLOCKERS,
            Constants.PrefsKeys.ACTIVE_SCHEDULE_ID,
            Constants.PrefsKeys.ACTIVE_TALISMAN_ID,
            Constants.PrefsKeys.SESSION_START_TIME,
            Constants.PrefsKeys.FOCUS_DURATION_MINUTES,
            Constants.PrefsKeys.FOCUS_TIME_REMAINING,
            Constants.PrefsKeys.FOCUS_END_TIME_MILLIS,
            Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS,
            Constants.PrefsKeys.SESSION_BREAKS_ENABLED,
            Constants.PrefsKeys.IS_ON_BREAK,
            Constants.PrefsKeys.BREAKS_USED_THIS_SESSION,
            Constants.PrefsKeys.BREAK_TIME_REMAINING,
            Constants.PrefsKeys.BREAK_START_TIME_MILLIS,
            Constants.PrefsKeys.BREAK_END_TIME_MILLIS,
            Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS,
            Constants.PrefsKeys.EXTRA_BREAK_TOKENS
        )

        fun read(prefs: SharedPreferences): SessionState {
            if (!prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)) {
                return SessionState.Idle
            }

            val blockerNames = readBlockerNames(prefs)
            val isOnBreak = prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            val breakState = if (isOnBreak) {
                SessionState.BreakState(
                    startTimeMillis = prefs.getLong(Constants.PrefsKeys.BREAK_START_TIME_MILLIS, 0L),
                    endTimeMillis = prefs.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L),
                    remainingSeconds = prefs.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
                )
            } else {
                null
            }

            return SessionState.Active(
                blockerNames = blockerNames,
                startTimeMillis = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L),
                plannedDurationMinutes = prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0),
                focusEndTimeMillis = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L),
                focusTimeRemainingSeconds = prefs.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0),
                scheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null),
                scheduleEndTimeMillis = prefs.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L),
                talismanId = prefs.getString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, null),
                breaksEnabled = prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true),
                breaksUsed = prefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0),
                extraBreakTokens = prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0),
                focusSegmentStartMillis = prefs.getLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, 0L),
                breakState = breakState
            )
        }

        fun readBreakPolicy(
            prefs: SharedPreferences,
            activeSchedule: Schedule? = null
        ): SessionBreakPolicy {
            return SessionBreakPolicy(
                autoBreakEnabled = prefs.getBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, false),
                autoBreakIntervalMinutes = prefs.getInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 25),
                breakDurationMinutes = prefs.getInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 5),
                maxBreaksPerSession = prefs.getInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 3),
                scheduleBreaksEnabled = activeSchedule?.breaksEnabled,
                scheduleBreakDurationMinutes = activeSchedule?.breakDurationMinutes,
                scheduleMaxBreaksPerSession = activeSchedule?.maxBreaksPerSession
            )
        }

        private fun readBlockerNames(prefs: SharedPreferences): List<String> {
            val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null) ?: return emptyList()
            return try {
                AppJson.decodeListLenient(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing active blockers JSON", e)
                emptyList()
            }
        }
    }
}
