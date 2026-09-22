package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
     * One package's seal, as [startSeals] takes them.
     *
     * [anchorMillis] is when the seal starts counting — for a lapsed pact that
     * is the moment the allowance expired, not the moment it was discovered, so
     * a user who walks away mid-pact isn't met with a fresh full-length seal
     * hours later.
     */
    data class SealRequest(
        val packageName: String,
        val config: AppTimeLimit,
        val anchorMillis: Long,
        /**
         * True for a seal the user earned (a spent session limit, a lapsed
         * pact): it consumes an escalation step and lengthens the next one.
         * False for a seal the user *chose* — the panic "seal everything now"
         * action and Group Seal's shared seal — which must neither escalate nor
         * spend a step, leaving the day's counter for later real offences.
         */
        val countsAsOffence: Boolean
    )

    /**
     * Starts a new cooldown for [packageName] using [config] to determine duration/escalation.
     * Also resets the in-session start time so the next visit counts as a fresh session.
     */
    fun startCooldown(packageName: String, config: AppTimeLimit, now: Long = System.currentTimeMillis()) =
        startSeals(listOf(SealRequest(packageName, config, now, countsAsOffence = true)))

    /**
     * Seals [packageName] for its base cooldown length without counting a daily
     * offence — see [SealRequest.countsAsOffence]. No-op semantics on duration:
     * always the base [AppTimeLimit.cooldownMinutes], never the escalated length.
     */
    fun startPanicSeal(packageName: String, config: AppTimeLimit, now: Long = System.currentTimeMillis()) =
        startSeals(listOf(SealRequest(packageName, config, now, countsAsOffence = false)))

    /**
     * Applies every seal in [requests] in a single store write. Both the panic
     * action and Group Seal's lapse seal every pact-gated app at once, and the
     * latter runs on the accessibility service's minute tick — one write per
     * app there re-serializes the whole cooldown store per app on the service's
     * main thread, which is an ANR the user experiences as blocking silently
     * switching off.
     */
    fun startSeals(requests: List<SealRequest>) {
        if (requests.isEmpty()) return
        val states = loadCooldownStates().toMutableMap()
        for (request in requests) {
            states[request.packageName] = sealStateFor(request, states[request.packageName])
            // Clear in-session tracking so returning later starts a fresh session
            sessionStartTimes.remove(request.packageName)
        }
        saveCooldownStates(states)
        Log.d(tag, "Sealed ${requests.size} app(s)")
    }

    /**
     * The one definition of a seal's duration and escalation bookkeeping,
     * shared by every entry point above.
     */
    private fun sealStateFor(request: SealRequest, existing: CooldownState?): CooldownState {
        val previousNumber = existing?.cooldownNumber ?: 0
        val config = request.config
        val baseDuration = config.cooldownMinutes.toLong() * 60 * 1000
        if (!request.countsAsOffence) {
            return CooldownState(
                packageName = request.packageName,
                cooldownExpiryMillis = request.anchorMillis + baseDuration,
                attemptCount = 0,
                cooldownNumber = previousNumber
            )
        }
        val cooldownNumber = previousNumber + 1
        val escalationExtra = if (config.cooldownEscalationEnabled) {
            (cooldownNumber - 1) * config.cooldownEscalationStepMinutes.toLong() * 60 * 1000
        } else 0L
        return CooldownState(
            packageName = request.packageName,
            cooldownExpiryMillis = request.anchorMillis + baseDuration + escalationExtra,
            attemptCount = 0,
            cooldownNumber = cooldownNumber
        )
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
        val json = prefs.getString(Constants.PrefsKeys.APP_COOLDOWN_STATES, null) ?: return emptyMap()
        cachedStatesJson?.let { if (it == json) return cachedStates }
        val type = object : TypeToken<Map<String, CooldownState>>() {}.type
        val parsed = PrefsHelper.load<Map<String, CooldownState>>(
            prefs, gson, Constants.PrefsKeys.APP_COOLDOWN_STATES, type
        ) ?: emptyMap()
        cachedStatesJson = json
        cachedStates = parsed
        return parsed
    }

    private fun saveCooldownStates(states: Map<String, CooldownState>) {
        val json = gson.toJson(states)
        prefs.edit { putString(Constants.PrefsKeys.APP_COOLDOWN_STATES, json) }
        cachedStatesJson = json
        cachedStates = states
    }

    companion object {
        // Parsed-store cache, shared across instances (the service, ViewModels,
        // and the Warden sync each construct their own manager). A blocked open
        // attempt reads this store three times in a row on the accessibility
        // service's main thread (isInCooldown → getCooldownState →
        // recordAttempt); keying on the raw stored JSON collapses those to one
        // parse. A racing writer just costs one redundant parse.
        @Volatile private var cachedStatesJson: String? = null
        @Volatile private var cachedStates: Map<String, CooldownState> = emptyMap()
        /**
         * Whole cooldown minutes remaining on [cooldownState] relative to [now],
         * rounded up (floor-plus-one over-reported exact minutes: 60 s left read "2").
         */
        fun minutesRemaining(cooldownState: CooldownState, now: Long = System.currentTimeMillis()): Int {
            val remainingMs = cooldownState.cooldownExpiryMillis - now
            return if (remainingMs <= 0) 0 else ((remainingMs + 59_999) / 60_000).toInt()
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
