package com.kinesis.camera

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.pose.CameraFacing
import com.kinesis.pose.PoseAdapter
import com.kinesis.pose.RawLandmarks
import com.kinesis.ui.live.OverlayGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard test - CLAUDE.md sections 4 and 18, DECISIONS.md P3-17.
 *
 * The live screen can use either camera. The two paths differ in two places -
 * whether the capture code mirrors the frame before the pose model sees it, and
 * which facing it tells the adapter - and a mismatch between them swaps every
 * left/right measurement with no visible symptom. MirroringRegressionTest pins
 * the adapter on inputs staged by hand; this drives the capture decision the
 * app actually makes, so the capture side and the adapter are checked together.
 *
 * ### The chain, per facing
 *
 * 1. **One physical pose.** The subject faces the phone. Either camera, pointed
 *    at them, delivers an upright image that is not mirrored: a photograph, in
 *    which the subject's left side is on the image's right.
 * 2. **Capture.** [LiveCamera.mirrorsAnalysisFrame] decides whether that image
 *    is flipped before MediaPipe sees it.
 * 3. **The model** labels landmarks from the image it is given. On a mirrored
 *    image it therefore calls the subject's left wrist `RIGHT_WRIST`, at the
 *    reflected x. That is DECISIONS.md P1-5's premise, simulated here and
 *    checked live in Phase B.
 * 4. **The adapter**, told the camera's facing, produces anatomical landmarks.
 */
class CameraFacingParityTest {

    private val width = 480
    private val height = 640
    private val adapter = PoseAdapter()

    /**
     * The physical pose, as pixel positions in the upright, unmirrored image.
     * Asymmetric on purpose: the left arm is raised and the right hangs, so a
     * side swap shows up as a wrong height, not just a wrong x.
     */
    private val pose: Map<LandmarkId, Pair<Double, Double>> = mapOf(
        // Subject's left side appears on the image's right.
        LandmarkId.LEFT_SHOULDER to (300.0 to 200.0),
        LandmarkId.LEFT_ELBOW to (360.0 to 150.0),
        LandmarkId.LEFT_WRIST to (380.0 to 80.0),
        LandmarkId.LEFT_HIP to (280.0 to 380.0),
        LandmarkId.RIGHT_SHOULDER to (180.0 to 205.0),
        LandmarkId.RIGHT_ELBOW to (150.0 to 290.0),
        LandmarkId.RIGHT_WRIST to (140.0 to 370.0),
        LandmarkId.RIGHT_HIP to (200.0 to 385.0),
    )

    /** Step 3: what the pose model reports for the image it was given. */
    private fun modelReports(mirrored: Boolean): RawLandmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for ((id, point) in pose) {
            val seenAs = if (mirrored) id.mirrored else id
            val px = if (mirrored) width - point.first else point.first
            val slot = seenAs.index * Landmarks.STRIDE
            packed[slot] = px / width
            packed[slot + 1] = point.second / height
            packed[slot + 3] = if (id.name.startsWith("LEFT")) 0.91 else 0.83
        }
        return RawLandmarks(packed)
    }

    /** Steps 2 to 4 for one camera, exactly as the live screen runs them. */
    private fun anatomical(facing: CameraFacing, mirrorsFrame: Boolean = LiveCamera.mirrorsAnalysisFrame(facing)): Landmarks =
        adapter.toAnatomical(modelReports(mirrorsFrame), facing, width, height)

    @Test
    fun both_cameras_give_the_same_anatomical_left_and_right_for_the_same_pose() {
        val front = anatomical(CameraFacing.FRONT)
        val back = anatomical(CameraFacing.BACK)

        for (id in pose.keys) {
            assertEquals("x of " + id, back.x(id), front.x(id), TOLERANCE)
            assertEquals("y of " + id, back.y(id), front.y(id), TOLERANCE)
            assertEquals("visibility of " + id, back.visibility(id), front.visibility(id), TOLERANCE)
        }
    }

    /** Agreement alone could be two cameras wrong the same way. Check against the pose. */
    @Test
    fun the_subjects_raised_left_arm_is_left_in_both() {
        for (facing in CameraFacing.entries) {
            val out = anatomical(facing)
            for ((id, point) in pose) {
                assertEquals(facing.name + " x of " + id, point.first / width, out.x(id), TOLERANCE)
                assertEquals(facing.name + " y of " + id, point.second / width, out.y(id), TOLERANCE)
            }
            assertTrue(
                facing.name + ": the raised wrist is not the left one",
                out.y(LandmarkId.LEFT_WRIST) < out.y(LandmarkId.RIGHT_WRIST),
            )
        }
    }

    /** The guard has to be able to fail: mirror the back frame and the sides swap. */
    @Test
    fun a_capture_decision_that_disagrees_with_the_adapter_is_detected() {
        val wrong = anatomical(CameraFacing.BACK, mirrorsFrame = true)
        assertNotEquals(
            pose.getValue(LandmarkId.LEFT_WRIST).second / width,
            wrong.y(LandmarkId.LEFT_WRIST),
            TOLERANCE,
        )
    }

    /**
     * The skeleton lands where the preview shows the body, for both cameras. The
     * preview shows the camera's image, mirrored or not; the overlay draws the
     * anatomical landmarks, mirrored or not; both must put the subject's left
     * wrist at the same place on the screen.
     */
    @Test
    fun the_overlay_lands_on_the_preview_for_both_cameras() {
        val viewWidth = 1080f
        for (facing in CameraFacing.entries) {
            val out = anatomical(facing)
            for ((id, point) in pose) {
                val imageX = point.first / width
                val previewX = (if (LiveCamera.previewIsMirrored(facing)) 1.0 - imageX else imageX) * viewWidth
                val overlayX = OverlayGeometry.screenX(out.x(id), LiveCamera.previewIsMirrored(facing), viewWidth)
                assertEquals(facing.name + " " + id, previewX.toFloat(), overlayX, 1e-3f)
            }
        }
    }

    @Test
    fun the_front_camera_is_mirrored_twice_and_the_back_not_at_all() {
        // Front: flipped before the model, and shown flipped - the selfie view.
        assertEquals(true, LiveCamera.mirrorsAnalysisFrame(CameraFacing.FRONT))
        assertEquals(true, LiveCamera.previewIsMirrored(CameraFacing.FRONT))
        // Back: neither. No mirroring anywhere on this path.
        assertEquals(false, LiveCamera.mirrorsAnalysisFrame(CameraFacing.BACK))
        assertEquals(false, LiveCamera.previewIsMirrored(CameraFacing.BACK))
    }

    private companion object {
        const val TOLERANCE = 1e-12
    }
}
