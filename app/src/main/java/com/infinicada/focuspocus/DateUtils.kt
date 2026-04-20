package com.infinicada.focuspocus

import java.util.Calendar

fun calculateCurrentStreak(sessions: List<FocusSession>): Int {
    if (sessions.isEmpty()) return 0
    val cal = Calendar.getInstance()
    val today = Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    // Group sessions by calendar day
    val daysWithSessions = sessions.map { session ->
        val c = Calendar.getInstance().apply { timeInMillis = session.endTimeMillis }
        Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }.toSet()

    var streak = 0
    val checkCal = Calendar.getInstance()

    // Check if today has a session; if not, start from yesterday
    if (today in daysWithSessions) {
        streak = 1
        checkCal.add(Calendar.DAY_OF_YEAR, -1)
    } else {
        checkCal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = Triple(checkCal.get(Calendar.YEAR), checkCal.get(Calendar.MONTH) + 1, checkCal.get(Calendar.DAY_OF_MONTH))
        if (yesterday !in daysWithSessions) return 0
    }

    // Count consecutive days backwards
    while (true) {
        val day = Triple(checkCal.get(Calendar.YEAR), checkCal.get(Calendar.MONTH) + 1, checkCal.get(Calendar.DAY_OF_MONTH))
        if (day in daysWithSessions) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return streak
}
