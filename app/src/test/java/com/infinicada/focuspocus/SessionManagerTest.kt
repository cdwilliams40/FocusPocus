package com.infinicada.focuspocus

import android.app.NotificationManager
import android.content.Context
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class SessionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockNotificationManager: NotificationManager

    private lateinit var fakePrefs: FakeSharedPreferences
    private val gson = Gson()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fakePrefs = FakeSharedPreferences()

        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager)
        `when`(mockContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        // Default behavior: DND permission granted
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
    }

    @Test
    fun testStopSession_clearsPrefsAndRecordsSession() {
        // Setup active session state
        val startTime = System.currentTimeMillis() - 600000 // 10 mins ago
        fakePrefs.putLong(Constants.PrefsKeys.SESSION_START_TIME, startTime)
        fakePrefs.putString(Constants.PrefsKeys.ACTIVE_BLOCKER, "TestBlocker")
        fakePrefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
        fakePrefs.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, 300)
        fakePrefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)

        SessionManager.stopSession(mockContext, fakePrefs, gson)

        // Verify session recorded (FOCUS_SESSIONS should not be empty)
        val sessionsJson = fakePrefs.getString(Constants.PrefsKeys.FOCUS_SESSIONS, "[]")
        assertFalse("Session should be recorded", sessionsJson == "[]")

        // Verify prefs cleared/reset
        assertFalse("Manual focus mode should be false", fakePrefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true))
        assertNull("Active blocker should be removed", fakePrefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null))
        assertNull("Active schedule ID should be removed", fakePrefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null))
        assertFalse("Is on break should be false", fakePrefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, true))
        assertEquals("Focus time remaining should be 0", 0, fakePrefs.getInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, -1))
        assertEquals("Break time remaining should be 0", 0, fakePrefs.getInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, -1))
        assertEquals("Breaks used should be 0", 0, fakePrefs.getInt(Constants.PrefsKeys.BREAKS_USED_THIS_SESSION, -1))
        assertEquals("Session start time should be removed", 0L, fakePrefs.getLong(Constants.PrefsKeys.SESSION_START_TIME, 0L))

        // Verify DND update called
        // Since session is stopped, manualFocusMode is false.
        // DndController logic: focusModeActive = false. shouldEnableDnd = false.
        // Expect: notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun testStopSession_clearsAutoTriggerIds() {
        fakePrefs.putString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, "bt_123")
        fakePrefs.putString(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID, "wifi_456")

        SessionManager.stopSession(mockContext, fakePrefs, gson)

        assertNull("Bluetooth trigger ID should be removed", fakePrefs.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null))
        assertNull("Wifi trigger ID should be removed", fakePrefs.getString(Constants.PrefsKeys.LAST_WIFI_TRIGGER_ID, null))
    }
}
