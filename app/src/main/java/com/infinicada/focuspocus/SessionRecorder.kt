package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.model.Trial
import kotlinx.serialization.encodeToString

data class RecordResult(
    val sessions: List<FocusSession>,
    val recorded: FocusSession? = null,
    val manaEarned: Long = 0L,
    val newStreak: Int = 0,
    val completedTrials: List<Trial> = emptyList()
)

object SessionRecorder {

    @Synchronized
    fun record(prefs: SharedPreferences): RecordResult {
        val startTime = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)
        if (startTime == 0L) return RecordResult(emptyList())

        val now = System.currentTimeMillis()
        val focusEnd = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        val scheduleEnd = prefs.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L)
        val endTime = listOf(focusEnd, scheduleEnd).filter { it in 1 until now }.minOrNull() ?: now
        val breakMillis = BreakClock.breakMillisUntil(prefs, endTime)
        val durationMin = ((endTime - startTime - breakMillis) / 60000).toInt()
        if (durationMin < 1) return RecordResult(emptyList())

        val blockerName = run {
            val blockersJson = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
            if (blockersJson != null) {
                try {
                    AppJson.decodeListLenient<String>(blockersJson).joinToString(", ").ifEmpty { null }
                } catch (_: Exception) { null }
            } else null
        } ?: "Unknown"
        val breaksUsed = prefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)

        val source = when {
            prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null -> FocusSession.SOURCE_RITUAL
            prefs.getString(Constants.PrefsKeys.ACTIVE_TALISMAN_ID, null) != null -> FocusSession.SOURCE_TALISMAN
            else -> FocusSession.SOURCE_MANUAL
        }
        val session = FocusSession(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            durationMinutes = durationMin,
            blockerName = blockerName,
            breaksUsed = breaksUsed,
            source = source,
            plannedMinutes = prefs.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, 0).takeIf {
                it > 0 && source != FocusSession.SOURCE_RITUAL
            }
        )

        val json = prefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        val sessions: MutableList<FocusSession> = if (json != null) {
            try {
                AppJson.decodeListLenient<FocusSession>(json).toMutableList()
            } catch (e: Exception) {
                Log.e("SessionRecorder", "Error parsing focus sessions JSON", e)
                mutableListOf()
            }
        } else mutableListOf()

        sessions.add(session)
        val pruned = if (sessions.size > 500) sessions.takeLast(500) else sessions

        val newStreak = calculateCurrentStreak(pruned)
        val currentLongest = prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

        prefs.edit {
            putString(Constants.PrefsKeys.FOCUS_SESSIONS, AppJson.json.encodeToString(pruned))
            remove(Constants.PrefsKeys.SESSION_START_TIME)
            if (newStreak > currentLongest) {
                putInt(Constants.PrefsKeys.LONGEST_STREAK, newStreak)
            }
        }

        ManaWell.onFocusSessionRecorded(
            prefs = prefs,
            durationMinutes = session.durationMinutes,
            now = endTime
        )

        val award = Progression.awardForSession(prefs, session)

        return RecordResult(
            sessions = pruned,
            recorded = session,
            manaEarned = award.manaEarned,
            newStreak = newStreak,
            completedTrials = award.completedTrials
        )
    }
}
