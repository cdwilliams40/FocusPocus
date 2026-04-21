package com.infinicada.focuspocus

data class Blocker(
    val name: String,
    val mode: BlockerMode,
    val apps: Set<String>? = emptySet(),
    val websites: List<String>? = null
) {
    /** Null-safe accessor — Gson can deserialize apps as null despite Kotlin non-null type. */
    val effectiveApps: Set<String>
        get() = apps ?: emptySet()

    /** Null-safe accessor — Gson can deserialize websites as null despite Kotlin nullable default. */
    val effectiveWebsites: List<String>
        get() = websites ?: emptyList()

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
