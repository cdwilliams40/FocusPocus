package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.TrialEngine
import com.infinicada.focuspocus.data.ProgressionRepository
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Trial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProgressionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ProgressionRepository =
        (application as FocusPocusApplication).container.progression

    private val _balance = MutableStateFlow(repo.getBalance())
    val balance: StateFlow<Long> = _balance.asStateFlow()

    // getTrials() rotates the slate on read, so init itself covers the
    // process-cached-overnight case.
    private val _trials = MutableStateFlow(repo.getTrials())
    val trials: StateFlow<List<Trial>> = _trials.asStateFlow()

    private val _ledger = MutableStateFlow(repo.getLedger())
    val ledger: StateFlow<List<ManaLedgerEntry>> = _ledger.asStateFlow()

    private val _extraBreakTokens = MutableStateFlow(repo.getExtraBreakTokens())
    val extraBreakTokens: StateFlow<Int> = _extraBreakTokens.asStateFlow()

    private val _manaEarnedThisWeek = MutableStateFlow(computeWeekEarned())
    val manaEarnedThisWeek: StateFlow<Long> = _manaEarnedThisWeek.asStateFlow()

    // What channeling mana into the Well costs right now; it doubles with
    // every channel bought the same day and falls back at midnight.
    private val _wellChannelCost = MutableStateFlow(repo.getWellChannelCost())
    val wellChannelCost: StateFlow<Long> = _wellChannelCost.asStateFlow()

    fun refresh() {
        _wellChannelCost.value = repo.getWellChannelCost()
        _balance.value = repo.getBalance()
        _trials.value = repo.getTrials()
        _ledger.value = repo.getLedger()
        _extraBreakTokens.value = repo.getExtraBreakTokens()
        _manaEarnedThisWeek.value = computeWeekEarned()
    }

    fun claimTrial(trialId: String): Long {
        val mana = repo.claimTrial(trialId)
        refresh()
        return mana
    }

    fun redeemPerk(perk: Perk): Boolean {
        val redeemed = repo.redeemPerk(perk)
        refresh()
        return redeemed
    }

    private fun computeWeekEarned(): Long {
        val weekKey = TrialEngine.weekKeyForDay(DayKey.today())
        return repo.getLedger()
            .filter { it.amount > 0 && it.dateKey.isNotEmpty() && TrialEngine.weekKeyForDay(it.dateKey) == weekKey }
            .sumOf { it.amount }
    }
}
