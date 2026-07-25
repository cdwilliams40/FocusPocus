package com.infinicada.focuspocus.enforcement

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.infinicada.focuspocus.ForegroundEvent

/**
 * Answers "which app is in the foreground right now?" from the UsageStats event
 * stream, for the enforcement path that has no accessibility service to tell it.
 *
 * Accessibility gets pushed the answer; this has to pull it, so it is strictly
 * worse in two ways the caller must live with: detection lags the app opening by
 * up to a poll interval plus however long the system takes to publish the event
 * (typically well under a second, but OEM-dependent), and it can only see
 * *activity* transitions — no IME, no notification shade.
 *
 * The event window slides from the last event already consumed, so a user
 * sitting in one app for ten minutes produces empty queries rather than
 * ever-growing ones. An empty window means "nothing changed", which is why
 * [poll] keeps returning the last known package instead of null.
 */
class ForegroundAppMonitor(private val context: Context) {

    private companion object {
        const val TAG = "ForegroundAppMonitor"

        /**
         * How far back a query may reach when there is no consumed-event mark to
         * slide from (first poll, or a mark gone stale after a long screen-off).
         * Long enough to find the resume of an app opened before polling began,
         * short enough that the scan stays trivial.
         */
        const val MAX_LOOKBACK_MS = 60_000L

        /**
         * Re-read this far behind the last consumed event. The system can publish
         * events slightly out of order, and a query window that starts exactly at
         * the last seen timestamp would drop them. Re-reading is harmless — the
         * resolver is a fold over timestamps, not a counter.
         */
        const val OVERLAP_MS = 1_000L
    }

    /** Timestamp of the newest event already folded into [lastKnown]. */
    private var lastConsumedMillis = 0L

    private var lastKnown: String? = null

    /**
     * The foreground package as of now, or null if the last thing the stream said
     * was that an app left the foreground (screen off, or a launcher that reports
     * no resume of its own).
     */
    fun poll(now: Long = System.currentTimeMillis()): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return lastKnown

        val windowStart = maxOf(lastConsumedMillis - OVERLAP_MS, now - MAX_LOOKBACK_MS)
        val events = try {
            readEvents(usageStatsManager, windowStart, now)
        } catch (e: Exception) {
            // Usage access revoked mid-session, or a flaky OEM implementation.
            // Holding the last known package is the safe answer: it keeps the
            // engine enforcing on the app it already knows about rather than
            // deciding the foreground went empty.
            Log.e(TAG, "Usage event query failed", e)
            return lastKnown
        }

        if (events.isEmpty()) return lastKnown

        lastConsumedMillis = events.maxOf { it.timeStamp }
        lastKnown = resolveForegroundPackage(events, lastKnown)
        return lastKnown
    }

    /** Drops the slide mark so the next [poll] re-reads a full lookback window. */
    fun reset() {
        lastConsumedMillis = 0L
        lastKnown = null
    }

    private fun readEvents(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): List<ForegroundEvent> {
        val usageEvents = manager.queryEvents(startMillis, endMillis) ?: return emptyList()
        val events = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    events.add(
                        ForegroundEvent(event.packageName ?: "", event.eventType, event.timeStamp)
                    )
            }
        }
        return events
    }
}

/**
 * Folds a window of activity transitions onto [seed] to get the package in the
 * foreground at the end of the window.
 *
 * The newest `ACTIVITY_RESUMED` wins. A pause or stop only clears the answer if
 * it belongs to the package currently held *and* is not older than the event
 * that established it — otherwise a late-delivered stop for an app the user
 * already left would wipe out a valid newer resume.
 *
 * Extracted as a pure function so the fold is unit-testable without a device;
 * [ForegroundAppMonitor] only supplies the events.
 */
internal fun resolveForegroundPackage(
    events: List<ForegroundEvent>,
    seed: String? = null
): String? {
    var current = seed
    // The seed's own resume happened before this window, so anything in the
    // window is "not older" than it.
    var currentSince = Long.MIN_VALUE
    for (e in events) {
        when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (e.timeStamp >= currentSince) {
                    current = e.packageName
                    currentSince = e.timeStamp
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED -> {
                if (e.packageName == current && e.timeStamp >= currentSince) {
                    current = null
                    currentSince = e.timeStamp
                }
            }
        }
    }
    return current
}
