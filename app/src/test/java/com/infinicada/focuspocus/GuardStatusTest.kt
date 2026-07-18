package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.GuardLiveState
import com.infinicada.focuspocus.limit.GuardRow
import com.infinicada.focuspocus.limit.GuardState
import com.infinicada.focuspocus.limit.GuardStatus
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardStatusTest {

    private val t0 = 1_000_000_000_000L

    private fun pact(pkg: String, backstop: Int = 0) = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = backstop,
        sessionLimitMinutes = 0,
        cooldownMinutes = 30,
        pactModeEnabled = true,
        pactMaxMinutes = 15
    )

    private fun ward(pkg: String, daily: Int = 60) = AppTimeLimit(
        packageName = pkg,
        dailyLimitMinutes = daily,
        sessionLimitMinutes = 0,
        cooldownMinutes = 30
    )

    // ── resolveState ──

    @Test
    fun `an active cooldown wins over everything`() {
        val live = GuardLiveState(
            allowanceExpiryMillis = t0 + 60_000,
            cooldownExpiryMillis = t0 + 60_000,
            usedMinutesToday = 999
        )
        assertEquals(GuardState.SEALED, GuardStatus.resolveState(pact("a"), live, t0))
        assertEquals(GuardState.SEALED, GuardStatus.resolveState(ward("a"), live, t0))
    }

    @Test
    fun `an active allowance means PACT_ACTIVE for pacts only`() {
        val live = GuardLiveState(allowanceExpiryMillis = t0 + 60_000)
        assertEquals(GuardState.PACT_ACTIVE, GuardStatus.resolveState(pact("a"), live, t0))
        // A ward never has an allowance in practice; even if one lingers, it's ignored.
        assertEquals(GuardState.QUIET, GuardStatus.resolveState(ward("a", daily = 0), live, t0))
    }

    @Test
    fun `expired timestamps do not count`() {
        val live = GuardLiveState(
            allowanceExpiryMillis = t0 - 1,
            cooldownExpiryMillis = t0
        )
        assertEquals(GuardState.QUIET, GuardStatus.resolveState(pact("a"), live, t0))
    }

    @Test
    fun `daily limit reached means OVER_LIMIT for wards and pact backstops`() {
        val live = GuardLiveState(usedMinutesToday = 60)
        assertEquals(GuardState.OVER_LIMIT, GuardStatus.resolveState(ward("a", daily = 60), live, t0))
        assertEquals(GuardState.OVER_LIMIT, GuardStatus.resolveState(pact("a", backstop = 60), live, t0))
        assertEquals(GuardState.QUIET, GuardStatus.resolveState(ward("a", daily = 61), live, t0))
        // No daily limit configured -> usage is irrelevant
        assertEquals(GuardState.QUIET, GuardStatus.resolveState(pact("a"), live, t0))
    }

    // ── minutesUntil ──

    @Test
    fun `minutesUntil rounds up and floors at zero`() {
        assertEquals(0, GuardStatus.minutesUntil(t0, t0))
        assertEquals(1, GuardStatus.minutesUntil(t0 + 1, t0))
        assertEquals(1, GuardStatus.minutesUntil(t0 + 60_000, t0))
        assertEquals(2, GuardStatus.minutesUntil(t0 + 60_001, t0))
        assertEquals(0, GuardStatus.minutesUntil(t0 - 5_000, t0))
    }

    // ── buildRows ──

    @Test
    fun `explicit configs are excluded from their circle's membership`() {
        val configs = mapOf("com.a" to pact("com.a"))
        val blockers = listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.a", "com.b", "com.c")))
        val groups = listOf(PactGroup(blockerName = "Doom"))

        val rows = GuardStatus.buildRows(
            configs, groups, blockers,
            liveStates = emptyMap(), openStats = emptyMap(), names = emptyMap(), now = t0
        )

        val circle = rows.filterIsInstance<GuardRow.Circle>().single()
        assertEquals(listOf("com.b", "com.c"), circle.memberPackages)
        assertEquals(1, rows.filterIsInstance<GuardRow.App>().size)
    }

    @Test
    fun `rows order by state then opens then name`() {
        val configs = mapOf(
            "com.quiet.busy" to pact("com.quiet.busy"),
            "com.quiet.idle" to pact("com.quiet.idle"),
            "com.sealed" to pact("com.sealed"),
            "com.running" to pact("com.running"),
            "com.spent" to ward("com.spent", daily = 30)
        )
        val liveStates = mapOf(
            "com.sealed" to GuardLiveState(cooldownExpiryMillis = t0 + 600_000),
            "com.running" to GuardLiveState(allowanceExpiryMillis = t0 + 300_000),
            "com.spent" to GuardLiveState(usedMinutesToday = 30)
        )
        val openStats = mapOf(
            "com.quiet.busy" to AppOpenStats(opens = 9, reflexOpens = 3),
            "com.quiet.idle" to AppOpenStats(opens = 1, reflexOpens = 0)
        )
        val names = mapOf(
            "com.quiet.busy" to "Busy",
            "com.quiet.idle" to "Idle"
        )

        val rows = GuardStatus.buildRows(
            configs, groups = emptyList(), blockers = emptyList(),
            liveStates = liveStates, openStats = openStats, names = names, now = t0
        )

        val order = rows.filterIsInstance<GuardRow.App>().map { it.packageName }
        assertEquals(
            listOf("com.sealed", "com.running", "com.spent", "com.quiet.busy", "com.quiet.idle"),
            order
        )
    }

    @Test
    fun `countdowns are populated only for the matching state`() {
        val configs = mapOf(
            "com.sealed" to pact("com.sealed"),
            "com.running" to pact("com.running")
        )
        val liveStates = mapOf(
            "com.sealed" to GuardLiveState(cooldownExpiryMillis = t0 + 22 * 60_000),
            "com.running" to GuardLiveState(allowanceExpiryMillis = t0 + 7 * 60_000)
        )

        val rows = GuardStatus.buildRows(
            configs, emptyList(), emptyList(), liveStates, emptyMap(), emptyMap(), t0
        ).filterIsInstance<GuardRow.App>().associateBy { it.packageName }

        assertEquals(22, rows.getValue("com.sealed").sealMinutesLeft)
        assertEquals(0, rows.getValue("com.sealed").allowanceMinutesLeft)
        assertEquals(7, rows.getValue("com.running").allowanceMinutesLeft)
        assertEquals(0, rows.getValue("com.running").sealMinutesLeft)
    }

    @Test
    fun `circle aggregates member states and stats`() {
        val blockers = listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.a", "com.b", "com.c")))
        val groups = listOf(PactGroup(blockerName = "Doom"))
        val liveStates = mapOf(
            "com.a" to GuardLiveState(cooldownExpiryMillis = t0 + 60_000),
            "com.b" to GuardLiveState(allowanceExpiryMillis = t0 + 60_000)
        )
        val openStats = mapOf(
            "com.a" to AppOpenStats(opens = 4, reflexOpens = 2),
            "com.c" to AppOpenStats(opens = 1, reflexOpens = 1)
        )

        val circle = GuardStatus.buildRows(
            emptyMap(), groups, blockers, liveStates, openStats, emptyMap(), t0
        ).filterIsInstance<GuardRow.Circle>().single()

        assertEquals(1, circle.sealedCount)
        assertEquals(1, circle.pactActiveCount)
        assertEquals(5, circle.opensToday)
        assertEquals(3, circle.reflexesToday)
    }

    @Test
    fun `circle quiet members are the requestable ones`() {
        val blockers = listOf(
            Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.a", "com.b", "com.c", "com.d"))
        )
        val groups = listOf(PactGroup(blockerName = "Doom", dailyLimitMinutes = 30))
        val liveStates = mapOf(
            "com.a" to GuardLiveState(cooldownExpiryMillis = t0 + 60_000),   // sealed
            "com.b" to GuardLiveState(allowanceExpiryMillis = t0 + 60_000),  // pact running
            "com.c" to GuardLiveState(usedMinutesToday = 30)                 // backstop spent
        )

        val circle = GuardStatus.buildRows(
            emptyMap(), groups, blockers, liveStates, emptyMap(), emptyMap(), t0
        ).filterIsInstance<GuardRow.Circle>().single()

        assertEquals(listOf("com.d"), circle.quietMemberPackages)
    }

    @Test
    fun `circle of a deleted enchantment renders with no members`() {
        val rows = GuardStatus.buildRows(
            emptyMap(), listOf(PactGroup(blockerName = "Gone")), emptyList(),
            emptyMap(), emptyMap(), emptyMap(), t0
        )
        assertEquals(0, rows.filterIsInstance<GuardRow.Circle>().single().memberPackages.size)
    }

    // ── headline & rollup ──

    @Test
    fun `headline sums app and circle counts`() {
        val configs = mapOf(
            "com.sealed" to pact("com.sealed"),
            "com.running" to pact("com.running"),
            "com.spent" to ward("com.spent", daily = 30)
        )
        val blockers = listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.x", "com.y")))
        val groups = listOf(PactGroup(blockerName = "Doom"))
        val liveStates = mapOf(
            "com.sealed" to GuardLiveState(cooldownExpiryMillis = t0 + 60_000),
            "com.running" to GuardLiveState(allowanceExpiryMillis = t0 + 60_000),
            "com.spent" to GuardLiveState(usedMinutesToday = 30),
            "com.x" to GuardLiveState(cooldownExpiryMillis = t0 + 60_000)
        )

        val rows = GuardStatus.buildRows(
            configs, groups, blockers, liveStates, emptyMap(), emptyMap(), t0
        )
        val headline = GuardStatus.headlineCounts(rows)

        assertEquals(2, headline.sealedCount)
        assertEquals(1, headline.pactActiveCount)
        assertEquals(1, headline.overLimitCount)
    }

    @Test
    fun `circle members over their daily backstop count as over limit`() {
        val blockers = listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.a", "com.b")))
        val groups = listOf(PactGroup(blockerName = "Doom", dailyLimitMinutes = 30))
        val liveStates = mapOf("com.a" to GuardLiveState(usedMinutesToday = 45))

        val rows = GuardStatus.buildRows(
            emptyMap(), groups, blockers, liveStates, emptyMap(), emptyMap(), t0
        )

        assertEquals(1, rows.filterIsInstance<GuardRow.Circle>().single().overLimitCount)
        assertEquals(1, GuardStatus.headlineCounts(rows).overLimitCount)
    }

    // ── pactGatedPackages ──

    @Test
    fun `pactGatedPackages applies the explicit-config-wins precedence`() {
        val blockers = listOf(Blocker("Doom", BlockerMode.BLACKLIST, setOf("com.a", "com.b", "com.c")))
        val groups = listOf(PactGroup(blockerName = "Doom"))
        val configs = mapOf(
            // Explicit ward inside the circle: its config wins, so it is NOT pact-gated.
            "com.a" to ward("com.a"),
            // Explicit pact outside the circle: gated.
            "com.z" to pact("com.z")
        )

        assertEquals(
            setOf("com.b", "com.c", "com.z"),
            GuardStatus.pactGatedPackages(configs, groups, blockers)
        )
    }

    @Test
    fun `rollup counts pact rows but not wards`() {
        val configs = mapOf(
            "com.pact" to pact("com.pact"),
            "com.ward" to ward("com.ward")
        )
        val openStats = mapOf(
            "com.pact" to AppOpenStats(opens = 5, reflexOpens = 2),
            // A ward app can have stray open stats (e.g. it was pact'd earlier
            // today); they must not leak into the pact rollup.
            "com.ward" to AppOpenStats(opens = 7, reflexOpens = 4)
        )

        val rows = GuardStatus.buildRows(
            configs, emptyList(), emptyList(), emptyMap(), openStats, emptyMap(), t0
        )
        val rollup = GuardStatus.todayRollup(rows)

        assertEquals(5, rollup.opens)
        assertEquals(2, rollup.reflexOpens)
        assertTrue(GuardStatus.hasPactRows(rows))
    }

    @Test
    fun `ward-only setups report no pact rows`() {
        val rows = GuardStatus.buildRows(
            mapOf("com.ward" to ward("com.ward")),
            emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap(), t0
        )
        assertFalse(GuardStatus.hasPactRows(rows))
    }

    // ── Guard hours ──

    @Test
    fun `outside its window a guard shows SCHEDULED_OFF unless sealed`() {
        val live = GuardLiveState(usedMinutesToday = 999, allowanceExpiryMillis = t0 + 60_000)
        assertEquals(
            GuardState.SCHEDULED_OFF,
            GuardStatus.resolveState(ward("a", daily = 60), live, t0, windowActive = false)
        )
        assertEquals(
            GuardState.SCHEDULED_OFF,
            GuardStatus.resolveState(pact("a"), live, t0, windowActive = false)
        )
        // A running seal still shows SEALED — a seal is a seal.
        val sealed = live.copy(cooldownExpiryMillis = t0 + 60_000)
        assertEquals(
            GuardState.SEALED,
            GuardStatus.resolveState(pact("a"), sealed, t0, windowActive = false)
        )
    }

    // ── pactGatedConfigs ──

    @Test
    fun `pactGatedConfigs maps explicit pacts and circle members to their governing config`() {
        val configs = mapOf(
            "com.pact" to pact("com.pact"),
            "com.ward" to ward("com.ward")
        )
        val group = PactGroup(blockerName = "Doomscroll", pactMaxMinutes = 5, cooldownMinutes = 45)
        val blockers = listOf(
            Blocker(
                name = "Doomscroll",
                mode = BlockerMode.BLACKLIST,
                apps = setOf("com.member", "com.pact", "com.ward")
            )
        )

        val gated = GuardStatus.pactGatedConfigs(configs, listOf(group), blockers)

        // Explicit pact config wins as itself; explicit ward excludes the app entirely.
        assertEquals(setOf("com.pact", "com.member"), gated.keys)
        assertEquals(configs["com.pact"], gated["com.pact"])
        // The circle member carries the group's settings.
        assertEquals(45, gated.getValue("com.member").cooldownMinutes)
        assertEquals(5, gated.getValue("com.member").pactMaxMinutes)
        assertTrue(gated.getValue("com.member").pactModeEnabled)
    }
}
