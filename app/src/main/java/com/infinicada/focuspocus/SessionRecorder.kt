package com.infinicada.focuspocus

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson

/**
 * Shared utility for recording completed focus sessions to SharedPreferences.
 */
object SessionRecorder {

    /**
     * Records the current focus session using state already stored in SharedPreferences
     * (SESSION_START_TIME, ACTIVE_BLOCKERS/ACTIVE_BLOCKER, BREAKS_USED_THIS_SESSION).
     *
     * Also updates LONGEST_STREAK if the new streak exceeds it.
     *
     * @return The updated session list, or an empty list if nothing was recorded
     *         (e.g. session was too short or start time was missing).
     */
    @Synchronized
    fun record(prefs: SharedPreferences, gson: Gson): List<FocusSession> {
        val startTime = prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L)
        if (startTime == 0L) return emptyList()

        val endTime = System.currentTimeMillis()
        val durationMin = ((endTime - startTime) / 60000).toInt()
        if (durationMin < 1) return emptyList()

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

        val editor = prefs.edit()
            .putString(Constants.PrefsKeys.FOCUS_SESSIONS, gson.toJson(pruned))
            .remove(Constants.PrefsKeys.SESSION_START_TIME)
        if (newStreak > currentLongest) {
            editor.putInt(Constants.PrefsKeys.LONGEST_STREAK, newStreak)
        }
        editor.commit()

        return pruned
    }
}
