package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import java.util.Calendar

/**
 * Returns true if two schedules overlap at any minute during the week.
 * Correctly accounts for overnight windows (including Sunday-night → Monday-morning wrap).
 */
fun schedulesOverlap(
    daysA: Set<DayOfWeek>, startMinsA: Int, endMinsA: Int,
    daysB: Set<DayOfWeek>, startMinsB: Int, endMinsB: Int
): Boolean {
    if (daysA.isEmpty() || daysB.isEmpty()) return false
    if (startMinsA == endMinsA || startMinsB == endMinsB) return false
    val intervalsA = weeklyIntervals(daysA, startMinsA, endMinsA)
    val intervalsB = weeklyIntervals(daysB, startMinsB, endMinsB)
    return intervalsA.any { (s1, e1) ->
        intervalsB.any { (s2, e2) -> s1 < e2 && s2 < e1 }
    }
}

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

private fun weeklyIntervals(days: Set<DayOfWeek>, startMins: Int, endMins: Int): List<Pair<Int, Int>> {
    val result = ArrayList<Pair<Int, Int>>(days.size * 2)
    val overnight = endMins < startMins
    for (day in days) {
        val base = day.ordinal * MINUTES_PER_DAY
        val start = base + startMins
        if (!overnight) {
            result += start to (base + endMins)
        } else {
            val end = base + MINUTES_PER_DAY + endMins
            if (end <= MINUTES_PER_WEEK) {
                result += start to end
            } else {
                // Sunday night wrapping into Monday morning
                result += start to MINUTES_PER_WEEK
                if (endMins > 0) result += 0 to endMins
            }
        }
    }
    return result
}

/** "HH:mm" → minutes since midnight, or null when absent/malformed. */
fun parseScheduleMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun calendarDayToDayOfWeek(calendarDay: Int): DayOfWeek? = when (calendarDay) {
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    Calendar.SUNDAY -> DayOfWeek.SUNDAY
    else -> null
}

/**
 * Whether [schedule]'s window contains [nowMillis], including the overnight
 * morning carry-over: a Friday-only 22:00–06:00 ritual is active Saturday at
 * 03:00 but not Friday at 03:00 — the same semantics the accessibility
 * service applies on restart (checkMissedScheduleActivation).
 */
fun isScheduleActiveAt(
    schedule: Schedule,
    nowMillis: Long,
    skippedDates: Set<String> = emptySet()
): Boolean {
    val startMins = parseScheduleMinutes(schedule.effectiveStartTime) ?: return false
    val endMins = parseScheduleMinutes(schedule.effectiveEndTime) ?: return false
    if (startMins == endMins) return false
    val days = schedule.effectiveDays
    if (days.isEmpty()) return false

    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentDay = calendarDayToDayOfWeek(cal.get(Calendar.DAY_OF_WEEK)) ?: return false
    val prevCal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val previousDay = calendarDayToDayOfWeek(prevCal.get(Calendar.DAY_OF_WEEK)) ?: return false
    val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val overnight = endMins < startMins

    val todayKey = TrialEngine.dateKeyOf(cal.timeInMillis)
    val prevKey = TrialEngine.dateKeyOf(prevCal.timeInMillis)

    val activeFromToday = currentDay in days && todayKey !in skippedDates &&
        if (overnight) currentMins >= startMins
        else currentMins >= startMins && currentMins < endMins
    val activeFromYesterday = overnight && previousDay in days && prevKey !in skippedDates && currentMins < endMins
    return activeFromToday || activeFromYesterday
}

/**
 * The ritual that should be running at [nowMillis] but isn't: the first whose
 * window is open, that still has one of its enchantments, and that the user
 * didn't end early in this window ([isDismissed]). Shared by the two paths
 * that catch up on a missed start — the accessibility service reconnecting
 * and the alarm backstop.
 */
fun findRitualToResume(
    schedules: List<Schedule>,
    nowMillis: Long,
    existingBlockerNames: Set<String>,
    skippedDatesFor: (scheduleId: String) -> Set<String> = { emptySet() },
    isDismissed: (scheduleId: String) -> Boolean
): Schedule? = schedules.firstOrNull { schedule ->
    isScheduleActiveAt(schedule, nowMillis, skippedDatesFor(schedule.id)) &&
        schedule.effectiveBlockerNames.any { it in existingBlockerNames } &&
        !isDismissed(schedule.id)
}

/**
 * Wall-clock millis when [schedule]'s window ends, resolved from [nowMillis]:
 * the next occurrence of the end time (tomorrow when already passed). Null if
 * the end time is malformed.
 */
fun computeScheduleEndMillis(schedule: Schedule, nowMillis: Long = System.currentTimeMillis()): Long? {
    val endMins = parseScheduleMinutes(schedule.effectiveEndTime) ?: return null
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, endMins / 60)
        set(Calendar.MINUTE, endMins % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= nowMillis) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

/**
 * The earliest upcoming ritual transition (a start or an end) strictly after
 * [nowMillis], or null when no schedule has a complete day+time definition.
 * Drives the AlarmManager backstop: one exact alarm per next transition.
 */
fun nextRitualTransitionMillis(
    schedules: List<Schedule>,
    nowMillis: Long,
    skippedDatesFor: (scheduleId: String) -> Set<String> = { emptySet() }
): Long? {
    var best: Long? = null
    for (schedule in schedules) {
        val startMins = parseScheduleMinutes(schedule.effectiveStartTime) ?: continue
        val endMins = parseScheduleMinutes(schedule.effectiveEndTime) ?: continue
        if (startMins == endMins) continue
        val days = schedule.effectiveDays
        if (days.isEmpty()) continue
        val overnight = endMins < startMins
        val skipped = skippedDatesFor(schedule.id)

        // Walk the coming 14 days so a skipped week still finds the next occurrence.
        for (offset in 0..14) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val day = calendarDayToDayOfWeek(dayCal.get(Calendar.DAY_OF_WEEK)) ?: continue
            if (day !in days) continue
            if (TrialEngine.dateKeyOf(dayCal.timeInMillis) in skipped) continue

            val start = dayCal.atMinutesOfDay(startMins)
            val endCal = if (overnight) {
                (dayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            } else dayCal
            val end = endCal.atMinutesOfDay(endMins)

            for (candidate in longArrayOf(start, end)) {
                if (candidate > nowMillis && (best == null || candidate < best)) {
                    best = candidate
                }
            }
        }
    }
    return best
}

private fun Calendar.atMinutesOfDay(minutes: Int): Long =
    (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
