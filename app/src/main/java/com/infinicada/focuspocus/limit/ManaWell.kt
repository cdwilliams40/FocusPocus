package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.Constants

/**
 * The Mana Well's terms: one pool of minutes shared by every Well app
 * ([WellApps]).
 */
data class WellSettings(
    val enabled: Boolean = false,
    /** The most the Well can hold, in minutes — the largest single binge. */
    val capacityMinutes: Int = WellMath.DEFAULT_CAPACITY_MINUTES,
    /**
     * Real minutes it takes the Well to regain one minute. 0 = no passive
     * refill: the Well only fills when mana is channeled into it.
     */
    val refillIntervalMinutes: Int = WellMath.DEFAULT_REFILL_INTERVAL_MINUTES
)

/** A loosening of the Well's terms, waiting out its cooling-off. */
data class WellPendingRevision(
    val settings: WellSettings,
    val appliesAtMillis: Long
)

/** What the Settings card shows: the enforced terms and any queued loosening. */
data class WellSettingsState(
    val enforced: WellSettings = WellSettings(),
    val pending: WellPendingRevision? = null
) {
    /** The terms the user is steering towards — what the controls display. */
    val target: WellSettings get() = pending?.settings ?: enforced
}

/**
 * A read of the Well at [takenAtMillis]. The level keeps refilling after the
 * read, so displays ask [levelMillisAt] with their own clock instead of
 * re-reading the store every second.
 */
data class WellSnapshot(
    val enabled: Boolean = false,
    val capacityMinutes: Int = WellMath.DEFAULT_CAPACITY_MINUTES,
    val refillIntervalMinutes: Int = WellMath.DEFAULT_REFILL_INTERVAL_MINUTES,
    val levelMillis: Long = 0L,
    val takenAtMillis: Long = 0L,
    /** Net minutes drawn from the Well today, across every pact app. */
    val drawnTodayMinutes: Int = 0
) {
    fun levelMillisAt(now: Long): Long =
        WellMath.levelAt(levelMillis, takenAtMillis, now, capacityMinutes, refillIntervalMinutes)

    /** Whole minutes that can be drawn at [now]. */
    fun availableMinutesAt(now: Long): Int = (levelMillisAt(now) / WellMath.MINUTE_MS).toInt()

    /**
     * Millis from [now] until the Well holds [targetMinutes]: 0 when it
     * already does, null when it never will on its own (no passive refill,
     * or more than the capacity).
     */
    fun millisUntilMinutes(targetMinutes: Int, now: Long): Long? =
        WellMath.millisUntilLevel(
            levelMillisAt(now), targetMinutes, capacityMinutes, refillIntervalMinutes
        )
}

/**
 * Pure Well arithmetic, free of Android types so it unit-tests like
 * GuardStatus and ProgressionMath. Every tuning constant lives here.
 */
object WellMath {
    const val MINUTE_MS = 60_000L

    const val DEFAULT_CAPACITY_MINUTES = 30
    const val MIN_CAPACITY_MINUTES = 10
    const val MAX_CAPACITY_MINUTES = 60

    const val DEFAULT_REFILL_INTERVAL_MINUTES = 20
    const val MIN_REFILL_INTERVAL_MINUTES = 10
    const val MAX_REFILL_INTERVAL_MINUTES = 60

    /** Loosening the Well waits 24 hours, like taking an app off it. */
    const val REVISION_DELAY_MS = 24L * 60 * 60 * 1000

    /**
     * The level at [now], given the level stored at [storedAtMillis]: the
     * Well regains one minute per [refillIntervalMinutes] real minutes, up to
     * its capacity. A clock that moved backwards counts as no time passing.
     */
    fun levelAt(
        storedLevelMillis: Long,
        storedAtMillis: Long,
        now: Long,
        capacityMinutes: Int,
        refillIntervalMinutes: Int
    ): Long {
        val capacityMillis = capacityMinutes * MINUTE_MS
        val base = storedLevelMillis.coerceIn(0L, capacityMillis)
        if (refillIntervalMinutes <= 0) return base
        val elapsed = (now - storedAtMillis).coerceAtLeast(0L)
        // Divide before adding: a store untouched for months must saturate
        // at the capacity, not overflow on the way there.
        val gained = elapsed / refillIntervalMinutes
        return if (gained >= capacityMillis - base) capacityMillis else base + gained
    }

    /**
     * Millis until a Well at [levelMillis] holds [targetMinutes]. 0 when it
     * already does; null when passive refill can never get it there.
     */
    fun millisUntilLevel(
        levelMillis: Long,
        targetMinutes: Int,
        capacityMinutes: Int,
        refillIntervalMinutes: Int
    ): Long? {
        val missing = targetMinutes * MINUTE_MS - levelMillis
        if (missing <= 0L) return 0L
        if (refillIntervalMinutes <= 0 || targetMinutes > capacityMinutes) return null
        return missing * refillIntervalMinutes
    }

    /**
     * Unused time to give back when pact allowances end early. [revoked]
     * holds the expiries of the Well-paid allowances being ended;
     * [stillRunning] those of every allowance left running.
     *
     * Each distinct expiry is one draw, refunded once, and only when nothing
     * from that same draw stays open (pacts granted together share an
     * expiry).
     */
    fun refundableMillis(
        revoked: Collection<Long>,
        stillRunning: Collection<Long>,
        now: Long
    ): Long {
        val running = stillRunning.toSet()
        return revoked.toSet().filter { it > now && it !in running }.sumOf { it - now }
    }

    /** A wait in whole minutes, rounded up — "in 1 min" for anything under a minute. */
    fun wholeMinutesCeil(millis: Long): Int =
        if (millis <= 0L) 0 else ((millis + MINUTE_MS - 1) / MINUTE_MS).toInt()

    /**
     * A pact's minute choices, limited to what the Well can pay for. Empty
     * means the Well is too low for even the smallest choice.
     */
    fun limitChoices(choices: List<Int>, availableMinutes: Int): List<Int> =
        choices.filter { it <= availableMinutes }

    /** Clamps stored or requested terms into their supported ranges. */
    fun sanitize(settings: WellSettings): WellSettings = settings.copy(
        capacityMinutes = settings.capacityMinutes
            .coerceIn(MIN_CAPACITY_MINUTES, MAX_CAPACITY_MINUTES),
        refillIntervalMinutes = if (settings.refillIntervalMinutes <= 0) {
            0
        } else {
            settings.refillIntervalMinutes
                .coerceIn(MIN_REFILL_INTERVAL_MINUTES, MAX_REFILL_INTERVAL_MINUTES)
        }
    )

    /** The part of a requested change that applies now, and the part that waits. */
    data class Split(val immediate: WellSettings, val pending: WellSettings?)

    /**
     * Splits [requested] against the [current] terms, field by field:
     * tightening applies at once, loosening (turning the Well off, a larger
     * capacity, a faster refill) waits. A Well that isn't on has nothing to
     * hold, so any terms apply immediately — including turning it on.
     */
    fun split(current: WellSettings, requested: WellSettings): Split {
        val wanted = sanitize(requested)
        if (!current.enabled) return Split(wanted, null)
        val immediate = WellSettings(
            enabled = true,
            capacityMinutes = minOf(current.capacityMinutes, wanted.capacityMinutes),
            refillIntervalMinutes =
                slowerRefill(current.refillIntervalMinutes, wanted.refillIntervalMinutes)
        )
        return Split(immediate, if (wanted == immediate) null else wanted)
    }

    /**
     * True when [candidate] loosens any term relative to [reference]. Used to
     * decide whether re-requesting restarts a queued change's clock: asking
     * for less than what is already queued keeps the earlier date.
     */
    fun loosensAny(candidate: WellSettings, reference: WellSettings): Boolean =
        (reference.enabled && !candidate.enabled) ||
            candidate.capacityMinutes > reference.capacityMinutes ||
            refillRank(candidate.refillIntervalMinutes) < refillRank(reference.refillIntervalMinutes)

    /** The slower of two refill intervals; 0 (no refill at all) is the slowest. */
    private fun slowerRefill(a: Int, b: Int): Int = if (refillRank(a) >= refillRank(b)) a else b

    /** Orders intervals by strictness: a longer interval ranks higher, "off" highest. */
    private fun refillRank(intervalMinutes: Int): Int =
        if (intervalMinutes <= 0) Int.MAX_VALUE else intervalMinutes
}

/**
 * The Mana Well's store: the single monitor for every change to the pool and
 * its terms, in the style of [com.infinicada.focuspocus.Progression]. The
 * accessibility service, the overlay and the view models all share one
 * process, so `@Synchronized` here is what keeps two grant paths from both
 * spending the same minutes.
 *
 * The level is never ticked. It is stored with the instant it was written
 * and computed on read ([WellMath.levelAt]); the store is rewritten only by a
 * mutation, so reads can't accumulate rounding loss.
 *
 * Everything is a primitive pref — no Gson, so nothing here needs an R8 keep
 * rule.
 */
object ManaWell {
    private const val TAG = "ManaWell"

    /** The enforced terms. Writes through a queued loosening that has come due. */
    @Synchronized
    fun settings(
        prefs: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): WellSettings {
        applyDueRevision(prefs, now)
        return readSettings(prefs)
    }

    fun isEnabled(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        settings(prefs, now).enabled

    /** The enforced terms plus any queued loosening, for the Settings card. */
    @Synchronized
    fun settingsState(
        prefs: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): WellSettingsState {
        applyDueRevision(prefs, now)
        return WellSettingsState(readSettings(prefs), readPending(prefs))
    }

    @Synchronized
    fun snapshot(
        prefs: SharedPreferences,
        now: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today()
    ): WellSnapshot {
        applyDueRevision(prefs, now)
        val settings = readSettings(prefs)
        return WellSnapshot(
            enabled = settings.enabled,
            capacityMinutes = settings.capacityMinutes,
            refillIntervalMinutes = settings.refillIntervalMinutes,
            levelMillis = currentLevel(prefs, settings, now),
            takenAtMillis = now,
            drawnTodayMinutes = (drawnTodayMillis(prefs, todayKey) / WellMath.MINUTE_MS).toInt()
        )
    }

    /**
     * Takes [minutes] out of the Well. False — and nothing changes — when it
     * doesn't hold that much; the caller must then refuse the pact.
     */
    @Synchronized
    fun tryDraw(
        prefs: SharedPreferences,
        minutes: Int,
        now: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today(),
        /** The app the pact is for, for the day's per-app record. */
        packageName: String? = null
    ): Boolean {
        if (minutes <= 0) return false
        applyDueRevision(prefs, now)
        val level = currentLevel(prefs, readSettings(prefs), now)
        val cost = minutes * WellMath.MINUTE_MS
        if (level < cost) return false
        val drawn = drawnTodayMillis(prefs, todayKey) + cost
        prefs.edit {
            putLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, level - cost)
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
            putString(Constants.PrefsKeys.WELL_DRAWN_DATE, todayKey)
            putLong(Constants.PrefsKeys.WELL_DRAWN_MILLIS, drawn)
        }
        DailyHistory.updateWell(prefs, todayKey) { day ->
            val perApp = day.perApp.orEmpty().toMutableMap()
            if (packageName != null) perApp[packageName] = (perApp[packageName] ?: 0L) + cost
            day.copy(drawnMillis = day.drawnMillis + cost, draws = day.draws + 1, perApp = perApp)
        }
        Log.d(TAG, "Drew ${minutes}m from the Well")
        return true
    }

    /**
     * Gives [millis] of unused pact time back (a pact ended early), up to
     * the capacity, and takes it off today's drawn total — the day's number
     * is what was actually spent, not what was first asked for.
     */
    @Synchronized
    fun refund(
        prefs: SharedPreferences,
        millis: Long,
        now: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today()
    ) {
        if (millis <= 0L) return
        applyDueRevision(prefs, now)
        val settings = readSettings(prefs)
        val level = currentLevel(prefs, settings, now)
        val drawn = (drawnTodayMillis(prefs, todayKey) - millis).coerceAtLeast(0L)
        prefs.edit {
            putLong(
                Constants.PrefsKeys.WELL_LEVEL_MILLIS,
                (level + millis).coerceAtMost(settings.capacityMinutes * WellMath.MINUTE_MS)
            )
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
            putString(Constants.PrefsKeys.WELL_DRAWN_DATE, todayKey)
            putLong(Constants.PrefsKeys.WELL_DRAWN_MILLIS, drawn)
        }
        DailyHistory.updateWell(prefs, todayKey) { day ->
            day.copy(
                drawnMillis = (day.drawnMillis - millis).coerceAtLeast(0L),
                refundedMillis = day.refundedMillis + millis
            )
        }
        Log.d(TAG, "Refunded ${millis / 1000}s to the Well")
    }

    /**
     * Pours [minutes] into the Well (channeled mana). All or nothing: false
     * when there isn't room for the whole amount, so nothing paid for is
     * ever lost to the capacity.
     */
    @Synchronized
    fun channel(
        prefs: SharedPreferences,
        minutes: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (minutes <= 0) return false
        applyDueRevision(prefs, now)
        val settings = readSettings(prefs)
        val level = currentLevel(prefs, settings, now)
        val added = minutes * WellMath.MINUTE_MS
        if (level + added > settings.capacityMinutes * WellMath.MINUTE_MS) return false
        prefs.edit {
            putLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, level + added)
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
        }
        DailyHistory.updateWell(prefs, DayKey.today()) { it.copy(pours = it.pours + 1) }
        Log.d(TAG, "Channeled ${minutes}m into the Well")
        return true
    }

    /** Notes a Well app opened while the Well held too little for a pact. */
    @Synchronized
    fun recordDry(prefs: SharedPreferences, todayKey: String = DayKey.today()) {
        DailyHistory.updateWell(prefs, todayKey) { it.copy(dryBlocks = it.dryBlocks + 1) }
    }

    /**
     * Requests new terms. Whatever tightens applies now; whatever loosens is
     * queued for [WellMath.REVISION_DELAY_MS], replacing any earlier request.
     * Requesting the enforced terms again withdraws a queued change. The
     * queued change keeps its original date unless the new request loosens
     * further than what was already waiting.
     */
    @Synchronized
    fun requestSettings(
        prefs: SharedPreferences,
        requested: WellSettings,
        now: Long = System.currentTimeMillis()
    ): WellSettingsState {
        applyDueRevision(prefs, now)
        val current = readSettings(prefs)
        val split = WellMath.split(current, requested)
        val previous = readPending(prefs)
        val pending = split.pending?.let { target ->
            val keepsClock = previous != null && !WellMath.loosensAny(target, previous.settings)
            WellPendingRevision(
                settings = target,
                appliesAtMillis = if (keepsClock) previous!!.appliesAtMillis
                else now + WellMath.REVISION_DELAY_MS
            )
        }
        val level = when {
            // The Well stays off: its terms are free to change, and the pool
            // is left alone so fiddling with them can't drain or fill it.
            !split.immediate.enabled -> null
            // Turning on: whatever the pool held, settled under the new terms.
            !current.enabled -> currentLevel(prefs, split.immediate, now)
            // Already on: settle at the old terms first — the minutes refilled
            // so far were earned at the old rate. A smaller capacity clamps
            // them below.
            else -> currentLevel(prefs, current, now)
        }
        prefs.edit {
            writeSettings(this, split.immediate)
            writePending(this, pending)
            if (level != null) {
                putLong(
                    Constants.PrefsKeys.WELL_LEVEL_MILLIS,
                    level.coerceAtMost(split.immediate.capacityMinutes * WellMath.MINUTE_MS)
                )
                putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
            }
        }
        return WellSettingsState(split.immediate, pending)
    }

    /** Withdraws a queued loosening; the enforced terms simply stay. */
    @Synchronized
    fun cancelPendingRevision(prefs: SharedPreferences) {
        prefs.edit { writePending(this, null) }
    }

    /**
     * What a backup restore must write so it can't loosen an enforced Well:
     * the terms to enforce and the loosening to queue, computed exactly as
     * [requestSettings] would. Null when the Well isn't on — then there is
     * nothing to hold and the restore is a plain replace.
     */
    @Synchronized
    fun holdForRestore(
        prefs: SharedPreferences,
        incoming: WellSettings,
        now: Long = System.currentTimeMillis()
    ): WellSettingsState? {
        applyDueRevision(prefs, now)
        val current = readSettings(prefs)
        if (!current.enabled) return null
        val split = WellMath.split(current, incoming)
        return WellSettingsState(
            enforced = split.immediate,
            pending = split.pending?.let {
                WellPendingRevision(it, now + WellMath.REVISION_DELAY_MS)
            }
        )
    }

    /** Writes [state] through [editor]; the restore's half of [holdForRestore]. */
    fun writeState(editor: SharedPreferences.Editor, state: WellSettingsState) {
        writeSettings(editor, state.enforced)
        writePending(editor, state.pending)
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Applies a queued loosening once its date has passed — at that date,
     * not at discovery: the level is settled at the old terms up to the
     * moment the new ones took over, and refills at the new rate from there.
     */
    private fun applyDueRevision(prefs: SharedPreferences, now: Long) {
        val pending = readPending(prefs) ?: return
        if (pending.appliesAtMillis > now) return
        val old = readSettings(prefs)
        val levelAtSwitch = currentLevel(prefs, old, pending.appliesAtMillis)
        prefs.edit {
            writeSettings(this, pending.settings)
            writePending(this, null)
            putLong(
                Constants.PrefsKeys.WELL_LEVEL_MILLIS,
                levelAtSwitch.coerceAtMost(pending.settings.capacityMinutes * WellMath.MINUTE_MS)
            )
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, pending.appliesAtMillis)
        }
        Log.d(TAG, "Queued Well terms applied")
    }

    /** A Well that has never been written starts full. */
    private fun currentLevel(prefs: SharedPreferences, settings: WellSettings, now: Long): Long {
        val capacityMillis = settings.capacityMinutes * WellMath.MINUTE_MS
        if (!prefs.contains(Constants.PrefsKeys.WELL_LEVEL_MILLIS)) return capacityMillis
        return WellMath.levelAt(
            storedLevelMillis = prefs.getLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, capacityMillis),
            storedAtMillis = prefs.getLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now),
            now = now,
            capacityMinutes = settings.capacityMinutes,
            refillIntervalMinutes = settings.refillIntervalMinutes
        )
    }

    private fun drawnTodayMillis(prefs: SharedPreferences, todayKey: String): Long =
        if (prefs.getString(Constants.PrefsKeys.WELL_DRAWN_DATE, null) == todayKey) {
            prefs.getLong(Constants.PrefsKeys.WELL_DRAWN_MILLIS, 0L)
        } else {
            0L
        }

    private fun readSettings(prefs: SharedPreferences): WellSettings = WellMath.sanitize(
        WellSettings(
            enabled = prefs.getBoolean(Constants.PrefsKeys.WELL_ENABLED, false),
            capacityMinutes = prefs.getInt(
                Constants.PrefsKeys.WELL_CAPACITY_MINUTES, WellMath.DEFAULT_CAPACITY_MINUTES
            ),
            refillIntervalMinutes = prefs.getInt(
                Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES,
                WellMath.DEFAULT_REFILL_INTERVAL_MINUTES
            )
        )
    )

    private fun readPending(prefs: SharedPreferences): WellPendingRevision? {
        val appliesAt = prefs.getLong(Constants.PrefsKeys.WELL_PENDING_APPLIES_AT_MILLIS, 0L)
        if (appliesAt <= 0L) return null
        return WellPendingRevision(
            settings = WellMath.sanitize(
                WellSettings(
                    enabled = prefs.getBoolean(Constants.PrefsKeys.WELL_PENDING_ENABLED, true),
                    capacityMinutes = prefs.getInt(
                        Constants.PrefsKeys.WELL_PENDING_CAPACITY_MINUTES,
                        WellMath.DEFAULT_CAPACITY_MINUTES
                    ),
                    refillIntervalMinutes = prefs.getInt(
                        Constants.PrefsKeys.WELL_PENDING_REFILL_INTERVAL_MINUTES,
                        WellMath.DEFAULT_REFILL_INTERVAL_MINUTES
                    )
                )
            ),
            appliesAtMillis = appliesAt
        )
    }

    private fun writeSettings(editor: SharedPreferences.Editor, settings: WellSettings) {
        editor.putBoolean(Constants.PrefsKeys.WELL_ENABLED, settings.enabled)
        editor.putInt(Constants.PrefsKeys.WELL_CAPACITY_MINUTES, settings.capacityMinutes)
        editor.putInt(
            Constants.PrefsKeys.WELL_REFILL_INTERVAL_MINUTES, settings.refillIntervalMinutes
        )
    }

    private fun writePending(editor: SharedPreferences.Editor, pending: WellPendingRevision?) {
        if (pending == null) {
            editor.remove(Constants.PrefsKeys.WELL_PENDING_APPLIES_AT_MILLIS)
            editor.remove(Constants.PrefsKeys.WELL_PENDING_ENABLED)
            editor.remove(Constants.PrefsKeys.WELL_PENDING_CAPACITY_MINUTES)
            editor.remove(Constants.PrefsKeys.WELL_PENDING_REFILL_INTERVAL_MINUTES)
            return
        }
        editor.putLong(Constants.PrefsKeys.WELL_PENDING_APPLIES_AT_MILLIS, pending.appliesAtMillis)
        editor.putBoolean(Constants.PrefsKeys.WELL_PENDING_ENABLED, pending.settings.enabled)
        editor.putInt(
            Constants.PrefsKeys.WELL_PENDING_CAPACITY_MINUTES, pending.settings.capacityMinutes
        )
        editor.putInt(
            Constants.PrefsKeys.WELL_PENDING_REFILL_INTERVAL_MINUTES,
            pending.settings.refillIntervalMinutes
        )
    }
}
