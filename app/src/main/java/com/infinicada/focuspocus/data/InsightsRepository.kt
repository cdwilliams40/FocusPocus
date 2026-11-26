package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.calculateCurrentStreak

class InsightsRepository(
    private val prefs: SharedPreferences
) {
    fun getBlockEvents(): List<BlockEvent> =
        PrefsHelper.loadListLenient<BlockEvent>(prefs, Constants.PrefsKeys.BLOCK_EVENTS) ?: emptyList()

    fun getFocusSessions(): List<FocusSession> =
        PrefsHelper.loadListLenient<FocusSession>(prefs, Constants.PrefsKeys.FOCUS_SESSIONS) ?: emptyList()

    fun getCurrentStreak(): Int = calculateCurrentStreak(getFocusSessions())

    fun getLongestStreak(): Int = prefs.getInt(Constants.PrefsKeys.LONGEST_STREAK, 0)
}
