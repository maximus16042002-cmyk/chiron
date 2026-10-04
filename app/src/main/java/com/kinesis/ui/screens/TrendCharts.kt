package com.kinesis.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.ExerciseSummary
import com.kinesis.core.analysis.history.TrendPoint
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.workout.WorkoutCatalogue
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Card
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.Raised
import com.kinesis.ui.theme.SeriesLeft
import com.kinesis.ui.theme.SeriesRight
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * One exercise's trend - DECISIONS.md P3-51. The points are the core's (`Trends.of`); these
 * charts draw them. Left and right are two series, never one line; a side not measured is a gap.
 * Measurements only: no fit, no slope, no "improving".
 */

/** An exercise's page from Progress: its records, its two charts, and a table of the same points. */
@Composable
fun ExerciseTrendScreen(exerciseId: String, points: List<TrendPoint>, summary: ExerciseSummary?, onBack: () -> Unit) {
    val isHold = WorkoutCatalogue.profileOf(exerciseId) is IsometricExercise
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    var table by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(Modifier.padding(top = 8.dp)) { BackLink("Progress", onBack) } }
        item { PageTitle(nameOf(exerciseId), if (points.isEmpty()) "No sets yet" else "The last " + points.size + if (points.size == 1) " set" else " sets") }
        if (points.isEmpty()) {
            item { KCard { Note("Sets of this exercise will appear here.") } }
            return@LazyColumn
        }
        item { KCard { Text(readout(points[selected], isHold), style = MaterialTheme.typography.bodyMedium, color = Ink) } }
        if (!isHold) {
            item {
                KCard {
                    Text("Range of motion", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Legend()
                    Spacer(Modifier.height(8.dp))
                    RangeChart(points, selected) { selected = it }
                }
            }
        }
        item {
            KCard {
                Text(if (isHold) "Seconds held in each set" else "Counted in each set", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(8.dp))
                CountColumns(points.map { if (isHold) it.heldMs / 1000.0 else it.countedRepetitions.toDouble() }, selected) { selected = it }
            }
        }
        if (summary != null) {
            item {
                KCard {
                    Text("Records", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    if (!isHold) {
                        Line("Most counted in one set", summary.mostRepsInASet.toString())
                        Line("Largest range, left", degreesOrDash(summary.bestRangeOfMotion.left))
                        Line("Largest range, right", degreesOrDash(summary.bestRangeOfMotion.right))
                    }
                    if (summary.longestHoldMs.isFinite()) Line("Longest hold", (summary.longestHoldMs / 1000.0).roundToInt().toString() + " s")
                }
            }
        }
        item {
            KCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("The same as a table", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                    FilterChip(if (table) "Hide" else "Show", table) { table = !table }
                }
                if (table) {
                    Spacer(Modifier.height(8.dp))
                    for (point in points.asReversed()) Text(readout(point, isHold), style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** "Sat 3 Oct · L 92° · R 88° · 10 counted", or "Sat 3 Oct · 45 s held". */
private fun readout(point: TrendPoint, isHold: Boolean): String =
    dayTitle(point.day) + " · " + if (isHold) {
        (point.heldMs / 1000.0).roundToInt().toString() + " s held"
    } else {
        "L " + degreesOrDash(point.rangeOfMotion.left) + " · R " + degreesOrDash(point.rangeOfMotion.right) + " · " + point.countedRepetitions + " counted"
    }

private fun degreesOrDash(value: Double): String = if (value.isFinite()) value.roundToInt().toString() + "°" else "—"

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

/** The legend: a coloured mark of each shape beside a word in text colour, never coloured text. */
@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SeriesLeft))
        Text(" Left", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(1.dp)).background(SeriesRight))
        Text(" Right", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
    }
}

/**
 * Left and right range of motion, set by set: two 2 dp lines, broken where a side was not
 * measured; a circle marks left and a square right, each ringed in the card's colour. Tap a set to
 * read it.
 */
@Composable
private fun RangeChart(points: List<TrendPoint>, selected: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = InkFaint)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = InkSecondary)
    val values = points.flatMap { listOf(it.rangeOfMotion.left, it.rangeOfMotion.right) }.filter { it.isFinite() }
    val axis = remember(points) { NiceAxis.of(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 90.0) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Range of motion, left and right, for the last ${points.size} sets. The table below lists every value." }
            .pointerInput(points) { detectTapGestures { offset -> onSelect(nearestIndex(offset.x, size.width.toFloat(), points.size, 40.dp.toPx(), 24.dp.toPx())) } },
    ) {
        val left = 40.dp.toPx()
        val right = size.width - 24.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        fun y(v: Double) = (bottom - (v - axis.min) / (axis.max - axis.min) * (bottom - top)).toFloat()
        fun x(i: Int) = xOf(i, points.size, left, right)

        // Hairline gridlines and round ticks, recessive.
        for (tick in axis.ticks) {
            drawLine(Raised, Offset(left, y(tick)), Offset(right, y(tick)), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(tick.roundToInt().toString() + "°", tickStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y(tick) - text.size.height / 2f))
        }
        // The selected set.
        drawLine(InkFaint, Offset(x(selected), top), Offset(x(selected), bottom), strokeWidth = 1.dp.toPx())

        for ((side, colour) in listOf(0 to SeriesLeft, 1 to SeriesRight)) {
            val value: (TrendPoint) -> Double = { if (side == 0) it.rangeOfMotion.left else it.rangeOfMotion.right }
            val path = Path()
            var drawing = false
            points.forEachIndexed { i, p ->
                val v = value(p)
                if (!v.isFinite()) {
                    drawing = false
                } else if (!drawing) {
                    path.moveTo(x(i), y(v)); drawing = true
                } else {
                    path.lineTo(x(i), y(v))
                }
            }
            drawPath(path, colour, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                val v = value(p)
                if (v.isFinite()) marker(Offset(x(i), y(v)), colour, square = side == 1)
            }
        }

        // End labels, in text colour, unless the two ends are too close to tell apart.
        val lastLeft = points.lastOrNull { it.rangeOfMotion.left.isFinite() }?.rangeOfMotion?.left
        val lastRight = points.lastOrNull { it.rangeOfMotion.right.isFinite() }?.rangeOfMotion?.right
        val apart = lastLeft == null || lastRight == null || kotlin.math.abs(y(lastLeft) - y(lastRight)) > 14.dp.toPx()
        if (apart) {
            for ((label, v) in listOf("L" to lastLeft, "R" to lastRight)) {
                if (v == null) continue
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(right + 6.dp.toPx(), y(v) - text.size.height / 2f))
            }
        }
    }
}

/** A marker: 8 dp, filled with the series colour, with a 2 dp ring in the card's colour. */
private fun DrawScope.marker(centre: Offset, colour: Color, square: Boolean) {
    val half = 4.dp.toPx()
    val ring = 2.dp.toPx()
    if (square) {
        drawRect(Card, topLeft = centre - Offset(half + ring, half + ring), size = Size(2 * (half + ring), 2 * (half + ring)))
        drawRect(colour, topLeft = centre - Offset(half, half), size = Size(2 * half, 2 * half))
    } else {
        drawCircle(Card, radius = half + ring, center = centre)
        drawCircle(colour, radius = half, center = centre)
    }
}

/**
 * One column per set, at most 24 dp wide with a 4 dp rounded top, grown from one baseline. The
 * selected set is in the accent, the rest in grey (the app's accent means selection); its value
 * sits on its cap.
 */
@Composable
private fun CountColumns(values: List<Double>, selected: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val valueStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(color = Ink)
    val top = remember(values) { NiceAxis.of(0.0, values.maxOrNull() ?: 1.0).max }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "One column per set, for the last ${values.size} sets. The table below lists every value." }
            .pointerInput(values) { detectTapGestures { offset -> onSelect(nearestIndex(offset.x, size.width.toFloat(), values.size, 8.dp.toPx(), 8.dp.toPx())) } },
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val labelSpace = 20.dp.toPx()
        val baseline = size.height - 1.dp.toPx()
        val slot = (right - left) / values.size
        val width = minOf(24.dp.toPx(), slot * 0.6f)
        drawLine(Raised, Offset(left, baseline), Offset(right, baseline), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val height = ((v / top) * (baseline - labelSpace)).toFloat().coerceAtLeast(0f)
            if (height <= 0f) return@forEachIndexed
            val cx = xOf(i, values.size, left, right)
            val rect = Rect(cx - width / 2, baseline - height, cx + width / 2, baseline)
            val radius = CornerRadius(minOf(4.dp.toPx(), height))
            val path = Path().apply {
                addRoundRect(RoundRect(rect, topLeft = radius, topRight = radius, bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero))
            }
            drawPath(path, if (i == selected) Accent else InkFaint)
        }
        if (selected in values.indices) {
            val v = values[selected]
            val text = measurer.measure(if (v == floor(v)) v.roundToInt().toString() else ((v * 10).roundToInt() / 10.0).toString(), valueStyle)
            val height = ((v / top) * (baseline - labelSpace)).toFloat().coerceAtLeast(0f)
            val cx = xOf(selected, values.size, left, right)
            val x = (cx - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(x, baseline - height - text.size.height - 2.dp.toPx()))
        }
    }
}

/** Points spread evenly, half a slot in from each edge. */
private fun xOf(index: Int, count: Int, left: Float, right: Float): Float {
    val slot = (right - left) / count
    return left + slot * (index + 0.5f)
}

private fun nearestIndex(x: Float, width: Float, count: Int, leftPad: Float, rightPad: Float): Int {
    val slot = (width - leftPad - rightPad) / count
    return ((x - leftPad) / slot).toInt().coerceIn(0, count - 1)
}

/** An axis with round ticks, about four of them, padded to whole steps. */
internal class NiceAxis(val min: Double, val max: Double, val ticks: List<Double>) {
    companion object {
        private val STEPS = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 180.0, 300.0, 600.0)

        fun of(low: Double, high: Double): NiceAxis {
            val span = (high - low).coerceAtLeast(1.0)
            val step = STEPS.firstOrNull { span / it <= 4.0 } ?: STEPS.last()
            val min = floor(low / step) * step
            var max = ceil(high / step) * step
            if (max <= min) max = min + step
            val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + 1e-9 }.toList()
            return NiceAxis(min, max, ticks)
        }
    }
}
