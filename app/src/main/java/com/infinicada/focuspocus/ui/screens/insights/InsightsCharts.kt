package com.infinicada.focuspocus.ui.screens.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The three categorical colours charts use, in fixed order (slots 1–3 of the
 * data-viz reference palette: blue, orange, aqua), stepped for the light or
 * dark surface. Those three stay distinguishable for every colour-vision
 * type in both modes. In light mode the aqua sits under 3:1 on the card, so
 * every chart that uses it also carries a legend with values.
 */
@Composable
fun seriesColors(): List<Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))
    } else {
        listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
    }
}

private val BASIC: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val MAX_BAR_WIDTH = 20.dp

/** "Tue 11 Nov"-style label for a "yyyyMMdd" key, in the device language. */
fun dayLabel(day: String, withWeekday: Boolean = true): String = try {
    val pattern = if (withWeekday) "EEE d MMM" else "d MMM"
    LocalDate.parse(day, BASIC).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
} catch (e: Exception) {
    day
}

/** "0 min", "45 min", "2h 5m". Unlike formatDuration, 0 means nothing, not "unlimited". */
@Composable
fun minutesText(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.insights_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.insights_minutes_value, minutes)

/**
 * One bar per day, stacked by series. Tap a bar to read its day; until then
 * the caption reads the latest day. Bars are 2dp apart with 4dp rounded
 * tops on a recessive baseline; the y scale is named in the caption rather
 * than with gridlines.
 *
 * [values] is per day, one entry per series. With more than one series a
 * legend with each series' total sits below (so colour is never the only
 * cue).
 */
@Composable
fun DayBarChart(
    days: List<String>,
    values: List<IntArray>,
    seriesNames: List<String>,
    colors: List<Color>,
    format: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp
) {
    if (days.isEmpty()) return
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val totals = values.map { it.sum() }
    val max = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    val shown = selected ?: totals.lastIndex
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val captionValues = values[shown].mapIndexed { i, v -> seriesNames.getOrElse(i) { "" } to v }
    val caption = buildString {
        append(dayLabel(days[shown]))
        append(" · ")
        append(format(totals[shown]))
    }
    val breakdownParts = captionValues.filter { it.second > 0 }.map { (name, v) -> "$name ${format(v)}" }
    val breakdown = if (seriesNames.size > 1) breakdownParts.joinToString(" · ") else ""
    val summary = days.indices.joinToString("; ") { "${dayLabel(days[it])}: ${totals[it]}" }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (breakdown.isNotEmpty()) {
            Text(
                breakdown,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = summary }
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / days.size
                        selected = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                    }
                }
        ) {
            val slot = size.width / days.size
            val gap = 2.dp.toPx()
            val radius = 4.dp.toPx().coerceAtMost((slot - gap) / 2f)
            // Thin marks: a bar never grows past MAX_BAR_WIDTH, however few days.
            val barWidth = (slot - gap).coerceIn(1f, MAX_BAR_WIDTH.toPx())
            values.forEachIndexed { i, stack ->
                val left = i * slot + (slot - barWidth) / 2f
                val alpha = if (selected != null && selected != i) 0.4f else 1f
                var bottom = size.height
                val topSeries = stack.indices.lastOrNull { stack[it] > 0 }
                stack.forEachIndexed { s, v ->
                    if (v <= 0) return@forEachIndexed
                    val h = v.toFloat() / max * size.height
                    val top = bottom - h
                    val segmentBottom = if (bottom < size.height) bottom - gap else bottom
                    if (segmentBottom > top) {
                        val r = if (s == topSeries) CornerRadius(radius) else CornerRadius.Zero
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left, top, left + barWidth, segmentBottom,
                                    topLeftCornerRadius = r, topRightCornerRadius = r,
                                    bottomLeftCornerRadius = CornerRadius.Zero,
                                    bottomRightCornerRadius = CornerRadius.Zero
                                )
                            )
                        }
                        drawPath(path, colors[s % colors.size].copy(alpha = alpha))
                    }
                    bottom = top
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayLabel(days.first(), withWeekday = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.insights_chart_max, format(max)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(dayLabel(days.last(), withWeekday = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (seriesNames.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            seriesNames.forEachIndexed { i, name ->
                LegendRow(color = colors[i % colors.size], label = name, value = format(values.sumOf { it[i] }))
            }
        }
    }
}

/** A coloured dot, the series name in text ink, and its value. */
@Composable
fun LegendRow(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A labelled horizontal bar for ranked lists (apps by minutes, by opens):
 * name and value in text ink, a thin single-colour bar scaled to [max].
 */
@Composable
fun RankedBarRow(
    label: String,
    value: String,
    amount: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))
        ) {
            val fraction = if (max <= 0) 0f else (amount.toFloat() / max).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
