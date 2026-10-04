package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.profile.CameraView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraViewEstimatorTest {

    private fun frame(
        leftShoulderX: Double,
        rightShoulderX: Double,
        leftHipX: Double = leftShoulderX,
        rightHipX: Double = rightShoulderX,
        shoulderY: Double = 0.30,
        hipY: Double = 0.55,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT) { index ->
            if (index % Landmarks.STRIDE == Landmarks.STRIDE - 1) 0.95 else 0.0
        }
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        put(LandmarkId.LEFT_SHOULDER, leftShoulderX, shoulderY)
        put(LandmarkId.RIGHT_SHOULDER, rightShoulderX, shoulderY)
        put(LandmarkId.LEFT_HIP, leftHipX, hipY)
        put(LandmarkId.RIGHT_HIP, rightHipX, hipY)
        return Landmarks.fromPacked(packed)
    }

    @Test
    fun front_on_pose_is_estimated_as_front_on() {
        // Torso scale = 0.25. Shoulder width = 0.20 -> ratio = 0.80 >= 0.60
        val frontFrame = frame(leftShoulderX = 0.40, rightShoulderX = 0.60)
        val result = CameraViewEstimator.estimate(frontFrame, torsoScale = 0.25)
        assertNotNull(result)
        assertEquals(CameraView.FRONT_ON, result.view)
        assertTrue(result.lateralRatio >= CameraViewEstimator.FRONT_ON_THRESHOLD)
        assertTrue(result.confidence >= 0.5)
    }

    @Test
    fun side_on_pose_is_estimated_as_side_on() {
        // Torso scale = 0.25. Shoulder width = 0.05 -> ratio = 0.20 <= 0.42
        val sideFrame = frame(leftShoulderX = 0.48, rightShoulderX = 0.53)
        val result = CameraViewEstimator.estimate(sideFrame, torsoScale = 0.25)
        assertNotNull(result)
        assertEquals(CameraView.SIDE_ON, result.view)
        assertTrue(result.lateralRatio <= CameraViewEstimator.SIDE_ON_THRESHOLD)
        assertTrue(result.confidence >= 0.5)
    }

    @Test
    fun angled_pose_is_estimated_as_angled_45() {
        // Torso scale = 0.25. Shoulder width = 0.125 -> ratio = 0.50 (between 0.42 and 0.60)
        val angledFrame = frame(leftShoulderX = 0.44, rightShoulderX = 0.565)
        val result = CameraViewEstimator.estimate(angledFrame, torsoScale = 0.25)
        assertNotNull(result)
        assertEquals(CameraView.ANGLED_45, result.view)
        assertTrue(result.lateralRatio in 0.42..0.60)
    }

    @Test
    fun returns_null_when_torso_scale_is_non_positive_or_nan() {
        val frontFrame = frame(leftShoulderX = 0.40, rightShoulderX = 0.60)
        assertNull(CameraViewEstimator.estimate(frontFrame, torsoScale = 0.0))
        assertNull(CameraViewEstimator.estimate(frontFrame, torsoScale = Double.NaN))
    }
}
