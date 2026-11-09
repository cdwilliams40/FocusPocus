package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.GuardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayTicksTest {

    @Test
    fun `countdown second wakes just after the floor value drops`() {
        // 5.300 s left shows 5; it shows 4 once under 5.000 s, i.e. 301 ms on.
        assertEquals(301L, DisplayTicks.untilCountdownSecondChanges(5_300L))
        // Exactly on a boundary: one millisecond later it drops.
        assertEquals(1L, DisplayTicks.untilCountdownSecondChanges(5_000L))
        assertEquals(1_000L, DisplayTicks.untilCountdownSecondChanges(4_999L))
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
