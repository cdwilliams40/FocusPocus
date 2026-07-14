package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.AppTimeLimit
import java.util.Calendar

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
     * Lazily removes expired cooldown entries from prefs.
     */
    fun getCooldownState(packageName: String, now: Long = System.currentTimeMillis()): CooldownState? {
        val states = loadCooldownStates()
        val state = states[packageName] ?: return null
        if (state.cooldownExpiryMillis <= now) {
            // Expired — remove lazily
            saveCooldownStates(states - packageName)
            return null
        }
        return state
    }

    /**
     * Read-only view of every currently-active cooldown. Unlike
     * [getCooldownState], expired entries are filtered but NOT pruned from
     * prefs — UI-side readers use this so they never write state the
     * enforcement service owns (pruning stays with the service's accessors).
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
     * Removes [packageName]'s cooldown outright (whether or not it has expired)
     * and forgets its in-session start time so the next visit counts as a fresh
     * session. Used by the sealed-minutes perk, where the user pays mana to
     * re-enter a sealed app before its cooldown lapses.
     */
    fun clearCooldown(packageName: String) {
        val states = loadCooldownStates()
        if (packageName in states) {
            saveCooldownStates(states - packageName)
            Log.d(tag, "Cooldown cleared for $packageName (perk)")
        }
        sessionStartTimes.remove(packageName)
    }

    /**
     * Removes all cooldown entries whose expiry has passed.
     * Call on every minute tick.
     */
    fun clearExpiredCooldowns(now: Long = System.currentTimeMillis()) {
        val states = loadCooldownStates()
        val active = states.filter { (_, s) -> s.cooldownExpiryMillis > now }
        if (active.size != states.size) {
            saveCooldownStates(active)
            Log.d(tag, "Cleared ${states.size - active.size} expired cooldown(s)")
        }
    }

    /**
     * Resets the [CooldownState.cooldownNumber] counters for all packages.
     * Call at midnight so daily escalation counts start fresh.
     */
    fun resetDailyCooldowns() {
        val states = loadCooldownStates()
        val now = System.currentTimeMillis()
        val reset = states.mapValues { (_, s) ->
            if (s.cooldownExpiryMillis <= now) s
            else s.copy(cooldownNumber = 0) // active cooldown survives, but counter resets
        }
        saveCooldownStates(reset)
        Log.d(tag, "Daily cooldown counters reset")
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
            val cal = Calendar.getInstance()
            val today = "%04d%02d%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            return previousDateStr != today
        }

        fun todayString(): String {
            val cal = Calendar.getInstance()
            return "%04d%02d%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
