package com.infinicada.focuspocus

/**
 * Immutable snapshot of the running focus session (or [Idle] when none is running).
 *
 * Persisted 1:1 into the existing SharedPreferences keys by [SessionManager] and
 * observed reactively through [com.infinicada.focuspocus.data.SessionStore].
 */
sealed class SessionState {
    data object Idle : SessionState()

    data class BreakState(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val remainingSeconds: Int
    )

    data class Active(
        val blockerNames: List<String>,
        val startTimeMillis: Long,
        val plannedDurationMinutes: Int = 0,
        val focusEndTimeMillis: Long = 0L,
        val focusTimeRemainingSeconds: Int = 0,
        val scheduleId: String? = null,
        val scheduleEndTimeMillis: Long = 0L,
        val talismanId: String? = null,
        val breaksEnabled: Boolean = true,
        val breaksUsed: Int = 0,
        val extraBreakTokens: Int = 0,
        val focusSegmentStartMillis: Long = 0L,
        val breakState: BreakState? = null
    ) : SessionState() {
        val isOnBreak: Boolean get() = breakState != null
        val isRitual: Boolean get() = scheduleId != null
        val isTalisman: Boolean get() = talismanId != null && scheduleId == null
        val isTimed: Boolean
            get() = scheduleId == null && (focusEndTimeMillis > 0L || (isOnBreak && focusTimeRemainingSeconds > 0))
    }

    val isActive: Boolean get() = this is Active
}

/**
 * Break and auto-break policy settings consumed by [SessionRules].
 * Separates user/schedule configuration from the running [SessionState] so rules stay pure.
 */
data class SessionBreakPolicy(
    val autoBreakEnabled: Boolean = false,
    val autoBreakIntervalMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
    val maxBreaksPerSession: Int = 3,
    val scheduleBreaksEnabled: Boolean? = null,
    val scheduleBreakDurationMinutes: Int? = null,
    val scheduleMaxBreaksPerSession: Int? = null
)
