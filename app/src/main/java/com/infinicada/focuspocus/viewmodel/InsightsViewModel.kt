package com.infinicada.focuspocus.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.calculateCurrentStreak
import com.infinicada.focuspocus.data.InsightsRepository
import com.infinicada.focuspocus.limit.AppOpenStats
import com.infinicada.focuspocus.limit.OpenReflexTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: InsightsRepository =
        (application as FocusPocusApplication).container.insights

    private val _focusSessions = MutableStateFlow(repo.getFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    // Derived from the sessions already loaded above — repo.getCurrentStreak()
    // would re-parse the whole session store for a second time.
    private val _currentStreak = MutableStateFlow(calculateCurrentStreak(_focusSessions.value))
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _longestStreak = MutableStateFlow(repo.getLongestStreak())
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    private val _blockEvents = MutableStateFlow(repo.getBlockEvents())
    val blockEvents: StateFlow<List<BlockEvent>> = _blockEvents.asStateFlow()

    private val openReflexTracker = OpenReflexTracker(
        application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE),
        Gson()
    )

    // Per-day open/reflex history ("yyyyMMdd" -> package -> stats)
    private val _appOpenDailyStats = MutableStateFlow(openReflexTracker.getDailyStats())
    val appOpenDailyStats: StateFlow<Map<String, Map<String, AppOpenStats>>> =
        _appOpenDailyStats.asStateFlow()

    fun refresh() {
        _focusSessions.value = repo.getFocusSessions()
        _currentStreak.value = calculateCurrentStreak(_focusSessions.value)
        _longestStreak.value = repo.getLongestStreak()
        _blockEvents.value = repo.getBlockEvents()
        _appOpenDailyStats.value = openReflexTracker.getDailyStats()
    }
}
