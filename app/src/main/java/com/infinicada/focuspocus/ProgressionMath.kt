package com.infinicada.focuspocus

import kotlin.math.floor
import kotlin.math.min

/**
 * Pure progression math, kept free of Android dependencies so it unit-tests
 * like calculateCurrentStreak. All economy tuning constants live here.
 */
object ProgressionMath {
    /** Base earn rate: 1 mana per focused minute. */
    const val SESSION_MANA_CAP = 240
    const val HIDDEN_STOP_MULTIPLIER = 1.25
    const val STREAK_BONUS_PER_DAY = 0.02
    const val STREAK_BONUS_MAX_DAYS = 25
    const val RITUAL_BONUS_MANA = 20L

    /** Streak day thresholds that pay a one-time mana bonus. */
    val STREAK_MILESTONES = listOf(7, 30, 100)
    val STREAK_MILESTONE_MANA = mapOf(7 to 100L, 30 to 500L, 100 to 2000L)

    /**
     * Mana earned by a completed session. The per-session cap keeps an 8-hour
     * Sleep Mode or an unlimited overnight session from minting a fortune;
     * everything else rewards the behaviors the app already treats as harder.
     */
    fun computeManaAward(
        durationMinutes: Int,
        newStreak: Int,
        hideStopButton: Boolean,
        fromRitual: Boolean
    ): Long {
        if (durationMinutes < 1) return 0L
        var mana = min(durationMinutes, SESSION_MANA_CAP).toDouble()
        if (hideStopButton) mana *= HIDDEN_STOP_MULTIPLIER
        mana *= 1.0 + STREAK_BONUS_PER_DAY * min(newStreak, STREAK_BONUS_MAX_DAYS)
        var result = floor(mana).toLong()
        if (fromRitual) result += RITUAL_BONUS_MANA
        return result
    }

    /**
     * One-time bonus for streak milestones crossed by [newStreak] that were
     * never paid before ([highestPaid] is the high-water mark). Returns the
     * total bonus and the new high-water mark.
     */
    fun milestoneBonus(newStreak: Int, highestPaid: Int): Pair<Long, Int> {
        var total = 0L
        var paid = highestPaid
        for (m in STREAK_MILESTONES) {
            if (newStreak >= m && m > highestPaid) {
                total += STREAK_MILESTONE_MANA[m] ?: 0L
                paid = m
            }
        }
        return total to paid
    }

    /**
     * Whether the evening wrap-up notification should fire. Pure so the
     * accessibility service's minute tick stays untestable-free: the cheap
     * [lastSessionRecordedDate] string comparison short-circuits inactive days
     * before any JSON is parsed.
     */
    fun shouldSendWrapup(
        hourOfDay: Int,
        todayKey: String,
        lastWrapupDate: String?,
        lastSessionRecordedDate: String?,
        wrapupEnabled: Boolean,
        progressionEnabled: Boolean
    ): Boolean {
        if (!wrapupEnabled || !progressionEnabled) return false
        if (hourOfDay < WRAPUP_HOUR) return false
        if (lastWrapupDate == todayKey) return false
        return lastSessionRecordedDate == todayKey
    }

    /** Local hour (24h) after which the daily wrap-up may fire. */
    const val WRAPUP_HOUR = 20
}
