package com.infinicada.focuspocus

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
        // Same-day schedule: deactivate at or past end time
        currentMins >= endMins
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
