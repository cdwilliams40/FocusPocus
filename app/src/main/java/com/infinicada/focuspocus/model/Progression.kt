package com.infinicada.focuspocus.model

/**
 * What a mana ledger entry was for. Entries are structured (not display strings)
 * so history renders in the current locale and perks can query past redemptions.
 */
enum class LedgerKind {
    SESSION, TRIAL, PERK,

    /** Legacy kinds from before 2.0 (boons, streak milestones, sigils). Never
     *  written any more, but kept so old ledger entries still load. */
    BOON, MILESTONE, SIGIL
}

data class ManaLedgerEntry(
    val timestampMillis: Long,
    /** Positive when earned, negative when spent. */
    val amount: Long,
    val kind: LedgerKind,
    /** SESSION: focused minutes the award was based on. */
    val minutes: Int = 0,
    /** BOON: the user's own boon title (user data, shown verbatim). */
    val title: String = "",
    /** TRIAL/SIGIL id, or [Perk] name for PERK entries. */
    val refId: String = "",
    /** PERK (sealed minutes): which app the allowance was granted to. */
    val packageName: String = "",
    /** "yyyyMMdd" of the day the entry was written (drives per-day perk caps). */
    val dateKey: String = ""
)

/**
 * In-app perks purchasable with mana. Deliberately a small, code-defined
 * catalog: each one trades a little enforcement for a little reward.
 */
enum class Perk(val costMana: Long) {
    /** One extra break in the current manual session, beyond the per-session max. */
    EXTRA_BREAK(50),

    /**
     * Minutes poured into the Mana Well. [costMana] is the day's first price;
     * every further channel the same day doubles it
     * (ProgressionMath.wellChannelCost), so banked mana can't buy a binge.
     */
    WELL_CHANNEL(75);

    companion object {
        const val WELL_CHANNEL_GRANT = 5
    }
}
