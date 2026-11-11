package com.infinicada.focuspocus

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.ManaWell
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.DailyHistory
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.limit.WellApps
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Trial

/**
 * The single monitor for every mana mutation. SessionRecorder's award path,
 * trial claims, perk redemptions, and rollover auto-credits all run
 * through @Synchronized methods on this object, so balance updates can never
 * lose a write even if callers arrive from different threads.
 *
 * Reads (balance, ledger, trials...) live in ProgressionRepository; only state
 * transitions live here. Static (prefs, gson) style matches SessionRecorder.
 */
object Progression {

    data class AwardResult(
        val manaEarned: Long,
        val completedTrials: List<Trial>
    ) {
        companion object {
            val EMPTY = AwardResult(0L, emptyList())
        }
    }

    /**
     * Awards everything a just-recorded session earns, by [ManaRules]: timed
     * sessions per minute up to the daily cap, open-ended ones a flat amount
     * once a day. Called from inside SessionRecorder.record().
     */
    @Synchronized
    fun awardForSession(
        prefs: SharedPreferences,
        gson: Gson,
        session: FocusSession
    ): AwardResult {
        rolloverIfNeeded(prefs, gson)
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val today = DayKey.today()
        val day = DailyHistory.manaDay(prefs, today)
        val award = ManaRules.sessionAward(
            session,
            timedEarnedToday = day.timedSession,
            openEndedPaidToday = day.openEndedSession > 0L
        )
        credit(
            prefs, gson, award.mana,
            ManaLedgerEntry(
                timestampMillis = session.endTimeMillis,
                amount = award.mana,
                kind = if (award.timed) LedgerKind.SESSION else LedgerKind.OPEN_ENDED,
                minutes = session.durationMinutes,
                dateKey = today
            )
        )

        val (advanced, completed) = TrialEngine.advanceForSession(
            loadTrials(prefs, gson),
            session.durationMinutes,
            timed = award.timed,
            startedByHand = session.source == FocusSession.SOURCE_MANUAL
        )
        saveTrials(prefs, gson, advanced)

        return AwardResult(award.mana, completed)
    }

    /**
     * Pays for today's time in productive apps: [minutesToday] is the total
     * so far, so this is safe to call as often as wanted — only the part not
     * yet paid is credited. Returns the mana added and any trials it
     * completed.
     */
    @Synchronized
    fun creditProductive(prefs: SharedPreferences, gson: Gson, minutesToday: Int): AwardResult {
        rolloverIfNeeded(prefs, gson)
        val today = DayKey.today()
        val day = DailyHistory.manaDay(prefs, today)
        if (minutesToday <= day.productiveMinutes) return AwardResult.EMPTY
        val added = minutesToday - day.productiveMinutes
        DailyHistory.updateMana(prefs, today) { it.copy(productiveMinutes = minutesToday) }
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val owed = (ManaRules.productiveMana(minutesToday) - day.productive).coerceAtLeast(0L)
        credit(
            prefs, gson, owed,
            ManaLedgerEntry(
                timestampMillis = System.currentTimeMillis(),
                amount = owed,
                kind = LedgerKind.PRODUCTIVE,
                minutes = added,
                dateKey = today
            )
        )
        val (advanced, completed) = TrialEngine.advanceForProductive(loadTrials(prefs, gson), minutesToday, added)
        saveTrials(prefs, gson, advanced)
        return AwardResult(owed, completed)
    }

    /**
     * Rotates the trial slate when a day or ISO week has ended: judges ended
     * day/week-scoped trials, auto-credits completed-but-unclaimed ones, and
     * draws the new slate deterministically. Cheap when nothing is stale.
     * Rotation itself runs even with progression disabled (a stale slate must
     * never linger); crediting does not.
     */
    @Synchronized
    fun rolloverIfNeeded(prefs: SharedPreferences, gson: Gson) {
        val dailyKey = DayKey.today()
        val weekKey = TrialEngine.weekKeyForDay(dailyKey)
        val trials = loadTrials(prefs, gson)
        val (active, ended) = trials.partition { !TrialEngine.isEnded(it, dailyKey, weekKey) }

        if (ended.isNotEmpty()) {
            val reflexDays = OpenReflexTracker(prefs, gson).getDailyStats()
            val wellMinutes = DailyHistory.wellDays(prefs)
                .mapValues { (_, day) -> (day.drawnMillis / 60_000L).toInt() }
            val enabled = isEnabled(prefs)
            for (endedTrial in ended) {
                val judged = TrialEngine.judgeEndedTrial(endedTrial, wellMinutes, reflexDays)
                if (judged.completed && !judged.claimed && enabled) {
                    credit(
                        prefs, gson, judged.rewardMana,
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

        val slate = TrialEngine.drawSlate(dailyKey, weekKey, active, eligibility(prefs, gson))
        if (slate != trials) saveTrials(prefs, gson, slate)
    }

    /** Credits a completed trial's reward. Returns the mana granted (0 if not claimable). */
    @Synchronized
    fun claimTrial(prefs: SharedPreferences, gson: Gson, trialId: String): Long {
        if (!isEnabled(prefs)) return 0L
        val trials = loadTrials(prefs, gson)
        val trial = trials.firstOrNull { it.id == trialId } ?: return 0L
        if (!trial.completed || trial.claimed) return 0L
        saveTrials(prefs, gson, trials.map { if (it.id == trialId) it.copy(claimed = true) else it })
        credit(
            prefs, gson, trial.rewardMana,
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

    /**
     * Buys an in-app perk. EXTRA_BREAK requires an active session (the token
     * is session-scoped). WELL_CHANNEL pours minutes into the Mana Well at a
     * price that doubles per channel per day.
     */
    @Synchronized
    fun redeemPerk(prefs: SharedPreferences, gson: Gson, perk: Perk): Boolean {
        if (!isEnabled(prefs)) return false
        when (perk) {
            Perk.EXTRA_BREAK -> {
                // A talisman can hold a session open with manual mode off, so
                // manual mode alone isn't "session active". And a token is
                // worthless when the running session disallows breaks — refuse
                // up front instead of silently burning the mana.
                if (!SessionManager.isFocusActive(prefs)) return false
                if (!breaksAllowedNow(prefs, gson)) return false
                if (!debit(prefs, gson, perk.costMana, perkEntry(perk))) return false
                prefs.edit {
                    putInt(
                        Constants.PrefsKeys.EXTRA_BREAK_TOKENS,
                        prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0) + 1
                    )
                }
            }
            Perk.WELL_CHANNEL -> {
                if (!ManaWell.isEnabled(prefs)) return false
                val cost = wellChannelCost(prefs, gson)
                // Balance first, then the pour, then the debit: the pour is
                // the step that can still refuse (no room for the whole
                // amount), and once it lands the debit can't fail — every
                // mana mutation runs under this monitor.
                if (prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L) < cost) return false
                if (!ManaWell.channel(prefs, Perk.WELL_CHANNEL_GRANT)) return false
                debit(prefs, gson, cost, perkEntry(perk, cost))
            }
        }
        return true
    }

    /**
     * What the next Well channel costs right now: the base price, doubled for
     * every channel already bought today (counted from the structured ledger).
     */
    fun wellChannelCost(
        prefs: SharedPreferences,
        gson: Gson,
        todayKey: String = DayKey.today()
    ): Long {
        val channelsToday = loadLedger(prefs, gson).count {
            it.kind == LedgerKind.PERK && it.refId == Perk.WELL_CHANNEL.name && it.dateKey == todayKey
        }
        return ManaRules.pourCost(channelsToday)
    }

    /**
     * Whether the running session permits breaks: the active schedule's
     * override when a ritual is running, the session toggle otherwise.
     * Mirrors the effective-breaks logic in the Home screen and the service.
     */
    private fun breaksAllowedNow(prefs: SharedPreferences, gson: Gson): Boolean {
        val scheduleId = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null)
        if (scheduleId != null) {
            val type = object : TypeToken<List<Schedule>>() {}.type
            val schedules = PrefsHelper.load<List<Schedule>>(prefs, gson, Constants.PrefsKeys.SCHEDULES, type)
            val schedule = schedules?.find { it.id == scheduleId }
            if (schedule != null) return schedule.breaksEnabled
        }
        return prefs.getBoolean(Constants.PrefsKeys.SESSION_BREAKS_ENABLED, true)
    }

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun eligibility(prefs: SharedPreferences, gson: Gson): TrialEngine.Eligibility {
        val usagePermission = prefs.getBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, false)
        return TrialEngine.Eligibility(
            hasWellApps = WellApps.packages(prefs, gson).isNotEmpty(),
            canMeasureProductive = usagePermission && ProductiveApps.load(prefs, gson).isNotEmpty(),
            hasReflexHistory = OpenReflexTracker(prefs, gson).getDailyStats().isNotEmpty()
        )
    }

    private fun credit(prefs: SharedPreferences, gson: Gson, amount: Long, entry: ManaLedgerEntry) {
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
        appendLedger(prefs, gson, entry)
        DailyHistory.updateMana(prefs, entry.dateKey.ifEmpty { DayKey.today() }) { day ->
            when (entry.kind) {
                LedgerKind.SESSION -> day.copy(timedSession = day.timedSession + amount)
                LedgerKind.OPEN_ENDED -> day.copy(openEndedSession = day.openEndedSession + amount)
                LedgerKind.PRODUCTIVE -> day.copy(productive = day.productive + amount)
                LedgerKind.TRIAL -> day.copy(trials = day.trials + amount)
                else -> day
            }
        }
    }

    /** Deducts [amount] if the balance covers it; false otherwise. */
    private fun debit(prefs: SharedPreferences, gson: Gson, amount: Long, entry: ManaLedgerEntry): Boolean {
        val balance = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)
        if (amount <= 0L || balance < amount) return false
        prefs.edit { putLong(Constants.PrefsKeys.MANA_BALANCE, balance - amount) }
        appendLedger(prefs, gson, entry)
        DailyHistory.updateMana(prefs, entry.dateKey.ifEmpty { DayKey.today() }) { day ->
            if (entry.refId == Perk.WELL_CHANNEL.name) day.copy(pours = day.pours + amount)
            else day.copy(breaks = day.breaks + amount)
        }
        return true
    }

    private fun appendLedger(prefs: SharedPreferences, gson: Gson, entry: ManaLedgerEntry) {
        val ledger = loadLedger(prefs, gson) + entry
        val pruned = if (ledger.size > Constants.MAX_MANA_LEDGER) ledger.takeLast(Constants.MAX_MANA_LEDGER) else ledger
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.MANA_LEDGER, pruned)
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

    internal fun loadTrials(prefs: SharedPreferences, gson: Gson): List<Trial> =
        (PrefsHelper.load(prefs, gson, Constants.PrefsKeys.TRIALS, object : TypeToken<List<Trial>>() {}.type)
            ?: emptyList<Trial>()).filterNotNull().filter(::isIntact)

    /**
     * True when a deserialized trial actually has values in all its non-null
     * Kotlin fields. Gson instantiates classes via Unsafe — no constructor, no
     * null checks — so records written by a build with broken R8 keep rules
     * (v1.4 persisted obfuscated enum constant and field names) can come back
     * with null enum/String fields and NPE far from the parse site, e.g.
     * TrialEngine.isEnded on trial.period. Such records are unrecoverable —
     * drop them so a fresh slate is drawn.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun isIntact(trial: Trial): Boolean =
        trial.id != null && trial.type != null && trial.period != null && trial.periodKey != null

    private fun saveTrials(prefs: SharedPreferences, gson: Gson, trials: List<Trial>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.TRIALS, trials)
    }

    internal fun loadLedger(prefs: SharedPreferences, gson: Gson): List<ManaLedgerEntry> =
        (PrefsHelper.load(prefs, gson, Constants.PrefsKeys.MANA_LEDGER, object : TypeToken<List<ManaLedgerEntry>>() {}.type)
            ?: emptyList<ManaLedgerEntry>()).filterNotNull().filter(::isIntact)

    /** Same Unsafe-null guard as the [Trial] overload — a null [ManaLedgerEntry.kind]
     *  NPEs in ledgerReason's `when` on the mana history screen. */
    @Suppress("SENSELESS_COMPARISON")
    private fun isIntact(entry: ManaLedgerEntry): Boolean =
        entry.kind != null && entry.title != null && entry.refId != null &&
            entry.packageName != null && entry.dateKey != null
}
