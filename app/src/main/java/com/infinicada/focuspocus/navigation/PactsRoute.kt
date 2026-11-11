package com.infinicada.focuspocus.navigation

/** Home tab navigation: the dashboard, or the editor for the Well's app list. */
sealed class PactsRoute {
    object Overview : PactsRoute()

    /** Add apps to the Well, set substitutes, take apps off. */
    object EditWellApps : PactsRoute()
}
