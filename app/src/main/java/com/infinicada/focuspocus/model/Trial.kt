package com.infinicada.focuspocus.model

enum class TrialType {
    /** Complete [Trial.target] focus sessions in the period. */
    COMPLETE_SESSIONS,

    /** Accumulate [Trial.target] focused minutes in the period. */
    FOCUS_MINUTES,

    /**
     * No daily-limit breaches during the period. Judged from BLOCK_EVENTS at
     * rollover (target 1, progress set to 1 on success) — usage history can't
     * be reconstructed after the fact, block events can.
     */
    STAY_UNDER_LIMITS,

    /**
     * At most [Trial.param] reflex opens across the period. Judged from
     * OpenReflexTracker history at rollover (target 1 on success).
     */
    NO_REFLEX_OPENS,

    /** Complete [Trial.target] scheduled rituals in the period. */
    COMPLETE_RITUAL
}

enum class TrialPeriod { DAILY, WEEKLY }

/**
 * One active (or just-ended) challenge. Titles are derived from
 * (type, target, param) via string resources at display time so persisted
 * state stays locale-independent.
 */
data class Trial(
    val id: String,
    val type: TrialType,
    val target: Int,
    val progress: Int = 0,
    val period: TrialPeriod,
    /** "yyyyMMdd" for DAILY, "yyyy-Www" (ISO week) for WEEKLY. */
    val periodKey: String,
    val rewardMana: Long,
    val claimed: Boolean = false,
    /** Extra threshold for types that need one (NO_REFLEX_OPENS: max reflexes). */
    val param: Int = 0
) {
    val completed: Boolean get() = progress >= target
}
