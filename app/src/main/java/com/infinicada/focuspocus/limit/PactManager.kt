package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
    fun grantAllowance(packageName: String, minutes: Int, now: Long = System.currentTimeMillis()) =
        grantAllowances(setOf(packageName), minutes, now)

    /**
     * Grants the same [minutes] to every package in [packageNames] in a single
     * store write. Group Seal's shared window opens every pact-gated app at
     * once, so the per-app twin would re-serialize and re-write the whole
     * allowance store once per app — on the accessibility service's main
     * thread, where that is an ANR the user experiences as blocking silently
     * switching off.
     */
    fun grantAllowances(
        packageNames: Set<String>,
        minutes: Int,
        now: Long = System.currentTimeMillis()
    ) {
        if (packageNames.isEmpty()) return
        val expiry = now + minutes.toLong() * 60 * 1000
        saveAllowances(loadAllowances() + packageNames.associateWith { expiry })
        Log.d(tag, "Pact granted for ${packageNames.size} app(s): ${minutes}m")
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

    /**
     * Take-once batch twin of [takeLapsedAllowance]: removes every allowance
     * that has already lapsed and returns package → the expiry it lapsed at, so
     * the caller can anchor each seal there. One store write for the whole
     * sweep — under Group Seal every app's shared window lapses on the same
     * minute tick, so the per-app twin would write once per app.
     */
    fun takeLapsedAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> {
        val allowances = loadAllowances()
        val lapsed = allowances.filterValues { it <= now }
        if (lapsed.isEmpty()) return emptyMap()
        saveAllowances(allowances - lapsed.keys)
        Log.d(tag, "Pact lapsed for ${lapsed.size} app(s)")
        return lapsed
    }

    /**
     * Drops any allowance for [packageName], active or lapsed, without starting
     * a seal — the panic "seal everything now" action revokes running pact time
     * and starts its own seal separately.
     */
    fun revokeAllowance(packageName: String) = revokeAllowances(setOf(packageName))

    /** Batch twin of [revokeAllowance]: drops every named allowance in one write. */
    fun revokeAllowances(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        val allowances = loadAllowances()
        val remaining = allowances - packageNames
        if (remaining.size == allowances.size) return
        saveAllowances(remaining)
        Log.d(tag, "Pact allowance revoked for ${allowances.size - remaining.size} app(s)")
    }

    // -------------------------------------------------------------------------
    // Pact groups (pact settings bound to a blacklist enchantment)
    // -------------------------------------------------------------------------

    fun getGroups(): List<PactGroup> {
        val json = prefs.getString(Constants.PrefsKeys.PACT_GROUPS, null) ?: return emptyList()
        cachedGroupsJson?.let { if (it == json) return cachedGroups }
        val type = object : TypeToken<List<PactGroup>>() {}.type
        val groups = PrefsHelper.load(prefs, gson, Constants.PrefsKeys.PACT_GROUPS, type)
            ?: emptyList<PactGroup>()
        // Gson fills fields via Unsafe, so groups stored by a build with broken
        // R8 keep rules (v1.4) can come back with a null blockerName despite the
        // non-null Kotlin type. Such groups can't be matched to an enchantment —
        // drop them instead of letting the null leak into lookups and the UI.
        @Suppress("SENSELESS_COMPARISON")
        val sanitized = groups.filterNotNull().filter { it.blockerName != null }
        cachedGroupsJson = json
        cachedGroups = sanitized
        return sanitized
    }

    /** Adds or replaces the group bound to the same enchantment. */
    fun saveGroup(group: PactGroup) {
        val updated = getGroups().filter { it.blockerName != group.blockerName } + group
        saveGroups(updated)
        Log.d(tag, "Pact group saved for enchantment ${group.blockerName}")
    }

    fun deleteGroup(blockerName: String) {
        val updated = getGroups().filter { it.blockerName != blockerName }
        saveGroups(updated)
        Log.d(tag, "Pact group removed for enchantment $blockerName")
    }

    private fun saveGroups(groups: List<PactGroup>) {
        val json = gson.toJson(groups)
        prefs.edit { putString(Constants.PrefsKeys.PACT_GROUPS, json) }
        cachedGroupsJson = json
        cachedGroups = groups
    }

    private fun loadAllowances(): Map<String, Long> {
        val json = prefs.getString(Constants.PrefsKeys.PACT_ALLOWANCES, null) ?: return emptyMap()
        cachedAllowancesJson?.let { if (it == json) return cachedAllowances }
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val parsed = PrefsHelper.load<Map<String, Long>>(
            prefs, gson, Constants.PrefsKeys.PACT_ALLOWANCES, type
        ) ?: emptyMap()
        cachedAllowancesJson = json
        cachedAllowances = parsed
        return parsed
    }

    private fun saveAllowances(allowances: Map<String, Long>) {
        val json = gson.toJson(allowances)
        prefs.edit { putString(Constants.PrefsKeys.PACT_ALLOWANCES, json) }
        cachedAllowancesJson = json
        cachedAllowances = allowances
    }

    companion object {
        // Parsed-store caches, shared across instances (the service, ViewModels,
        // and the Warden sync each construct their own manager). Allowances are
        // read on every open attempt of a pact-gated app and on every minute
        // tick; groups on every Warden sync. Keyed on the raw stored JSON, like
        // the service's other caches; a racing writer costs one redundant parse.
        @Volatile private var cachedAllowancesJson: String? = null
        @Volatile private var cachedAllowances: Map<String, Long> = emptyMap()
        @Volatile private var cachedGroupsJson: String? = null
        @Volatile private var cachedGroups: List<PactGroup> = emptyList()
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
