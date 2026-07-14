package com.infinicada.focuspocus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

data class AppUsage(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long
)

/**
 * A foreground-transition event decoupled from [UsageEvents.Event] so the
 * aggregation logic can be unit tested on the JVM.
 */
data class ForegroundEvent(
    val packageName: String,
    val eventType: Int,
    val timeStamp: Long
)

object UsageStatsHelper {

    /** See [getForegroundUsageSince]: pre-window context for apps already in the foreground. */
    private const val EVENT_LOOKBACK_MS = 6L * 60 * 60 * 1000

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayUsage(context: Context): List<AppUsage> {
        return getForegroundUsageSince(context, startOfTodayMillis())
            .filter { it.value > 0 }
            .map { (packageName, totalTime) ->
                AppUsage(
                    packageName = packageName,
                    appName = AppUtils.getAppName(context, packageName),
                    totalTimeInForeground = totalTime
                )
            }
            .sortedByDescending { it.totalTimeInForeground }
    }

    fun getWeeklyUsage(context: Context): List<AppUsage> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return queryUsageStats(context, calendar.timeInMillis, System.currentTimeMillis())
    }

    fun getMonthlyUsage(context: Context): List<AppUsage> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return queryUsageStats(context, calendar.timeInMillis, System.currentTimeMillis())
    }

    fun getPackageUsageToday(context: Context, packageName: String): Long {
        return getForegroundUsageSince(context, startOfTodayMillis())[packageName] ?: 0L
    }

    fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Per-package foreground time between [startTime] and now, computed from the
     * activity resume/pause event stream rather than [UsageStatsManager.queryUsageStats]
     * buckets. The bucket API cannot answer "usage since local midnight": daily buckets
     * are not aligned to local midnight on all devices, so bucket-based results either
     * bleed in yesterday's usage or (when filtered by bucket start time) drop today's
     * usage entirely. Events carry exact timestamps, so sessions can be clipped to the
     * window precisely.
     *
     * Only reliable for recent windows (the system retains detailed events for a few
     * days) — use the bucket queries for weekly/monthly aggregates.
     *
     * Events are read from [EVENT_LOOKBACK_MS] before [startTime]: an app resumed
     * before the window and still in the foreground produces no event inside the
     * window at all, so without pre-window context it would count as zero usage
     * (e.g. a video app held open across midnight escaping its daily limit until
     * its first pause). The aggregation clips all credited time to the window, so
     * the lookback only supplies state, never extra minutes.
     */
    fun getForegroundUsageSince(context: Context, startTime: Long): Map<String, Long> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
        val endTime = System.currentTimeMillis()
        val usageEvents =
            usageStatsManager.queryEvents(startTime - EVENT_LOOKBACK_MS, endTime) ?: return emptyMap()

        val events = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.DEVICE_STARTUP -> {
                    events.add(
                        ForegroundEvent(event.packageName ?: "", event.eventType, event.timeStamp)
                    )
                }
            }
        }
        return aggregateForegroundTime(events, startTime, endTime)
    }

    /**
     * Sums per-package foreground time within [[windowStart], [windowEnd]] from a
     * time-ordered event stream.
     *
     * - ACTIVITY_RESUMED opens a foreground session (clipped to [windowStart]).
     * - ACTIVITY_PAUSED / ACTIVITY_STOPPED closes it. A PAUSED with no open session and
     *   no earlier event for that package means the app was already in the foreground
     *   when the window began, so that stretch counts from [windowStart]. A STOPPED
     *   with no open session is ignored (apps that left the foreground before the
     *   window can emit a late STOPPED inside it).
     * - DEVICE_SHUTDOWN / DEVICE_STARTUP close every open session at that instant.
     * - Sessions still open at the end of the stream count up to [windowEnd].
     */
    internal fun aggregateForegroundTime(
        events: List<ForegroundEvent>,
        windowStart: Long,
        windowEnd: Long
    ): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val foregroundSince = HashMap<String, Long>()
        val seenPackages = HashSet<String>()

        fun close(packageName: String, atTime: Long) {
            val start = foregroundSince.remove(packageName) ?: return
            val duration = (atTime.coerceAtMost(windowEnd) - start).coerceAtLeast(0L)
            totals.merge(packageName, duration, Long::plus)
        }

        for (e in events) {
            if (e.timeStamp > windowEnd) break
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Multiple activities of one app can resume back to back; keep the
                    // earliest open timestamp rather than restarting the session.
                    if (e.packageName !in foregroundSince) {
                        foregroundSince[e.packageName] = e.timeStamp.coerceAtLeast(windowStart)
                    }
                    seenPackages.add(e.packageName)
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (e.packageName in foregroundSince) {
                        close(e.packageName, e.timeStamp)
                    } else if (e.packageName !in seenPackages) {
                        // First event for this package is a pause: it was foreground
                        // across the window start (e.g. in use at midnight).
                        val duration = (e.timeStamp.coerceAtMost(windowEnd) - windowStart)
                            .coerceAtLeast(0L)
                        totals.merge(e.packageName, duration, Long::plus)
                    }
                    seenPackages.add(e.packageName)
                }
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    close(e.packageName, e.timeStamp)
                    seenPackages.add(e.packageName)
                }
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.DEVICE_STARTUP -> {
                    for (pkg in foregroundSince.keys.toList()) close(pkg, e.timeStamp)
                }
            }
        }

        for (pkg in foregroundSince.keys.toList()) close(pkg, windowEnd)
        return totals
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    private fun queryUsageStats(context: Context, startTime: Long, endTime: Long): List<AppUsage> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, startTime, endTime
        )
        return (stats ?: emptyList())
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (packageName, usageList) ->
                val totalTime = usageList.sumOf { it.totalTimeInForeground }
                val appName = AppUtils.getAppName(context, packageName)
                AppUsage(
                    packageName = packageName,
                    appName = appName,
                    totalTimeInForeground = totalTime
                )
            }
            .sortedByDescending { it.totalTimeInForeground }
    }
}
