package com.infinicada.focuspocus

import android.content.Context
import com.infinicada.focuspocus.data.SessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class SessionStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var mockContext: Context
    private lateinit var sessionRecorderMock: MockedStatic<SessionRecorder>
    private lateinit var dndControllerMock: MockedStatic<DndController>

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
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
    fun `store reacts automatically to SessionManager start, break, endBreak, and stop`() {
        val store = SessionStore(prefs)
        assertEquals(SessionState.Idle, store.state.value)

        SessionManager.startSession(
            sharedPreferences = prefs,
            blockerNames = listOf("Social", "Games"),
            durationMinutes = 45,
            breaksEnabled = true,
            talismanId = "tag-abc"
        )

        val active = store.state.value as SessionState.Active
        assertEquals(listOf("Social", "Games"), active.blockerNames)
        assertEquals(45, active.plannedDurationMinutes)
        assertEquals("tag-abc", active.talismanId)
        assertFalse(active.isOnBreak)

        val tBreak = System.currentTimeMillis()
        assertTrue(SessionManager.takeBreak(mockContext, prefs, breakDurationMinutes = 5, now = tBreak))
        val onBreak = store.state.value as SessionState.Active
        assertTrue(onBreak.isOnBreak)
        assertEquals(1, onBreak.breaksUsed)
        assertEquals(tBreak + 5 * 60_000L, onBreak.breakState!!.endTimeMillis)

        assertTrue(SessionManager.endBreak(mockContext, prefs, now = tBreak + 2 * 60_000L))
        val resumed = store.state.value as SessionState.Active
        assertFalse(resumed.isOnBreak)
        assertTrue(resumed.focusEndTimeMillis > tBreak)

        SessionManager.stopSession(mockContext, prefs)
        assertEquals(SessionState.Idle, store.state.value)

        store.close()
    }

    @Test
    fun `SessionManager advance expires break and timed session`() {
        val store = SessionStore(prefs)
        val t0 = 1_700_000_000_000L

        SessionManager.startSession(prefs, listOf("Social"), durationMinutes = 10)
        val started = store.state.value as SessionState.Active
        val focusEnd = started.focusEndTimeMillis

        val early = SessionManager.advance(mockContext, prefs, now = focusEnd - 1_000L)
        assertFalse(early.changed)
        assertTrue(store.state.value is SessionState.Active)

        val expired = SessionManager.advance(mockContext, prefs, now = focusEnd)
        assertTrue(expired.changed)
        assertEquals(listOf(SessionTransition.SessionExpired), expired.events)
        assertEquals(SessionState.Idle, store.state.value)

        // Start another, take break, and expire break via advance
        SessionManager.startSession(prefs, listOf("Work"), durationMinutes = 30)
        SessionManager.takeBreak(mockContext, prefs, breakDurationMinutes = 5, now = t0)
        val breakExpired = SessionManager.advance(mockContext, prefs, now = t0 + 5 * 60_000L)
        assertEquals(listOf(SessionTransition.BreakEnded), breakExpired.events)
        assertFalse((store.state.value as SessionState.Active).isOnBreak)

        store.close()
    }
}
