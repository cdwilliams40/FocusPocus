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
}
