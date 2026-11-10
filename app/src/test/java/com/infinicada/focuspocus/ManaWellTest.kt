package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellMath
import com.infinicada.focuspocus.limit.WellSettings
import com.infinicada.focuspocus.model.AppTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ManaWellTest {

    private lateinit var prefs: FakeSharedPreferences

    private val t0 = 1_000_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val today = "20261109"
    private val tomorrow = "20261110"

    /** The owner's chosen terms: 30 minutes, one minute back per 20. */
    private val on = WellSettings(enabled = true, capacityMinutes = 30, refillIntervalMinutes = 20)

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    private fun turnOn(settings: WellSettings = on, now: Long = t0) {
        ManaWell.requestSettings(prefs, settings, now)
    }

    private fun minutesAt(now: Long): Int = ManaWell.snapshot(prefs, now, today).availableMinutesAt(now)

    // ── WellMath.levelAt ──

    @Test
    fun `level regains one minute per refill interval`() {
        assertEquals(minute, WellMath.levelAt(0L, t0, t0 + 20 * minute, 30, 20))
        assertEquals(3 * minute, WellMath.levelAt(0L, t0, t0 + hour, 30, 20))
    }

    @Test
    fun `level never exceeds the capacity`() {
        assertEquals(30 * minute, WellMath.levelAt(29 * minute, t0, t0 + day, 30, 20))
        // Months untouched must saturate, not overflow.
        assertEquals(30 * minute, WellMath.levelAt(0L, 0L, Long.MAX_VALUE, 30, 10))
    }

    @Test
    fun `a clock that moved backwards refills nothing`() {
        assertEquals(5 * minute, WellMath.levelAt(5 * minute, t0, t0 - hour, 30, 20))
    }

    @Test
    fun `no passive refill when the interval is zero`() {
        assertEquals(5 * minute, WellMath.levelAt(5 * minute, t0, t0 + day, 30, 0))
    }

    @Test
    fun `a stored level above a shrunken capacity is clamped`() {
        assertEquals(10 * minute, WellMath.levelAt(25 * minute, t0, t0, 10, 20))
    }

    // ── WellMath.millisUntilLevel / limitChoices ──

    @Test
    fun `time until a level counts the missing minutes at the refill rate`() {
        assertEquals(0L, WellMath.millisUntilLevel(5 * minute, 5, 30, 20))
        assertEquals(40 * minute, WellMath.millisUntilLevel(0L, 2, 30, 20))
        assertEquals(30 * minute, WellMath.millisUntilLevel(30_000L, 2, 30, 20))
    }

    @Test
    fun `a level is unreachable without refill or beyond the capacity`() {
        assertNull(WellMath.millisUntilLevel(0L, 2, 30, 0))
        assertNull(WellMath.millisUntilLevel(0L, 45, 30, 20))
    }

    @Test
    fun `choices stop at what the Well holds`() {
        val ladder = listOf(2, 5, 10, 15)
        assertEquals(ladder, WellMath.limitChoices(ladder, 30))
        assertEquals(listOf(2, 5), WellMath.limitChoices(ladder, 7))
        assertEquals(emptyList<Int>(), WellMath.limitChoices(ladder, 1))
    }

    // ── WellMath.split ──

    @Test
    fun `any terms apply at once while the Well is off`() {
        val wanted = WellSettings(enabled = true, capacityMinutes = 60, refillIntervalMinutes = 10)
        val split = WellMath.split(WellSettings(enabled = false), wanted)
        assertEquals(wanted, split.immediate)
        assertNull(split.pending)
    }

    @Test
    fun `tightening applies at once`() {
        val wanted = on.copy(capacityMinutes = 15, refillIntervalMinutes = 40)
        val split = WellMath.split(on, wanted)
        assertEquals(wanted, split.immediate)
        assertNull(split.pending)
    }

    @Test
    fun `loosening waits and the enforced terms hold`() {
        val wanted = on.copy(capacityMinutes = 45, refillIntervalMinutes = 10)
        val split = WellMath.split(on, wanted)
        assertEquals(on, split.immediate)
        assertEquals(wanted, split.pending)
    }

    @Test
    fun `a mixed change tightens now and loosens later`() {
        val wanted = on.copy(capacityMinutes = 15, refillIntervalMinutes = 10)
        val split = WellMath.split(on, wanted)
        assertEquals(on.copy(capacityMinutes = 15), split.immediate)
        assertEquals(wanted, split.pending)
    }

    @Test
    fun `turning the Well off waits`() {
        val split = WellMath.split(on, on.copy(enabled = false))
        assertEquals(on, split.immediate)
        assertEquals(on.copy(enabled = false), split.pending)
    }

    @Test
    fun `dropping passive refill tightens and restoring it loosens`() {
        val earnedOnly = on.copy(refillIntervalMinutes = 0)
        assertEquals(earnedOnly, WellMath.split(on, earnedOnly).immediate)
        assertNull(WellMath.split(on, earnedOnly).pending)

        val back = WellMath.split(earnedOnly, on)
        assertEquals(earnedOnly, back.immediate)
        assertEquals(on, back.pending)
    }

    @Test
    fun `requested terms are clamped into range`() {
        val split = WellMath.split(
            WellSettings(enabled = false),
            WellSettings(enabled = true, capacityMinutes = 500, refillIntervalMinutes = 1)
        )
        assertEquals(WellMath.MAX_CAPACITY_MINUTES, split.immediate.capacityMinutes)
        assertEquals(WellMath.MIN_REFILL_INTERVAL_MINUTES, split.immediate.refillIntervalMinutes)
    }

    // ── The pool ──

    @Test
    fun `the Well is off by default and binds nothing`() {
        val pact = AppTimeLimit(packageName = "a", dailyLimitMinutes = 0, pactModeEnabled = true)
        assertFalse(ManaWell.isEnabled(prefs, t0))
        assertFalse(ManaWell.isBound(prefs, pact, t0))
    }

    @Test
    fun `a Well that is on binds pacts but not exempt pacts or wards`() {
        turnOn()
        val pact = AppTimeLimit(packageName = "a", dailyLimitMinutes = 0, pactModeEnabled = true)
        assertTrue(ManaWell.isBound(prefs, pact, t0))
        assertFalse(ManaWell.isBound(prefs, pact.copy(wellExempt = true), t0))
        assertFalse(ManaWell.isBound(prefs, pact.copy(pactModeEnabled = false), t0))
    }

    @Test
    fun `a fresh Well starts full`() {
        turnOn()
        assertEquals(30, minutesAt(t0))
    }

    @Test
    fun `a Well first turned on with a larger capacity starts full at that capacity`() {
        turnOn(on.copy(capacityMinutes = 60))
        assertEquals(60, minutesAt(t0))
    }

    @Test
    fun `drawing takes minutes out and they come back slowly`() {
        turnOn()
        assertTrue(ManaWell.tryDraw(prefs, 15, t0, today))
        assertEquals(15, minutesAt(t0))
        assertEquals(18, minutesAt(t0 + hour))
        // The 15 drawn minutes take five hours to return.
        assertEquals(29, minutesAt(t0 + 5 * hour - 1))
        assertEquals(30, minutesAt(t0 + 5 * hour))
    }

    @Test
    fun `a draw the Well cannot cover is refused and changes nothing`() {
        turnOn()
        assertTrue(ManaWell.tryDraw(prefs, 30, t0, today))
        assertFalse(ManaWell.tryDraw(prefs, 2, t0 + minute, today))
        assertEquals(0, minutesAt(t0 + minute))
        assertEquals(30, ManaWell.snapshot(prefs, t0 + minute, today).drawnTodayMinutes)
    }

    @Test
    fun `hopping between apps spends the same pool`() {
        turnOn()
        assertTrue(ManaWell.tryDraw(prefs, 15, t0, today))
        assertTrue(ManaWell.tryDraw(prefs, 15, t0 + 15 * minute, today))
        // A third app right after finds almost nothing left.
        assertFalse(ManaWell.tryDraw(prefs, 15, t0 + 30 * minute, today))
        assertEquals(1, minutesAt(t0 + 30 * minute))
    }

    @Test
    fun `non-positive draws are refused`() {
        turnOn()
        assertFalse(ManaWell.tryDraw(prefs, 0, t0, today))
        assertFalse(ManaWell.tryDraw(prefs, -5, t0, today))
        assertEquals(30, minutesAt(t0))
    }

    @Test
    fun `drawn today sums the day and resets on the next`() {
        turnOn()
        ManaWell.tryDraw(prefs, 10, t0, today)
        ManaWell.tryDraw(prefs, 5, t0 + hour, today)
        assertEquals(15, ManaWell.snapshot(prefs, t0 + hour, today).drawnTodayMinutes)
        assertEquals(0, ManaWell.snapshot(prefs, t0 + day, tomorrow).drawnTodayMinutes)

        ManaWell.tryDraw(prefs, 2, t0 + day, tomorrow)
        assertEquals(2, ManaWell.snapshot(prefs, t0 + day, tomorrow).drawnTodayMinutes)
    }

    @Test
    fun `a snapshot keeps refilling on the reader's clock`() {
        turnOn()
        ManaWell.tryDraw(prefs, 30, t0, today)
        val snapshot = ManaWell.snapshot(prefs, t0, today)
        assertEquals(0, snapshot.availableMinutesAt(t0))
        assertEquals(2, snapshot.availableMinutesAt(t0 + 40 * minute))
        assertEquals(40 * minute, snapshot.millisUntilMinutes(2, t0))
        assertEquals(10 * minute, snapshot.millisUntilMinutes(2, t0 + 30 * minute))
    }

    // ── Sealing early ──

    @Test
    fun `an early seal refunds the time left on the pact`() {
        val expiry = t0 + 15 * minute
        assertEquals(
            10 * minute,
            WellMath.refundableMillis(listOf(expiry), emptyList(), t0 + 5 * minute)
        )
    }

    @Test
    fun `a lapsed pact refunds nothing`() {
        assertEquals(0L, WellMath.refundableMillis(listOf(t0), emptyList(), t0))
        assertEquals(0L, WellMath.refundableMillis(listOf(t0 - minute), emptyList(), t0))
        assertEquals(0L, WellMath.refundableMillis(emptyList(), emptyList(), t0))
    }

    @Test
    fun `a shared window is one draw and is refunded once`() {
        // Group Seal: three apps opened by a single 10-minute draw.
        val window = t0 + 10 * minute
        assertEquals(
            6 * minute,
            WellMath.refundableMillis(listOf(window, window, window), emptyList(), t0 + 4 * minute)
        )
    }

    @Test
    fun `separate draws are each refunded`() {
        val first = t0 + 10 * minute
        val second = t0 + 12 * minute
        assertEquals(
            (10 + 12) * minute,
            WellMath.refundableMillis(listOf(first, second), emptyList(), t0)
        )
    }

    @Test
    fun `sealing one app out of a shared window refunds nothing while the rest stay open`() {
        val window = t0 + 10 * minute
        assertEquals(0L, WellMath.refundableMillis(listOf(window), listOf(window), t0))
        // An unrelated pact still running elsewhere doesn't block the refund.
        assertEquals(
            10 * minute,
            WellMath.refundableMillis(listOf(window), listOf(t0 + 3 * minute), t0)
        )
    }

    @Test
    fun `a refund returns minutes to the Well and to the day's total`() {
        turnOn()
        ManaWell.tryDraw(prefs, 15, t0, today)
        ManaWell.refund(prefs, 10 * minute, t0 + 5 * minute, today)

        // 15 left + 5 minutes of refill (a quarter minute) + 10 back.
        assertEquals(25, minutesAt(t0 + 5 * minute))
        assertEquals(5, ManaWell.snapshot(prefs, t0 + 5 * minute, today).drawnTodayMinutes)
    }

    @Test
    fun `a refund never overfills the Well or drives the day's total negative`() {
        turnOn()
        ManaWell.tryDraw(prefs, 5, t0, today)
        ManaWell.refund(prefs, 20 * minute, t0, today)
        assertEquals(30, minutesAt(t0))
        assertEquals(0, ManaWell.snapshot(prefs, t0, today).drawnTodayMinutes)

        ManaWell.refund(prefs, 0L, t0, today)
        ManaWell.refund(prefs, -minute, t0, today)
        assertEquals(30, minutesAt(t0))
    }

    // ── Channeling ──

    @Test
    fun `channeling adds minutes when there is room`() {
        turnOn()
        ManaWell.tryDraw(prefs, 20, t0, today)
        assertTrue(ManaWell.channel(prefs, 5, t0))
        assertEquals(15, minutesAt(t0))
    }

    @Test
    fun `channeling is refused when the whole amount would not fit`() {
        turnOn()
        ManaWell.tryDraw(prefs, 4, t0, today)
        assertFalse(ManaWell.channel(prefs, 5, t0))
        assertEquals(26, minutesAt(t0))
        assertFalse(ManaWell.channel(prefs, 0, t0))
    }

    @Test
    fun `an earned-only Well fills only by channeling`() {
        turnOn(on.copy(refillIntervalMinutes = 0))
        ManaWell.tryDraw(prefs, 30, t0, today)
        assertEquals(0, minutesAt(t0 + day))
        assertTrue(ManaWell.channel(prefs, 5, t0 + day))
        assertEquals(5, minutesAt(t0 + day))
    }

    // ── Changing the terms ──

    @Test
    fun `turning the Well on is immediate`() {
        val state = ManaWell.requestSettings(prefs, on, t0)
        assertEquals(on, state.enforced)
        assertNull(state.pending)
        assertTrue(ManaWell.isEnabled(prefs, t0))
    }

    @Test
    fun `turning the Well off takes a day`() {
        turnOn()
        val state = ManaWell.requestSettings(prefs, on.copy(enabled = false), t0)
        assertTrue(state.enforced.enabled)
        assertEquals(t0 + WellMath.REVISION_DELAY_MS, state.pending?.appliesAtMillis)

        assertTrue(ManaWell.isEnabled(prefs, t0 + day - 1))
        assertFalse(ManaWell.isEnabled(prefs, t0 + day))
        assertNull(ManaWell.settingsState(prefs, t0 + day).pending)
    }

    @Test
    fun `a larger capacity waits a day and a smaller one clamps the level now`() {
        turnOn()
        ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 60), t0)
        assertEquals(30, ManaWell.settings(prefs, t0 + hour).capacityMinutes)
        assertEquals(60, ManaWell.settings(prefs, t0 + day).capacityMinutes)

        ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 10), t0 + 2 * day)
        assertEquals(10, minutesAt(t0 + 2 * day))
    }

    @Test
    fun `a queued change is settled at its due instant, not at discovery`() {
        turnOn()
        ManaWell.tryDraw(prefs, 30, t0, today)
        // Faster refill queued: 1 per 10 instead of 1 per 20.
        ManaWell.requestSettings(prefs, on.copy(refillIntervalMinutes = 10), t0)

        // Discovered two hours late: 24 h at the old rate would have filled
        // the Well anyway, so check the rate switch on a drained Well instead.
        ManaWell.tryDraw(prefs, 30, t0 + day - hour, today)
        // One hour at 3/hour, then two hours at 6/hour.
        assertEquals(15, minutesAt(t0 + day + 2 * hour))
    }

    @Test
    fun `requesting the enforced terms again withdraws a queued change`() {
        turnOn()
        ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 60), t0)
        assertNotNull(ManaWell.settingsState(prefs, t0).pending)

        ManaWell.requestSettings(prefs, on, t0 + hour)
        assertNull(ManaWell.settingsState(prefs, t0 + hour).pending)
        assertEquals(30, ManaWell.settings(prefs, t0 + 2 * day).capacityMinutes)
    }

    @Test
    fun `cancel keeps the enforced terms`() {
        turnOn()
        ManaWell.requestSettings(prefs, on.copy(enabled = false), t0)
        ManaWell.cancelPendingRevision(prefs)
        assertTrue(ManaWell.isEnabled(prefs, t0 + 2 * day))
    }

    @Test
    fun `asking for less than what is queued keeps the earlier date`() {
        turnOn()
        ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 60), t0)
        val eased = ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 45), t0 + hour)
        assertEquals(45, eased.pending?.settings?.capacityMinutes)
        assertEquals(t0 + day, eased.pending?.appliesAtMillis)
    }

    @Test
    fun `asking for more than what is queued restarts the clock`() {
        turnOn()
        ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 45), t0)
        val more = ManaWell.requestSettings(prefs, on.copy(capacityMinutes = 60), t0 + hour)
        assertEquals(t0 + hour + day, more.pending?.appliesAtMillis)
    }

    @Test
    fun `changing terms while the Well is off leaves the pool alone`() {
        turnOn()
        ManaWell.tryDraw(prefs, 30, t0, today)
        ManaWell.requestSettings(prefs, on.copy(enabled = false), t0)
        // Off from t0 + day. Shrinking and regrowing the capacity while off
        // must not act on the pool.
        ManaWell.requestSettings(prefs, WellSettings(false, 10, 20), t0 + day + minute)
        ManaWell.requestSettings(prefs, WellSettings(false, 30, 20), t0 + day + 2 * minute)
        ManaWell.requestSettings(prefs, on, t0 + day + 3 * minute)
        assertEquals(30, minutesAt(t0 + day + 3 * minute))
    }

    // ── Backup restore ──

    @Test
    fun `a restore has nothing to hold while the Well is off`() {
        assertNull(ManaWell.holdForRestore(prefs, on, t0))
    }

    @Test
    fun `a restore cannot loosen an enforced Well`() {
        turnOn()
        val held = ManaWell.holdForRestore(prefs, WellSettings(false, 60, 10), t0 + hour)
        assertEquals(on, held?.enforced)
        assertEquals(WellSettings(false, 60, 10), held?.pending?.settings)
        assertEquals(t0 + hour + day, held?.pending?.appliesAtMillis)
    }

    @Test
    fun `a stricter restore applies in full`() {
        turnOn()
        val stricter = on.copy(capacityMinutes = 15)
        val held = ManaWell.holdForRestore(prefs, stricter, t0)
        assertEquals(stricter, held?.enforced)
        assertNull(held?.pending)
    }
}
