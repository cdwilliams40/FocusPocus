package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.SessionCooldownManager
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
        hasSchedules = true, canJudgeLimits = true, hasReflexHistory = true
    )
    private val noneExtra = TrialEngine.Eligibility(
        hasSchedules = false, canJudgeLimits = false, hasReflexHistory = false
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

            val dayKey = SessionCooldownManager.todayString()
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
            assertTrue(slate.none { it.type == TrialType.COMPLETE_RITUAL })
            assertTrue(slate.none { it.type == TrialType.STAY_UNDER_LIMITS })
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
    fun `sessions advance session-count and minute trials`() {
        val trials = listOf(
            trial(TrialType.COMPLETE_SESSIONS, target = 2),
            trial(TrialType.FOCUS_MINUTES, target = 60)
        )
        val (updated, completed) = TrialEngine.advanceForSession(trials, durationMinutes = 45, fromRitual = false)
        assertEquals(1, updated[0].progress)
        assertEquals(45, updated[1].progress)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `crossing the target reports a completion exactly once`() {
        val trials = listOf(trial(TrialType.FOCUS_MINUTES, target = 60, progress = 30))
        val (updated, completed) = TrialEngine.advanceForSession(trials, 45, false)
        assertEquals(60, updated[0].progress) // capped
        assertEquals(1, completed.size)

        val (again, completedAgain) = TrialEngine.advanceForSession(updated, 45, false)
        assertEquals(60, again[0].progress)
        assertTrue(completedAgain.isEmpty())
    }

    @Test
    fun `ritual trials only advance for ritual sessions`() {
        val trials = listOf(trial(TrialType.COMPLETE_RITUAL, target = 1))
        val (unchanged, none) = TrialEngine.advanceForSession(trials, 30, fromRitual = false)
        assertEquals(0, unchanged[0].progress)
        assertTrue(none.isEmpty())
        val (updated, completed) = TrialEngine.advanceForSession(trials, 30, fromRitual = true)
        assertEquals(1, updated[0].progress)
        assertEquals(1, completed.size)
    }

    @Test
    fun `claimed trials never advance`() {
        val trials = listOf(trial(TrialType.COMPLETE_SESSIONS, target = 2, progress = 1, claimed = true))
        val (updated, completed) = TrialEngine.advanceForSession(trials, 30, false)
        assertEquals(1, updated[0].progress)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `rollover-judged types are not advanced by sessions`() {
        val trials = listOf(
            trial(TrialType.STAY_UNDER_LIMITS, target = 1),
            trial(TrialType.NO_REFLEX_OPENS, target = 1, period = TrialPeriod.WEEKLY, param = 10)
        )
        val (updated, completed) = TrialEngine.advanceForSession(trials, 120, true)
        assertTrue(updated.all { it.progress == 0 })
        assertTrue(completed.isEmpty())
    }

    // ── judgment ──

    @Test
    fun `stay-under-limits succeeds on a clean day`() {
        val t = trial(TrialType.STAY_UNDER_LIMITS, target = 1)
        val judged = TrialEngine.judgeEndedTrial(t, emptyList(), emptyMap(), limitsConfigured = true)
        assertTrue(judged.completed)
    }

    @Test
    fun `stay-under-limits fails on a time-limit block that day`() {
        val t = trial(TrialType.STAY_UNDER_LIMITS, target = 1)
        val blockTime = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 13, 15, 0, 0)
        }.timeInMillis
        val events = listOf(BlockEvent("com.example.feed", blockTime, TrialEngine.TIME_LIMIT_BLOCK_REASON))
        val judged = TrialEngine.judgeEndedTrial(t, events, emptyMap(), limitsConfigured = true)
        assertFalse(judged.completed)
    }

    @Test
    fun `stay-under-limits ignores blocks on other days and other reasons`() {
        val t = trial(TrialType.STAY_UNDER_LIMITS, target = 1)
        val otherDay = Calendar.getInstance().apply { set(2026, Calendar.JULY, 12, 15, 0, 0) }.timeInMillis
        val sameDay = Calendar.getInstance().apply { set(2026, Calendar.JULY, 13, 15, 0, 0) }.timeInMillis
        val events = listOf(
            BlockEvent("com.example.feed", otherDay, TrialEngine.TIME_LIMIT_BLOCK_REASON),
            BlockEvent("com.example.feed", sameDay, "Pact Gate")
        )
        val judged = TrialEngine.judgeEndedTrial(t, events, emptyMap(), limitsConfigured = true)
        assertTrue(judged.completed)
    }

    @Test
    fun `stay-under-limits is vacuously false without configured limits`() {
        val t = trial(TrialType.STAY_UNDER_LIMITS, target = 1)
        val judged = TrialEngine.judgeEndedTrial(t, emptyList(), emptyMap(), limitsConfigured = false)
        assertFalse(judged.completed)
    }

    @Test
    fun `no-reflex-opens sums the trial week only`() {
        val inWeekDay = "20260713"
        val nextDay = "20260714"
        val farOffDay = "20260601" // clearly a different ISO week
        val weekKey = TrialEngine.weekKeyForDay(inWeekDay)
        assertEquals(weekKey, TrialEngine.weekKeyForDay(nextDay))
        assertTrue(weekKey != TrialEngine.weekKeyForDay(farOffDay))

        val t = Trial(
            "t", TrialType.NO_REFLEX_OPENS, target = 1, progress = 0,
            period = TrialPeriod.WEEKLY, periodKey = weekKey, rewardMana = 150L, param = 10
        )
        // 8 reflexes in the trial week, a flood outside it: still under the cap
        val history = mapOf(
            inWeekDay to mapOf("a" to AppOpenStats(opens = 20, reflexOpens = 8)),
            farOffDay to mapOf("a" to AppOpenStats(opens = 30, reflexOpens = 30))
        )
        val judged = TrialEngine.judgeEndedTrial(t, emptyList(), history, true)
        assertTrue(judged.completed)

        // 11 reflexes inside the week: over the cap of 10
        val overHistory = mapOf(
            inWeekDay to mapOf("a" to AppOpenStats(opens = 20, reflexOpens = 8)),
            nextDay to mapOf("b" to AppOpenStats(opens = 10, reflexOpens = 3))
        )
        val judgedOver = TrialEngine.judgeEndedTrial(t, emptyList(), overHistory, true)
        assertFalse(judgedOver.completed)
    }

    // ── date keys ──

    @Test
    fun `dateKeyOf matches todayString format`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 13, 0, 30, 0) }
        assertEquals("20260713", TrialEngine.dateKeyOf(cal.timeInMillis))
    }
}
