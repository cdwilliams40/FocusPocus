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

    companion object {
        /**
         * Drops records Gson left in an unusable state. Gson instantiates classes
         * via Unsafe — no constructor, no Kotlin null checks — so a blocker persisted
         * by a build with broken R8 keep rules (v1.4 obfuscated the [BlockerMode] enum
         * constant names) can come back with a null [name] or [mode] despite the
         * non-null Kotlin types. A null [mode] makes [shouldBlock]'s exhaustive
         * `when` throw: the accessibility service and DeviceOwnerManager swallow it
         * (silently disabling ALL blocking / suspension), and
         * FocusNotificationListenerService lets it crash the process. Filtering such
         * records here — as PactManager, Progression and OpenReflexTracker already do
         * for their models — keeps one corrupt enchantment from poisoning the rest.
         */
        @Suppress("SENSELESS_COMPARISON")
        fun sanitize(blockers: List<Blocker>?): List<Blocker> =
            blockers?.filterNotNull()?.filter { it.name != null && it.mode != null } ?: emptyList()
    }
}

enum class BlockerMode {
    BLACKLIST,
    WHITELIST
}
