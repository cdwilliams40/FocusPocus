package com.infinicada.focuspocus

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.app.usage.UsageStats
import java.util.Calendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AppTimeLimitManager {

    fun getTimeLimits(prefs: SharedPreferences, gson: Gson): Map<String, Int> {
        val json = prefs.getString(Constants.PrefsKeys.APP_TIME_LIMITS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            val empty = emptyMap<String, Int>()
            saveTimeLimits(prefs, gson, empty)
            empty
        }
    }

    fun saveTimeLimits(prefs: SharedPreferences, gson: Gson, limits: Map<String, Int>) {
        prefs.edit().putString(Constants.PrefsKeys.APP_TIME_LIMITS, gson.toJson(limits)).apply()
    }

    fun isOverLimit(context: Context, packageName: String, limitMinutes: Int): Boolean {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return false
        val totalForegroundMs = UsageStatsHelper.getPackageUsageToday(context, packageName)
        val usedMinutes = totalForegroundMs / 1000 / 60
        return usedMinutes >= limitMinutes
    }

    fun getUsedMinutesToday(context: Context, packageName: String): Int {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return 0
        val totalForegroundMs = UsageStatsHelper.getPackageUsageToday(context, packageName)
        return (totalForegroundMs / 1000 / 60).toInt()
    }

    fun getAllUsedMinutesToday(context: Context): Map<String, Int> {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            calendar.timeInMillis,
            System.currentTimeMillis()
        ) ?: return emptyMap()

        return stats
            .groupBy { it.packageName }
            .mapValues { (_, usageList) ->
                (usageList.sumOf { it.totalTimeInForeground } / 1000 / 60).toInt()
            }
    }
}
