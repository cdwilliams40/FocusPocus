package com.infinicada.focuspocus.navigation

import com.infinicada.focuspocus.model.Schedule

sealed class SpellbookRoute {
    object Overview : SpellbookRoute()
    // Enchantments
    object EnchantmentsList : SpellbookRoute()
    object CreateEnchantment : SpellbookRoute()
    object EditEnchantment : SpellbookRoute()
    // Rituals
    object RitualsList : SpellbookRoute()
    object CreateRitual : SpellbookRoute()
    data class EditRitual(val schedule: Schedule) : SpellbookRoute()
    // Talismans
    object Talismans : SpellbookRoute()
}
