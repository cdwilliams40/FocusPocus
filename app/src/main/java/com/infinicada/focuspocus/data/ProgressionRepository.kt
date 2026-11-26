package com.infinicada.focuspocus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.Progression
import com.infinicada.focuspocus.UsageStatsHelper
import com.infinicada.focuspocus.limit.DailyHistory
import com.infinicada.focuspocus.limit.DayKey
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Perk
import com.infinicada.focuspocus.model.Trial

class ProgressionRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    fun isEnabled(): Boolean = Progression.isEnabled(prefs)

    fun getBalance(): Long = prefs.getLong(Constants.PrefsKeys.MANA_BALANCE, 0L)

    fun getLedger(): List<ManaLedgerEntry> = Progression.loadLedger(prefs)

    fun getManaToday(): ManaDay = DailyHistory.manaDay(prefs, DayKey.today())

    fun getTrials(): List<Trial> {
        refreshUsagePermissionSnapshot()
        Progression.rolloverIfNeeded(prefs)
        return Progression.loadTrials(prefs)
    }

    fun redeemPerk(perk: Perk): Boolean = Progression.redeemPerk(prefs, perk)

    fun claimTrial(trialId: String): Long = Progression.claimTrial(prefs, trialId)

    fun getWellChannelCost(): Long = Progression.wellChannelCost(prefs)

    fun getExtraBreakTokens(): Int = prefs.getInt(Constants.PrefsKeys.EXTRA_BREAK_TOKENS, 0)

    fun refreshUsagePermissionSnapshot() {
        val granted = try {
            UsageStatsHelper.hasUsageStatsPermission(context)
        } catch (_: Exception) {
            false
        }
        prefs.edit { putBoolean(Constants.PrefsKeys.USAGE_PERMISSION_SNAPSHOT, granted) }
    }
}
