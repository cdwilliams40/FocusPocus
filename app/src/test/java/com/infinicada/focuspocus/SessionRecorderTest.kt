package com.infinicada.focuspocus

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class SessionRecorderTest {

    private val gson = Gson()
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
    }

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
        fakePrefs.putInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, 1)

        val result = SessionRecorder.record(fakePrefs, gson)
        val sessions = result.sessions

        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].durationMinutes)
        assertEquals("TestBlocker", sessions[0].blockerName)
        assertEquals(1, sessions[0].breaksUsed)
        assertEquals(sessions[0], result.recorded)
        assertEquals(1, result.newStreak)

        // SESSION_START_TIME should be removed
        assertEquals(0L, fakePrefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L))

        // FOCUS_SESSIONS should be updated in prefs
        val json = fakePrefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, "[]")
        assertTrue(json!!.contains("TestBlocker"))
    }

    @Test
    fun testRecord_noStartTime() {
        // No start time set in prefs
        val result = SessionRecorder.record(fakePrefs, gson)
        assertEquals(0, result.sessions.size)
        assertEquals(null, result.recorded)
    }

    @Test
    fun testRecord_shortSession() {
        // Set start time to 30 seconds ago
        val startTime = System.currentTimeMillis() - 30000
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, startTime)

        val result = SessionRecorder.record(fakePrefs, gson)
        assertEquals(0, result.sessions.size)
        assertEquals(null, result.recorded)

        // Start time should NOT be removed if session was too short
        assertEquals(startTime, fakePrefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L))

        // Discarded sessions never earn mana
        assertEquals(0L, fakePrefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L))
    }

    @Test
    fun testRecord_updatesStreak() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000)
        fakePrefs.putInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

        val result = SessionRecorder.record(fakePrefs, gson)

        assertEquals(1, result.sessions.size)
        assertEquals(1, fakePrefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0))
    }

    @Test
    fun testRecord_noUpdateStreak() {
        // Longest streak is already 5
        fakePrefs.putInt(Constants.PrefsKeys.LONGEST_STREAK, 5)
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000)

        SessionRecorder.record(fakePrefs, gson)

        // New streak is 1 (just today). 1 <= 5, so no update.
        assertEquals(5, fakePrefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0))
    }

    @Test
    fun testRecord_prunesOldSessions() {
        val manySessions = mutableListOf<FocusSession>()
        // Create 505 sessions
        val now = System.currentTimeMillis()
        for (i in 1..505) {
            manySessions.add(FocusSession(
                startTimeMillis = now - (i * 60000),
                endTimeMillis = now - (i * 60000) + 60000,
                durationMinutes = 1,
                blockerName = "Old$i",
                breaksUsed = 0
            ))
        }

        fakePrefs.putString(Constants.PrefsKeys.FOCUS_SESSIONS, gson.toJson(manySessions))
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000)

        val result = SessionRecorder.record(fakePrefs, gson)

        // Should have 500 sessions.
        assertEquals(500, result.sessions.size)

        // The last one added should be present
        assertEquals("Unknown", result.sessions.last().blockerName) // Since we didn't set ACTIVE_BLOCKER
    }

    @Test
    fun testRecord_corruptedJson() {
        fakePrefs.putString(Constants.PrefsKeys.FOCUS_SESSIONS, "{ corrupted json }")
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000)

        val result = SessionRecorder.record(fakePrefs, gson)

        // Should ignore corrupted json, start fresh list
        assertEquals(1, result.sessions.size)

        // Streak should be 1
        assertEquals(1, fakePrefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0))
    }

    // ── Progression award integration ──

    @Test
    fun testRecord_awardsManaAtomically() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 25 * 60000)

        val result = SessionRecorder.record(fakePrefs, gson)

        // 25 min * (1 + 0.02 * streak(1)) = 25.5 -> 25
        assertEquals(25L, result.manaEarned)
        assertEquals(25L, fakePrefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L))
        assertTrue(fakePrefs.getString(Constants.PrefsKeys.MANA_LEDGER, "")!!.contains("SESSION"))
        // Activity marker for the wrap-up guard
        assertTrue(fakePrefs.contains(Constants.PrefsKeys.LAST_SESSION_RECORDED_DATE))
    }

    @Test
    fun testRecord_noAwardWhenProgressionDisabled() {
        fakePrefs.putBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, false)
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 25 * 60000)

        val result = SessionRecorder.record(fakePrefs, gson)

        assertEquals(1, result.sessions.size)
        assertEquals(0L, result.manaEarned)
        assertEquals(0L, fakePrefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L))
    }

    @Test
    fun testRecord_advancesTrialsAndReportsCompletions() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120 * 60000)

        val result = SessionRecorder.record(fakePrefs, gson)

        // The deterministic daily slate always contains at least one
        // session-driven trial; a 120-minute session completes any
        // FOCUS_MINUTES daily draw and advances COMPLETE_SESSIONS.
        val trialsJson = fakePrefs.getString(Constants.PrefsKeys.TRIALS, "")!!
        assertTrue(trialsJson.isNotEmpty())
        assertTrue(result.completedTrials.all { it.progress >= it.target })
    }

    @Test
    fun testRecord_unlocksFirstSpellSigil() {
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 120000)

        val result = SessionRecorder.record(fakePrefs, gson)

        assertTrue(result.unlockedSigils.any { it.id == "first_spell" })
        assertTrue(fakePrefs.getString(Constants.PrefsKeys.UNLOCKED_SIGILS, "")!!.contains("first_spell"))
    }
}
