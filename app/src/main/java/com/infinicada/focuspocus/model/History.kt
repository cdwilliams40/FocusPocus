package com.infinicada.focuspocus.model

/**
 * One day of the Mana Well, kept for trials and Insights. Primitive fields
 * only, so a record missing a field (older data) reads as zero.
 */
data class WellDay(
    /** Net time drawn: pacts made minus time handed back by ending early. */
    val drawnMillis: Long = 0L,
    val refundedMillis: Long = 0L,
    /** Pacts made. */
    val draws: Int = 0,
    /** Mana pours into the Well. */
    val pours: Int = 0,
    /** Times a Well app was opened with too little in the Well for a pact. */
    val dryBlocks: Int = 0,
    /** Package → millis drawn for it (before refunds). */
    val perApp: Map<String, Long>? = null
)

/** One day of mana, by where it came from and where it went. */
data class ManaDay(
    val timedSession: Long = 0L,
    val openEndedSession: Long = 0L,
    val productive: Long = 0L,
    val trials: Long = 0L,
    /** Mana spent on pours, and on extra breaks. */
    val pours: Long = 0L,
    val breaks: Long = 0L,
    /** Minutes spent in productive apps, as last measured. */
    val productiveMinutes: Int = 0
) {
    val earned: Long get() = timedSession + openEndedSession + productive + trials
    val spent: Long get() = pours + breaks
}
