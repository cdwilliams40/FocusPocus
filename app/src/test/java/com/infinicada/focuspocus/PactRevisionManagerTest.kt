package com.infinicada.focuspocus

import com.google.gson.Gson
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.PactRevisionManager
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PactRevisionManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var gson: Gson
    private lateinit var manager: PactRevisionManager
    private lateinit var pactManager: PactManager

    private val pkg = "com.example.social"
    private val t0 = 1_000_000_000_000L
    private val delay = PactRevisionManager.REVISION_DELAY_MS

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        gson = Gson()
        manager = PactRevisionManager(prefs, gson)
        pactManager = PactManager(prefs, gson)
    }

    private fun pact(packageName: String = pkg, maxMinutes: Int = 15) = AppTimeLimit(
        packageName = packageName,
        dailyLimitMinutes = 0,
        pactModeEnabled = true,
        pactMaxMinutes = maxMinutes
    )

    private fun storedConfigs(): Map<String, AppTimeLimit> =
        AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)

    private fun seedConfig(config: AppTimeLimit) {
        AppTimeLimitManager.saveTimeLimitConfigs(
            prefs, gson, storedConfigs() + (config.packageName to config)
        )
    }

    @Test
    fun `queueing a revision leaves the enforced store untouched`() {
        seedConfig(pact(maxMinutes = 15))
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)

        assertEquals(15, storedConfigs()[pkg]?.pactMaxMinutes)
        val revision = manager.revisionForApp(pkg)!!
        assertEquals(t0, revision.requestedAtMillis)
        assertEquals(t0 + delay, revision.appliesAtMillis)
        assertFalse(revision.isRemoval)
    }

    @Test
    fun `applyDueRevisions is a no-op before the 24 h have passed`() {
        seedConfig(pact(maxMinutes = 15))
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)

        assertFalse(manager.applyDueRevisions(now = t0 + delay - 1))
        assertEquals(15, storedConfigs()[pkg]?.pactMaxMinutes)
        assertEquals(1, manager.getRevisions().size)
    }

    @Test
    fun `a due config change is written through and consumed`() {
        seedConfig(pact(maxMinutes = 15))
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)

        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertEquals(30, storedConfigs()[pkg]?.pactMaxMinutes)
        assertTrue(manager.getRevisions().isEmpty())
        // Nothing left to apply.
        assertFalse(manager.applyDueRevisions(now = t0 + delay))
    }

    @Test
    fun `a due removal deletes the config`() {
        seedConfig(pact())
        manager.queueAppRevision(pkg, null, now = t0)
        assertTrue(manager.revisionForApp(pkg)!!.isRemoval)

        // Still enforced until due.
        assertEquals(true, storedConfigs()[pkg]?.pactModeEnabled)
        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertNull(storedConfigs()[pkg])
    }

    @Test
    fun `re-queueing replaces the previous request and restarts the clock`() {
        seedConfig(pact(maxMinutes = 15))
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)
        manager.queueAppRevision(pkg, pact(maxMinutes = 5), now = t0 + 60_000)

        assertEquals(1, manager.getRevisions().size)
        val revision = manager.revisionForApp(pkg)!!
        assertEquals(5, revision.newConfig?.pactMaxMinutes)
        assertEquals(t0 + 60_000 + delay, revision.appliesAtMillis)

        // The first request's due time no longer applies anything.
        assertFalse(manager.applyDueRevisions(now = t0 + delay))
        assertTrue(manager.applyDueRevisions(now = t0 + 60_000 + delay))
        assertEquals(5, storedConfigs()[pkg]?.pactMaxMinutes)
    }

    @Test
    fun `cancelling keeps the current terms`() {
        seedConfig(pact(maxMinutes = 15))
        manager.queueAppRevision(pkg, null, now = t0)
        manager.cancelAppRevision(pkg)

        assertNull(manager.revisionForApp(pkg))
        assertFalse(manager.applyDueRevisions(now = t0 + delay))
        assertEquals(15, storedConfigs()[pkg]?.pactMaxMinutes)
    }

    @Test
    fun `a due circle change is written through to the group store`() {
        pactManager.saveGroup(PactGroup(blockerName = "Doomscroll", pactMaxMinutes = 15))
        manager.queueCircleRevision(
            "Doomscroll", PactGroup(blockerName = "Doomscroll", pactMaxMinutes = 5), now = t0
        )

        assertEquals(15, pactManager.getGroups().single().pactMaxMinutes)
        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertEquals(5, pactManager.getGroups().single().pactMaxMinutes)
        assertNull(manager.revisionForCircle("Doomscroll"))
    }

    @Test
    fun `a due circle removal deletes the group`() {
        pactManager.saveGroup(PactGroup(blockerName = "Doomscroll"))
        manager.queueCircleRevision("Doomscroll", null, now = t0)

        assertEquals(1, pactManager.getGroups().size)
        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertTrue(pactManager.getGroups().isEmpty())
    }

    @Test
    fun `app and circle revisions with the same name are independent`() {
        // A circle named like a package must not collide with the app entry.
        seedConfig(pact())
        pactManager.saveGroup(PactGroup(blockerName = pkg))
        manager.queueAppRevision(pkg, null, now = t0)
        manager.queueCircleRevision(pkg, null, now = t0)
        assertEquals(2, manager.getRevisions().size)

        manager.cancelCircleRevision(pkg)
        assertNull(manager.revisionForCircle(pkg))
        assertTrue(manager.revisionForApp(pkg)!!.isRemoval)
    }

    @Test
    fun `only due revisions apply, the rest stay queued`() {
        seedConfig(pact(maxMinutes = 15))
        seedConfig(pact(packageName = "com.other.app", maxMinutes = 15))
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)
        manager.queueAppRevision(
            "com.other.app", pact(packageName = "com.other.app", maxMinutes = 5), now = t0 + 60_000
        )

        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertEquals(30, storedConfigs()[pkg]?.pactMaxMinutes)
        assertEquals(15, storedConfigs()["com.other.app"]?.pactMaxMinutes)
        assertEquals(1, manager.getRevisions().size)
        assertEquals("com.other.app", manager.getRevisions().single().packageName)
    }

    @Test
    fun `revisions survive a manager restart via prefs`() {
        manager.queueAppRevision(pkg, pact(maxMinutes = 30), now = t0)

        val restarted = PactRevisionManager(prefs, gson)
        assertEquals(t0 + delay, restarted.revisionForApp(pkg)?.appliesAtMillis)
    }

    @Test
    fun `entries without any target are dropped on load`() {
        prefs.putString(
            Constants.PrefsKeys.PACT_PENDING_REVISIONS,
            """[{"requestedAtMillis":1,"appliesAtMillis":2},
                {"packageName":"$pkg","requestedAtMillis":1,"appliesAtMillis":2}]"""
        )
        assertEquals(listOf(pkg), manager.getRevisions().map { it.packageName })
    }

    @Test
    fun `a due creation is dropped when the config store is full`() {
        (1..Constants.MAX_APP_TIME_LIMITS).forEach { i ->
            seedConfig(pact(packageName = "com.filler.app$i"))
        }
        // pkg has no stored config (a circle-member creation), the store is full.
        manager.queueAppRevision(pkg, pact(), now = t0)
        // An existing entry's change still applies.
        manager.queueAppRevision(
            "com.filler.app1", pact(packageName = "com.filler.app1", maxMinutes = 5), now = t0
        )

        assertTrue(manager.applyDueRevisions(now = t0 + delay))
        assertNull(storedConfigs()[pkg])
        assertEquals(5, storedConfigs()["com.filler.app1"]?.pactMaxMinutes)
        assertTrue(manager.getRevisions().isEmpty())
    }

    // ── requiresDelay decision ──

    private val blockers =
        listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.member.a", "com.member.b")))
    private val groups = listOf(PactGroup(blockerName = "Doom"))

    @Test
    fun `requiresDelayForApp is true for an explicit pact config`() {
        val configs = mapOf(pkg to pact())
        assertTrue(PactRevisionManager.requiresDelayForApp(pkg, configs, emptyList(), emptyList()))
    }

    @Test
    fun `requiresDelayForApp is false for a ward config`() {
        val configs = mapOf(pkg to AppTimeLimit(packageName = pkg, dailyLimitMinutes = 60))
        assertFalse(PactRevisionManager.requiresDelayForApp(pkg, configs, groups, blockers))
    }

    @Test
    fun `requiresDelayForApp is true for a live circle member without its own config`() {
        assertTrue(
            PactRevisionManager.requiresDelayForApp("com.member.a", emptyMap(), groups, blockers)
        )
    }

    @Test
    fun `requiresDelayForApp is false for a circle member overridden by an explicit ward`() {
        val configs =
            mapOf("com.member.a" to AppTimeLimit(packageName = "com.member.a", dailyLimitMinutes = 60))
        assertFalse(
            PactRevisionManager.requiresDelayForApp("com.member.a", configs, groups, blockers)
        )
    }

    @Test
    fun `requiresDelayForApp is false for an unguarded app`() {
        assertFalse(
            PactRevisionManager.requiresDelayForApp("com.free.app", emptyMap(), groups, blockers)
        )
    }

    @Test
    fun `requiresDelayForCircle is true only for an existing group`() {
        assertTrue(PactRevisionManager.requiresDelayForCircle("Doom", groups))
        assertFalse(PactRevisionManager.requiresDelayForCircle("New Circle", groups))
    }
}
