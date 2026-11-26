package com.infinicada.focuspocus.data

import android.content.Context
import com.infinicada.focuspocus.Constants

class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    val settings = SettingsRepository(prefs)
    val blockers = BlockerListRepository(prefs)
    val schedules = ScheduleRepository(prefs)
    val talismans = TalismanRepository(prefs)
    val sessionStore = SessionStore(prefs)
    val session = SessionRepository(context, prefs, sessionStore)
    val insights = InsightsRepository(prefs)
    val progression = ProgressionRepository(context, prefs)
}
