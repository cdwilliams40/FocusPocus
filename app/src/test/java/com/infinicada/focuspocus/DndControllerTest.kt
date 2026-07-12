package com.infinicada.focuspocus

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class DndControllerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockNotificationManager: NotificationManager

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var mockLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockLog = Mockito.mockStatic(Log::class.java)
        mockLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockLog.`when`<Int> { Log.e(anyString(), anyString(), Mockito.any()) }.thenReturn(0)

        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager)
        `when`(mockContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), Mockito.anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    @Test
    fun `updateDndState does nothing when permission not granted`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(false)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager, never()).setInterruptionFilter(anyInt())
    }

    @Test
    fun `updateDndState enables priority mode when manual focus active and mute enabled`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(true)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(false)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    @Test
    fun `updateDndState enables priority mode when scheduled focus active and mute enabled`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(false)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn("schedule_1")
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(false)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    @Test
    fun `updateDndState enables priority mode when talisman focus tag active and mute enabled`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(false)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null)).thenReturn("tag-1")
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(false)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    @Test
    fun `updateDndState restores previous filter when focus ends and app had enabled DND`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(false)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("dndPreviousFilter", NotificationManager.INTERRUPTION_FILTER_ALL))
            .thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun `updateDndState does not touch DND when focus ends and app had not enabled DND`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(false)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(false)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager, never()).setInterruptionFilter(anyInt())
    }

    @Test
    fun `updateDndState restores previous filter when mute disabled and app had enabled DND`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(true)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("dndPreviousFilter", NotificationManager.INTERRUPTION_FILTER_ALL))
            .thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun `updateDndState restores previous filter when on break and app had enabled DND`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(true)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("dndPreviousFilter", NotificationManager.INTERRUPTION_FILTER_ALL))
            .thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)

        DndController.updateDndState(mockContext)

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun `updateDndState handles SecurityException gracefully`() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)).thenReturn(true)
        `when`(mockSharedPreferences.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)).thenReturn(null)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.MUTE_BLOCKED_NOTIFICATIONS, true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("dndEnabledByApp", false)).thenReturn(false)

        doThrow(SecurityException("Test exception")).`when`(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        try {
            DndController.updateDndState(mockContext)
        } catch (e: Exception) {
            // Should not crash
            throw e
        }

        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }
}
