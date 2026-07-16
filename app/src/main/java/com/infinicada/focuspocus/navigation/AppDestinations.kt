package com.infinicada.focuspocus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinicada.focuspocus.R

enum class AppDestinations(
    val labelRes: Int,
    val icon: ImageVector,
) {
    /**
     * The app's single front door: the focus-session caster up top and the
     * Pacts guard dashboard below it, one scroll.
     */
    HOME(R.string.nav_home, Icons.Filled.AutoFixHigh),

    SPELLBOOK(R.string.nav_spellbook, Icons.AutoMirrored.Filled.MenuBook),
    INSIGHTS(R.string.nav_insights, Icons.Filled.Insights),
}
