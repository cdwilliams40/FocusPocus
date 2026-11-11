package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Apps whose use earns mana (ManaRules.PRODUCTIVE_*). Time is measured from
 * usage stats, so it needs usage access; without it nothing is credited.
 */
object ProductiveApps {

    fun load(prefs: SharedPreferences, gson: Gson): List<String> =
        PrefsHelper.load<List<String>>(
            prefs, gson, Constants.PrefsKeys.PRODUCTIVE_APPS, object : TypeToken<List<String>>() {}.type
        ).orEmpty().filterNotNull().distinct()

    fun save(prefs: SharedPreferences, gson: Gson, packages: List<String>) {
        prefs.edit { putString(Constants.PrefsKeys.PRODUCTIVE_APPS, gson.toJson(packages.distinct())) }
    }

    /**
     * Whole minutes spent in productive apps today, or null when they can't
     * be measured (no apps, or no usage access). Walks the day's usage
     * events — call it off the main thread.
     */
    fun minutesToday(context: Context, prefs: SharedPreferences, gson: Gson): Int? {
        val apps = load(prefs, gson).toSet()
        if (apps.isEmpty() || !UsageStatsHelper.hasUsageStatsPermission(context)) return null
        val millis = UsageStatsHelper.getTodayForegroundUsage(context)
            .filterKeys { it in apps }
            .values.sum()
        return (millis / 60_000L).toInt()
    }

    /** Measures today's productive time and pays whatever is owed. Off the main thread. */
    fun creditToday(context: Context, prefs: SharedPreferences, gson: Gson): Progression.AwardResult {
        val minutes = minutesToday(context, prefs, gson) ?: return Progression.AwardResult.EMPTY
        val result = Progression.creditProductive(prefs, gson, minutes)
        ProgressionNotifier.postTrialCompletions(context, prefs, result.completedTrials)
        return result
    }
}
