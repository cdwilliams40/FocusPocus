package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper

/**
 * An app behind the Mana Well: blocked by default, opened by making a pact
 * that draws minutes from the Well. [substitutePackage] is an optional
 * healthier app offered on the pact screen ("Open X instead").
 */
data class WellApp(
    val packageName: String,
    val substitutePackage: String? = null
)

/**
 * The list of Well apps, plus the 24-hour delay on taking one off it.
 *
 * Adding an app is instant: it only makes things stricter. Removing one is
 * the loosening move, so it is queued and lands [REMOVAL_DELAY_MS] later
 * (on the service's minute tick, via [applyDueRemovals]); until then the
 * app stays behind the Well and the request can be cancelled.
 */
object WellApps {
    private const val TAG = "WellApps"
    const val REMOVAL_DELAY_MS = 24 * 60 * 60 * 1000L

    /** Pact lengths offered on the pact screen, in minutes; trimmed to what the Well holds. */
    val PACT_CHOICES = listOf(2, 5, 10, 15, 30)

    // Parsed-store caches keyed on the raw JSON: the list is read on every
    // app open and every minute tick, by the service and the Warden sync.
    @Volatile private var cachedAppsJson: String? = null
    @Volatile private var cachedApps: List<WellApp> = emptyList()
    @Volatile private var cachedRemovalsJson: String? = null
    @Volatile private var cachedRemovals: Map<String, Long> = emptyMap()

    fun load(prefs: SharedPreferences, gson: Gson): List<WellApp> {
        val json = prefs.getString(Constants.PrefsKeys.WELL_APPS, null) ?: return emptyList()
        cachedAppsJson?.let { if (it == json) return cachedApps }
        val parsed = PrefsHelper.load<List<WellApp>>(
            prefs, gson, Constants.PrefsKeys.WELL_APPS, object : TypeToken<List<WellApp>>() {}.type
        ) ?: emptyList()
        // Gson fills fields via Unsafe, so a damaged record can carry a null
        // package despite the non-null type. Drop it rather than let it leak.
        @Suppress("SENSELESS_COMPARISON")
        val sane = parsed.filterNotNull().filter { it.packageName != null }.distinctBy { it.packageName }
        cachedAppsJson = json
        cachedApps = sane
        return sane
    }

    fun packages(prefs: SharedPreferences, gson: Gson): Set<String> =
        load(prefs, gson).mapTo(mutableSetOf()) { it.packageName }

    fun find(prefs: SharedPreferences, gson: Gson, packageName: String): WellApp? =
        load(prefs, gson).firstOrNull { it.packageName == packageName }

    fun save(prefs: SharedPreferences, gson: Gson, apps: List<WellApp>) {
        val json = gson.toJson(apps.distinctBy { it.packageName })
        prefs.edit { putString(Constants.PrefsKeys.WELL_APPS, json) }
    }

    /** Puts [packageName] behind the Well now. Also cancels a pending removal of it. */
    fun add(prefs: SharedPreferences, gson: Gson, packageName: String) {
        val apps = load(prefs, gson)
        if (apps.none { it.packageName == packageName }) save(prefs, gson, apps + WellApp(packageName))
        cancelRemoval(prefs, gson, packageName)
    }

    /** Sets or clears an app's substitute. Instant: it doesn't loosen anything. */
    fun setSubstitute(prefs: SharedPreferences, gson: Gson, packageName: String, substitute: String?) {
        val apps = load(prefs, gson)
        if (apps.none { it.packageName == packageName }) return
        save(prefs, gson, apps.map {
            if (it.packageName == packageName) it.copy(substitutePackage = substitute?.takeIf { s -> s != packageName })
            else it
        })
    }

    /** Pending removals: package → epoch millis when it comes off the Well. */
    fun pendingRemovals(prefs: SharedPreferences, gson: Gson): Map<String, Long> {
        val json = prefs.getString(Constants.PrefsKeys.WELL_APP_REMOVALS, null) ?: return emptyMap()
        cachedRemovalsJson?.let { if (it == json) return cachedRemovals }
        val parsed = PrefsHelper.load<Map<String, Long>>(
            prefs, gson, Constants.PrefsKeys.WELL_APP_REMOVALS,
            object : TypeToken<Map<String, Long>>() {}.type
        ) ?: emptyMap()
        cachedRemovalsJson = json
        cachedRemovals = parsed
        return parsed
    }

    /** Queues [packageName] to come off the Well in 24 hours. Re-requesting keeps the earlier time. */
    fun requestRemoval(
        prefs: SharedPreferences,
        gson: Gson,
        packageName: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (load(prefs, gson).none { it.packageName == packageName }) return
        val pending = pendingRemovals(prefs, gson)
        if (packageName in pending) return
        saveRemovals(prefs, gson, pending + (packageName to now + REMOVAL_DELAY_MS))
    }

    fun cancelRemoval(prefs: SharedPreferences, gson: Gson, packageName: String) {
        val pending = pendingRemovals(prefs, gson)
        if (packageName !in pending) return
        saveRemovals(prefs, gson, pending - packageName)
    }

    /**
     * Takes every app whose removal delay has passed off the Well. Returns
     * true when anything changed.
     */
    fun applyDueRemovals(
        prefs: SharedPreferences,
        gson: Gson,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val pending = pendingRemovals(prefs, gson)
        if (pending.isEmpty()) return false
        val due = pending.filterValues { it <= now }.keys
        if (due.isEmpty()) return false
        save(prefs, gson, load(prefs, gson).filterNot { it.packageName in due })
        saveRemovals(prefs, gson, pending - due)
        Log.d(TAG, "Took ${due.size} app(s) off the Well")
        return true
    }

    private fun saveRemovals(prefs: SharedPreferences, gson: Gson, removals: Map<String, Long>) {
        prefs.edit {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, gson.toJson(removals))
        }
    }
}
