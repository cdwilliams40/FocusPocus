package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.FocusPreset

class PresetRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getPresets(): List<FocusPreset> {
        val type = object : TypeToken<List<FocusPreset>>() {}.type
        return PrefsHelper.load<List<FocusPreset>>(prefs, gson, Constants.PrefsKeys.FOCUS_PRESETS, type)
            ?: createDefaults()
    }

    private fun createDefaults(): List<FocusPreset> {
        val defaults = listOf(
            FocusPreset(
                name = Constants.Defaults.FocusPresets.DEEP_WORK_NAME,
                blockerName = Constants.Defaults.FocusPresets.DEFAULT_BLOCKER_NAME,
                durationMinutes = Constants.Defaults.FocusPresets.DEEP_WORK_DURATION,
                breaksEnabled = Constants.Defaults.FocusPresets.DEEP_WORK_BREAKS
            ),
            FocusPreset(
                name = Constants.Defaults.FocusPresets.QUICK_FOCUS_NAME,
                blockerName = Constants.Defaults.FocusPresets.DEFAULT_BLOCKER_NAME,
                durationMinutes = Constants.Defaults.FocusPresets.QUICK_FOCUS_DURATION,
                breaksEnabled = Constants.Defaults.FocusPresets.QUICK_FOCUS_BREAKS
            ),
            FocusPreset(
                name = Constants.Defaults.FocusPresets.SLEEP_MODE_NAME,
                blockerName = Constants.Defaults.FocusPresets.DEFAULT_BLOCKER_NAME,
                durationMinutes = Constants.Defaults.FocusPresets.SLEEP_MODE_DURATION,
                breaksEnabled = Constants.Defaults.FocusPresets.SLEEP_MODE_BREAKS
            )
        )
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.FOCUS_PRESETS, defaults)
        return defaults
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun savePreset(preset: FocusPreset, currentList: List<FocusPreset>): Boolean {
        val isUpdate = currentList.any { it.id == preset.id }
        if (!isUpdate && currentList.size >= Constants.MAX_PRESETS) return false

        val updated = currentList.filterNot { it.id == preset.id } + preset
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.FOCUS_PRESETS, updated)
        return true
    }

    fun deletePreset(preset: FocusPreset, currentList: List<FocusPreset>): List<FocusPreset> {
        val updated = currentList.filterNot { it.id == preset.id }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.FOCUS_PRESETS, updated)
        return updated
    }

    fun cleanupOrphanedPresets(
        presets: List<FocusPreset>,
        talismanIds: Set<String>
    ): List<FocusPreset> {
        val cleaned = presets.map { preset ->
            if (preset.talismanId != null && preset.talismanId !in talismanIds) {
                preset.copy(talismanId = null)
            } else {
                preset
            }
        }
        if (cleaned != presets) {
            PrefsHelper.save(prefs, gson, Constants.PrefsKeys.FOCUS_PRESETS, cleaned)
        }
        return cleaned
    }
}
