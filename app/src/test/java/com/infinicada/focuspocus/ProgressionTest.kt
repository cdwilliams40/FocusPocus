package com.infinicada.focuspocus

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.Boon
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.SigilCatalog
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
        val today = com.infinicada.focuspocus.limit.SessionCooldownManager.todayString()
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
        val today = com.infinicada.focuspocus.limit.SessionCooldownManager.todayString()
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
        val today = com.infinicada.focuspocus.limit.SessionCooldownManager.todayString()
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
    fun `rollover judges an ended stay-under-limits day from block events`() {
        prefs.putBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, true)
        // A configured limit so the trial is judgeable
        prefs.putString(Constants.PrefsKeys.APP_TIME_LIMITS, gson.toJson(mapOf("com.example.feed" to 30)))
        saveTrials(listOf(
            Trial("ul", TrialType.STAY_UNDER_LIMITS, target = 1, progress = 0,
                period = TrialPeriod.DAILY, periodKey = "20200101", rewardMana = 60L)
        ))
        // No block events on 2020-01-01 -> success, auto-credited
        Progression.rolloverIfNeeded(prefs, gson)
        assertEquals(60L, balance())
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

    // ── boons ──

    @Test
    fun `redeeming a boon debits and unlocks the first-boon sigil`() {
        setBalance(500L)
        val boon = Boon("b1", "Game night", 300L)

        assertTrue(Progression.redeemBoon(prefs, gson, boon))
        assertEquals(200L, balance())
        val entry = ledger().last()
        assertEquals(LedgerKind.BOON, entry.kind)
        assertEquals(-300L, entry.amount)
        assertEquals("Game night", entry.title)
        assertTrue(Progression.loadUnlockedSigilIds(prefs, gson).contains(SigilCatalog.FIRST_BOON))
    }

    @Test
    fun `a boon costing more than the balance is refused`() {
        setBalance(100L)
        assertFalse(Progression.redeemBoon(prefs, gson, Boon("b1", "Too dear", 300L)))
        assertEquals(100L, balance())
        assertTrue(ledger().isEmpty())
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

    @Test
    fun `sealed minutes perk grants an allowance and clears the cooldown`() {
        setBalance(500L)
        // An active cooldown for the sealed app
        val cooldown = com.infinicada.focuspocus.limit.SessionCooldownManager(prefs, gson)
        val config = com.infinicada.focuspocus.model.AppTimeLimit(
            packageName = "com.example.feed", dailyLimitMinutes = 0,
            sessionLimitMinutes = 10, cooldownMinutes = 60
        )
        cooldown.startCooldown("com.example.feed", config)
        assertTrue(cooldown.isInCooldown("com.example.feed"))

        assertTrue(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, "com.example.feed"))
        assertEquals(350L, balance())
        assertFalse(cooldown.isInCooldown("com.example.feed"))
        val pacts = com.infinicada.focuspocus.limit.PactManager(prefs, gson)
        assertTrue(pacts.getAllowanceExpiry("com.example.feed") != null)
    }

    @Test
    fun `sealed minutes perk is once per app per day`() {
        setBalance(500L)
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, "com.example.feed"))
        assertFalse(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, "com.example.feed"))
        assertEquals(350L, balance())
        // A different app is still allowed
        assertTrue(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, "com.example.other"))
    }

    @Test
    fun `sealed minutes perk requires a package`() {
        setBalance(500L)
        assertFalse(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, null))
        assertFalse(Progression.redeemPerk(prefs, gson, Perk.SEALED_MINUTES, ""))
    }

    // ── sigils ──

    @Test
    fun `sigil unlocks are idempotent`() {
        val first = Progression.unlockSigils(prefs, gson, listOf(SigilCatalog.WARDEN))
        assertEquals(1, first.size)
        val again = Progression.unlockSigils(prefs, gson, listOf(SigilCatalog.WARDEN))
        assertTrue(again.isEmpty())
        assertEquals(setOf(SigilCatalog.WARDEN), Progression.loadUnlockedSigilIds(prefs, gson))
    }

    @Test
    fun `unknown sigil ids are ignored`() {
        assertTrue(Progression.unlockSigils(prefs, gson, listOf("not_a_sigil")).isEmpty())
        assertTrue(Progression.loadUnlockedSigilIds(prefs, gson).isEmpty())
    }

    @Test
    fun `sigil unlocks are gated by the master toggle`() {
        prefs.putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, false)
        assertTrue(Progression.unlockSigils(prefs, gson, listOf(SigilCatalog.WARDEN)).isEmpty())
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

    @Test
    fun `lifetime earned tracks credits not debits`() {
        val today = com.infinicada.focuspocus.limit.SessionCooldownManager.todayString()
        saveTrials(listOf(
            Trial("t1", TrialType.COMPLETE_SESSIONS, target = 1, progress = 1,
                period = TrialPeriod.DAILY, periodKey = today, rewardMana = 40L)
        ))
        Progression.claimTrial(prefs, gson, "t1")
        assertEquals(40L, prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L))
        assertTrue(Progression.redeemBoon(prefs, gson, Boon("b", "Snack", 10L)))
        assertEquals(40L, prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L))
        assertEquals(30L, balance())
    }
}
