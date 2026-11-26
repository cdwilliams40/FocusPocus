package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.limit.DailyHistory
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Trial

object Progression {

    data class AwardResult(
        val manaEarned: Long,
        val completedTrials: List<Trial>
    ) {
        companion object {
            val EMPTY = AwardResult(0L, emptyList())
        }
    }

    @Synchronized
    fun awardForSession(
        prefs: SharedPreferences,
        session: FocusSession
    ): AwardResult {
        rolloverIfNeeded(prefs)
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val today = DayKey.today()
        val day = DailyHistory.manaDay(prefs, today)
        val award = ManaRules.sessionAward(
            session,
            timedEarnedToday = day.timedSession,
            openEndedPaidToday = day.openEndedSession > 0L
        )
        credit(
            prefs, award.mana,
            ManaLedgerEntry(
                timestampMillis = session.endTimeMillis,
                amount = award.mana,
                kind = if (award.timed) LedgerKind.SESSION else LedgerKind.OPEN_ENDED,
                minutes = session.durationMinutes,
                dateKey = today
            )
        )

        val (advanced, completed) = TrialEngine.advanceForSession(
            loadTrials(prefs),
            session.durationMinutes,
            timed = award.timed,
            startedByHand = session.source == FocusSession.SOURCE_MANUAL
        )
        saveTrials(prefs, advanced)

        return AwardResult(award.mana, completed)
    }

    @Synchronized
    fun creditProductive(prefs: SharedPreferences, minutesToday: Int): AwardResult {
        rolloverIfNeeded(prefs)
        val today = DayKey.today()
        val day = DailyHistory.manaDay(prefs, today)
        if (minutesToday <= day.productiveMinutes) return AwardResult.EMPTY
        val added = minutesToday - day.productiveMinutes
        DailyHistory.updateMana(prefs, today) { it.copy(productiveMinutes = minutesToday) }
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val owed = (ManaRules.productiveMana(minutesToday) - day.productive).coerceAtLeast(0L)
        credit(
            prefs, owed,
            ManaLedgerEntry(
                timestampMillis = System.currentTimeMillis(),
                amount = owed,
                kind = LedgerKind.PRODUCTIVE,
                minutes = added,
                dateKey = today
            )
        )
        val (advanced, completed) = TrialEngine.advanceForProductive(loadTrials(prefs), minutesToday, added)
        saveTrials(prefs, advanced)
        return AwardResult(owed, completed)
    }

    /** Mana the next pick of a substitute would pay today (0 once the day's paid picks are used). */
    fun nextSubstituteWinMana(prefs: SharedPreferences, todayKey: String = DayKey.today()): Long =
        if (isEnabled(prefs)) ManaRules.substituteWinMana(DailyHistory.wellDay(prefs, todayKey).substituteWins)
        else 0L

    @Synchronized
    fun awardSubstituteWin(
        prefs: SharedPreferences,
        substitutePackageName: String,
        nowMillis: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today()
    ): AwardResult {
        rolloverIfNeeded(prefs)
        val winsBefore = DailyHistory.wellDay(prefs, todayKey).substituteWins
        DailyHistory.updateWell(prefs, todayKey) { it.copy(substituteWins = it.substituteWins + 1) }
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val mana = ManaRules.substituteWinMana(winsBefore)
        if (mana > 0L) {
            credit(
                prefs, mana,
                ManaLedgerEntry(
                    timestampMillis = nowMillis,
                    amount = mana,
                    kind = LedgerKind.SUBSTITUTE_WIN,
                    packageName = substitutePackageName,
                    dateKey = todayKey
                )
            )
        }
        val (advanced, completed) = TrialEngine.advanceForSubstituteWin(loadTrials(prefs))
        saveTrials(prefs, advanced)
        return AwardResult(mana, completed)
    }

    @Synchronized
    fun rolloverIfNeeded(prefs: SharedPreferences) {
        val dailyKey = DayKey.today()
        val weekKey = TrialEngine.weekKeyForDay(dailyKey)
        val trials = loadTrials(prefs)
        val (active, ended) = trials.partition { !TrialEngine.isEnded(it, dailyKey, weekKey) }

        if (ended.isNotEmpty()) {
            val wellMinutes = DailyHistory.wellDays(prefs)
                .mapValues { (_, day) -> (day.drawnMillis / 60_000L).toInt() }
            val enabled = isEnabled(prefs)
            for (endedTrial in ended) {
                val judged = TrialEngine.judgeEndedTrial(endedTrial, wellMinutes)
                if (judged.completed && !judged.claimed && enabled) {
                    credit(
                        prefs, judged.rewardMana,
                        ManaLedgerEntry(
                            timestampMillis = System.currentTimeMillis(),
                            amount = judged.rewardMana,
                            kind = LedgerKind.TRIAL,
                            refId = judged.id,
                            dateKey = dailyKey
                        )
                    )
                }
            }
        }

        val slate = TrialEngine.drawSlate(dailyKey, weekKey, active, eligibility(prefs))
        if (slate != trials) saveTrials(prefs, slate)
    }

    @Synchronized
    fun claimTrial(prefs: SharedPreferences, trialId: String): Long {
        if (!isEnabled(prefs)) return 0L
        val trials = loadTrials(prefs)
        val trial = trials.firstOrNull { it.id == trialId } ?: return 0L
        if (!trial.completed || trial.claimed) return 0L
        saveTrials(prefs, trials.map { if (it.id == trialId) it.copy(claimed = true) else it })
        credit(
            prefs, trial.rewardMana,
            ManaLedgerEntry(
                timestampMillis = System.currentTimeMillis(),
                amount = trial.rewardMana,
                kind = LedgerKind.TRIAL,
                refId = trial.id,
                dateKey = DayKey.today()
            )
        )
        return trial.rewardMana
    }

    @Synchronized
    fun redeemPerk(prefs: SharedPreferences, perk: Perk): Boolean {
        if (!isEnabled(prefs)) return false
        when (perk) {
            Perk.EXTRA_BREAK -> {
                if (!SessionManager.isFocusActive(prefs)) return false
                if (!breaksAllowedNow(prefs)) return false
                if (!debit(prefs, perk.costMana, perkEntry(perk))) return false
                prefs.edit {
                    putInt(
                        Constants.PrefsKeys.EXTRA_BREAK_TOKENS,
                        prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0) + 1
                    )
                }
            }
            Perk.WELL_CHANNEL -> {
                if (!ManaWell.isEnabled(prefs)) return false
                val cost = wellChannelCost(prefs)
                if (prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L) < cost) return false
                if (!ManaWell.channel(prefs, Perk.WELL_CHANNEL_GRANT)) return false
                debit(prefs, cost, perkEntry(perk, cost))
            }
        }
        return true
    }

    fun wellChannelCost(
        prefs: SharedPreferences,
        todayKey: String = DayKey.today()
    ): Long = ManaRules.pourCost(DailyHistory.wellDay(prefs, todayKey).pours)

    private fun breaksAllowedNow(prefs: SharedPreferences): Boolean {
        val scheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (scheduleId != null) {
            val schedules = PrefsHelper.loadListLenient<Schedule>(prefs, Constants.PrefsKeys.SCHEDULES)
            val schedule = schedules?.find { it.id == scheduleId }
            if (schedule != null) return schedule.breaksEnabled
        }
        return prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
    }

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)

    private fun eligibility(prefs: SharedPreferences): TrialEngine.Eligibility {
        val usagePermission = prefs.getBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, false)
        val wellApps = WellApps.load(prefs)
        return TrialEngine.Eligibility(
            hasWellApps = wellApps.isNotEmpty(),
            canMeasureProductive = usagePermission && ProductiveApps.earningPackages(prefs).isNotEmpty(),
            hasSubstitutes = wellApps.any { !it.substitutePackage.isNullOrBlank() }
        )
    }

    private fun credit(prefs: SharedPreferences, amount: Long, entry: ManaLedgerEntry) {
        if (amount <= 0L) return
        prefs.edit {
            putLong(
                Constants.PrefsKeys.MANA_BALANCE,
                prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L) + amount
            )
            putLong(
                Constants.PrefsKeys.MANA_LIFETIME_EARNED,
                prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L) + amount
            )
        }
        appendLedger(prefs, entry)
        DailyHistory.updateMana(prefs, entry.dateKey.ifEmpty { DayKey.today() }) { day ->
            when (entry.kind) {
                LedgerKind.SESSION -> day.copy(timedSession = day.timedSession + amount)
                LedgerKind.OPEN_ENDED -> day.copy(openEndedSession = day.openEndedSession + amount)
                LedgerKind.PRODUCTIVE -> day.copy(productive = day.productive + amount)
                LedgerKind.TRIAL -> day.copy(trials = day.trials + amount)
                LedgerKind.SUBSTITUTE_WIN -> day.copy(substitute = day.substitute + amount)
                else -> day
            }
        }
    }

    private fun debit(prefs: SharedPreferences, amount: Long, entry: ManaLedgerEntry): Boolean {
        val balance = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)
        if (amount <= 0L || balance < amount) return false
        prefs.edit { putLong(Constants.PrefsKeys.MANA_BALANCE, balance - amount) }
        appendLedger(prefs, entry)
        DailyHistory.updateMana(prefs, entry.dateKey.ifEmpty { DayKey.today() }) { day ->
            if (entry.refId == Perk.WELL_CHANNEL.name) day.copy(pours = day.pours + amount)
            else day.copy(breaks = day.breaks + amount)
        }
        return true
    }

    private fun appendLedger(prefs: SharedPreferences, entry: ManaLedgerEntry) {
        val ledger = loadLedger(prefs) + entry
        val pruned = if (ledger.size > Constants.MAX_MANA_LEDGER) ledger.takeLast(Constants.MAX_MANA_LEDGER) else ledger
        PrefsHelper.save(prefs, Constants.PrefsKeys.MANA_LEDGER, pruned)
    }

    private fun perkEntry(
        perk: Perk,
        cost: Long = perk.costMana
    ) = ManaLedgerEntry(
        timestampMillis = System.currentTimeMillis(),
        amount = -cost,
        kind = LedgerKind.PERK,
        refId = perk.name,
        dateKey = DayKey.today()
    )

    internal fun loadTrials(prefs: SharedPreferences): List<Trial> =
        PrefsHelper.loadListLenient<Trial>(prefs, Constants.PrefsKeys.TRIALS).orEmpty()

    private fun saveTrials(prefs: SharedPreferences, trials: List<Trial>) {
        PrefsHelper.save(prefs, Constants.PrefsKeys.TRIALS, trials)
    }

    internal fun loadLedger(prefs: SharedPreferences): List<ManaLedgerEntry> =
        PrefsHelper.loadListLenient<ManaLedgerEntry>(prefs, Constants.PrefsKeys.MANA_LEDGER).orEmpty()
}
