package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.GuardSchedule
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardScheduleTest {

    private fun config(
        days: Set<DayOfWeek>? = null,
        start: String? = null,
        end: String? = null
    ) = AppTimeLimit(
        packageName = "com.test",
        dailyLimitMinutes = 0,
        pactModeEnabled = true,
        activeDays = days,
        activeStartTime = start,
        activeEndTime = end
    )

    private fun activeAt(
        c: AppTimeLimit,
        day: DayOfWeek,
        minute: Int
    ): Boolean = GuardSchedule.isActiveAt(c, day, GuardSchedule.previousDayOf(day), minute)

    // ── hasSchedule ──

    @Test
    fun `config without schedule fields has no schedule`() {
        assertFalse(GuardSchedule.hasSchedule(config()))
        assertFalse(GuardSchedule.hasSchedule(config(days = emptySet(), start = "", end = "")))
    }

    @Test
    fun `config with days or times has a schedule`() {
        assertTrue(GuardSchedule.hasSchedule(config(days = setOf(DayOfWeek.MONDAY))))
        assertTrue(GuardSchedule.hasSchedule(config(start = "09:00", end = "17:00")))
    }

    // ── no schedule → always active ──

    @Test
    fun `no restrictions means active any time`() {
        val c = config()
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 0))
        assertTrue(activeAt(c, DayOfWeek.SUNDAY, 23 * 60 + 59))
    }

    // ── day-only gating ──

    @Test
    fun `day gating without times applies all day`() {
        val c = config(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 0))
        assertTrue(activeAt(c, DayOfWeek.TUESDAY, 23 * 60 + 59))
        assertFalse(activeAt(c, DayOfWeek.WEDNESDAY, 12 * 60))
    }

    // ── same-day window ──

    @Test
    fun `same-day window is active only inside it`() {
        val c = config(start = "09:00", end = "17:00")
        assertFalse(activeAt(c, DayOfWeek.MONDAY, 8 * 60 + 59))
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 9 * 60))
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 16 * 60 + 59))
        assertFalse(activeAt(c, DayOfWeek.MONDAY, 17 * 60))
    }

    @Test
    fun `same-day window respects day gating`() {
        val c = config(days = setOf(DayOfWeek.MONDAY), start = "09:00", end = "17:00")
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 12 * 60))
        assertFalse(activeAt(c, DayOfWeek.TUESDAY, 12 * 60))
    }

    // ── overnight window ──

    @Test
    fun `overnight window spans midnight into the next morning`() {
        val c = config(start = "21:00", end = "06:00")
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 21 * 60))
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 23 * 60 + 59))
        // Small hours of Tuesday belong to Monday's window
        assertTrue(activeAt(c, DayOfWeek.TUESDAY, 3 * 60))
        assertFalse(activeAt(c, DayOfWeek.TUESDAY, 6 * 60))
        assertFalse(activeAt(c, DayOfWeek.MONDAY, 12 * 60))
    }

    @Test
    fun `overnight window is anchored to its start day`() {
        val c = config(days = setOf(DayOfWeek.FRIDAY), start = "22:00", end = "02:00")
        assertTrue(activeAt(c, DayOfWeek.FRIDAY, 23 * 60))
        // Saturday 01:00 belongs to Friday night
        assertTrue(activeAt(c, DayOfWeek.SATURDAY, 60))
        // Saturday 23:00 is Saturday's own evening — not gated
        assertFalse(activeAt(c, DayOfWeek.SATURDAY, 23 * 60))
        // Friday 01:00 belongs to Thursday night — not gated
        assertFalse(activeAt(c, DayOfWeek.FRIDAY, 60))
    }

    // ── degenerate inputs ──

    @Test
    fun `zero-length window falls back to day gating`() {
        val c = config(days = setOf(DayOfWeek.MONDAY), start = "09:00", end = "09:00")
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 12 * 60))
        assertFalse(activeAt(c, DayOfWeek.TUESDAY, 12 * 60))
    }

    @Test
    fun `malformed times fall back to day gating`() {
        val c = config(days = setOf(DayOfWeek.MONDAY), start = "nope", end = "17:00")
        assertTrue(activeAt(c, DayOfWeek.MONDAY, 3 * 60))
        assertFalse(activeAt(c, DayOfWeek.TUESDAY, 3 * 60))
    }

    // ── parseMinutes ──

    @Test
    fun `parseMinutes handles valid and invalid input`() {
        assertEquals(0, GuardSchedule.parseMinutes("00:00"))
        assertEquals(21 * 60 + 30, GuardSchedule.parseMinutes("21:30"))
        assertNull(GuardSchedule.parseMinutes(null))
        assertNull(GuardSchedule.parseMinutes(""))
        assertNull(GuardSchedule.parseMinutes("24:00"))
        assertNull(GuardSchedule.parseMinutes("12:60"))
        assertNull(GuardSchedule.parseMinutes("noon"))
        assertNull(GuardSchedule.parseMinutes("12"))
    }

    // ── previousDayOf ──

    @Test
    fun `previousDayOf wraps the week`() {
        assertEquals(DayOfWeek.SUNDAY, GuardSchedule.previousDayOf(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.MONDAY, GuardSchedule.previousDayOf(DayOfWeek.TUESDAY))
        assertEquals(DayOfWeek.SATURDAY, GuardSchedule.previousDayOf(DayOfWeek.SUNDAY))
    }

    // ── PactGroup passthrough ──

    @Test
    fun `pact group schedule fields survive toAppTimeLimit`() {
        val group = com.infinicada.focuspocus.model.PactGroup(
            blockerName = "Doomscroll",
            activeDays = setOf(DayOfWeek.MONDAY),
            activeStartTime = "21:00",
            activeEndTime = "23:00"
        )
        val asConfig = group.toAppTimeLimit("com.test")
        assertEquals(setOf(DayOfWeek.MONDAY), asConfig.activeDays)
        assertEquals("21:00", asConfig.activeStartTime)
        assertEquals("23:00", asConfig.activeEndTime)
        assertTrue(GuardSchedule.hasSchedule(asConfig))
    }
}
