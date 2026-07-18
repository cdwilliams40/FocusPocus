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
        val stored = PrefsHelper.load<List<Blocker>>(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, type)
        // Drop records Gson (Unsafe) left with a null name/mode so a corrupt
        // enchantment can't crash shouldBlock on the UI or notification-listener path.
        if (stored != null) return Blocker.sanitize(stored)
        // First run: persist the starter blocker instead of synthesizing it on
        // every read. The accessibility service reads the same key through
        // BlockerRepository and synthesizes nothing, so an unpersisted default
        // is shown in the UI as activatable but never actually enforced.
        val defaults = listOf(Blocker("Default", BlockerMode.BLACKLIST, setOf("com.google.android.youtube")))
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, defaults)
        return defaults
    }

    /**
     * Persists [blocker], replacing any stored blocker with the same name.
     *
     * The rest of the list is re-read from prefs rather than taken from the
     * caller: the accessibility service writes to the same key concurrently
     * (auto-adding newly installed apps to opted-in blacklists), so writing
     * back a caller-supplied snapshot would silently discard those updates.
     *
     * @return true if saved successfully, false if at max capacity
     */
    fun saveBlocker(blocker: Blocker): Boolean {
        val currentList = getBlockers()
        val isUpdate = currentList.any { it.name == blocker.name }
        if (!isUpdate && currentList.size >= Constants.MAX_BLOCKERS) return false

        val capped = blocker.copy(
            apps = blocker.effectiveApps.take(Constants.MAX_APPS_PER_BLOCKER).toSet()
        )
        val updated = currentList.filterNot { it.name == capped.name } + capped
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return true
    }

    /** Removes the stored blocker named like [blocker]. Returns the updated list. */
    fun deleteBlocker(blocker: Blocker): List<Blocker> {
        val updated = getBlockers().filterNot { it.name == blocker.name }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return updated
    }
}
