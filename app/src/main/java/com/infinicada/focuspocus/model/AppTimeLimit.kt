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
    val cooldownEscalationStepMinutes: Int = 15,
    /**
     * Pact Mode: the app is blocked by default at all times. Opening it offers a
     * "pact" — the user consciously picks an allowance of a few minutes, and when
     * it runs out the app is sealed in a cooldown for [cooldownMinutes]
     * (escalating per the fields above). Replaces the passive session limit.
     */
    val pactModeEnabled: Boolean = false,
    /** Longest allowance offered in a single pact, in minutes. 0 = use default. */
    val pactMaxMinutes: Int = 15,
    /**
     * Optional healthier substitute offered on the pact overlay ("Open X instead").
     * Reflexes don't disappear — they get rerouted; this gives them somewhere to go.
     */
    val pactAlternativePackage: String? = null,
    /**
     * Guard hours: days on which this guard is enforced. Null or empty = every
     * day. Off-schedule the app is free (though a running seal still blocks —
     * a seal is a seal). Evaluated by GuardWindow.
     */
    val activeDays: Set<DayOfWeek>? = null,
    /** "HH:mm" start of the daily enforcement window. Null/blank = all day. */
    val activeStartTime: String? = null,
    /** "HH:mm" end of the window; before [activeStartTime] means overnight. */
    val activeEndTime: String? = null
)
