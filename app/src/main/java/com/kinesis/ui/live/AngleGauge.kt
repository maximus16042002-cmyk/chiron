package com.kinesis.ui.live

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One angle arc, written in place so drawing it allocates nothing - DECISIONS.md P3-76.
 *
 * Angles are in Canvas degrees: 0 along +x, increasing clockwise, because screen y runs down.
 * [sweepDeg] is signed - Canvas draws a negative sweep anticlockwise - and its size is the angle.
 */
class GaugeArc {
    var visible: Boolean = false
    var centreX: Float = 0f
    var centreY: Float = 0f
    var radius: Float = 0f
    var startDeg: Float = 0f
    var sweepDeg: Float = 0f
    var labelX: Float = 0f
    var labelY: Float = 0f
}

/**
 * Where the live angle arc is drawn - DECISIONS.md P3-76.
 *
 * Drawing geometry only: it decides nothing about the movement, and the number in the label is
 * the core's. Pure, so it is tested on the JVM. The overlay scales both image axes by the same
 * factor (P3-16) and every profile measures in the image plane, so the arc swept between the drawn
 * bones is the angle the core computed - mirroring reverses the sweep and keeps its size.
 */
object AngleGauge {

    /** How far along the shorter bone the arc reaches. */
    private const val BONE_FRACTION = 0.4f

    /** Below this, in pixels, a bone has no direction worth drawing. */
    private const val DEGENERATE_PX = 1e-3f

    /**
     * The arc at a joint's [vertex][vx], between the bone toward [proximal][px] and the bone toward
     * [distal][dx]: always the interior angle, at most 180 degrees.
     */
    fun joint(
        px: Float, py: Float,
        vx: Float, vy: Float,
        dx: Float, dy: Float,
        maxRadius: Float, minRadius: Float, labelGap: Float,
        out: GaugeArc,
    ) {
        val toProximal = hypot(px - vx, py - vy)
        val toDistal = hypot(dx - vx, dy - vy)
        if (!(toProximal > DEGENERATE_PX && toDistal > DEGENERATE_PX)) {
            // NaN fails the comparison too, so an unmeasured point lands here.
            out.visible = false
            return
        }
        val start = direction(px - vx, py - vy)
        val end = direction(dx - vx, dy - vy)
        place(out, vx, vy, start, wrap(end - start), min(toProximal, toDistal), maxRadius, minRadius, labelGap)
    }

    /**
     * The arc for a segment's inclination (P3-45): at its [lower][lx] end, from the image horizontal
     * - toward whichever side the [upper][ux] end lies - to the segment. Its size is the inclination.
     */
    fun inclination(
        lx: Float, ly: Float,
        ux: Float, uy: Float,
        maxRadius: Float, minRadius: Float, labelGap: Float,
        out: GaugeArc,
    ) {
        val length = hypot(ux - lx, uy - ly)
        if (!(length > DEGENERATE_PX)) {
            out.visible = false
            return
        }
        val horizontal = if (ux >= lx) 0f else 180f
        val segment = direction(ux - lx, uy - ly)
        place(out, lx, ly, horizontal, wrap(segment - horizontal), length, maxRadius, minRadius, labelGap)
    }

    /**
     * The label for an angle in whole degrees, or `null` for a side that was not measured. Built
     * once, so a frame asks for one without allocating a string.
     */
    fun label(degrees: Double): String? {
        if (degrees.isNaN()) return null
        val whole = degrees.roundToInt().coerceIn(-LABEL_RANGE, LABEL_RANGE)
        return LABELS[whole + LABEL_RANGE]
    }

    private const val LABEL_RANGE = 180

    /** −180° to 180°: every joint angle (0 to 180) and every inclination (−90 to 90). */
    private val LABELS: Array<String> = Array(2 * LABEL_RANGE + 1) { "${it - LABEL_RANGE}°" }

    private fun place(
        out: GaugeArc,
        cx: Float, cy: Float,
        start: Float, sweep: Float,
        shorterBone: Float,
        maxRadius: Float, minRadius: Float, labelGap: Float,
    ) {
        val radius = (shorterBone * BONE_FRACTION).coerceIn(minRadius, maxRadius)
        // Inside the angle it reads, on the bisector, like a protractor: the caller's gap includes
        // the label's own half-width, so even a narrow angle's label clears both bones. P3-76 first
        // put it opposite, on the open side - on the device that put an arm's number on the torso.
        val away = Math.toRadians((start + sweep / 2f).toDouble())
        out.visible = true
        out.centreX = cx
        out.centreY = cy
        out.radius = radius
        out.startDeg = start
        out.sweepDeg = sweep
        out.labelX = cx + ((radius + labelGap) * cos(away)).toFloat()
        out.labelY = cy + ((radius + labelGap) * sin(away)).toFloat()
    }

    private fun direction(x: Float, y: Float): Float = Math.toDegrees(atan2(y, x).toDouble()).toFloat()

    /** Into (−180, 180]: the shorter way round, which is the interior angle. */
    private fun wrap(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}

/**
 * The flash on a counted repetition - DECISIONS.md P3-76: bright at the moment of counting and
 * gone within [DURATION_MS]. Pure, so the curve is tested on the JVM.
 */
object RepFlash {

    const val DURATION_MS: Long = 500L

    /** No repetition counted yet. */
    const val NEVER: Long = Long.MIN_VALUE

    /** 0 at the moment of counting, 1 once the flash is over (and before any repetition). */
    fun progress(nowMs: Long, countedAtMs: Long): Float {
        if (countedAtMs == NEVER) return 1f
        val elapsed = nowMs - countedAtMs
        if (elapsed < 0L) return 1f
        return (elapsed.toFloat() / DURATION_MS).coerceAtMost(1f)
    }

    /** 1 at the moment of counting, easing out to 0. */
    fun intensity(nowMs: Long, countedAtMs: Long): Float {
        val remaining = 1f - progress(nowMs, countedAtMs)
        return remaining * remaining
    }
}
