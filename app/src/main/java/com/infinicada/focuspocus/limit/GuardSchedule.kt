package com.infinicada.focuspocus.limit

import com.infinicada.focuspocus.model.AppTimeLimit
import com.infinicada.focuspocus.model.DayOfWeek
import java.util.Calendar

/**
 * Active-hours resolution for guards ("this pact only applies after 9 pm").
 * Pure day/time arithmetic so it is unit-testable; the [Calendar] overloads
 * are thin adapters for callers on the enforcement and UI sides.
 *
 * Semantics: a guard with no days and no times is always active. Days without
 * times mean "all day on those days". A window whose end is at or before its
 * start spans midnight and belongs to its *start* day — the guard stays
 * active into the next morning, mirroring ritual windows.
 */
object GuardSchedule {

    /** True when [config] carries any active-hours restriction at all. */
    fun hasSchedule(config: AppTimeLimit): Boolean =
        !config.activeDays.isNullOrEmpty() ||
            !config.activeStartTime.isNullOrBlank() ||
            !config.activeEndTime.isNullOrBlank()

    /** Whether [config]'s guard is enforcing at the given local time. */
    fun isActiveAt(
        config: AppTimeLimit,
        dayOfWeek: DayOfWeek,
        previousDay: DayOfWeek,
        minuteOfDay: Int
    ): Boolean = isActiveAt(
        config.activeDays, config.activeStartTime, config.activeEndTime,
        dayOfWeek, previousDay, minuteOfDay
    )

    fun isActiveAt(
        activeDays: Set<DayOfWeek>?,
        activeStartTime: String?,
        activeEndTime: String?,
        dayOfWeek: DayOfWeek,
        previousDay: DayOfWeek,
        minuteOfDay: Int
    ): Boolean {
        val days = activeDays ?: emptySet()
        val dayMatches = days.isEmpty() || dayOfWeek in days
        val start = parseMinutes(activeStartTime)
        val end = parseMinutes(activeEndTime)
        if (start == null || end == null) {
            // No (valid) time window: day gating alone applies.
            return dayMatches
        }
        if (start == end) {
            // Zero-length window: treat as no time restriction, day gating only.
            return dayMatches
        }
        return if (end > start) {
            dayMatches && minuteOfDay >= start && minuteOfDay < end
        } else {
            // Overnight window, anchored to its start day: tonight's stretch
            // counts against today, the small-hours tail against yesterday.
            (dayMatches && minuteOfDay >= start) ||
                ((days.isEmpty() || previousDay in days) && minuteOfDay < end)
        }
    }

    /** Whether [config]'s guard is enforcing right now. */
    fun isActiveNow(
        config: AppTimeLimit,
        calendar: Calendar = Calendar.getInstance()
    ): Boolean {
        if (!hasSchedule(config)) return true
        val day = dayOfWeekOf(calendar)
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return isActiveAt(config, day, previousDayOf(day), minute)
    }

    fun previousDayOf(day: DayOfWeek): DayOfWeek =
        DayOfWeek.entries[(day.ordinal + DayOfWeek.entries.size - 1) % DayOfWeek.entries.size]

    fun dayOfWeekOf(calendar: Calendar): DayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

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
}
