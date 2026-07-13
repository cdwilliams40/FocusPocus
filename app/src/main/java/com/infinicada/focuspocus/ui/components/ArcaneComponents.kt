package com.infinicada.focuspocus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.ui.StarfieldBackground
import com.infinicada.focuspocus.ui.theme.DaySkyBottom
import com.infinicada.focuspocus.ui.theme.DaySkyMid
import com.infinicada.focuspocus.ui.theme.DaySkyTop
import com.infinicada.focuspocus.ui.theme.NightSkyBottom
import com.infinicada.focuspocus.ui.theme.NightSkyMid
import com.infinicada.focuspocus.ui.theme.NightSkyTop

/** True when the current color scheme is the dark (night) variant. */
@Composable
fun isArcaneNight(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The shared arcane sky: a vertical violet gradient with the twinkling
 * starfield on top. Drawn behind every screen so the whole app inhabits
 * the same night (or dawn) sky.
 */
@Composable
fun ArcaneBackground(
    modifier: Modifier = Modifier,
    starCount: Int = 56,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val night = isArcaneNight()
    val skyBrush = if (night) {
        Brush.verticalGradient(listOf(NightSkyTop, NightSkyMid, NightSkyBottom))
    } else {
        Brush.verticalGradient(listOf(DaySkyTop, DaySkyMid, DaySkyBottom))
    }
    Box(modifier = modifier.fillMaxSize().background(skyBrush)) {
        StarfieldBackground(
            starCount = starCount,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (night) 1f else 0.45f }
        )
        content()
    }
}

/**
 * A translucent "enchanted glass" card: frosted surface over the sky
 * gradient with a faint magical rim that catches the light on one edge.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow.copy(alpha = 0.78f),
        // The alpha-modified color can't be resolved by contentColorFor, whose
        // fallback is LocalContentColor -- black when no Surface sits above us
        // (the overlay activity), turning default-colored text invisible in
        // dark mode. Pin the content color explicitly.
        contentColor = scheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    scheme.primary.copy(alpha = 0.35f),
                    scheme.outlineVariant.copy(alpha = 0.25f),
                    scheme.tertiary.copy(alpha = 0.25f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Section label used across screens: small-caps style text with a thin
 * gradient rule trailing to the edge.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.tertiary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(scheme.tertiary.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )
    }
}

/** A compact statistic tile: large serif value over a quiet label. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    GlassCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 16.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Clock-style time formatting for tickers: `m:ss` under an hour,
 * `h:mm:ss` from an hour up. Locale-independent digits.
 */
fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
