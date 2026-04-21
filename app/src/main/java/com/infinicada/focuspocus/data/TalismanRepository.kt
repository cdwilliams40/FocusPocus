package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.NamedTag
import com.infinicada.focuspocus.PrefsHelper

class TalismanRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getNamedTags(): List<NamedTag> {
        val type = object : TypeToken<List<NamedTag>>() {}.type
        return PrefsHelper.load<List<NamedTag>>(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, type)
            ?: emptyList()
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveNamedTag(tag: NamedTag, currentList: List<NamedTag>): Boolean {
        val isUpdate = currentList.any { it.id == tag.id }
        if (!isUpdate && currentList.size >= Constants.MAX_NAMED_TAGS) return false

        val updated = currentList.filterNot { it.id == tag.id } + tag
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, updated)
        return true
    }

    fun deleteNamedTag(tag: NamedTag, currentList: List<NamedTag>): List<NamedTag> {
        val updated = currentList.filterNot { it.id == tag.id }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, updated)
        return updated
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveQrTalisman(tag: NamedTag, currentList: List<NamedTag>): Boolean {
        val isUpdate = currentList.any { it.id == tag.id }
        if (!isUpdate && currentList.size >= Constants.MAX_NAMED_TAGS) return false
        val updated = currentList.filterNot { it.id == tag.id } + tag
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.NAMED_TAGS, updated)
        return true
    }
}
