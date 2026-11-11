package com.infinicada.focuspocus

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgressionTest {

    private val gson = Gson()
    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    private fun balance() = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)

    private fun setBalance(value: Long) {
        prefs.putLong(Constants.PrefsKeys.MANA_BALANCE, value)
    }

    private fun ledger(): List<ManaLedgerEntry> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.MANA_LEDGER,
            object : TypeToken<List<ManaLedgerEntry>>() {}.type) ?: emptyList()

    private fun trials(): List<Trial> = Progression.loadTrials(prefs, gson)

    private fun saveTrials(list: List<Trial>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.TRIALS, list)
    }

    // ── claimTrial ──

    @Test
    fun `claiming a completed trial credits its reward once`() {
        val today = com.infinicada.focuspocus.limit.DayKey.today()
        saveTrials(listOf(
            Trial("t1", TrialType.COMPLETE_SESSIONS, target = 2, progress = 2,
                period = TrialPeriod.DAILY, periodKey = today, rewardMana = 40L)
        ))

        assertEquals(40L, Progression.claimTrial(prefs, gson, "t1"))
        assertEquals(40L, balance())
        assertTrue(trials().first().claimed)
        assertEquals(LedgerKind.TRIAL, ledger().last().kind)

        // Second claim is a no-op
        assertEquals(0L, Progression.claimTrial(prefs, gson, "t1"))
        assertEquals(40L, balance())
    }

    @Test
    fun `incomplete or unknown trials cannot be claimed`() {
        val today = com.infinicada.focuspocus.limit.DayKey.today()
        saveTrials(listOf(
            Trial("t1", TrialType.COMPLETE_SESSIONS, target = 3, progress = 1,
                period = TrialPeriod.DAILY, periodKey = today, rewardMana = 40L)
        ))
        assertEquals(0L, Progression.claimTrial(prefs, gson, "t1"))
        assertEquals(0L, Progression.claimTrial(prefs, gson, "missing"))
        assertEquals(0L, balance())
    }

    @Test
    fun `claims are gated by the master toggle`() {
        prefs.putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, false)
        val today = com.infinicada.focuspocus.limit.DayKey.today()
        saveTrials(listOf(
            Trial("t1", TrialType.COMPLETE_SESSIONS, target = 1, progress = 1,
                period = TrialPeriod.DAILY, periodKey = today, rewardMana = 40L)
        ))
        assertEquals(0L, Progression.claimTrial(prefs, gson, "t1"))
        assertEquals(0L, balance())
    }

    // ── rollover ──

    @Test
    fun `rollover draws an initial slate`() {
        Progression.rolloverIfNeeded(prefs, gson)
        val slate = trials()
        assertEquals(2, slate.count { it.period == TrialPeriod.DAILY })
        assertEquals(1, slate.count { it.period == TrialPeriod.WEEKLY })
    }

    @Test
    fun `rollover is idempotent within a day`() {
        Progression.rolloverIfNeeded(prefs, gson)
        val first = trials()
        Progression.rolloverIfNeeded(prefs, gson)
        assertEquals(first, trials())
    }

    @Test
    fun `rollover auto-credits completed unclaimed trials from ended periods`() {
        saveTrials(listOf(
            Trial("old", TrialType.COMPLETE_SESSIONS, target = 2, progress = 2,
                period = TrialPeriod.DAILY, periodKey = "20200101", rewardMana = 40L)
        ))
        Progression.rolloverIfNeeded(prefs, gson)
        assertEquals(40L, balance())
        assertEquals("old", ledger().last().refId)
        // The stale trial is gone, replaced by a fresh slate
        assertTrue(trials().none { it.id == "old" })
    }

    @Test
    fun `a leftover stay-under-limits trial is never credited now that limits are gone`() {
        prefs.putBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, true)
        saveTrials(listOf(
            Trial("ul", TrialType.STAY_UNDER_LIMITS, target = 1, progress = 0,
                period = TrialPeriod.DAILY, periodKey = "20200101", rewardMana = 60L)
        ))
        Progression.rolloverIfNeeded(prefs, gson)
        assertEquals(0L, balance())
        assertTrue(trials().none { it.type == TrialType.STAY_UNDER_LIMITS })
    }

    @Test
    fun `rollover rotates but never credits while disabled`() {
        prefs.putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, false)
        saveTrials(listOf(
            Trial("old", TrialType.COMPLETE_SESSIONS, target = 2, progress = 2,
                period = TrialPeriod.DAILY, periodKey = "20200101", rewardMana = 40L)
        ))
        Progression.rolloverIfNeeded(prefs, gson)
        assertEquals(0L, balance())
        assertTrue(trials().none { it.id == "old" })
        assertTrue(trials().isNotEmpty())
    }

    // ── perks ──

    @Test
    fun `extra break perk needs an active manual session`() {
        setBalance(500L)
        assertFalse(Progression.redeemPerk(prefs, gson, Perk.EXTRA_BREAK))
        assertEquals(500L, balance())

        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.EXTRA_BREAK))
        assertEquals(450L, balance())
        assertEquals(1, prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0))
    }

    // ── Mana Well channeling ──

    private val wellOn = com.infinicada.focuspocus.limit.WellSettings(
        enabled = true, capacityMinutes = 30, refillIntervalMinutes = 20
    )

    /** Turns the Well on and drains [drawn] minutes so there is room to pour into. */
    private fun wellWithRoom(drawn: Int = 30) {
        com.infinicada.focuspocus.limit.ManaWell.requestSettings(prefs, wellOn)
        com.infinicada.focuspocus.limit.ManaWell.tryDraw(prefs, drawn)
    }

    private fun wellMinutes(): Int {
        val now = System.currentTimeMillis()
        return com.infinicada.focuspocus.limit.ManaWell.snapshot(prefs, now).availableMinutesAt(now)
    }

    @Test
    fun `channeling pours minutes into the Well for mana`() {
        setBalance(500L)
        wellWithRoom()

        assertTrue(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(425L, balance())
        assertEquals(Perk.WELL_CHANNEL_GRANT, wellMinutes())
        val entry = ledger().last()
        assertEquals(LedgerKind.PERK, entry.kind)
        assertEquals(Perk.WELL_CHANNEL.name, entry.refId)
        assertEquals(-75L, entry.amount)
    }

    @Test
    fun `each channel in a day costs double the one before`() {
        setBalance(1000L)
        wellWithRoom()

        assertEquals(75L, Progression.wellChannelCost(prefs, gson))
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(150L, Progression.wellChannelCost(prefs, gson))
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(300L, Progression.wellChannelCost(prefs, gson))
        assertEquals(1000L - 75L - 150L, balance())
        // Yesterday's channels don't count against today's price.
        assertEquals(75L, Progression.wellChannelCost(prefs, gson, todayKey = "19990101"))
    }

    @Test
    fun `channeling is refused without the mana and spends nothing`() {
        setBalance(74L)
        wellWithRoom()

        assertFalse(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(74L, balance())
        assertEquals(0, wellMinutes())
    }

    @Test
    fun `channeling is refused when the Well has no room and spends nothing`() {
        setBalance(500L)
        wellWithRoom(drawn = 4)

        assertFalse(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(500L, balance())
        assertEquals(26, wellMinutes())
    }

    @Test
    fun `channeling needs the Well to be on`() {
        setBalance(500L)
        assertFalse(Progression.redeemPerk(prefs, gson, Perk.WELL_CHANNEL))
        assertEquals(500L, balance())
    }

    // ── ledger ──

    @Test
    fun `ledger prunes to the cap`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        setBalance(1_000_000L)
        val big = (1..Constants.MAX_MANA_LEDGER + 5).map {
            ManaLedgerEntry(it.toLong(), 1L, LedgerKind.SESSION, dateKey = "20200101")
        }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.MANA_LEDGER, big)
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.EXTRA_BREAK))
        assertEquals(Constants.MAX_MANA_LEDGER, ledger().size)
        assertEquals(LedgerKind.PERK, ledger().last().kind)
    }

    // ── stored-data corruption guards ──
    // Records written by a build with broken R8 keep rules (v1.4) carry
    // obfuscated enum constant names; Gson maps those to null even in fields
    // whose Kotlin types are non-null. Loaders must drop such records instead
    // of letting the null NPE later (e.g. TrialEngine.isEnded on trial.period).

    @Test
    fun `trials with unmappable enum names are dropped on load`() {
        prefs.putString(
            Constants.PrefsKeys.TRIALS,
            """[
              {"id":"bad","type":"a","target":2,"progress":0,"period":"b","periodKey":"20260713","rewardMana":40,"claimed":false,"param":0},
              {"id":"good","type":"COMPLETE_SESSIONS","target":2,"progress":0,"period":"DAILY","periodKey":"20260713","rewardMana":40,"claimed":false,"param":0}
            ]"""
        )
        assertEquals(listOf("good"), trials().map { it.id })
    }

    @Test
    fun `ledger entries with unmappable kind are dropped on load`() {
        prefs.putString(
            Constants.PrefsKeys.MANA_LEDGER,
            """[
              {"timestampMillis":1,"amount":40,"kind":"z"},
              {"timestampMillis":2,"amount":50,"kind":"SESSION","minutes":25,"title":"","refId":"","packageName":"","dateKey":"20260713"}
            ]"""
        )
        assertEquals(listOf(50L), Progression.loadLedger(prefs, gson).map { it.amount })
    }

    @Test
    fun `lifetime earned tracks credits not debits`() {
        val today = com.infinicada.focuspocus.limit.DayKey.today()
        saveTrials(listOf(
            Trial("t1", TrialType.COMPLETE_SESSIONS, target = 1, progress = 1,
                period = TrialPeriod.DAILY, periodKey = today, rewardMana = 40L)
        ))
        setBalance(60L)
        Progression.claimTrial(prefs, gson, "t1")
        assertEquals(40L, prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L))
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.EXTRA_BREAK))
        assertEquals(40L, prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L))
        assertEquals(50L, balance())
    }
}
