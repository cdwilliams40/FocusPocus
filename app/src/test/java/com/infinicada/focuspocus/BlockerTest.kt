package com.infinicada.focuspocus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerTest {

    @Test
    fun shouldBlock_blacklist_containsPackage_returnsTrue() {
        val apps = setOf("com.example.app1", "com.example.app2")
        val blocker = Blocker("Test", BlockerMode.BLACKLIST, apps)

        assertTrue(blocker.shouldBlock("com.example.app1"))
    }

    @Test
    fun shouldBlock_blacklist_doesNotContainPackage_returnsFalse() {
        val apps = setOf("com.example.app1", "com.example.app2")
        val blocker = Blocker("Test", BlockerMode.BLACKLIST, apps)

        assertFalse(blocker.shouldBlock("com.example.app3"))
    }

    @Test
    fun shouldBlock_whitelist_containsPackage_returnsFalse() {
        val apps = setOf("com.example.app1", "com.example.app2")
        val blocker = Blocker("Test", BlockerMode.WHITELIST, apps)

        assertFalse(blocker.shouldBlock("com.example.app1"))
    }

    @Test
    fun shouldBlock_whitelist_doesNotContainPackage_returnsTrue() {
        val apps = setOf("com.example.app1", "com.example.app2")
        val blocker = Blocker("Test", BlockerMode.WHITELIST, apps)

        assertTrue(blocker.shouldBlock("com.example.app3"))
    }
}
