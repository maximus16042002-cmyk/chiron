package com.kinesis.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where the live angle arc is drawn - DECISIONS.md P3-76. Drawing geometry only: the number in the
 * label is the core's, and these tests pin that the arc swept between the drawn bones is the same
 * angle, so the picture never disagrees with the number beside it.
 */
class AngleGaugeTest {

    private val arc = GaugeArc()

    private fun joint(px: Float, py: Float, vx: Float, vy: Float, dx: Float, dy: Float) =
        AngleGauge.joint(px, py, vx, vy, dx, dy, MAX_R, MIN_R, GAP, arc)

    @Test
    fun a_right_angle_sweeps_ninety_degrees_from_the_first_bone() {
        // Shoulder straight above the elbow, wrist straight ahead of it.
        joint(100f, 0f, 100f, 100f, 200f, 100f)

        assertTrue(arc.visible)
        assertEquals(100f, arc.centreX, 1e-4f)
        assertEquals(100f, arc.centreY, 1e-4f)
        assertEquals(-90f, arc.startDeg, 1e-4f)
        assertEquals(90f, arc.sweepDeg, 1e-4f)
    }

    /** Every orientation and opening: the sweep is the interior angle the core computes, never more than 180. */
    @Test
    fun the_sweep_is_always_the_interior_angle() {
        for (turnStep in 0 until 24) {
            // Opened clockwise and anticlockwise: either way round, the sweep is the interior angle.
            for (opening in listOf(5.0, 30.0, 60.0, 90.0, 120.0, 150.0, 175.0, 179.5).flatMap { listOf(it, -it) }) {
                val turn = turnStep * 15.0 * Math.PI / 180.0
                val open = opening * Math.PI / 180.0
                val px = (300 + 80 * cos(turn)).toFloat()
                val py = (300 + 80 * sin(turn)).toFloat()
                val dx = (300 + 120 * cos(turn + open)).toFloat()
                val dy = (300 + 120 * sin(turn + open)).toFloat()
                joint(px, py, 300f, 300f, dx, dy)

                assertTrue(arc.visible)
                assertEquals("turn $turnStep opening $opening", interior(px, py, 300f, 300f, dx, dy), abs(arc.sweepDeg).toDouble(), 1e-3)
                assertEquals("turn $turnStep opening $opening: which way round", opening > 0, arc.sweepDeg > 0)
                assertTrue(abs(arc.sweepDeg) <= 180f)
            }
        }
    }

    /** A mirrored preview reflects the figure: the arc's size is unchanged, its direction reversed. */
    @Test
    fun mirroring_keeps_the_size_of_the_sweep() {
        joint(100f, 20f, 140f, 110f, 230f, 90f)
        val sweep = arc.sweepDeg
        val w = 400f
        joint(w - 100f, 20f, w - 140f, 110f, w - 230f, 90f)

        assertEquals(abs(sweep), abs(arc.sweepDeg), 1e-3f)
        assertEquals(-sweep, arc.sweepDeg, 1e-3f)
    }

    /**
     * The label sits just beyond the arc, inside the angle it reads, on the bisector. Placed on the
     * open side instead, the device showed an arm's number on the torso (P3-76).
     */
    @Test
    fun the_label_sits_beyond_the_arc_inside_the_angle() {
        joint(100f, 0f, 100f, 100f, 200f, 100f)

        val lx = arc.labelX - arc.centreX
        val ly = arc.labelY - arc.centreY
        assertEquals(arc.radius + GAP, hypot(lx, ly), 1e-3f)
        val bisector = Math.toRadians((arc.startDeg + arc.sweepDeg / 2f).toDouble())
        assertEquals("label off the bisector", 0.0, lx * sin(bisector) - ly * cos(bisector), 1e-3)
        assertTrue("label outside the angle", lx * cos(bisector) + ly * sin(bisector) > 0)
    }

    @Test
    fun the_radius_follows_the_shorter_bone_within_its_bounds() {
        joint(100f, -1000f, 100f, 100f, 1100f, 100f) // long bones: the largest arc
        assertEquals(MAX_R, arc.radius, 1e-4f)

        joint(100f, 0f, 100f, 100f, 200f, 100f) // 100 px bones: 0.4 of the shorter
        assertEquals(40f, arc.radius, 1e-4f)

        joint(100f, 95f, 100f, 100f, 200f, 100f) // a 5 px bone: never smaller than the floor
        assertEquals(MIN_R, arc.radius, 1e-4f)
    }

    @Test
    fun missing_or_degenerate_points_draw_nothing() {
        joint(Float.NaN, 0f, 100f, 100f, 200f, 100f)
        assertFalse(arc.visible)
        joint(100f, 0f, 100f, 100f, 200f, Float.NaN)
        assertFalse(arc.visible)
        joint(100f, 100f, 100f, 100f, 200f, 100f) // a bone of no length has no direction
        assertFalse(arc.visible)
    }

    /** The sit-up's trunk (P3-45): from the image horizontal, toward the shoulder's side, to the trunk. */
    @Test
    fun an_inclination_sweeps_from_horizontal_to_the_segment_whichever_way_it_faces() {
        AngleGauge.inclination(100f, 200f, 200f, 100f, MAX_R, MIN_R, GAP, arc) // 45 degrees up, facing right
        assertTrue(arc.visible)
        assertEquals(0f, arc.startDeg, 1e-4f)
        assertEquals(45f, abs(arc.sweepDeg), 1e-3f)

        AngleGauge.inclination(100f, 200f, 0f, 100f, MAX_R, MIN_R, GAP, arc) // the same, facing left
        assertEquals(180f, abs(arc.startDeg), 1e-4f)
        assertEquals(45f, abs(arc.sweepDeg), 1e-3f)

        AngleGauge.inclination(100f, 200f, 100f, 200f, MAX_R, MIN_R, GAP, arc)
        assertFalse(arc.visible)
    }

    @Test
    fun labels_are_whole_degrees_built_once_and_absent_for_an_unmeasured_side() {
        assertEquals("92°", AngleGauge.label(92.4))
        assertEquals("93°", AngleGauge.label(92.5))
        assertEquals("0°", AngleGauge.label(-0.4))
        assertEquals("180°", AngleGauge.label(180.0))
        assertEquals("-30°", AngleGauge.label(-30.2))
        assertSame("a label built per frame", AngleGauge.label(92.4), AngleGauge.label(91.6))
        assertNull(AngleGauge.label(Double.NaN))
    }

    private fun interior(px: Float, py: Float, vx: Float, vy: Float, dx: Float, dy: Float): Double {
        val ax = (px - vx).toDouble()
        val ay = (py - vy).toDouble()
        val bx = (dx - vx).toDouble()
        val by = (dy - vy).toDouble()
        val cosine = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))
        return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    }

    private companion object {
        const val MAX_R = 60f
        const val MIN_R = 18f
        const val GAP = 14f
    }
}
