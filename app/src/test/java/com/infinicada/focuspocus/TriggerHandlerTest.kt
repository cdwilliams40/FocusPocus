package com.infinicada.focuspocus

import com.infinicada.focuspocus.handler.NfcResult
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerHandlerTest {

    private val social = Blocker("Social", BlockerMode.BLACKLIST, setOf("com.feed"))
    private val shield = Talisman("tag-1", "Shield", blockerNames = listOf("Social"), durationMinutes = 0)

    private fun ritual(key: String?) = Schedule(
        id = "sched-1", name = "Morning", blockerNames = listOf("Social"),
        days = setOf(DayOfWeek.MONDAY), startTime = "06:00", endTime = "12:00",
        unbindingTalismanId = key
    )

    private fun tap(
        tagId: String,
        activeScheduleId: String? = null,
        activeTalismanId: String? = null,
        sessionActive: Boolean = false,
        schedules: List<Schedule> = emptyList(),
        talismans: List<Talisman> = listOf(shield),
        blockers: List<Blocker> = listOf(social)
    ) = TriggerHandler.handleNfcTag(
        tagId, activeScheduleId, activeTalismanId, sessionActive, schedules, talismans, blockers
    )

    // ── ritual keys ──

    @Test
    fun `the ritual's own talisman ends it`() {
        val result = tap("tag-1", activeScheduleId = "sched-1", sessionActive = true, schedules = listOf(ritual("tag-1")))
        assertTrue(result is NfcResult.DispelSchedule)
        assertEquals(R.string.toast_ritual_dispelled, (result as NfcResult.DispelSchedule).messageResId)
    }

    @Test
    fun `any other tag is refused while a keyed ritual runs`() {
        val result = tap("tag-1", activeScheduleId = "sched-1", sessionActive = true, schedules = listOf(ritual("tag-other")))
        assertEquals(NfcResult.Toast(R.string.toast_wrong_talisman), result)
    }

    @Test
    fun `a ritual without a key doesn't let a tap replace it`() {
        val result = tap("tag-1", activeScheduleId = "sched-1", sessionActive = true, schedules = listOf(ritual(null)))
        assertEquals(NfcResult.Toast(R.string.toast_session_already_running), result)
    }

    // ── talisman sessions ──

    @Test
    fun `a tap starts the talisman's session`() {
        val result = tap("tag-1")
        assertEquals(NfcResult.StartTalismanSession(shield, listOf("Social")), result)
    }

    @Test
    fun `a second tap of the same talisman ends its session`() {
        val result = tap("tag-1", activeTalismanId = "tag-1", sessionActive = true)
        assertEquals(NfcResult.StopTalismanSession(shield), result)
    }

    @Test
    fun `a tap never replaces a session something else started`() {
        val other = Talisman("tag-2", "Desk", blockerNames = listOf("Social"))
        val result = tap("tag-2", activeTalismanId = "tag-1", sessionActive = true, talismans = listOf(shield, other))
        assertEquals(NfcResult.Toast(R.string.toast_session_already_running), result)
    }

    @Test
    fun `deleted enchantments are dropped from the setup`() {
        val talisman = shield.copy(blockerNames = listOf("Gone", "Social"))
        val result = tap("tag-1", talismans = listOf(talisman))
        assertEquals(NfcResult.StartTalismanSession(talisman, listOf("Social")), result)
    }

    @Test
    fun `a talisman with no usable setup says so`() {
        assertEquals(
            NfcResult.Toast(R.string.toast_talisman_no_setup),
            tap("tag-1", talismans = listOf(Talisman("tag-1", "Bare")))
        )
        assertEquals(
            NfcResult.Toast(R.string.toast_talisman_no_setup),
            tap("tag-1", talismans = listOf(shield.copy(blockerNames = listOf("Gone"))))
        )
    }

    @Test
    fun `an unknown tag does nothing`() {
        assertEquals(NfcResult.UnknownTag, tap("tag-unknown"))
    }

    // ── setup defaults for talismans stored before 2.0 ──

    @Test
    fun `a stored talisman without setup fields reads as open-ended, breaks on, locking`() {
        val bare = Talisman("t", "Old")
        assertEquals(null, bare.timedMinutes)
        assertTrue(bare.breaksAllowed)
        assertTrue(bare.locksSession)
        assertTrue(bare.enchantments.isEmpty())
    }
}
