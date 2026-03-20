package com.infinicada.focuspocus.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infinicada.focuspocus.Constants
import com.infinicada.focuspocus.PrefsHelper
import com.infinicada.focuspocus.model.Schedule

class ScheduleRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getSchedules(): List<Schedule> {
        val type = object : TypeToken<List<Schedule>>() {}.type
        return PrefsHelper.load<List<Schedule>>(prefs, gson, Constants.PrefsKeys.SCHEDULES, type)
            ?: emptyList()
    }

    /**
     * @return true if saved successfully, false if at max capacity
     */
    fun saveSchedule(schedule: Schedule, currentList: List<Schedule>): Boolean {
        val isUpdate = currentList.any { it.id == schedule.id }
        if (!isUpdate && currentList.size >= Constants.MAX_SCHEDULES) return false

        val updated = currentList.filterNot { it.id == schedule.id } + schedule
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.SCHEDULES, updated)
        return true
    }

    fun deleteSchedule(schedule: Schedule, currentList: List<Schedule>): List<Schedule> {
        val updated = currentList.filterNot { it.id == schedule.id }
        PrefsHelper.save(prefs, gson, Constants.PrefsKeys.SCHEDULES, updated)
        return updated
    }

    fun cleanupOrphanedSchedules(
        schedules: List<Schedule>,
        blockerNames: Set<String>,
        talismanIds: Set<String>
    ): List<Schedule> {
        val cleaned = schedules.mapNotNull { schedule ->
            if (schedule.blockerName !in blockerNames) {
                null
            } else if (schedule.unbindingTalismanId != null && schedule.unbindingTalismanId !in talismanIds) {
                schedule.copy(unbindingTalismanId = null)
            } else {
                schedule
            }
        }
        if (cleaned.size != schedules.size || cleaned != schedules) {
            PrefsHelper.save(prefs, gson, Constants.PrefsKeys.SCHEDULES, cleaned)
        }
        return cleaned
    }
}
