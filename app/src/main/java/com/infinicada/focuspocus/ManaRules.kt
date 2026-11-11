package com.infinicada.focuspocus

/**
 * Every number in the mana economy, in one place. The "How mana works"
 * section of the Mana screen is drawn from these, so the rules the user
 * reads are the rules the code applies.
 */
object ManaRules {
    // ── Earning ──

    /** A timed session (a length chosen up front): mana per focused minute… */
    const val TIMED_MANA_PER_MINUTE = 1L
    /** …up to this much a day across all timed sessions. */
    const val TIMED_DAILY_CAP = 240L

    /** An open-ended session (a ritual, or a talisman with no length) pays a flat amount… */
    const val OPEN_ENDED_MANA = 30L
    /** …once a day, once a session has run this long. */
    const val OPEN_ENDED_MIN_MINUTES = 60

    /** Time in a productive app: one mana per this many minutes… */
    const val PRODUCTIVE_MINUTES_PER_MANA = 2
    /** …up to this much a day. */
    const val PRODUCTIVE_DAILY_CAP = 60L

    // ── Spending ──

    /** Minutes one pour adds to the Mana Well. */
    const val POUR_MINUTES = 5
    /** The day's first pour; every further pour that day costs double the last. */
    const val POUR_BASE_COST = 75L
    /** One more break in the running session. */
    const val EXTRA_BREAK_COST = 50L

    // ── Trials ──

    const val DAILY_TRIAL_MIN_REWARD = 40L
    const val DAILY_TRIAL_MAX_REWARD = 60L
    const val WEEKLY_TRIAL_MIN_REWARD = 150L
    const val WEEKLY_TRIAL_MAX_REWARD = 200L

    /**
     * Mana for one session. [timedEarnedToday] is what timed sessions have
     * already paid today; [openEndedPaidToday] whether the day's open-ended
     * payment has been made.
     */
    fun sessionAward(
        session: FocusSession,
        timedEarnedToday: Long,
        openEndedPaidToday: Boolean
    ): SessionAward {
        if (session.durationMinutes < 1) return SessionAward(0L, timed = isTimed(session))
        return if (isTimed(session)) {
            val room = (TIMED_DAILY_CAP - timedEarnedToday).coerceAtLeast(0L)
            SessionAward(minOf(room, session.durationMinutes * TIMED_MANA_PER_MINUTE), timed = true)
        } else {
            val pays = !openEndedPaidToday && session.durationMinutes >= OPEN_ENDED_MIN_MINUTES
            SessionAward(if (pays) OPEN_ENDED_MANA else 0L, timed = false)
        }
    }

    data class SessionAward(val mana: Long, val timed: Boolean)

    /**
     * Timed: started with a chosen length, by hand or by a talisman. A ritual
     * always counts as open-ended, whatever its window.
     */
    fun isTimed(session: FocusSession): Boolean =
        session.source != FocusSession.SOURCE_RITUAL && (session.plannedMinutes ?: 0) > 0

    /** Mana a day's productive minutes are worth, before anything already paid. */
    fun productiveMana(minutesToday: Int): Long =
        minOf(PRODUCTIVE_DAILY_CAP, (minutesToday / PRODUCTIVE_MINUTES_PER_MANA).toLong())

    /**
     * Price of the next pour today, given how many were already bought: the
     * base price doubled once per earlier pour. The doubling is capped so the
     * shift can never overflow — long before then it is out of reach.
     */
    fun pourCost(poursToday: Int): Long =
        POUR_BASE_COST shl poursToday.coerceIn(0, MAX_DOUBLINGS)

    const val MAX_DOUBLINGS = 20
}
