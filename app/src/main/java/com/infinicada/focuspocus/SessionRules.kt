package com.infinicada.focuspocus

/**
 * Events emitted by [SessionRules.advance] when time crosses a session boundary.
 */
sealed class SessionTransition {
    data object BreakEnded : SessionTransition()
    data object SessionExpired : SessionTransition()
    data class AutoBreakStarted(val focusedMinutes: Int, val breakMinutes: Int) : SessionTransition()
}

data class AdvanceResult(
    val state: SessionState,
    val events: List<SessionTransition> = emptyList()
)

/**
 * Pure session state transition rules — Android-free and clock-injected so
 * ViewModel, AccessibilityService, and AlarmReceiver all share one source of truth.
 */
object SessionRules {

    /** Focus countdown seconds remaining at [nowMillis] (frozen while on break). */
    fun effectiveFocusTimeRemainingSeconds(state: SessionState, nowMillis: Long): Int {
        if (state !is SessionState.Active) return 0
        if (state.isOnBreak) return state.focusTimeRemainingSeconds.coerceAtLeast(0)
        if (state.focusEndTimeMillis <= 0L) return state.focusTimeRemainingSeconds.coerceAtLeast(0)
        return DisplayTicks.countdownSeconds(state.focusEndTimeMillis - nowMillis)
    }

    /** Break countdown seconds remaining at [nowMillis]. */
    fun effectiveBreakTimeRemainingSeconds(state: SessionState, nowMillis: Long): Int {
        if (state !is SessionState.Active) return 0
        val breakState = state.breakState ?: return 0
        if (breakState.endTimeMillis <= 0L) return breakState.remainingSeconds.coerceAtLeast(0)
        return DisplayTicks.countdownSeconds(breakState.endTimeMillis - nowMillis)
    }

    /** Effective maximum breaks for [state] under [policy], including purchased tokens. */
    fun effectiveMaxBreaks(state: SessionState.Active, policy: SessionBreakPolicy): Int {
        val base = (policy.scheduleMaxBreaksPerSession ?: policy.maxBreaksPerSession).coerceAtLeast(1)
        return base + state.extraBreakTokens.coerceAtLeast(0)
    }

    /** Whether breaks are allowed for [state] under [policy]. */
    fun areBreaksAllowed(state: SessionState.Active, policy: SessionBreakPolicy): Boolean {
        return policy.scheduleBreaksEnabled ?: state.breaksEnabled
    }

    /**
     * Starts a break on an active session: freezes the focus countdown at its
     * current wall-clock value, stamps the break window, and increments [SessionState.Active.breaksUsed].
     */
    fun startBreak(
        state: SessionState.Active,
        breakDurationMinutes: Int,
        nowMillis: Long
    ): SessionState.Active {
        val durationMin = breakDurationMinutes.coerceAtLeast(1)
        val frozenFocusRemaining = if (state.scheduleId == null && state.focusEndTimeMillis > 0L) {
            DisplayTicks.countdownSeconds(state.focusEndTimeMillis - nowMillis)
        } else {
            state.focusTimeRemainingSeconds.coerceAtLeast(0)
        }
        val breakStart = if (state.breakState != null && state.breakState.startTimeMillis > 0L) {
            state.breakState.startTimeMillis
        } else {
            nowMillis
        }
        return state.copy(
            breaksUsed = state.breaksUsed + 1,
            focusTimeRemainingSeconds = frozenFocusRemaining,
            focusEndTimeMillis = 0L,
            breakState = SessionState.BreakState(
                startTimeMillis = breakStart,
                endTimeMillis = nowMillis + durationMin * 60_000L,
                remainingSeconds = durationMin * 60
            )
        )
    }

    /**
     * Ends a running break: restarts the timed focus end-time clock from the frozen
     * remaining seconds and starts a fresh uninterrupted focus stretch at [nowMillis].
     */
    fun endBreak(state: SessionState.Active, nowMillis: Long): SessionState.Active {
        val resumedFocusEnd = if (state.scheduleId == null && state.focusTimeRemainingSeconds > 0) {
            nowMillis + state.focusTimeRemainingSeconds * 1000L
        } else {
            0L
        }
        return state.copy(
            focusEndTimeMillis = resumedFocusEnd,
            focusSegmentStartMillis = nowMillis,
            breakState = null
        )
    }

    /**
     * Advances [state] to [nowMillis], resolving expired breaks, expired timed sessions,
     * and due Pomodoro auto-breaks.
     *
     * Ritual window expiry is intentionally handled separately by schedule reconciliation
     * because rituals depend on the full schedule table and dismissal rules.
     */
    fun advance(
        state: SessionState,
        policy: SessionBreakPolicy,
        nowMillis: Long
    ): AdvanceResult {
        if (state !is SessionState.Active) return AdvanceResult(SessionState.Idle)

        // 1. Running break check
        val breakState = state.breakState
        if (breakState != null) {
            return if (breakState.endTimeMillis in 1..nowMillis) {
                AdvanceResult(
                    state = endBreak(state, nowMillis),
                    events = listOf(SessionTransition.BreakEnded)
                )
            } else {
                // Still on break: focus countdown is frozen and auto-breaks don't fire.
                AdvanceResult(state)
            }
        }

        // 2. Timed non-ritual session expiry
        if (state.scheduleId == null && state.focusEndTimeMillis in 1..nowMillis) {
            return AdvanceResult(
                state = SessionState.Idle,
                events = listOf(SessionTransition.SessionExpired)
            )
        }

        // 3. Pomodoro auto-break check
        if (policy.autoBreakEnabled &&
            state.breaksEnabled &&
            policy.scheduleBreaksEnabled != false &&
            state.focusSegmentStartMillis > 0L
        ) {
            val maxBreaks = effectiveMaxBreaks(state, policy)
            if (state.breaksUsed < maxBreaks) {
                val intervalMinutes = policy.autoBreakIntervalMinutes.coerceIn(5, 60)
                if (nowMillis - state.focusSegmentStartMillis >= intervalMinutes * 60_000L) {
                    val breakDuration = (policy.scheduleBreakDurationMinutes ?: policy.breakDurationMinutes)
                        .coerceAtLeast(1)
                    return AdvanceResult(
                        state = startBreak(state, breakDuration, nowMillis),
                        events = listOf(SessionTransition.AutoBreakStarted(intervalMinutes, breakDuration))
                    )
                }
            }
        }

        return AdvanceResult(state)
    }

    /**
     * Earliest upcoming session transition strictly after [nowMillis], or null when none.
     * Past-due instants are deliberately skipped so wakeups cannot spin.
     */
    fun nextDueMillis(
        state: SessionState,
        policy: SessionBreakPolicy,
        nowMillis: Long
    ): Long? {
        if (state !is SessionState.Active) return null

        val breakState = state.breakState
        if (breakState != null) {
            return breakState.endTimeMillis.takeIf { it > nowMillis }
        }

        val candidates = mutableListOf<Long>()

        if (state.scheduleId == null && state.focusEndTimeMillis > 0L) {
            candidates += state.focusEndTimeMillis
        }

        if (policy.autoBreakEnabled &&
            state.breaksEnabled &&
            policy.scheduleBreaksEnabled != false &&
            state.focusSegmentStartMillis > 0L &&
            state.breaksUsed < effectiveMaxBreaks(state, policy)
        ) {
            val intervalMinutes = policy.autoBreakIntervalMinutes.coerceIn(5, 60)
            candidates += state.focusSegmentStartMillis + intervalMinutes * 60_000L
        }

        return candidates.filter { it > nowMillis }.minOrNull()
    }
}
