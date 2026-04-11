package com.infinicada.focuspocus.model

data class AppTimeLimit(
    val packageName: String,
    val dailyLimitMinutes: Int,
    /** Minutes of continuous use before a cooldown is triggered. 0 = disabled. */
    val sessionLimitMinutes: Int = 0,
    /** Duration of the cooldown block in minutes. */
    val cooldownMinutes: Int = 30,
    /** If true, each subsequent cooldown in the same day gets longer. */
    val cooldownEscalationEnabled: Boolean = false,
    /** Extra minutes added per escalation step. */
    val cooldownEscalationStepMinutes: Int = 15
)
