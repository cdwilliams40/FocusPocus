package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SessionManager {

    private val gson = Gson()

    fun startSession(
        sharedPreferences: SharedPreferences,
        blockerNames: List<String>,
        scheduleId: String? = null,
        durationMinutes: Int = 0,
        breaksEnabled: Boolean = true,
        scheduleEndTimeMillis: Long? = null,
        /** The talisman whose tap started this session, if one did. */
        talismanId: String? = null
    ) {
        val now = System.currentTimeMillis()
        sharedPreferences.edit {
            putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(blockerNames))
            if (talismanId != null) putString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, talismanId)
            else remove(Constants.PrefsKeys.ACTIVE_TALISMAN_ID)
            putLong(Constants.PrefsKeys.SESSION_START_TIME, now)
            putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
            putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, breaksEnabled)
            // A new session always starts outside any leftover break state
            putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
            putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0)
            putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)
            remove(Constants.PrefsKeys.BREAK_END_TIME_MILLIS)
            // Extra-break perk tokens are session-scoped; never inherit one
            remove(Constants.PrefsKeys.EXTRA_BREAK_TOKENS)
            BreakClock.reset(this)

            if (scheduleId != null) {
                // Running again, so no longer "ended early".
                writeDismissedRituals(this, dismissedRituals(sharedPreferences, now) - scheduleId)
                putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, scheduleId)
                remove(Constants.PrefsKeys.FOCUS_DURATION_MINUTES)
                remove(Constants.PrefsKeys.FOCUS_TIME_REMAINING)
                remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
                // Snapshot the window end so a missed deactivation (service dead when
                // the ritual ended) can't credit idle hours as focus time.
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
        sharedPreferences: SharedPreferences,
        gson: Gson
    ): RecordResult {
        // A ritual ended while its window is still open (its talisman, the
        // stop button, the emergency stop) stays ended for the rest of that
        // window. Without the note, anything that brings rituals back in line
        // with the schedule table — the service reconnecting, the alarm
        // backstop, a reboot, a clock correction — would start it again.
        val now = System.currentTimeMillis()
        val endedScheduleId = sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        val windowEnd = sharedPreferences.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L)
        val dismissed = dismissedRituals(sharedPreferences, now).let {
            if (endedScheduleId != null && windowEnd > now) it + (endedScheduleId to windowEnd) else it
        }

        // Record completed session (and run the progression award step) before
        // the session prefs it reads are cleared below.
        val result: RecordResult? = SessionRecorder.record(sharedPreferences, gson)

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

        // Null tolerance is for unit tests that static-mock SessionRecorder.
        val recordResult = result ?: RecordResult(emptyList())
        ProgressionNotifier.postTrialCompletions(context, sharedPreferences, recordResult.completedTrials)
        return recordResult
    }

    fun isSessionActive(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
    }

    /** True while any focus session is running — timed, ritual or talisman. */
    fun isFocusActive(sharedPreferences: SharedPreferences): Boolean = isSessionActive(sharedPreferences)

    /** The talisman that started the running session, if one did. */
    fun activeTalismanId(sharedPreferences: SharedPreferences): String? =
        sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, null)

    /**
     * True when the ritual [scheduleId] was ended early and the window it was
     * ended in is still open at [now] — it must not be started again until
     * its next window.
     */
    fun isRitualDismissed(
        sharedPreferences: SharedPreferences,
        scheduleId: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = scheduleId in dismissedRituals(sharedPreferences, now)

    /** Early-ended rituals whose window is still open at [now]: id → window end. */
    private fun dismissedRituals(sharedPreferences: SharedPreferences, now: Long): Map<String, Long> {
        val json = sharedPreferences.getString(Constants.PrefsKeys.DISMISSED_RITUALS, null)
            ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson<Map<String, Long>?>(json, type).orEmpty().filterValues { it > now }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeDismissedRituals(editor: SharedPreferences.Editor, dismissed: Map<String, Long>) {
        if (dismissed.isEmpty()) editor.remove(Constants.PrefsKeys.DISMISSED_RITUALS)
        else editor.putString(Constants.PrefsKeys.DISMISSED_RITUALS, gson.toJson(dismissed))
    }
}
