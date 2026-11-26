package com.infinicada.focuspocus

import kotlinx.serialization.Serializable

@Serializable
data class BlockEvent(
    val packageName: String,
    val timestamp: Long,
    val blockerName: String
) {
    companion object {
        const val PACT_GATE_BLOCKER_NAME = "Pact Gate"
    }
}
