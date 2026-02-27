package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AutoTriggerHelper {
    private val gson = Gson()
    private var cachedPresetsJson: String? = null
    private var cachedPresets: List<FocusPreset> = emptyList()
    private var cachedTriggersJson: String? = null
    private var cachedTriggers: List<AutoTrigger> = emptyList()

    fun activatePreset(context: Context, prefs: SharedPreferences, presetId: String) {
        val presets = getPresets(prefs)
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

    private fun getPresets(prefs: SharedPreferences): List<FocusPreset> {
        val json = prefs.getString(Constants.PrefsKeys.FOCUS_PRESETS, null)

        synchronized(this) {
            if (json == null) {
                cachedPresetsJson = null
                cachedPresets = emptyList()
                return emptyList()
            }

            if (json == cachedPresetsJson) {
                return cachedPresets
            }

            return try {
                val type = object : TypeToken<List<FocusPreset>>() {}.type
                val parsed: List<FocusPreset> = gson.fromJson(json, type)
                cachedPresetsJson = json
                cachedPresets = parsed
                parsed
            } catch (e: Exception) {
                cachedPresetsJson = null
                cachedPresets = emptyList()
                emptyList()
            }
        }
    }

    fun loadTriggers(prefs: SharedPreferences): List<AutoTrigger> {
        val json = prefs.getString(Constants.PrefsKeys.AUTO_TRIGGERS, null)

        synchronized(this) {
            if (json == null) {
                cachedTriggersJson = null
                cachedTriggers = emptyList()
                return emptyList()
            }

            if (json == cachedTriggersJson) {
                return cachedTriggers
            }

            return try {
                val type = object : TypeToken<List<AutoTrigger>>() {}.type
                val parsed: List<AutoTrigger> = gson.fromJson(json, type)
                cachedTriggersJson = json
                cachedTriggers = parsed
                parsed
            } catch (e: Exception) {
                cachedTriggersJson = null
                cachedTriggers = emptyList()
                emptyList()
            }
        }
    }

    fun incrementServicesTriggerCount(prefs: SharedPreferences) {
        val current = prefs.getInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, 0)
        prefs.edit().putInt(Constants.PrefsKeys.SERVICES_TRIGGER_COUNT, current + 1).apply()
    }
}
