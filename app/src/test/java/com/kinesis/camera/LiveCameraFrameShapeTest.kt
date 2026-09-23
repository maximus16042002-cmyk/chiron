package com.kinesis.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame shape the live screen is built around - DECISIONS.md P3-16.
 *
 * The camera area is sized to a portrait 4:3 frame and the overlay draws on that
 * assumption, so a camera that binds any other shape must be refused rather than
 * shown with a skeleton drifting off the body.
 */
class LiveCameraFrameShapeTest {

    @Test
    fun four_by_three_frames_are_accepted_in_either_orientation() {
        assertTrue(LiveCamera.isFourByThree(640, 480))
        assertTrue(LiveCamera.isFourByThree(480, 640))
        assertTrue(LiveCamera.isFourByThree(1440, 1080))
    }

    @Test
    fun any_other_shape_is_refused() {
        assertFalse(LiveCamera.isFourByThree(1280, 720))
        assertFalse(LiveCamera.isFourByThree(1920, 1080))
        assertFalse(LiveCamera.isFourByThree(640, 640))
        assertFalse(LiveCamera.isFourByThree(0, 0))
    }

    /** The screen's camera area and the check must describe the same shape. */
    @Test
    fun the_screen_aspect_is_the_accepted_frame_turned_upright() {
        assertEquals(0.75f, LiveCamera.PORTRAIT_FRAME_ASPECT, 0f)
        assertTrue(LiveCamera.isFourByThree(3, 4))
    }
}
