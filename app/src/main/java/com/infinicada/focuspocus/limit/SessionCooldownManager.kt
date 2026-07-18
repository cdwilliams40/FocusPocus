package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.AppTimeLimit
import java.util.Calendar
import java.util.Locale

/**
 * Manages per-app session cooldowns and the escalating friction state that goes with them.
 *
 * A "usage session" starts when the user brings a time-limited app to the foreground and ends
 * when they switch away from it. Once the user has spent [AppTimeLimit.sessionLimitMinutes]
 * continuously in an app the app is placed into a cooldown block for
 * [AppTimeLimit.cooldownMinutes] minutes (optionally escalating on repeated offences in the
 * same day). Every attempt to open the app while in cooldown increments [CooldownState.attemptCount],
 * which drives the [FrictionLevel] shown on the overlay.
 *
 * Cooldown state is persisted in SharedPreferences so it survives service restarts. In-memory
 * session-start timestamps are ephemeral and reset on service restart (acceptable — users get a
 * fresh session window after a crash/reboot).
 */
class SessionCooldownManager(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val tag = "SessionCooldownManager"

    /** Epoch millis when each time-limited package last came to the foreground. */
    private val sessionStartTimes = mutableMapOf<String, Long>()

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /** Call when [packageName] comes to the foreground. Records session start if not already tracking. */
    fun onAppForegrounded(packageName: String, now: Long = System.currentTimeMillis()) {
        if (!sessionStartTimes.containsKey(packageName)) {
            sessionStartTimes[packageName] = now
            Log.d(tag, "Session started for $packageName at $now")
        }
    }

    /** Call when [packageName] leaves the foreground. Clears in-session start time. */
    fun onAppLeft(packageName: String) {
        sessionStartTimes.remove(packageName)
        Log.d(tag, "Session ended for $packageName")
    }

    /** Elapsed minutes since the current session for [packageName] started, or 0 if not tracked. */
    fun getInSessionMinutes(packageName: String, now: Long = System.currentTimeMillis()): Int {
        val startMs = sessionStartTimes[packageName] ?: return 0
        return ((now - startMs) / 1000 / 60).toInt()
    }

    // -------------------------------------------------------------------------
    // Cooldown checks
    // -------------------------------------------------------------------------

    /** Returns true if [packageName] is currently in a cooldown block. */
    fun isInCooldown(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = getCooldownState(packageName, now) ?: return false
        return state.cooldownExpiryMillis > now
    }

    /**
     * Returns the active [CooldownState] for [packageName], or null if no cooldown is active.
     *
     * Expired entries are deliberately kept in prefs: [startCooldown] reads
     * [CooldownState.cooldownNumber] off them to escalate repeat offences within
     * the same day. They are pruned by [resetDailyCooldowns] at the daily rollover.
     */
    fun getCooldownState(packageName: String, now: Long = System.currentTimeMillis()): CooldownState? {
        val state = loadCooldownStates()[packageName] ?: return null
        return if (state.cooldownExpiryMillis <= now) null else state
    }

    /**
     * Read-only view of every currently-active cooldown, for UI-side readers.
     * Expired entries are filtered but never written back — like every accessor
     * here, pruning is left to [resetDailyCooldowns] at the daily rollover.
     */
    fun peekActiveCooldowns(now: Long = System.currentTimeMillis()): Map<String, CooldownState> =
        loadCooldownStates().filterValues { it.cooldownExpiryMillis > now }

    /**
     * Starts a new cooldown for [packageName] using [config] to determine duration/escalation.
     * Also resets the in-session start time so the next visit counts as a fresh session.
     */
    fun startCooldown(packageName: String, config: AppTimeLimit, now: Long = System.currentTimeMillis()) {
        val states = loadCooldownStates().toMutableMap()
        val existing = states[packageName]
        val cooldownNumber = (existing?.cooldownNumber ?: 0) + 1

        val baseDuration = config.cooldownMinutes.toLong() * 60 * 1000
        val escalationExtra = if (config.cooldownEscalationEnabled) {
            (cooldownNumber - 1) * config.cooldownEscalationStepMinutes.toLong() * 60 * 1000
        } else 0L
        val totalDurationMs = baseDuration + escalationExtra

        val newState = CooldownState(
            packageName = packageName,
            cooldownExpiryMillis = now + totalDurationMs,
            attemptCount = 0,
            cooldownNumber = cooldownNumber
        )
        states[packageName] = newState
        saveCooldownStates(states)

        // Clear in-session tracking so returning later starts a fresh session
        sessionStartTimes.remove(packageName)

        val cooldownMins = totalDurationMs / 1000 / 60
        Log.d(tag, "Cooldown #$cooldownNumber started for $packageName: ${cooldownMins}m")
    }

    /**
     * Seals [packageName] for its base cooldown length without counting a daily
     * offence: the "seal everything now" panic action is the user *choosing*
     * protection, so it must neither escalate nor consume an escalation step.
     * The existing [CooldownState.cooldownNumber] is preserved untouched for the
     * day's later real offences. No-op semantics on duration: always the base
     * [AppTimeLimit.cooldownMinutes], never the escalated length.
     */
    fun startPanicSeal(packageName: String, config: AppTimeLimit, now: Long = System.currentTimeMillis()) {
        val states = loadCooldownStates().toMutableMap()
        val existingNumber = states[packageName]?.cooldownNumber ?: 0
        states[packageName] = CooldownState(
            packageName = packageName,
            cooldownExpiryMillis = now + config.cooldownMinutes.toLong() * 60 * 1000,
            attemptCount = 0,
            cooldownNumber = existingNumber
        )
        saveCooldownStates(states)
        sessionStartTimes.remove(packageName)
        Log.d(tag, "Panic seal started for $packageName: ${config.cooldownMinutes}m")
    }

    /**
     * Increments the attempt count for [packageName]'s current cooldown and returns
     * the updated count. Returns 1 if no state exists (first attempt).
     */
    fun recordAttempt(packageName: String, now: Long = System.currentTimeMillis()): Int {
        val states = loadCooldownStates().toMutableMap()
        val existing = states[packageName] ?: return 1
        if (existing.cooldownExpiryMillis <= now) return 1

        val updated = existing.copy(attemptCount = existing.attemptCount + 1)
        states[packageName] = updated
        saveCooldownStates(states)
        Log.d(tag, "Attempt #${updated.attemptCount} for $packageName during cooldown")
        return updated.attemptCount
    }

    /**
     * Ends [packageName]'s cooldown immediately and forgets its in-session start
     * time so the next visit counts as a fresh session. Used by the sealed-minutes
     * perk, where the user pays mana to re-enter a sealed app before its cooldown
     * lapses. The entry itself is kept (expired) so the day's escalation counter
     * still applies to the next offence.
     */
    fun clearCooldown(packageName: String) {
        val states = loadCooldownStates()
        val state = states[packageName]
        if (state != null && state.cooldownExpiryMillis > 0) {
            saveCooldownStates(states + (packageName to state.copy(cooldownExpiryMillis = 0)))
            Log.d(tag, "Cooldown cleared for $packageName (perk)")
        }
        sessionStartTimes.remove(packageName)
    }

    /**
     * Daily rollover: prunes expired cooldown entries (their escalation counters
     * only matter within the day they were earned) and resets the
     * [CooldownState.cooldownNumber] counters on still-active cooldowns so
     * escalation counts start fresh. Call when the calendar date rolls over.
     */
    fun resetDailyCooldowns(now: Long = System.currentTimeMillis()) {
        val states = loadCooldownStates()
        val reset = states
            .filterValues { it.cooldownExpiryMillis > now }
            .mapValues { (_, s) -> s.copy(cooldownNumber = 0) } // active cooldown survives, but counter resets
        if (reset != states) saveCooldownStates(reset)
        Log.d(tag, "Daily cooldown counters reset (${states.size - reset.size} expired entries pruned)")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun loadCooldownStates(): Map<String, CooldownState> {
        val type = object : TypeToken<Map<String, CooldownState>>() {}.type
        return PrefsHelper.load(prefs, gson, Constants.PrefsKeys.APP_COOLDOWN_STATES, type)
            ?: emptyMap()
    }

    private fun saveCooldownStates(states: Map<String, CooldownState>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.APP_COOLDOWN_STATES, states)
    }

    companion object {
        /** Returns the cooldown minutes remaining from the given [cooldownState] relative to [now]. */
        fun minutesRemaining(cooldownState: CooldownState, now: Long = System.currentTimeMillis()): Int {
            val remainingMs = cooldownState.cooldownExpiryMillis - now
            return if (remainingMs <= 0) 0 else (remainingMs / 1000 / 60).toInt() + 1
        }

        /** Detects a date change between [previousDateStr] and now. [previousDateStr] format: "yyyyMMdd". */
        fun isNewDay(previousDateStr: String?): Boolean {
            return previousDateStr != todayString()
        }

        fun todayString(): String {
            val cal = Calendar.getInstance()
            // Locale.ROOT: the default locale's number system (e.g. Persian) would
            // emit non-ASCII digits, and these keys are parsed downstream by
            // SimpleDateFormat(Locale.US) and LocalDate.parse(BASIC_ISO_DATE), both
            // of which reject them. Matches TrialEngine.weekKeyForDay.
            return String.format(
                Locale.ROOT, "%04d%02d%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
