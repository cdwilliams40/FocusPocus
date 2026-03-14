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

    // How long to trust a cached "over-limit" result (1 minute).
    // Once over, stays over for the rest of the day, so this is safe.
    private val CACHE_DURATION_MS = 60_000L

    fun shouldBlock(packageName: String, limitMinutes: Int): Boolean {
        val now = clock()
        val entry = cache[packageName]

        // Only serve from cache when the app is already known to be over its limit.
        // Once over, it stays over for the rest of the day so the cached answer is safe.
        // "Under limit" results are NOT cached: the user could be actively using the app
        // right now, so every call needs a fresh UsageStats query to catch the moment they
        // cross the threshold (including the minute-tick check while the app is in the foreground).
        if (entry != null && entry.isOverLimit && now - entry.timestamp < CACHE_DURATION_MS) {
            return true
        }

        val isOverLimit = checkFunction(context, packageName, limitMinutes)
        if (isOverLimit) {
            cache[packageName] = CacheEntry(now, isOverLimit)
        } else {
            cache.remove(packageName)
        }
        return isOverLimit
    }

    /**
     * Clears the cache. Useful for testing or when time limits change.
     */
    fun clearCache() {
        cache.clear()
    }
}
