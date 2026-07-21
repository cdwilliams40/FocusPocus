package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object BlockerRepository {
    private val gson = Gson()
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
                val type = object : TypeToken<List<Blocker>>() {}.type
                // Drop records Gson (Unsafe) left with a null name/mode — a null mode
                // makes shouldBlock throw, which silently disables all blocking here.
                val parsed: List<Blocker> = Blocker.sanitize(gson.fromJson(json, type))
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

    fun getBlocker(context: Context, name: String): Blocker? {
        return getBlockers(context).find { it.name == name }
    }

    fun getBlocker(prefs: SharedPreferences, name: String): Blocker? {
        return getBlockers(prefs).find { it.name == name }
    }

    private var cachedActiveNamesKey: Pair<String?, String?>? = null
    private var cachedActiveNames: List<String> = emptyList()

    /**
     * Names of the blockers active in the current session, from the
     * ACTIVE_BLOCKERS JSON list with the legacy single-blocker key as
     * fallback. Shared by the accessibility service, the Warden sync, the
     * session notification, and the notification listener — all of which
     * previously each re-parsed the JSON on their own hot paths. Cached on
     * the raw stored strings, same as [getBlockers] above.
     */
    fun getActiveBlockerNames(prefs: SharedPreferences): List<String> {
        synchronized(this) {
            val json = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKERS, null)
            val single = prefs.getString(Constants.PrefsKeys.ACTIVE_BLOCKER, null)
            val key = json to single
            cachedActiveNamesKey?.let { if (it == key) return cachedActiveNames }

            val parsed: List<String>? = if (json != null) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(json, type)
                } catch (e: Exception) {
                    Log.e("BlockerRepository", "Error parsing active blockers JSON", e)
                    null
                }
            } else null
            val names = parsed ?: single?.let { listOf(it) } ?: emptyList()

            cachedActiveNamesKey = key
            cachedActiveNames = names
            return names
        }
    }
}
