package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUtilsTest {

    // ----- schedulesOverlap -----

    @Test
    fun `schedulesOverlap detects same-day overlaps and ignores disjoint windows`() {
        val mon = setOf(com.infinicada.focuspocus.model.DayOfWeek.MONDAY)
        val tue = setOf(com.infinicada.focuspocus.model.DayOfWeek.TUESDAY)
        assertTrue(schedulesOverlap(mon, 9 * 60, 12 * 60, mon, 11 * 60, 14 * 60))
        // Back-to-back (end == start) is not an overlap
        assertFalse(schedulesOverlap(mon, 9 * 60, 12 * 60, mon, 12 * 60, 14 * 60))
        assertFalse(schedulesOverlap(mon, 9 * 60, 12 * 60, tue, 9 * 60, 12 * 60))
    }

    @Test
    fun `schedulesOverlap handles overnight rituals and day-after overlaps`() {
        val mon = setOf(com.infinicada.focuspocus.model.DayOfWeek.MONDAY)
        val tue = setOf(com.infinicada.focuspocus.model.DayOfWeek.TUESDAY)
        val sun = setOf(com.infinicada.focuspocus.model.DayOfWeek.SUNDAY)
        // Monday 22:00–06:00 overlaps Tuesday 05:00–08:00
        assertTrue(schedulesOverlap(mon, 22 * 60, 6 * 60, tue, 5 * 60, 8 * 60))
        // Monday 22:00–06:00 does NOT overlap Monday 09:00–17:00
        assertFalse(schedulesOverlap(mon, 22 * 60, 6 * 60, mon, 9 * 60, 17 * 60))
        // Monday 22:00–06:00 does NOT overlap Tuesday 06:00–09:00 (exact boundary)
        assertFalse(schedulesOverlap(mon, 22 * 60, 6 * 60, tue, 6 * 60, 9 * 60))
        // Sunday 22:00–06:00 wraps into Monday morning and overlaps Monday 05:00–07:00
        assertTrue(schedulesOverlap(sun, 22 * 60, 6 * 60, mon, 5 * 60, 7 * 60))
        // Sunday 22:00–06:00 does NOT overlap Monday 07:00–09:00
        assertFalse(schedulesOverlap(sun, 22 * 60, 6 * 60, mon, 7 * 60, 9 * 60))
    }

    // ----- shared schedule math (alarm backstop) -----

    /** A Calendar pinned to a known Wednesday at [hour]:[minute] local time. */
    private fun wednesdayAt(hour: Int, minute: Int): java.util.Calendar =
        java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.JULY, 15, hour, minute, 0) // a Wednesday
        }

    private fun schedule(
        days: Set<com.infinicada.focuspocus.model.DayOfWeek>,
        start: String,
        end: String
    ) = com.infinicada.focuspocus.model.Schedule(
        name = "Test",
        blockerNames = listOf("Work"),
        days = days,
        startTime = start,
        endTime = end
    )

    private val weekdays = setOf(
        com.infinicada.focuspocus.model.DayOfWeek.MONDAY,
        com.infinicada.focuspocus.model.DayOfWeek.TUESDAY,
        com.infinicada.focuspocus.model.DayOfWeek.WEDNESDAY,
        com.infinicada.focuspocus.model.DayOfWeek.THURSDAY,
        com.infinicada.focuspocus.model.DayOfWeek.FRIDAY
    )

    @Test
    fun `parseScheduleMinutes accepts valid and rejects malformed times`() {
        org.junit.Assert.assertEquals(9 * 60 + 30, parseScheduleMinutes("09:30"))
        org.junit.Assert.assertNull(parseScheduleMinutes(null))
        org.junit.Assert.assertNull(parseScheduleMinutes(""))
        org.junit.Assert.assertNull(parseScheduleMinutes("24:00"))
        org.junit.Assert.assertNull(parseScheduleMinutes("banana"))
    }

    @Test
    fun `isScheduleActiveAt covers same-day windows`() {
        val s = schedule(weekdays, "09:00", "17:00")
        assertTrue(isScheduleActiveAt(s, wednesdayAt(10, 0).timeInMillis))
        assertFalse(isScheduleActiveAt(s, wednesdayAt(8, 59).timeInMillis))
        assertFalse(isScheduleActiveAt(s, wednesdayAt(17, 0).timeInMillis))
    }

    @Test
    fun `isScheduleActiveAt covers overnight carry-over mornings`() {
        val tuesdayOnly = setOf(com.infinicada.focuspocus.model.DayOfWeek.TUESDAY)
        val s = schedule(tuesdayOnly, "22:00", "06:00")
        // Wednesday 03:00 belongs to Tuesday's overnight window.
        assertTrue(isScheduleActiveAt(s, wednesdayAt(3, 0).timeInMillis))
        // Wednesday 23:00 does not — Wednesday isn't a scheduled day.
        assertFalse(isScheduleActiveAt(s, wednesdayAt(23, 0).timeInMillis))
    }

    @Test
    fun `isScheduleActiveAt ends a ritual whose end was missed into an unscheduled day`() {
        // A Tuesday-only 09:00-17:00 ritual still marked active on Wednesday at
        // 10:00 (phone off across its end) must read inactive, even though
        // 10:00 falls inside the time-of-day window.
        val tuesdayOnly = setOf(com.infinicada.focuspocus.model.DayOfWeek.TUESDAY)
        val s = schedule(tuesdayOnly, "09:00", "17:00")
        assertFalse(isScheduleActiveAt(s, wednesdayAt(10, 0).timeInMillis))
    }

    @Test
    fun `findRitualToResume picks an open ritual that still has an enchantment`() {
        val open = schedule(weekdays, "09:00", "17:00")
        val closed = schedule(weekdays, "18:00", "19:00")
        val now = wednesdayAt(10, 0).timeInMillis

        org.junit.Assert.assertEquals(
            open, findRitualToResume(listOf(closed, open), now, setOf("Work")) { false }
        )
        // Its only enchantment has been deleted: nothing to enforce.
        org.junit.Assert.assertNull(findRitualToResume(listOf(open), now, setOf("Other")) { false })
        org.junit.Assert.assertNull(findRitualToResume(listOf(closed), now, setOf("Work")) { false })
    }

    @Test
    fun `findRitualToResume leaves a ritual ended early in this window alone`() {
        val shield = schedule(weekdays, "06:00", "12:00")
        val later = schedule(weekdays, "09:00", "17:00")
        val now = wednesdayAt(10, 0).timeInMillis

        // Shield was ended with its talisman at some point this morning.
        org.junit.Assert.assertNull(
            findRitualToResume(listOf(shield), now, setOf("Work")) { it == shield.id }
        )
        // Another ritual whose window is open is still caught up on.
        org.junit.Assert.assertEquals(
            later, findRitualToResume(listOf(shield, later), now, setOf("Work")) { it == shield.id }
        )
    }

    @Test
    fun `computeScheduleEndMillis resolves to the next end occurrence`() {
        val s = schedule(weekdays, "09:00", "17:00")
        val beforeEnd = wednesdayAt(10, 0)
        org.junit.Assert.assertEquals(
            wednesdayAt(17, 0).timeInMillis,
            computeScheduleEndMillis(s, beforeEnd.timeInMillis)
        )
        // Past today's end: tomorrow's end.
        val afterEnd = wednesdayAt(18, 0)
        val tomorrowEnd = wednesdayAt(17, 0).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        org.junit.Assert.assertEquals(
            tomorrowEnd.timeInMillis,
            computeScheduleEndMillis(s, afterEnd.timeInMillis)
        )
    }

    @Test
    fun `nextRitualTransitionMillis picks the earliest upcoming start or end`() {
        val s = schedule(weekdays, "09:00", "17:00")
        // Wednesday 08:00 -> next transition is today's 09:00 start.
        org.junit.Assert.assertEquals(
            wednesdayAt(9, 0).timeInMillis,
            nextRitualTransitionMillis(listOf(s), wednesdayAt(8, 0).timeInMillis)
        )
        // Wednesday 10:00 -> next transition is today's 17:00 end.
        org.junit.Assert.assertEquals(
            wednesdayAt(17, 0).timeInMillis,
            nextRitualTransitionMillis(listOf(s), wednesdayAt(10, 0).timeInMillis)
        )
        // Wednesday 18:00 -> next transition is Thursday's 09:00 start.
        val thursdayStart = wednesdayAt(9, 0).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        org.junit.Assert.assertEquals(
            thursdayStart.timeInMillis,
            nextRitualTransitionMillis(listOf(s), wednesdayAt(18, 0).timeInMillis)
        )
    }

    @Test
    fun `nextRitualTransitionMillis handles overnight ends and empty schedules`() {
        val fridayOnly = setOf(com.infinicada.focuspocus.model.DayOfWeek.FRIDAY)
        val s = schedule(fridayOnly, "22:00", "06:00")
        // Wednesday noon -> next transition is Friday 22:00.
        val fridayStart = wednesdayAt(22, 0).apply { add(java.util.Calendar.DAY_OF_YEAR, 2) }
        org.junit.Assert.assertEquals(
            fridayStart.timeInMillis,
            nextRitualTransitionMillis(listOf(s), wednesdayAt(12, 0).timeInMillis)
        )
        // Friday 23:00 -> next transition is Saturday 06:00 (the overnight end).
        val fridayNight = wednesdayAt(23, 0).apply { add(java.util.Calendar.DAY_OF_YEAR, 2) }
        val saturdayEnd = wednesdayAt(6, 0).apply { add(java.util.Calendar.DAY_OF_YEAR, 3) }
        org.junit.Assert.assertEquals(
            saturdayEnd.timeInMillis,
            nextRitualTransitionMillis(listOf(s), fridayNight.timeInMillis)
        )
        org.junit.Assert.assertNull(nextRitualTransitionMillis(emptyList(), wednesdayAt(12, 0).timeInMillis))
        // Malformed times are skipped rather than crashing the scheduler.
        org.junit.Assert.assertNull(
            nextRitualTransitionMillis(
                listOf(schedule(weekdays, "banana", "17:00")),
                wednesdayAt(12, 0).timeInMillis
            )
        )
    }
}
