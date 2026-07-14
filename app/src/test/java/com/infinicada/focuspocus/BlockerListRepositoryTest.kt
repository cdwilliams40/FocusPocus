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

    /** Writes [blockers] straight to prefs, bypassing the repo (simulates existing state). */
    private fun storeDirectly(blockers: List<Blocker>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, blockers)
    }

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
        assertTrue(blockers[0].effectiveApps.contains("com.google.android.youtube"))
    }

    @Test
    fun `saveBlocker adds new blocker`() {
        storeDirectly(emptyList())
        val result = repo.saveBlocker(makeBlocker())

        assertTrue(result)
        assertEquals(listOf("TestBlocker"), repo.getBlockers().map { it.name })
    }

    @Test
    fun `saveBlocker updates existing blocker by name`() {
        storeDirectly(emptyList())
        repo.saveBlocker(makeBlocker(apps = setOf("com.old")))

        val result = repo.saveBlocker(makeBlocker(apps = setOf("com.new")))

        assertTrue(result)
        val stored = repo.getBlockers()
        assertEquals(1, stored.size)
        assertEquals(setOf("com.new"), stored[0].effectiveApps)
    }

    @Test
    fun `saveBlocker rejects new blocker at capacity`() {
        storeDirectly((1..Constants.MAX_BLOCKERS).map { makeBlocker(name = "Blocker$it") })

        val result = repo.saveBlocker(makeBlocker(name = "NewBlocker"))

        assertFalse(result)
    }

    @Test
    fun `saveBlocker caps apps at MAX_APPS_PER_BLOCKER`() {
        storeDirectly(emptyList())
        val bigAppSet = (1..600).map { "com.app.$it" }.toSet()

        repo.saveBlocker(makeBlocker(apps = bigAppSet))
        val saved = repo.getBlockers()[0]

        assertEquals(Constants.MAX_APPS_PER_BLOCKER, saved.effectiveApps.size)
    }

    @Test
    fun `saveBlocker caps websites at MAX_WEBSITES_PER_BLOCKER`() {
        storeDirectly(emptyList())
        val bigWebsiteList = (1..200).map { "site$it.com" }

        repo.saveBlocker(makeBlocker(websites = bigWebsiteList))
        val saved = repo.getBlockers()[0]

        assertEquals(Constants.MAX_WEBSITES_PER_BLOCKER, saved.effectiveWebsites.size)
    }

    @Test
    fun `deleteBlocker removes by name`() {
        storeDirectly(listOf(makeBlocker()))
        val remaining = repo.deleteBlocker(makeBlocker())

        assertEquals(0, remaining.size)
    }

    @Test
    fun `saveBlocker preserves blockers written concurrently by another writer`() {
        // UI reads its snapshot...
        storeDirectly(listOf(makeBlocker(name = "A", apps = setOf("com.a"))))
        val uiSnapshotOfA = repo.getBlockers().first()

        // ...then the accessibility service auto-adds an app to another blocker.
        storeDirectly(
            listOf(
                makeBlocker(name = "A", apps = setOf("com.a")),
                makeBlocker(name = "B", apps = setOf("com.freshly.installed"))
            )
        )

        // Saving A from the stale UI snapshot must not clobber B.
        repo.saveBlocker(uiSnapshotOfA)

        val names = repo.getBlockers().map { it.name }
        assertTrue("expected B to survive the save, got $names", "B" in names)
    }
}
