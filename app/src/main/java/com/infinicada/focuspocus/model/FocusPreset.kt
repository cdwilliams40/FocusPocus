package com.infinicada.focuspocus.model

data class FocusPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val blockerName: String = "",
    val blockerNames: List<String> = emptyList(),
    val durationMinutes: Int,
    val breaksEnabled: Boolean,
    val talismanId: String? = null,
    val action: PresetAction? = PresetAction.TOGGLE,
    val tempDurationMinutes: Int? = 30
) {
    /** Resolved list: uses blockerNames if non-empty, falls back to single blockerName.
     *  Both fields are null-safe: Gson can deserialize them as null even though Kotlin types are
     *  non-null, so we guard against that to avoid NPEs after a build whose ProGuard rules
     *  obfuscated the field names (changing mapping across releases). */
    val effectiveBlockerNames: List<String>
        get() = (blockerNames ?: emptyList()).ifEmpty {
            @Suppress("SENSELESS_COMPARISON")
            if (blockerName != null && blockerName.isNotEmpty()) listOf(blockerName) else emptyList()
        }
}

enum class PresetAction { TOGGLE, TEMP_ENABLE, TEMP_DISABLE }
