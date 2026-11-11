package com.infinicada.focuspocus.limit

import java.util.Calendar
import java.util.Locale

/** "yyyyMMdd" keys for local calendar days. */
object DayKey {
    fun today(): String {
        val cal = Calendar.getInstance()
        // Locale.ROOT: the default locale's number system (e.g. Persian) would
        // emit non-ASCII digits, and these keys are parsed downstream by
        // SimpleDateFormat(Locale.US) and LocalDate.parse(BASIC_ISO_DATE), both
        // of which reject them. Matches TrialEngine.weekKeyForDay.
        return String.format(
            Locale.ROOT, "%04d%02d%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
