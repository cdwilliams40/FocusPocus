package com.infinicada.focuspocus

import com.google.gson.Gson
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
    fun `sanitize drops a Gson record left with a null mode`() {
        // Gson instantiates via Unsafe, so a record whose "mode" is missing (or an
        // obfuscated enum name from a v1.4 build) deserializes with a null mode
        // despite the non-null Kotlin type — and shouldBlock's when(mode) would then
        // throw. The valid blocker beside it must survive.
        val json = """[{"name":"Broken","apps":["com.x"]},{"name":"Good","mode":"BLACKLIST","apps":["com.y"]}]"""
        val parsed = Gson().fromJson(json, Array<Blocker>::class.java).toList()

        val sanitized = Blocker.sanitize(parsed)

        assertEquals(listOf("Good"), sanitized.map { it.name })
        // The survivor is fully usable — no throw from shouldBlock.
        assertTrue(sanitized.single().shouldBlock("com.y"))
    }

    @Test
    fun `sanitize drops a record with an unknown mode value`() {
        // An unknown enum string also deserializes to a null mode in Gson.
        val json = """[{"name":"Weird","mode":"PURPLE","apps":["com.z"]}]"""
        val parsed = Gson().fromJson(json, Array<Blocker>::class.java).toList()

        assertEquals(emptyList<Blocker>(), Blocker.sanitize(parsed))
    }
}
