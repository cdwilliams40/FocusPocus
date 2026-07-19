package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.model.AppTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private val escalatingConfig = config.copy(
        cooldownEscalationEnabled = true,
        cooldownEscalationStepMinutes = 15
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
        // The expired entry must still be persisted: it carries the day's
        // escalation counter, and pruning belongs to the daily rollover.
        assertTrue(storedJson()?.contains(pkg) == true)
    }

    @Test
    fun `getCooldownState filters expired entries without pruning them`() {
        manager.startCooldown(pkg, config, now = t0)
        val afterExpiry = t0 + 31 * 60_000L

        assertNull(manager.getCooldownState(pkg, now = afterExpiry))
        // Same contract as peek: the expired entry survives so startCooldown
        // can escalate the next same-day offence.
        assertTrue(storedJson()?.contains(pkg) == true)
    }

    @Test
    fun `peekActiveCooldowns is empty when nothing was stored`() {
        assertTrue(manager.peekActiveCooldowns(now = t0).isEmpty())
        assertNull(storedJson())
    }

    @Test
    fun `startCooldown blocks for the base duration`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)

        assertTrue(manager.isInCooldown(pkg, now = t0 + 29 * 60_000))
        assertFalse(manager.isInCooldown(pkg, now = t0 + 31 * 60_000))
    }

    @Test
    fun `escalation counter survives cooldown expiry within the same day`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)

        // First cooldown (30m) fully lapses, and the expired state is observed
        // (this used to delete the entry and with it the escalation counter).
        val afterFirst = t0 + 40 * 60_000L
        assertFalse(manager.isInCooldown(pkg, now = afterFirst))
        assertNull(manager.getCooldownState(pkg, now = afterFirst))

        // Second offence the same day must escalate: 30m base + 15m step.
        manager.startCooldown(pkg, escalatingConfig, now = afterFirst)
        val second = manager.getCooldownState(pkg, now = afterFirst)
        assertNotNull(second)
        assertEquals(2, second!!.cooldownNumber)
        assertEquals(afterFirst + 45 * 60_000L, second.cooldownExpiryMillis)
    }

    @Test
    fun `resetDailyCooldowns prunes expired entries and resets active counters`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)
        manager.startCooldown("com.other.app", escalatingConfig, now = t0 + 60 * 60_000L)

        // pkg's cooldown has expired; com.other.app's is still running.
        val midnight = t0 + 75 * 60_000L
        manager.resetDailyCooldowns(now = midnight)

        // Expired entry pruned: the next cooldown for pkg starts back at #1.
        manager.startCooldown(pkg, escalatingConfig, now = midnight)
        assertEquals(1, manager.getCooldownState(pkg, now = midnight)!!.cooldownNumber)

        // Active cooldown survives the rollover but its counter is reset.
        val other = manager.getCooldownState("com.other.app", now = midnight)
        assertNotNull(other)
        assertEquals(0, other!!.cooldownNumber)
    }

    @Test
    fun `clearCooldown ends the block but keeps the escalation counter`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)
        manager.clearCooldown(pkg)

        assertFalse(manager.isInCooldown(pkg, now = t0 + 1))

        // Paying to unseal doesn't wipe the day's offence count.
        manager.startCooldown(pkg, escalatingConfig, now = t0 + 5 * 60_000L)
        assertEquals(2, manager.getCooldownState(pkg, now = t0 + 5 * 60_000L)!!.cooldownNumber)
    }

    @Test
    fun `session start times are dropped when a cooldown starts`() {
        manager.onAppForegrounded(pkg, now = t0)
        assertEquals(10, manager.getInSessionMinutes(pkg, now = t0 + 10 * 60_000L))

        manager.startCooldown(pkg, escalatingConfig, now = t0 + 10 * 60_000L)
        assertEquals(0, manager.getInSessionMinutes(pkg, now = t0 + 10 * 60_000L))
    }

    @Test
    fun `onAppLeft clears the in-session start time`() {
        manager.onAppForegrounded(pkg, now = t0)
        manager.onAppLeft(pkg)
        assertEquals(0, manager.getInSessionMinutes(pkg, now = t0 + 60 * 60_000L))
    }

    @Test
    fun `recordAttempt increments only while the cooldown is active`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)

        assertEquals(1, manager.recordAttempt(pkg, now = t0 + 60_000L))
        assertEquals(2, manager.recordAttempt(pkg, now = t0 + 120_000L))
        // Expired cooldown: attempts no longer count against it.
        assertEquals(1, manager.recordAttempt(pkg, now = t0 + 40 * 60_000L))
    }

    @Test
    fun `startPanicSeal blocks for the base duration without counting an offence`() {
        manager.startPanicSeal(pkg, escalatingConfig, now = t0)

        val state = manager.getCooldownState(pkg, now = t0)
        assertNotNull(state)
        // Base duration only — never the escalated length.
        assertEquals(t0 + 30 * 60_000L, state!!.cooldownExpiryMillis)
        // No offence counted: the day's escalation counter is untouched.
        assertEquals(0, state.cooldownNumber)
    }

    @Test
    fun `startPanicSeal preserves the day's existing escalation counter`() {
        manager.startCooldown(pkg, escalatingConfig, now = t0)          // offence #1
        manager.startPanicSeal(pkg, escalatingConfig, now = t0 + 60 * 60_000L)

        val state = manager.getCooldownState(pkg, now = t0 + 60 * 60_000L)
        assertEquals(1, state!!.cooldownNumber)
        // Panic seal runs for the base 30m even though escalation is enabled.
        assertEquals(t0 + 60 * 60_000L + 30 * 60_000L, state.cooldownExpiryMillis)

        // The next real offence still escalates as offence #2 (30 + 15).
        manager.startCooldown(pkg, escalatingConfig, now = t0 + 120 * 60_000L)
        val next = manager.getCooldownState(pkg, now = t0 + 120 * 60_000L)
        assertEquals(2, next!!.cooldownNumber)
        assertEquals(t0 + 120 * 60_000L + 45 * 60_000L, next.cooldownExpiryMillis)
    }

    @Test
    fun `startPanicSeal drops the in-session start time`() {
        manager.onAppForegrounded(pkg, now = t0)
        manager.startPanicSeal(pkg, config, now = t0 + 5 * 60_000L)
        assertEquals(0, manager.getInSessionMinutes(pkg, now = t0 + 6 * 60_000L))
    }
}
