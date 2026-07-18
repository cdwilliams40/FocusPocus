package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUtilsTest {

    // ----- shouldDeactivateSchedule -----

    @Test
    fun `shouldDeactivate sameDay - before end time returns false`() {
        // Schedule 09:00–17:00, current 10:00
        assertFalse(shouldDeactivateSchedule(10, 0, 9, 0, 17, 0))
    }

    @Test
    fun `shouldDeactivate sameDay - at exact end time returns true`() {
        // Schedule 09:00–17:00, current 17:00
        assertTrue(shouldDeactivateSchedule(17, 0, 9, 0, 17, 0))
    }

    @Test
    fun `shouldDeactivate sameDay - past end time returns true`() {
        // Schedule 09:00–17:00, current 18:30
        assertTrue(shouldDeactivateSchedule(18, 30, 9, 0, 17, 0))
    }

    @Test
    fun `shouldDeactivate sameDay - before start time returns true (missed end across midnight)`() {
        // Schedule 09:00–17:00, current 08:00. The check only runs while the
        // schedule is active, so a pre-start time means yesterday's end was
        // missed (e.g. phone off overnight) and the block must lift now.
        assertTrue(shouldDeactivateSchedule(8, 0, 9, 0, 17, 0))
    }

    @Test
    fun `shouldDeactivate overnight - in active evening window returns false`() {
        // Schedule 22:00–06:00, current 23:30 (still Friday evening)
        assertFalse(shouldDeactivateSchedule(23, 30, 22, 0, 6, 0))
    }

    @Test
    fun `shouldDeactivate overnight - in active morning window returns false`() {
        // Schedule 22:00–06:00, current 03:00 (Saturday morning, still active)
        assertFalse(shouldDeactivateSchedule(3, 0, 22, 0, 6, 0))
    }

    @Test
    fun `shouldDeactivate overnight - at exact end time returns true`() {
        // Schedule 22:00–06:00, current 06:00
        assertTrue(shouldDeactivateSchedule(6, 0, 22, 0, 6, 0))
    }

    @Test
    fun `shouldDeactivate overnight - past end time in morning returns true`() {
        // Schedule 22:00–06:00, current 07:00
        assertTrue(shouldDeactivateSchedule(7, 0, 22, 0, 6, 0))
    }

    @Test
    fun `shouldDeactivate overnight - midday gap returns true`() {
        // Schedule 22:00–06:00, current 14:00 (daytime gap)
        assertTrue(shouldDeactivateSchedule(14, 0, 22, 0, 6, 0))
    }

    // ----- isWithinScheduleWindow -----

    @Test
    fun `isWithinWindow sameDay - before start returns false`() {
        // Schedule 09:00–17:00, current 08:00
        assertFalse(isWithinScheduleWindow(8, 0, 9, 0, 17, 0))
    }

    @Test
    fun `isWithinWindow sameDay - at start returns true`() {
        // Schedule 09:00–17:00, current 09:00
        assertTrue(isWithinScheduleWindow(9, 0, 9, 0, 17, 0))
    }

    @Test
    fun `isWithinWindow sameDay - mid-window returns true`() {
        // Schedule 09:00–17:00, current 12:30
        assertTrue(isWithinScheduleWindow(12, 30, 9, 0, 17, 0))
    }

    @Test
    fun `isWithinWindow sameDay - at end time returns false (exclusive end)`() {
        // Schedule 09:00–17:00, current 17:00
        assertFalse(isWithinScheduleWindow(17, 0, 9, 0, 17, 0))
    }

    @Test
    fun `isWithinWindow sameDay - past end returns false`() {
        // Schedule 09:00–17:00, current 18:00
        assertFalse(isWithinScheduleWindow(18, 0, 9, 0, 17, 0))
    }

    @Test
    fun `isWithinWindow overnight - at start returns true`() {
        // Schedule 22:00–06:00, current 22:00
        assertTrue(isWithinScheduleWindow(22, 0, 22, 0, 6, 0))
    }

    @Test
    fun `isWithinWindow overnight - in active evening window returns true`() {
        // Schedule 22:00–06:00, current 23:30
        assertTrue(isWithinScheduleWindow(23, 30, 22, 0, 6, 0))
    }

    @Test
    fun `isWithinWindow overnight - in active morning carry-over returns true`() {
        // Schedule 22:00–06:00, current 03:00 (early Saturday morning)
        assertTrue(isWithinScheduleWindow(3, 0, 22, 0, 6, 0))
    }

    @Test
    fun `isWithinWindow overnight - at end time returns false (exclusive end)`() {
        // Schedule 22:00–06:00, current 06:00
        assertFalse(isWithinScheduleWindow(6, 0, 22, 0, 6, 0))
    }

    @Test
    fun `isWithinWindow overnight - in daytime gap returns false`() {
        // Schedule 22:00–06:00, current 14:00
        assertFalse(isWithinScheduleWindow(14, 0, 22, 0, 6, 0))
    }

    @Test
    fun `isWithinWindow zero-duration (start equals end) returns false`() {
        // start == end is treated as invalid / never-active
        assertFalse(isWithinScheduleWindow(10, 0, 10, 0, 10, 0))
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
