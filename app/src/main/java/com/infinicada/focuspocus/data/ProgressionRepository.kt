package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.Progression
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.model.Boon
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Trial

/**
 * Read surface for progression state plus thin delegates to the [Progression]
 * monitor for mutations. Takes a Context (like SessionRepository) so it can
 * snapshot the usage-stats permission for Context-free trial gating.
 */
class ProgressionRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun isEnabled(): Boolean = Progression.isEnabled(prefs)

    fun getBalance(): Long = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)

    fun getLifetimeEarned(): Long = prefs.getLong(Constants.PrefsKeys.MANA_LIFETIME_EARNED, 0L)

    fun getLedger(): List<ManaLedgerEntry> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.MANA_LEDGER,
            object : TypeToken<List<ManaLedgerEntry>>() {}.type) ?: emptyList()

    fun getBoons(): List<Boon> =
        PrefsHelper.load(prefs, gson, Constants.PrefsKeys.BOONS,
            object : TypeToken<List<Boon>>() {}.type) ?: emptyList()

    /**
     * Active trials. Refreshes the usage-permission snapshot and rotates the
     * slate first (compare-on-read), so an app that sat cached in recents
     * overnight still shows a fresh slate.
     */
    fun getTrials(): List<Trial> {
        refreshUsagePermissionSnapshot()
        Progression.rolloverIfNeeded(prefs, gson)
        return Progression.loadTrials(prefs, gson)
    }

    fun getUnlockedSigilIds(): Set<String> = Progression.loadUnlockedSigilIds(prefs, gson)

    /** Adds or replaces a boon. False when the boon cap is hit. */
    fun saveBoon(boon: Boon, currentBoons: List<Boon>): Boolean {
        val isUpdate = currentBoons.any { it.id == boon.id }
        if (!isUpdate && currentBoons.size >= Constants.MAX_BOONS) return false
        val updated = currentBoons.filterNot { it.id == boon.id } + boon
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BOONS, updated)
        return true
    }

    fun deleteBoon(boonId: String, currentBoons: List<Boon>): List<Boon> {
        val updated = currentBoons.filterNot { it.id == boonId }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BOONS, updated)
        return updated
    }

    fun redeemBoon(boon: Boon): Boolean = Progression.redeemBoon(prefs, gson, boon)

    fun redeemPerk(perk: Perk, packageName: String? = null): Boolean =
        Progression.redeemPerk(prefs, gson, perk, packageName)

    fun claimTrial(trialId: String): Long = Progression.claimTrial(prefs, gson, trialId)

    /** Extra-break tokens bought during the current session. */
    fun getExtraBreakTokens(): Int = prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0)

    /**
     * Whether a sealed-minutes redemption is still available for [packageName]
     * today (one per app per day, checked against structured ledger fields).
     */
    fun isSealedMinutesAvailableToday(packageName: String): Boolean {
        val today = com.infinicada.focuspocus.limit.SessionCooldownManager.todayString()
        return getLedger().none {
            it.kind == com.infinicada.focuspocus.model.LedgerKind.PERK &&
                it.refId == Perk.SEALED_MINUTES.name &&
                it.packageName == packageName &&
                it.dateKey == today
        }
    }

    fun refreshUsagePermissionSnapshot() {
        val granted = try {
            UsageStatsHelper.hasUsageStatsPermission(context)
        } catch (e: Exception) {
            false
        }
        prefs.edit().putBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, granted).apply()
    }
}
