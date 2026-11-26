package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.encodeToString

/**
 * Apps whose use earns mana (ManaRules.PRODUCTIVE_*): the ones picked here,
 * plus every Well app's substitute. Time is measured from usage stats, so it
 * needs usage access; without it nothing is credited.
 */
object ProductiveApps {

    fun load(prefs: SharedPreferences): List<String> =
        PrefsHelper.loadListLenient<String>(prefs, Constants.PrefsKeys.PRODUCTIVE_APPS)
            .orEmpty()
            .distinct()

    fun save(prefs: SharedPreferences, packages: List<String>) {
        prefs.edit {
            putString(Constants.PrefsKeys.PRODUCTIVE_APPS, AppJson.json.encodeToString(packages.distinct()))
        }
    }

    /**
     * Every app whose time earns mana: the chosen productive apps and the
     * Well apps' substitutes. A Well app itself never earns, even if it is
     * also another Well app's substitute.
     */
    fun earningPackages(prefs: SharedPreferences): Set<String> {
        val wellApps = com.infinicada.focuspocus.limit.WellApps.load(prefs)
        val substitutes = wellApps.mapNotNull { it.substitutePackage }
        return (load(prefs) + substitutes).toSet() - wellApps.mapTo(mutableSetOf()) { it.packageName }
    }

    /**
     * Whole minutes spent in productive apps and substitutes today, or null when they can't
     * be measured (no apps, or no usage access). Walks the day's usage
     * events — call it off the main thread.
     */
    fun minutesToday(context: Context, prefs: SharedPreferences): Int? {
        val apps = earningPackages(prefs)
        if (apps.isEmpty() || !UsageStatsHelper.hasUsageStatsPermission(context)) return null
        val millis = UsageStatsHelper.getTodayForegroundUsage(context)
            .filterKeys { it in apps }
            .values.sum()
        return (millis / 60_000L).toInt()
    }

    /** Measures today's productive time and pays whatever is owed. Off the main thread. */
    fun creditToday(context: Context, prefs: SharedPreferences): Progression.AwardResult {
        val minutes = minutesToday(context, prefs) ?: return Progression.AwardResult.EMPTY
        val result = Progression.creditProductive(prefs, minutes)
        ProgressionNotifier.postTrialCompletions(context, prefs, result.completedTrials)
        return result
    }
}
