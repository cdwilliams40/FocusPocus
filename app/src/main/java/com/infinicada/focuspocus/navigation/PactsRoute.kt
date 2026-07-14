package com.infinicada.focuspocus.navigation

/**
 * Sub-routes of the Pacts (HOME) tab, mirroring the SpellbookRoute pattern:
 * the dashboard overview plus the unified guard editor in its three modes.
 */
sealed class PactsRoute {
    object Overview : PactsRoute()

    /** Create a new guard: pick a target (app or enchantment) and a style. */
    object CreateGuard : PactsRoute()

    /** Edit the explicit per-app config (pact or ward) of [packageName]. */
    data class EditGuard(val packageName: String) : PactsRoute()

    /** Edit the pact circle bound to the enchantment named [blockerName]. */
    data class EditCircle(val blockerName: String) : PactsRoute()
}
