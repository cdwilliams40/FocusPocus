package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNotificationListenerServiceTest {

    @Test
    fun `shouldBlockApp returns true when app is in blacklist`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.BLACKLIST,
            apps = setOf("com.example.app")
        )
        assertTrue(FocusNotificationListenerService.shouldBlockApp("com.example.app", blocker))
    }

    @Test
    fun `shouldBlockApp returns false when app is not in blacklist`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.BLACKLIST,
            apps = setOf("com.example.app")
        )
        assertFalse(FocusNotificationListenerService.shouldBlockApp("com.other.app", blocker))
    }

    @Test
    fun `shouldBlockApp returns false when app is in whitelist`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.WHITELIST,
            apps = setOf("com.example.app")
        )
        assertFalse(FocusNotificationListenerService.shouldBlockApp("com.example.app", blocker))
    }

    @Test
    fun `shouldBlockApp returns true when app is not in whitelist`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.WHITELIST,
            apps = setOf("com.example.app")
        )
        assertTrue(FocusNotificationListenerService.shouldBlockApp("com.other.app", blocker))
    }

    @Test
    fun `shouldBlockApp handles empty blacklist correctly`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.BLACKLIST,
            apps = emptySet()
        )
        assertFalse(FocusNotificationListenerService.shouldBlockApp("com.example.app", blocker))
    }

    @Test
    fun `shouldBlockApp handles empty whitelist correctly`() {
        val blocker = Blocker(
            name = "Test Blocker",
            mode = BlockerMode.WHITELIST,
            apps = emptySet()
        )
        assertTrue(FocusNotificationListenerService.shouldBlockApp("com.example.app", blocker))
    }
}
