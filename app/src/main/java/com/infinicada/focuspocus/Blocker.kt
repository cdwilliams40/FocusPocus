package com.infinicada.focuspocus

// Older persisted JSON may carry a `websites` field from the retired URL-blocking
// feature; Gson ignores unknown fields, so those records still deserialize fine.
data class Blocker(
    val name: String,
    val mode: BlockerMode,
    val apps: Set<String>? = emptySet(),
    // Blacklist only: newly installed apps are added to this list automatically.
    // Gson deserializes the field as false when absent in stored JSON.
    val autoAddNewApps: Boolean = false
) {
    /** Null-safe accessor — Gson can deserialize apps as null despite Kotlin non-null type. */
    val effectiveApps: Set<String>
        get() = apps ?: emptySet()

    fun shouldBlock(packageName: String): Boolean {
        return when (mode) {
            BlockerMode.BLACKLIST -> effectiveApps.contains(packageName)
            BlockerMode.WHITELIST -> !effectiveApps.contains(packageName)
        }
    }
}

enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
