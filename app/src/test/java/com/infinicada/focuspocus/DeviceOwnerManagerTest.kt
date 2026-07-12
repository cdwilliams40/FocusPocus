package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerManagerTest {

    private val launchable = setOf(
        "com.example.social",
        "com.example.games",
        "com.example.notes",
        "com.example.mail"
    )

    @Test
    fun computeBlockedPackages_noActiveBlockers_returnsEmpty() {
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = emptyList(),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun computeBlockedPackages_blacklist_suspendsOnlyListedApps() {
        val blocker = Blocker("Distractions", BlockerMode.BLACKLIST, setOf("com.example.social", "com.example.games"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blocker),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social", "com.example.games"), result)
    }

    @Test
    fun computeBlockedPackages_blacklist_ignoresUninstalledApps() {
        val blocker = Blocker("Distractions", BlockerMode.BLACKLIST, setOf("com.example.social", "com.example.gone"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blocker),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social"), result)
    }

    @Test
    fun computeBlockedPackages_whitelist_suspendsEverythingElse() {
        val blocker = Blocker("Work Only", BlockerMode.WHITELIST, setOf("com.example.notes", "com.example.mail"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blocker),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social", "com.example.games"), result)
    }

    @Test
    fun computeBlockedPackages_exemptPackagesNeverSuspended() {
        val blocker = Blocker("Work Only", BlockerMode.WHITELIST, setOf("com.example.notes"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blocker),
            launchablePackages = launchable + "com.infinicada.focuspocus" + "com.android.launcher3",
            exemptPackages = setOf("com.infinicada.focuspocus", "com.android.launcher3")
        )
        assertEquals(setOf("com.example.social", "com.example.games", "com.example.mail"), result)
    }

    @Test
    fun computeBlockedPackages_multipleBlockers_unionOfBlocks() {
        val blacklist = Blocker("Social", BlockerMode.BLACKLIST, setOf("com.example.social"))
        val otherBlacklist = Blocker("Games", BlockerMode.BLACKLIST, setOf("com.example.games"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blacklist, otherBlacklist),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social", "com.example.games"), result)
    }

    @Test
    fun computeBlockedPackages_blacklistPlusWhitelist_anyBlockerBlocking_suspends() {
        // Matches the accessibility service: an app is blocked if ANY active blocker blocks it.
        val blacklist = Blocker("Social", BlockerMode.BLACKLIST, setOf("com.example.notes"))
        val whitelist = Blocker("Work Only", BlockerMode.WHITELIST, setOf("com.example.notes", "com.example.mail"))
        val result = DeviceOwnerManager.computeBlockedPackages(
            activeBlockers = listOf(blacklist, whitelist),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social", "com.example.games", "com.example.notes"), result)
    }
}
