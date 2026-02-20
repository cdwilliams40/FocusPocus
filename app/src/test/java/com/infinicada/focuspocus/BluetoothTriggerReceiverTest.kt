package com.infinicada.focuspocus

import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.util.UUID

@RunWith(MockitoJUnitRunner::class)
class BluetoothTriggerReceiverTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockIntent: Intent

    @Mock
    private lateinit var mockDevice: BluetoothDevice

    @Mock
    private lateinit var mockNotificationManager: NotificationManager

    private lateinit var receiver: BluetoothTriggerReceiver
    private lateinit var sharedPreferences: FakeSharedPreferences
    private lateinit var mockLog: MockedStatic<Log>
    private val gson = Gson()

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java)
        receiver = BluetoothTriggerReceiver()
        sharedPreferences = FakeSharedPreferences()

        // Mock Context to return our FakeSharedPreferences
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)

        // Mock NotificationManager
        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager)
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)

        // Mock Intent to return our MockDevice
        // Note: In unit tests on JVM, SDK_INT is 0, so the DEPRECATED path is taken.
        `when`(mockIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)).thenReturn(mockDevice)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    @Test
    fun testOnReceive_connected_activatesFocus() {
        // Setup data
        val deviceAddress = "00:11:22:33:44:55"
        val triggerId = UUID.randomUUID().toString()
        val presetId = UUID.randomUUID().toString()
        val blockerName = "MyBlocker"

        val blocker = Blocker(
            name = blockerName,
            mode = BlockerMode.BLACKLIST,
            apps = setOf("com.example.app")
        )
        val preset = FocusPreset(
            id = presetId,
            name = "MyPreset",
            blockerName = blockerName,
            durationMinutes = 30,
            breaksEnabled = true
        )
        val trigger = AutoTrigger(
            id = triggerId,
            name = "MyTrigger",
            type = TriggerType.BLUETOOTH,
            identifier = deviceAddress,
            presetId = presetId,
            enabled = true
        )

        // Populate SharedPreferences
        sharedPreferences.edit()
            .putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(listOf(blocker)))
            .putString(Constants.PrefsKeys.FOCUS_PRESETS, gson.toJson(listOf(preset)))
            .putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(listOf(trigger)))
            .apply()

        // Mock Intent and Device
        `when`(mockIntent.action).thenReturn(BluetoothDevice.ACTION_ACL_CONNECTED)
        `when`(mockDevice.address).thenReturn(deviceAddress)

        // Execute
        receiver.onReceive(mockContext, mockIntent)

        // Verify
        assertTrue("Manual focus mode should be active",
            sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals("Active blocker should be set",
            blockerName, sharedPreferences.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null))
        assertEquals("Last BT trigger ID should be set",
            triggerId, sharedPreferences.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null))
        assertEquals("Services trigger count should be incremented",
            1, sharedPreferences.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0))

        // Verify DND enabled (priority only)
        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    @Test
    fun testOnReceive_connected_doesNothingIfManualFocusActive() {
        // Setup data
        val deviceAddress = "00:11:22:33:44:55"
        val triggerId = UUID.randomUUID().toString()
        val presetId = UUID.randomUUID().toString()

        val trigger = AutoTrigger(
            id = triggerId,
            name = "MyTrigger",
            type = TriggerType.BLUETOOTH,
            identifier = deviceAddress,
            presetId = presetId,
            enabled = true
        )

        // Populate SharedPreferences with existing manual focus
        sharedPreferences.edit()
            .putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(listOf(trigger)))
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .apply()

        // Mock Intent and Device
        `when`(mockIntent.action).thenReturn(BluetoothDevice.ACTION_ACL_CONNECTED)
        `when`(mockDevice.address).thenReturn(deviceAddress)

        // Execute
        receiver.onReceive(mockContext, mockIntent)

        // Verify state did NOT change (count not incremented, last trigger ID not set)
        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0))
        assertNotNull(sharedPreferences.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null) == null) // Should be null
    }

    @Test
    fun testOnReceive_disconnected_deactivatesFocus() {
        // Setup data
        val deviceAddress = "00:11:22:33:44:55"
        val triggerId = UUID.randomUUID().toString()
        val presetId = UUID.randomUUID().toString()

        val trigger = AutoTrigger(
            id = triggerId,
            name = "MyTrigger",
            type = TriggerType.BLUETOOTH,
            identifier = deviceAddress,
            presetId = presetId,
            enabled = true
        )

        // Populate SharedPreferences with active session triggered by this BT device
        sharedPreferences.edit()
            .putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(listOf(trigger)))
            .putString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, triggerId)
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, "SomeBlocker")
            .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis() - 60000) // 1 min ago
            .apply()

        // Mock Intent and Device
        `when`(mockIntent.action).thenReturn(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        `when`(mockDevice.address).thenReturn(deviceAddress)

        // Execute
        receiver.onReceive(mockContext, mockIntent)

        // Verify
        assertFalse("Manual focus mode should be inactive",
            sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true))
        assertEquals("Last BT trigger ID should be removed",
            null, sharedPreferences.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null))
        assertEquals("Services trigger count should be incremented",
            1, sharedPreferences.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0))

        // Verify session recorded
        val sessionsJson = sharedPreferences.getString(Constants.PrefsKeys.FOCUS_SESSIONS, null)
        assertNotNull("Focus sessions should be recorded", sessionsJson)
        assertTrue("Focus sessions should contain data", sessionsJson!!.contains("SomeBlocker"))

        // Verify DND disabled (all allowed)
        verify(mockNotificationManager).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun testOnReceive_disconnected_doesNothingIfTriggerIdMismatch() {
        // Setup data
        val deviceAddress = "00:11:22:33:44:55"
        val triggerId = UUID.randomUUID().toString()
        val otherTriggerId = UUID.randomUUID().toString()
        val presetId = UUID.randomUUID().toString()

        val trigger = AutoTrigger(
            id = triggerId,
            name = "MyTrigger",
            type = TriggerType.BLUETOOTH,
            identifier = deviceAddress,
            presetId = presetId,
            enabled = true
        )

        // Populate SharedPreferences with DIFFERENT trigger ID
        sharedPreferences.edit()
            .putString(Constants.PrefsKeys.AUTO_TRIGGERS, gson.toJson(listOf(trigger)))
            .putString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, otherTriggerId) // Mismatch
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .apply()

        // Mock Intent and Device
        `when`(mockIntent.action).thenReturn(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        `when`(mockDevice.address).thenReturn(deviceAddress)

        // Execute
        receiver.onReceive(mockContext, mockIntent)

        // Verify state unchanged
        assertTrue(sharedPreferences.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false))
        assertEquals(otherTriggerId, sharedPreferences.getString(Constants.PrefsKeys.LAST_BT_TRIGGER_ID, null))
        assertEquals(0, sharedPreferences.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0))
        verify(mockNotificationManager, never()).setInterruptionFilter(anyInt())
    }
}
