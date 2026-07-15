package com.infinicada.focuspocus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.infinicada.focuspocus.DeviceOwnerManager
import com.infinicada.focuspocus.FocusPocusApplication
import com.infinicada.focuspocus.TrialEngine
import com.infinicada.focuspocus.data.ProgressionRepository
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.model.Boon
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

    private val _boons = MutableStateFlow(repo.getBoons())
    val boons: StateFlow<List<Boon>> = _boons.asStateFlow()

    private val _ledger = MutableStateFlow(repo.getLedger())
    val ledger: StateFlow<List<ManaLedgerEntry>> = _ledger.asStateFlow()

    private val _unlockedSigilIds = MutableStateFlow(repo.getUnlockedSigilIds())
    val unlockedSigilIds: StateFlow<Set<String>> = _unlockedSigilIds.asStateFlow()

    private val _extraBreakTokens = MutableStateFlow(repo.getExtraBreakTokens())
    val extraBreakTokens: StateFlow<Int> = _extraBreakTokens.asStateFlow()

    private val _manaEarnedThisWeek = MutableStateFlow(computeWeekEarned())
    val manaEarnedThisWeek: StateFlow<Long> = _manaEarnedThisWeek.asStateFlow()

    fun refresh() {
        _balance.value = repo.getBalance()
        _trials.value = repo.getTrials()
        _boons.value = repo.getBoons()
        _ledger.value = repo.getLedger()
        _unlockedSigilIds.value = repo.getUnlockedSigilIds()
        _extraBreakTokens.value = repo.getExtraBreakTokens()
        _manaEarnedThisWeek.value = computeWeekEarned()
    }

    fun claimTrial(trialId: String): Long {
        val mana = repo.claimTrial(trialId)
        refresh()
        return mana
    }

    /** False when the boon cap is hit. */
    fun saveBoon(boon: Boon): Boolean {
        val saved = repo.saveBoon(boon, _boons.value)
        refresh()
        return saved
    }

    fun deleteBoon(boonId: String) {
        repo.deleteBoon(boonId, _boons.value)
        refresh()
    }

    /** False when the balance doesn't cover the boon. */
    fun redeemBoon(boon: Boon): Boolean {
        val redeemed = repo.redeemBoon(boon)
        refresh()
        return redeemed
    }

    fun redeemPerk(perk: Perk, packageName: String? = null): Boolean {
        val redeemed = repo.redeemPerk(perk, packageName)
        // The sealed-minutes perk grants a pact allowance, which under Warden
        // greying must also lift the app's OS suspension right away.
        if (redeemed && perk == Perk.SEALED_MINUTES) {
            DeviceOwnerManager.syncSuspensions(getApplication())
        }
        refresh()
        return redeemed
    }

    fun isSealedMinutesAvailableToday(packageName: String): Boolean =
        repo.isSealedMinutesAvailableToday(packageName)

    private fun computeWeekEarned(): Long {
        val weekKey = TrialEngine.weekKeyForDay(SessionCooldownManager.todayString())
        return repo.getLedger()
            .filter { it.amount > 0 && it.dateKey.isNotEmpty() && TrialEngine.weekKeyForDay(it.dateKey) == weekKey }
            .sumOf { it.amount }
    }
}
