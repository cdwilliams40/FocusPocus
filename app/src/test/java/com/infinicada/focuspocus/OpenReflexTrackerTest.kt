package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.OpenReflexTracker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OpenReflexTrackerTest {

    private lateinit var prefs: FakeSharedPreferences
    private var today = "20260712"
    private lateinit var tracker: OpenReflexTracker

    private val pkg = "com.example.social"

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        tracker = OpenReflexTracker(prefs, Gson()) { today }
    }

    @Test
    fun `no stats by default`() {
        assertEquals(AppOpenStats(0, 0), tracker.getStats(pkg))
    }

    @Test
    fun `opens are counted per app`() {
        tracker.recordOpen(pkg)
        tracker.recordOpen(pkg)
        tracker.recordOpen("com.other.app")

        assertEquals(AppOpenStats(opens = 2, reflexOpens = 0), tracker.getStats(pkg))
        assertEquals(AppOpenStats(opens = 1, reflexOpens = 0), tracker.getStats("com.other.app"))
    }

    @Test
    fun `a close under the threshold counts as a reflex open`() {
        tracker.recordOpen(pkg)
        tracker.recordClose(pkg, dwellMs = 10_000L)

        assertEquals(AppOpenStats(opens = 1, reflexOpens = 1), tracker.getStats(pkg))
    }

    @Test
    fun `a close at or over the threshold is not a reflex`() {
        tracker.recordOpen(pkg)
        tracker.recordClose(pkg, dwellMs = OpenReflexTracker.REFLEX_THRESHOLD_MS)
        tracker.recordClose(pkg, dwellMs = 5 * 60_000L)

        assertEquals(AppOpenStats(opens = 1, reflexOpens = 0), tracker.getStats(pkg))
    }

    @Test
    fun `a close without a recorded open is ignored`() {
        tracker.recordClose(pkg, dwellMs = 5_000L)

        assertEquals(AppOpenStats(0, 0), tracker.getStats(pkg))
    }

    @Test
    fun `negative dwell is ignored`() {
        tracker.recordOpen(pkg)
        tracker.recordClose(pkg, dwellMs = -1L)

        assertEquals(AppOpenStats(opens = 1, reflexOpens = 0), tracker.getStats(pkg))
    }

    @Test
    fun `today's counters reset when the date rolls over, history is retained`() {
        tracker.recordOpen(pkg)
        tracker.recordClose(pkg, dwellMs = 1_000L)
        assertEquals(AppOpenStats(1, 1), tracker.getStats(pkg))

        today = "20260713"

        assertEquals(AppOpenStats(0, 0), tracker.getStats(pkg))
        tracker.recordOpen(pkg)
        assertEquals(AppOpenStats(1, 0), tracker.getStats(pkg))

        val history = tracker.getDailyStats()
        assertEquals(AppOpenStats(1, 1), history["20260712"]?.get(pkg))
        assertEquals(AppOpenStats(1, 0), history["20260713"]?.get(pkg))
    }

    @Test
    fun `history older than the retention window is pruned on write`() {
        tracker.recordOpen(pkg)

        today = "20270101" // far beyond the 30-day window

        tracker.recordOpen(pkg)
        val history = tracker.getDailyStats()
        assertEquals(setOf("20270101"), history.keys)
    }

    @Test
    fun `legacy single-day storage shape is migrated`() {
        prefs.putString(
            Constants.PrefsKeys.APP_OPEN_STATS,
            """{"date":"20260712","stats":{"$pkg":{"opens":4,"reflexOpens":2}}}"""
        )

        assertEquals(AppOpenStats(4, 2), tracker.getStats(pkg))
        tracker.recordOpen(pkg)
        assertEquals(AppOpenStats(5, 2), tracker.getStats(pkg))
    }

    @Test
    fun `stats survive a tracker restart via prefs`() {
        tracker.recordOpen(pkg)
        tracker.recordClose(pkg, dwellMs = 2_000L)

        val restarted = OpenReflexTracker(prefs, Gson()) { today }
        assertEquals(AppOpenStats(1, 1), restarted.getStats(pkg))
    }
}
