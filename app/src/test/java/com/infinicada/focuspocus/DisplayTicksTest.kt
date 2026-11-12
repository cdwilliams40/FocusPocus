package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.GuardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayTicksTest {

    @Test
    fun `countdown seconds round up and read zero only once the end has passed`() {
        assertEquals(1_500, DisplayTicks.countdownSeconds(25 * 60_000L))
        assertEquals(900, DisplayTicks.countdownSeconds(899_500L))
        assertEquals(1, DisplayTicks.countdownSeconds(999L))
        assertEquals(1, DisplayTicks.countdownSeconds(1L))
        assertEquals(0, DisplayTicks.countdownSeconds(0L))
        assertEquals(0, DisplayTicks.countdownSeconds(-5_000L))
    }

    @Test
    fun `countdown second wakes exactly when the shown value drops`() {
        // 5.300 s left shows 6; it shows 5 at 5.000 s left, 300 ms on.
        assertEquals(300L, DisplayTicks.untilCountdownSecondChanges(5_300L))
        // Exactly on a boundary: the value holds for a full second.
        assertEquals(1_000L, DisplayTicks.untilCountdownSecondChanges(5_000L))
        assertEquals(999L, DisplayTicks.untilCountdownSecondChanges(4_999L))
        // The last wake of a countdown lands on its end, never before it.
        val wait = DisplayTicks.untilCountdownSecondChanges(700L)
        assertEquals(700L, wait)
        assertEquals(1, DisplayTicks.countdownSeconds(700L - wait + 1L))
        assertEquals(0, DisplayTicks.countdownSeconds(700L - wait))
    }

    @Test
    fun `countdown second never returns a non-positive wait`() {
        assertEquals(1_000L, DisplayTicks.untilCountdownSecondChanges(0L))
        assertEquals(1_000L, DisplayTicks.untilCountdownSecondChanges(-250L))
    }

    @Test
    fun `elapsed second wakes on the next whole second`() {
        assertEquals(1_000L, DisplayTicks.untilElapsedSecondChanges(0L))
        assertEquals(700L, DisplayTicks.untilElapsedSecondChanges(12_300L))
        assertEquals(1L, DisplayTicks.untilElapsedSecondChanges(12_999L))
    }

    @Test
    fun `countdown minute tracks rounded-up minutesUntil`() {
        val now = 1_700_000_000_000L
        val expiry = now + 4 * 60_000L + 30_000L
        val wait = DisplayTicks.untilCountdownMinuteChanges(expiry, now)!!
        assertEquals(30_000L, wait)
        // The wait lands exactly where GuardStatus.minutesUntil steps down.
        assertEquals(5, GuardStatus.minutesUntil(expiry, now + wait - 1))
        assertEquals(4, GuardStatus.minutesUntil(expiry, now + wait))
    }

    @Test
    fun `countdown minute's last change is the expiry itself`() {
        val now = 1_700_000_000_000L
        assertEquals(45_000L, DisplayTicks.untilCountdownMinuteChanges(now + 45_000L, now))
        assertEquals(60_000L, DisplayTicks.untilCountdownMinuteChanges(now + 60_000L, now))
        assertNull(DisplayTicks.untilCountdownMinuteChanges(now, now))
        assertNull(DisplayTicks.untilCountdownMinuteChanges(now - 1L, now))
    }

    @Test
    fun `wall-clock minute lands on the boundary`() {
        val minuteStart = 1_700_000_040_000L // a multiple of 60 000
        assertEquals(60_000L, DisplayTicks.untilNextWallClockMinute(minuteStart))
        assertEquals(15_000L, DisplayTicks.untilNextWallClockMinute(minuteStart + 45_000L))
    }
}
