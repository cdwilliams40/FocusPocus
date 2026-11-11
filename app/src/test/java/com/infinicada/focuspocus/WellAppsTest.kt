package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WellAppsTest {

    private lateinit var prefs: FakeSharedPreferences
    private val gson = Gson()
    private val t0 = 1_000_000_000_000L

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `adding is instant and idempotent`() {
        WellApps.add(prefs, gson, "com.a")
        WellApps.add(prefs, gson, "com.a")
        assertEquals(listOf(WellApp("com.a")), WellApps.load(prefs, gson))
    }

    @Test
    fun `removal waits 24 hours and then applies`() {
        WellApps.add(prefs, gson, "com.a")
        WellApps.requestRemoval(prefs, gson, "com.a", now = t0)

        assertFalse(WellApps.applyDueRemovals(prefs, gson, now = t0 + WellApps.REMOVAL_DELAY_MS - 1))
        assertEquals(setOf("com.a"), WellApps.packages(prefs, gson))

        assertTrue(WellApps.applyDueRemovals(prefs, gson, now = t0 + WellApps.REMOVAL_DELAY_MS))
        assertTrue(WellApps.packages(prefs, gson).isEmpty())
        assertTrue(WellApps.pendingRemovals(prefs, gson).isEmpty())
    }

    @Test
    fun `asking again does not restart the clock and cancelling keeps the app`() {
        WellApps.add(prefs, gson, "com.a")
        WellApps.requestRemoval(prefs, gson, "com.a", now = t0)
        WellApps.requestRemoval(prefs, gson, "com.a", now = t0 + 1_000)
        assertEquals(mapOf("com.a" to t0 + WellApps.REMOVAL_DELAY_MS), WellApps.pendingRemovals(prefs, gson))

        WellApps.cancelRemoval(prefs, gson, "com.a")
        assertFalse(WellApps.applyDueRemovals(prefs, gson, now = t0 + 2 * WellApps.REMOVAL_DELAY_MS))
        assertEquals(setOf("com.a"), WellApps.packages(prefs, gson))
    }

    @Test
    fun `re-adding an app on its way out cancels the removal`() {
        WellApps.add(prefs, gson, "com.a")
        WellApps.requestRemoval(prefs, gson, "com.a", now = t0)
        WellApps.add(prefs, gson, "com.a")
        assertTrue(WellApps.pendingRemovals(prefs, gson).isEmpty())
    }

    @Test
    fun `an app can't be its own substitute`() {
        WellApps.add(prefs, gson, "com.a")
        WellApps.setSubstitute(prefs, gson, "com.a", "com.read")
        assertEquals("com.read", WellApps.find(prefs, gson, "com.a")?.substitutePackage)
        WellApps.setSubstitute(prefs, gson, "com.a", "com.a")
        assertEquals(null, WellApps.find(prefs, gson, "com.a")?.substitutePackage)
    }

    @Test
    fun `damaged records are dropped on load`() {
        prefs.putString(Constants.PrefsKeys.WELL_APPS, """[{"packageName":"com.a"},{},null]""")
        assertEquals(listOf(WellApp("com.a")), WellApps.load(prefs, gson))
    }
}
