package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {

    private val t0 = 1_700_000_000_000L
    private val min = 60_000L

    private fun activeSession(
        durationMin: Int = 60,
        scheduleId: String? = null,
        talismanId: String? = null,
        breaksEnabled: Boolean = true,
        breaksUsed: Int = 0,
        extraTokens: Int = 0,
        segmentStart: Long = t0,
        breakState: SessionState.BreakState? = null
    ) = SessionState.Active(
        blockerNames = listOf("Social"),
        startTimeMillis = t0,
        plannedDurationMinutes = durationMin,
        focusEndTimeMillis = if (scheduleId == null && durationMin > 0 && breakState == null) t0 + durationMin * min else 0L,
        focusTimeRemainingSeconds = if (scheduleId == null && durationMin > 0) durationMin * 60 else 0,
        scheduleId = scheduleId,
        talismanId = talismanId,
        breaksEnabled = breaksEnabled,
        breaksUsed = breaksUsed,
        extraBreakTokens = extraTokens,
        focusSegmentStartMillis = segmentStart,
        breakState = breakState
    )

    @Test
    fun `advance on Idle stays Idle with no events`() {
        val result = SessionRules.advance(SessionState.Idle, SessionBreakPolicy(), t0 + 10 * min)
        assertEquals(SessionState.Idle, result.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `advance expires timed manual session at or after focus end`() {
        val session = activeSession(durationMin = 30)
        val before = SessionRules.advance(session, SessionBreakPolicy(), t0 + 30 * min - 1)
        assertTrue(before.events.isEmpty())
        assertTrue(before.state is SessionState.Active)

        val atEnd = SessionRules.advance(session, SessionBreakPolicy(), t0 + 30 * min)
        assertEquals(SessionState.Idle, atEnd.state)
        assertEquals(listOf(SessionTransition.SessionExpired), atEnd.events)
    }

    @Test
    fun `advance does not expire ritual session on focus end`() {
        val ritual = activeSession(durationMin = 0, scheduleId = "morning")
        val result = SessionRules.advance(ritual, SessionBreakPolicy(), t0 + 120 * min)
        assertTrue(result.events.isEmpty())
        assertEquals(ritual, result.state)
    }

    @Test
    fun `startBreak freezes focus remaining and endBreak resumes focus end from frozen remaining`() {
        val session = activeSession(durationMin = 30)
        val breakStartAt = t0 + 10 * min // 20m remaining
        val onBreak = SessionRules.startBreak(session, breakDurationMinutes = 5, nowMillis = breakStartAt)

        assertTrue(onBreak.isOnBreak)
        assertEquals(1, onBreak.breaksUsed)
        assertEquals(0L, onBreak.focusEndTimeMillis)
        assertEquals(20 * 60, onBreak.focusTimeRemainingSeconds)
        assertEquals(breakStartAt + 5 * min, onBreak.breakState!!.endTimeMillis)

        // While on break, advance before break end is a no-op
        val midBreak = SessionRules.advance(onBreak, SessionBreakPolicy(), breakStartAt + 4 * min)
        assertTrue(midBreak.events.isEmpty())
        assertEquals(onBreak, midBreak.state)

        // At break end, break ends and focus end is recomputed from frozen 20m
        val breakEndAt = breakStartAt + 5 * min
        val afterBreak = SessionRules.advance(onBreak, SessionBreakPolicy(), breakEndAt)
        assertEquals(listOf(SessionTransition.BreakEnded), afterBreak.events)
        val resumed = afterBreak.state as SessionState.Active
        assertFalse(resumed.isOnBreak)
        assertEquals(breakEndAt + 20 * min, resumed.focusEndTimeMillis)
        assertEquals(breakEndAt, resumed.focusSegmentStartMillis)
    }

    @Test
    fun `advance starts Pomodoro auto-break when focus stretch reaches interval`() {
        val session = activeSession(durationMin = 60, segmentStart = t0)
        val policy = SessionBreakPolicy(
            autoBreakEnabled = true,
            autoBreakIntervalMinutes = 25,
            breakDurationMinutes = 5,
            maxBreaksPerSession = 3
        )

        val before = SessionRules.advance(session, policy, t0 + 25 * min - 1)
        assertTrue(before.events.isEmpty())

        val atDue = SessionRules.advance(session, policy, t0 + 25 * min)
        assertEquals(listOf(SessionTransition.AutoBreakStarted(25, 5)), atDue.events)
        val onBreak = atDue.state as SessionState.Active
        assertTrue(onBreak.isOnBreak)
        assertEquals(1, onBreak.breaksUsed)
        assertEquals(35 * 60, onBreak.focusTimeRemainingSeconds)
        assertEquals(t0 + 30 * min, onBreak.breakState!!.endTimeMillis)
    }

    @Test
    fun `auto-break respects quota and extra break tokens`() {
        val policy = SessionBreakPolicy(autoBreakEnabled = true, autoBreakIntervalMinutes = 25, maxBreaksPerSession = 2)
        val exhausted = activeSession(durationMin = 90, breaksUsed = 2, extraTokens = 0)
        val resultExhausted = SessionRules.advance(exhausted, policy, t0 + 30 * min)
        assertTrue(resultExhausted.events.isEmpty())
        assertNull(SessionRules.nextDueMillis(exhausted.copy(focusEndTimeMillis = 0L), policy, t0))

        val withToken = exhausted.copy(extraBreakTokens = 1)
        val resultToken = SessionRules.advance(withToken, policy, t0 + 30 * min)
        assertEquals(listOf(SessionTransition.AutoBreakStarted(25, 5)), resultToken.events)
    }

    @Test
    fun `auto-break respects schedule overrides`() {
        val ritual = activeSession(durationMin = 0, scheduleId = "work")
        val disabledBySchedule = SessionBreakPolicy(
            autoBreakEnabled = true,
            autoBreakIntervalMinutes = 25,
            scheduleBreaksEnabled = false
        )
        assertTrue(SessionRules.advance(ritual, disabledBySchedule, t0 + 30 * min).events.isEmpty())

        val customScheduleBreak = SessionBreakPolicy(
            autoBreakEnabled = true,
            autoBreakIntervalMinutes = 25,
            breakDurationMinutes = 5,
            scheduleBreaksEnabled = true,
            scheduleBreakDurationMinutes = 10
        )
        val result = SessionRules.advance(ritual, customScheduleBreak, t0 + 25 * min)
        assertEquals(listOf(SessionTransition.AutoBreakStarted(25, 10)), result.events)
        assertEquals(t0 + 35 * min, (result.state as SessionState.Active).breakState!!.endTimeMillis)
    }

    @Test
    fun `nextDueMillis picks earliest future transition`() {
        val policy = SessionBreakPolicy(autoBreakEnabled = true, autoBreakIntervalMinutes = 25)
        val session = activeSession(durationMin = 60, segmentStart = t0)
        // Auto-break at t0 + 25m is earlier than focus end at t0 + 60m
        assertEquals(t0 + 25 * min, SessionRules.nextDueMillis(session, policy, t0 + 10 * min))

        // If focus ends at t0 + 20m, focus end wins over auto-break at t0 + 25m
        val shorter = activeSession(durationMin = 20, segmentStart = t0)
        assertEquals(t0 + 20 * min, SessionRules.nextDueMillis(shorter, policy, t0 + 10 * min))
    }
}
