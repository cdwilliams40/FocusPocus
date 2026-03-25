package com.infinicada.focuspocus.model

import java.util.UUID

data class ConditionalUnlock(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val requiredAppPackage: String,
    val requiredMinutes: Int,
    val unlockedBlockerNames: Set<String>? = emptySet(),
    val unlockedTimeLimitApps: Set<String>? = emptySet()
) {
    /** Null-safe accessor to handle deserialization from older JSON (field was renamed from unlockedApps). */
    val effectiveUnlockedBlockerNames: Set<String>
        get() = unlockedBlockerNames ?: emptySet()

    /** Null-safe accessor for time-limit apps (field may be absent in older JSON). */
    val effectiveUnlockedTimeLimitApps: Set<String>
        get() = unlockedTimeLimitApps ?: emptySet()
}
