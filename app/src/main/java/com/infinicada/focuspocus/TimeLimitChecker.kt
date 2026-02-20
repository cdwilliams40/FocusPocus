package com.infinicada.focuspocus

import android.content.Context

/**
 * Checks if an app has exceeded its time limit, with caching to reduce
 * expensive UsageStatsManager queries.
 */
class TimeLimitChecker(
    private val context: Context,
    private val checkFunction: (Context, String, Int) -> Boolean = { ctx, pkg, limit ->
        AppTimeLimitManager.isOverLimit(ctx, pkg, limit)
    },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry(val timestamp: Long, val isOverLimit: Boolean)
    private val cache = mutableMapOf<String, CacheEntry>()

    // Cache duration in milliseconds (1 minute)
    // This balances performance (avoiding frequent IPC calls) with responsiveness (blocking within 1 minute of limit)
    private val CACHE_DURATION_MS = 60_000L

    fun shouldBlock(packageName: String, limitMinutes: Int): Boolean {
        val now = clock()
        val entry = cache[packageName]

        if (entry != null) {
            if (now - entry.timestamp < CACHE_DURATION_MS) {
                // If the app was NOT over limit, and we checked less than a minute ago,
                // assume it's still safe.
                // If the app WAS over limit, assume it's still over limit.
                return entry.isOverLimit
            }
        }

        // Cache expired or not present, query UsageStats
        val isOverLimit = checkFunction(context, packageName, limitMinutes)
        cache[packageName] = CacheEntry(now, isOverLimit)
        return isOverLimit
    }

    /**
     * Clears the cache. Useful for testing or when time limits change.
     */
    fun clearCache() {
        cache.clear()
    }
}
