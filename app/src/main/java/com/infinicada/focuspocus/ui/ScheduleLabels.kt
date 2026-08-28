package com.infinicada.focuspocus.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import com.infinicada.focuspocus.limit.GuardWindow
import com.infinicada.focuspocus.model.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Locale-aware labels for the day-and-hour parts of rituals and guard hours.
 *
 * Everything the user reads about a schedule used to be derived from the
 * [DayOfWeek] enum's own name ("Mon", "M") and from the raw stored "HH:mm"
 * string, so a French build still said "Mon, Tue" and a phone set to 12-hour
 * time showed "22:00" next to a picker that had just said 10:00 PM. These
 * helpers put both back under the platform's locale data.
 *
 * The pure functions take an explicit [Locale] and display pattern so they can
 * be unit tested on the JVM; the composables below resolve both from the current
 * configuration.
 */
object ScheduleLabels {

    /**
     * The week's days in the order this [locale] starts its week — Monday first
     * in France, Sunday first in the US. Only affects presentation: the stored
     * value is an unordered set either way.
     *
     * Week-start data is keyed by *region*, so a locale carrying no country
     * ("fr" rather than "fr-FR") would silently resolve to the CLDR root's
     * Sunday. There is nothing to localize to in that case, so it keeps the
     * Monday-first order this app has always drawn.
     */
    fun daysInWeekOrder(locale: Locale): List<DayOfWeek> {
        val first = if (locale.country.isNullOrEmpty()) {
            java.time.DayOfWeek.MONDAY.value
        } else {
            WeekFields.of(locale).firstDayOfWeek.value // 1 = Monday
        }
        return List(DayOfWeek.entries.size) { i ->
            DayOfWeek.entries[(first - 1 + i) % DayOfWeek.entries.size]
        }
    }

    /** One-or-two character label for a day chip ("M", "L", "Пн"). */
    fun narrow(day: DayOfWeek, locale: Locale): String =
        javaDay(day).getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    /** Abbreviated label for inline summaries ("Mon", "lun."). */
    fun short(day: DayOfWeek, locale: Locale): String =
        javaDay(day).getDisplayName(TextStyle.SHORT_STANDALONE, locale)

    /**
     * The day's full name. Used as the accessibility label of a chip: several
     * locales — English among them — repeat narrow letters (T/T, S/S), so the
     * chip's visible glyph is not enough on its own for a screen reader.
     */
    fun full(day: DayOfWeek, locale: Locale): String =
        javaDay(day).getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /**
     * [days] rendered in week order with abbreviated names. Sorting matters:
     * the stored set keeps the order the user tapped the chips in, so an
     * unsorted join reads "Wed, Mon, Fri".
     */
    fun shortSummary(days: Set<DayOfWeek>, locale: Locale, separator: String = ", "): String =
        daysInWeekOrder(locale).filter { it in days }.joinToString(separator) { short(it, locale) }

    /** As [shortSummary], with narrow labels — for the space-tight guard cards. */
    fun narrowSummary(days: Set<DayOfWeek>, locale: Locale, separator: String = " "): String =
        daysInWeekOrder(locale).filter { it in days }.joinToString(separator) { narrow(it, locale) }

    /**
     * The canonical "HH:mm" form that gets persisted. Deliberately [Locale.ROOT]:
     * the default locale's number system (Persian, say) would write "۲۲:۰۰" into
     * the store, and everything downstream — the schedule tick, [GuardWindow],
     * the ritual alarms, a backup restored on another phone — treats these as
     * machine-readable ASCII. Matches SessionCooldownManager.todayString().
     */
    fun storedTime(hour: Int, minute: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** Last-resort display patterns when ICU has no skeleton to offer (see [clockPattern]). */
    const val FALLBACK_PATTERN_24H = "HH:mm"
    const val FALLBACK_PATTERN_12H = "h:mm a"

    /**
     * A stored "HH:mm" rendered for display with [pattern] — see [clockPattern],
     * which derives it from the phone's 12/24-hour setting, the same preference
     * `rememberTimePickerState` reads, so a summary and the picker that wrote it
     * never disagree. Unparseable input is passed through untouched.
     */
    fun clockTime(storedTime: String?, pattern: String, locale: Locale): String {
        val minutesOfDay = GuardWindow.parseMinutes(storedTime) ?: return storedTime.orEmpty()
        return clockTime(minutesOfDay / 60, minutesOfDay % 60, pattern, locale)
    }

    /** As [clockTime], from an hour/minute pair (the time pickers' own state). */
    fun clockTime(hour: Int, minute: Int, pattern: String, locale: Locale): String {
        val time = LocalTime.of(hour, minute)
        return try {
            DateTimeFormatter.ofPattern(pattern, locale).format(time)
        } catch (e: RuntimeException) {
            // ICU can answer a skeleton with pattern letters java.time rejects.
            // A plainer-looking clock is a blemish; throwing out of a composable
            // is a crash. 'h' and 'K' are java.time's two 12-hour hour fields.
            val twelveHour = pattern.contains('h') || pattern.contains('K')
            val fallback = if (twelveHour) FALLBACK_PATTERN_12H else FALLBACK_PATTERN_24H
            DateTimeFormatter.ofPattern(fallback, locale).format(time)
        }
    }

    /** [DayOfWeek] is ordered Monday-first, matching java.time's 1..7. */
    private fun javaDay(day: DayOfWeek): java.time.DayOfWeek =
        java.time.DayOfWeek.of(day.ordinal + 1)
}

/** The locale the UI is currently rendered in. */
@Composable
fun currentUiLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/** Whether the phone is set to a 24-hour clock. */
@Composable
fun uses24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

/**
 * The time-of-day pattern to display in: the locale's own preferred rendering of
 * an hour-and-minute skeleton, forced to the clock the user picked. Asking ICU
 * rather than hard-coding "h:mm a" is what keeps the AM/PM marker on the correct
 * side of the digits once the app ships beyond en/fr.
 */
@Composable
fun clockPattern(): String {
    val locale = currentUiLocale()
    val skeleton = if (uses24HourClock()) "Hm" else "hm"
    return remember(locale, skeleton) {
        DateFormat.getBestDateTimePattern(locale, skeleton).ifBlank {
            if (skeleton == "Hm") ScheduleLabels.FALLBACK_PATTERN_24H
            else ScheduleLabels.FALLBACK_PATTERN_12H
        }
    }
}

/** Composable shorthand for [ScheduleLabels.clockTime] on a stored "HH:mm". */
@Composable
fun formatClockTime(storedTime: String?): String =
    ScheduleLabels.clockTime(storedTime, clockPattern(), currentUiLocale())

/** Composable shorthand for [ScheduleLabels.clockTime] on picker state. */
@Composable
fun formatClockTime(hour: Int, minute: Int): String =
    ScheduleLabels.clockTime(hour, minute, clockPattern(), currentUiLocale())
