package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.ManaDay
import com.infinicada.focuspocus.model.WellDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-day records keyed "yyyyMMdd", pruned to [RETENTION_DAYS]. Writers hold
 * their own monitor (ManaWell, Progression), so reads and writes here are
 * plain read-modify-write.
 */
object DailyHistory {
    const val RETENTION_DAYS = 60L
    private val gson = Gson()

    fun wellDays(prefs: SharedPreferences): Map<String, WellDay> =
        load(prefs, Constants.PrefsKeys.WELL_HISTORY, object : TypeToken<Map<String, WellDay>>() {})

    fun manaDays(prefs: SharedPreferences): Map<String, ManaDay> =
        load(prefs, Constants.PrefsKeys.MANA_HISTORY, object : TypeToken<Map<String, ManaDay>>() {})

    fun wellDay(prefs: SharedPreferences, day: String): WellDay = wellDays(prefs)[day] ?: WellDay()
    fun manaDay(prefs: SharedPreferences, day: String): ManaDay = manaDays(prefs)[day] ?: ManaDay()

    fun updateWell(prefs: SharedPreferences, day: String, change: (WellDay) -> WellDay) {
        val days = wellDays(prefs).toMutableMap()
        days[day] = change(days[day] ?: WellDay())
        save(prefs, Constants.PrefsKeys.WELL_HISTORY, prune(days, day))
    }

    fun updateMana(prefs: SharedPreferences, day: String, change: (ManaDay) -> ManaDay) {
        val days = manaDays(prefs).toMutableMap()
        days[day] = change(days[day] ?: ManaDay())
        save(prefs, Constants.PrefsKeys.MANA_HISTORY, prune(days, day))
    }

    private fun <T> load(prefs: SharedPreferences, key: String, type: TypeToken<Map<String, T>>): Map<String, T> =
        (PrefsHelper.load<Map<String, T>>(prefs, gson, key, type.type) ?: emptyMap())
            .filterValues { it != null }

    private fun save(prefs: SharedPreferences, key: String, days: Map<String, Any>) {
        prefs.edit { putString(key, gson.toJson(days)) }
    }

    private fun <T> prune(days: Map<String, T>, today: String): Map<String, T> {
        val cutoff = try {
            LocalDate.parse(today, DateTimeFormatter.BASIC_ISO_DATE)
                .minusDays(RETENTION_DAYS - 1)
                .format(DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: Exception) {
            return days
        }
        return days.filterKeys { it >= cutoff }
    }
}
