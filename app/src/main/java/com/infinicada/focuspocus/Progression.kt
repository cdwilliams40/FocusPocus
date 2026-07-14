package com.infinicada.focuspocus

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.limit.OpenReflexTracker
import com.infinicada.focuspocus.limit.PactManager
import com.infinicada.focuspocus.limit.SessionCooldownManager
import com.infinicada.focuspocus.model.Boon
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Schedule
import com.infinicada.focuspocus.model.Sigil
import com.infinicada.focuspocus.model.SigilCatalog
import com.infinicada.focuspocus.model.Trial

/**
 * The single monitor for every mana mutation. SessionRecorder's award path,
 * trial claims, boon/perk redemptions, and rollover auto-credits all run
 * through @Synchronized methods on this object, so balance updates can never
 * lose a write even if callers arrive from different threads.
 *
 * Reads (balance, ledger, trials...) live in ProgressionRepository; only state
 * transitions live here. Static (prefs, gson) style matches SessionRecorder.
 */
object Progression {

    data class AwardResult(
        val manaEarned: Long,
        val milestoneBonus: Long,
        val completedTrials: List<Trial>,
        val unlockedSigils: List<Sigil>
    ) {
        companion object {
            val EMPTY = AwardResult(0L, 0L, emptyList(), emptyList())
        }
    }

    /**
     * Awards everything a just-recorded session earns. Called from inside
     * SessionRecorder.record() after the session list and streak are computed
     * but before session prefs are cleared, so ACTIVE_SCHEDULE_ID and
     * HIDE_STOP_BUTTON still describe the session being awarded.
     */
    @Synchronized
    fun awardForSession(
        prefs: SharedPreferences,
        gson: Gson,
        session: FocusSession,
        newStreak: Int
    ): AwardResult {
        // Cheap activity marker for the evening wrap-up guard — written even
        // when progression is off so re-enabling doesn't miss today.
        prefs.edit()
            .putString(Constants.PrefsKeys.LAST_SESSION_RECORDED_DATE, SessionCooldownManager.todayString())
            .apply()

        rolloverIfNeeded(prefs, gson)
        if (!isEnabled(prefs)) return AwardResult.EMPTY

        val fromRitual = prefs.getString(Constants.PrefsKeys.ACTIVE_SCHEDULE_ID, null) != null
        val hideStop = prefs.getBoolean(Constants.PrefsKeys.HIDE_STOP_BUTTON, false)

        val mana = ProgressionMath.computeManaAward(session.durationMinutes, newStreak, hideStop, fromRitual)
        credit(
            prefs, gson, mana,
            ManaLedgerEntry(
                timestampMillis = session.endTimeMillis,
                amount = mana,
                kind = LedgerKind.SESSION,
                minutes = session.durationMinutes,
                dateKey = SessionCooldownManager.todayString()
            )
        )

        val (advanced, completed) = TrialEngine.advanceForSession(loadTrials(prefs, gson), session.durationMinutes, fromRitual)
        saveTrials(prefs, gson, advanced)

        val highestPaid = prefs.getInt(Constants.PrefsKeys.HIGHEST_STREAK_MILESTONE_PAID, 0)
        val (bonus, newPaid) = ProgressionMath.milestoneBonus(newStreak, highestPaid)
        if (bonus > 0) {
            credit(
                prefs, gson, bonus,
                ManaLedgerEntry(
                    timestampMillis = session.endTimeMillis,
                    amount = bonus,
                    kind = LedgerKind.MILESTONE,
                    refId = "streak_$newPaid",
                    dateKey = SessionCooldownManager.todayString()
                )
            )
            prefs.edit().putInt(Constants.PrefsKeys.HIGHEST_STREAK_MILESTONE_PAID, newPaid).apply()
        }

        val unlocked = unlockSigils(prefs, gson, sessionSigilIds(prefs, gson, session, newStreak, hideStop, fromRitual))

        return AwardResult(mana, bonus, completed, unlocked)
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
        val dailyKey = SessionCooldownManager.todayString()
        val weekKey = TrialEngine.weekKeyForDay(dailyKey)
        val trials = loadTrials(prefs, gson)
        val (active, ended) = trials.partition { !TrialEngine.isEnded(it, dailyKey, weekKey) }

        if (ended.isNotEmpty()) {
            val blockEvents = loadBlockEvents(prefs, gson)
            val reflexDays = OpenReflexTracker(prefs, gson).getDailyStats()
            val limitsConfigured = AppTimeLimitManager.getTimeLimits(prefs, gson).values.any { it > 0 }
            val enabled = isEnabled(prefs)
            for (endedTrial in ended) {
                val judged = TrialEngine.judgeEndedTrial(endedTrial, blockEvents, reflexDays, limitsConfigured)
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
                dateKey = SessionCooldownManager.todayString()
            )
        )
        return trial.rewardMana
    }

    /** Deducts a boon's cost on the honor system. False when the balance is too low. */
    @Synchronized
    fun redeemBoon(prefs: SharedPreferences, gson: Gson, boon: Boon): Boolean {
        if (!isEnabled(prefs)) return false
        if (!debit(
                prefs, gson, boon.costMana,
                ManaLedgerEntry(
                    timestampMillis = System.currentTimeMillis(),
                    amount = -boon.costMana,
                    kind = LedgerKind.BOON,
                    title = boon.title,
                    refId = boon.id,
                    dateKey = SessionCooldownManager.todayString()
                )
            )
        ) return false
        unlockSigils(prefs, gson, listOf(SigilCatalog.FIRST_BOON))
        return true
    }

    /**
     * Buys an in-app perk. EXTRA_BREAK requires an active manual session (the
     * token is session-scoped); SEALED_MINUTES requires the target package and
     * is capped at one redemption per app per day. The daily-limit precedence
     * check (a spent daily limit can't be bought back) is enforced by the UI,
     * which has the Context this method deliberately doesn't.
     */
    @Synchronized
    fun redeemPerk(prefs: SharedPreferences, gson: Gson, perk: Perk, packageName: String? = null): Boolean {
        if (!isEnabled(prefs)) return false
        when (perk) {
            Perk.EXTRA_BREAK -> {
                // A talisman can hold a session open with manual mode off, so
                // manual mode alone isn't "session active". And a token is
                // worthless when the running session disallows breaks — refuse
                // up front instead of silently burning the mana.
                val sessionActive = prefs.getBoolean(Constants.PrefsKeys.MANUAL_FOCUS_MODE, false) ||
                    prefs.getString(Constants.PrefsKeys.FOCUS_TAG_ID, null) != null
                if (!sessionActive) return false
                if (!breaksAllowedNow(prefs, gson)) return false
                if (!debit(prefs, gson, perk.costMana, perkEntry(perk, ""))) return false
                prefs.edit()
                    .putInt(
                        Constants.PrefsKeys.EXTRA_BREAK_TOKENS,
                        prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0) + 1
                    )
                    .apply()
            }
            Perk.SEALED_MINUTES -> {
                if (packageName.isNullOrEmpty()) return false
                val today = SessionCooldownManager.todayString()
                val alreadyToday = loadLedger(prefs, gson).any {
                    it.kind == LedgerKind.PERK && it.refId == perk.name &&
                        it.packageName == packageName && it.dateKey == today
                }
                if (alreadyToday) return false
                if (!debit(prefs, gson, perk.costMana, perkEntry(perk, packageName))) return false
                PactManager(prefs, gson).grantAllowance(packageName, Perk.SEALED_MINUTES_GRANT)
                SessionCooldownManager(prefs, gson).clearCooldown(packageName)
            }
        }
        return true
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

    /**
     * Idempotently unlocks sigils by id and returns the ones that are new.
     * External unlock sites (Warden provisioning) call this directly.
     */
    @Synchronized
    fun unlockSigils(prefs: SharedPreferences, gson: Gson, ids: List<String>): List<Sigil> {
        if (ids.isEmpty() || !isEnabled(prefs)) return emptyList()
        val current = loadUnlockedSigilIds(prefs, gson)
        val fresh = ids.filter { it !in current }.mapNotNull { SigilCatalog.byId(it) }
        if (fresh.isEmpty()) return emptyList()
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.UNLOCKED_SIGILS, current + fresh.map { it.id })
        return fresh
    }

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Constants.PrefsKeys.PROGRESSION_ENABLED, true)

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun sessionSigilIds(
        prefs: SharedPreferences,
        gson: Gson,
        session: FocusSession,
        newStreak: Int,
        hideStop: Boolean,
        fromRitual: Boolean
    ): List<String> {
        val ids = mutableListOf(SigilCatalog.FIRST_SPELL)
        if (fromRitual) ids += SigilCatalog.RITUAL_KEPT
        if (hideStop) ids += SigilCatalog.IRON_WILL
        if (session.durationMinutes >= 240) ids += SigilCatalog.DEEP_TRANCE
        if (newStreak >= 7) ids += SigilCatalog.STREAK_7
        if (newStreak >= 30) ids += SigilCatalog.STREAK_30
        if (newStreak >= 100) ids += SigilCatalog.STREAK_100

        val sessions = loadSessions(prefs, gson)
        if (sessions.size >= 100) ids += SigilCatalog.HUNDRED_CASTINGS
        val weekKey = TrialEngine.weekKeyForDay(SessionCooldownManager.todayString())
        val weekMinutes = sessions
            .filter { TrialEngine.weekKeyForDay(TrialEngine.dateKeyOf(it.endTimeMillis)) == weekKey }
            .sumOf { it.durationMinutes }
        if (weekMinutes >= 600) ids += SigilCatalog.TEN_HOUR_WEEK
        if (prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L) >= 1000L) ids += SigilCatalog.FONT_OF_MANA
        return ids
    }

    private fun eligibility(prefs: SharedPreferences, gson: Gson): TrialEngine.Eligibility {
        val schedules: List<Schedule> = PrefsHelper.load(
            prefs, gson, Constants.PrefsKeys.SCHEDULES,
            object : TypeToken<List<Schedule>>() {}.type
        ) ?: emptyList()
        val limitsConfigured = AppTimeLimitManager.getTimeLimits(prefs, gson).values.any { it > 0 }
        val usagePermission = prefs.getBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, false)
        val reflexHistory = OpenReflexTracker(prefs, gson).getDailyStats().isNotEmpty()
        return TrialEngine.Eligibility(
            hasSchedules = schedules.isNotEmpty(),
            canJudgeLimits = limitsConfigured && usagePermission,
            hasReflexHistory = reflexHistory
        )
    }

    private fun credit(prefs: SharedPreferences, gson: Gson, amount: Long, entry: ManaLedgerEntry) {
        if (amount <= 0L) return
        prefs.edit()
            .putLong(
                Constants.PrefsKeys.MANA_BALANCE,
                prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L) + amount
            )
            .putLong(
                Constants.PrefsKeys.MANA_LIFETIME_EARNED,
                prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L) + amount
            )
            .apply()
        appendLedger(prefs, gson, entry)
    }

    /** Deducts [amount] if the balance covers it; false otherwise. */
    private fun debit(prefs: SharedPreferences, gson: Gson, amount: Long, entry: ManaLedgerEntry): Boolean {
        val balance = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)
        if (amount <= 0L || balance < amount) return false
        prefs.edit().putLong(Constants.PrefsKeys.MANA_BALANCE, balance - amount).apply()
        appendLedger(prefs, gson, entry)
        return true
    }

    private fun appendLedger(prefs: SharedPreferences, gson: Gson, entry: ManaLedgerEntry) {
        val ledger = loadLedger(prefs, gson) + entry
        val pruned = if (ledger.size > Constants.MAX_MANA_LEDGER) ledger.takeLast(Constants.MAX_MANA_LEDGER) else ledger
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.MANA_LEDGER, pruned)
    }

    private fun perkEntry(perk: Perk, packageName: String) = ManaLedgerEntry(
        timestampMillis = System.currentTimeMillis(),
        amount = -perk.costMana,
        kind = LedgerKind.PERK,
        refId = perk.name,
        packageName = packageName,
        dateKey = SessionCooldownManager.todayString()
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

    internal fun loadUnlockedSigilIds(prefs: SharedPreferences, gson: Gson): Set<String> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.UNLOCKED_SIGILS, object : TypeToken<Set<String>>() {}.type)
            ?: emptySet()

    private fun loadSessions(prefs: SharedPreferences, gson: Gson): List<FocusSession> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.FOCUS_SESSIONS, object : TypeToken<List<FocusSession>>() {}.type)
            ?: emptyList()

    private fun loadBlockEvents(prefs: SharedPreferences, gson: Gson): List<BlockEvent> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.BLOCK_EVENTS, object : TypeToken<List<BlockEvent>>() {}.type)
            ?: emptyList()
}
