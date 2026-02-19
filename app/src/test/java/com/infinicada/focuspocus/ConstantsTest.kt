package com.infinicada.focuspocus

import org.junit.Test
import org.junit.Assert.assertEquals

class ConstantsTest {
    @Test
    fun testWifiTriggerConstants() {
        assertEquals("wifi_trigger_channel", Constants.WIFI_TRIGGER_CHANNEL_ID)
        assertEquals(9001, Constants.WIFI_TRIGGER_NOTIFICATION_ID)
    }
}
