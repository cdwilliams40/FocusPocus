package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class TrialEngineTest {

    private val allEligible = TrialEngine.Eligibility(
        hasWellApps = true, canMeasureProductive = true, hasReflexHistory = true
    )
    private val noneExtra = TrialEngine.Eligibility(
        hasWellApps = false, canMeasureProductive = false, hasReflexHistory = false
    )

    // ── locale-independent date keys ──

    @Test
    fun `date keys stay ASCII-parseable under a non-Latin default locale`() {
        // Persian's default number system emits non-ASCII digits from String.format
        // with no explicit locale. The machine keys must stay ASCII or the downstream
        // parsers (weekKeyForDay's BASIC_ISO_DATE, the yyyyMMdd retention cutoff)
        // reject them. \d matches only [0-9], so a localized digit fails these.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))

            val dayKey = DayKey.today()
            assertTrue("todayString must be ASCII digits, got $dayKey", dayKey.matches(Regex("\\d{8}")))

            val weekKey = TrialEngine.weekKeyForDay(dayKey)
            assertTrue("weekKeyForDay must resolve, got $weekKey", weekKey.matches(Regex("\\d{4}-W\\d{2}")))

            val dateKey = TrialEngine.dateKeyOf(1_700_000_000_000L)
            assertTrue("dateKeyOf must be ASCII digits, got $dateKey", dateKey.matches(Regex("\\d{8}")))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ── week keys ──

    @Test
    fun `week key follows ISO weeks across a year boundary`() {
        // 2024-12-30 (Monday) and 2025-01-01 (Wednesday) share ISO week 2025-W01
        assertEquals("2025-W01", TrialEngine.weekKeyForDay("20241230"))
        assertEquals("2025-W01", TrialEngine.weekKeyForDay("20250101"))
        // The Sunday before belongs to 2024's last week
        assertEquals("2024-W52", TrialEngine.weekKeyForDay("20241229"))
    }

    // ── drawing ──

    @Test
    fun `draw is deterministic for a period key`() {
        val a = TrialEngine.drawSlate("20260713", "2026-W29", emptyList(), allEligible)
        val b = TrialEngine.drawSlate("20260713", "2026-W29", emptyList(), allEligible)
        assertEquals(a, b)
    }

    @Test
    fun `draw fills two daily slots and one weekly slot`() {
        val slate = TrialEngine.drawSlate("20260713", "2026-W29", emptyList(), allEligible)
        assertEquals(2, slate.count { it.period == TrialPeriod.DAILY })
        assertEquals(1, slate.count { it.period == TrialPeriod.WEEKLY })
        assertTrue(slate.all { it.progress == 0 && !it.claimed })
    }

    @Test
    fun `unmeasurable templates are never drawn`() {
        // Run across many period keys: gated types must never appear
        for (day in 1..28) {
            val key = "202607%02d".format(day)
            val slate = TrialEngine.drawSlate(key, TrialEngine.weekKeyForDay(key), emptyList(), noneExtra)
            assertTrue(slate.none { it.type == TrialType.WELL_UNDER })
            assertTrue(slate.none { it.type == TrialType.PRODUCTIVE_MINUTES })
            assertTrue(slate.none { it.type == TrialType.NO_REFLEX_OPENS })
            assertEquals(2, slate.count { it.period == TrialPeriod.DAILY })
            assertEquals(1, slate.count { it.period == TrialPeriod.WEEKLY })
        }
    }

    @Test
    fun `active trials keep their slots`() {
        val existing = TrialEngine.drawSlate("20260713", "2026-W29", emptyList(), allEligible)
        val redrawn = TrialEngine.drawSlate("20260713", "2026-W29", existing, allEligible)
        assertEquals(existing, redrawn)
    }

    @Test
    fun `weekly trial survives a daily rotation`() {
        val slate = TrialEngine.drawSlate("20260713", "2026-W29", emptyList(), allEligible)
        val weekly = slate.filter { it.period == TrialPeriod.WEEKLY }
        // Next day, same week: daily trials ended, weekly kept
        val active = slate.filter { !TrialEngine.isEnded(it, "20260714", "2026-W29") }
        assertEquals(weekly, active.filter { it.period == TrialPeriod.WEEKLY })
        val newSlate = TrialEngine.drawSlate("20260714", "2026-W29", active, allEligible)
        assertEquals(weekly, newSlate.filter { it.period == TrialPeriod.WEEKLY })
        assertEquals(2, newSlate.count { it.period == TrialPeriod.DAILY })
        assertTrue(newSlate.filter { it.period == TrialPeriod.DAILY }.all { it.periodKey == "20260714" })
    }

    // ── advancement ──

    private fun trial(
        type: TrialType,
        target: Int,
        progress: Int = 0,
        period: TrialPeriod = TrialPeriod.DAILY,
        param: Int = 0,
        claimed: Boolean = false
    ) = Trial("t_${type.name}", type, target, progress, period, "20260713", 40L, claimed, param)

    @Test
    fun `retired templates are never drawn`() {
        for (day in 1..28) {
            val key = "202607%02d".format(day)
            val slate = TrialEngine.drawSlate(key, TrialEngine.weekKeyForDay(key), emptyList(), allEligible)
            assertTrue(slate.none {
                it.type in setOf(TrialType.COMPLETE_SESSIONS, TrialType.COMPLETE_RITUAL, TrialType.STAY_UNDER_LIMITS)
            })
        }
    }

    @Test
    fun `only timed sessions advance minute trials`() {
        val trials = listOf(trial(TrialType.FOCUS_MINUTES, target = 60))
        val (afterRitual, _) = TrialEngine.advanceForSession(trials, 240, timed = false, startedByHand = false)
        assertEquals(0, afterRitual.single().progress)
        val (afterTimed, _) = TrialEngine.advanceForSession(trials, 25, timed = true, startedByHand = true)
        assertEquals(25, afterTimed.single().progress)
    }

    @Test
    fun `starting a timed session by hand completes the start trial once`() {
        val trials = listOf(trial(TrialType.START_TIMED_SESSION, target = 1))
        val (byTalisman, none) = TrialEngine.advanceForSession(trials, 30, timed = true, startedByHand = false)
        assertEquals(0, byTalisman.single().progress)
        assertTrue(none.isEmpty())
        val (byHand, done) = TrialEngine.advanceForSession(trials, 30, timed = true, startedByHand = true)
        assertTrue(byHand.single().completed)
        assertEquals(1, done.size)
        val (again, doneAgain) = TrialEngine.advanceForSession(byHand, 30, timed = true, startedByHand = true)
        assertEquals(byHand, again)
        assertTrue(doneAgain.isEmpty())
    }

    @Test
    fun `claimed trials never advance`() {
        val trials = listOf(trial(TrialType.FOCUS_MINUTES, target = 60, progress = 60, claimed = true))
        assertEquals(trials, TrialEngine.advanceForSession(trials, 30, timed = true, startedByHand = true).first)
    }

    @Test
    fun `productive trials follow the day's total and the week's growth`() {
        val trials = listOf(
            trial(TrialType.PRODUCTIVE_MINUTES, target = 60),
            trial(TrialType.PRODUCTIVE_MINUTES, target = 120, period = TrialPeriod.WEEKLY).copy(id = "w")
        )
        val (after, _) = TrialEngine.advanceForProductive(trials, minutesToday = 40, addedMinutes = 10)
        assertEquals(40, after[0].progress)
        assertEquals(10, after[1].progress)
    }

    @Test
    fun `well-under is judged from the period's net draws`() {
        val day = trial(TrialType.WELL_UNDER, target = 1, param = 15)
        assertTrue(TrialEngine.judgeEndedTrial(day, mapOf("20260713" to 15, "20260714" to 90), emptyMap()).completed)
        assertFalse(TrialEngine.judgeEndedTrial(day, mapOf("20260713" to 16), emptyMap()).completed)

        val week = Trial(
            "w", TrialType.WELL_UNDER, target = 1, period = TrialPeriod.WEEKLY,
            periodKey = TrialEngine.weekKeyForDay("20260713"), rewardMana = 200L, param = 60
        )
        assertTrue(TrialEngine.judgeEndedTrial(week, mapOf("20260713" to 30, "20260714" to 30), emptyMap()).completed)
        assertFalse(TrialEngine.judgeEndedTrial(week, mapOf("20260713" to 30, "20260714" to 31), emptyMap()).completed)
    }

    @Test
    fun `daily reflex trial counts its own day only`() {
        val day = trial(TrialType.NO_REFLEX_OPENS, target = 1, param = 3)
        val history = mapOf(
            "20260713" to mapOf("a" to AppOpenStats(opens = 9, reflexOpens = 3)),
            "20260714" to mapOf("a" to AppOpenStats(opens = 9, reflexOpens = 9))
        )
        assertTrue(TrialEngine.judgeEndedTrial(day, emptyMap(), history).completed)
    }

    @Test
    fun `no-reflex-opens sums the trial week only`() {
        val inWeekDay = "20260713"
        val nextDay = "20260714"
        val farOffDay = "20260601"
        val weekKey = TrialEngine.weekKeyForDay(inWeekDay)
        val t = Trial(
            "t", TrialType.NO_REFLEX_OPENS, target = 1, progress = 0,
            period = TrialPeriod.WEEKLY, periodKey = weekKey, rewardMana = 150L, param = 10
        )
        val history = mapOf(
            inWeekDay to mapOf("a" to AppOpenStats(opens = 20, reflexOpens = 8)),
            farOffDay to mapOf("a" to AppOpenStats(opens = 30, reflexOpens = 30))
        )
        assertTrue(TrialEngine.judgeEndedTrial(t, emptyMap(), history).completed)
        val overHistory = mapOf(
            inWeekDay to mapOf("a" to AppOpenStats(opens = 20, reflexOpens = 8)),
            nextDay to mapOf("b" to AppOpenStats(opens = 10, reflexOpens = 3))
        )
        assertFalse(TrialEngine.judgeEndedTrial(t, emptyMap(), overHistory).completed)
    }

    // ── date keys ──

    @Test
    fun `dateKeyOf matches todayString format`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 13, 0, 30, 0) }
        assertEquals("20260713", TrialEngine.dateKeyOf(cal.timeInMillis))
    }
}
