package com.infinicada.focuspocus

import android.content.SharedPreferences

/**
 * Tracks how much of a session was spent on break, so the recorded focus
 * duration (and the mana, trials and sigils it drives) counts only time
 * spent under enforcement. Every edit that turns a break on or off calls
 * [markStarted] / [markEnded] inside the same editor block; the recorder
 * reads the total through [breakMillisUntil].
 */
object BreakClock {

    /** Stamps a break's start, unless one is already running (a restarted break keeps its start). */
    fun markStarted(prefs: SharedPreferences, editor: SharedPreferences.Editor, now: Long) {
        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false) &&
            prefs.getLong(Constants.PrefsKeys.BREAK_START_TIME_MILLIS, 0L) > 0L
        ) return
        editor.putLong(Constants.PrefsKeys.BREAK_START_TIME_MILLIS, now)
    }

    /** Banks the running break's length into the session total and clears its start. */
    fun markEnded(prefs: SharedPreferences, editor: SharedPreferences.Editor, now: Long) {
        val start = prefs.getLong(Constants.PrefsKeys.BREAK_START_TIME_MILLIS, 0L)
        if (start > 0L) {
            val total = prefs.getLong(Constants.PrefsKeys.SESSION_BREAK_MILLIS, 0L) +
                (now - start).coerceAtLeast(0L)
            editor.putLong(Constants.PrefsKeys.SESSION_BREAK_MILLIS, total)
        }
        editor.remove(Constants.PrefsKeys.BREAK_START_TIME_MILLIS)
    }

    /** Session boundary: forgets all break bookkeeping. */
    fun reset(editor: SharedPreferences.Editor) {
        editor.remove(Constants.PrefsKeys.BREAK_START_TIME_MILLIS)
        editor.remove(Constants.PrefsKeys.SESSION_BREAK_MILLIS)
    }

    /**
     * Break time in the current session up to [endTime], including a break
     * still running then. Time after a break's scheduled end but before the
     * resume was noticed counts as break: nothing was blocked during it.
     */
    fun breakMillisUntil(prefs: SharedPreferences, endTime: Long): Long {
        var total = prefs.getLong(Constants.PrefsKeys.SESSION_BREAK_MILLIS, 0L)
        val start = prefs.getLong(Constants.PrefsKeys.BREAK_START_TIME_MILLIS, 0L)
        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false) && start in 1 until endTime) {
            total += endTime - start
        }
        return total.coerceAtLeast(0L)
    }
}
