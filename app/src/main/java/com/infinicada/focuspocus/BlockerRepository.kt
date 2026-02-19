package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
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
        val json = prefs.getString(Constants.PrefsKeys.BLOCKER_LISTS, null)

        synchronized(this) {
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
                val parsed: List<Blocker> = gson.fromJson(json, type)
                cachedBlockerListsJson = json
                cachedBlockerLists = parsed
                parsed
            } catch (e: Exception) {
                // If parsing fails, we reset cache to be safe
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
}
