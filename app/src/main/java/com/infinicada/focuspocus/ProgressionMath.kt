package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.Perk
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
    const val RITUAL_BONUS_MANA = 20L

    /**
     * Mana earned by a completed session. The per-session cap keeps an 8-hour
     * Sleep Mode or an unlimited overnight session from minting a fortune;
     * everything else rewards the behaviors the app already treats as harder.
     */
    fun computeManaAward(
        durationMinutes: Int,
        hideStopButton: Boolean,
        fromRitual: Boolean
    ): Long {
        if (durationMinutes < 1) return 0L
        var mana = min(durationMinutes, SESSION_MANA_CAP).toDouble()
        if (hideStopButton) mana *= HIDDEN_STOP_MULTIPLIER
        var result = floor(mana).toLong()
        if (fromRitual) result += RITUAL_BONUS_MANA
        return result
    }

    /**
     * Mana price of the next Well channel today, given how many were already
     * bought today: the base price doubled once per earlier channel. The
     * doubling is capped so the shift can never overflow — long before then
     * the price is out of anyone's reach.
     */
    fun wellChannelCost(channelsToday: Int): Long =
        Perk.WELL_CHANNEL.costMana shl channelsToday.coerceIn(0, WELL_CHANNEL_MAX_DOUBLINGS)

    const val WELL_CHANNEL_MAX_DOUBLINGS = 20
}
