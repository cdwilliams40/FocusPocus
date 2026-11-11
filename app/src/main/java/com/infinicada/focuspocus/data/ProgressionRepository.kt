package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.Progression
import com.infinicada.focuspocus.UsageStatsHelper
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

    fun getLedger(): List<ManaLedgerEntry> = Progression.loadLedger(prefs, gson)

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

    fun redeemPerk(perk: Perk): Boolean = Progression.redeemPerk(prefs, gson, perk)

    fun claimTrial(trialId: String): Long = Progression.claimTrial(prefs, gson, trialId)

    /** Mana price of the next Well channel today (doubles per channel per day). */
    fun getWellChannelCost(): Long = Progression.wellChannelCost(prefs, gson)

    /** Extra-break tokens bought during the current session. */
    fun getExtraBreakTokens(): Int = prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0)

    fun refreshUsagePermissionSnapshot() {
        val granted = try {
            UsageStatsHelper.hasUsageStatsPermission(context)
        } catch (e: Exception) {
            false
        }
        prefs.edit { putBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, granted) }
    }
}
