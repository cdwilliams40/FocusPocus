package com.infinicada.focuspocus

import java.util.UUID

data class FocusSession(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val blockerName: String,
    val breaksUsed: Int
)
