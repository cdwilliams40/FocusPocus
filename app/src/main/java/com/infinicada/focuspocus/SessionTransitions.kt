package com.infinicada.focuspocus

import android.content.SharedPreferences
import com.infinicada.focuspocus.data.SessionStore
import com.infinicada.focuspocus.model.Schedule

/**
 * When the next timed session transition is due: a break ending, a timed
 * (non-ritual) session expiring, or a Pomodoro auto-break starting.
 *
 * Delegates to the pure [SessionRules.nextDueMillis] so the accessibility
 * service's handler wakeup and the unified AlarmManager scheduler always agree.
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
        Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES,
        Constants.PrefsKeys.BREAKS_USED_THIS_SESSION,
        Constants.PrefsKeys.MAX_BREAKS_PER_SESSION,
        Constants.PrefsKeys.EXTRA_BREAK_TOKENS
    )

    /**
     * Earliest transition strictly after [now], or null when nothing is pending.
     */
    fun nextDueMillis(
        prefs: SharedPreferences,
        now: Long,
        activeSchedule: Schedule? = null
    ): Long? {
        val state = SessionStore.read(prefs)
        val policy = SessionStore.readBreakPolicy(prefs, activeSchedule)
        return SessionRules.nextDueMillis(state, policy, now)
    }
}
