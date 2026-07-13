package com.infinicada.focuspocus.model

/**
 * What a mana ledger entry was for. Entries are structured (not display strings)
 * so history renders in the current locale and perks can query past redemptions.
 */
enum class LedgerKind { SESSION, TRIAL, BOON, PERK, MILESTONE, SIGIL }

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

/** A self-defined real-life reward, redeemed on the honor system. */
data class Boon(
    val id: String,
    val title: String,
    val costMana: Long,
    val note: String = ""
)

/**
 * In-app perks purchasable with mana. Deliberately a small, code-defined
 * catalog: each one trades a little enforcement for a little reward, so new
 * entries deserve the same scrutiny the first two got.
 */
enum class Perk(val costMana: Long) {
    /** One extra break in the current manual session, beyond the per-session max. */
    EXTRA_BREAK(50),

    /** A 10-minute allowance in one pacted/sealed app (once per app per day). */
    SEALED_MINUTES(150);

    companion object {
        const val SEALED_MINUTES_GRANT = 10
    }
}
