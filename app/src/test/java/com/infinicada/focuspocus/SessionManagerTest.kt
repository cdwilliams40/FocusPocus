package com.infinicada.focuspocus

import android.content.Context
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class SessionManagerTest {

    private lateinit var sharedPreferences: FakeSharedPreferences
    private lateinit var mockContext: Context
    private lateinit var sessionRecorderMock: MockedStatic<SessionRecorder>
    private lateinit var dndControllerMock: MockedStatic<DndController>
    private val gson = Gson()

    @Before
    fun setUp() {
        sharedPreferences = FakeSharedPreferences()
        mockContext = Mockito.mock(Context::class.java)
        sessionRecorderMock = Mockito.mockStatic(SessionRecorder::class.java)
        dndControllerMock = Mockito.mockStatic(DndController::class.java)
    }

    @After
    fun tearDown() {
        sessionRecorderMock.close()
        dndControllerMock.close()
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

    // ── stopSession ──

    @Test
    fun `stopSession clears all session prefs`() {
        SessionManager.startSession(sharedPreferences, "TestBlocker", durationMinutes = 25)
        assertTrue(SessionManager.isSessionActive(sharedPreferences))

        SessionManager.stopSession(mockContext, sharedPreferences, gson)

        assertFalse(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.ACTIVE_BLOCKER))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.ACTIVE_BLOCKERS))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.FOCUS_TAG_ID))
        assertFalse(sharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, true))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, -1))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, -1))
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.SESSION_START_TIME))
    }

    @Test
    fun `start then stop round trip leaves session inactive`() {
        SessionManager.startSession(sharedPreferences, "TestBlocker", durationMinutes = 25)
        assertTrue(SessionManager.isSessionActive(sharedPreferences))

        SessionManager.stopSession(mockContext, sharedPreferences, gson)
        assertFalse(SessionManager.isSessionActive(sharedPreferences))
    }

    // ── extra-break perk tokens ──

    @Test
    fun `startSession clears leftover extra-break tokens`() {
        sharedPreferences.putInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 2)
        SessionManager.startSession(sharedPreferences, "TestBlocker", durationMinutes = 25)
        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.EXTRA_BREAK_TOKENS))
    }

    @Test
    fun `stopSession clears extra-break tokens and returns a result`() {
        SessionManager.startSession(sharedPreferences, "TestBlocker", durationMinutes = 25)
        sharedPreferences.putInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 1)

        val result = SessionManager.stopSession(mockContext, sharedPreferences, gson)

        assertFalse(sharedPreferences.contains(Constants.PrefsKeys.EXTRA_BREAK_TOKENS))
        // SessionRecorder is static-mocked in this test, so the tolerant
        // empty result is returned rather than a real recording.
        assertTrue(result.sessions.isEmpty())
        assertEquals(null, result.recorded)
    }
}
