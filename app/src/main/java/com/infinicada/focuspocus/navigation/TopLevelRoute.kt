package com.infinicada.focuspocus.navigation

sealed class TopLevelRoute {
    object Main : TopLevelRoute()
    object Settings : TopLevelRoute()
}
