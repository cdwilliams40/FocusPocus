package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object SessionManager {

    fun startSession(
        sharedPreferences: SharedPreferences,
        blockerName: String,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true
    ) {
        val editor = sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blockerName)
            .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis())
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, breaksEnabled)

        if (scheduleId != null) {
            editor.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, scheduleId)
            // Schedule-based sessions generally don't have a fixed duration or time remaining
            editor.remove(Constants.PrefsKeys.FOCUS_DURATION_MINUTES)
            editor.remove(Constants.PrefsKeys.FOCUS_TIME_REMAINING)
        } else {
            editor.remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            editor.putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, durationMinutes)
            val focusTimeRemaining = if (durationMinutes > 0) durationMinutes * 60 else 0
            editor.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
        }

        editor.apply()
    }

    fun stopSession(
        context: Context,
        sharedPreferences: SharedPreferences,
        gson: Gson
    ) {
        // Record completed session
        SessionRecorder.record(sharedPreferences, gson)

        sharedPreferences.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
            .remove(Constants.PrefsKeys.ACTIVE_BLOCKER)
            .remove(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID)
            .remove(Constants.PrefsKeys.FOCUS_TAG_ID)
            .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 0)
            .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            .putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            .remove(Constants.PrefsKeys.SESSION_START_TIME)
            .apply()

        DndController.updateDndState(context)
    }

    fun isSessionActive(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
    }
}
