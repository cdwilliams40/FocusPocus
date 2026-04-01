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

    fun saveAppTimeLimit(
        packageName: String,
        limitMinutes: Int,
        currentLimits: Map<String, Int>
    ): Boolean {
        val isUpdate = packageName in currentLimits
        if (!isUpdate && currentLimits.size >= Constants.MAX_APP_TIME_LIMITS) return false
        val updated = currentLimits.toMutableMap()
        updated[packageName] = limitMinutes
        AppTimeLimitManager.saveTimeLimits(prefs, gson, updated)
        return true
    }

    fun deleteAppTimeLimit(packageName: String, currentLimits: Map<String, Int>): Map<String, Int> {
        val updated = currentLimits.toMutableMap()
        updated.remove(packageName)
        AppTimeLimitManager.saveTimeLimits(prefs, gson, updated)
        return updated
    }
}
