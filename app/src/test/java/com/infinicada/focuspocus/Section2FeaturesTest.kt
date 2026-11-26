package com.infinicada.focuspocus

import com.infinicada.focuspocus.insights.InsightsReport
import com.infinicada.focuspocus.insights.TimeOfDayBucket
import com.infinicada.focuspocus.limit.DailyHistory
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.RefillPauseReason
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellRefillGate
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.PactIntention
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType
import com.infinicada.focuspocus.model.WellDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class Section2FeaturesTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `substitute wins pay mana for the first picks of the day and every pick advances trials`() {
        val prefs = FakeSharedPreferences()
        val today = "20261126"
        val trial = Trial(
            id = "sub_daily",
            type = TrialType.SUBSTITUTE_WINS,
            target = 3,
            progress = 0,
            period = TrialPeriod.DAILY,
            periodKey = today,
            rewardMana = 50L
        )
        PrefsHelper.save(prefs, Constants.PrefsKeys.TRIALS, listOf(trial))

        // Only the day's first few picks pay, so tapping back and forth can't farm mana.
        var totalMana = 0L
        repeat(20) {
            val res = Progression.awardSubstituteWin(prefs, "com.amazon.kindle", todayKey = today)
            totalMana += res.manaEarned
        }

        val paid = ManaRules.SUBSTITUTE_WINS_PAID_PER_DAY * ManaRules.SUBSTITUTE_WIN_MANA
        assertEquals(paid, totalMana)
        assertEquals(20, DailyHistory.wellDay(prefs, today).substituteWins)
        assertEquals(paid, DailyHistory.manaDay(prefs, today).substitute)
        assertEquals(0L, Progression.nextSubstituteWinMana(prefs, today))
        assertEquals(ManaRules.SUBSTITUTE_WIN_MANA, Progression.nextSubstituteWinMana(prefs, "20261127"))
        assertTrue(Progression.loadTrials(prefs).single { it.id == "sub_daily" }.progress >= 3)
    }

    @Test
    fun `InsightsReport computes substitute wins, pact intentions, time-of-day, and period deltas`() {
        val days = listOf("20261125", "20261126")
        val wellDays = mapOf(
            // Prior 2-day period: 40 min
            "20261123" to WellDay(drawnMillis = 20 * 60_000L, draws = 2),
            "20261124" to WellDay(drawnMillis = 20 * 60_000L, draws = 2),
            // Current 2-day period: 25 min (-15 min delta)
            "20261125" to WellDay(
                drawnMillis = 10 * 60_000L,
                draws = 2,
                substituteWins = 3,
                intentions = mapOf(PactIntention.QUICK_REPLY.name to 2),
                drawsByHour = mapOf(9 to 10)
            ),
            "20261126" to WellDay(
                drawnMillis = 15 * 60_000L,
                draws = 1,
                substituteWins = 2,
                intentions = mapOf(PactIntention.UNWINDING.name to 1),
                drawsByHour = mapOf(23 to 15)
            )
        )
        val manaDays = mapOf(
            "20261126" to ManaDay(timedSession = 30L, substitute = 25L)
        )

        val report = InsightsReport.build(
            days = days,
            wellDays = wellDays,
            manaDays = manaDays,
            openStats = emptyMap(),
            sessions = emptyList(),
            blockEvents = emptyList(),
            ledger = emptyList()
        )

        assertEquals(5, report.well.substituteWins)
        assertEquals(2, report.well.intentions[PactIntention.QUICK_REPLY])
        assertEquals(1, report.well.intentions[PactIntention.UNWINDING])
        assertEquals(10, report.well.minutesByTimeOfDay[TimeOfDayBucket.MORNING])
        assertEquals(15, report.well.minutesByTimeOfDay[TimeOfDayBucket.LATE_NIGHT])
        assertEquals(40, report.well.previousPeriodMinutes)
        assertEquals(-15, report.well.deltaMinutes)
        assertEquals(25L, report.mana.substitutes)
    }

    @Test
    fun `quiet Well hours pause passive refill overnight and resume at wake time`() {
        val gate = WellRefillGate(
            quietHoursEnabled = true,
            quietStartMinute = 22 * 60, // 22:00
            quietEndMinute = 7 * 60     // 07:00
        )
        val start = ZonedDateTime.of(2026, 11, 25, 21, 0, 0, 0, utc).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, utc).toInstant().toEpochMilli()

        // 21:00–22:00 (1h) + 07:00–09:00 (2h) = 3 hours active; 22:00–07:00 (9h) quiet
        val activeMs = WellMath.effectiveRefillElapsedMillis(start, end, gate, utc)
        assertEquals(3L * 60 * 60 * 1000L, activeMs)

        val midnight = ZonedDateTime.of(2026, 11, 26, 0, 30, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(RefillPauseReason.QUIET_HOURS, WellMath.pauseReasonAt(midnight, gate, utc))
        assertEquals(RefillPauseReason.NONE, WellMath.pauseReasonAt(end, gate, utc))
    }

    @Test
    fun `morning focus gate blocks refill until qualifying session completes`() {
        val gateLocked = WellRefillGate(
            morningFocusRequired = true,
            morningFocusMinutes = 10,
            morningUnlockedDate = "20261125"
        )
        val morningStart = ZonedDateTime.of(2026, 11, 26, 7, 0, 0, 0, utc).toInstant().toEpochMilli()
        val morningLate = ZonedDateTime.of(2026, 11, 26, 11, 0, 0, 0, utc).toInstant().toEpochMilli()

        assertEquals(RefillPauseReason.MORNING_FOCUS, WellMath.pauseReasonAt(morningLate, gateLocked, utc))
        assertEquals(0L, WellMath.effectiveRefillElapsedMillis(morningStart, morningLate, gateLocked, utc))

        val gateUnlocked = gateLocked.copy(morningUnlockedDate = "20261126")
        assertEquals(RefillPauseReason.NONE, WellMath.pauseReasonAt(morningLate, gateUnlocked, utc))
        assertEquals(4L * 60 * 60 * 1000L, WellMath.effectiveRefillElapsedMillis(morningStart, morningLate, gateUnlocked, utc))
    }

    @Test
    fun `planned ritual exceptions enforce 24-hour advance rule and suppress activation`() {
        // 2026-11-26 is Thursday
        val schedule = Schedule(
            id = "morning-shield",
            name = "Morning Shield",
            blockerNames = listOf("Social"),
            days = setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            startTime = "09:00",
            endTime = "12:00"
        )
        val thu10am = ZonedDateTime.of(2026, 11, 26, 10, 0, 0, 0, utc).toInstant().toEpochMilli()

        // Friday 09:00 is only 23 hours away -> rejected
        assertFalse(RitualExceptions.canRequestSkip(schedule, "20261127", thu10am, utc))
        // Saturday 09:00 is 47 hours away -> allowed
        assertTrue(RitualExceptions.canRequestSkip(schedule, "20261128", thu10am, utc))

        val sat10am = ZonedDateTime.of(2026, 11, 28, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(isScheduleActiveAt(schedule, sat10am, skippedDates = emptySet()))
        assertFalse(isScheduleActiveAt(schedule, sat10am, skippedDates = setOf("20261128")))
    }

    @Test
    fun `AutoBackupManager computes due state and next backup timestamp per cadence`() {
        val prefs = FakeSharedPreferences()
        val t0 = 1_700_000_000_000L
        assertNull(AutoBackupManager.nextDueMillis(prefs, t0))
        assertFalse(AutoBackupManager.isDue(prefs, t0))

        AutoBackupManager.setTreeUri(prefs, "content://com.android.externalstorage.documents/tree/primary%3ABackups")
        AutoBackupManager.setCadence(prefs, AutoBackupCadence.DAILY)
        assertTrue(AutoBackupManager.isDue(prefs, t0))

        prefs.putLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_MILLIS, t0)
        assertFalse(AutoBackupManager.isDue(prefs, t0 + 12 * 3600_000L))
        assertTrue(AutoBackupManager.isDue(prefs, t0 + 24 * 3600_000L))
        assertEquals(t0 + AutoBackupCadence.DAILY.intervalMillis, AutoBackupManager.nextDueMillis(prefs, t0 + 1_000L))
    }
}
