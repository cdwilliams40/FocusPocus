package com.infinicada.focuspocus.model

import java.util.UUID

data class ConditionalUnlock(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val requiredAppPackage: String,
    val requiredMinutes: Int,
    val unlockedApps: Set<String>
)
