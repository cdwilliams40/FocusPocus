package com.infinicada.focuspocus

import com.infinicada.focuspocus.model.DayOfWeek
import com.infinicada.focuspocus.model.Schedule
import java.util.Calendar

/**
 * Determines if the schedule should deactivate at the current time.
 * Handles both same-day schedules (start < end) and overnight schedules (start > end).
 * Uses "at or past" logic so missed end times are caught on service restart.
 */
fun shouldDeactivateSchedule(
    currentHour: Int, currentMinute: Int,
    startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int
): Boolean {
    if (currentHour !in 0..23 || currentMinute !in 0..59) return false
    if (startHour !in 0..23 || startMinute !in 0..59) return false
    if (endHour !in 0..23 || endMinute !in 0..59) return false

    val currentMins = currentHour * 60 + currentMinute
    val startMins = startHour * 60 + startMinute
    val endMins = endHour * 60 + endMinute

    return if (endMins > startMins) {
        // Same-day schedule: deactivate whenever we're outside the active
        // window. This check only runs while the schedule is active, so a
        // current time before the start means the end was missed across
        // midnight (phone off overnight) — without the second clause the
        // block would persist until the *next* day's end time.
        currentMins >= endMins || currentMins < startMins
    } else {
        // Overnight schedule: deactivate when past end time AND before start time
        // (i.e., in the "morning after" window, not the "active evening" window)
        currentMins >= endMins && currentMins < startMins
    }
}

/**
 * Returns true when the current time falls inside a schedule's active window.
 * Handles both same-day schedules (startMins < endMins) and overnight schedules
 * (startMins > endMins, e.g. 22:00–06:00).
 */
fun isWithinScheduleWindow(
    currentHour: Int, currentMinute: Int,
    startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int
): Boolean {
    if (currentHour !in 0..23 || currentMinute !in 0..59) return false
    if (startHour !in 0..23 || startMinute !in 0..59) return false
    if (endHour !in 0..23 || endMinute !in 0..59) return false

    val currentMins = currentHour * 60 + currentMinute
    val startMins = startHour * 60 + startMinute
    val endMins = endHour * 60 + endMinute

    return when {
        endMins > startMins -> currentMins >= startMins && currentMins < endMins
        endMins < startMins -> currentMins >= startMins || currentMins < endMins
        else -> false // startMins == endMins: treat as zero-duration, never active
    }
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
fun isScheduleActiveAt(schedule: Schedule, nowMillis: Long): Boolean {
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

    val activeFromToday = currentDay in days &&
        if (overnight) currentMins >= startMins
        else currentMins >= startMins && currentMins < endMins
    val activeFromYesterday = overnight && previousDay in days && currentMins < endMins
    return activeFromToday || activeFromYesterday
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
fun nextRitualTransitionMillis(schedules: List<Schedule>, nowMillis: Long): Long? {
    var best: Long? = null
    for (schedule in schedules) {
        val startMins = parseScheduleMinutes(schedule.effectiveStartTime) ?: continue
        val endMins = parseScheduleMinutes(schedule.effectiveEndTime) ?: continue
        if (startMins == endMins) continue
        val days = schedule.effectiveDays
        if (days.isEmpty()) continue
        val overnight = endMins < startMins

        // Walk the coming week (8 offsets cover today's already-passed times).
        for (offset in 0..7) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val day = calendarDayToDayOfWeek(dayCal.get(Calendar.DAY_OF_WEEK)) ?: continue
            if (day !in days) continue

            val start = dayCal.atMinutesOfDay(startMins)
            val endCal = if (overnight) {
                (dayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            } else dayCal
            val end = endCal.atMinutesOfDay(endMins)

            for (candidate in longArrayOf(start, end)) {
                if (candidate > nowMillis && (best == null || candidate < best!!)) {
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
