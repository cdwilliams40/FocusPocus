package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.WellApp
import com.infinicada.focuspocus.limit.WellApps
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

    private val gson = Gson()

    private fun seedLegacyLimits() {
        prefs.putString(
            Constants.PrefsKeys.BLOCKER_LISTS,
            """[{"name":"Social","mode":"BLACKLIST","apps":["com.circle1","com.circle2"]}]"""
        )
        prefs.putString(Migrations.LegacyKeys.APP_TIME_LIMIT_CONFIGS, gson.toJson(mapOf(
            "com.pact" to mapOf("packageName" to "com.pact", "pactModeEnabled" to true,
                "pactAlternativePackage" to "com.read", "wellExempt" to true),
            "com.ward" to mapOf("packageName" to "com.ward", "dailyLimitMinutes" to 30),
            "com.leaving" to mapOf("packageName" to "com.leaving", "pactModeEnabled" to true)
        )))
        prefs.putString(Migrations.LegacyKeys.PACT_GROUPS, gson.toJson(listOf(
            mapOf("blockerName" to "Social", "pactAlternativePackage" to "com.walk")
        )))
        prefs.putString(Migrations.LegacyKeys.PACT_PENDING_REVISIONS, gson.toJson(listOf(
            mapOf("packageName" to "com.leaving", "appliesAtMillis" to 5_000L),
            mapOf("packageName" to "com.pact", "appliesAtMillis" to 6_000L,
                "newConfig" to mapOf("packageName" to "com.pact", "pactModeEnabled" to true))
        )))
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
            WellApps.load(prefs, gson)
        )
    }

    @Test
    fun `a pending pact removal keeps its date and a pending edit is dropped`() {
        seedLegacyLimits()
        Migrations.run(prefs)
        assertEquals(mapOf("com.leaving" to 5_000L), WellApps.pendingRemovals(prefs, gson))
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
        assertTrue(WellApps.load(prefs, gson).isEmpty())
        assertEquals(Migrations.CURRENT_VERSION, prefs.getInt(Constants.PrefsKeys.SCHEMA_VERSION, 0))
    }

    // ── Step 3: talismans carry their own setup ──

    private fun talismans() = PrefsHelper.load<List<Talisman>>(
        prefs, gson, Constants.PrefsKeys.NAMED_TAGS,
        object : com.google.gson.reflect.TypeToken<List<Talisman>>() {}.type
    ).orEmpty()

    @Test
    fun `a talisman takes its bound quick spell's setup`() {
        prefs.putString(Constants.PrefsKeys.NAMED_TAGS, """[{"id":"t1","name":"Desk"},{"id":"t2","name":"Timer"}]""")
        prefs.putString(Migrations.LegacyKeys.FOCUS_PRESETS, gson.toJson(listOf(
            mapOf("id" to "p1", "name" to "Deep", "blockerNames" to listOf("Social", "Games"),
                "durationMinutes" to 45, "breaksEnabled" to false, "talismanId" to "t1", "action" to "TOGGLE"),
            mapOf("id" to "p2", "name" to "Quick", "blockerName" to "Social", "durationMinutes" to 0,
                "breaksEnabled" to true, "talismanId" to "t2", "action" to "TEMP_ENABLE", "tempDurationMinutes" to 20)
        )))
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
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Shield list")))
        Migrations.run(prefs)
        assertEquals(listOf("Shield list"), talismans().single().enchantments)
        assertTrue(talismans().single().locksSession)
    }

    @Test
    fun `a running tag-only session becomes a recorded talisman session`() {
        prefs.putString(Constants.PrefsKeys.NAMED_TAGS, """[{"id":"t1","name":"Shield"}]""")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
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
        assertEquals(gson.toJson(listOf("Social")), prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null))
        assertFalse(prefs.contains(Migrations.LegacyKeys.ACTIVE_BLOCKER))
    }
}
