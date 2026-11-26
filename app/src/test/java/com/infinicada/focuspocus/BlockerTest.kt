package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
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

    @Test
    fun `sanitize keeps well-formed blockers`() {
        val valid = listOf(
            Blocker("A", BlockerMode.BLACKLIST, setOf("com.a")),
            Blocker("B", BlockerMode.WHITELIST, setOf("com.b"))
        )
        assertEquals(valid, Blocker.sanitize(valid))
    }

    @Test
    fun `sanitize returns empty for null input`() {
        assertEquals(emptyList<Blocker>(), Blocker.sanitize(null))
    }

    @Test
    fun `lenient decode drops a record missing mode while preserving valid neighbors`() {
        val json = """[{"name":"Broken","apps":["com.x"]},{"name":"Good","mode":"BLACKLIST","apps":["com.y"]}]"""
        val sanitized = Blocker.sanitize(AppJson.decodeListLenient(json))

        assertEquals(listOf("Good"), sanitized.map { it.name })
        assertTrue(sanitized.single().shouldBlock("com.y"))
    }

    @Test
    fun `lenient decode drops a record with an unknown mode value`() {
        val json = """[{"name":"Weird","mode":"PURPLE","apps":["com.z"]}]"""
        val parsed = AppJson.decodeListLenient<Blocker>(json)

        assertEquals(emptyList<Blocker>(), Blocker.sanitize(parsed))
    }
}
