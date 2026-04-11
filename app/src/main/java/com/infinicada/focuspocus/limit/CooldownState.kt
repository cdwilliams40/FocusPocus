package com.infinicada.focuspocus.limit

data class CooldownState(
    val packageName: String,
    /** Epoch millis when the cooldown block expires. */
    val cooldownExpiryMillis: Long,
    /** Number of times the user has tried to open the app during this cooldown. */
    val attemptCount: Int = 0,
    /** Which cooldown number this is in the day (used for escalation). */
    val cooldownNumber: Int = 1
)
