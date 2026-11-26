package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.model.PactIntention
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Why passive Well refill is currently paused, if it is. */
enum class RefillPauseReason {
    NONE,
    QUIET_HOURS,
    MORNING_FOCUS
}

/**
 * Optional "No Morning Windfall" gates that pause passive Well refill during
 * quiet hours and/or until a qualifying morning focus session completes.
 */
data class WellRefillGate(
    val quietHoursEnabled: Boolean = false,
    /** Minutes since midnight when quiet hours start (default 22:00 = 1320). */
    val quietStartMinute: Int = 22 * 60,
    /** Minutes since midnight when quiet hours end (default 07:00 = 420). */
    val quietEndMinute: Int = 7 * 60,
    val morningFocusRequired: Boolean = false,
    /** Minimum focus session length in minutes to unlock today's passive refill. */
    val morningFocusMinutes: Int = 10,
    /** "yyyyMMdd" of the day morning focus was last satisfied. */
    val morningUnlockedDate: String? = null
) {
    val isActive: Boolean get() = quietHoursEnabled || morningFocusRequired
}

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

/** A loosening of the refill gate, waiting out the same cooling-off as the terms. */
data class WellGatePendingRevision(
    val gate: WellRefillGate,
    val appliesAtMillis: Long
)

/** The enforced refill gate and any queued loosening of it, for the Settings card. */
data class WellGateState(
    val enforced: WellRefillGate = WellRefillGate(),
    val pending: WellGatePendingRevision? = null
) {
    /** What the controls display: the gate the user is steering towards. */
    val target: WellRefillGate get() = pending?.gate ?: enforced
}

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
    val drawnTodayMinutes: Int = 0,
    val refillGate: WellRefillGate = WellRefillGate()
) {
    fun levelMillisAt(now: Long): Long =
        WellMath.levelAt(levelMillis, takenAtMillis, now, capacityMinutes, refillIntervalMinutes, refillGate)

    /** Whole minutes that can be drawn at [now]. */
    fun availableMinutesAt(now: Long): Int = (levelMillisAt(now) / WellMath.MINUTE_MS).toInt()

    /** Why passive refill is paused right now, or [RefillPauseReason.NONE]. */
    fun refillPauseReasonAt(now: Long): RefillPauseReason =
        WellMath.pauseReasonAt(now, refillGate)

    /**
     * Millis from [now] until the Well holds [targetMinutes]: 0 when it
     * already does, null when it never will on its own (no passive refill,
     * morning focus still required, or more than the capacity).
     */
    fun millisUntilMinutes(targetMinutes: Int, now: Long): Long? {
        if (refillPauseReasonAt(now) == RefillPauseReason.MORNING_FOCUS) return null
        return WellMath.millisUntilLevel(
            levelMillisAt(now), targetMinutes, capacityMinutes, refillIntervalMinutes
        )
    }
}

/**
 * Pure Well arithmetic, free of Android types so it unit-tests without a
 * device. Every tuning constant lives here.
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
        refillIntervalMinutes: Int,
        gate: WellRefillGate = WellRefillGate(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val capacityMillis = capacityMinutes * MINUTE_MS
        val base = storedLevelMillis.coerceIn(0L, capacityMillis)
        if (refillIntervalMinutes <= 0) return base
        val elapsed = effectiveRefillElapsedMillis(storedAtMillis, now, gate, zoneId)
        // Divide before adding: a store untouched for months must saturate
        // at the capacity, not overflow on the way there.
        val gained = elapsed / refillIntervalMinutes
        return if (gained >= capacityMillis - base) capacityMillis else base + gained
    }

    /**
     * Returns whether passive refill is currently paused at [nowMillis] due to
     * morning focus gating or quiet hours.
     */
    fun pauseReasonAt(
        nowMillis: Long,
        gate: WellRefillGate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RefillPauseReason {
        if (!gate.isActive) return RefillPauseReason.NONE
        val zdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val dayKey = zdt.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
        if (gate.morningFocusRequired && gate.morningUnlockedDate != dayKey) {
            return RefillPauseReason.MORNING_FOCUS
        }
        if (gate.quietHoursEnabled && isInQuietHours(zdt.hour * 60 + zdt.minute, gate.quietStartMinute, gate.quietEndMinute)) {
            return RefillPauseReason.QUIET_HOURS
        }
        return RefillPauseReason.NONE
    }

    fun isInQuietHours(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    /**
     * Real milliseconds between [fromMillis] and [toMillis] during which passive
     * refill is allowed (outside quiet hours, and on days where morning focus
     * is satisfied when [WellRefillGate.morningFocusRequired] is enabled).
     */
    fun effectiveRefillElapsedMillis(
        fromMillis: Long,
        toMillis: Long,
        gate: WellRefillGate = WellRefillGate(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (toMillis <= fromMillis) return 0L
        if (!gate.isActive) return toMillis - fromMillis

        val startZdt = Instant.ofEpochMilli(fromMillis).atZone(zoneId)
        val endZdt = Instant.ofEpochMilli(toMillis).atZone(zoneId)
        var day = startZdt.toLocalDate()
        val endDay = endZdt.toLocalDate()
        // Cap walking to 30 days: even at the slowest refill rate, 30 days of
        // waking hours easily saturates the maximum 60-minute Well.
        val maxEndDay = day.plusDays(30)
        val walkEndDay = if (endDay.isAfter(maxEndDay)) maxEndDay else endDay

        var totalActiveMs = 0L
        while (!day.isAfter(walkEndDay)) {
            val dayKey = day.format(DateTimeFormatter.BASIC_ISO_DATE)
            val morningBlocked = gate.morningFocusRequired && gate.morningUnlockedDate != dayKey
            if (!morningBlocked) {
                val dayStartMs = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val nextDayStartMs = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val segStart = maxOf(fromMillis, dayStartMs)
                val segEnd = minOf(toMillis, nextDayStartMs)
                if (segEnd > segStart) {
                    var activeMs = segEnd - segStart
                    if (gate.quietHoursEnabled && gate.quietStartMinute != gate.quietEndMinute) {
                        val qStart = gate.quietStartMinute.coerceIn(0, 1439)
                        val qEnd = gate.quietEndMinute.coerceIn(0, 1439)
                        if (qStart < qEnd) {
                            activeMs -= overlapMillis(
                                segStart, segEnd,
                                dayStartMs + qStart * MINUTE_MS,
                                dayStartMs + qEnd * MINUTE_MS
                            )
                        } else {
                            // Overnight quiet window: [00:00, qEnd) and [qStart, 24:00)
                            activeMs -= overlapMillis(
                                segStart, segEnd,
                                dayStartMs,
                                dayStartMs + qEnd * MINUTE_MS
                            )
                            activeMs -= overlapMillis(
                                segStart, segEnd,
                                dayStartMs + qStart * MINUTE_MS,
                                nextDayStartMs
                            )
                        }
                    }
                    totalActiveMs += activeMs.coerceAtLeast(0L)
                }
            }
            day = day.plusDays(1)
        }
        return totalActiveMs
    }

    private fun overlapMillis(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
        val s = maxOf(aStart, bStart)
        val e = minOf(aEnd, bEnd)
        return (e - s).coerceAtLeast(0L)
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

    /** The part of a requested gate change that applies now, and the part that waits. */
    data class GateSplit(val immediate: WellRefillGate, val pending: WellRefillGate?)

    /**
     * Splits a requested refill gate against the [current] one, like [split]:
     * turning a gate on, widening quiet hours or asking for a longer morning
     * session applies at once; turning one off, narrowing or moving quiet
     * hours, or a shorter morning session waits. With the Well off
     * ([wellEnabled] false) nothing is held. The morning unlock date is
     * bookkeeping, not a term, and always carries over from [current].
     */
    fun splitGate(
        current: WellRefillGate,
        requested: WellRefillGate,
        wellEnabled: Boolean
    ): GateSplit {
        val wanted = sanitizeGate(requested).copy(morningUnlockedDate = current.morningUnlockedDate)
        if (!wellEnabled) return GateSplit(wanted, null)
        val quietNow = !current.quietHoursEnabled ||
            (wanted.quietHoursEnabled && quietWindowCovers(wanted, current))
        val morningNow = !current.morningFocusRequired ||
            (wanted.morningFocusRequired && wanted.morningFocusMinutes >= current.morningFocusMinutes)
        val immediate = current.copy(
            quietHoursEnabled = if (quietNow) wanted.quietHoursEnabled else current.quietHoursEnabled,
            quietStartMinute = if (quietNow) wanted.quietStartMinute else current.quietStartMinute,
            quietEndMinute = if (quietNow) wanted.quietEndMinute else current.quietEndMinute,
            morningFocusRequired = if (morningNow) wanted.morningFocusRequired else current.morningFocusRequired,
            morningFocusMinutes = if (morningNow) wanted.morningFocusMinutes else current.morningFocusMinutes
        )
        return GateSplit(immediate, if (sameTerms(wanted, immediate)) null else wanted)
    }

    /** True when [candidate] loosens any gate term relative to [reference]. */
    fun gateLoosensAny(candidate: WellRefillGate, reference: WellRefillGate): Boolean =
        (reference.quietHoursEnabled &&
            !(candidate.quietHoursEnabled && quietWindowCovers(candidate, reference))) ||
            (reference.morningFocusRequired &&
                !(candidate.morningFocusRequired &&
                    candidate.morningFocusMinutes >= reference.morningFocusMinutes))

    fun sanitizeGate(gate: WellRefillGate): WellRefillGate = gate.copy(
        quietStartMinute = gate.quietStartMinute.coerceIn(0, 1439),
        quietEndMinute = gate.quietEndMinute.coerceIn(0, 1439),
        morningFocusMinutes = gate.morningFocusMinutes.coerceIn(5, 60)
    )

    private fun sameTerms(a: WellRefillGate, b: WellRefillGate): Boolean =
        a.copy(morningUnlockedDate = null) == b.copy(morningUnlockedDate = null)

    /** Every quiet minute of [inner] is also quiet in [outer]. */
    private fun quietWindowCovers(outer: WellRefillGate, inner: WellRefillGate): Boolean =
        (0 until 24 * 60).all { minute ->
            !isInQuietHours(minute, inner.quietStartMinute, inner.quietEndMinute) ||
                isInQuietHours(minute, outer.quietStartMinute, outer.quietEndMinute)
        }

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
        val gate = readRefillGate(prefs)
        return WellSnapshot(
            enabled = settings.enabled,
            capacityMinutes = settings.capacityMinutes,
            refillIntervalMinutes = settings.refillIntervalMinutes,
            levelMillis = currentLevel(prefs, settings, now),
            takenAtMillis = now,
            drawnTodayMinutes = (drawnTodayMillis(prefs, todayKey) / WellMath.MINUTE_MS).toInt(),
            refillGate = gate
        )
    }

    /** The enforced quiet-hours & morning-focus refill gate. */
    @Synchronized
    fun refillGate(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): WellRefillGate {
        applyDueRevision(prefs, now)
        return readRefillGate(prefs)
    }

    /** The enforced refill gate plus any queued loosening, for the Settings card. */
    @Synchronized
    fun refillGateState(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): WellGateState {
        applyDueRevision(prefs, now)
        return WellGateState(readRefillGate(prefs), readGatePending(prefs))
    }

    /**
     * Asks for new quiet-hours and morning-focus settings. Like
     * [requestSettings], tightening applies now and loosening waits
     * [WellMath.REVISION_DELAY_MS]; the level is settled under the old gate
     * first. Returns what is now enforced and queued.
     */
    @Synchronized
    fun updateRefillGate(
        prefs: SharedPreferences,
        quietHoursEnabled: Boolean,
        quietStartMinute: Int,
        quietEndMinute: Int,
        morningFocusRequired: Boolean,
        morningFocusMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): WellGateState {
        applyDueRevision(prefs, now)
        val settings = readSettings(prefs)
        val current = readRefillGate(prefs)
        val requested = current.copy(
            quietHoursEnabled = quietHoursEnabled,
            quietStartMinute = quietStartMinute,
            quietEndMinute = quietEndMinute,
            morningFocusRequired = morningFocusRequired,
            morningFocusMinutes = morningFocusMinutes
        )
        val split = WellMath.splitGate(current, requested, settings.enabled)
        val previous = readGatePending(prefs)
        val pending = split.pending?.let { target ->
            val keepsClock = previous != null && !WellMath.gateLoosensAny(target, previous.gate)
            WellGatePendingRevision(
                gate = target,
                appliesAtMillis = if (keepsClock) previous.appliesAtMillis
                else now + WellMath.REVISION_DELAY_MS
            )
        }
        val settledLevel = currentLevel(prefs, settings, now)
        prefs.edit {
            putLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, settledLevel)
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
            writeGate(this, split.immediate)
            writeGatePending(this, pending)
        }
        return WellGateState(split.immediate, pending)
    }

    /** Withdraws a queued loosening of the refill gate; the enforced gate stays. */
    @Synchronized
    fun cancelPendingGateRevision(prefs: SharedPreferences) {
        prefs.edit { writeGatePending(this, null) }
    }

    /**
     * Called when a focus session finishes: if it meets the morning-focus threshold,
     * settles the Well at [now] first (so pre-focus morning hours don't retroactively
     * count) and unlocks passive refill for [todayKey].
     */
    @Synchronized
    fun onFocusSessionRecorded(
        prefs: SharedPreferences,
        durationMinutes: Int,
        now: Long = System.currentTimeMillis(),
        todayKey: String = DayKey.today()
    ) {
        val gate = readRefillGate(prefs)
        if (!gate.morningFocusRequired) return
        if (durationMinutes < gate.morningFocusMinutes) return
        if (gate.morningUnlockedDate == todayKey) return

        val settings = readSettings(prefs)
        val settledLevel = currentLevel(prefs, settings, now)
        prefs.edit {
            putLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, settledLevel)
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, now)
            putString(Constants.PrefsKeys.WELL_MORNING_UNLOCK_DATE, todayKey)
        }
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
        packageName: String? = null,
        /** Optional 1-tap intention chosen on the pact screen. */
        intention: PactIntention? = null
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
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        DailyHistory.updateWell(prefs, todayKey) { day ->
            val perApp = day.perApp.orEmpty().toMutableMap()
            if (packageName != null) perApp[packageName] = (perApp[packageName] ?: 0L) + cost
            val intentionsMap = day.intentions.orEmpty().toMutableMap()
            if (intention != null) {
                intentionsMap[intention.name] = (intentionsMap[intention.name] ?: 0) + 1
            }
            val byHour = day.drawsByHour.orEmpty().toMutableMap()
            byHour[hour] = (byHour[hour] ?: 0) + minutes
            day.copy(
                drawnMillis = day.drawnMillis + cost,
                draws = day.draws + 1,
                perApp = perApp,
                intentions = intentionsMap.ifEmpty { null },
                drawsByHour = byHour
            )
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
                appliesAtMillis = if (keepsClock) previous.appliesAtMillis
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

    /**
     * The refill-gate half of [holdForRestore]: what a restore must write so
     * it can't loosen the gate of an enforced Well. Null when the Well isn't
     * on, and the restore is a plain replace.
     */
    @Synchronized
    fun holdGateForRestore(
        prefs: SharedPreferences,
        incoming: WellRefillGate,
        now: Long = System.currentTimeMillis()
    ): WellGateState? {
        applyDueRevision(prefs, now)
        if (!readSettings(prefs).enabled) return null
        val split = WellMath.splitGate(readRefillGate(prefs), incoming, wellEnabled = true)
        return WellGateState(
            enforced = split.immediate,
            pending = split.pending?.let {
                WellGatePendingRevision(it, now + WellMath.REVISION_DELAY_MS)
            }
        )
    }

    /** Writes [state] through [editor]; the restore's half of [holdGateForRestore]. */
    fun writeGateState(editor: SharedPreferences.Editor, state: WellGateState) {
        writeGate(editor, state.enforced)
        writeGatePending(editor, state.pending)
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
        val termsAt = readPending(prefs)?.appliesAtMillis?.takeIf { it <= now }
        val gateAt = readGatePending(prefs)?.appliesAtMillis?.takeIf { it <= now }
        // Apply in date order, so each settles the level under the terms that
        // were really in force up to its own date.
        if (gateAt != null && (termsAt == null || gateAt <= termsAt)) {
            applyDueGateRevision(prefs, now)
            applyDueTermsRevision(prefs, now)
        } else {
            applyDueTermsRevision(prefs, now)
            applyDueGateRevision(prefs, now)
        }
    }

    private fun applyDueGateRevision(prefs: SharedPreferences, now: Long) {
        val pending = readGatePending(prefs) ?: return
        if (pending.appliesAtMillis > now) return
        val levelAtSwitch = currentLevel(prefs, readSettings(prefs), pending.appliesAtMillis)
        prefs.edit {
            writeGate(this, pending.gate)
            writeGatePending(this, null)
            putLong(Constants.PrefsKeys.WELL_LEVEL_MILLIS, levelAtSwitch)
            putLong(Constants.PrefsKeys.WELL_UPDATED_AT_MILLIS, pending.appliesAtMillis)
        }
        Log.d(TAG, "Queued refill gate applied")
    }

    private fun applyDueTermsRevision(prefs: SharedPreferences, now: Long) {
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
            refillIntervalMinutes = settings.refillIntervalMinutes,
            gate = readRefillGate(prefs)
        )
    }

    private fun readRefillGate(prefs: SharedPreferences): WellRefillGate = WellRefillGate(
        quietHoursEnabled = prefs.getBoolean(Constants.PrefsKeys.WELL_QUIET_HOURS_ENABLED, false),
        quietStartMinute = prefs.getInt(Constants.PrefsKeys.WELL_QUIET_START_MINUTE, 22 * 60),
        quietEndMinute = prefs.getInt(Constants.PrefsKeys.WELL_QUIET_END_MINUTE, 7 * 60),
        morningFocusRequired = prefs.getBoolean(Constants.PrefsKeys.WELL_MORNING_FOCUS_REQUIRED, false),
        morningFocusMinutes = prefs.getInt(Constants.PrefsKeys.WELL_MORNING_FOCUS_MINUTES, 10),
        morningUnlockedDate = prefs.getString(Constants.PrefsKeys.WELL_MORNING_UNLOCK_DATE, null)
    )

    /** Writes the gate's terms; the morning unlock date is left alone. */
    private fun writeGate(editor: SharedPreferences.Editor, gate: WellRefillGate) {
        val g = WellMath.sanitizeGate(gate)
        editor.putBoolean(Constants.PrefsKeys.WELL_QUIET_HOURS_ENABLED, g.quietHoursEnabled)
        editor.putInt(Constants.PrefsKeys.WELL_QUIET_START_MINUTE, g.quietStartMinute)
        editor.putInt(Constants.PrefsKeys.WELL_QUIET_END_MINUTE, g.quietEndMinute)
        editor.putBoolean(Constants.PrefsKeys.WELL_MORNING_FOCUS_REQUIRED, g.morningFocusRequired)
        editor.putInt(Constants.PrefsKeys.WELL_MORNING_FOCUS_MINUTES, g.morningFocusMinutes)
    }

    private fun readGatePending(prefs: SharedPreferences): WellGatePendingRevision? {
        val appliesAt = prefs.getLong(Constants.PrefsKeys.WELL_GATE_PENDING_APPLIES_AT_MILLIS, 0L)
        if (appliesAt <= 0L) return null
        val enforced = readRefillGate(prefs)
        return WellGatePendingRevision(
            gate = WellMath.sanitizeGate(
                enforced.copy(
                    quietHoursEnabled = prefs.getBoolean(
                        Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_HOURS_ENABLED, enforced.quietHoursEnabled
                    ),
                    quietStartMinute = prefs.getInt(
                        Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_START_MINUTE, enforced.quietStartMinute
                    ),
                    quietEndMinute = prefs.getInt(
                        Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_END_MINUTE, enforced.quietEndMinute
                    ),
                    morningFocusRequired = prefs.getBoolean(
                        Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_REQUIRED, enforced.morningFocusRequired
                    ),
                    morningFocusMinutes = prefs.getInt(
                        Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_MINUTES, enforced.morningFocusMinutes
                    )
                )
            ),
            appliesAtMillis = appliesAt
        )
    }

    private fun writeGatePending(editor: SharedPreferences.Editor, pending: WellGatePendingRevision?) {
        if (pending == null) {
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_APPLIES_AT_MILLIS)
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_HOURS_ENABLED)
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_START_MINUTE)
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_END_MINUTE)
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_REQUIRED)
            editor.remove(Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_MINUTES)
            return
        }
        val g = pending.gate
        editor.putLong(Constants.PrefsKeys.WELL_GATE_PENDING_APPLIES_AT_MILLIS, pending.appliesAtMillis)
        editor.putBoolean(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_HOURS_ENABLED, g.quietHoursEnabled)
        editor.putInt(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_START_MINUTE, g.quietStartMinute)
        editor.putInt(Constants.PrefsKeys.WELL_GATE_PENDING_QUIET_END_MINUTE, g.quietEndMinute)
        editor.putBoolean(Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_REQUIRED, g.morningFocusRequired)
        editor.putInt(Constants.PrefsKeys.WELL_GATE_PENDING_MORNING_FOCUS_MINUTES, g.morningFocusMinutes)
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
