package com.infinicada.focuspocus

data class Blocker(
    val name: String,
    val mode: BlockerMode,
    val apps: Set<String>,
    val websites: List<String>? = null
) {
    fun shouldBlock(packageName: String): Boolean {
        return when (mode) {
            BlockerMode.BLACKLIST -> apps.contains(packageName)
            BlockerMode.WHITELIST -> !apps.contains(packageName)
        }
    }
}

enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
