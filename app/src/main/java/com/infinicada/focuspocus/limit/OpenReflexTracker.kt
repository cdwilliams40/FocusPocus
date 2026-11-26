package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import androidx.core.content.edit
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.Constants
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One day's open count for one app. */
@Serializable
data class AppOpenStats(
    val opens: Int = 0
)

/**
 * Counts how many times each tracked app was opened per day. Today's numbers are
 * surfaced on the pact overlay ("This is open #14 today") and sort the Well's
 * Request Time grid; the retained [RETENTION_DAYS]-day history feeds Insights.
 */
class OpenReflexTracker(
    private val prefs: SharedPreferences,
    private val today: () -> String = { DayKey.today() }
) {
    /**
     * Persisted shape: [days] maps "yyyyMMdd" to per-package stats. The [date] and
     * [stats] fields exist only to migrate the original single-day shape.
     */
    @Serializable
    private data class Store(
        val days: Map<String, Map<String, AppOpenStats>>? = null,
        val date: String? = null,
        val stats: Map<String, AppOpenStats>? = null
    )

    fun recordOpen(packageName: String) {
        val days = loadDays()
        val todayStr = today()
        val todayStats = days[todayStr] ?: emptyMap()
        val current = todayStats[packageName] ?: AppOpenStats()
        save(days + (todayStr to (todayStats + (packageName to current.copy(opens = current.opens + 1)))))
    }

    fun getStats(packageName: String): AppOpenStats =
        loadDays()[today()]?.get(packageName) ?: AppOpenStats()

    fun getAllStats(): Map<String, AppOpenStats> = loadDays()[today()] ?: emptyMap()

    fun getDailyStats(): Map<String, Map<String, AppOpenStats>> = loadDays()

    private fun loadDays(): Map<String, Map<String, AppOpenStats>> {
        val json = prefs.getString(Constants.PrefsKeys.APP_OPEN_STATS, null) ?: return emptyMap()
        cachedDaysJson?.let { if (it == json) return cachedDays }
        val days = try {
            val store = AppJson.json.decodeFromString<Store>(json)
            when {
                store.days != null -> store.days
                store.date != null && store.stats != null -> mapOf(store.date to store.stats)
                else -> emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
        cachedDaysJson = json
        cachedDays = days
        return days
    }

    private fun save(days: Map<String, Map<String, AppOpenStats>>) {
        val cutoff = retentionCutoff(today())
        val pruned = days.filterKeys { it >= cutoff }
        val json = AppJson.json.encodeToString(Store(days = pruned))
        prefs.edit { putString(Constants.PrefsKeys.APP_OPEN_STATS, json) }
        cachedDaysJson = json
        cachedDays = pruned
    }

    private fun retentionCutoff(todayStr: String): String = try {
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = format.parse(todayStr)!!
        cal.add(Calendar.DAY_OF_YEAR, -(RETENTION_DAYS - 1))
        format.format(cal.time)
    } catch (_: Exception) {
        todayStr
    }

    companion object {
        const val RETENTION_DAYS = 30
        @Volatile private var cachedDaysJson: String? = null
        @Volatile private var cachedDays: Map<String, Map<String, AppOpenStats>> = emptyMap()
    }
}
