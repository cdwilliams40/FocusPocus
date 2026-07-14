package com.infinicada.focuspocus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinicada.focuspocus.R

enum class AppDestinations(
    val labelRes: Int,
    val icon: ImageVector,
) {
    /** The Pacts guard dashboard — the app's default screen. */
    HOME(R.string.nav_pacts, Icons.Filled.Shield),

    /** Focus sessions: the cast flow, timers, and breaks. */
    FOCUS(R.string.nav_focus, Icons.Filled.AutoFixHigh),

    SPELLBOOK(R.string.nav_spellbook, Icons.AutoMirrored.Filled.MenuBook),
    INSIGHTS(R.string.nav_insights, Icons.Filled.Insights),
}
