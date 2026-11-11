package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

}
