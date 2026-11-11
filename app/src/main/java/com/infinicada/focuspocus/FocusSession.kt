package com.infinicada.focuspocus

data class FocusSession(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val blockerName: String,
    val breaksUsed: Int,
    /**
     * What started the session: [SOURCE_MANUAL], [SOURCE_RITUAL] or
     * [SOURCE_TALISMAN]. Null on sessions recorded before 2.0.
     */
    val source: String? = null,
    /** The length chosen when it started, for a timed session; null or 0 when open-ended. */
    val plannedMinutes: Int? = null
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_RITUAL = "ritual"
        const val SOURCE_TALISMAN = "talisman"
    }
}
