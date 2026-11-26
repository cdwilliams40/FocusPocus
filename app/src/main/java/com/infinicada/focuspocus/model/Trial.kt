package com.infinicada.focuspocus.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrialType {
    /** Legacy (before 2.0): complete [Trial.target] focus sessions. Never drawn now. */
    COMPLETE_SESSIONS,

    /** Accumulate [Trial.target] minutes of timed focus sessions in the period. */
    FOCUS_MINUTES,

    /** Start [Trial.target] timed sessions by hand in the period. */
    START_TIMED_SESSION,

    /**
     * Draw at most [Trial.param] minutes from the Mana Well across the period.
     * Judged at rollover from the Well's day history (target 1 on success).
     */
    WELL_UNDER,

    /** Spend [Trial.target] minutes in productive apps in the period. */
    PRODUCTIVE_MINUTES,

    /** Choose a healthier substitute [Trial.target] times in the period. */
    SUBSTITUTE_WINS,

    /**
     * Legacy (before 2.0, never drawn now): no daily-limit breaches. Judged from BLOCK_EVENTS at
     * rollover (target 1, progress set to 1 on success) — usage history can't
     * be reconstructed after the fact, block events can.
     */
    STAY_UNDER_LIMITS,

    /** Legacy (removed): never drawn now. Kept so persisted records deserialize cleanly. */
    NO_REFLEX_OPENS,

    /** Legacy (before 2.0): complete scheduled rituals. Never drawn now. */
    COMPLETE_RITUAL
}

@Serializable
enum class TrialPeriod { DAILY, WEEKLY }

/**
 * One active (or just-ended) challenge. Titles are derived from
 * (type, target, param) via string resources at display time so persisted
 * state stays locale-independent.
 */
@Serializable
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
    /** Extra threshold for types that need one (WELL_UNDER: max minutes drawn). */
    val param: Int = 0
) {
    val completed: Boolean get() = progress >= target
}
