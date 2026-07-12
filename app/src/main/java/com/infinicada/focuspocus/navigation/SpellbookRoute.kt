package com.infinicada.focuspocus.navigation

import com.infinicada.focuspocus.model.FocusPreset
import com.infinicada.focuspocus.model.Schedule

sealed class SpellbookRoute {
    object Overview : SpellbookRoute()
    // Enchantments
    object EnchantmentsList : SpellbookRoute()
    object CreateEnchantment : SpellbookRoute()
    object EditEnchantment : SpellbookRoute()
    // Quick Spells
    object QuickSpellsList : SpellbookRoute()
    object CreateQuickSpell : SpellbookRoute()
    data class EditQuickSpell(val preset: FocusPreset) : SpellbookRoute()
    // Rituals
    object RitualsList : SpellbookRoute()
    object CreateRitual : SpellbookRoute()
    data class EditRitual(val schedule: Schedule) : SpellbookRoute()
    // Talismans & Time Limits
    object Talismans : SpellbookRoute()
    object TimeLimits : SpellbookRoute()
    // Pacts
    object Pacts : SpellbookRoute()
}
