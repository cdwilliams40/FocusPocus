package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.model.AppTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionCooldownManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var manager: SessionCooldownManager

    private val pkg = "com.example.social"
    private val t0 = 1_000_000_000_000L

    private val config = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = 0,
        sessionLimitMinutes = 10,
        cooldownMinutes = 30
    )

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        manager = SessionCooldownManager(prefs, Gson())
    }

    private fun storedJson(): String? =
        prefs.getString(Constants.PrefsKeys.APP_COOLDOWN_STATES, null)

    @Test
    fun `peekActiveCooldowns returns active entries`() {
        manager.startCooldown(pkg, config, now = t0)

        val peeked = manager.peekActiveCooldowns(now = t0 + 60_000)
        assertEquals(setOf(pkg), peeked.keys)
        assertEquals(t0 + 30 * 60_000L, peeked.getValue(pkg).cooldownExpiryMillis)
    }

    @Test
    fun `peekActiveCooldowns filters expired entries without pruning them`() {
        manager.startCooldown(pkg, config, now = t0)
        val afterExpiry = t0 + 31 * 60_000L

        assertTrue(manager.peekActiveCooldowns(now = afterExpiry).isEmpty())
        // The expired entry must still be persisted: pruning belongs to the
        // enforcement side, and it also carries the escalation counter.
        assertTrue(storedJson()?.contains(pkg) == true)
    }

    @Test
    fun `getCooldownState prunes expired entries, unlike peek`() {
        manager.startCooldown(pkg, config, now = t0)
        val afterExpiry = t0 + 31 * 60_000L

        assertNull(manager.getCooldownState(pkg, now = afterExpiry))
        assertFalse(storedJson()?.contains(pkg) == true)
    }

    @Test
    fun `peekActiveCooldowns is empty when nothing was stored`() {
        assertTrue(manager.peekActiveCooldowns(now = t0).isEmpty())
        assertNull(storedJson())
    }
}
