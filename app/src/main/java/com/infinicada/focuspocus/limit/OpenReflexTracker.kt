package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import com.google.gson.Gson
import com.infinicada.focuspocus.Constants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One day's open counts for one app. */
data class AppOpenStats(
    val opens: Int = 0,
    /** Opens the user abandoned within [OpenReflexTracker.REFLEX_THRESHOLD_MS]. */
    val reflexOpens: Int = 0
)

/**
 * Counts how many times each tracked app was opened per day and how many of those
 * opens were "reflexes" — abandoned in under 30 seconds. Today's numbers are
 * surfaced on the pact overlay ("this is open #14 today..."); the retained
 * [RETENTION_DAYS]-day history feeds the Insights screen.
 *
 * Dwell times are measured by the accessibility service from foreground
 * transitions, so they're approximate (e.g. screen-off time inside an app counts
 * as dwell) — good enough for awareness, not billing.
 */
class OpenReflexTracker(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val today: () -> String = { SessionCooldownManager.todayString() }
) {

    /**
     * Persisted shape: [days] maps "yyyyMMdd" to per-package stats. The [date] and
     * [stats] fields exist only to migrate the original single-day shape.
     */
    private data class Store(
        val days: Map<String, Map<String, AppOpenStats>>? = null,
        val date: String? = null,
        val stats: Map<String, AppOpenStats>? = null
    )

    /** Call when a tracked app comes to the foreground. */
    fun recordOpen(packageName: String) {
        val days = loadDays()
        val todayStr = today()
        val todayStats = days[todayStr] ?: emptyMap()
        val current = todayStats[packageName] ?: AppOpenStats()
        save(days + (todayStr to (todayStats + (packageName to current.copy(opens = current.opens + 1)))))
    }

    /**
     * Call when a tracked app leaves the foreground after [dwellMs] in it.
     * Counts a reflex open when the dwell was under [REFLEX_THRESHOLD_MS].
     * No-op for apps with no opens recorded today (e.g. an open that straddled
     * midnight), so reflex counts can never exceed open counts.
     */
    fun recordClose(packageName: String, dwellMs: Long) {
        if (dwellMs < 0 || dwellMs >= REFLEX_THRESHOLD_MS) return
        val days = loadDays()
        val todayStr = today()
        val todayStats = days[todayStr] ?: return
        val current = todayStats[packageName] ?: return
        if (current.opens <= 0) return
        save(days + (todayStr to (todayStats + (packageName to current.copy(reflexOpens = current.reflexOpens + 1)))))
    }

    /** Today's stats for [packageName]; zeros if it hasn't been opened today. */
    fun getStats(packageName: String): AppOpenStats =
        loadDays()[today()]?.get(packageName) ?: AppOpenStats()

    /** Today's stats for every tracked app that has been opened today. */
    fun getAllStats(): Map<String, AppOpenStats> = loadDays()[today()] ?: emptyMap()

    /** Full retained history: "yyyyMMdd" date -> package -> stats. */
    fun getDailyStats(): Map<String, Map<String, AppOpenStats>> = loadDays()

    private fun loadDays(): Map<String, Map<String, AppOpenStats>> {
        val json = prefs.getString(Constants.PrefsKeys.APP_OPEN_STATS, null) ?: return emptyMap()
        return try {
            val store = gson.fromJson(json, Store::class.java)
            when {
                store?.days != null -> store.days
                // Migration from the original single-day shape
                store?.date != null && store.stats != null -> mapOf(store.date to store.stats)
                else -> emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun save(days: Map<String, Map<String, AppOpenStats>>) {
        val cutoff = retentionCutoff(today())
        val pruned = days.filterKeys { it >= cutoff }
        prefs.edit()
            .putString(Constants.PrefsKeys.APP_OPEN_STATS, gson.toJson(Store(days = pruned)))
            .apply()
    }

    /** Oldest "yyyyMMdd" date to retain, relative to [todayStr] (lexicographic compare is safe). */
    private fun retentionCutoff(todayStr: String): String = try {
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = format.parse(todayStr)!!
        cal.add(Calendar.DAY_OF_YEAR, -(RETENTION_DAYS - 1))
        format.format(cal.time)
    } catch (e: Exception) {
        todayStr
    }

    companion object {
        /** An open shorter than this counts as a reflex check rather than real use. */
        const val REFLEX_THRESHOLD_MS = 30_000L

        /** How many days of per-app open history to retain. */
        const val RETENTION_DAYS = 30
    }
}
