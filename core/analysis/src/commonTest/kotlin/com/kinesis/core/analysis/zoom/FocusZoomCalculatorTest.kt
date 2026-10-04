package com.kinesis.core.analysis.zoom

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusZoomCalculatorTest {

    private fun sampleLandmarks(
        centerX: Double = 0.50,
        centerY: Double = 0.45,
        width: Double = 0.24,
        height: Double = 0.36,
        visibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        fun put(id: LandmarkId, x: Double, y: Double, v: Double = visibility) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = v
        }

        val halfW = width * 0.5
        val halfH = height * 0.5

        put(LandmarkId.NOSE, centerX, centerY - halfH + 0.04)
        put(LandmarkId.LEFT_SHOULDER, centerX - halfW * 0.6, centerY - halfH * 0.4)
        put(LandmarkId.RIGHT_SHOULDER, centerX + halfW * 0.6, centerY - halfH * 0.4)
        put(LandmarkId.LEFT_ELBOW, centerX - halfW, centerY)
        put(LandmarkId.RIGHT_ELBOW, centerX + halfW, centerY)
        put(LandmarkId.LEFT_WRIST, centerX - halfW * 0.8, centerY + halfH * 0.5)
        put(LandmarkId.RIGHT_WRIST, centerX + halfW * 0.8, centerY + halfH * 0.5)
        put(LandmarkId.LEFT_HIP, centerX - halfW * 0.4, centerY + halfH * 0.8)
        put(LandmarkId.RIGHT_HIP, centerX + halfW * 0.4, centerY + halfH * 0.8)
        put(LandmarkId.LEFT_KNEE, centerX - halfW * 0.4, centerY + halfH)
        put(LandmarkId.RIGHT_KNEE, centerX + halfW * 0.4, centerY + halfH)
        put(LandmarkId.LEFT_ANKLE, centerX - halfW * 0.4, centerY + halfH + 0.1)
        put(LandmarkId.RIGHT_ANKLE, centerX + halfW * 0.4, centerY + halfH + 0.1)

        return Landmarks.fromPacked(packed)
    }

    @Test
    fun target_mapping_and_invariants() {
        val target = FocusZoomTarget(scale = 2.0, centerX = 0.5, centerY = 0.5)

        // Center maps to 0.5
        assertEquals(0.5, target.mapX(0.5), 1e-6)
        assertEquals(0.5, target.mapY(0.5), 1e-6)

        // Left edge of 2x view (0.25) maps to 0.0
        assertEquals(0.0, target.mapX(0.25), 1e-6)
        assertEquals(1.0, target.mapX(0.75), 1e-6)

        // Round-trip unmap
        assertEquals(0.25, target.unmapX(0.0), 1e-6)
        assertEquals(0.75, target.unmapX(1.0), 1e-6)

        assertFailsWith<IllegalArgumentException> {
            FocusZoomTarget(scale = 0.9, centerX = 0.5, centerY = 0.5)
        }
        assertFailsWith<IllegalArgumentException> {
            FocusZoomTarget(scale = 1.5, centerX = -0.1, centerY = 0.5)
        }
    }

    @Test
    fun policy_validation() {
        val defaultPolicy = FocusZoomPolicy.DEFAULT
        assertEquals(1.8, defaultPolicy.maxZoom)
        assertEquals(15, defaultPolicy.enterHysteresisFrames)
        assertEquals(15, defaultPolicy.exitHysteresisFrames)
        assertTrue(defaultPolicy.isEnabled)

        assertFailsWith<IllegalArgumentException> {
            FocusZoomPolicy(maxZoom = 0.5)
        }
        assertFailsWith<IllegalArgumentException> {
            FocusZoomPolicy(marginRatio = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            FocusZoomPolicy(enterHysteresisFrames = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            FocusZoomPolicy(smoothingAlpha = 1.5)
        }
    }

    @Test
    fun whole_body_exercises_are_strictly_suppressed() {
        val squatCalc = FocusZoomCalculator(ExerciseLibrary.squat)
        val pushupCalc = FocusZoomCalculator(ExerciseLibrary.pushUp)
        val plankCalc = FocusZoomCalculator(ExerciseLibrary.plank)
        val frame = sampleLandmarks()

        for (i in 0 until 30) {
            val sTarget = squatCalc.onFrame(frame)
            val pTarget = pushupCalc.onFrame(frame)
            val plTarget = plankCalc.onFrame(frame)

            assertEquals(1.0, sTarget.scale, 1e-6)
            assertEquals(0.5, sTarget.centerX, 1e-6)
            assertEquals(0.5, sTarget.centerY, 1e-6)
            assertFalse(squatCalc.isEngaged)

            assertEquals(1.0, pTarget.scale, 1e-6)
            assertFalse(pushupCalc.isEngaged)

            assertEquals(1.0, plTarget.scale, 1e-6)
            assertFalse(plankCalc.isEngaged)
        }
    }

    @Test
    fun disabled_policy_is_suppressed() {
        val calc = FocusZoomCalculator(
            ExerciseLibrary.bicepCurl,
            policy = FocusZoomPolicy.DISABLED,
        )
        val frame = sampleLandmarks()

        for (i in 0 until 30) {
            val target = calc.onFrame(frame)
            assertEquals(1.0, target.scale, 1e-6)
            assertFalse(calc.isEngaged)
        }
    }

    @Test
    fun eligible_exercise_enter_hysteresis_and_convergence() {
        val policy = FocusZoomPolicy(
            enterHysteresisFrames = 10,
            exitHysteresisFrames = 10,
            smoothingAlpha = 0.25,
            maxZoom = 1.8,
        )
        val calc = FocusZoomCalculator(ExerciseLibrary.bicepCurl, policy = policy)
        val frame = sampleLandmarks(width = 0.20, height = 0.30)

        // Frames 1 to 9: reliable count accumulating, but not yet engaged
        for (i in 1..9) {
            val target = calc.onFrame(frame)
            assertFalse(calc.isEngaged)
            assertEquals(1.0, target.scale, 1e-6)
        }

        // Frame 10: reaches hysteresis threshold -> engages!
        val frame10Target = calc.onFrame(frame)
        assertTrue(calc.isEngaged)
        assertTrue(frame10Target.scale > 1.0)

        // Continue feeding frames: converges towards target zoom
        for (i in 11..30) {
            calc.onFrame(frame)
        }

        assertTrue(calc.currentScale >= 1.5, "Expected zoom to converge >= 1.5, was ${calc.currentScale}")
        assertTrue(calc.currentScale <= policy.maxZoom)
    }

    @Test
    fun exit_hysteresis_when_landmarks_lost() {
        val policy = FocusZoomPolicy(
            enterHysteresisFrames = 5,
            exitHysteresisFrames = 5,
            smoothingAlpha = 0.30,
        )
        val calc = FocusZoomCalculator(ExerciseLibrary.bicepCurl, policy = policy)
        val frame = sampleLandmarks()

        // Engage
        for (i in 0 until 10) {
            calc.onFrame(frame)
        }
        assertTrue(calc.isEngaged)
        assertTrue(calc.currentScale > 1.1)

        // Lose tracking
        for (i in 1..4) {
            calc.onNothingSeen()
            assertTrue(calc.isEngaged, "Should remain engaged during exit debounce at frame $i")
        }

        // 5th missing frame: disengages
        calc.onNothingSeen()
        assertFalse(calc.isEngaged)

        // Smoothly returns to 1.0x
        for (i in 0 until 20) {
            calc.onNothingSeen()
        }
        assertEquals(1.0, calc.currentScale, 1e-6)
        assertEquals(0.5, calc.currentCenterX, 1e-6)
        assertEquals(0.5, calc.currentCenterY, 1e-6)
    }

    @Test
    fun reset_clears_state_immediately() {
        val calc = FocusZoomCalculator(ExerciseLibrary.bicepCurl)
        val frame = sampleLandmarks()
        for (i in 0 until 25) {
            calc.onFrame(frame)
        }
        assertTrue(calc.isEngaged)
        assertTrue(calc.currentScale > 1.0)

        calc.reset()
        assertFalse(calc.isEngaged)
        assertEquals(1.0, calc.currentScale, 1e-6)
        assertEquals(0.5, calc.currentCenterX, 1e-6)
        assertEquals(0.5, calc.currentCenterY, 1e-6)
    }

    @Test
    fun clamping_keeps_viewport_within_frame_bounds() {
        val policy = FocusZoomPolicy(
            enterHysteresisFrames = 5,
            maxZoom = 1.8,
            smoothingAlpha = 1.0, // Instant snap to target for precise testing
        )
        val calc = FocusZoomCalculator(ExerciseLibrary.bicepCurl, policy = policy)

        // Person at far right edge (centerX = 0.85)
        val edgeFrame = sampleLandmarks(centerX = 0.85, width = 0.15, height = 0.25)
        for (i in 0 until 10) {
            calc.onFrame(edgeFrame)
        }

        assertTrue(calc.isEngaged)
        val scale = calc.currentScale
        val halfSpanX = 0.5 / scale

        // Viewport right edge must not exceed 1.0
        val rightEdge = calc.currentCenterX + halfSpanX
        assertTrue(rightEdge <= 1.0 + 1e-6, "Right edge $rightEdge exceeded 1.0")

        // Viewport left edge must not be < 0.0
        val leftEdge = calc.currentCenterX - halfSpanX
        assertTrue(leftEdge >= -1e-6, "Left edge $leftEdge was < 0.0")
    }
}
