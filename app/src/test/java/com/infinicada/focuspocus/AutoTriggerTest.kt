package com.infinicada.focuspocus

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AutoTriggerTest {

    private val gson = Gson()

    @Test
    fun `serialization round trip preserves all fields`() {
        val original = AutoTrigger(
            id = "test-id",
            name = "Test Trigger",
            type = TriggerType.WIFI,
            identifier = "test-ssid",
            deviceName = "Test Device",
            presetId = "preset-123",
            enabled = false
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, AutoTrigger::class.java)

        assertEquals(original, deserialized)
    }

    @Test
    fun `default values are set correctly`() {
        // Create trigger with only required fields
        val trigger = AutoTrigger(
            name = "Default Trigger",
            type = TriggerType.BLUETOOTH,
            identifier = "device-mac",
            presetId = "preset-456"
        )

        assertNotNull(trigger.id)
        assertTrue(trigger.id.isNotEmpty())
        // Verify it looks like a UUID
        try {
            UUID.fromString(trigger.id)
        } catch (e: IllegalArgumentException) {
            throw AssertionError("ID is not a valid UUID: ${trigger.id}")
        }

        assertNull(trigger.deviceName)
        assertTrue(trigger.enabled)
    }

    @Test
    fun `serialization handles null deviceName`() {
        val original = AutoTrigger(
            name = "Null Device Trigger",
            type = TriggerType.WIFI,
            identifier = "ssid",
            presetId = "preset-789",
            deviceName = null
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, AutoTrigger::class.java)

        assertEquals(original, deserialized)
        assertNull(deserialized.deviceName)
    }

    @Test
    fun `serialization handles non-null deviceName`() {
        val original = AutoTrigger(
            name = "Device Trigger",
            type = TriggerType.WIFI,
            identifier = "ssid",
            presetId = "preset-789",
            deviceName = "My Phone"
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, AutoTrigger::class.java)

        assertEquals(original, deserialized)
        assertEquals("My Phone", deserialized.deviceName)
    }
}
