package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.PactManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `takeLapsedAllowances returns every lapsed expiry and leaves active ones alone`() {
        manager.grantAllowance(pkg, minutes = 5, now = t0)
        manager.grantAllowance("com.other.app", minutes = 30, now = t0)
        val now = t0 + 10 * 60 * 1000L

        assertEquals(mapOf(pkg to t0 + 5 * 60 * 1000L), manager.takeLapsedAllowances(now))
        assertEquals(t0 + 30 * 60 * 1000L, manager.getAllowanceExpiry("com.other.app", now))
    }

    @Test
    fun `takeLapsedAllowances is take-once`() {
        manager.grantAllowance(pkg, minutes = 5, now = t0)
        val now = t0 + 10 * 60 * 1000L

        assertEquals(1, manager.takeLapsedAllowances(now).size)
        assertTrue(manager.takeLapsedAllowances(now).isEmpty())
        assertNull(manager.takeLapsedAllowance(pkg, now))
    }

    @Test
    fun `grantAllowances opens every named package on the same shared expiry`() {
        manager.grantAllowances(setOf(pkg, "com.other.app"), minutes = 15, now = t0)

        val expiry = t0 + 15 * 60 * 1000L
        assertEquals(expiry, manager.getAllowanceExpiry(pkg, t0))
        assertEquals(expiry, manager.getAllowanceExpiry("com.other.app", t0))
    }

    @Test
    fun `revokeAllowances drops every named package and leaves the rest`() {
        manager.grantAllowance(pkg, minutes = 10, now = t0)
        manager.grantAllowance("com.other.app", minutes = 10, now = t0)

        manager.revokeAllowances(setOf(pkg))

        assertNull(manager.getAllowanceExpiry(pkg, t0))
        assertEquals(t0 + 10 * 60 * 1000L, manager.getAllowanceExpiry("com.other.app", t0))
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
    fun `revokeAllowance drops an active allowance`() {
        manager.grantAllowance(pkg, minutes = 10, now = t0)
        manager.revokeAllowance(pkg)

        assertNull(manager.getAllowanceExpiry(pkg, t0))
        // Nothing left to lapse either — revocation is not a lapse.
        assertNull(manager.takeLapsedAllowance(pkg, t0 + 11 * 60 * 1000L))
    }

    @Test
    fun `revokeAllowance is a no-op for unknown packages`() {
        manager.revokeAllowance("com.example.unknown")
        assertNull(manager.getAllowanceExpiry("com.example.unknown", t0))
    }
}
