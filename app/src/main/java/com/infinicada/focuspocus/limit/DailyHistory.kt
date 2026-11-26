package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.WellDay
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-day records keyed "yyyyMMdd", pruned to [RETENTION_DAYS].
 */
object DailyHistory {
    const val RETENTION_DAYS = 60L

    fun wellDays(prefs: SharedPreferences): Map<String, WellDay> =
        PrefsHelper.load<Map<String, WellDay?>>(prefs, Constants.PrefsKeys.WELL_HISTORY)
            .orEmpty()
            .mapNotNull { (k, v) -> if (v != null) k to v else null }
            .toMap()

    fun manaDays(prefs: SharedPreferences): Map<String, ManaDay> =
        PrefsHelper.load<Map<String, ManaDay?>>(prefs, Constants.PrefsKeys.MANA_HISTORY)
            .orEmpty()
            .mapNotNull { (k, v) -> if (v != null) k to v else null }
            .toMap()

    fun wellDay(prefs: SharedPreferences, day: String): WellDay = wellDays(prefs)[day] ?: WellDay()
    fun manaDay(prefs: SharedPreferences, day: String): ManaDay = manaDays(prefs)[day] ?: ManaDay()

    fun updateWell(prefs: SharedPreferences, day: String, change: (WellDay) -> WellDay) {
        val days = wellDays(prefs).toMutableMap()
        days[day] = change(days[day] ?: WellDay())
        prefs.edit {
            putString(Constants.PrefsKeys.WELL_HISTORY, AppJson.json.encodeToString(prune(days, day)))
        }
    }

    fun updateMana(prefs: SharedPreferences, day: String, change: (ManaDay) -> ManaDay) {
        val days = manaDays(prefs).toMutableMap()
        days[day] = change(days[day] ?: ManaDay())
        prefs.edit {
            putString(Constants.PrefsKeys.MANA_HISTORY, AppJson.json.encodeToString(prune(days, day)))
        }
    }

    private fun <T> prune(days: Map<String, T>, today: String): Map<String, T> {
        val cutoff = try {
            LocalDate.parse(today, DateTimeFormatter.BASIC_ISO_DATE)
                .minusDays(RETENTION_DAYS - 1)
                .format(DateTimeFormatter.BASIC_ISO_DATE)
        } catch (_: Exception) {
            return days
        }
        return days.filterKeys { it >= cutoff }
    }
}
