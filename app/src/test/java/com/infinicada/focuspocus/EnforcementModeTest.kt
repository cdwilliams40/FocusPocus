package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementModeTest {

    private val prefs = FakeSharedPreferences()

    @Test
    fun `defaults to the accessibility detector`() {
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.DEFAULT)
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.of(prefs))
    }

    @Test
    fun `round-trips through prefs`() {
        EnforcementMode.store(prefs, EnforcementMode.POLLING)
        assertEquals(EnforcementMode.POLLING, EnforcementMode.of(prefs))

        EnforcementMode.store(prefs, EnforcementMode.ACCESSIBILITY)
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.of(prefs))
    }

    /**
     * The stored value is a bare enum name, so a rename or a corrupted store
     * would otherwise leave the app with no detector at all. Falling back to
     * the default keeps blocking working, which is the whole job.
     */
    @Test
    fun `an unrecognised stored value falls back rather than failing`() {
        prefs.putString(Constants.PrefsKeys.ENFORCEMENT_MODE, "SOMETHING_ELSE")
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.of(prefs))

        prefs.putString(Constants.PrefsKeys.ENFORCEMENT_MODE, "")
        assertEquals(EnforcementMode.ACCESSIBILITY, EnforcementMode.of(prefs))
    }

    /** Names are persisted, so renaming a constant silently migrates users. */
    @Test
    fun `stored names are the ones already in the wild`() {
        assertEquals(listOf("ACCESSIBILITY", "POLLING"), EnforcementMode.entries.map { it.name })
    }
}
