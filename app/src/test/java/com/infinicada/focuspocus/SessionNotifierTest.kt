package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionNotifierTest {

    private lateinit var prefs: FakeSharedPreferences
    private val gson = Gson()
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `no active session resolves to null`() {
        assertNull(SessionNotifier.resolveState(prefs, gson, now))
    }

    @Test
    fun `active blockers alone without a session anchor resolve to null`() {
        // Selecting enchantments on the Focus tab persists them before any
        // session starts; that alone must not surface a notification.
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
        assertNull(SessionNotifier.resolveState(prefs, gson, now))
    }

    @Test
    fun `timed manual session counts down to focus end and names blockers`() {
        val end = now + 25 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social", "Games")))
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, now - 60_000L)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, end)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertEquals("Social, Games", state!!.sessionName)
        assertFalse(state.isRitual)
        assertFalse(state.onBreak)
        assertEquals(end, state.countdownEndMillis)
    }

    @Test
    fun `untimed session counts up from session start`() {
        val start = now - 10 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, start)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertNull(state!!.countdownEndMillis)
        assertEquals(start, state.countUpStartMillis)
    }

    @Test
    fun `expired focus end falls back to counting up instead of a negative countdown`() {
        val start = now - 30 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, start)
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now - 5_000L)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertNull(state!!.countdownEndMillis)
        assertEquals(start, state.countUpStartMillis)
    }

    @Test
    fun `scheduled session uses ritual name and schedule window end`() {
        val schedule = Schedule(id = "sched-1", name = "Evening Ritual")
        val windowEnd = now + 2 * 60 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.SCHEDULES, gson.toJson(listOf(schedule)))
        prefs.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "sched-1")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, now - 60_000L)
        prefs.putLong(Constants.PrefsKeys.SCHEDULE_END_TIME_MILLIS, windowEnd)
        // A leftover manual end time must not drive a ritual's countdown.
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 60_000L)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertEquals("Evening Ritual", state!!.sessionName)
        assertTrue(state.isRitual)
        assertEquals(windowEnd, state.countdownEndMillis)
    }

    @Test
    fun `dangling schedule id falls back to blocker names as a plain spell`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, "deleted-id")
        prefs.putString(Constants.PrefsKeys.SCHEDULES, gson.toJson(listOf(Schedule(id = "other", name = "Other"))))
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertEquals("Social", state!!.sessionName)
        assertFalse(state.isRitual)
    }

    @Test
    fun `break counts down to break end and keeps the session name`() {
        val breakEnd = now + 5 * 60_000L
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))
        prefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
        prefs.putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, breakEnd)
        // During a break the focus end is parked; even if a stale one lingers
        // the break end must win.
        prefs.putLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, now + 60 * 60_000L)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertTrue(state!!.onBreak)
        assertEquals("Social", state.sessionName)
        assertEquals(breakEnd, state.countdownEndMillis)
        assertNull(state.countUpStartMillis)
    }

    @Test
    fun `break whose end already passed shows no countdown`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
        prefs.putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now - 1_000L)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertTrue(state!!.onBreak)
        assertNull(state.countdownEndMillis)
    }

    @Test
    fun `talisman-only session is active with no chronometer anchors`() {
        prefs.putString(Constants.PrefsKeys.FOCUS_TAG_ID, "tag-1")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, gson.toJson(listOf("Social")))

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertEquals("Social", state!!.sessionName)
        assertNull(state.countdownEndMillis)
        assertNull(state.countUpStartMillis)
    }

    @Test
    fun `corrupt active blockers json falls back to the single blocker pref`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKERS, "{not json]")
        prefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKER, "Social")

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertEquals("Social", state!!.sessionName)
    }

    @Test
    fun `no blockers and no schedule leaves the session unnamed`() {
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertNull(state!!.sessionName)
        assertFalse(state.isRitual)
    }

    @Test
    fun `session start in the future is not used as a count-up anchor`() {
        // A clock set backwards can leave the recorded start ahead of "now";
        // a chronometer counting up from the future would render negative.
        prefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        prefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, now + 60_000L)

        val state = SessionNotifier.resolveState(prefs, gson, now)

        assertNotNull(state)
        assertNull(state!!.countUpStartMillis)
    }
}
