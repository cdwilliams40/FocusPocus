package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The health surface's job is to be believed. These pin the one rule that makes
 * it believable: it must not report a permission as broken that the current
 * enforcement mode does not use.
 */
class ProtectionHealthStatusTest {

    private fun status(
        accessibility: Boolean = true,
        usage: Boolean = true,
        notifications: Boolean = true,
        battery: Boolean = true,
        mode: EnforcementMode = EnforcementMode.ACCESSIBILITY
    ) = ProtectionHealth.Status(
        accessibilityEnabled = accessibility,
        usageAccessGranted = usage,
        notificationsEnabled = notifications,
        batteryUnrestricted = battery,
        enforcementMode = mode
    )

    @Test
    fun `accessibility mode is unhealthy without the accessibility service`() {
        assertTrue(status().allHealthy)
        assertFalse(status(accessibility = false).allHealthy)
        assertTrue(status().accessibilityRequired)
    }

    @Test
    fun `polling mode does not fault a permission it never uses`() {
        val polling = status(accessibility = false, mode = EnforcementMode.POLLING)
        assertFalse(polling.accessibilityRequired)
        assertTrue(polling.allHealthy)
    }

    @Test
    fun `usage access is required in both modes`() {
        assertFalse(status(usage = false).allHealthy)
        assertFalse(status(usage = false, mode = EnforcementMode.POLLING).allHealthy)
    }

    @Test
    fun `notifications and battery still matter in polling mode`() {
        // The poller's own ongoing notification is what keeps it alive, so a
        // blocked notification channel is worse here, not better.
        assertFalse(status(notifications = false, mode = EnforcementMode.POLLING).allHealthy)
        assertFalse(status(battery = false, mode = EnforcementMode.POLLING).allHealthy)
    }
}
