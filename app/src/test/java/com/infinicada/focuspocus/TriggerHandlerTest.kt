package com.infinicada.focuspocus

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.infinicada.focuspocus.handler.NfcResult
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.handler.TriggerResult
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.model.Schedule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class TriggerHandlerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var handler: TriggerHandler
    private lateinit var mockContext: Context
    private lateinit var sessionRecorderMock: MockedStatic<SessionRecorder>
    private lateinit var dndControllerMock: MockedStatic<DndController>

    private val gson = Gson()

    private val testBlocker = Blocker("TestBlocker", BlockerMode.BLACKLIST, setOf("com.test"))
    private val blockerLists = listOf(testBlocker)

    private fun makePreset(
        id: String = "abc123",
        name: String = "TestPreset",
        blockerNames: List<String> = listOf("TestBlocker"),
        action: PresetAction? = PresetAction.TOGGLE,
        durationMinutes: Int = 25,
        breaksEnabled: Boolean = true,
        talismanId: String? = null,
        tempDurationMinutes: Int? = 30
    ) = FocusPreset(
        id = id,
        name = name,
        blockerNames = blockerNames,
        action = action,
        durationMinutes = durationMinutes,
        breaksEnabled = breaksEnabled,
        talismanId = talismanId,
        tempDurationMinutes = tempDurationMinutes
    )

    private fun mockUri(scheme: String?, host: String?, pathSegments: List<String>): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.scheme).thenReturn(scheme)
        Mockito.`when`(uri.host).thenReturn(host)
        Mockito.`when`(uri.pathSegments).thenReturn(pathSegments)
        return uri
    }

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        mockContext = Mockito.mock(Context::class.java)
        sessionRecorderMock = Mockito.mockStatic(SessionRecorder::class.java)
        dndControllerMock = Mockito.mockStatic(DndController::class.java)
        handler = TriggerHandler(mockContext, prefs, gson)
    }

    @After
    fun tearDown() {
        sessionRecorderMock.close()
        dndControllerMock.close()
    }

    // ── togglePreset TOGGLE ──

    @Test
    fun `togglePreset TOGGLE when inactive starts session and returns Success`() {
        val preset = makePreset(action = PresetAction.TOGGLE)
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        assertEquals(R.string.toast_preset_cast, (result as TriggerResult.Success).messageResId)
        assertTrue(SessionManager.isSessionActive(prefs))
    }

    @Test
    fun `togglePreset TOGGLE when active stops session and returns Success`() {
        SessionManager.startSession(prefs, "TestBlocker", durationMinutes = 25)
        val preset = makePreset(action = PresetAction.TOGGLE)

        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        assertEquals(R.string.toast_preset_dispelled, (result as TriggerResult.Success).messageResId)
    }

    @Test
    fun `togglePreset TOGGLE when inactive with no valid blockers returns Error`() {
        val preset = makePreset(blockerNames = listOf("NonExistent"))
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Error)
        assertEquals(R.string.toast_enchantment_missing, (result as TriggerResult.Error).messageResId)
    }

    @Test
    fun `togglePreset null action defaults to TOGGLE`() {
        val preset = makePreset(action = null)
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        assertEquals(R.string.toast_preset_cast, (result as TriggerResult.Success).messageResId)
        assertTrue(SessionManager.isSessionActive(prefs))
    }

    // ── togglePreset TEMP_ENABLE ──

    @Test
    fun `togglePreset TEMP_ENABLE starts timed session`() {
        val preset = makePreset(action = PresetAction.TEMP_ENABLE, tempDurationMinutes = 15)
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        assertEquals(R.string.toast_preset_cast_timed, (result as TriggerResult.Success).messageResId)
        assertTrue(SessionManager.isSessionActive(prefs))
    }

    @Test
    fun `togglePreset TEMP_ENABLE with no valid blockers returns Error`() {
        val preset = makePreset(action = PresetAction.TEMP_ENABLE, blockerNames = listOf("NonExistent"))
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Error)
        assertEquals(R.string.toast_enchantment_missing, (result as TriggerResult.Error).messageResId)
    }

    @Test
    fun `togglePreset TEMP_ENABLE with null tempDuration defaults to 30`() {
        val preset = makePreset(action = PresetAction.TEMP_ENABLE, tempDurationMinutes = null)
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        val args = (result as TriggerResult.Success).args
        assertEquals(30, args[1])
    }

    // ── togglePreset TEMP_DISABLE ──

    @Test
    fun `togglePreset TEMP_DISABLE when active sets break prefs and returns Success`() {
        SessionManager.startSession(prefs, "TestBlocker", durationMinutes = 25)
        val preset = makePreset(action = PresetAction.TEMP_DISABLE, tempDurationMinutes = 10)

        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Success)
        assertEquals(R.string.toast_temp_break, (result as TriggerResult.Success).messageResId)
        assertTrue(prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false))
        assertEquals(600, prefs.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, 0))
    }

    @Test
    fun `togglePreset TEMP_DISABLE freezes focus countdown for the break`() {
        // A 25-minute timed session persists its wall-clock end timestamp.
        SessionManager.startSession(prefs, "TestBlocker", durationMinutes = 25)
        val focusEndBefore = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
        assertTrue(focusEndBefore > 0L)

        val preset = makePreset(action = PresetAction.TEMP_DISABLE, tempDurationMinutes = 10)
        handler.togglePreset(preset, blockerLists)

        // The countdown must be parked (end timestamp dropped, remaining seconds frozen)
        // so the break-end path resumes from where the session left off instead of
        // restarting the full duration from the stale value written at session start.
        assertEquals(0L, prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L))
        val frozenRemaining = prefs.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1)
        assertTrue("Frozen remaining should be ~25min, was $frozenRemaining",
            frozenRemaining in (25 * 60 - 5)..(25 * 60))
    }

    @Test
    fun `togglePreset TEMP_DISABLE without timed session leaves focus countdown untouched`() {
        // Unlimited session: no end timestamp exists and none must be invented.
        SessionManager.startSession(prefs, "TestBlocker", durationMinutes = 0)
        val preset = makePreset(action = PresetAction.TEMP_DISABLE, tempDurationMinutes = 10)

        handler.togglePreset(preset, blockerLists)

        assertTrue(prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false))
        assertEquals(0L, prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L))
        assertEquals(0, prefs.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1))
    }

    @Test
    fun `togglePreset TEMP_DISABLE when inactive returns Error`() {
        val preset = makePreset(action = PresetAction.TEMP_DISABLE)
        val result = handler.togglePreset(preset, blockerLists)

        assertTrue(result is TriggerResult.Error)
        assertEquals(R.string.toast_no_active_focus, (result as TriggerResult.Error).messageResId)
    }

    // ── handleNfcTag ──

    @Test
    fun `handleNfcTag with matching unbinding talisman returns DispelSchedule`() {
        val schedule = Schedule(
            id = "sched-1", name = "Test", blockerNames = listOf("TestBlocker"),
            days = setOf(DayOfWeek.MONDAY), startTime = "09:00", endTime = "17:00",
            unbindingTalismanId = "tag-1"
        )
        val result = handler.handleNfcTag("tag-1", "sched-1", listOf(schedule), emptyList(), emptyList(), blockerLists)

        assertTrue(result is NfcResult.DispelSchedule)
        assertEquals(R.string.toast_ritual_dispelled, (result as NfcResult.DispelSchedule).messageResId)
    }

    @Test
    fun `handleNfcTag with wrong talisman for schedule returns Toast`() {
        val schedule = Schedule(
            id = "sched-1", name = "Test", blockerNames = listOf("TestBlocker"),
            days = setOf(DayOfWeek.MONDAY), startTime = "09:00", endTime = "17:00",
            unbindingTalismanId = "tag-other"
        )
        val result = handler.handleNfcTag("tag-1", "sched-1", listOf(schedule), emptyList(), emptyList(), blockerLists)

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_wrong_talisman, (result as NfcResult.Toast).messageResId)
    }

    @Test
    fun `handleNfcTag with bound preset returns PresetToggled`() {
        val preset = makePreset(id = "abc1", talismanId = "tag-1")
        val result = handler.handleNfcTag("tag-1", null, emptyList(), listOf(preset), emptyList(), blockerLists)

        assertTrue(result is NfcResult.PresetToggled)
    }

    @Test
    fun `handleNfcTag with named tag but no preset returns ToggleFocusTag`() {
        val namedTag = NamedTag("tag-1", "My Tag")
        val result = handler.handleNfcTag("tag-1", null, emptyList(), emptyList(), listOf(namedTag), blockerLists)

        assertTrue(result is NfcResult.ToggleFocusTag)
    }

    @Test
    fun `handleNfcTag with unknown tag returns UnknownTag`() {
        val result = handler.handleNfcTag("tag-unknown", null, emptyList(), emptyList(), emptyList(), blockerLists)

        assertTrue(result is NfcResult.UnknownTag)
    }

    @Test
    fun `handleNfcTag skips schedule check when activeScheduleId is null`() {
        val preset = makePreset(id = "abc1", talismanId = "tag-1")
        val result = handler.handleNfcTag("tag-1", null, emptyList(), listOf(preset), emptyList(), blockerLists)

        assertTrue(result is NfcResult.PresetToggled)
    }

    @Test
    fun `handleNfcTag schedule with null unbindingTalismanId falls through`() {
        val schedule = Schedule(
            id = "sched-1", name = "Test", blockerNames = listOf("TestBlocker"),
            days = setOf(DayOfWeek.MONDAY), startTime = "09:00", endTime = "17:00",
            unbindingTalismanId = null
        )
        val preset = makePreset(id = "abc1", talismanId = "tag-1")
        val result = handler.handleNfcTag("tag-1", "sched-1", listOf(schedule), listOf(preset), emptyList(), blockerLists)

        assertTrue(result is NfcResult.PresetToggled)
    }

    // ── resolveDeepLink ──

    @Test
    fun `resolveDeepLink with preset host returns matching preset`() {
        val preset = makePreset(id = "abc123")
        val uri = mockUri("focuspocus", "preset", listOf("abc123"))

        val result = handler.resolveDeepLink(uri, listOf(preset), emptyList())

        assertEquals(preset, result)
    }

    @Test
    fun `resolveDeepLink with talisman host returns bound preset`() {
        val talisman = NamedTag("aaa111", "MyTag")
        val preset = makePreset(id = "abc1", talismanId = "aaa111")
        val uri = mockUri("focuspocus", "talisman", listOf("aaa111"))

        val result = handler.resolveDeepLink(uri, listOf(preset), listOf(talisman))

        assertEquals(preset, result)
    }

    @Test
    fun `resolveDeepLink with wrong scheme returns null`() {
        val uri = mockUri("https", "preset", listOf("abc123"))
        val result = handler.resolveDeepLink(uri, emptyList(), emptyList())
        assertNull(result)
    }

    @Test
    fun `resolveDeepLink with unknown host returns null`() {
        val uri = mockUri("focuspocus", "unknown", listOf("abc123"))
        val result = handler.resolveDeepLink(uri, emptyList(), emptyList())
        assertNull(result)
    }

    @Test
    fun `resolveDeepLink with invalid ID returns null`() {
        val uri = mockUri("focuspocus", "preset", listOf("INVALID!@#"))
        val result = handler.resolveDeepLink(uri, emptyList(), emptyList())
        assertNull(result)
    }

    @Test
    fun `resolveDeepLink with no path segments returns null`() {
        val uri = mockUri("focuspocus", "preset", emptyList())
        val result = handler.resolveDeepLink(uri, emptyList(), emptyList())
        assertNull(result)
    }
}
