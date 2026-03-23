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
     *  Null-safe to handle deserialization from older JSON that lacked the blockerNames field. */
    val effectiveBlockerNames: List<String>
        get() = (blockerNames ?: emptyList()).ifEmpty { if (blockerName.isNotEmpty()) listOf(blockerName) else emptyList() }
}

enum class PresetAction { TOGGLE, TEMP_ENABLE, TEMP_DISABLE }
