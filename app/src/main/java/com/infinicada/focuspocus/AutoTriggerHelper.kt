package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AutoTriggerHelper {
    private const val TAG = "AutoTriggerHelper"
    private val gson = Gson()

    fun activatePreset(context: Context, prefs: SharedPreferences, presetId: String) {
        val presetsJson = prefs.getString(Constants.PrefsKeys.FOCUS_PRESETS, null) ?: return
        val presets: List<FocusPreset> = try {
            val type = object : TypeToken<List<FocusPreset>>() {}.type
            gson.fromJson(presetsJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing presets", e)
            return
        }
        val preset = presets.find { it.id == presetId } ?: return

        val blocker = BlockerRepository.getBlocker(prefs, preset.blockerName) ?: return

        val focusTimeRemaining = if (preset.durationMinutes > 0) preset.durationMinutes * 60 else 0
        prefs.edit()
            .putBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, true)
            .putString(Constants.PrefsKeys.ACTIVE_BLOCKER, blocker.name)
            .putInt(Constants.PrefsKeys.FOCUS_DURATION_MINUTES, preset.durationMinutes)
            .putInt(Constants.PrefsKeys.FOCUS_TIME_REMAINING, focusTimeRemaining)
            .putBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, preset.breaksEnabled)
            .putLong(Constants.PrefsKeys.SESSION_START_TIME, System.currentTimeMillis())
            .apply()

        DndController.updateDndState(context)
    }

    fun loadTriggers(prefs: SharedPreferences): List<AutoTrigger> {
        val json = prefs.getString(Constants.PrefsKeys.AUTO_TRIGGERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AutoTrigger>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun incrementServicesTriggerCount(prefs: SharedPreferences) {
        val current = prefs.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0)
        prefs.edit().putInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, current + 1).apply()
    }
}
