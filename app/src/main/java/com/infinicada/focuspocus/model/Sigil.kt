package com.infinicada.focuspocus.model

import com.infinicada.focuspocus.R

/**
 * A permanent milestone. The catalog is code-defined; only the set of unlocked
 * ids is persisted, so the catalog can grow without migrations.
 */
data class Sigil(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int
)

object SigilCatalog {
    const val FIRST_SPELL = "first_spell"
    const val RITUAL_KEPT = "ritual_kept"
    const val IRON_WILL = "iron_will"
    const val DEEP_TRANCE = "deep_trance"
    const val TEN_HOUR_WEEK = "ten_hour_week"
    const val HUNDRED_CASTINGS = "hundred_castings"
    const val STREAK_7 = "streak_7"
    const val STREAK_30 = "streak_30"
    const val STREAK_100 = "streak_100"
    const val WARDEN = "warden"
    const val FIRST_BOON = "first_boon"
    const val FONT_OF_MANA = "font_of_mana"

    val ALL = listOf(
        Sigil(FIRST_SPELL, R.string.sigil_first_spell_title, R.string.sigil_first_spell_desc),
        Sigil(RITUAL_KEPT, R.string.sigil_ritual_kept_title, R.string.sigil_ritual_kept_desc),
        Sigil(IRON_WILL, R.string.sigil_iron_will_title, R.string.sigil_iron_will_desc),
        Sigil(DEEP_TRANCE, R.string.sigil_deep_trance_title, R.string.sigil_deep_trance_desc),
        Sigil(TEN_HOUR_WEEK, R.string.sigil_ten_hour_week_title, R.string.sigil_ten_hour_week_desc),
        Sigil(HUNDRED_CASTINGS, R.string.sigil_hundred_castings_title, R.string.sigil_hundred_castings_desc),
        Sigil(STREAK_7, R.string.sigil_streak_7_title, R.string.sigil_streak_7_desc),
        Sigil(STREAK_30, R.string.sigil_streak_30_title, R.string.sigil_streak_30_desc),
        Sigil(STREAK_100, R.string.sigil_streak_100_title, R.string.sigil_streak_100_desc),
        Sigil(WARDEN, R.string.sigil_warden_title, R.string.sigil_warden_desc),
        Sigil(FIRST_BOON, R.string.sigil_first_boon_title, R.string.sigil_first_boon_desc),
        Sigil(FONT_OF_MANA, R.string.sigil_font_of_mana_title, R.string.sigil_font_of_mana_desc)
    )

    fun byId(id: String): Sigil? = ALL.firstOrNull { it.id == id }
}
