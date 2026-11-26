package com.infinicada.focuspocus.handler

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.data.AppContainer

/**
 * Shared foreground-only NFC reader wiring and feedback used by [com.infinicada.focuspocus.MainActivity]
 * and [com.infinicada.focuspocus.OverlayActivity].
 *
 * Deliberately foreground-only: uses [NfcAdapter.enableReaderMode] with
 * [NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK] and NEVER writes NDEF data or registers
 * a manifest NFC intent filter, so tags containing website URLs or other NDEF records
 * keep working normally outside Focus Pocus.
 */
object NfcForegroundHelper {

    private const val PRESENCE_CHECK_DELAY_MS = 500

    fun enableReaderMode(
        activity: Activity,
        nfcAdapter: NfcAdapter?,
        callback: NfcAdapter.ReaderCallback
    ) {
        if (nfcAdapter == null) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS)
        }
        try {
            nfcAdapter.enableReaderMode(activity, callback, flags, extras)
        } catch (_: Exception) {
            // Device may lack active NFC or be in a transient lifecycle state.
        }
    }

    fun disableReaderMode(activity: Activity, nfcAdapter: NfcAdapter?) {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (_: Exception) {
            // Safe no-op if reader mode was not active.
        }
    }

    /**
     * Evaluates and executes a foreground NFC tap, plays distinct haptic feedback,
     * shows a toast, and optionally emits a foreground session summary for stops.
     */
    fun handleTag(
        activity: Activity,
        container: AppContainer,
        tagId: String,
        emitForegroundSummary: Boolean
    ): NfcResult {
        val result = TriggerHandler.handleNfcTag(
            tagId = tagId,
            activeScheduleId = container.session.getActiveScheduleId(),
            activeTalismanId = container.session.getActiveTalismanId(),
            sessionActive = container.session.isSessionActive(),
            schedules = container.schedules.getSchedules(),
            talismans = container.talismans.getTalismans(),
            blockerLists = container.blockers.getBlockers(),
            nfcLockMode = container.settings.getNfcLockMode()
        )

        when (result) {
            is NfcResult.DispelSchedule -> {
                val recordResult = container.session.stopSession()
                if (emitForegroundSummary) container.sessionStore.emitForegroundSummary(recordResult)
                hapticConfirm(activity)
                Toast.makeText(activity, activity.getString(result.messageResId), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.DispelSession -> {
                val recordResult = container.session.stopSession()
                if (emitForegroundSummary) container.sessionStore.emitForegroundSummary(recordResult)
                hapticConfirm(activity)
                Toast.makeText(activity, activity.getString(result.messageResId), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.Toast -> {
                hapticReject(activity)
                Toast.makeText(activity, activity.getString(result.messageResId), Toast.LENGTH_SHORT).show()
            }
            is NfcResult.StartTalismanSession -> {
                container.session.startSession(
                    blockerNames = result.enchantments,
                    durationMinutes = result.talisman.timedMinutes ?: 0,
                    breaksEnabled = result.talisman.breaksAllowed,
                    talismanId = result.talisman.id
                )
                hapticConfirm(activity)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_talisman_started, result.talisman.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is NfcResult.StopTalismanSession -> {
                val recordResult = container.session.stopSession()
                if (emitForegroundSummary) container.sessionStore.emitForegroundSummary(recordResult)
                hapticConfirm(activity)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_talisman_ended, result.talisman.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is NfcResult.UnknownTag -> {
                hapticTick(activity)
            }
        }
        return result
    }

    private fun hapticConfirm(activity: Activity) {
        val view = activity.window?.decorView ?: return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    private fun hapticReject(activity: Activity) {
        val view = activity.window?.decorView ?: return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    private fun hapticTick(activity: Activity) {
        val view = activity.window?.decorView ?: return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
