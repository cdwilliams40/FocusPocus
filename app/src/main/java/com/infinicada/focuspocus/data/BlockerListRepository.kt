package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.infinicada.focuspocus.Blocker
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper

class BlockerListRepository(
    private val prefs: SharedPreferences
) {
    fun getBlockers(): List<Blocker> {
        val stored = PrefsHelper.loadListLenient<Blocker>(prefs, Constants.PrefsKeys.BLOCKER_LISTS)
            ?: return emptyList()
        return Blocker.sanitize(stored)
    }

    fun saveBlocker(blocker: Blocker): Boolean {
        val currentList = getBlockers()
        val isUpdate = currentList.any { it.name == blocker.name }
        if (!isUpdate && currentList.size >= Constants.MAX_BLOCKERS) return false

        val capped = blocker.copy(
            apps = blocker.effectiveApps.take(Constants.MAX_APPS_PER_BLOCKER).toSet()
        )
        val updated = currentList.filterNot { it.name == capped.name } + capped
        PrefsHelper.save(prefs, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return true
    }

    fun deleteBlocker(blocker: Blocker): List<Blocker> {
        val updated = getBlockers().filterNot { it.name == blocker.name }
        PrefsHelper.save(prefs, Constants.PrefsKeys.BLOCKER_LISTS, updated)
        return updated
    }
}
