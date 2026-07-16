package com.infinicada.focuspocus.model

/**
 * A pact configuration bound to a blacklist enchantment instead of a single app:
 * every app in the enchantment is pact-gated with these settings. Membership is
 * resolved live, so apps added to the enchantment later (including auto-banished
 * new installs) are covered automatically.
 *
 * An explicit per-app [AppTimeLimit] config always wins over group membership.
 */
data class PactGroup(
    val blockerName: String,
    /** Longest allowance offered in a single pact, in minutes. 0 = default. */
    val pactMaxMinutes: Int = 15,
    /** Seal duration after a lapsed pact, in minutes. */
    val cooldownMinutes: Int = 30,
    /** If true, each subsequent seal in the same day gets longer. */
    val cooldownEscalationEnabled: Boolean = false,
    /** Extra minutes added per escalation step. */
    val cooldownEscalationStepMinutes: Int = 15,
    /** Optional healthier substitute offered on the pact overlay. */
    val pactAlternativePackage: String? = null,
    /** Optional daily cap on top of the pact gate. 0 = no daily cap. */
    val dailyLimitMinutes: Int = 0,
    /** Optional active-hours schedule, same semantics as [AppTimeLimit]. */
    val activeDays: Set<DayOfWeek>? = null,
    val activeStartTime: String? = null,
    val activeEndTime: String? = null
) {
    /** The per-app view of this group used by the enforcement layer. */
    fun toAppTimeLimit(packageName: String): AppTimeLimit = AppTimeLimit(
        packageName = packageName,
        dailyLimitMinutes = dailyLimitMinutes,
        sessionLimitMinutes = 0,
        cooldownMinutes = cooldownMinutes,
        cooldownEscalationEnabled = cooldownEscalationEnabled,
        cooldownEscalationStepMinutes = cooldownEscalationStepMinutes,
        pactModeEnabled = true,
        pactMaxMinutes = pactMaxMinutes,
        pactAlternativePackage = pactAlternativePackage,
        activeDays = activeDays,
        activeStartTime = activeStartTime,
        activeEndTime = activeEndTime
    )
}
