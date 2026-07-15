package com.infinicada.focuspocus

import com.infinicada.focuspocus.data.SettingsRepository
import com.infinicada.focuspocus.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = SettingsRepository(prefs)
    }

    // ── breakDurationMinutes ──

    @Test
    fun `getBreakDurationMinutes returns default 5 when not set`() {
        assertEquals(5, repo.getBreakDurationMinutes())
    }

    @Test
    fun `getBreakDurationMinutes returns stored value within range`() {
        prefs.putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 15)
        assertEquals(15, repo.getBreakDurationMinutes())
    }

    @Test
    fun `getBreakDurationMinutes clamps below minimum to 1`() {
        prefs.putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 0)
        assertEquals(1, repo.getBreakDurationMinutes())
    }

    @Test
    fun `getBreakDurationMinutes clamps above maximum to 30`() {
        prefs.putInt(Constants.PrefsKeys.BREAK_DURATION_MINUTES, 99)
        assertEquals(30, repo.getBreakDurationMinutes())
    }

    // ── maxBreaksPerSession ──

    @Test
    fun `getMaxBreaksPerSession returns default 3 when not set`() {
        assertEquals(3, repo.getMaxBreaksPerSession())
    }

    @Test
    fun `getMaxBreaksPerSession returns stored value within range`() {
        prefs.putInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 7)
        assertEquals(7, repo.getMaxBreaksPerSession())
    }

    @Test
    fun `getMaxBreaksPerSession clamps below minimum to 1`() {
        prefs.putInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 0)
        assertEquals(1, repo.getMaxBreaksPerSession())
    }

    @Test
    fun `getMaxBreaksPerSession clamps above maximum to 10`() {
        prefs.putInt(Constants.PrefsKeys.MAX_BREAKS_PER_SESSION, 50)
        assertEquals(10, repo.getMaxBreaksPerSession())
    }

    // ── emergencyBreakCadenceWeeks ──

    @Test
    fun `getEmergencyBreakCadenceWeeks returns default 2 when not set`() {
        assertEquals(2, repo.getEmergencyBreakCadenceWeeks())
    }

    @Test
    fun `getEmergencyBreakCadenceWeeks returns stored value within range`() {
        prefs.putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 5)
        assertEquals(5, repo.getEmergencyBreakCadenceWeeks())
    }

    @Test
    fun `getEmergencyBreakCadenceWeeks clamps below minimum to 2`() {
        prefs.putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 1)
        assertEquals(2, repo.getEmergencyBreakCadenceWeeks())
    }

    @Test
    fun `getEmergencyBreakCadenceWeeks clamps above maximum to 8`() {
        prefs.putInt(Constants.PrefsKeys.EMERGENCY_BREAK_CADENCE_WEEKS, 20)
        assertEquals(8, repo.getEmergencyBreakCadenceWeeks())
    }

    // ── themeMode ──

    @Test
    fun `getThemeMode returns DARK by default`() {
        assertEquals(ThemeMode.DARK, repo.getThemeMode())
    }

    @Test
    fun `getThemeMode returns stored mode`() {
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "DARK")
        assertEquals(ThemeMode.DARK, repo.getThemeMode())
    }

    @Test
    fun `getThemeMode returns DARK for invalid value`() {
        prefs.putString(Constants.PrefsKeys.THEME_MODE, "INVALID_GARBAGE")
        assertEquals(ThemeMode.DARK, repo.getThemeMode())
    }

    @Test
    fun `setThemeMode round trip`() {
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.getThemeMode())
    }

    // ── setOnboardingCompleted ──

    @Test
    fun `setOnboardingCompleted sets multiple prefs atomically`() {
        repo.setOnboardingCompleted()
        assertTrue(prefs.getBoolean(Constants.PrefsKeys.ONBOARDING_COMPLETED, false))
        assertEquals(1, prefs.getInt(Constants.PrefsKeys.ONBOARDING_VERSION, 0))
        assertTrue(prefs.getBoolean(Constants.PrefsKeys.ANALYTICS_CONSENT_SHOWN, false))
    }

    // ── boolean settings round trips ──

    @Test
    fun `muteBlockedNotifications defaults to true`() {
        assertTrue(repo.getMuteBlockedNotifications())
    }

    @Test
    fun `hideStopButton defaults to false`() {
        assertFalse(repo.getHideStopButton())
    }

    @Test
    fun `nfcLockMode defaults to false`() {
        assertFalse(repo.getNfcLockMode())
    }

    // ── progression toggles ──

    @Test
    fun `progression is on by default with a working toggle`() {
        assertTrue(repo.getProgressionEnabled())
        repo.setProgressionEnabled(false)
        assertFalse(repo.getProgressionEnabled())
        repo.setProgressionEnabled(true)
        assertTrue(repo.getProgressionEnabled())
    }

    @Test
    fun `wrapup and trial alerts default on and toggle`() {
        assertTrue(repo.getWrapupEnabled())
        assertTrue(repo.getTrialAlertsEnabled())
        repo.setWrapupEnabled(false)
        repo.setTrialAlertsEnabled(false)
        assertFalse(repo.getWrapupEnabled())
        assertFalse(repo.getTrialAlertsEnabled())
    }

    @Test
    fun `progression intro is shown once`() {
        assertFalse(repo.isProgressionIntroShown())
        repo.setProgressionIntroShown()
        assertTrue(repo.isProgressionIntroShown())
    }

    // ── warden (device owner) settings ──

    @Test
    fun `pact greying defaults on and toggles`() {
        assertTrue(repo.getDeviceOwnerSuspendPacts())
        repo.setDeviceOwnerSuspendPacts(false)
        assertFalse(repo.getDeviceOwnerSuspendPacts())
        repo.setDeviceOwnerSuspendPacts(true)
        assertTrue(repo.getDeviceOwnerSuspendPacts())
    }

    @Test
    fun `warden removal request stores, survives, and clears`() {
        assertEquals(0L, repo.getWardenRemovalRequestMillis())
        repo.setWardenRemovalRequestMillis(1_234L)
        assertEquals(1_234L, repo.getWardenRemovalRequestMillis())
        repo.clearWardenRemovalRequest()
        assertEquals(0L, repo.getWardenRemovalRequestMillis())
    }
}
