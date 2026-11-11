package com.infinicada.focuspocus

/**
 * An NFC tag the user has named. A tap starts this talisman's session (its
 * enchantments, for [durationMinutes] or open-ended) and, when [requireToEnd]
 * holds, only another tap of the same tag ends it. A ritual can also name a
 * talisman as its key (Schedule.unbindingTalismanId).
 *
 * The setup fields are nullable on purpose: talismans stored before 2.0 have
 * none, and Gson leaves absent fields null rather than applying Kotlin
 * defaults. Read them through the accessors below.
 */
data class Talisman(
    val id: String,
    val name: String,
    val blockerNames: List<String>? = null,
    /** Null or 0: open-ended, until the talisman is tapped again. */
    val durationMinutes: Int? = null,
    /** Null means on. */
    val breaksEnabled: Boolean? = null,
    /** Null means on: only this talisman can end its session. */
    val requireToEnd: Boolean? = null
) {
    val enchantments: List<String> get() = blockerNames.orEmpty()
    val timedMinutes: Int? get() = durationMinutes?.takeIf { it > 0 }
    val breaksAllowed: Boolean get() = breaksEnabled != false
    val locksSession: Boolean get() = requireToEnd != false
}
