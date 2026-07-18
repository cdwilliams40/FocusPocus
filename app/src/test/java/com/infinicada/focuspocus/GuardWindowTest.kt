package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.GuardWindow
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardWindowTest {

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )

    private fun active(
        days: Set<DayOfWeek>? = null,
        start: String? = null,
        end: String? = null,
        day: DayOfWeek = DayOfWeek.MONDAY,
        previousDay: DayOfWeek = DayOfWeek.SUNDAY,
        minutes: Int = 12 * 60
    ) = GuardWindow.isActive(days, start, end, day, previousDay, minutes)

    // ── No schedule ──

    @Test
    fun `no days and no window means always active`() {
        assertTrue(active())
        assertTrue(active(days = emptySet(), start = "", end = ""))
    }

    // ── Days only ──

    @Test
    fun `days without a window gate on the day alone`() {
        assertTrue(active(days = weekdays, day = DayOfWeek.FRIDAY))
        assertFalse(active(days = weekdays, day = DayOfWeek.SATURDAY))
    }

    // ── Same-day window ──

    @Test
    fun `same-day window is start-inclusive end-exclusive`() {
        val start = "09:00"
        val end = "17:00"
        assertFalse(active(start = start, end = end, minutes = 8 * 60 + 59))
        assertTrue(active(start = start, end = end, minutes = 9 * 60))
        assertTrue(active(start = start, end = end, minutes = 16 * 60 + 59))
        assertFalse(active(start = start, end = end, minutes = 17 * 60))
    }

    @Test
    fun `same-day window with days needs both to match`() {
        assertTrue(
            active(days = weekdays, start = "09:00", end = "17:00", day = DayOfWeek.MONDAY, minutes = 10 * 60)
        )
        assertFalse(
            active(days = weekdays, start = "09:00", end = "17:00", day = DayOfWeek.SATURDAY, minutes = 10 * 60)
        )
    }

    // ── Overnight window ──

    @Test
    fun `overnight window spans evening and following morning`() {
        val start = "22:00"
        val end = "06:00"
        // Friday-only guard, 22:00–06:00.
        val friday = setOf(DayOfWeek.FRIDAY)
        // Friday 23:00 — active (evening part).
        assertTrue(active(days = friday, start = start, end = end, day = DayOfWeek.FRIDAY, minutes = 23 * 60))
        // Saturday 03:00 — active (morning carry-over from Friday).
        assertTrue(
            active(
                days = friday, start = start, end = end,
                day = DayOfWeek.SATURDAY, previousDay = DayOfWeek.FRIDAY, minutes = 3 * 60
            )
        )
        // Friday 03:00 — NOT active (that morning belongs to Thursday's window).
        assertFalse(
            active(
                days = friday, start = start, end = end,
                day = DayOfWeek.FRIDAY, previousDay = DayOfWeek.THURSDAY, minutes = 3 * 60
            )
        )
        // Friday noon — outside the window entirely.
        assertFalse(active(days = friday, start = start, end = end, day = DayOfWeek.FRIDAY, minutes = 12 * 60))
    }

    @Test
    fun `overnight window without days applies every night`() {
        assertTrue(active(start = "22:00", end = "06:00", minutes = 23 * 60))
        assertTrue(active(start = "22:00", end = "06:00", minutes = 5 * 60))
        assertFalse(active(start = "22:00", end = "06:00", minutes = 12 * 60))
    }

    // ── Degenerate input fails open ──

    @Test
    fun `malformed or identical times degrade to all-day`() {
        // Malformed strings: window ignored → always active (no days set).
        assertTrue(active(start = "banana", end = "06:00", minutes = 12 * 60))
        assertTrue(active(start = "25:00", end = "06:00", minutes = 12 * 60))
        // Identical times: zero-duration nonsense → all-day, still day-gated.
        assertTrue(active(days = weekdays, start = "09:00", end = "09:00", day = DayOfWeek.MONDAY))
        assertFalse(active(days = weekdays, start = "09:00", end = "09:00", day = DayOfWeek.SUNDAY))
    }

    // ── parseMinutes ──

    @Test
    fun `parseMinutes accepts HH-mm and rejects garbage`() {
        assertEquals(0, GuardWindow.parseMinutes("00:00"))
        assertEquals(21 * 60 + 30, GuardWindow.parseMinutes("21:30"))
        assertNull(GuardWindow.parseMinutes(null))
        assertNull(GuardWindow.parseMinutes(""))
        assertNull(GuardWindow.parseMinutes("21"))
        assertNull(GuardWindow.parseMinutes("24:00"))
        assertNull(GuardWindow.parseMinutes("12:60"))
        assertNull(GuardWindow.parseMinutes("aa:bb"))
    }

    // ── isScheduled ──

    @Test
    fun `isScheduled detects days or a complete window`() {
        val base = AppTimeLimit(packageName = "a", dailyLimitMinutes = 0)
        assertFalse(GuardWindow.isScheduled(base))
        assertTrue(GuardWindow.isScheduled(base.copy(activeDays = setOf(DayOfWeek.MONDAY))))
        assertTrue(GuardWindow.isScheduled(base.copy(activeStartTime = "21:00", activeEndTime = "07:00")))
        // Half a window is not a schedule.
        assertFalse(GuardWindow.isScheduled(base.copy(activeStartTime = "21:00")))
    }
}
