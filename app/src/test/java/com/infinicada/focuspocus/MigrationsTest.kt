package com.infinicada.focuspocus

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
}
