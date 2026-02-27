package com.infinicada.focuspocus

data class AutoTrigger(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: TriggerType,
    val identifier: String,
    val deviceName: String? = null,
    val presetId: String,
    val enabled: Boolean = true
)

enum class TriggerType { WIFI, BLUETOOTH }
