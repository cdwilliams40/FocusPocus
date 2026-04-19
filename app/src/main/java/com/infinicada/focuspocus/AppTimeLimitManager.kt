package com.infinicada.focuspocus

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.model.AppTimeLimit

object AppTimeLimitManager {

    fun getTimeLimits(prefs: SharedPreferences, gson: Gson): Map<String, Int> {
        val json = prefs.getString(Constants.PrefsKeys.APP_TIME_LIMITS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            val empty = emptyMap<String, Int>()
            saveTimeLimits(prefs, gson, empty)
            empty
        }
    }

    fun saveTimeLimits(prefs: SharedPreferences, gson: Gson, limits: Map<String, Int>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.APP_TIME_LIMITS, limits)
    }

    // --- Per-app config (includes session cooldown settings) ---

    fun getTimeLimitConfigs(prefs: SharedPreferences, gson: Gson): Map<String, AppTimeLimit> {
        val json = prefs.getString(Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS, null)
        if (json != null) {
            return try {
                val type = object : TypeToken<Map<String, AppTimeLimit>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
        // Migration: if users have existing time limits stored in the legacy flat map but no
        // config map yet, bootstrap configs from the flat map (no cooldown settings, just the
        // daily limit). This preserves their data after an app update.
        val legacyLimits = getTimeLimits(prefs, gson)
        if (legacyLimits.isEmpty()) return emptyMap()
        val migrated = legacyLimits.mapValues { (pkg, limitMinutes) ->
            AppTimeLimit(packageName = pkg, dailyLimitMinutes = limitMinutes)
        }
        saveTimeLimitConfigs(prefs, gson, migrated)
        return migrated
    }

    fun saveTimeLimitConfigs(
        prefs: SharedPreferences,
        gson: Gson,
        configs: Map<String, AppTimeLimit>
    ) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.APP_TIME_LIMIT_CONFIGS, configs)
        // Keep the legacy flat map in sync so existing daily-limit logic keeps working.
        val flat = configs.mapValues { (_, v) -> v.dailyLimitMinutes }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.APP_TIME_LIMITS, flat)
    }

    fun isOverLimit(context: Context, packageName: String, limitMinutes: Int): Boolean {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return false
        val totalForegroundMs = UsageStatsHelper.getPackageUsageToday(context, packageName)
        val usedMinutes = totalForegroundMs / 60_000
        return usedMinutes >= limitMinutes
    }

    fun getUsedMinutesToday(context: Context, packageName: String): Int {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return 0
        val totalForegroundMs = UsageStatsHelper.getPackageUsageToday(context, packageName)
        return (totalForegroundMs / 60_000).toInt()
    }

    fun getAllUsedMinutesToday(context: Context): Map<String, Int> {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        // Use INTERVAL_DAILY to get day-aligned buckets. INTERVAL_BEST can return
        // cross-day data that includes yesterday's usage, preventing proper midnight reset.
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            System.currentTimeMillis()
        ) ?: return emptyMap()

        return stats
            .filter { it.firstTimeStamp >= startTime }
            .groupBy { it.packageName }
            .mapValues { (_, usageList) ->
                (usageList.sumOf { it.totalTimeInForeground } / 60_000).toInt()
            }
    }
}
