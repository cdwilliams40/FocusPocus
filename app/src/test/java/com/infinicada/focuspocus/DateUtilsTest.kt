package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DateUtilsTest {

    private fun createSession(daysAgo: Int): FocusSession {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val time = calendar.timeInMillis
        // FocusSession(startTimeMillis, endTimeMillis, durationMinutes, blockerName, breaksUsed)
        return FocusSession(time - 60000, time, 1, "TestBlocker", 0)
    }

    @Test
    fun calculateCurrentStreak_emptyList_returnsZero() {
        val sessions = emptyList<FocusSession>()
        assertEquals(0, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_singleSessionToday_returnsOne() {
        val sessions = listOf(createSession(0))
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_singleSessionYesterday_returnsOne() {
        val sessions = listOf(createSession(1))
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_todayAndYesterday_returnsTwo() {
        val sessions = listOf(createSession(0), createSession(1))
        assertEquals(2, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_streakOfThreeDays_returnsThree() {
        val sessions = listOf(createSession(0), createSession(1), createSession(2))
        assertEquals(3, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_gapToday_breakAtYesterday_returnsOne() {
        // Sessions: Today, 2 days ago. (Missing yesterday)
        // Expected: Streak of 1 (today).
        val sessions = listOf(createSession(0), createSession(2))
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_gapYesterday_breakAtTwoDaysAgo_returnsOne() {
        // Sessions: Yesterday, 3 days ago. (Missing today, missing 2 days ago)
        // Expected: Streak of 1 (yesterday).
        val sessions = listOf(createSession(1), createSession(3))
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_multipleSessionsSameDay_countsAsOne() {
        val sessions = listOf(createSession(0), createSession(0))
        assertEquals(1, calculateCurrentStreak(sessions))
    }

    @Test
    fun calculateCurrentStreak_onlySessionTwoDaysAgo_returnsZero() {
        val sessions = listOf(createSession(2))
        assertEquals(0, calculateCurrentStreak(sessions))
    }
}
