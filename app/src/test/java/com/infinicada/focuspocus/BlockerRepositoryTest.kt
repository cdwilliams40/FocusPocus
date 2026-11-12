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

    // ── sessionBlockerFor ──

    private fun runSession(vararg active: String) {
        fakePrefs.putString(
            Constants.PrefsKeys.BLOCKER_LISTS,
            gson.toJson(
                listOf(
                    Blocker("Social", BlockerMode.BLACKLIST, setOf("com.social")),
                    Blocker("Shield", BlockerMode.WHITELIST, setOf("com.maps")),
                    Blocker("Games", BlockerMode.BLACKLIST, setOf("com.game"))
                )
            )
        )
        SessionManager.startSession(fakePrefs, active.toList())
    }

    @Test
    fun `sessionBlockerFor names the active enchantment that blocks the app`() {
        runSession("Social")
        assertEquals("Social", BlockerRepository.sessionBlockerFor(fakePrefs, "com.social") { true }?.name)
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.other") { true })
        // "Games" exists but isn't part of this session.
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.game") { true })
    }

    @Test
    fun `sessionBlockerFor lets a whitelist block only what the picker offers`() {
        runSession("Shield")
        assertEquals("Shield", BlockerRepository.sessionBlockerFor(fakePrefs, "com.social") { true }?.name)
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.maps") { true })
        // A system surface the picker never listed can't be whitelisted, so it isn't blocked.
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.android.permissioncontroller") { false })
    }

    @Test
    fun `sessionBlockerFor blocks nothing on a break or without a session`() {
        runSession("Social")
        fakePrefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.social") { true })

        fakePrefs.putBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)
        fakePrefs.putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false)
        assertNull(BlockerRepository.sessionBlockerFor(fakePrefs, "com.social") { true })
    }
}
