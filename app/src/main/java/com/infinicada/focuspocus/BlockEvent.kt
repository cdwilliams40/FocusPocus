package com.infinicada.focuspocus

data class BlockEvent(
    val packageName: String,
    val timestamp: Long,
    val blockerName: String
)
