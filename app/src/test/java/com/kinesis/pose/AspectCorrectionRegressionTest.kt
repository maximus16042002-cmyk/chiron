package com.kinesis.pose

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Permanent guard test - DECISIONS.md P3-15 and P3-16.
 *
 * MediaPipe reports x as a fraction of the frame's width and y as a fraction of
 * its height. On any frame that is not square those are different units, and a
 * joint angle computed from them is wrong by up to about sixteen degrees on a
 * portrait 3:4 frame - with nothing crashing and nothing looking wrong. The
 * adapter puts both axes in one unit, the frame's width, so the shared core
 * never has to know a frame had a shape.
 *
 * The scene is built in pixels, where angles are true, and reported the way
 * MediaPipe would report it. Each knee is posed at the orientation where the
 * distortion is worst for its angle, and the two knees differ, so a side swap
 * cannot hide behind a correct-looking number.
 */
class AspectCorrectionRegressionTest {

    private val adapter = PoseAdapter()
    private val policy = VisibilityPolicy.PROVISIONAL

    private class Pose(val leftKneeDeg: Double, val rightKneeDeg: Double)

    /** Pixel positions of the scene on a [width] x [height] frame, anatomical sides. */
    private fun pixels(width: Int, height: Int, pose: Pose): Map<LandmarkId, Pair<Double, Double>> {
        val out = HashMap<LandmarkId, Pair<Double, Double>>()
        fun knee(hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, kx: Double, thighDeg: Double, angleDeg: Double) {
            val ky = height * 0.6
            val length = width * 0.3
            val t = thighDeg * PI / 180.0
            val s = (thighDeg + angleDeg) * PI / 180.0
            out[knee] = kx to ky
            out[hip] = (kx + length * cos(t)) to (ky + length * sin(t))
            out[ankle] = (kx + length * cos(s)) to (ky + length * sin(s))
        }
        // 240 and 120 degrees are where the error for 60 and 120 is largest.
        knee(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, width * 0.35, 240.0, pose.leftKneeDeg)
        knee(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, width * 0.65, 120.0, pose.rightKneeDeg)
        return out
    }

    /**
     * What MediaPipe reports. The front camera's frame reaches the model mirrored
     * (P1-5, made true by LiveCamera), so each landmark lands in its opposite
     * slot at the reflected x - the same staging MirroringRegressionTest uses.
     */
    private fun reported(width: Int, height: Int, pose: Pose, facing: CameraFacing): RawLandmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for ((id, point) in pixels(width, height, pose)) {
            val mirrored = facing == CameraFacing.FRONT
            val slot = (if (mirrored) id.mirrored else id).index * Landmarks.STRIDE
            val x = point.first / width
            packed[slot] = if (mirrored) 1.0 - x else x
            packed[slot + 1] = point.second / height
            packed[slot + 3] = 0.95
        }
        return RawLandmarks(packed)
    }

    private fun kneeAngles(landmarks: Landmarks) =
        JointAngles.compute(landmarks, JointAngleId.KNEE, MeasurementPlane.IMAGE_2D, policy)

    @Test
    fun a_known_knee_angle_on_a_portrait_three_by_four_frame_comes_out_true() {
        val pose = Pose(leftKneeDeg = 60.0, rightKneeDeg = 120.0)
        for (facing in CameraFacing.entries) {
            val angles = kneeAngles(
                adapter.toAnatomical(reported(480, 640, pose, facing), facing, frameWidthPx = 480, frameHeightPx = 640),
            )
            assertEquals(facing.name + " left knee", 60.0, angles.left, TOLERANCE)
            assertEquals(facing.name + " right knee", 120.0, angles.right, TOLERANCE)
        }
    }

    @Test
    fun a_landscape_frame_is_corrected_the_same_way() {
        val pose = Pose(leftKneeDeg = 60.0, rightKneeDeg = 120.0)
        for (facing in CameraFacing.entries) {
            val angles = kneeAngles(
                adapter.toAnatomical(reported(640, 480, pose, facing), facing, frameWidthPx = 640, frameHeightPx = 480),
            )
            assertEquals(facing.name + " left knee", 60.0, angles.left, TOLERANCE)
            assertEquals(facing.name + " right knee", 120.0, angles.right, TOLERANCE)
        }
    }

    /** The unit is pinned, because the skeleton overlay draws in it. */
    @Test
    fun both_axes_come_out_in_frame_widths() {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        val slot = LandmarkId.NOSE.index * Landmarks.STRIDE
        packed[slot] = 1.0
        packed[slot + 1] = 1.0
        val out = adapter.toAnatomical(RawLandmarks(packed), CameraFacing.BACK, frameWidthPx = 480, frameHeightPx = 640)

        assertEquals("the right edge is one frame width across", 1.0, out.x(LandmarkId.NOSE), TOLERANCE)
        assertEquals("the bottom edge is 640/480 frame widths down", 640.0 / 480.0, out.y(LandmarkId.NOSE), TOLERANCE)
    }

    /** The guard has to be able to fail: uncorrected, this scene is visibly wrong. */
    @Test
    fun the_staged_scene_is_distorted_without_the_correction() {
        val raw = reported(480, 640, Pose(leftKneeDeg = 60.0, rightKneeDeg = 120.0), CameraFacing.BACK)
        val angles = kneeAngles(Landmarks.fromPacked(raw.copyPacked()))
        assertTrue("left read " + angles.left + " uncorrected", abs(angles.left - 60.0) > 10.0)
        assertTrue("right read " + angles.right + " uncorrected", abs(angles.right - 120.0) > 10.0)
    }

    private companion object {
        /** CONTRACTS.md section 6's tolerance for a computed angle. */
        const val TOLERANCE = 1e-9
    }
}
