package com.infinicada.focuspocus

import kotlinx.serialization.Serializable

// Older persisted JSON may carry a `websites` field from the retired URL-blocking
// feature; AppJson ignores unknown fields, so those records still deserialize fine.
@Serializable
data class Blocker(
    val name: String,
    val mode: BlockerMode,
    val apps: Set<String>? = emptySet(),
    // Blacklist only: newly installed apps are added to this list automatically.
    val autoAddNewApps: Boolean = false
) {
    val effectiveApps: Set<String>
        get() = apps ?: emptySet()

    fun shouldBlock(packageName: String): Boolean {
        return when (mode) {
            BlockerMode.BLACKLIST -> effectiveApps.contains(packageName)
            BlockerMode.WHITELIST -> !effectiveApps.contains(packageName)
        }
    }

    companion object {
        @Suppress("SENSELESS_COMPARISON")
        fun sanitize(blockers: List<Blocker>?): List<Blocker> =
            blockers?.filterNotNull()?.filter { it.name != null && it.mode != null } ?: emptyList()
    }
}

@Serializable
enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
