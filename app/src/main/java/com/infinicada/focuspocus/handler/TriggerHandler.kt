package com.infinicada.focuspocus.handler

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.DndController
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.SessionManager
import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.PresetAction
import com.infinicada.focuspocus.model.Schedule

sealed class TriggerResult {
    data class Success(val messageResId: Int, val args: List<Any> = emptyList()) : TriggerResult()
    data class Error(val messageResId: Int, val args: List<Any> = emptyList()) : TriggerResult()
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
    fun togglePreset(
        preset: FocusPreset,
        blockerLists: List<Blocker>,
        schedules: List<Schedule> = emptyList()
    ): TriggerResult {
        // A ritual bound to an unbinding talisman is locked against every
        // other trigger. The NFC path enforces this before reaching here; QR
        // and deep links funnel through this gate instead — otherwise any
        // preset toggle could dispel or overwrite a talisman-locked session.
        val activeScheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (activeScheduleId != null &&
            schedules.find { it.id == activeScheduleId }?.unbindingTalismanId != null
        ) {
            return TriggerResult.Error(R.string.toast_ritual_locked)
        }

        val isActive = SessionManager.isSessionActive(prefs)
        val tempDuration = preset.tempDurationMinutes ?: 30

        return when (preset.action ?: PresetAction.TOGGLE) {
            PresetAction.TEMP_ENABLE -> {
                val validNames = preset.effectiveBlockerNames.filter { name -> blockerLists.any { it.name == name } }
                if (validNames.isNotEmpty()) {
                    // Record any in-progress session before replacing it — the
                    // bare overwrite silently discarded its accrued focus time
                    // and left a scheduled session half-cleared.
                    if (isActive) {
                        SessionManager.stopSession(context, prefs, gson)
                    }
                    SessionManager.startSession(
                        sharedPreferences = prefs,
                        blockerNames = validNames,
                        durationMinutes = tempDuration,
                        breaksEnabled = preset.breaksEnabled
                    )
                    DeviceOwnerManager.syncSuspensions(context)
                    TriggerResult.Success(R.string.toast_preset_cast_timed, listOf(preset.name, tempDuration))
                } else {
                    TriggerResult.Error(R.string.toast_enchantment_missing, listOf(preset.name))
                }
            }
            PresetAction.TEMP_DISABLE -> {
                if (isActive) {
                    val breakSeconds = tempDuration * 60
                    val now = System.currentTimeMillis()
                    val editor = prefs.edit()
                        .putBoolean(Constants.PrefsKeys.IS_ON_BREAK, true)
                        .putInt(Constants.PrefsKeys.BREAK_TIME_REMAINING, breakSeconds)
                        .putLong(Constants.PrefsKeys.BREAK_END_TIME_MILLIS, now + breakSeconds * 1000L)
                    // Freeze the focus countdown for the length of the break, mirroring
                    // SessionRepository.writeBreakState: park the remaining seconds and drop
                    // the end timestamp so the break-end path recomputes it. Without this the
                    // stale FOCUS_TIME_REMAINING from session start would restart the full
                    // countdown when the break expires.
                    val focusEnd = prefs.getLong(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS, 0L)
                    if (focusEnd > 0L) {
                        val focusRemaining = ((focusEnd - now) / 1000L).toInt().coerceAtLeast(0)
                        editor.putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusRemaining)
                        editor.remove(Constants.PrefsKeys.FOCUS_END_TIME_MILLIS)
                    }
                    editor.apply()
                    DndController.updateDndState(context)
                    DeviceOwnerManager.syncSuspensions(context)
                    TriggerResult.Success(R.string.toast_temp_break, listOf(tempDuration))
                } else {
                    TriggerResult.Error(R.string.toast_no_active_focus)
                }
            }
            PresetAction.TOGGLE -> {
                if (isActive) {
                    SessionManager.stopSession(context, prefs, gson)
                    TriggerResult.Success(R.string.toast_preset_dispelled, listOf(preset.name))
                } else {
                    val validNames = preset.effectiveBlockerNames.filter { name -> blockerLists.any { it.name == name } }
                    if (validNames.isNotEmpty()) {
                        SessionManager.startSession(
                            sharedPreferences = prefs,
                            blockerNames = validNames,
                            durationMinutes = preset.durationMinutes,
                            breaksEnabled = preset.breaksEnabled
                        )
                        DeviceOwnerManager.syncSuspensions(context)
                        TriggerResult.Success(R.string.toast_preset_cast, listOf(preset.name))
                    } else {
                        TriggerResult.Error(R.string.toast_enchantment_missing, listOf(preset.name))
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
        blockerLists: List<Blocker>,
        schedules: List<Schedule> = emptyList()
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
                togglePreset(preset, blockerLists, schedules)
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
                    togglePreset(boundPreset, blockerLists, schedules)
                } else {
                    TriggerResult.Error(R.string.toast_no_quick_spell_bound, listOf(talisman.name))
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
            val result = togglePreset(boundPreset, blockerLists, schedules)
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
