package com.infinicada.focuspocus

import android.content.SharedPreferences
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Calendar

class SessionRecorderTest {

    private val gson = Gson()
    private val fakePrefs = FakeSharedPreferences()

    @Test
    fun testCalculateCurrentStreak_empty() {
        val sessions = emptyList<FocusSession>()
        assertEquals(0, calculateCurrentStreak(sessions))
    }

    @Test
    fun testCalculateCurrentStreak_single() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            FocusSession(startTimeMillis = now - 60000, endTimeMillis = now, durationMinutes = 1, blockerName = "test", breaksUsed = 0)
        )
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun testRecord_basic() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000) // 2 mins ago
        fakePrefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKER, "TestBlocker")

        val sessions = SessionRecorder.record(fakePrefs, gson)

        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].durationMinutes)
        assertEquals("TestBlocker", sessions[0].blockerName)

        // SESSION_START_TIME should be removed
        assertEquals(0L, fakePrefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L))

        // FOCUS_SESSIONS should be updated in prefs
        val json = fakePrefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, "[]")
        assertTrue(json!!.contains("TestBlocker"))
    }
}
