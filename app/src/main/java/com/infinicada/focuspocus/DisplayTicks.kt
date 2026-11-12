package com.infinicada.focuspocus

/**
 * How long a display ticker should sleep so it wakes exactly when the value
 * it shows next changes, rather than on a fixed period from an arbitrary
 * phase. A fixed `delay(1000)` started mid-second shows each value up to a
 * second late and, as the loop's own overhead accumulates, now and then
 * skips one; a fixed minute tick can show an ended pact as "1 min" for up
 * to a minute after it ended.
 *
 * All results are in 1..period, so a ticker can never busy-spin.
 */
object DisplayTicks {
    private const val SECOND = 1_000L
    private const val MINUTE = 60_000L

    /**
     * A countdown's whole seconds left, rounded up: 25:00 through the first
     * second, 0:01 through the last, and 0 only once the end has passed.
     *
     * Rounding up is what lets "the countdown reads 0" mean "it is over".
     * Rounded down it read 0 for the whole last second, so the session was
     * stopped up to a second early — and parked a part second short at every
     * break — and the recorder then counted a full 25 minutes as 24.
     */
    fun countdownSeconds(msLeft: Long): Int =
        if (msLeft <= 0L) 0 else ((msLeft + SECOND - 1L) / SECOND).toInt()

    /** For a countdown shown as [countdownSeconds]: millis until it drops. */
    fun untilCountdownSecondChanges(msLeft: Long): Long =
        if (msLeft <= 0L) SECOND else (msLeft - 1L) % SECOND + 1L

    /** For an elapsed time shown as whole seconds, `floor(msElapsed / 1000)`: millis until it rises. */
    fun untilElapsedSecondChanges(msElapsed: Long): Long =
        SECOND - Math.floorMod(msElapsed, SECOND)

    /**
     * For a countdown shown as whole minutes, rounded up
     * (GuardStatus.minutesUntil): millis until it changes — down a minute,
     * or to "passed" at [expiryMillis] itself. Null once already passed.
     */
    fun untilCountdownMinuteChanges(expiryMillis: Long, now: Long): Long? {
        val msLeft = expiryMillis - now
        if (msLeft <= 0L) return null
        return (msLeft - 1L) % MINUTE + 1L
    }

    /** Millis until the next wall-clock minute boundary. */
    fun untilNextWallClockMinute(now: Long): Long =
        MINUTE - Math.floorMod(now, MINUTE)
}
