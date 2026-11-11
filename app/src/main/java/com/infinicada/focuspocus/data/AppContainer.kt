package com.infinicada.focuspocus.data

import android.content.Context
import com.google.gson.Gson
import com.infinicada.focuspocus.Constants

class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    val settings = SettingsRepository(prefs)
    val blockers = BlockerListRepository(prefs, gson)
    val schedules = ScheduleRepository(prefs, gson)
    val talismans = TalismanRepository(prefs, gson)
    val session = SessionRepository(context, prefs, gson)
    val insights = InsightsRepository(prefs, gson)
    val progression = ProgressionRepository(context, prefs, gson)
}
