package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.CooldownState
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

    @Test
    fun `startPanicSeal with a copied config uses only the overridden duration`() {
        // This is how Group Seal mode synthesizes a shared duration: a
        // config.copy(cooldownMinutes = N) passed to startPanicSeal must seal
        // for exactly N minutes regardless of the original config's own
        // cooldownMinutes or escalation settings.
        val sharedDurationConfig = escalatingConfig.copy(cooldownMinutes = 12)
        manager.startPanicSeal(pkg, sharedDurationConfig, now = t0)

        val state = manager.getCooldownState(pkg, now = t0)
        assertNotNull(state)
        assertEquals(t0 + 12 * 60_000L, state!!.cooldownExpiryMillis)
        assertEquals(0, state.cooldownNumber)
    }

    @Test
    fun `startSeals applies every request in one store write`() {
        val other = "com.other.app"
        manager.startSeals(
            listOf(
                SessionCooldownManager.SealRequest(pkg, config, t0, countsAsOffence = false),
                SessionCooldownManager.SealRequest(other, config, t0, countsAsOffence = false)
            )
        )

        // Both land: a per-app write would have been fine too, but the point is
        // that Group Seal's whole sweep is a single serialize + commit.
        assertEquals(setOf(pkg, other), manager.peekActiveCooldowns(now = t0).keys)
        assertEquals(t0 + 30 * 60_000L, manager.getCooldownState(pkg, now = t0)!!.cooldownExpiryMillis)
        assertEquals(t0 + 30 * 60_000L, manager.getCooldownState(other, now = t0)!!.cooldownExpiryMillis)
    }

    @Test
    fun `startSeals anchors each seal at its own lapse moment`() {
        // A lapsed pact seals from when it lapsed, not from when the sweep
        // discovered it — two apps that lapsed an hour apart keep that gap.
        val other = "com.other.app"
        val lapsedEarly = t0
        val lapsedLate = t0 + 60 * 60_000L
        manager.startSeals(
            listOf(
                SessionCooldownManager.SealRequest(pkg, config, lapsedEarly, countsAsOffence = false),
                SessionCooldownManager.SealRequest(other, config, lapsedLate, countsAsOffence = false)
            )
        )

        assertEquals(lapsedEarly + 30 * 60_000L, manager.getCooldownState(pkg, now = t0)!!.cooldownExpiryMillis)
        assertEquals(lapsedLate + 30 * 60_000L, manager.getCooldownState(other, now = t0)!!.cooldownExpiryMillis)
    }

    @Test
    fun `startSeals escalates only the requests that count as an offence`() {
        val other = "com.other.app"
        manager.startCooldown(pkg, escalatingConfig, now = t0)          // offence #1 for pkg
        manager.startCooldown(other, escalatingConfig, now = t0)        // offence #1 for other

        val now = t0 + 120 * 60_000L
        manager.startSeals(
            listOf(
                SessionCooldownManager.SealRequest(pkg, escalatingConfig, now, countsAsOffence = true),
                SessionCooldownManager.SealRequest(other, escalatingConfig, now, countsAsOffence = false)
            )
        )

        // pkg escalates as offence #2 (30 + 15); other keeps its counter and base length.
        val escalated = manager.getCooldownState(pkg, now)!!
        assertEquals(2, escalated.cooldownNumber)
        assertEquals(now + 45 * 60_000L, escalated.cooldownExpiryMillis)

        val chosen = manager.getCooldownState(other, now)!!
        assertEquals(1, chosen.cooldownNumber)
        assertEquals(now + 30 * 60_000L, chosen.cooldownExpiryMillis)
    }

    @Test
    fun `startSeals with no requests leaves the store untouched`() {
        manager.startSeals(emptyList())
        assertNull(storedJson())
    }

    @Test
    fun `startSeals drops every sealed package's in-session start time`() {
        val other = "com.other.app"
        manager.onAppForegrounded(pkg, now = t0)
        manager.onAppForegrounded(other, now = t0)

        manager.startSeals(
            listOf(
                SessionCooldownManager.SealRequest(pkg, config, t0 + 5 * 60_000L, countsAsOffence = false),
                SessionCooldownManager.SealRequest(other, config, t0 + 5 * 60_000L, countsAsOffence = true)
            )
        )

        assertEquals(0, manager.getInSessionMinutes(pkg, now = t0 + 6 * 60_000L))
        assertEquals(0, manager.getInSessionMinutes(other, now = t0 + 6 * 60_000L))
    }

    @Test
    fun `minutesRemaining rounds partial minutes up and exact minutes exactly`() {
        val now = 1_000_000L
        fun state(msLeft: Long) = CooldownState("pkg", cooldownExpiryMillis = now + msLeft)
        assertEquals(1, SessionCooldownManager.minutesRemaining(state(60_000L), now))
        assertEquals(2, SessionCooldownManager.minutesRemaining(state(60_001L), now))
        assertEquals(1, SessionCooldownManager.minutesRemaining(state(1L), now))
        assertEquals(0, SessionCooldownManager.minutesRemaining(state(0L), now))
    }
}
