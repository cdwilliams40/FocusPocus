package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper

/** Today's open counts for one app. */
data class AppOpenStats(
    val opens: Int = 0,
    /** Opens the user abandoned within [OpenReflexTracker.REFLEX_THRESHOLD_MS]. */
    val reflexOpens: Int = 0
)

/**
 * Counts how many times each tracked app was opened today and how many of those
 * opens were "reflexes" — abandoned in under 30 seconds. Surfaced on the pact
 * overlay so the user sees the shape of the habit at the exact moment the urge
 * fires ("this is open #14 today...").
 *
 * Counters are persisted per calendar day and reset lazily when the date rolls
 * over. Dwell times are measured by the accessibility service from foreground
 * transitions, so they're approximate (e.g. screen-off time inside an app counts
 * as dwell) — good enough for awareness, not billing.
 */
class OpenReflexTracker(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val today: () -> String = { SessionCooldownManager.todayString() }
) {

    private data class Store(val date: String, val stats: Map<String, AppOpenStats>)

    /** Call when a tracked app comes to the foreground. */
    fun recordOpen(packageName: String) {
        val store = loadForToday()
        val current = store.stats[packageName] ?: AppOpenStats()
        save(store.copy(stats = store.stats + (packageName to current.copy(opens = current.opens + 1))))
    }

    /**
     * Call when a tracked app leaves the foreground after [dwellMs] in it.
     * Counts a reflex open when the dwell was under [REFLEX_THRESHOLD_MS].
     * No-op for apps with no opens recorded today (e.g. an open that straddled
     * midnight), so reflex counts can never exceed open counts.
     */
    fun recordClose(packageName: String, dwellMs: Long) {
        if (dwellMs < 0 || dwellMs >= REFLEX_THRESHOLD_MS) return
        val store = loadForToday()
        val current = store.stats[packageName] ?: return
        if (current.opens <= 0) return
        save(store.copy(stats = store.stats + (packageName to current.copy(reflexOpens = current.reflexOpens + 1))))
    }

    /** Today's stats for [packageName]; zeros if it hasn't been opened today. */
    fun getStats(packageName: String): AppOpenStats =
        loadForToday().stats[packageName] ?: AppOpenStats()

    /** Today's stats for every tracked app that has been opened today. */
    fun getAllStats(): Map<String, AppOpenStats> = loadForToday().stats

    private fun loadForToday(): Store {
        val type = object : TypeToken<Store>() {}.type
        val stored: Store? = PrefsHelper.load(prefs, gson, Constants.PrefsKeys.APP_OPEN_STATS, type)
        val todayStr = today()
        return if (stored != null && stored.date == todayStr) stored else Store(todayStr, emptyMap())
    }

    private fun save(store: Store) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.APP_OPEN_STATS, store)
    }

    companion object {
        /** An open shorter than this counts as a reflex check rather than real use. */
        const val REFLEX_THRESHOLD_MS = 30_000L
    }
}
