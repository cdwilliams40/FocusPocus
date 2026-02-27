package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private lateinit var sharedPreferences: FakeSharedPreferences

    @Before
    fun setUp() {
        sharedPreferences = FakeSharedPreferences()
    }

    @Test
    fun `startSession sets manual focus mode and active blocker`() {
        val blockerName = "TestBlocker"
        val durationMinutes = 25
        val breaksEnabled = true

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerName = blockerName,
            durationMinutes = durationMinutes,
            breaksEnabled = breaksEnabled
        )

        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals(blockerName, sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null))
        assertTrue(sharedPreferences.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L) > 0)
        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, false))

        // Schedule ID should be removed
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID))

        // Duration and Time Remaining
        assertEquals(durationMinutes, sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, -1))
        assertEquals(durationMinutes * 60, sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1))
    }

    @Test
    fun `startSession with scheduleId sets schedule and removes duration`() {
        val blockerName = "ScheduledBlocker"
        val scheduleId = "schedule_123"

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerName = blockerName,
            scheduleId = scheduleId
        )

        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals(scheduleId, sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null))

        // Duration and Time Remaining should be removed for scheduled sessions
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.FOCUS_DURATION_MINUTES))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.FOCUS_TIME_REMAINING))
    }

    @Test
    fun `startSession with default values`() {
        val blockerName = "DefaultBlocker"
        // Relying on defaults: durationMinutes=0, breaksEnabled=true, scheduleId=null

        SessionManager.startSession(
            sharedPreferences = sharedPreferences,
            blockerName = blockerName
        )

        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, -1))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1))
        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, false))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID))
    }
}
