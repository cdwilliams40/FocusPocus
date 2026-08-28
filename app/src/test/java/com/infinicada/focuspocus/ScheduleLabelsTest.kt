package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.ui.ScheduleLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ScheduleLabelsTest {

    private val english = Locale.ENGLISH
    private val french = Locale.FRENCH
    private val france = Locale.FRANCE
    private val us = Locale.US

    // ── Stored times ──

    @Test
    fun `stored time is zero-padded ASCII`() {
        assertEquals("09:05", ScheduleLabels.storedTime(9, 5))
        assertEquals("22:00", ScheduleLabels.storedTime(22, 0))
        assertEquals("00:00", ScheduleLabels.storedTime(0, 0))
    }

    /**
     * The regression this guards: the stored form used to come from
     * `"%02d:%02d".format(...)`, which follows the default locale's number
     * system. Under a Persian default it wrote "۲۲:۰۰" into the store, where
     * the schedule tick, GuardWindow and the ritual alarms all expect ASCII.
     */
    @Test
    fun `stored time ignores a non-latin default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            assertEquals("22:00", ScheduleLabels.storedTime(22, 0))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ── Clock display ──

    private val pattern24 = ScheduleLabels.FALLBACK_PATTERN_24H
    private val pattern12 = ScheduleLabels.FALLBACK_PATTERN_12H

    @Test
    fun `24-hour display keeps the stored shape`() {
        assertEquals("22:00", ScheduleLabels.clockTime("22:00", pattern24, english))
        assertEquals("07:30", ScheduleLabels.clockTime("07:30", pattern24, english))
    }

    @Test
    fun `12-hour display uses the locale's am pm form`() {
        val evening = ScheduleLabels.clockTime("22:00", pattern12, us)
        assertTrue(evening, evening.startsWith("10:00"))
        assertNotEquals("22:00", evening)

        val morning = ScheduleLabels.clockTime("07:30", pattern12, us)
        assertTrue(morning, morning.startsWith("7:30"))
    }

    @Test
    fun `midnight and noon survive the 12-hour conversion`() {
        assertTrue(ScheduleLabels.clockTime("00:00", pattern12, us).startsWith("12:00"))
        assertTrue(ScheduleLabels.clockTime("12:00", pattern12, us).startsWith("12:00"))
    }

    @Test
    fun `unparseable stored times are passed through untouched`() {
        assertEquals("", ScheduleLabels.clockTime(null, pattern24, english))
        assertEquals("", ScheduleLabels.clockTime("", pattern24, english))
        assertEquals("nonsense", ScheduleLabels.clockTime("nonsense", pattern12, english))
        assertEquals("99:99", ScheduleLabels.clockTime("99:99", pattern24, english))
    }

    @Test
    fun `the hour-minute overload matches the stored-string overload`() {
        for (hour in 0..23) {
            for (minute in intArrayOf(0, 30, 59)) {
                val stored = ScheduleLabels.storedTime(hour, minute)
                assertEquals(
                    ScheduleLabels.clockTime(stored, pattern12, us),
                    ScheduleLabels.clockTime(hour, minute, pattern12, us)
                )
            }
        }
    }

    /**
     * ICU can answer a skeleton with pattern letters java.time refuses ("j" is
     * skeleton-only, for instance). Formatting must degrade, not throw: this
     * runs inside composition.
     */
    @Test
    fun `a pattern java-time cannot parse falls back instead of throwing`() {
        assertEquals("22:00", ScheduleLabels.clockTime("22:00", "j:mm", english))
        assertTrue(ScheduleLabels.clockTime("22:00", "h:mm j", english).startsWith("10:00"))
    }

    // ── Day labels ──

    @Test
    fun `day labels are localized`() {
        assertEquals("Monday", ScheduleLabels.full(DayOfWeek.MONDAY, english))
        assertTrue(ScheduleLabels.short(DayOfWeek.MONDAY, english).startsWith("Mon"))

        // The bug this replaces: every label came from the enum's own name, so
        // a French build still read "Mon"/"M".
        assertTrue(
            ScheduleLabels.full(DayOfWeek.MONDAY, french).lowercase(french).startsWith("lun")
        )
        assertEquals("l", ScheduleLabels.narrow(DayOfWeek.MONDAY, french).lowercase(french))
    }

    @Test
    fun `every day maps to its own java-time counterpart`() {
        val expected = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )
        assertEquals(expected, DayOfWeek.entries.map { ScheduleLabels.full(it, english) })
    }

    // ── Week order ──

    @Test
    fun `week order follows the locale's first day`() {
        assertEquals(DayOfWeek.MONDAY, ScheduleLabels.daysInWeekOrder(france).first())
        assertEquals(DayOfWeek.SUNDAY, ScheduleLabels.daysInWeekOrder(us).first())
    }

    @Test
    fun `a region-less locale keeps the monday-first order`() {
        // "fr" carries no country, so there is no week-start data to read —
        // CLDR's root would answer Sunday, which is not a localization.
        assertEquals(DayOfWeek.MONDAY, ScheduleLabels.daysInWeekOrder(french).first())
        assertEquals(DayOfWeek.MONDAY, ScheduleLabels.daysInWeekOrder(english).first())
    }

    @Test
    fun `week order always covers all seven days exactly once`() {
        for (locale in listOf(us, france, english, Locale.forLanguageTag("ar-EG"))) {
            val order = ScheduleLabels.daysInWeekOrder(locale)
            assertEquals(7, order.size)
            assertEquals(DayOfWeek.entries.toSet(), order.toSet())
        }
    }

    // ── Summaries ──

    @Test
    fun `summaries render in week order, not tap order`() {
        // A Set built the way the editor builds it: the order the user tapped.
        val tapped = linkedSetOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertEquals("M W F", ScheduleLabels.narrowSummary(tapped, english))
        assertEquals(
            listOf("Mon", "Wed", "Fri"),
            ScheduleLabels.shortSummary(tapped, english).split(", ").map { it.take(3) }
        )
    }

    @Test
    fun `summaries start on the locale's first day`() {
        val weekend = setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals("S S", ScheduleLabels.narrowSummary(weekend, us))
        assertEquals(
            DayOfWeek.SUNDAY,
            ScheduleLabels.daysInWeekOrder(us).first { it in weekend }
        )
        assertEquals(
            DayOfWeek.SATURDAY,
            ScheduleLabels.daysInWeekOrder(france).first { it in weekend }
        )
    }

    @Test
    fun `an empty day set summarizes to nothing`() {
        assertEquals("", ScheduleLabels.narrowSummary(emptySet(), english))
        assertEquals("", ScheduleLabels.shortSummary(emptySet(), english))
    }
}
