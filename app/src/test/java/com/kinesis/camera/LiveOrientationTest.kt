package com.kinesis.camera

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-19: the live screen runs portrait either way
 * up and never landscape, and a frame is turned upright and mirrored the same way
 * whichever way up the phone is.
 */
class LiveOrientationTest {

    private val quarterTurns = listOf(0, 90, 180, 270)
    private val samples = listOf(0.0 to 0.0, 1.0 to 0.0, 0.2 to 0.7, 0.9 to 0.35, 0.5 to 0.5)

    private val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val upsideDown = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

    @Test
    fun upright_tilts_ask_for_portrait_and_upside_down_tilts_for_reverse_portrait() {
        for (tilt in listOf(0, 30, 60, 300, 330, 359, 360)) assertEquals("tilt $tilt", portrait, LiveOrientation.requestFor(tilt))
        for (tilt in listOf(120, 150, 180, 210, 240)) assertEquals("tilt $tilt", upsideDown, LiveOrientation.requestFor(tilt))
    }

    /** Landscape is never asked for: a sideways tilt keeps whatever was last asked for. */
    @Test
    fun a_sideways_or_flat_phone_keeps_the_current_orientation() {
        for (tilt in listOf(61, 90, 119, 241, 270, 299)) assertNull("tilt $tilt", LiveOrientation.requestFor(tilt))
        assertNull("flat", LiveOrientation.requestFor(OrientationEventListener.ORIENTATION_UNKNOWN))
    }

    /** The two bands never meet, so a phone near one edge cannot make the screen flap. */
    @Test
    fun the_portrait_and_upside_down_bands_are_separated_by_landscape() {
        val asked = (0 until 360).map { LiveOrientation.requestFor(it) }
        for (tilt in 0 until 360) {
            val here = asked[tilt]
            val next = asked[(tilt + 1) % 360]
            if (here != null && next != null) assertEquals("tilts $tilt and ${tilt + 1} flip directly", here, next)
        }
        assertEquals(portrait, LiveOrientation.INITIAL)
    }

    @Test
    fun the_live_screen_runs_upright_and_upside_down_but_never_landscape() {
        assertTrue(LiveOrientation.isSupported(Surface.ROTATION_0))
        assertTrue(LiveOrientation.isSupported(Surface.ROTATION_180))
        assertFalse(LiveOrientation.isSupported(Surface.ROTATION_90))
        assertFalse(LiveOrientation.isSupported(Surface.ROTATION_270))
    }

    /** Clockwise quarter turns: the raw frame's top-left corner goes where the turn takes it. */
    @Test
    fun each_quarter_turn_takes_the_top_left_corner_where_a_clockwise_turn_would() {
        assertEquals(0.0 to 0.0, FrameUpright.uprightPoint(0.0, 0.0, 0, mirror = false))
        assertEquals(1.0 to 0.0, FrameUpright.uprightPoint(0.0, 0.0, 90, mirror = false))
        assertEquals(1.0 to 1.0, FrameUpright.uprightPoint(0.0, 0.0, 180, mirror = false))
        assertEquals(0.0 to 1.0, FrameUpright.uprightPoint(0.0, 0.0, 270, mirror = false))
    }

    @Test
    fun two_quarter_turns_are_a_half_turn() {
        for (r in quarterTurns) for ((u, v) in samples) {
            val (x1, y1) = FrameUpright.uprightPoint(u, v, r, mirror = false)
            val (x2, y2) = FrameUpright.uprightPoint(x1, y1, 90, mirror = false)
            assertClose(FrameUpright.uprightPoint(u, v, r + 90, mirror = false), x2 to y2)
        }
    }

    /**
     * The upside-down phone: the same camera reports a rotation 180 degrees apart,
     * and the upright frame comes out turned half a circle - which is what cancels
     * the phone being inverted. Holds with and without the front camera's mirror.
     */
    @Test
    fun upside_down_the_upright_frame_is_the_upright_one_turned_half_a_circle() {
        for (mirror in listOf(false, true)) for (r in quarterTurns) for ((u, v) in samples) {
            val (x, y) = FrameUpright.uprightPoint(u, v, r, mirror)
            assertClose(1.0 - x to 1.0 - y, FrameUpright.uprightPoint(u, v, r + 180, mirror))
        }
    }

    /** The mirror is left-to-right in the upright frame, whatever the turn - the same selfie mirror either way up. */
    @Test
    fun the_mirror_is_left_to_right_in_the_upright_frame_at_every_rotation() {
        for (r in quarterTurns) for ((u, v) in samples) {
            val (x, y) = FrameUpright.uprightPoint(u, v, r, mirror = false)
            assertClose(1.0 - x to y, FrameUpright.uprightPoint(u, v, r, mirror = true))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_rotation_that_is_not_a_quarter_turn_is_refused() {
        FrameUpright.uprightPoint(0.5, 0.5, 45, mirror = false)
    }

    private fun assertClose(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals(expected.first, actual.first, 1e-12)
        assertEquals(expected.second, actual.second, 1e-12)
    }
}
