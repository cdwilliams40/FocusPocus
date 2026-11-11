package com.infinicada.focuspocus

import com.infinicada.focuspocus.insights.FocusSource
import com.infinicada.focuspocus.insights.InsightsRange
import com.infinicada.focuspocus.insights.InsightsReport
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.WellDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class InsightsReportTest {

    private fun millisOn(day: String, hour: Int = 12): Long = Calendar.getInstance().apply {
        set(day.substring(0, 4).toInt(), day.substring(4, 6).toInt() - 1, day.substring(6, 8).toInt(), hour, 0, 0)
    }.timeInMillis

    private val days = InsightsReport.daysFor(InsightsRange.WEEK, "20261111")

    private fun build(
        wellDays: Map<String, WellDay> = emptyMap(),
        manaDays: Map<String, ManaDay> = emptyMap(),
        openStats: Map<String, Map<String, AppOpenStats>> = emptyMap(),
        sessions: List<FocusSession> = emptyList(),
        blocks: List<BlockEvent> = emptyList(),
        ledger: List<ManaLedgerEntry> = emptyList()
    ) = InsightsReport.build(days, wellDays, manaDays, openStats, sessions, blocks, ledger)

    @Test
    fun `ranges end today and run oldest first`() {
        assertEquals(listOf("20261111"), InsightsReport.daysFor(InsightsRange.TODAY, "20261111"))
        assertEquals("20261105", days.first())
        assertEquals("20261111", days.last())
        assertEquals(30, InsightsReport.daysFor(InsightsRange.MONTH, "20260301").size)
        assertEquals("20260131", InsightsReport.daysFor(InsightsRange.MONTH, "20260301").first())
    }

    @Test
    fun `well totals cover the range only and rank apps`() {
        val report = build(wellDays = mapOf(
            "20261104" to WellDay(drawnMillis = 99 * 60_000L, draws = 9),
            "20261110" to WellDay(drawnMillis = 10 * 60_000L, refundedMillis = 5 * 60_000L, draws = 1,
                perApp = mapOf("chrome" to 15 * 60_000L)),
            "20261111" to WellDay(drawnMillis = 20 * 60_000L, draws = 2, pours = 1, dryBlocks = 3,
                perApp = mapOf("store" to 20 * 60_000L))
        )).well
        assertEquals(30, report.totalMinutes)
        assertEquals(3, report.pacts)
        assertEquals(5, report.refundedMinutes)
        assertEquals(1, report.pours)
        assertEquals(3, report.dryOpens)
        assertEquals(listOf("store" to 20, "chrome" to 15), report.perApp)
        assertEquals(listOf(0, 0, 0, 0, 0, 10, 20), report.minutesPerDay)
        // (30 net + 5 handed back) over 3 pacts
        assertEquals(11, report.averagePactMinutes)
    }

    @Test
    fun `focus minutes split by source, with old sessions counted as by hand`() {
        val sessions = listOf(
            FocusSession(0L, millisOn("20261111"), 30, "Social", 0, FocusSession.SOURCE_MANUAL, 30),
            FocusSession(0L, millisOn("20261111"), 240, "Shield", 0, FocusSession.SOURCE_RITUAL),
            FocusSession(0L, millisOn("20261110"), 60, "Shield", 0, FocusSession.SOURCE_TALISMAN),
            FocusSession(0L, millisOn("20261109"), 20, "Old", 0),
            FocusSession(0L, millisOn("20261001"), 500, "Out of range", 0)
        )
        val focus = build(sessions = sessions).focus
        assertEquals(4, focus.sessions)
        assertEquals(50, focus.minutesBySource[FocusSource.MANUAL])
        assertEquals(240, focus.minutesBySource[FocusSource.RITUAL])
        assertEquals(60, focus.minutesBySource[FocusSource.TALISMAN])
        assertEquals(listOf(30, 240, 0), focus.minutesPerDay.last().toList())
        assertEquals(87, focus.averageSessionMinutes)
    }

    @Test
    fun `blocked attempts rank apps within the range`() {
        val blocks = listOf(
            BlockEvent("feed", millisOn("20261111"), "Social"),
            BlockEvent("feed", millisOn("20261110"), "Pact Gate"),
            BlockEvent("tube", millisOn("20261110"), "Social"),
            BlockEvent("feed", millisOn("20261001"), "Social")
        )
        val focus = build(blocks = blocks).focus
        assertEquals(3, focus.blockedAttempts)
        assertEquals(listOf("feed" to 2, "tube" to 1), focus.mostBlocked)
    }

    @Test
    fun `reflexes sum opens per app and per day`() {
        val reflexes = build(openStats = mapOf(
            "20261110" to mapOf("feed" to AppOpenStats(5, 2)),
            "20261111" to mapOf("feed" to AppOpenStats(3, 1), "tube" to AppOpenStats(4, 0))
        )).reflexes
        assertEquals(12, reflexes.opens)
        assertEquals(3, reflexes.reflexOpens)
        assertEquals(listOf(0, 0, 0, 0, 0, 2, 1), reflexes.reflexesPerDay)
        assertEquals(listOf("feed", "tube"), reflexes.perApp.map { it.packageName })
    }

    @Test
    fun `mana splits earned by source and lists pours with their price`() {
        val mana = build(
            manaDays = mapOf(
                "20261110" to ManaDay(timedSession = 30, trials = 50),
                "20261111" to ManaDay(openEndedSession = 30, productive = 12, pours = 225, breaks = 50)
            ),
            ledger = listOf(
                ManaLedgerEntry(1L, -75, LedgerKind.PERK, refId = Perk.WELL_CHANNEL.name, dateKey = "20261111"),
                ManaLedgerEntry(2L, -150, LedgerKind.PERK, refId = Perk.WELL_CHANNEL.name, dateKey = "20261111"),
                ManaLedgerEntry(3L, -50, LedgerKind.PERK, refId = Perk.EXTRA_BREAK.name, dateKey = "20261111"),
                ManaLedgerEntry(4L, 50, LedgerKind.TRIAL, refId = "t", dateKey = "20261110"),
                ManaLedgerEntry(5L, 40, LedgerKind.TRIAL, refId = "old", dateKey = "20260101")
            )
        ).mana
        assertEquals(122L, mana.earned)
        assertEquals(275L, mana.spent)
        assertEquals(30L, mana.openEndedSessions)
        assertEquals(1, mana.trialsCompleted)
        assertEquals(listOf(-150L, -75L), mana.pours.map { it.amount })
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 80L, 42L), mana.earnedPerDay)
    }
}
