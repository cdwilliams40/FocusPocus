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
     *  Null-safe to handle deserialization from older JSON that lacked the blockerNames field. */
    val effectiveBlockerNames: List<String>
        get() = (blockerNames ?: emptyList()).ifEmpty { if (blockerName.isNotEmpty()) listOf(blockerName) else emptyList() }
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
