package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object BlockerRepository {
    private var cachedBlockerListsJson: String? = null
    private var cachedBlockerLists: List<Blocker> = emptyList()

    fun getBlockers(context: Context): List<Blocker> {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return getBlockers(prefs)
    }

    fun getBlockers(prefs: SharedPreferences): List<Blocker> {
        synchronized(this) {
            val json = prefs.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)

            if (json == null) {
                cachedBlockerListsJson = null
                cachedBlockerLists = emptyList()
                return emptyList()
            }

            if (json == cachedBlockerListsJson) {
                return cachedBlockerLists
            }

            return try {
                val parsed: List<Blocker> = Blocker.sanitize(AppJson.decodeListLenient(json))
                cachedBlockerListsJson = json
                cachedBlockerLists = parsed
                parsed
            } catch (e: Exception) {
                Log.e("BlockerRepository", "Error parsing blocker lists JSON", e)
                cachedBlockerListsJson = null
                cachedBlockerLists = emptyList()
                emptyList()
            }
        }
    }

    fun sessionBlockerFor(
        prefs: SharedPreferences,
        packageName: String,
        whitelistBlockable: (String) -> Boolean
    ): Blocker? {
        if (!SessionManager.isFocusActive(prefs)) return null
        if (prefs.getBoolean(Constants.PrefsKeys.IS_ON_BREAK, false)) return null
        val activeNames = getActiveBlockerNames(prefs)
        return getBlockers(prefs).firstOrNull { blocker ->
            blocker.name in activeNames && blocker.shouldBlock(packageName) &&
                (blocker.mode != BlockerMode.WHITELIST || whitelistBlockable(packageName))
        }
    }

    private var cachedActiveNamesKey: String? = null
    private var cachedActiveNamesValid = false
    private var cachedActiveNames: List<String> = emptyList()

    fun getActiveBlockerNames(prefs: SharedPreferences): List<String> {
        synchronized(this) {
            val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
            val key = json
            if (cachedActiveNamesValid && cachedActiveNamesKey == key) return cachedActiveNames

            val parsed: List<String>? = if (json != null) {
                try {
                    AppJson.decodeListLenient(json)
                } catch (e: Exception) {
                    Log.e("BlockerRepository", "Error parsing active blockers JSON", e)
                    null
                }
            } else null
            val names = parsed ?: emptyList()

            cachedActiveNamesKey = key
            cachedActiveNamesValid = true
            cachedActiveNames = names
            return names
        }
    }
}
