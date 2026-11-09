package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionTransitionsTest {

    private lateinit var prefs: FakeSharedPreferences
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `no session has no transition`() {
        assertNull(SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `timed manual session is due at its focus end`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 90_000L)
        assertEquals(now + 90_000L, SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `untimed manual session has no transition`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        assertNull(SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `ritual ignores the focus end timestamp`() {
        // Rituals end through checkSchedules and the ritual alarm, not a duration.
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "ritual")
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 90_000L)
        assertNull(SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `break is due at its end and hides the frozen focus end`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
        prefs.putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now + 300_000L)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 60_000L)
        assertEquals(now + 300_000L, SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `auto-break is due one interval after the focus stretch began`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, true)
        prefs.putInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 25)
        prefs.putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now - 10 * 60_000L)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 60 * 60_000L)
        assertEquals(now + 15 * 60_000L, SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `auto-break is skipped when session breaks are off`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, true)
        prefs.putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, false)
        prefs.putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now - 60_000L)
        assertNull(SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `auto-break applies to talisman sessions too`() {
        prefs.putString(Constants.PrefsKeys.FOCUS_TAG_ID, "tag")
        prefs.putBoolean(Constants.PrefsKeys.AUTO_BREAK_ENABLED, true)
        prefs.putInt(Constants.PrefsKeys.AUTO_BREAK_INTERVAL_MINUTES, 5)
        prefs.putLong(Constants.PrefsKeys.FOCUS_SEGMENT_START_MILLIS, now)
        assertEquals(now + 5 * 60_000L, SessionTransitions.nextDueMillis(prefs, now))
    }

    @Test
    fun `past-due instants are skipped so the wakeup cannot spin`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now - 1L)
        assertNull(SessionTransitions.nextDueMillis(prefs, now))

        prefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
        prefs.putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now)
        assertNull(SessionTransitions.nextDueMillis(prefs, now))
    }
}
