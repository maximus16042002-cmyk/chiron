package com.kinesis.core.analysis.verifier

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovementContextTrackerTest {

    private val policy = VisibilityPolicy.PROVISIONAL

    @Test
    fun tracker_extracts_null_when_window_is_inverted_or_empty() {
        val tracker = MovementContextTracker(capacity = 64)
        assertNull(tracker.extract(1000.0, 500.0))
        assertNull(tracker.extract(500.0, 500.0))
        
        // Empty buffer
        val emptyContext = tracker.extract(0.0, 1000.0)
        assertNotNull(emptyContext)
        assertEquals(MovementContext.FEATURE_COUNT, emptyContext.features.size)
        assertEquals(1000.0, emptyContext.durationMs)
    }

    @Test
    fun tracker_imputes_neutral_defaults_when_landmarks_are_unmeasurable() {
        val tracker = MovementContextTracker(capacity = 64)
        // Feed unmeasurable frames (visibility 0.0)
        val blankFrame = Landmarks.fromPacked(DoubleArray(Landmarks.PACKED_SIZE))
        for (i in 0..10) {
            tracker.push(i * 33.0, blankFrame, policy)
        }

        val context = tracker.extract(0.0, 330.0)
        assertNotNull(context)
        assertEquals(MovementContext.FEATURE_COUNT, context.features.size)
        assertEquals(330.0, context.durationMs)

        // Verify each signal is filled with its neutral default angle
        for (s in 0 until MovementContext.SIGNAL_COUNT) {
            val expected = MovementContextTracker.DEFAULT_ANGLES[s]
            val offset = s * MovementContext.POINTS_PER_SIGNAL
            for (p in 0 until MovementContext.POINTS_PER_SIGNAL) {
                assertEquals(expected, context.features[offset + p], "signal $s point $p mismatch")
            }
        }
    }

    @Test
    fun tracker_resamples_measured_signals_accurately() {
        val tracker = MovementContextTracker(capacity = 64)
        // Synthesize an upright frame with right elbow flexing from 180 down to 90 degrees
        for (i in 0..10) {
            val t = i * 100.0
            val fraction = i / 10.0
            val elbowAngle = 180.0 - 90.0 * fraction
            val frame = syntheticFrame(rightElbowDeg = elbowAngle)
            tracker.push(t, frame, policy)
        }

        val context = tracker.extract(0.0, 1000.0)
        assertNotNull(context)
        assertEquals(1000.0, context.durationMs)

        // Right elbow is signal index 1
        val rightElbowOffset = 1 * MovementContext.POINTS_PER_SIGNAL
        val startVal = context.features[rightElbowOffset]
        val endVal = context.features[rightElbowOffset + MovementContext.POINTS_PER_SIGNAL - 1]

        assertTrue(startVal > 170.0, "start value should be near 180, was $startVal")
        assertTrue(endVal < 100.0, "end value should be near 90, was $endVal")
    }

    @Test
    fun tracker_reset_clears_all_buffers() {
        val tracker = MovementContextTracker(capacity = 64)
        val frame = syntheticFrame(rightElbowDeg = 90.0)
        for (i in 0..5) {
            tracker.push(i * 33.0, frame, policy)
        }
        tracker.reset()
        val context = tracker.extract(0.0, 165.0)
        assertNotNull(context)
        // Since reset was called, all signals should be imputed with defaults
        val rightElbowOffset = 1 * MovementContext.POINTS_PER_SIGNAL
        assertEquals(MovementContextTracker.DEFAULT_ANGLES[1], context.features[rightElbowOffset])
    }

    private fun syntheticFrame(rightElbowDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        // Body points for right arm
        put(LandmarkId.RIGHT_SHOULDER, 0.5, 0.2)
        put(LandmarkId.RIGHT_ELBOW, 0.5, 0.4)
        // At 180 degrees (straight arm), elbow-to-wrist points straight down (dx=0, dy=+0.2).
        // As elbow flexes towards 90 degrees, wrist bends.
        // angle at vertex between (0, -0.2) and (dx, dy):
        val rad = (180.0 - rightElbowDeg) * kotlin.math.PI / 180.0
        put(LandmarkId.RIGHT_WRIST, 0.5 + 0.2 * kotlin.math.sin(rad), 0.4 + 0.2 * kotlin.math.cos(rad))

        // Torso / hips / knees
        put(LandmarkId.LEFT_SHOULDER, 0.4, 0.2)
        put(LandmarkId.LEFT_ELBOW, 0.4, 0.4)
        put(LandmarkId.LEFT_WRIST, 0.4, 0.6)
        put(LandmarkId.LEFT_HIP, 0.4, 0.6)
        put(LandmarkId.RIGHT_HIP, 0.5, 0.6)
        put(LandmarkId.LEFT_KNEE, 0.4, 0.8)
        put(LandmarkId.RIGHT_KNEE, 0.5, 0.8)
        put(LandmarkId.LEFT_ANKLE, 0.4, 1.0)
        put(LandmarkId.RIGHT_ANKLE, 0.5, 1.0)
        return Landmarks.fromPacked(packed)
    }
}
