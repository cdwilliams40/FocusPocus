package com.infinicada.focuspocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ── computePactSuspendedPackages ──

    @Test
    fun computePactSuspendedPackages_gatedAppsSuspendedByDefault() {
        val result = DeviceOwnerManager.computePactSuspendedPackages(
            pactGated = setOf("com.example.social", "com.example.games"),
            allowedPackages = emptySet(),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social", "com.example.games"), result)
    }

    @Test
    fun computePactSuspendedPackages_activeAllowanceOrUnlockEscapesSuspension() {
        val result = DeviceOwnerManager.computePactSuspendedPackages(
            pactGated = setOf("com.example.social", "com.example.games", "com.example.mail"),
            // Union of active pact allowances and satisfied conditional unlocks.
            allowedPackages = setOf("com.example.games", "com.example.mail"),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertEquals(setOf("com.example.social"), result)
    }

    @Test
    fun computePactSuspendedPackages_ignoresUninstalledAndExemptApps() {
        val result = DeviceOwnerManager.computePactSuspendedPackages(
            pactGated = setOf("com.example.social", "com.example.gone", "com.example.notes"),
            allowedPackages = emptySet(),
            launchablePackages = launchable,
            exemptPackages = setOf("com.example.notes")
        )
        assertEquals(setOf("com.example.social"), result)
    }

    @Test
    fun computePactSuspendedPackages_noGatedApps_returnsEmpty() {
        val result = DeviceOwnerManager.computePactSuspendedPackages(
            pactGated = emptySet(),
            allowedPackages = emptySet(),
            launchablePackages = launchable,
            exemptPackages = emptySet()
        )
        assertTrue(result.isEmpty())
    }

    // ── isRemovalUnlocked (24-hour cooling-off) ──

    @Test
    fun isRemovalUnlocked_noPendingRequest_stayslocked() {
        assertFalse(DeviceOwnerManager.isRemovalUnlocked(0L, now = 1_000_000L))
        assertFalse(DeviceOwnerManager.isRemovalUnlocked(-5L, now = 1_000_000L))
    }

    @Test
    fun isRemovalUnlocked_unlocksExactlyAfterTheCoolingOffPeriod() {
        val requestedAt = 1_000_000_000_000L
        val unlockAt = requestedAt + DeviceOwnerManager.REMOVAL_COOLDOWN_MS

        assertFalse(DeviceOwnerManager.isRemovalUnlocked(requestedAt, now = requestedAt))
        assertFalse(DeviceOwnerManager.isRemovalUnlocked(requestedAt, now = unlockAt - 1))
        assertTrue(DeviceOwnerManager.isRemovalUnlocked(requestedAt, now = unlockAt))
        assertTrue(DeviceOwnerManager.isRemovalUnlocked(requestedAt, now = unlockAt + 1))
    }
}
