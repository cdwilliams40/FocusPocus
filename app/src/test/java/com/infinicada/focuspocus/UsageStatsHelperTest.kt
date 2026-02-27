package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsHelperTest {

    @Test
    fun formatDuration_zeroMillis_returns0m() {
        assertEquals("0m", UsageStatsHelper.formatDuration(0))
    }

    @Test
    fun formatDuration_lessThanOneMinute_returns0m() {
        assertEquals("0m", UsageStatsHelper.formatDuration(59000))
    }

    @Test
    fun formatDuration_exactlyOneMinute_returns1m() {
        assertEquals("1m", UsageStatsHelper.formatDuration(60000))
    }

    @Test
    fun formatDuration_minutesOnly_returnsMinutes() {
        assertEquals("59m", UsageStatsHelper.formatDuration(59 * 60 * 1000))
    }

    @Test
    fun formatDuration_exactlyOneHour_returns1h0m() {
        assertEquals("1h 0m", UsageStatsHelper.formatDuration(60 * 60 * 1000))
    }

    @Test
    fun formatDuration_hoursAndMinutes_returnsHoursAndMinutes() {
        // 1h 30m = 90 minutes = 90 * 60 * 1000
        assertEquals("1h 30m", UsageStatsHelper.formatDuration(90 * 60 * 1000))
    }

    @Test
    fun formatDuration_largeDuration_returnsCorrectFormat() {
        // 25h 5m = (25 * 60 + 5) minutes = 1505 minutes
        assertEquals("25h 5m", UsageStatsHelper.formatDuration(1505 * 60 * 1000L))
    }

    @Test
    fun formatDuration_minutesAndSeconds_ignoresSeconds() {
        // 1m 30s = 90000ms. Should format to "1m" as seconds are ignored/truncated
        assertEquals("1m", UsageStatsHelper.formatDuration(90000))
    }

    @Test
    fun formatDuration_hoursAndSeconds_ignoresSeconds() {
        // 1h 0m 30s = 3600000 + 30000 = 3630000ms. Should format to "1h 0m"
        assertEquals("1h 0m", UsageStatsHelper.formatDuration(3630000))
    }
}
