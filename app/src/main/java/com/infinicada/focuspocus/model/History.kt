package com.infinicada.focuspocus.model

import kotlinx.serialization.Serializable

/** Optional 1-tap reason chosen during the 3-second pact pause. */
@Serializable
enum class PactIntention {
    QUICK_REPLY,
    SPECIFIC_TASK,
    UNWINDING
}

/**
 * One day of the Mana Well, kept for trials and Insights. Primitive fields
 * and nullable maps only, so a record missing a field (older data) reads cleanly.
 */
@Serializable
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
    val perApp: Map<String, Long>? = null,
    /** Times the user chose a healthier substitute instead of opening a blocked app. */
    val substituteWins: Int = 0,
    /** PactIntention.name → count of pacts drawn with that intention. */
    val intentions: Map<String, Int>? = null,
    /** Local hour of day (0..23) → minutes drawn from the Well during that hour. */
    val drawsByHour: Map<Int, Int>? = null
)

/** One day of mana, by where it came from and where it went. */
@Serializable
data class ManaDay(
    val timedSession: Long = 0L,
    val openEndedSession: Long = 0L,
    val productive: Long = 0L,
    val trials: Long = 0L,
    /** Uncapped mana earned from choosing healthier substitutes on block screens. */
    val substitute: Long = 0L,
    /** Mana spent on pours, and on extra breaks. */
    val pours: Long = 0L,
    val breaks: Long = 0L,
    /** Minutes spent in productive apps, as last measured. */
    val productiveMinutes: Int = 0
) {
    val earned: Long get() = timedSession + openEndedSession + productive + trials + substitute
    val spent: Long get() = pours + breaks
}
