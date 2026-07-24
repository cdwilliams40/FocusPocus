package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.infinicada.focuspocus.model.Sigil
import com.infinicada.focuspocus.model.Trial

/**
 * Everything one call to [SessionRecorder.record] produced. Callers that only
 * ever cared about the session list keep working through [sessions]; the
 * enriched session-summary dialog and trial notifications read the rest.
 */
data class RecordResult(
    /** The updated (pruned) session list — empty when nothing was recorded. */
    val sessions: List<FocusSession>,
    /** The session that was just recorded, or null when it was discarded. */
    val recorded: FocusSession? = null,
    /** Mana earned by the session itself (0 when progression is disabled). */
    val manaEarned: Long = 0L,
    /** One-time streak milestone bonus paid out by this recording, if any. */
    val milestoneBonus: Long = 0L,
    val newStreak: Int = 0,
    /** Trials whose target was crossed by this session (unclaimed). */
    val completedTrials: List<Trial> = emptyList(),
    /** Sigils newly unlocked by this session. */
    val unlockedSigils: List<Sigil> = emptyList()
)

/**
 * Shared utility for recording completed focus sessions to SharedPreferences.
 */
object SessionRecorder {

    /**
     * Records the current focus session using state already stored in SharedPreferences
     * (SESSION_START_TIME, ACTIVE_BLOCKERS/ACTIVE_BLOCKER, BREAKS_USED_THIS_SESSION).
     *
     * Also updates LONGEST_STREAK if the new streak exceeds it, and runs the
     * progression award step (mana, trials, sigils, milestones) exactly once
     * per recorded session — this is the single choke point every session-stop
     * path funnels through.
     *
     * @return A [RecordResult]; its [RecordResult.recorded] is null if nothing
     *         was recorded (e.g. session was too short or start time was missing).
     */
    @Synchronized
    fun record(prefs: SharedPreferences, gson: Gson): RecordResult {
        val startTime = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)
        if (startTime == 0L) return RecordResult(emptyList())

        // A session that expired while nothing was running to stop it (service
        // disabled, app dead) is credited until its scheduled end, not until
        // whenever the stop finally executed — otherwise duration, mana and
        // sigils are inflated by however long the phone sat idle. Timed sessions
        // cap at FOCUS_END_TIME_MILLIS; ritual sessions at the schedule window
        // end snapshotted in SCHEDULE_END_TIME_MILLIS.
        val now = System.currentTimeMillis()
        val focusEnd = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        val scheduleEnd = prefs.getLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, 0L)
        val endTime = listOf(focusEnd, scheduleEnd).filter { it in 1 until now }.minOrNull() ?: now
        val durationMin = ((endTime - startTime) / 60000).toInt()
        if (durationMin < 1) return RecordResult(emptyList())

        val blockerName = run {
            val blockersJson = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
            if (blockersJson != null) {
                try {
                    val names = gson.fromJson(blockersJson, Array<String>::class.java)
                    names?.joinToString(", ")?.ifEmpty { null }
                } catch (e: Exception) { null }
            } else null
        } ?: prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: "Unknown"
        val breaksUsed = prefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 0)

        val session = FocusSession(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            durationMinutes = durationMin,
            blockerName = blockerName,
            breaksUsed = breaksUsed
        )

        val json = prefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        val sessions: MutableList<FocusSession> = if (json != null) {
            try {
                // Use Array deserialization to avoid TypeToken
                val array = gson.fromJson(json, Array<FocusSession>::class.java)
                array?.toMutableList() ?: mutableListOf()
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
            putString(Constants.PrefsKeys.FOCUS_SESSIONS, gson.toJson(pruned))
            remove(Constants.PrefsKeys.SESSION_START_TIME)
            if (newStreak > currentLongest) {
                putInt(Constants.PrefsKeys.LONGEST_STREAK, newStreak)
            }
        }

        // Award after the session list is committed, while ACTIVE_SCHEDULE_ID
        // and HIDE_STOP_BUTTON still describe this session (SessionManager
        // clears session prefs only after record() returns).
        val award = Progression.awardForSession(prefs, gson, session, newStreak)

        return RecordResult(
            sessions = pruned,
            recorded = session,
            manaEarned = award.manaEarned,
            milestoneBonus = award.milestoneBonus,
            newStreak = newStreak,
            completedTrials = award.completedTrials,
            unlockedSigils = award.unlockedSigils
        )
    }
}
