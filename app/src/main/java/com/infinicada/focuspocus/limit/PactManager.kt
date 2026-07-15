package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.PactGroup

/**
 * Manages Pact Mode allowances.
 *
 * A pact-gated app ([AppTimeLimit.pactModeEnabled]) is blocked by default at all
 * times. When the user opens it, the pact overlay offers a choice of allowance
 * durations; picking one grants an allowance that expires [grantAllowance]'s
 * minutes later, wall-clock — leaving the app early does not pause it. When the
 * allowance lapses, the caller seals the app by starting a cooldown *anchored at
 * the allowance's expiry time* (not at discovery time), so a user who walks away
 * mid-pact isn't punished with a fresh full-length seal when they come back hours
 * later.
 *
 * Allowances are persisted in SharedPreferences so they survive service restarts.
 */
class PactManager(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val tag = "PactManager"

    /** Grants [minutes] of access to [packageName], starting now. */
    fun grantAllowance(packageName: String, minutes: Int, now: Long = System.currentTimeMillis()) {
        val allowances = loadAllowances().toMutableMap()
        allowances[packageName] = now + minutes.toLong() * 60 * 1000
        saveAllowances(allowances)
        Log.d(tag, "Pact granted for $packageName: ${minutes}m")
    }

    /** Epoch millis when [packageName]'s active allowance expires, or null if none is active. */
    fun getAllowanceExpiry(packageName: String, now: Long = System.currentTimeMillis()): Long? {
        val expiry = loadAllowances()[packageName] ?: return null
        return if (expiry > now) expiry else null
    }

    /**
     * Read-only batch view: every currently-active allowance (package →
     * expiry epoch millis). One load of the store instead of one per package;
     * lapsed entries are filtered but never removed (that is
     * [takeLapsedAllowance]'s job, on the enforcement side).
     */
    fun getActiveAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> =
        loadAllowances().filterValues { it > now }

    /**
     * Read-only batch view of the lapsed side: every allowance that has already
     * expired (package → expiry epoch millis). The enforcement layer walks this
     * on its minute tick to seal apps proactively — under Warden greying the
     * user can't reopen a suspended app, so the open-attempt path that used to
     * trigger [takeLapsedAllowance] lazily never runs.
     */
    fun getLapsedAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> =
        loadAllowances().filterValues { it <= now }

    /**
     * If [packageName] has an allowance that has already lapsed, removes it and
     * returns its expiry time so the caller can start the seal cooldown anchored
     * there. Returns null if there is no allowance or it is still active.
     */
    fun takeLapsedAllowance(packageName: String, now: Long = System.currentTimeMillis()): Long? {
        val allowances = loadAllowances()
        val expiry = allowances[packageName] ?: return null
        if (expiry > now) return null
        saveAllowances(allowances - packageName)
        Log.d(tag, "Pact lapsed for $packageName")
        return expiry
    }

    // -------------------------------------------------------------------------
    // Pact groups (pact settings bound to a blacklist enchantment)
    // -------------------------------------------------------------------------

    fun getGroups(): List<PactGroup> {
        val type = object : TypeToken<List<PactGroup>>() {}.type
        val groups = PrefsHelper.load(prefs, gson, Constants.PrefsKeys.PACT_GROUPS, type)
            ?: emptyList<PactGroup>()
        // Gson fills fields via Unsafe, so groups stored by a build with broken
        // R8 keep rules (v1.4) can come back with a null blockerName despite the
        // non-null Kotlin type. Such groups can't be matched to an enchantment —
        // drop them instead of letting the null leak into lookups and the UI.
        @Suppress("SENSELESS_COMPARISON")
        return groups.filterNotNull().filter { it.blockerName != null }
    }

    /** Adds or replaces the group bound to the same enchantment. */
    fun saveGroup(group: PactGroup) {
        val updated = getGroups().filter { it.blockerName != group.blockerName } + group
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.PACT_GROUPS, updated)
        Log.d(tag, "Pact group saved for enchantment ${group.blockerName}")
    }

    fun deleteGroup(blockerName: String) {
        val updated = getGroups().filter { it.blockerName != blockerName }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.PACT_GROUPS, updated)
        Log.d(tag, "Pact group removed for enchantment $blockerName")
    }

    private fun loadAllowances(): Map<String, Long> {
        val type = object : TypeToken<Map<String, Long>>() {}.type
        return PrefsHelper.load(prefs, gson, Constants.PrefsKeys.PACT_ALLOWANCES, type)
            ?: emptyMap()
    }

    private fun saveAllowances(allowances: Map<String, Long>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.PACT_ALLOWANCES, allowances)
    }

    companion object {
        /** Candidate allowance durations offered on the pact overlay, in minutes. */
        val DEFAULT_CHOICES = listOf(2, 5, 10, 15, 30)

        /**
         * Fallback for configs whose pactMaxMinutes is non-positive (pre-field
         * data Gson fills with 0). Public so UI summaries and prefills quote
         * the same ladder cap the overlay actually offers.
         */
        const val DEFAULT_MAX_MINUTES = 15

        /**
         * The allowance choices to offer for [config]: the default ladder capped at
         * [AppTimeLimit.pactMaxMinutes]. A non-positive max (e.g. configs persisted
         * before this field existed, which Gson deserializes as 0) falls back to
         * 15 minutes.
         */
        fun choicesFor(config: AppTimeLimit): List<Int> {
            val max = if (config.pactMaxMinutes > 0) config.pactMaxMinutes else DEFAULT_MAX_MINUTES
            val choices = DEFAULT_CHOICES.filter { it <= max }
            return choices.ifEmpty { listOf(max) }
        }
    }
}
