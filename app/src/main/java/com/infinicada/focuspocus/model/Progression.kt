package com.infinicada.focuspocus.model

/**
 * What a mana ledger entry was for. Entries are structured (not display strings)
 * so history renders in the current locale and perks can query past redemptions.
 */
enum class LedgerKind {
    /** A timed session, paid per minute. */
    SESSION,
    TRIAL,
    /** A pour into the Well or an extra break; refId names the [Perk]. */
    PERK,
    /** An open-ended session (ritual or untimed talisman), paid flat once a day. */
    OPEN_ENDED,
    /** Time in productive apps. */
    PRODUCTIVE,


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
    /** Legacy BOON entries: the user's own boon title (shown verbatim). */
    val title: String = "",
    /** TRIAL id (or legacy SIGIL id), or [Perk] name for PERK entries. */
    val refId: String = "",
    /** Legacy PERK (sealed minutes) entries: which app it was for. */
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
    EXTRA_BREAK(com.infinicada.focuspocus.ManaRules.EXTRA_BREAK_COST),

    /**
     * Minutes poured into the Mana Well. [costMana] is the day's first price;
     * every further channel the same day doubles it (ManaRules.pourCost).
     */
    WELL_CHANNEL(com.infinicada.focuspocus.ManaRules.POUR_BASE_COST);

    companion object {
        const val WELL_CHANNEL_GRANT = com.infinicada.focuspocus.ManaRules.POUR_MINUTES
    }
}
