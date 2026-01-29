package com.example.focuspocus

data class Blocker(val name: String, val mode: BlockerMode, val apps: List<String>)

enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
