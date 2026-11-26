package com.infinicada.focuspocus.handler

import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.Talisman
import com.infinicada.focuspocus.model.Schedule

/**
 * Decides what an NFC tap means. Pure: the caller carries out the result,
 * so every tap goes through the ordinary session start and stop paths.
 */
object TriggerHandler {

    /**
     * In order: a key for the running ritual; the end of the session this
     * talisman started; the start of this talisman's own session.
     *
     * [activeTalismanId] is the talisman that started the running session,
     * if one did. [sessionActive] covers every kind of session.
     */
    fun handleNfcTag(
        tagId: String,
        activeScheduleId: String?,
        activeTalismanId: String?,
        sessionActive: Boolean,
        schedules: List<Schedule>,
        talismans: List<Talisman>,
        blockerLists: List<Blocker>,
        nfcLockMode: Boolean = false
    ): NfcResult {
        // A running ritual bound to a talisman only answers to that talisman.
        if (activeScheduleId != null) {
            val schedule = schedules.find { it.id == activeScheduleId }
            if (schedule?.unbindingTalismanId != null) {
                return if (schedule.unbindingTalismanId == tagId) {
                    NfcResult.DispelSchedule(R.string.toast_ritual_dispelled)
                } else {
                    NfcResult.Toast(R.string.toast_wrong_talisman)
                }
            }
        }

        val talisman = talismans.find { it.id == tagId } ?: return NfcResult.UnknownTag

        if (activeTalismanId == tagId) return NfcResult.StopTalismanSession(talisman)
        // Another session is running.
        if (sessionActive) {
            // A session locked to another talisman only answers to that talisman.
            val lockingTalisman = activeTalismanId?.let { id -> talismans.find { it.id == id } }
            if (lockingTalisman?.locksSession == true) {
                return NfcResult.Toast(R.string.toast_wrong_talisman)
            }
            // Under Talisman Lock Mode, any bound talisman can end a session
            // that has no talisman key of its own (manual session or keyless ritual).
            if (nfcLockMode) {
                return if (activeScheduleId != null) {
                    NfcResult.DispelSchedule(R.string.toast_ritual_dispelled)
                } else {
                    NfcResult.DispelSession(R.string.toast_session_dispelled)
                }
            }
            return NfcResult.Toast(R.string.toast_session_already_running)
        }

        val enchantments = talisman.enchantments.filter { name -> blockerLists.any { it.name == name } }
        if (enchantments.isEmpty()) return NfcResult.Toast(R.string.toast_talisman_no_setup)
        return NfcResult.StartTalismanSession(talisman, enchantments)
    }
}

sealed class NfcResult {
    data class DispelSchedule(val messageResId: Int) : NfcResult()
    data class DispelSession(val messageResId: Int) : NfcResult()
    data class Toast(val messageResId: Int) : NfcResult()
    /** Start [talisman]'s session on [enchantments] (its setup, minus any since deleted). */
    data class StartTalismanSession(val talisman: Talisman, val enchantments: List<String>) : NfcResult()
    data class StopTalismanSession(val talisman: Talisman) : NfcResult()
    object UnknownTag : NfcResult()
}
