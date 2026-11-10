package com.infinicada.focuspocus

import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellSettings
import com.infinicada.focuspocus.limit.WellUnlocks
import com.infinicada.focuspocus.model.ConditionalUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WellUnlocksTest {

    private lateinit var prefs: FakeSharedPreferences

    private val t0 = 1_000_000_000_000L
    private val minute = 60_000L
    private val today = "20261109"
    private val tomorrow = "20261110"

    private val study = "com.example.study"
    private val feed = "com.example.feed"
    private val mail = "com.example.mail"

    /** Only the feed is a Well-bound pact; mail stands for a ward or an exempt pact. */
    private val isWellBound: (String) -> Boolean = { it == feed }

    private fun rule(id: String, minutes: Int = 30, apps: Set<String> = setOf(feed)) =
        ConditionalUnlock(
            id = id,
            name = id,
            requiredAppPackage = study,
            requiredMinutes = minutes,
            unlockedTimeLimitApps = apps
        )

    /** Turns the Well on and drains it, so a grant has somewhere to go. */
    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        ManaWell.requestSettings(prefs, WellSettings(true, 30, 20), t0)
        ManaWell.tryDraw(prefs, 30, t0, today)
    }

    private fun minutesInWell(now: Long = t0): Int =
        ManaWell.snapshot(prefs, now, today).availableMinutesAt(now)

    private fun credit(
        rules: List<ConditionalUnlock>,
        studiedMinutes: Int,
        now: Long = t0,
        day: String = today
    ): Int = WellUnlocks.creditSatisfied(
        prefs, rules, isWellBound, { if (it == study) studiedMinutes * minute else 0L }, now, day
    )

    @Test
    fun `only rules naming a Well-bound pact pay into the Well`() {
        val paying = rule("feed-rule")
        val wardOnly = rule("mail-rule", apps = setOf(mail))
        val enchantmentOnly = ConditionalUnlock(
            id = "ench", name = "ench", requiredAppPackage = study, requiredMinutes = 30,
            unlockedBlockerNames = setOf("Social")
        )
        val zeroMinutes = rule("zero", minutes = 0)

        assertEquals(
            listOf(paying),
            WellUnlocks.payingRules(listOf(paying, wardOnly, enchantmentOnly, zeroMinutes), isWellBound)
        )
    }

    @Test
    fun `a satisfied rule pours its grant into the Well`() {
        assertEquals(WellUnlocks.GRANT_MINUTES, credit(listOf(rule("r")), studiedMinutes = 30))
        assertEquals(WellUnlocks.GRANT_MINUTES, minutesInWell())
    }

    @Test
    fun `an unsatisfied rule pays nothing and can still pay later`() {
        assertEquals(0, credit(listOf(rule("r")), studiedMinutes = 29))
        assertEquals(0, minutesInWell())
        assertEquals(WellUnlocks.GRANT_MINUTES, credit(listOf(rule("r")), studiedMinutes = 30))
    }

    @Test
    fun `a rule pays once a day`() {
        credit(listOf(rule("r")), studiedMinutes = 45)
        assertEquals(0, credit(listOf(rule("r")), studiedMinutes = 90))
        assertEquals(WellUnlocks.GRANT_MINUTES, minutesInWell())

        // A new day, and the Well drained again: the rule pays afresh.
        val nextDay = t0 + 24 * 60 * minute
        ManaWell.tryDraw(prefs, 30, nextDay, tomorrow)
        assertEquals(
            WellUnlocks.GRANT_MINUTES,
            credit(listOf(rule("r")), studiedMinutes = 30, now = nextDay, day = tomorrow)
        )
    }

    @Test
    fun `each satisfied rule pays separately`() {
        val added = credit(listOf(rule("a"), rule("b", minutes = 60)), studiedMinutes = 60)
        assertEquals(2 * WellUnlocks.GRANT_MINUTES, added)
        assertEquals(setOf("a", "b"), ManaWell.creditedUnlocks(prefs, today))
    }

    @Test
    fun `a rule that only frees wards or exempt pacts never pays`() {
        assertEquals(0, credit(listOf(rule("mail-rule", apps = setOf(mail))), studiedMinutes = 120))
        assertEquals(0, minutesInWell())
    }

    @Test
    fun `a full Well takes nothing and the rule stays unpaid`() {
        ManaWell.channel(prefs, 30, t0)
        assertEquals(0, credit(listOf(rule("r")), studiedMinutes = 30))
        assertTrue(ManaWell.creditedUnlocks(prefs, today).isEmpty())

        // Room appears later the same day: the reward lands then.
        ManaWell.tryDraw(prefs, 15, t0, today)
        assertEquals(WellUnlocks.GRANT_MINUTES, credit(listOf(rule("r")), studiedMinutes = 30))
    }

    @Test
    fun `a nearly full Well takes what fits`() {
        ManaWell.channel(prefs, 26, t0)
        assertEquals(4, credit(listOf(rule("r")), studiedMinutes = 30))
        assertEquals(30, minutesInWell())
        // Paid, even though it was less than the full grant.
        assertEquals(0, credit(listOf(rule("r")), studiedMinutes = 30))
    }

    @Test
    fun `nothing is paid while the Well is off`() {
        val off = FakeSharedPreferences()
        val added = WellUnlocks.creditSatisfied(
            off, listOf(rule("r")), isWellBound, { 90 * minute }, t0, today
        )
        assertEquals(0, added)
        assertTrue(ManaWell.creditedUnlocks(off, today).isEmpty())
    }

    @Test
    fun `usage is not queried once every paying rule has paid`() {
        credit(listOf(rule("r")), studiedMinutes = 30)
        var queries = 0
        WellUnlocks.creditSatisfied(
            prefs, listOf(rule("r"), rule("mail-rule", apps = setOf(mail))), isWellBound,
            { queries++; 90 * minute }, t0, today
        )
        assertEquals(0, queries)
    }
}
