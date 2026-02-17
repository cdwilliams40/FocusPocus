package com.infinicada.focuspocus

data class Blocker(
    val name: String,
    val mode: BlockerMode,
    val apps: List<String>,
    val websites: List<String>? = null
)

enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
