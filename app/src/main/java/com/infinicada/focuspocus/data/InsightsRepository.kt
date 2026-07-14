package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.calculateCurrentStreak
import com.infinicada.focuspocus.model.AppTimeLimit

class InsightsRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getBlockEvents(): List<BlockEvent> {
        val type = object : TypeToken<List<BlockEvent>>() {}.type
        return PrefsHelper.load<List<BlockEvent>>(prefs, gson, Constants.PrefsKeys.BLOCK_EVENTS, type)
            ?: emptyList()
    }

    fun getFocusSessions(): List<FocusSession> {
        val type = object : TypeToken<List<FocusSession>>() {}.type
        return PrefsHelper.load<List<FocusSession>>(prefs, gson, Constants.PrefsKeys.FOCUS_SESSIONS, type)
            ?: emptyList()
    }

    fun getCurrentStreak(): Int = calculateCurrentStreak(getFocusSessions())

    fun getLongestStreak(): Int =
        prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)

    fun getAppTimeLimits(): Map<String, Int> =
        AppTimeLimitManager.getTimeLimits(prefs, gson)

    // NOTE: the legacy flat map (APP_TIME_LIMITS) is read-only here on purpose.
    // saveTimeLimitConfigs rebuilds it wholesale from the config map, so any
    // write routed directly at the flat map would be clobbered (deletions
    // resurrected) by the next config save. All writes go through the config
    // API below.

    // --- Config (includes session-cooldown settings) ---

    fun getAppTimeLimitConfigs(): Map<String, AppTimeLimit> =
        AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)

    fun saveAppTimeLimitConfig(
        config: AppTimeLimit,
        currentConfigs: Map<String, AppTimeLimit>
    ): Boolean {
        val updated = currentConfigs.toMutableMap()
        updated[config.packageName] = config
        if (updated.size > Constants.MAX_APP_TIME_LIMITS) return false
        AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, updated)
        return true
    }

    fun deleteAppTimeLimitConfig(
        packageName: String,
        currentConfigs: Map<String, AppTimeLimit>
    ): Map<String, AppTimeLimit> {
        val updated = currentConfigs.toMutableMap()
        updated.remove(packageName)
        // saveTimeLimitConfigs keeps the legacy flat map in sync automatically.
        AppTimeLimitManager.saveTimeLimitConfigs(prefs, gson, updated)
        return updated
    }
}
