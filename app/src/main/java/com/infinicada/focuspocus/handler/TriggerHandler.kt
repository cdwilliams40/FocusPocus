package com.infinicada.focuspocus.handler

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.model.Schedule

sealed class TriggerResult {
    data class Success(val messageResId: Int, val args: Array<Any> = emptyArray()) : TriggerResult()
    data class Error(val messageResId: Int, val args: Array<Any> = emptyArray()) : TriggerResult()
    data class DeepLinkPending(val preset: FocusPreset) : TriggerResult()
    object NoOp : TriggerResult()
}

class TriggerHandler(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val validIdPattern = Regex("^[a-f0-9\\-]{1,64}$")

    /**
     * Core preset toggle logic used by NFC, QR, and deep links.
     * Returns a TriggerResult describing what happened.
     */
    fun togglePreset(preset: FocusPreset, blockerLists: List<Blocker>): TriggerResult {
        val isActive = SessionManager.isSessionActive(prefs)
        val tempDuration = preset.tempDurationMinutes ?: 30

        return when (preset.action ?: PresetAction.TOGGLE) {
            PresetAction.TEMP_ENABLE -> {
                val validNames = preset.effectiveBlockerNames.filter { name -> blockerLists.any { it.name == name } }
                if (validNames.isNotEmpty()) {
                    SessionManager.startSession(
                        sharedPreferences = prefs,
                        blockerNames = validNames,
                        durationMinutes = tempDuration,
                        breaksEnabled = preset.breaksEnabled
                    )
                    TriggerResult.Success(R.string.toast_preset_cast_timed, arrayOf(preset.name, tempDuration))
                } else {
                    TriggerResult.Error(R.string.toast_enchantment_missing, arrayOf(preset.name))
                }
            }
            PresetAction.TEMP_DISABLE -> {
                if (isActive) {
                    val breakSeconds = tempDuration * 60
                    prefs.edit()
                        .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
                        .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakSeconds)
                        .putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS,
                            System.currentTimeMillis() + breakSeconds * 1000L)
                        .apply()
                    DndController.updateDndState(context)
                    TriggerResult.Success(R.string.toast_temp_break, arrayOf(tempDuration))
                } else {
                    TriggerResult.Error(R.string.toast_no_active_focus)
                }
            }
            PresetAction.TOGGLE -> {
                if (isActive) {
                    SessionManager.stopSession(context, prefs, gson)
                    TriggerResult.Success(R.string.toast_preset_dispelled, arrayOf(preset.name))
                } else {
                    val validNames = preset.effectiveBlockerNames.filter { name -> blockerLists.any { it.name == name } }
                    if (validNames.isNotEmpty()) {
                        SessionManager.startSession(
                            sharedPreferences = prefs,
                            blockerNames = validNames,
                            durationMinutes = preset.durationMinutes,
                            breaksEnabled = preset.breaksEnabled
                        )
                        TriggerResult.Success(R.string.toast_preset_cast, arrayOf(preset.name))
                    } else {
                        TriggerResult.Error(R.string.toast_enchantment_missing, arrayOf(preset.name))
                    }
                }
            }
        }
    }

    /**
     * Handle QR code scan result.
     */
    fun handleQrResult(
        contents: String,
        focusPresets: List<FocusPreset>,
        namedTags: List<NamedTag>,
        blockerLists: List<Blocker>
    ): TriggerResult {
        val presetPrefix = "focuspocus://preset/"
        val talismanPrefix = "focuspocus://talisman/"

        return when {
            contents.startsWith(presetPrefix) -> {
                val presetId = contents.removePrefix(presetPrefix)
                if (!validIdPattern.matches(presetId)) {
                    return TriggerResult.Error(R.string.toast_invalid_qr)
                }
                val preset = focusPresets.find { it.id == presetId }
                    ?: return TriggerResult.Error(R.string.toast_quick_spell_not_found)
                togglePreset(preset, blockerLists)
            }
            contents.startsWith(talismanPrefix) -> {
                val talismanId = contents.removePrefix(talismanPrefix)
                if (!validIdPattern.matches(talismanId)) {
                    return TriggerResult.Error(R.string.toast_invalid_qr)
                }
                val talisman = namedTags.find { it.id == talismanId }
                    ?: return TriggerResult.Error(R.string.toast_talisman_not_found)
                val boundPreset = focusPresets.find { it.talismanId == talismanId }
                if (boundPreset != null) {
                    togglePreset(boundPreset, blockerLists)
                } else {
                    TriggerResult.Error(R.string.toast_no_quick_spell_bound, arrayOf(talisman.name))
                }
            }
            else -> TriggerResult.Error(R.string.toast_invalid_qr)
        }
    }

    /**
     * Handle NFC tag discovery.
     */
    fun handleNfcTag(
        tagId: String,
        activeScheduleId: String?,
        schedules: List<Schedule>,
        focusPresets: List<FocusPreset>,
        namedTags: List<NamedTag>,
        blockerLists: List<Blocker>
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

        // Check if a preset is bound to this talisman
        val boundPreset = focusPresets.find { it.talismanId == tagId }
        if (boundPreset != null) {
            val result = togglePreset(boundPreset, blockerLists)
            return NfcResult.PresetToggled(result)
        }

        // Check if it's a named tag (toggle focus tag)
        val isNamed = namedTags.any { it.id == tagId }
        if (isNamed) {
            return NfcResult.ToggleFocusTag
        }

        return NfcResult.UnknownTag
    }

    /**
     * Parse a deep link URI and resolve the preset.
     */
    fun resolveDeepLink(
        data: Uri,
        focusPresets: List<FocusPreset>,
        namedTags: List<NamedTag>
    ): FocusPreset? {
        if (data.scheme != "focuspocus") return null
        val id = data.pathSegments.firstOrNull() ?: return null
        if (!validIdPattern.matches(id)) return null

        return when (data.host) {
            "preset" -> focusPresets.find { it.id == id }
            "talisman" -> {
                val talisman = namedTags.find { it.id == id }
                if (talisman != null) focusPresets.find { it.talismanId == id } else null
            }
            else -> null
        }
    }
}

sealed class NfcResult {
    data class DispelSchedule(val messageResId: Int) : NfcResult()
    data class Toast(val messageResId: Int) : NfcResult()
    data class PresetToggled(val triggerResult: TriggerResult) : NfcResult()
    object ToggleFocusTag : NfcResult()
    object UnknownTag : NfcResult()
}
