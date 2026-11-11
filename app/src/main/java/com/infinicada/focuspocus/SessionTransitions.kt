package com.infinicada.focuspocus

import android.content.SharedPreferences

/**
 * When the next timed session transition is due: a break ending, a timed
 * (non-ritual) session expiring, or a Pomodoro auto-break starting.
 *
 * The accessibility service acts on these on its minute tick, which used to
 * leave each one up to a minute late — the notification chronometer ran past
 * zero, blocking stayed off after a break, and the UI (when not in front)
 * lagged by the same amount. The service now also arms a one-shot wakeup for
 * this instant. The minute tick stays as the backstop, so this only has to
 * be a close-enough prediction: a wakeup whose conditions turn out not to
 * hold (e.g. auto-break quota spent) is a harmless no-op.
 */
object SessionTransitions {

    /** Keys whose change can move the next transition; the service re-arms on them. */
    val WATCHED_KEYS = setOf(
        Constants.PrefsKeys.IS_ON_BREAK,
        Constants.PrefsKeys.BREAK_END_TIME_MILLIS,
        Constants.PrefsKeys.FOCUS_END_TIME_MILLIS,
        Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS,
        Constants.PrefsKeys.MANUAL_FOCUS_MODE,
        Constants.PrefsKeys.ACTIVE_SCHEDULE_ID,
        Constants.PrefsKeys.SESSION_BREAKS_ENABLED,
        Constants.PrefsKeys.AUTO_BREAK_ENABLED,
        Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES
    )

    /**
     * Earliest transition strictly after [now], or null when nothing is
     * pending. Past-due instants are deliberately skipped: the wakeup that
     * covers them has already run (or the minute tick will), and returning
     * one whose conditions don't hold would spin.
     */
    fun nextDueMillis(prefs: SharedPreferences, now: Long): Long? {
        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) {
            // On a break the focus countdown is frozen; only the break end matters.
            return prefs.getLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, 0L)
                .takeIf { it > now }
        }

        val candidates = mutableListOf<Long>()

        // Timed manual session end (rituals end via checkSchedules / the ritual alarm).
        if (prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false) &&
            prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) == null
        ) {
            candidates += prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        }

        // Pomodoro auto-break, mirroring the cheap gates of maybeStartAutoBreak.
        if (prefs.getBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, false) &&
            prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true) &&
            SessionManager.isFocusActive(prefs)
        ) {
            val segmentStart = prefs.getLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, 0L)
            if (segmentStart > 0L) {
                val interval = prefs.getInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 25)
                    .coerceIn(5, 60)
                candidates += segmentStart + interval * 60_000L
            }
        }

        return candidates.filter { it > now }.minOrNull()
    }
}
