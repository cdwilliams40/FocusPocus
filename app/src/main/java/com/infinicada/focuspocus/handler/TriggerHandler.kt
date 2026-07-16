package com.infinicada.focuspocus.handler

import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.Schedule

/**
 * Outcome of presenting a talisman — an NFC tap or a scanned QR talisman.
 * Both paths resolve through the same rules: a talisman-locked ritual is
 * dispelled only by its own talisman; any other named talisman toggles the
 * focus-tag session anchor.
 */
sealed class NfcResult {
    data class DispelSchedule(val messageResId: Int) : NfcResult()
    data class Toast(val messageResId: Int) : NfcResult()

    /** A named talisman was presented: toggle the focus-tag session anchor. */
    data class ToggleFocusTag(val tagId: String) : NfcResult()

    object UnknownTag : NfcResult()
}

class TriggerHandler {
    private val validIdPattern = Regex("^[a-f0-9\\-]{1,64}$")

    /** Handle NFC tag discovery. */
    fun handleNfcTag(
        tagId: String,
        activeScheduleId: String?,
        schedules: List<Schedule>,
        namedTags: List<NamedTag>
    ): NfcResult {
        // Check if an active schedule needs unbinding
        if (activeScheduleId != null) {
            val schedule = schedules.find { it.id == activeScheduleId }
            if (schedule != null && schedule.unbindingTalismanId != null) {
                return if (schedule.unbindingTalismanId == tagId) {
                    NfcResult.DispelSchedule(R.string.toast_ritual_dispelled)
                } else {
                    NfcResult.Toast(R.string.toast_wrong_talisman)
                }
            }
        }

        // A named tag toggles the focus-tag session anchor
        if (namedTags.any { it.id == tagId }) {
            return NfcResult.ToggleFocusTag(tagId)
        }

        return NfcResult.UnknownTag
    }

    /**
     * Handle a QR scan result. A QR talisman ("focuspocus://talisman/<id>")
     * behaves exactly like tapping the NFC talisman with that id; anything
     * else is rejected with a toast.
     */
    fun handleQrResult(
        contents: String,
        activeScheduleId: String?,
        schedules: List<Schedule>,
        namedTags: List<NamedTag>
    ): NfcResult {
        val talismanPrefix = "focuspocus://talisman/"
        if (!contents.startsWith(talismanPrefix)) {
            return NfcResult.Toast(R.string.toast_invalid_qr)
        }
        val talismanId = contents.removePrefix(talismanPrefix)
        if (!validIdPattern.matches(talismanId)) {
            return NfcResult.Toast(R.string.toast_invalid_qr)
        }
        if (namedTags.none { it.id == talismanId }) {
            return NfcResult.Toast(R.string.toast_talisman_not_found)
        }
        return handleNfcTag(talismanId, activeScheduleId, schedules, namedTags)
    }
}
