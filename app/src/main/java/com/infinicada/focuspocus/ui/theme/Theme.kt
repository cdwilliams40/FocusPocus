package com.infinicada.focuspocus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

private val DarkColorScheme = darkColorScheme(
    primary = GlowingPurple,
    onPrimary = OnDarkPrimary,
    primaryContainer = MysticalPurpleDark,
    onPrimaryContainer = GlowingPurple,

    secondary = MidnightBlueLight,
    onSecondary = OnDarkPrimary,
    secondaryContainer = MidnightBlueDark,
    onSecondaryContainer = Color(0xFFB0BEC5),

    tertiary = GlowingGold,
    onTertiary = Color(0xFF1A1A1A),
    tertiaryContainer = ArcaneGoldDark,
    onTertiaryContainer = ArcaneGoldLight,

    background = DarkEnchantedBackground,
    onBackground = OnDarkSecondary,

    surface = DarkEnchantedSurface,
    onSurface = OnDarkSecondary,
    surfaceVariant = DarkEnchantedSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),

    error = SpellError,
    onError = OnDarkPrimary,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = MysticalPurple,
    onPrimary = OnLightPrimary,
    primaryContainer = MysticalPurpleLight,
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = MidnightBlue,
    onSecondary = OnLightPrimary,
    secondaryContainer = MidnightBlueLight,
    onSecondaryContainer = Color(0xFFFFFFFF),

    tertiary = ArcaneGoldDark,
    onTertiary = Color(0xFF1A1A1A),
    tertiaryContainer = ArcaneGold,
    onTertiaryContainer = Color(0xFF1A1A1A),

    background = ParchmentBackground,
    onBackground = OnLightSecondary,

    surface = ParchmentSurface,
    onSurface = OnLightSecondary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FocusPocusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Disable dynamic color to use our mystical theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
