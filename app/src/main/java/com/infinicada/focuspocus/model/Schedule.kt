package com.infinicada.focuspocus.model

data class Schedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String? = "",
    val blockerNames: List<String>? = emptyList(),
    val days: Set<DayOfWeek>? = emptySet(),
    val startTime: String? = "", // "HH:mm"
    val endTime: String? = "", // "HH:mm"
    val unbindingTalismanId: String? = null,
    val breaksEnabled: Boolean = true,
    val breakDurationMinutes: Int = 5,
    val maxBreaksPerSession: Int = 3
) {
    /** Resolved list: uses blockerNames if non-empty, falls back to single blockerName.
     *  Both fields are nullable because Gson can deserialize them as null despite Kotlin defaults,
     *  especially after ProGuard/R8 obfuscation changes field mappings across releases. */
    val effectiveBlockerNames: List<String>
        get() = (blockerNames ?: emptyList()).ifEmpty {
            if (!blockerName.isNullOrEmpty()) listOf(blockerName) else emptyList()
        }

    val effectiveDays: Set<DayOfWeek>
        get() = days ?: emptySet()

    val effectiveStartTime: String
        get() = startTime ?: ""

    val effectiveEndTime: String
        get() = endTime ?: ""
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
