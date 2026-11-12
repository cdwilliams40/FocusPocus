package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper

/**
 * Running pacts: a Well app opened for a few minutes paid from the Mana Well.
 * A pact expires wall-clock — leaving the app early does not pause it — and
 * when it runs out the app simply goes back behind the Well.
 *
 * Pacts are persisted in SharedPreferences so they survive service restarts.
 *
 * Every change is a read-modify-write of one JSON map, and the writers sit on
 * different threads (the pact screen grants off the main thread while the
 * service sweeps lapsed pacts on it), so each one runs under [lock]. Without
 * it a sweep that read the map just before a grant landed would write the
 * grant away — minutes drawn from the Well for a pact that no longer exists.
 */
class PactManager(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val tag = "PactManager"

    /** Grants [minutes] of access to [packageName], starting now. */
    fun grantAllowance(packageName: String, minutes: Int, now: Long = System.currentTimeMillis()) =
        grantAllowances(setOf(packageName), minutes, now)

    /** Grants the same [minutes] to every package in [packageNames] in one store write. */
    fun grantAllowances(
        packageNames: Set<String>,
        minutes: Int,
        now: Long = System.currentTimeMillis()
    ) {
        if (packageNames.isEmpty()) return
        val expiry = now + minutes.toLong() * 60 * 1000
        synchronized(lock) {
            saveAllowances(loadAllowances() + packageNames.associateWith { expiry })
        }
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
     * returns its expiry time. Returns null if there is no allowance or it is
     * still active.
     */
    fun takeLapsedAllowance(packageName: String, now: Long = System.currentTimeMillis()): Long? {
        synchronized(lock) {
            val allowances = loadAllowances()
            val expiry = allowances[packageName] ?: return null
            if (expiry > now) return null
            saveAllowances(allowances - packageName)
            Log.d(tag, "Pact lapsed for $packageName")
            return expiry
        }
    }

    /**
     * Take-once batch twin of [takeLapsedAllowance]: removes every allowance
     * that has already lapsed and returns package → the expiry it lapsed at.
     * One store write for the whole sweep.
     */
    fun takeLapsedAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> {
        synchronized(lock) {
            val allowances = loadAllowances()
            val lapsed = allowances.filterValues { it <= now }
            if (lapsed.isEmpty()) return emptyMap()
            saveAllowances(allowances - lapsed.keys)
            Log.d(tag, "Pact lapsed for ${lapsed.size} app(s)")
            return lapsed
        }
    }

    /**
     * Drops any allowance for [packageName], active or lapsed ("End pact").
     */
    fun revokeAllowance(packageName: String) = revokeAllowances(setOf(packageName))

    /** Batch twin of [revokeAllowance]: drops every named allowance in one write. */
    fun revokeAllowances(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        synchronized(lock) {
            val allowances = loadAllowances()
            val remaining = allowances - packageNames
            if (remaining.size == allowances.size) return
            saveAllowances(remaining)
            Log.d(tag, "Pact allowance revoked for ${allowances.size - remaining.size} app(s)")
        }
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
        // Guards every read-modify-write of the store, across instances.
        private val lock = Any()

        // Parsed-store caches, shared across instances (the service, ViewModels,
        // and the Warden sync each construct their own manager). Allowances are
        // read on every open attempt of a Well app and on every minute
        // tick. Keyed on the raw stored JSON, like
        // the service's other caches; a racing writer costs one redundant parse.
        @Volatile private var cachedAllowancesJson: String? = null
        @Volatile private var cachedAllowances: Map<String, Long> = emptyMap()
    }
}
