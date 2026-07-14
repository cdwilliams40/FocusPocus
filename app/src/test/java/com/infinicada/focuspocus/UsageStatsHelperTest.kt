package com.infinicada.focuspocus

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {

    // --- aggregateForegroundTime ---

    private val windowStart = 1_000_000L
    private val windowEnd = windowStart + 60 * 60_000L // one hour window

    private fun resumed(pkg: String, t: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_RESUMED, t)

    private fun paused(pkg: String, t: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_PAUSED, t)

    private fun stopped(pkg: String, t: Long) =
        ForegroundEvent(pkg, UsageEvents.Event.ACTIVITY_STOPPED, t)

    private fun shutdown(t: Long) =
        ForegroundEvent("", UsageEvents.Event.DEVICE_SHUTDOWN, t)

    @Test
    fun aggregate_resumePausePair_countsSessionDuration() {
        val events = listOf(
            resumed("com.app", windowStart + 5_000),
            paused("com.app", windowStart + 65_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(60_000L, totals["com.app"])
    }

    @Test
    fun aggregate_multipleSessions_accumulate() {
        val events = listOf(
            resumed("com.app", windowStart + 1_000),
            paused("com.app", windowStart + 11_000),
            resumed("com.app", windowStart + 20_000),
            paused("com.app", windowStart + 50_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(40_000L, totals["com.app"])
    }

    @Test
    fun aggregate_stillForegroundAtEnd_countsUpToWindowEnd() {
        val events = listOf(resumed("com.app", windowEnd - 120_000))
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(120_000L, totals["com.app"])
    }

    @Test
    fun aggregate_pauseWithoutResume_countsFromWindowStart() {
        // App was in the foreground when the window began (e.g. in use at midnight).
        val events = listOf(paused("com.app", windowStart + 30_000))
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(30_000L, totals["com.app"])
    }

    @Test
    fun aggregate_preWindowResume_countsFromWindowStart() {
        // App resumed before the window (supplied by the query lookback) and never
        // paused: it was foreground across the whole window despite producing no
        // event inside it (e.g. a video app held open across midnight).
        val events = listOf(resumed("com.app", windowStart - 60_000))
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(windowEnd - windowStart, totals["com.app"])
    }

    @Test
    fun aggregate_preWindowSession_contributesNothing() {
        // A resume/pause pair entirely before the window must not leak time in.
        val events = listOf(
            resumed("com.app", windowStart - 120_000),
            paused("com.app", windowStart - 60_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(0L, totals["com.app"] ?: 0L)
    }

    @Test
    fun aggregate_stopWithoutResume_isIgnored() {
        // App left the foreground before the window; only its late STOPPED landed inside.
        val events = listOf(stopped("com.app", windowStart + 30_000))
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertFalse(totals.containsKey("com.app"))
    }

    @Test
    fun aggregate_pauseAfterStop_doesNotDoubleCount() {
        val events = listOf(
            resumed("com.app", windowStart + 1_000),
            stopped("com.app", windowStart + 11_000),
            paused("com.app", windowStart + 12_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(10_000L, totals["com.app"])
    }

    @Test
    fun aggregate_duplicateResumes_keepEarliestStart() {
        // Two activities of one app resuming back to back must not restart the session.
        val events = listOf(
            resumed("com.app", windowStart + 1_000),
            resumed("com.app", windowStart + 5_000),
            paused("com.app", windowStart + 11_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(10_000L, totals["com.app"])
    }

    @Test
    fun aggregate_deviceShutdown_closesOpenSessions() {
        val events = listOf(
            resumed("com.app", windowStart + 1_000),
            shutdown(windowStart + 31_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(30_000L, totals["com.app"])
    }

    @Test
    fun aggregate_eventsAfterWindowEnd_areIgnored() {
        val events = listOf(
            resumed("com.app", windowStart + 1_000),
            paused("com.app", windowStart + 11_000),
            resumed("com.other", windowEnd + 1_000),
            paused("com.other", windowEnd + 60_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(10_000L, totals["com.app"])
        assertFalse(totals.containsKey("com.other"))
    }

    @Test
    fun aggregate_independentPackages_trackedSeparately() {
        val events = listOf(
            resumed("com.a", windowStart + 1_000),
            paused("com.a", windowStart + 11_000),
            resumed("com.b", windowStart + 11_000),
            paused("com.b", windowStart + 41_000)
        )
        val totals = UsageStatsHelper.aggregateForegroundTime(events, windowStart, windowEnd)
        assertEquals(10_000L, totals["com.a"])
        assertEquals(30_000L, totals["com.b"])
    }

    @Test
    fun aggregate_emptyEvents_returnsEmptyMap() {
        val totals = UsageStatsHelper.aggregateForegroundTime(emptyList(), windowStart, windowEnd)
        assertTrue(totals.isEmpty())
    }

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
