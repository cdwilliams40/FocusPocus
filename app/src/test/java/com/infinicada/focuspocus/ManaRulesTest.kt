package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaRulesTest {

    private fun session(minutes: Int, source: String, planned: Int?) =
        FocusSession(0L, minutes * 60_000L, minutes, "Social", 0, source, planned)

    @Test
    fun `timed sessions pay per minute up to the daily cap`() {
        val s = session(100, FocusSession.SOURCE_MANUAL, 120)
        assertEquals(100L, ManaRules.sessionAward(s, 0L, false).mana)
        assertEquals(40L, ManaRules.sessionAward(s, 200L, false).mana)
        assertEquals(0L, ManaRules.sessionAward(s, ManaRules.TIMED_DAILY_CAP, false).mana)
    }

    @Test
    fun `a ritual is open-ended whatever its length and pays flat once a day after an hour`() {
        val ritual = session(300, FocusSession.SOURCE_RITUAL, null)
        assertFalse(ManaRules.isTimed(ritual))
        assertEquals(ManaRules.OPEN_ENDED_MANA, ManaRules.sessionAward(ritual, 0L, false).mana)
        assertEquals(0L, ManaRules.sessionAward(ritual, 0L, openEndedPaidToday = true).mana)
        assertEquals(0L, ManaRules.sessionAward(session(59, FocusSession.SOURCE_RITUAL, null), 0L, false).mana)
    }

    @Test
    fun `a talisman session is timed only when it has a length`() {
        assertTrue(ManaRules.isTimed(session(30, FocusSession.SOURCE_TALISMAN, 30)))
        assertFalse(ManaRules.isTimed(session(30, FocusSession.SOURCE_TALISMAN, null)))
    }

    @Test
    fun `productive minutes pay one per two minutes up to the cap`() {
        assertEquals(0L, ManaRules.productiveMana(1))
        assertEquals(15L, ManaRules.productiveMana(31))
        assertEquals(ManaRules.PRODUCTIVE_DAILY_CAP, ManaRules.productiveMana(10_000))
    }

    @Test
    fun `pour price doubles per pour and never overflows`() {
        assertEquals(75L, ManaRules.pourCost(0))
        assertEquals(150L, ManaRules.pourCost(1))
        assertEquals(600L, ManaRules.pourCost(3))
        assertTrue(ManaRules.pourCost(ManaRules.MAX_DOUBLINGS) > 0)
        assertEquals(ManaRules.pourCost(ManaRules.MAX_DOUBLINGS), ManaRules.pourCost(10_000))
    }
}
