package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.data.SessionStore
import com.infinicada.focuspocus.model.Schedule
import kotlinx.serialization.encodeToString

data class AdvanceOutcome(
    val events: List<SessionTransition> = emptyList(),
    val stoppedResult: RecordResult? = null
) {
    val changed: Boolean get() = events.isNotEmpty()
}

object SessionManager {

    private const val TAG = "SessionManager"

    fun startSession(
        context: Context,
        sharedPreferences: SharedPreferences,
        blockerNames: List<String>,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true,
        scheduleEndTimeMillis: Long? = null,
        talismanId: String? = null
    ) {
        startSession(
            sharedPreferences = sharedPreferences,
            blockerNames = blockerNames,
            scheduleId = scheduleId,
            durationMinutes = durationMinutes,
            breaksEnabled = breaksEnabled,
            scheduleEndTimeMillis = scheduleEndTimeMillis,
            talismanId = talismanId
        )
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
        RitualAlarmScheduler.scheduleNext(context)
    }

    fun startSession(
        sharedPreferences: SharedPreferences,
        blockerNames: List<String>,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true,
        scheduleEndTimeMillis: Long? = null,
        talismanId: String? = null
    ) {
        val now = System.currentTimeMillis()
        sharedPreferences.edit {
            putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(blockerNames))
            if (talismanId != null) putString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, talismanId)
            else remove(Constants.PrefsKeys.ACTIVE_TALISMAN_ID)
            putLong(Constants.PrefsKeys.SESSION_START_TIME, now)
            putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
            putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, breaksEnabled)
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            remove(Constants.PrefsKeys.EXTRA_BREAK_TOKENS)
            BreakClock.reset(this)

            if (scheduleId != null) {
                writeDismissedRituals(this, dismissedRituals(sharedPreferences, now) - scheduleId)
                putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, scheduleId)
                remove(Constants.PrefsKeys.FOCUS_DURATION_MINUTES)
                remove(Constants.PrefsKeys.FOCUS_TIME_REMAINING)
                remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
                if (scheduleEndTimeMillis != null) {
                    putLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, scheduleEndTimeMillis)
                } else {
                    remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
                }
            } else {
                remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
                remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
                putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, durationMinutes)
                val focusTimeRemaining = if (durationMinutes > 0) durationMinutes * 60 else 0
                putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
                if (durationMinutes > 0) {
                    putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + durationMinutes * 60_000L)
                } else {
                    remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
                }
            }
        }
    }

    fun stopSession(
        context: Context,
        sharedPreferences: SharedPreferences
    ): RecordResult = endSession(context, sharedPreferences, recordAndDismiss = true)

    /**
     * Ends a session that can no longer run because what it blocked or the
     * ritual that started it was deleted. Unlike [stopSession] it records
     * nothing (no history entry, mana or trial progress) and doesn't mark the
     * ritual as ended for its window, so a ritual whose enchantment is fixed
     * can start again.
     */
    fun discardSession(context: Context, sharedPreferences: SharedPreferences) {
        endSession(context, sharedPreferences, recordAndDismiss = false)
    }

    private fun endSession(
        context: Context,
        sharedPreferences: SharedPreferences,
        recordAndDismiss: Boolean
    ): RecordResult {
        val now = System.currentTimeMillis()
        val endedScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val windowEnd = sharedPreferences.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L)
        val dismissed = dismissedRituals(sharedPreferences, now).let {
            if (recordAndDismiss && endedScheduleId != null && windowEnd > now) {
                it + (endedScheduleId to windowEnd)
            } else {
                it
            }
        }

        val result: RecordResult? =
            if (recordAndDismiss) SessionRecorder.record(sharedPreferences) else null

        sharedPreferences.edit {
            writeDismissedRituals(this, dismissed)
            putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            remove(Constants.PrefsKeys.ACTIVE_BLOCKERS)
            remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            remove(Constants.PrefsKeys.ACTIVE_TALISMAN_ID)
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            remove(Constants.PrefsKeys.SESSION_START_TIME)
            remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
            remove(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS)
            remove(Constants.PrefsKeys.EXTRA_BREAK_TOKENS)
            BreakClock.reset(this)
        }

        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
        RitualAlarmScheduler.scheduleNext(context)

        val recordResult = result ?: RecordResult(emptyList())
        ProgressionNotifier.postTrialCompletions(context, sharedPreferences, recordResult.completedTrials)
        return recordResult
    }

    fun takeBreak(
        context: Context,
        sharedPreferences: SharedPreferences,
        breakDurationMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val current = SessionStore.read(sharedPreferences) as? SessionState.Active ?: return false
        if (current.isOnBreak) return false
        val next = SessionRules.startBreak(current, breakDurationMinutes, now)
        writeBreakStarted(sharedPreferences, next, now)
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
        RitualAlarmScheduler.scheduleNext(context)
        return true
    }

    fun endBreak(
        context: Context,
        sharedPreferences: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val current = SessionStore.read(sharedPreferences) as? SessionState.Active ?: return false
        if (!current.isOnBreak) return false
        val next = SessionRules.endBreak(current, now)
        writeBreakEnded(sharedPreferences, next, now)
        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)
        RitualAlarmScheduler.scheduleNext(context)
        return true
    }

    fun emergencyStop(
        context: Context,
        sharedPreferences: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): RecordResult {
        sharedPreferences.edit {
            putLong(Constants.PrefsKeys.LAST_EMERGENCY_BREAK_MILLIS, now)
        }
        return stopSession(context, sharedPreferences)
    }

    fun advance(
        context: Context,
        sharedPreferences: SharedPreferences,
        schedules: List<Schedule>? = null,
        now: Long = System.currentTimeMillis()
    ): AdvanceOutcome {
        val current = SessionStore.read(sharedPreferences) as? SessionState.Active
            ?: return AdvanceOutcome()

        val activeSchedule = current.scheduleId?.let { id ->
            val list = schedules ?: loadSchedules(sharedPreferences)
            list.find { it.id == id }
        }
        val policy = SessionStore.readBreakPolicy(sharedPreferences, activeSchedule)
        val result = SessionRules.advance(current, policy, now)
        if (result.events.isEmpty()) return AdvanceOutcome()

        var stoppedResult: RecordResult? = null
        for (event in result.events) {
            when (event) {
                is SessionTransition.BreakEnded -> {
                    val next = result.state as SessionState.Active
                    writeBreakEnded(sharedPreferences, next, now)
                    Log.d(TAG, "Break expired — resuming focus mode")
                    DndController.updateDndState(context)
                    DeviceOwnerManager.syncSuspensions(context)
                    RitualAlarmScheduler.scheduleNext(context)
                }
                is SessionTransition.SessionExpired -> {
                    Log.d(TAG, "Timed session expired — stopping session")
                    stoppedResult = stopSession(context, sharedPreferences)
                }
                is SessionTransition.AutoBreakStarted -> {
                    val next = result.state as SessionState.Active
                    writeBreakStarted(sharedPreferences, next, now)
                    Log.d(TAG, "Auto-break started after ${event.focusedMinutes}m of focus")
                    DndController.updateDndState(context)
                    DeviceOwnerManager.syncSuspensions(context)
                    RitualAlarmScheduler.scheduleNext(context)
                    RitualNotifier.postAutoBreakStarted(context, event.focusedMinutes, event.breakMinutes)
                }
            }
        }
        return AdvanceOutcome(events = result.events, stoppedResult = stoppedResult)
    }

    private fun writeBreakStarted(
        prefs: SharedPreferences,
        next: SessionState.Active,
        now: Long
    ) {
        val breakState = next.breakState ?: return
        prefs.edit {
            BreakClock.markStarted(prefs, this, now)
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakState.remainingSeconds)
            putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, breakState.endTimeMillis)
            putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, next.breaksUsed)
            putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, next.focusTimeRemainingSeconds)
            remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
        }
    }

    private fun writeBreakEnded(
        prefs: SharedPreferences,
        next: SessionState.Active,
        now: Long
    ) {
        prefs.edit {
            BreakClock.markEnded(prefs, this, now)
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, next.focusSegmentStartMillis)
            if (next.focusEndTimeMillis > 0L) {
                putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, next.focusEndTimeMillis)
            } else {
                remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            }
        }
    }

    private fun loadSchedules(prefs: SharedPreferences): List<Schedule> =
        PrefsHelper.loadListLenient<Schedule>(prefs, Constants.PrefsKeys.SCHEDULES) ?: emptyList()

    fun isSessionActive(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)

    fun isFocusActive(sharedPreferences: SharedPreferences): Boolean = isSessionActive(sharedPreferences)

    fun activeTalismanId(sharedPreferences: SharedPreferences): String? =
        sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, null)

    fun isRitualDismissed(
        sharedPreferences: SharedPreferences,
        scheduleId: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = scheduleId in dismissedRituals(sharedPreferences, now)

    private fun dismissedRituals(sharedPreferences: SharedPreferences, now: Long): Map<String, Long> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.DISMISSED_RITUALS, null)
            ?: return emptyMap()
        return try {
            AppJson.json.decodeFromString<Map<String, Long>>(json).filterValues { it > now }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeDismissedRituals(editor: SharedPreferences.Editor, dismissed: Map<String, Long>) {
        if (dismissed.isEmpty()) editor.remove(Constants.PrefsKeys.DISMISSED_RITUALS)
        else editor.putString(Constants.PrefsKeys.DISMISSED_RITUALS, AppJson.json.encodeToString(dismissed))
    }
}
