package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import kotlinx.serialization.encodeToString

class PactManager(
    private val prefs: SharedPreferences
) {
    private val tag = "PactManager"

    fun grantAllowance(packageName: String, minutes: Int, now: Long = System.currentTimeMillis()) =
        grantAllowances(setOf(packageName), minutes, now)

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

    fun getAllowanceExpiry(packageName: String, now: Long = System.currentTimeMillis()): Long? {
        val expiry = loadAllowances()[packageName] ?: return null
        return if (expiry > now) expiry else null
    }

    fun getActiveAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> =
        loadAllowances().filterValues { it > now }

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

    fun revokeAllowance(packageName: String) = revokeAllowances(setOf(packageName))

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

    /**
     * Revokes every pact and returns the ones still running at [now], in one
     * step under the lock, so a pact granted meanwhile is either returned
     * (and refunded by the caller) or left running, never revoked unseen.
     */
    fun takeAllActiveAllowances(now: Long = System.currentTimeMillis()): Map<String, Long> {
        synchronized(lock) {
            val allowances = loadAllowances()
            if (allowances.isEmpty()) return emptyMap()
            saveAllowances(emptyMap())
            Log.d(tag, "Revoked all ${allowances.size} pact allowance(s)")
            return allowances.filterValues { it > now }
        }
    }

    fun revokeAllAllowances() {
        synchronized(lock) {
            val allowances = loadAllowances()
            if (allowances.isEmpty()) return
            saveAllowances(emptyMap())
            Log.d(tag, "Revoked all ${allowances.size} active pact allowance(s)")
        }
    }

    private fun loadAllowances(): Map<String, Long> {
        val json = prefs.getString(Constants.PrefsKeys.PACT_ALLOWANCES, null) ?: return emptyMap()
        cachedAllowancesJson?.let { if (it == json) return cachedAllowances }
        val parsed = PrefsHelper.load<Map<String, Long>>(prefs, Constants.PrefsKeys.PACT_ALLOWANCES)
            ?: emptyMap()
        cachedAllowancesJson = json
        cachedAllowances = parsed
        return parsed
    }

    private fun saveAllowances(allowances: Map<String, Long>) {
        val json = AppJson.json.encodeToString(allowances)
        prefs.edit { putString(Constants.PrefsKeys.PACT_ALLOWANCES, json) }
        cachedAllowancesJson = json
        cachedAllowances = allowances
    }

    companion object {
        private val lock = Any()
        @Volatile private var cachedAllowancesJson: String? = null
        @Volatile private var cachedAllowances: Map<String, Long> = emptyMap()
    }
}
