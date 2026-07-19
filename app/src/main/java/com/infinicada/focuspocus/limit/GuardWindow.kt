package com.infinicada.focuspocus.limit

import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.DayOfWeek
import java.util.Calendar

/**
 * Guard-hours resolution: whether a guard's schedule makes it enforceable right
 * now. A guard with no schedule (no days, no time window) is always active.
 *
 * Semantics mirror rituals: a same-day window runs start ≤ t < end on each
 * selected day; an overnight window (end before start) runs from the selected
 * day's start time into the *following* morning, so a Friday-only 22:00–06:00
 * guard is active Saturday at 03:00 but not Friday at 03:00.
 *
 * Malformed or half-set times fail open (guard active) — protection first.
 */
object GuardWindow {

    /** Whether [config]'s guard hours make it enforceable at [now]. */
    fun isActiveNow(config: AppTimeLimit, now: Long = System.currentTimeMillis()): Boolean =
        isActiveAt(config.activeDays, config.activeStartTime, config.activeEndTime, now)

    fun isActiveAt(
        days: Set<DayOfWeek>?,
        startTime: String?,
        endTime: String?,
        now: Long
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val day = dayOfWeekOf(cal) ?: return true
        val prevCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val previousDay = dayOfWeekOf(prevCal) ?: return true
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isActive(days, startTime, endTime, day, previousDay, minutesOfDay)
    }

    /**
     * Pure core, unit-testable without wall clocks: [day] is today,
     * [previousDay] yesterday (for overnight carry-over), [minutesOfDay] the
     * local time as minutes since midnight.
     */
    fun isActive(
        days: Set<DayOfWeek>?,
        startTime: String?,
        endTime: String?,
        day: DayOfWeek,
        previousDay: DayOfWeek,
        minutesOfDay: Int
    ): Boolean {
        val hasDays = !days.isNullOrEmpty()
        val startMins = parseMinutes(startTime)
        val endMins = parseMinutes(endTime)
        // A window needs both ends, and identical times are zero-duration
        // nonsense — both degrade to "all day" rather than disabling the guard.
        val hasWindow = startMins != null && endMins != null && startMins != endMins

        if (!hasWindow) return !hasDays || day in days!!

        val dayMatches = !hasDays || day in days!!
        val previousDayMatches = !hasDays || previousDay in days!!
        return if (endMins!! > startMins!!) {
            dayMatches && minutesOfDay >= startMins && minutesOfDay < endMins
        } else {
            // Overnight: the evening part belongs to the selected day, the
            // morning part to the day after it.
            (dayMatches && minutesOfDay >= startMins) ||
                (previousDayMatches && minutesOfDay < endMins)
        }
    }

    /** True when [config] carries any schedule at all (days or a time window). */
    fun isScheduled(config: AppTimeLimit): Boolean =
        !config.activeDays.isNullOrEmpty() ||
            (parseMinutes(config.activeStartTime) != null && parseMinutes(config.activeEndTime) != null)

    /** "HH:mm" → minutes since midnight, or null when absent/malformed. */
    fun parseMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun dayOfWeekOf(cal: Calendar): DayOfWeek? = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        else -> null
    }
}
