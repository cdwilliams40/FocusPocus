package com.infinicada.focuspocus

data class FocusSession(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val blockerName: String,
    val breaksUsed: Int
)
