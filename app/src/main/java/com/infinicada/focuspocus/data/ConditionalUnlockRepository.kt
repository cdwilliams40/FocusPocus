package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.ConditionalUnlock

class ConditionalUnlockRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getConditionalUnlocks(): List<ConditionalUnlock> {
        val type = object : TypeToken<List<ConditionalUnlock>>() {}.type
        return PrefsHelper.load<List<ConditionalUnlock>>(prefs, gson, Constants.PrefsKeys.CONDITIONAL_UNLOCKS, type)
            ?: emptyList()
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveConditionalUnlock(rule: ConditionalUnlock, currentList: List<ConditionalUnlock>): Boolean {
        val isUpdate = currentList.any { it.id == rule.id }
        if (!isUpdate && currentList.size >= Constants.MAX_CONDITIONAL_UNLOCKS) return false
        val updated = currentList.filterNot { it.id == rule.id } + rule
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.CONDITIONAL_UNLOCKS, updated)
        return true
    }

    fun deleteConditionalUnlock(rule: ConditionalUnlock, currentList: List<ConditionalUnlock>): List<ConditionalUnlock> {
        val updated = currentList.filterNot { it.id == rule.id }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.CONDITIONAL_UNLOCKS, updated)
        return updated
    }
}
