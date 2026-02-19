package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.google.gson.Gson

class BlockerRepositoryTest {
    private val fakePrefs = FakeSharedPreferences()
    private val gson = Gson()

    @Test
    fun testGetBlockers_empty() {
        fakePrefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, null)
        val blockers = BlockerRepository.getBlockers(fakePrefs)
        assertEquals(0, blockers.size)
    }

    @Test
    fun testGetBlockers_valid() {
        val blockers = listOf(
            Blocker("Test1", BlockerMode.BLACKLIST, setOf("com.pkg1")),
            Blocker("Test2", BlockerMode.WHITELIST, setOf("com.pkg2"))
        )
        fakePrefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(blockers))

        val result = BlockerRepository.getBlockers(fakePrefs)
        assertEquals(2, result.size)
        assertEquals("Test1", result[0].name)
        assertEquals("Test2", result[1].name)
    }

    @Test
    fun testGetBlockers_invalidJson() {
        fakePrefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, "{invalid json}")
        val result = BlockerRepository.getBlockers(fakePrefs)
        assertEquals(0, result.size)
    }

    @Test
    fun testGetBlocker_found() {
        val blockers = listOf(
            Blocker("Test1", BlockerMode.BLACKLIST, setOf("com.pkg1"))
        )
        fakePrefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(blockers))

        val result = BlockerRepository.getBlocker(fakePrefs, "Test1")
        assertNotNull(result)
        assertEquals("Test1", result?.name)
    }

    @Test
    fun testGetBlocker_notFound() {
        val blockers = listOf(
            Blocker("Test1", BlockerMode.BLACKLIST, setOf("com.pkg1"))
        )
        fakePrefs.putString(Constants.PrefsKeys.BLOCKER_LISTS, gson.toJson(blockers))

        val result = BlockerRepository.getBlocker(fakePrefs, "Test2")
        assertNull(result)
    }
}
