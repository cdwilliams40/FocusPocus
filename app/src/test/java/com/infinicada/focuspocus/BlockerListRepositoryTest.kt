package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.data.BlockerListRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockerListRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: BlockerListRepository
    private val gson = Gson()

    private fun makeBlocker(
        name: String = "TestBlocker",
        apps: Set<String> = setOf("com.test"),
        websites: List<String>? = null
    ) = Blocker(name, BlockerMode.BLACKLIST, apps, websites)

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        repo = BlockerListRepository(prefs, gson)
    }

    @Test
    fun `getBlockers returns default when nothing stored`() {
        val blockers = repo.getBlockers()
        assertEquals(1, blockers.size)
        assertEquals("Default", blockers[0].name)
        assertTrue(blockers[0].apps.contains("com.google.android.youtube"))
    }

    @Test
    fun `saveBlocker adds new blocker`() {
        val blocker = makeBlocker()
        val result = repo.saveBlocker(blocker, emptyList())

        assertTrue(result)
    }

    @Test
    fun `saveBlocker updates existing blocker by name`() {
        val original = makeBlocker(apps = setOf("com.old"))
        repo.saveBlocker(original, emptyList())

        val updated = makeBlocker(apps = setOf("com.new"))
        val stored = repo.getBlockers()
        val result = repo.saveBlocker(updated, stored)

        assertTrue(result)
    }

    @Test
    fun `saveBlocker rejects new blocker at capacity`() {
        val fullList = (1..Constants.MAX_BLOCKERS).map { makeBlocker(name = "Blocker$it") }
        val newBlocker = makeBlocker(name = "NewBlocker")

        val result = repo.saveBlocker(newBlocker, fullList)

        assertFalse(result)
    }

    @Test
    fun `saveBlocker caps apps at MAX_APPS_PER_BLOCKER`() {
        val bigAppSet = (1..600).map { "com.app.$it" }.toSet()
        val blocker = makeBlocker(apps = bigAppSet)

        repo.saveBlocker(blocker, emptyList())
        val saved = repo.getBlockers()[0]

        assertEquals(Constants.MAX_APPS_PER_BLOCKER, saved.apps.size)
    }

    @Test
    fun `saveBlocker caps websites at MAX_WEBSITES_PER_BLOCKER`() {
        val bigWebsiteList = (1..200).map { "site$it.com" }
        val blocker = makeBlocker(websites = bigWebsiteList)

        repo.saveBlocker(blocker, emptyList())
        val saved = repo.getBlockers()[0]

        assertEquals(Constants.MAX_WEBSITES_PER_BLOCKER, saved.websites?.size)
    }

    @Test
    fun `deleteBlocker removes by name`() {
        val blocker = makeBlocker()
        val remaining = repo.deleteBlocker(blocker, listOf(blocker))

        assertEquals(0, remaining.size)
    }
}
