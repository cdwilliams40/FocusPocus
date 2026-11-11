package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.Talisman
import com.infinicada.focuspocus.PrefsHelper

class TalismanRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getNamedTags(): List<Talisman> {
        val type = object : TypeToken<List<Talisman>>() {}.type
        return PrefsHelper.load<List<Talisman>>(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, type)
            ?: emptyList()
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveNamedTag(tag: Talisman, currentList: List<Talisman>): Boolean {
        val isUpdate = currentList.any { it.id == tag.id }
        if (!isUpdate && currentList.size >= Constants.MAX_NAMED_TAGS) return false

        val updated = currentList.filterNot { it.id == tag.id } + tag
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, updated)
        return true
    }

    fun deleteNamedTag(tag: Talisman, currentList: List<Talisman>): List<Talisman> {
        val updated = currentList.filterNot { it.id == tag.id }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, updated)
        return updated
    }
}
