package com.infinicada.focuspocus

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Shared utility for recording completed focus sessions to SharedPreferences.
 * Replaces the duplicated recordSession() logic that previously existed in
 * WifiTriggerService, BluetoothTriggerReceiver, and MainActivity.
 */
object SessionRecorder {

    /**
     * Records the current focus session using state already stored in SharedPreferences
     * (SESSION_START_TIME, ACTIVE_BLOCKER, BREAKS_USED_THIS_SESSION).
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

        val blockerName = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null) ?: "Unknown"
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
                val type = object : TypeToken<MutableList<FocusSession>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) { mutableListOf() }
        } else mutableListOf()

        sessions.add(session)
        val pruned = if (sessions.size > 500) sessions.drop(sessions.size - 500) else sessions

        val newStreak = calculateCurrentStreak(pruned)
        val currentLongest = prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

        val editor = prefs.edit()
            .putString(Constants.PrefsKeys.FOCUS_SESSIONS, gson.toJson(pruned))
            .remove(Constants.PrefsKeys.SESSION_START_TIME)
        if (newStreak > currentLongest) {
            editor.putInt(Constants.PrefsKeys.LONGEST_STREAK, newStreak)
        }
        editor.apply()

        return pruned
    }
}
