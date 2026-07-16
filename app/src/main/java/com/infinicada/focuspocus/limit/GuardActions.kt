package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import com.google.gson.Gson
import com.infinicada.focuspocus.AppTimeLimitManager
import com.infinicada.focuspocus.BlockerRepository

/**
 * Prefs-level guard actions shared by every entry point — the dashboard's
 * panic button, the home-screen widget, and anything else without a
 * ViewModel. Callers are responsible for their own UI feedback and for
 * reconciling Warden greying afterwards.
 */
object GuardActions {

    /**
     * Immediately seals every pact-gated app that isn't already sealed:
     * revokes any running allowance and starts the app's configured seal
     * cooldown, escalation rules included. Returns how many apps were sealed.
     */
    fun sealAllPacts(
        prefs: SharedPreferences,
        gson: Gson,
        now: Long = System.currentTimeMillis()
    ): Int {
        val pactManager = PactManager(prefs, gson)
        val cooldownManager = SessionCooldownManager(prefs, gson)
        val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
        val groups = pactManager.getGroups()
        val blockers = BlockerRepository.getBlockers(prefs)

        var sealed = 0
        GuardStatus.pactGatedPackages(configs, groups, blockers).forEach { pkg ->
            if (cooldownManager.isInCooldown(pkg, now)) return@forEach
            val config = GuardStatus.effectivePactConfig(pkg, configs, groups, blockers)
                ?: return@forEach
            pactManager.revokeAllowance(pkg)
            cooldownManager.startCooldown(pkg, config, now)
            sealed++
        }
        return sealed
    }

    /**
     * True when at least one pact-gated app is currently unsealed — i.e. the
     * panic button would actually do something.
     */
    fun anyPactUnsealed(
        prefs: SharedPreferences,
        gson: Gson,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val pactManager = PactManager(prefs, gson)
        val cooldownManager = SessionCooldownManager(prefs, gson)
        val configs = AppTimeLimitManager.getTimeLimitConfigs(prefs, gson)
        val gated = GuardStatus.pactGatedPackages(
            configs, pactManager.getGroups(), BlockerRepository.getBlockers(prefs)
        )
        return gated.any { !cooldownManager.isInCooldown(it, now) }
    }
}
