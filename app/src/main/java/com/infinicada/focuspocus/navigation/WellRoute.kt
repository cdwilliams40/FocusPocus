package com.infinicada.focuspocus.navigation

/** Well tab navigation: the dashboard, or the editor for the Well's app list. */
sealed class WellRoute {
    data object Overview : WellRoute()

    /** Add apps to the Well, set substitutes, take apps off. */
    data object EditWellApps : WellRoute()
}
