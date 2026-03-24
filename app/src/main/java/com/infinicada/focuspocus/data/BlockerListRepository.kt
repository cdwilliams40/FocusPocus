package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.BlockerMode
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper

class BlockerListRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getBlockers(): List<Blocker> {
        val type = object : TypeToken<List<Blocker>>() {}.type
        return PrefsHelper.load<List<Blocker>>(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, type)
            ?: listOf(Blocker("Default", BlockerMode.BLACKLIST, setOf("com.google.android.youtube")))
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveBlocker(blocker: Blocker, currentList: List<Blocker>): Boolean {
        val isUpdate = currentList.any { it.name == blocker.name }
        if (!isUpdate && currentList.size >= Constants.MAX_BLOCKERS) return false

        val capped = blocker.copy(
            apps = blocker.effectiveApps.take(Constants.MAX_APPS_PER_BLOCKER).toSet(),
            websites = blocker.websites?.take(Constants.MAX_WEBSITES_PER_BLOCKER)
        )
        val updated = currentList.filterNot { it.name == capped.name } + capped
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return true
    }

    fun deleteBlocker(blocker: Blocker, currentList: List<Blocker>): List<Blocker> {
        val updated = currentList.filterNot { it.name == blocker.name }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return updated
    }
}
