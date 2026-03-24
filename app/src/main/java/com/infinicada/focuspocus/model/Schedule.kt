package com.infinicada.focuspocus.model

data class Schedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String = "",
    val blockerNames: List<String> = emptyList(),
    val days: Set<DayOfWeek>,
    val startTime: String, // "HH:mm"
    val endTime: String, // "HH:mm"
    val unbindingTalismanId: String? = null,
    val breaksEnabled: Boolean = true,
    val breakDurationMinutes: Int = 5,
    val maxBreaksPerSession: Int = 3
) {
    /** Resolved list: uses blockerNames if non-empty, falls back to single blockerName.
     *  Both fields are null-safe: Gson can deserialize them as null even though Kotlin types are
     *  non-null, so we guard against that to avoid NPEs after a build whose ProGuard rules
     *  obfuscated the field names (changing mapping across releases). */
    val effectiveBlockerNames: List<String>
        get() = (blockerNames ?: emptyList()).ifEmpty {
            @Suppress("SENSELESS_COMPARISON")
            if (blockerName != null && blockerName.isNotEmpty()) listOf(blockerName) else emptyList()
        }
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
