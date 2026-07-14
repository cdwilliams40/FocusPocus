package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object SessionManager {

    private val gson = Gson()

    fun startSession(
        sharedPreferences: SharedPreferences,
        blockerNames: List<String>,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true,
        scheduleEndTimeMillis: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val editor = sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(blockerNames))
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blockerNames.firstOrNull())
            .putLong(Constants.PrefsKeys.SESSION_START_TIME, now)
            .putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, breaksEnabled)
            // A new session always starts outside any leftover break state
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            .remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            // Extra-break perk tokens are session-scoped; never inherit one
            .remove(Constants.PrefsKeys.EXTRA_BREAK_TOKENS)

        if (scheduleId != null) {
            editor.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, scheduleId)
            editor.remove(Constants.PrefsKeys.FOCUS_DURATION_MINUTES)
            editor.remove(Constants.PrefsKeys.FOCUS_TIME_REMAINING)
            editor.remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            // Snapshot the window end so a missed deactivation (service dead when
            // the ritual ended) can't credit idle hours as focus time.
            if (scheduleEndTimeMillis != null) {
                editor.putLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, scheduleEndTimeMillis)
            } else {
                editor.remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
            }
        } else {
            editor.remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            editor.remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
            editor.putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, durationMinutes)
            val focusTimeRemaining = if (durationMinutes > 0) durationMinutes * 60 else 0
            editor.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
            if (durationMinutes > 0) {
                editor.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + durationMinutes * 60_000L)
            } else {
                editor.remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            }
        }

        editor.apply()
    }

    /** Convenience overload for single blocker name */
    fun startSession(
        sharedPreferences: SharedPreferences,
        blockerName: String,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true
    ) = startSession(sharedPreferences, listOf(blockerName), scheduleId, durationMinutes, breaksEnabled)

    fun stopSession(
        context: Context,
        sharedPreferences: SharedPreferences,
        gson: Gson
    ): RecordResult {
        // Record completed session (and run the progression award step) before
        // the session prefs it reads are cleared below.
        val result: RecordResult? = SessionRecorder.record(sharedPreferences, gson)

        sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            .remove(Constants.PrefsKeys.ACTIVE_BLOCKER)
            .remove(Constants.PrefsKeys.ACTIVE_BLOCKERS)
            .remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            .remove(Constants.PrefsKeys.FOCUS_TAG_ID)
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            .remove(Constants.PrefsKeys.SESSION_START_TIME)
            .remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            .remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
            .remove(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS)
            .remove(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS)
            .remove(Constants.PrefsKeys.EXTRA_BREAK_TOKENS)
            .apply()

        DndController.updateDndState(context)
        DeviceOwnerManager.syncSuspensions(context)

        // Null tolerance is for unit tests that static-mock SessionRecorder.
        val recordResult = result ?: RecordResult(emptyList())
        ProgressionNotifier.postTrialCompletions(context, sharedPreferences, recordResult.completedTrials)
        return recordResult
    }

    fun isSessionActive(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
    }
}
