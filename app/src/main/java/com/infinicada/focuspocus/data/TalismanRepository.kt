package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.Talisman

class TalismanRepository(
    private val prefs: SharedPreferences
) {
    fun getTalismans(): List<Talisman> =
        PrefsHelper.loadListLenient<Talisman>(prefs, Constants.PrefsKeys.NAMED_TAGS) ?: emptyList()

    fun saveTalisman(talisman: Talisman, currentList: List<Talisman>): Boolean {
        val isUpdate = currentList.any { it.id == talisman.id }
        if (!isUpdate && currentList.size >= Constants.MAX_NAMED_TAGS) return false

        val updated = currentList.filterNot { it.id == talisman.id } + talisman
        PrefsHelper.save(prefs, Constants.PrefsKeys.NAMED_TAGS, updated)
        return true
    }

    fun deleteTalisman(talisman: Talisman, currentList: List<Talisman>): List<Talisman> {
        val updated = currentList.filterNot { it.id == talisman.id }
        PrefsHelper.save(prefs, Constants.PrefsKeys.NAMED_TAGS, updated)
        return updated
    }
}
