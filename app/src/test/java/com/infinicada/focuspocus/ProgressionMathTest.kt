package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionMathTest {

    // ── computeManaAward ──

    @Test
    fun `base award is one mana per minute`() {
        assertEquals(25L, ProgressionMath.computeManaAward(25, hideStopButton = false, fromRitual = false))
    }

    @Test
    fun `zero and negative durations award nothing`() {
        assertEquals(0L, ProgressionMath.computeManaAward(0, true, true))
        assertEquals(0L, ProgressionMath.computeManaAward(-10, true, true))
    }

    @Test
    fun `session cap stops overnight farming`() {
        // 480-minute Sleep Mode caps at 240 base
        assertEquals(240L, ProgressionMath.computeManaAward(480, false, false))
    }

    @Test
    fun `hidden stop button pays 25 percent more`() {
        assertEquals(125L, ProgressionMath.computeManaAward(100, hideStopButton = true, fromRitual = false))
    }

    @Test
    fun `ritual bonus is a flat 20 after the multiplier`() {
        assertEquals(120L, ProgressionMath.computeManaAward(100, false, fromRitual = true))
    }

    @Test
    fun `bonuses stack`() {
        // min(300,240)=240; *1.25=300; +20=320
        assertEquals(320L, ProgressionMath.computeManaAward(300, hideStopButton = true, fromRitual = true))
    }

    @Test
    fun `award rounds down`() {
        // 25 * 1.25 = 31.25 -> 31
        assertEquals(31L, ProgressionMath.computeManaAward(25, true, false))
    }

    // ── Well channel pricing ──

    @Test
    fun `well channel price doubles per channel bought that day`() {
        assertEquals(75L, ProgressionMath.wellChannelCost(0))
        assertEquals(150L, ProgressionMath.wellChannelCost(1))
        assertEquals(300L, ProgressionMath.wellChannelCost(2))
        assertEquals(600L, ProgressionMath.wellChannelCost(3))
    }

    @Test
    fun `well channel price is capped instead of overflowing`() {
        val capped = ProgressionMath.wellChannelCost(ProgressionMath.WELL_CHANNEL_MAX_DOUBLINGS)
        assertTrue(capped > 0)
        assertEquals(capped, ProgressionMath.wellChannelCost(10_000))
        assertEquals(75L, ProgressionMath.wellChannelCost(-3))
    }
}
