package com.infinicada.focuspocus.limit

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.infinicada.focuspocus.AppJson
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * An app behind the Mana Well: blocked by default, opened by making a pact
 * that draws minutes from the Well. [substitutePackage] is an optional
 * healthier app offered on the pact screen ("Open X instead").
 */
@Serializable
data class WellApp(
    val packageName: String,
    val substitutePackage: String? = null
)

object WellApps {
    private const val TAG = "WellApps"
    const val REMOVAL_DELAY_MS = 24 * 60 * 60 * 1000L

    /** Pact lengths offered on the pact screen, in minutes; trimmed to what the Well holds. */
    val PACT_CHOICES = listOf(2, 5, 10, 15, 30)

    @Volatile private var cachedAppsJson: String? = null
    @Volatile private var cachedApps: List<WellApp> = emptyList()
    @Volatile private var cachedRemovalsJson: String? = null
    @Volatile private var cachedRemovals: Map<String, Long> = emptyMap()

    fun load(prefs: SharedPreferences): List<WellApp> {
        val json = prefs.getString(Constants.PrefsKeys.WELL_APPS, null) ?: return emptyList()
        cachedAppsJson?.let { if (it == json) return cachedApps }
        val parsed = PrefsHelper.loadListLenient<WellApp>(prefs, Constants.PrefsKeys.WELL_APPS)
            ?: emptyList()
        val sane = parsed.distinctBy { it.packageName }
        cachedAppsJson = json
        cachedApps = sane
        return sane
    }

    fun packages(prefs: SharedPreferences): Set<String> =
        load(prefs).mapTo(mutableSetOf()) { it.packageName }

    fun find(prefs: SharedPreferences, packageName: String): WellApp? =
        load(prefs).firstOrNull { it.packageName == packageName }

    fun save(prefs: SharedPreferences, apps: List<WellApp>) {
        val json = AppJson.json.encodeToString(apps.distinctBy { it.packageName })
        prefs.edit { putString(Constants.PrefsKeys.WELL_APPS, json) }
    }

    fun add(prefs: SharedPreferences, packageName: String) {
        val apps = load(prefs)
        if (apps.none { it.packageName == packageName }) save(prefs, apps + WellApp(packageName))
        cancelRemoval(prefs, packageName)
    }

    fun setSubstitute(prefs: SharedPreferences, packageName: String, substitute: String?) {
        val apps = load(prefs)
        if (apps.none { it.packageName == packageName }) return
        save(prefs, apps.map {
            if (it.packageName == packageName) it.copy(substitutePackage = substitute?.takeIf { s -> s != packageName })
            else it
        })
    }

    fun pendingRemovals(prefs: SharedPreferences): Map<String, Long> {
        val json = prefs.getString(Constants.PrefsKeys.WELL_APP_REMOVALS, null) ?: return emptyMap()
        cachedRemovalsJson?.let { if (it == json) return cachedRemovals }
        val parsed = PrefsHelper.load<Map<String, Long>>(prefs, Constants.PrefsKeys.WELL_APP_REMOVALS)
            ?: emptyMap()
        cachedRemovalsJson = json
        cachedRemovals = parsed
        return parsed
    }

    fun requestRemoval(
        prefs: SharedPreferences,
        packageName: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (load(prefs).none { it.packageName == packageName }) return
        val pending = pendingRemovals(prefs)
        if (packageName in pending) return
        saveRemovals(prefs, pending + (packageName to now + REMOVAL_DELAY_MS))
    }

    fun cancelRemoval(prefs: SharedPreferences, packageName: String) {
        val pending = pendingRemovals(prefs)
        if (packageName !in pending) return
        saveRemovals(prefs, pending - packageName)
    }

    fun applyDueRemovals(
        prefs: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val pending = pendingRemovals(prefs)
        if (pending.isEmpty()) return false
        val due = pending.filterValues { it <= now }.keys
        if (due.isEmpty()) return false
        save(prefs, load(prefs).filterNot { it.packageName in due })
        saveRemovals(prefs, pending - due)
        Log.d(TAG, "Took ${due.size} app(s) off the Well")
        return true
    }

    private fun saveRemovals(prefs: SharedPreferences, removals: Map<String, Long>) {
        prefs.edit {
            if (removals.isEmpty()) remove(Constants.PrefsKeys.WELL_APP_REMOVALS)
            else putString(Constants.PrefsKeys.WELL_APP_REMOVALS, AppJson.json.encodeToString(removals))
        }
    }
}
