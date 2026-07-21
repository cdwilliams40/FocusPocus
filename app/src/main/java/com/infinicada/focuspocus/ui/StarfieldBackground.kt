package com.infinicada.focuspocus.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val phase: Float,
    val maxAlpha: Float
)

// One twinkle cycle is divided into this many discrete animation steps. A
// stepped Int animation only changes state when the step does (~12x/s here),
// so the canvas redraws at that rate instead of every display frame — a slow
// subtle twinkle reads identically, for a fraction of the draw work. The step
// count divides the cycle evenly, so the sine is continuous across restarts.
private const val TWINKLE_STEPS = 96

/**
 * A subtle field of slowly twinkling stars drawn behind screen content.
 * Positions are seeded deterministically so the sky doesn't reshuffle
 * between recompositions or screen visits. Most stars glow in the primary
 * lavender; every fifth one twinkles gold.
 */
@Composable
fun StarfieldBackground(
    modifier: Modifier = Modifier,
    starCount: Int = 48
) {
    val stars = remember(starCount) {
        val random = Random(1337)
        List(starCount) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radiusDp = 0.6f + random.nextFloat() * 1.3f,
                phase = random.nextFloat(),
                maxAlpha = 0.2f + random.nextFloat() * 0.4f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val timeStep by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = TWINKLE_STEPS,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starfieldPhase"
    )

    val starColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.fillMaxSize()) {
        val time = timeStep.toFloat() / TWINKLE_STEPS
        stars.forEachIndexed { index, star ->
            val twinkle = 0.5f + 0.5f * sin(2f * PI.toFloat() * (time + star.phase))
            val color = if (index % 5 == 0) accentColor else starColor
            drawCircle(
                color = color.copy(alpha = star.maxAlpha * twinkle),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}
