package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MigrationsTest {

    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `a fresh install ends at the current version`() {
        Migrations.run(prefs)
        assertEquals(Migrations.CURRENT_VERSION, prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0))
    }

    @Test
    fun `removed-feature keys are deleted and everything else is kept`() {
        prefs.putString(Migrations.LegacyKeys.BOONS, "[]")
        prefs.putString(Migrations.LegacyKeys.UNLOCKED_SIGILS, "[\"warden\"]")
        prefs.putInt(Migrations.LegacyKeys.HIGHEST_STREAK_MILESTONE_PAID, 30)
        prefs.putString(Migrations.LegacyKeys.LAST_SESSION_RECORDED_DATE, "20261110")
        prefs.putString(Migrations.LegacyKeys.LAST_WRAPUP_DATE, "20261110")
        prefs.putBoolean(Migrations.LegacyKeys.WRAPUP_ENABLED, false)
        prefs.putLong(Constants.PrefsKeys.MANA_BALANCE, 1234L)

        Migrations.run(prefs)

        assertFalse(prefs.contains(Migrations.LegacyKeys.BOONS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.UNLOCKED_SIGILS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.HIGHEST_STREAK_MILESTONE_PAID))
        assertFalse(prefs.contains(Migrations.LegacyKeys.LAST_SESSION_RECORDED_DATE))
        assertFalse(prefs.contains(Migrations.LegacyKeys.LAST_WRAPUP_DATE))
        assertFalse(prefs.contains(Migrations.LegacyKeys.WRAPUP_ENABLED))
        // The mana balance carries over unchanged.
        assertEquals(1234L, prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L))
    }

    @Test
    fun `running twice is a no-op`() {
        Migrations.run(prefs)
        prefs.putString(Migrations.LegacyKeys.BOONS, "[]")
        Migrations.run(prefs)
        // Already at the current version, so step 1 doesn't run again.
        assertTrue(prefs.contains(Migrations.LegacyKeys.BOONS))
    }

    // ── Step 2: the Well becomes the only limit ──

    private fun seedLegacyLimits() {
        prefs.putString(
            Constants.PrefsKeys.BLOCKER_LISTS,
            """[{"name":"Social","mode":"BLACKLIST","apps":["com.circle1","com.circle2"]}]"""
        )
        prefs.putString(
            Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS,
            """{"com.pact":{"packageName":"com.pact","pactModeEnabled":true,"pactAlternativePackage":"com.read","wellExempt":true},"com.ward":{"packageName":"com.ward","dailyLimitMinutes":30},"com.leaving":{"packageName":"com.leaving","pactModeEnabled":true}}"""
        )
        prefs.putString(
            Migrations.LegacyKeys.PACT_GROUPS,
            """[{"blockerName":"Social","pactAlternativePackage":"com.walk"}]"""
        )
        prefs.putString(
            Migrations.LegacyKeys.PACT_PENDING_REVISIONS,
            """[{"packageName":"com.leaving","appliesAtMillis":5000},{"packageName":"com.pact","appliesAtMillis":6000,"newConfig":{"packageName":"com.pact","pactModeEnabled":true}}]"""
        )
        prefs.putString(Migrations.LegacyKeys.APP_COOLDOWN_STATES, "{}")
        prefs.putBoolean(Migrations.LegacyKeys.GROUP_SEAL_ENABLED, true)
        prefs.putBoolean(Constants.PrefsKeys.WELL_ENABLED, false)
        prefs.putString(Constants.PrefsKeys.PACT_ALLOWANCES, """{"com.pact":999}""")
    }

    @Test
    fun `pacts and circle members become well apps and wards are dropped`() {
        seedLegacyLimits()
        Migrations.run(prefs)

        assertEquals(
            listOf(
                WellApp("com.pact", "com.read"),
                WellApp("com.leaving"),
                WellApp("com.circle1", "com.walk"),
                WellApp("com.circle2", "com.walk")
            ),
            WellApps.load(prefs)
        )
    }

    @Test
    fun `a pending pact removal keeps its date and a pending edit is dropped`() {
        seedLegacyLimits()
        Migrations.run(prefs)
        assertEquals(mapOf("com.leaving" to 5_000L), WellApps.pendingRemovals(prefs))
    }

    @Test
    fun `the well is switched on and the old stores are gone but running pacts stay`() {
        seedLegacyLimits()
        Migrations.run(prefs)

        assertTrue(prefs.getBoolean(Constants.PrefsKeys.WELL_ENABLED, false))
        assertFalse(prefs.contains(Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.PACT_GROUPS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.PACT_PENDING_REVISIONS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.APP_COOLDOWN_STATES))
        assertFalse(prefs.contains(Migrations.LegacyKeys.GROUP_SEAL_ENABLED))
        assertEquals("""{"com.pact":999}""", prefs.getString(Constants.PrefsKeys.PACT_ALLOWANCES, null))
    }

    @Test
    fun `unreadable legacy stores are skipped, not fatal`() {
        prefs.putString(Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS, "{not json")
        prefs.putString(Migrations.LegacyKeys.PACT_GROUPS, "[1, \"x\", null]")
        Migrations.run(prefs)
        assertTrue(WellApps.load(prefs).isEmpty())
        assertEquals(Migrations.CURRENT_VERSION, prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0))
    }

    // ── Step 3: talismans carry their own setup ──

    private fun talismans() = PrefsHelper.load<List<Talisman>>(
        prefs, Constants.PrefsKeys.NAMED_TAGS
    ).orEmpty()

    @Test
    fun `a talisman takes its bound quick spell's setup`() {
        prefs.putString(Constants.PrefsKeys.NAMED_TAGS, """[{"id":"t1","name":"Desk"},{"id":"t2","name":"Timer"}]""")
        prefs.putString(
            Migrations.LegacyKeys.FOCUS_PRESETS,
            """[{"id":"p1","name":"Deep","blockerNames":["Social","Games"],"durationMinutes":45,"breaksEnabled":false,"talismanId":"t1","action":"TOGGLE"},{"id":"p2","name":"Quick","blockerName":"Social","durationMinutes":0,"breaksEnabled":true,"talismanId":"t2","action":"TEMP_ENABLE","tempDurationMinutes":20}]"""
        )
        Migrations.run(prefs)

        assertEquals(
            listOf(
                Talisman("t1", "Desk", listOf("Social", "Games"), 45, breaksEnabled = false, requireToEnd = true),
                Talisman("t2", "Timer", listOf("Social"), 20, breaksEnabled = true, requireToEnd = true)
            ),
            talismans()
        )
        assertFalse(prefs.contains(Migrations.LegacyKeys.FOCUS_PRESETS))
    }

    @Test
    fun `a talisman without a spell takes the focus tab's enchantments`() {
        prefs.putString(Constants.PrefsKeys.NAMED_TAGS, """[{"id":"t1","name":"Shield"}]""")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(listOf("Shield list")))
        Migrations.run(prefs)
        assertEquals(listOf("Shield list"), talismans().single().enchantments)
        assertTrue(talismans().single().locksSession)
    }

    @Test
    fun `a running tag-only session becomes a recorded talisman session`() {
        prefs.putString(Constants.PrefsKeys.NAMED_TAGS, """[{"id":"t1","name":"Shield"}]""")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, AppJson.json.encodeToString(listOf("Social")))
        prefs.putString(Migrations.LegacyKeys.FOCUS_TAG_ID, "t1")
        Migrations.run(prefs)

        assertTrue(SessionManager.isSessionActive(prefs))
        assertEquals("t1", SessionManager.activeTalismanId(prefs))
        assertTrue(prefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L) > 0L)
        assertFalse(prefs.contains(Migrations.LegacyKeys.FOCUS_TAG_ID))
    }

    @Test
    fun `the single-enchantment key folds into the list`() {
        prefs.putString(Migrations.LegacyKeys.ACTIVE_BLOCKER, "Social")
        Migrations.run(prefs)
        assertEquals(AppJson.json.encodeToString(listOf("Social")), prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null))
        assertFalse(prefs.contains(Migrations.LegacyKeys.ACTIVE_BLOCKER))
    }

    // ── Step 4: conditional unlocks become productive apps ──

    @Test
    fun `each rule's required app becomes a productive app`() {
        prefs.putString(
            Migrations.LegacyKeys.CONDITIONAL_UNLOCKS,
            """[{"name":"Duo","requiredAppPackage":"com.duo","requiredMinutes":10},{"name":"Read","requiredAppPackage":"com.read","requiredMinutes":20}]"""
        )
        prefs.putString(Migrations.LegacyKeys.WELL_UNLOCK_CREDIT_DATE, "20261110")
        Migrations.run(prefs)
        assertEquals(listOf("com.duo", "com.read"), ProductiveApps.load(prefs))
        assertFalse(prefs.contains(Migrations.LegacyKeys.CONDITIONAL_UNLOCKS))
        assertFalse(prefs.contains(Migrations.LegacyKeys.WELL_UNLOCK_CREDIT_DATE))
    }

    @Test
    fun `unfinished trials are dropped and finished unclaimed ones kept`() {
        prefs.putString(
            Constants.PrefsKeys.TRIALS,
            """[{"id":"a","type":"COMPLETE_SESSIONS","target":2,"progress":1,"period":"DAILY","periodKey":"20261111","rewardMana":40,"claimed":false},{"id":"b","type":"FOCUS_MINUTES","target":60,"progress":60,"period":"DAILY","periodKey":"20261111","rewardMana":50,"claimed":false}]"""
        )
        Migrations.run(prefs)
        val ids = Progression.loadTrials(prefs).map { it.id }
        assertEquals(listOf("b"), ids)
    }

    @Test
    fun `step 5 drops unfinished reflex trials while keeping other active trials`() {
        prefs.putInt(Constants.PrefsKeys.SCHEMA_VERSION, 4)
        prefs.putString(
            Constants.PrefsKeys.TRIALS,
            """[{"id":"r1","type":"NO_REFLEX_OPENS","target":1,"progress":0,"period":"DAILY","periodKey":"20261111","rewardMana":40,"claimed":false},{"id":"r2","type":"NO_REFLEX_OPENS","target":1,"progress":1,"period":"DAILY","periodKey":"20261111","rewardMana":40,"claimed":false},{"id":"f1","type":"FOCUS_MINUTES","target":60,"progress":10,"period":"DAILY","periodKey":"20261111","rewardMana":50,"claimed":false}]"""
        )
        Migrations.run(prefs)
        val ids = Progression.loadTrials(prefs).map { it.id }
        assertEquals(listOf("r2", "f1"), ids)
    }
}
