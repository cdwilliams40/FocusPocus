package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionMathTest {

    // ── computeManaAward ──

    @Test
    fun `base award is one mana per minute`() {
        assertEquals(25L, ProgressionMath.computeManaAward(25, 0, hideStopButton = false, fromRitual = false))
    }

    @Test
    fun `zero and negative durations award nothing`() {
        assertEquals(0L, ProgressionMath.computeManaAward(0, 5, true, true))
        assertEquals(0L, ProgressionMath.computeManaAward(-10, 5, true, true))
    }

    @Test
    fun `session cap stops overnight farming`() {
        // 480-minute Sleep Mode caps at 240 base
        assertEquals(240L, ProgressionMath.computeManaAward(480, 0, false, false))
    }

    @Test
    fun `hidden stop button pays 25 percent more`() {
        assertEquals(125L, ProgressionMath.computeManaAward(100, 0, hideStopButton = true, fromRitual = false))
    }

    @Test
    fun `streak bonus scales at 2 percent per day`() {
        // 100 min * (1 + 0.02*10) = 120
        assertEquals(120L, ProgressionMath.computeManaAward(100, 10, false, false))
    }

    @Test
    fun `streak bonus tops out at 25 days`() {
        val at25 = ProgressionMath.computeManaAward(100, 25, false, false)
        val at40 = ProgressionMath.computeManaAward(100, 40, false, false)
        assertEquals(150L, at25)
        assertEquals(at25, at40)
    }

    @Test
    fun `ritual bonus is a flat 20 after multipliers`() {
        assertEquals(120L, ProgressionMath.computeManaAward(100, 0, false, fromRitual = true))
    }

    @Test
    fun `all bonuses stack`() {
        // min(300,240)=240; *1.25=300; *1.5=450; +20=470
        assertEquals(470L, ProgressionMath.computeManaAward(300, 30, hideStopButton = true, fromRitual = true))
    }

    @Test
    fun `award rounds down`() {
        // 25 * 1.02 = 25.5 -> 25
        assertEquals(25L, ProgressionMath.computeManaAward(25, 1, false, false))
    }

    // ── milestoneBonus ──

    @Test
    fun `first milestone pays once`() {
        val (bonus, paid) = ProgressionMath.milestoneBonus(newStreak = 7, highestPaid = 0)
        assertEquals(100L, bonus)
        assertEquals(7, paid)
    }

    @Test
    fun `long streak pays all crossed milestones at once`() {
        // Existing user with a 45-day streak on first award: 7 + 30 pay together
        val (bonus, paid) = ProgressionMath.milestoneBonus(newStreak = 45, highestPaid = 0)
        assertEquals(600L, bonus)
        assertEquals(30, paid)
    }

    @Test
    fun `re-crossing a paid milestone pays nothing`() {
        val (bonus, paid) = ProgressionMath.milestoneBonus(newStreak = 8, highestPaid = 30)
        assertEquals(0L, bonus)
        assertEquals(30, paid)
    }

    @Test
    fun `below first milestone pays nothing`() {
        val (bonus, paid) = ProgressionMath.milestoneBonus(newStreak = 6, highestPaid = 0)
        assertEquals(0L, bonus)
        assertEquals(0, paid)
    }

    @Test
    fun `hundred day milestone pays 2000`() {
        val (bonus, paid) = ProgressionMath.milestoneBonus(newStreak = 100, highestPaid = 30)
        assertEquals(2000L, bonus)
        assertEquals(100, paid)
    }

    // ── shouldSendWrapup ──

    @Test
    fun `wrapup fires in the evening on an active day`() {
        assertTrue(ProgressionMath.shouldSendWrapup(20, "20260713", null, "20260713", true, true))
    }

    @Test
    fun `wrapup respects the hour gate`() {
        assertFalse(ProgressionMath.shouldSendWrapup(19, "20260713", null, "20260713", true, true))
    }

    @Test
    fun `wrapup fires at most once per day`() {
        assertFalse(ProgressionMath.shouldSendWrapup(21, "20260713", "20260713", "20260713", true, true))
    }

    @Test
    fun `wrapup is suppressed on inactive days`() {
        assertFalse(ProgressionMath.shouldSendWrapup(21, "20260713", null, "20260712", true, true))
        assertFalse(ProgressionMath.shouldSendWrapup(21, "20260713", null, null, true, true))
    }

    @Test
    fun `wrapup respects both toggles`() {
        assertFalse(ProgressionMath.shouldSendWrapup(21, "20260713", null, "20260713", false, true))
        assertFalse(ProgressionMath.shouldSendWrapup(21, "20260713", null, "20260713", true, false))
    }

    @Test
    fun `wrapup fires late in the evening if the service was down at 20`() {
        assertTrue(ProgressionMath.shouldSendWrapup(23, "20260713", "20260712", "20260713", true, true))
    }
}
