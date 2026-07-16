package com.infinicada.focuspocus

import com.infinicada.focuspocus.handler.NfcResult
import com.infinicada.focuspocus.handler.TriggerHandler
import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerHandlerTest {

    private val handler = TriggerHandler()

    private fun lockedSchedule(talismanId: String?) = Schedule(
        id = "sched-1", name = "Test", blockerNames = listOf("TestBlocker"),
        days = setOf(DayOfWeek.MONDAY), startTime = "09:00", endTime = "17:00",
        unbindingTalismanId = talismanId
    )

    // ── handleNfcTag ──

    @Test
    fun `handleNfcTag with matching unbinding talisman returns DispelSchedule`() {
        val result = handler.handleNfcTag(
            "tag-1", "sched-1", listOf(lockedSchedule("tag-1")), emptyList()
        )

        assertTrue(result is NfcResult.DispelSchedule)
        assertEquals(R.string.toast_ritual_dispelled, (result as NfcResult.DispelSchedule).messageResId)
    }

    @Test
    fun `handleNfcTag with wrong talisman for schedule returns Toast`() {
        val result = handler.handleNfcTag(
            "tag-1", "sched-1", listOf(lockedSchedule("tag-other")), emptyList()
        )

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_wrong_talisman, (result as NfcResult.Toast).messageResId)
    }

    @Test
    fun `handleNfcTag with named tag returns ToggleFocusTag carrying the id`() {
        val namedTag = NamedTag("tag-1", "My Tag")
        val result = handler.handleNfcTag("tag-1", null, emptyList(), listOf(namedTag))

        assertTrue(result is NfcResult.ToggleFocusTag)
        assertEquals("tag-1", (result as NfcResult.ToggleFocusTag).tagId)
    }

    @Test
    fun `handleNfcTag with unknown tag returns UnknownTag`() {
        val result = handler.handleNfcTag("tag-unknown", null, emptyList(), emptyList())

        assertTrue(result is NfcResult.UnknownTag)
    }

    @Test
    fun `handleNfcTag schedule with null unbindingTalismanId falls through to named tags`() {
        val namedTag = NamedTag("tag-1", "My Tag")
        val result = handler.handleNfcTag(
            "tag-1", "sched-1", listOf(lockedSchedule(null)), listOf(namedTag)
        )

        assertTrue(result is NfcResult.ToggleFocusTag)
    }

    // ── handleQrResult ──

    @Test
    fun `handleQrResult with known talisman returns ToggleFocusTag`() {
        val talisman = NamedTag("aaa111", "MyTalisman")
        val result = handler.handleQrResult(
            "focuspocus://talisman/aaa111", null, emptyList(), listOf(talisman)
        )

        assertTrue(result is NfcResult.ToggleFocusTag)
        assertEquals("aaa111", (result as NfcResult.ToggleFocusTag).tagId)
    }

    @Test
    fun `handleQrResult with unknown talisman returns not-found Toast`() {
        val result = handler.handleQrResult(
            "focuspocus://talisman/abc123", null, emptyList(), emptyList()
        )

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_talisman_not_found, (result as NfcResult.Toast).messageResId)
    }

    @Test
    fun `handleQrResult with invalid talisman ID returns invalid Toast`() {
        val result = handler.handleQrResult(
            "focuspocus://talisman/INVALID!@#", null, emptyList(), emptyList()
        )

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_invalid_qr, (result as NfcResult.Toast).messageResId)
    }

    @Test
    fun `handleQrResult with unrecognized URI returns invalid Toast`() {
        val result = handler.handleQrResult("https://example.com", null, emptyList(), emptyList())

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_invalid_qr, (result as NfcResult.Toast).messageResId)
    }

    @Test
    fun `handleQrResult with matching unbinding talisman dispels the ritual`() {
        val talisman = NamedTag("aaa111", "MyTalisman")
        val result = handler.handleQrResult(
            "focuspocus://talisman/aaa111", "sched-1",
            listOf(lockedSchedule("aaa111")), listOf(talisman)
        )

        assertTrue(result is NfcResult.DispelSchedule)
    }

    @Test
    fun `handleQrResult with wrong talisman for locked ritual returns Toast`() {
        val talisman = NamedTag("aaa111", "MyTalisman")
        val result = handler.handleQrResult(
            "focuspocus://talisman/aaa111", "sched-1",
            listOf(lockedSchedule("tag-other")), listOf(talisman)
        )

        assertTrue(result is NfcResult.Toast)
        assertEquals(R.string.toast_wrong_talisman, (result as NfcResult.Toast).messageResId)
    }
}
