package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.infinicada.focuspocus.BlockEvent
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.FocusSession
import com.infinicada.focuspocus.data.InsightsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: InsightsRepository =
        (application as FocusPocusApplication).container.insights

    private val _focusSessions = MutableStateFlow(repo.getFocusSessions())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private val _currentStreak = MutableStateFlow(repo.getCurrentStreak())
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _longestStreak = MutableStateFlow(repo.getLongestStreak())
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    private val _blockEvents = MutableStateFlow(repo.getBlockEvents())
    val blockEvents: StateFlow<List<BlockEvent>> = _blockEvents.asStateFlow()

    fun refresh() {
        _focusSessions.value = repo.getFocusSessions()
        _currentStreak.value = repo.getCurrentStreak()
        _longestStreak.value = repo.getLongestStreak()
        _blockEvents.value = repo.getBlockEvents()
    }
}
