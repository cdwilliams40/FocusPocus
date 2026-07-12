package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.model.AppTimeLimit
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

    private fun config(pactMaxMinutes: Int) = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = 60,
        pactModeEnabled = true,
        pactMaxMinutes = pactMaxMinutes
    )
}
