package com.infinicada.focuspocus

/**
 * What the home-screen widget should say, derived from persisted state alone.
 *
 * Split out from [FocusWidgetProvider] so the decision — which of four things
 * is worth reporting, and the rounding on the countdown — is unit tested
 * without an AppWidgetManager. The provider does nothing but turn a [Snapshot]
 * into strings.
 */
object FocusWidgetState {

    enum class Kind {
        /** A session is running and blocking. */
        FOCUSING,

        /** A session is running but paused for a break, so nothing is blocked. */
        ON_BREAK,

        /**
         * No session, but standing guards are holding apps shut. Worth its own
         * state: pacts are the app's home screen, and "nothing is happening" is
         * wrong when three apps are sealed.
         */
        SEALED,

        /** Nothing is holding. */
        IDLE
    }

    data class Snapshot(
        val kind: Kind,
        /** Whole minutes left, rounded up; 0 for an untimed session. */
        val minutesRemaining: Int = 0,
        /** Apps currently sealed by a guard, for [Kind.SEALED]. */
        val sealedCount: Int = 0
    )

    fun of(
        sessionActive: Boolean,
        onBreak: Boolean,
        focusEndMillis: Long,
        breakEndMillis: Long,
        sealedCount: Int,
        now: Long = System.currentTimeMillis()
    ): Snapshot = when {
        sessionActive && onBreak ->
            Snapshot(Kind.ON_BREAK, minutesRemaining = minutesUntil(breakEndMillis, now))
        sessionActive ->
            Snapshot(Kind.FOCUSING, minutesRemaining = minutesUntil(focusEndMillis, now))
        sealedCount > 0 ->
            Snapshot(Kind.SEALED, sealedCount = sealedCount)
        else -> Snapshot(Kind.IDLE)
    }

    /**
     * Whole minutes until [endMillis], rounded up so the last partial minute
     * still reads as "1m" rather than "0m". Zero means no end time is set —
     * an untimed session — not "it just ended".
     */
    internal fun minutesUntil(endMillis: Long, now: Long): Int {
        if (endMillis <= 0L) return 0
        val remaining = endMillis - now
        if (remaining <= 0L) return 0
        return ((remaining + 59_999L) / 60_000L).toInt()
    }
}
