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

    // How long to trust a cached "under-limit" result. The underlying query walks
    // the whole day's UsageStats event stream on the accessibility service's main
    // thread, so rapid app switches must not each pay for a fresh scan (worst case
    // that lag ANRs the service, which silently disables ALL blocking). The minute
    // tick re-checks the foreground app anyway, so the cost of this cache is
    // blocking landing at most a few seconds later than the exact crossing moment.
    private val UNDER_LIMIT_CACHE_DURATION_MS = 15_000L

    fun shouldBlock(packageName: String, limitMinutes: Int): Boolean {
        val now = clock()
        val entry = cache[packageName]
        if (entry != null) {
            val ttl = if (entry.isOverLimit) CACHE_DURATION_MS else UNDER_LIMIT_CACHE_DURATION_MS
            if (now - entry.timestamp < ttl) return entry.isOverLimit
        }

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
