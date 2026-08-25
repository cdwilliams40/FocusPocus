package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.AppInfo

/**
 * Remembers the last package scan so app names are on screen in the very first
 * frame.
 *
 * Scanning every installed package and loading its label takes long enough to
 * see (SpellbookViewModel.loadInstalledApps does it off the main thread), and
 * until it lands the screens have no label for a guarded package and fall back
 * to the raw package name — so a pact card would read "com.thing.stuff" for a
 * beat before flipping to the real name. Seeding the list from this cache means
 * that fallback is only ever reached for a package that has never been scanned.
 *
 * The cache is a display convenience, never an authority: the fresh scan
 * replaces it wholesale as soon as it completes, so an app installed or removed
 * since the last scan is only briefly out of date.
 */
class InstalledAppsRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getCachedApps(): List<AppInfo> {
        val type = object : TypeToken<List<AppInfo>>() {}.type
        return PrefsHelper.load<List<AppInfo>>(prefs, gson, Constants.PrefsKeys.INSTALLED_APPS_CACHE, type)
            ?: emptyList()
    }

    fun cacheApps(apps: List<AppInfo>) {
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.INSTALLED_APPS_CACHE, apps)
    }
}
