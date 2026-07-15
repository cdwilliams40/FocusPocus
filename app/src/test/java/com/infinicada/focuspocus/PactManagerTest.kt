package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PactManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: PactManager

    private val pkg = "com.example.social"
    private val t0 = 1_000_000_000_000L

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = PactManager(prefs, Gson())
    }

    @Test
    fun `no allowance by default`() {
        assertNull(manager.getAllowanceExpiry(pkg, t0))
        assertNull(manager.takeLapsedAllowance(pkg, t0))
    }

    @Test
    fun `granted allowance is active until its expiry`() {
        manager.grantAllowance(pkg, minutes = 10, now = t0)
        val expectedExpiry = t0 + 10 * 60 * 1000L

        assertEquals(expectedExpiry, manager.getAllowanceExpiry(pkg, t0))
        assertEquals(expectedExpiry, manager.getAllowanceExpiry(pkg, expectedExpiry - 1))
        assertNull(manager.getAllowanceExpiry(pkg, expectedExpiry))
    }

    @Test
    fun `takeLapsedAllowance leaves an active allowance untouched`() {
        manager.grantAllowance(pkg, minutes = 5, now = t0)

        assertNull(manager.takeLapsedAllowance(pkg, t0 + 60 * 1000L))
        assertEquals(t0 + 5 * 60 * 1000L, manager.getAllowanceExpiry(pkg, t0 + 60 * 1000L))
    }

    @Test
    fun `takeLapsedAllowance returns the expiry once and removes the entry`() {
        manager.grantAllowance(pkg, minutes = 5, now = t0)
        val expiry = t0 + 5 * 60 * 1000L

        assertEquals(expiry, manager.takeLapsedAllowance(pkg, expiry + 1))
        // Second call: entry gone
        assertNull(manager.takeLapsedAllowance(pkg, expiry + 1))
        assertNull(manager.getAllowanceExpiry(pkg, expiry + 1))
    }

    @Test
    fun `getLapsedAllowances splits lapsed from active without removing either`() {
        manager.grantAllowance(pkg, minutes = 5, now = t0)
        manager.grantAllowance("com.other.app", minutes = 30, now = t0)
        val now = t0 + 10 * 60 * 1000L

        assertEquals(mapOf(pkg to t0 + 5 * 60 * 1000L), manager.getLapsedAllowances(now))
        // Reading must not consume: the lapsed entry is still takeable, the
        // active one still active.
        assertEquals(t0 + 5 * 60 * 1000L, manager.takeLapsedAllowance(pkg, now))
        assertEquals(t0 + 30 * 60 * 1000L, manager.getAllowanceExpiry("com.other.app", now))
    }

    @Test
    fun `allowances survive a manager restart via prefs`() {
        manager.grantAllowance(pkg, minutes = 10, now = t0)

        val restarted = PactManager(prefs, Gson())
        assertEquals(t0 + 10 * 60 * 1000L, restarted.getAllowanceExpiry(pkg, t0))
    }

    @Test
    fun `allowances are tracked per package`() {
        manager.grantAllowance(pkg, minutes = 10, now = t0)
        manager.grantAllowance("com.other.app", minutes = 2, now = t0)

        val otherExpiry = t0 + 2 * 60 * 1000L
        assertEquals(otherExpiry, manager.takeLapsedAllowance("com.other.app", otherExpiry + 1))
        // Taking one package's lapsed allowance must not disturb the other's
        assertEquals(t0 + 10 * 60 * 1000L, manager.getAllowanceExpiry(pkg, otherExpiry + 1))
    }

    @Test
    fun `choicesFor caps the ladder at the configured max`() {
        assertEquals(listOf(2, 5, 10, 15), PactManager.choicesFor(config(pactMaxMinutes = 15)))
        assertEquals(listOf(2, 5), PactManager.choicesFor(config(pactMaxMinutes = 5)))
        assertEquals(listOf(2, 5, 10, 15, 30), PactManager.choicesFor(config(pactMaxMinutes = 30)))
    }

    @Test
    fun `choicesFor falls back to 15 minutes for legacy configs without the field`() {
        // Gson deserializes configs persisted before pactMaxMinutes existed as 0
        assertEquals(listOf(2, 5, 10, 15), PactManager.choicesFor(config(pactMaxMinutes = 0)))
    }

    @Test
    fun `choicesFor offers the max itself when it undercuts the ladder`() {
        assertEquals(listOf(1), PactManager.choicesFor(config(pactMaxMinutes = 1)))
    }

    @Test
    fun `groups are persisted, replaced by enchantment name, and deleted`() {
        manager.saveGroup(PactGroup(blockerName = "Doomscroll", pactMaxMinutes = 10))
        manager.saveGroup(PactGroup(blockerName = "Games", pactMaxMinutes = 5))
        assertEquals(2, manager.getGroups().size)

        // Same enchantment name replaces rather than duplicates
        manager.saveGroup(PactGroup(blockerName = "Doomscroll", pactMaxMinutes = 30))
        assertEquals(2, manager.getGroups().size)
        assertEquals(30, manager.getGroups().first { it.blockerName == "Doomscroll" }.pactMaxMinutes)

        manager.deleteGroup("Doomscroll")
        assertEquals(listOf("Games"), manager.getGroups().map { it.blockerName })
    }

    @Test
    fun `groups stored without a blockerName are dropped on load`() {
        // Simulates records written by a build with broken R8 keep rules (v1.4):
        // Gson instantiates via Unsafe, so a missing field stays null even though
        // the Kotlin type is non-null.
        prefs.putString(
            Constants.PrefsKeys.PACT_GROUPS,
            """[{"pactMaxMinutes":15},{"blockerName":"Social","pactMaxMinutes":10}]"""
        )
        assertEquals(listOf("Social"), manager.getGroups().map { it.blockerName })
    }

    @Test
    fun `groups survive a manager restart via prefs`() {
        manager.saveGroup(PactGroup(blockerName = "Doomscroll"))

        val restarted = PactManager(prefs, Gson())
        assertEquals(1, restarted.getGroups().size)
    }

    @Test
    fun `toAppTimeLimit carries group settings into a pact config`() {
        val group = PactGroup(
            blockerName = "Doomscroll",
            pactMaxMinutes = 10,
            cooldownMinutes = 45,
            pactAlternativePackage = "com.kindle",
            dailyLimitMinutes = 0
        )
        val config = group.toAppTimeLimit(pkg)

        assertEquals(pkg, config.packageName)
        assertEquals(true, config.pactModeEnabled)
        assertEquals(10, config.pactMaxMinutes)
        assertEquals(45, config.cooldownMinutes)
        assertEquals("com.kindle", config.pactAlternativePackage)
        assertEquals(0, config.dailyLimitMinutes)
        assertEquals(0, config.sessionLimitMinutes)
    }

    private fun config(pactMaxMinutes: Int) = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = 60,
        pactModeEnabled = true,
        pactMaxMinutes = pactMaxMinutes
    )
}
