package com.kinesis.pose

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard test - CLAUDE.md section 4 and 18, CONTRACTS.md section 4.
 *
 * FINAL_ARCHITECTURE section 12-D names front-camera mirroring as one of the
 * three highest technical risks, and the one that fails most quietly: nothing
 * crashes, nothing looks wrong on screen, and every left/right measurement in
 * the system is silently swapped.
 *
 * The contract being guarded: the platform pose adapter converts camera
 * coordinates into anatomical left and right before anything reaches the shared
 * core. A landmark named LEFT_KNEE is the subject's left knee, regardless of
 * which side of the image it appeared on.
 *
 * The test therefore stages one physical scene, describes it the way each
 * camera would report it, and asserts that both descriptions produce the same
 * anatomical output. It lives in :app because the adapter is platform code -
 * the shared core has no CameraFacing type at all, which is checked separately
 * by SharedCoreSourceGuardTest in :core:analysis.
 *
 * This test exists from P1 and is never deleted.
 */
class MirroringRegressionTest {

    /**
     * One physical scene, described in anatomical terms.
     *
     * Every landmark gets a distinct x, y and visibility. That matters: a pose
     * where any two landmarks share a value is a pose in which a side swap can
     * hide, and a real frame has all 33 populated anyway. Values are normalised
     * image coordinates as MediaPipe reports them, x in 0..1 increasing toward
     * the right-hand edge of an unmirrored image.
     */
    private object Scene {
        fun x(id: LandmarkId): Double = 0.05 + 0.02 * id.index
        fun y(id: LandmarkId): Double = 0.10 + 0.025 * id.index
        fun visibility(id: LandmarkId): Double = 0.30 + 0.02 * id.index
        fun z(id: LandmarkId): Double = -0.20 + 0.01 * id.index
    }

    /**
     * What the back camera reports. The image is not mirrored, so the pose model
     * labels the subject's left side as LEFT and the coordinates are already in
     * the anatomical frame.
     */
    private fun backCameraFrame(): RawLandmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for (id in LandmarkId.entries) {
            val slot = id.index * Landmarks.STRIDE
            packed[slot] = Scene.x(id)
            packed[slot + 1] = Scene.y(id)
            packed[slot + 2] = Scene.z(id)
            packed[slot + 3] = Scene.visibility(id)
        }
        return RawLandmarks(packed)
    }

    /**
     * What the front camera reports for the same physical scene.
     *
     * The front preview is delivered horizontally mirrored, so the subject's
     * left wrist appears toward the right-hand edge of the image. The pose model
     * labels landmarks from the image it is given, so it calls that wrist
     * RIGHT_WRIST. Both the slot and the x coordinate are therefore wrong
     * anatomically, and the adapter has to undo both.
     *
     * The opposite-side pairing used here is LandmarkId.mirrored, which is
     * anatomy rather than camera geometry and is pinned independently by
     * LandmarkModelTest in :core:domain.
     */
    private fun frontCameraFrame(): RawLandmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for (id in LandmarkId.entries) {
            val slot = id.mirrored.index * Landmarks.STRIDE
            packed[slot] = 1.0 - Scene.x(id)
            packed[slot + 1] = Scene.y(id)
            packed[slot + 2] = Scene.z(id)
            packed[slot + 3] = Scene.visibility(id)
        }
        return RawLandmarks(packed)
    }

    private val adapter = PoseAdapter()

    /**
     * The adapter also puts both axes in one unit (P3-16). On a square frame
     * that step changes nothing, so every assertion here tests mirroring and
     * mirroring alone. AspectCorrectionRegressionTest covers the two together,
     * through the front path, on frames that are not square.
     */
    private fun PoseAdapter.toAnatomical(raw: RawLandmarks, facing: CameraFacing): Landmarks =
        toAnatomical(raw, facing, frameWidthPx = SQUARE_FRAME_PX, frameHeightPx = SQUARE_FRAME_PX)

    // --- The contract ---------------------------------------------------------

    @Test
    fun both_cameras_produce_the_same_anatomical_assignment() {
        val fromBack = adapter.toAnatomical(backCameraFrame(), CameraFacing.BACK)
        val fromFront = adapter.toAnatomical(frontCameraFrame(), CameraFacing.FRONT)

        for (id in LandmarkId.entries) {
            assertEquals(
                "x disagrees for " + id.name,
                fromBack.x(id),
                fromFront.x(id),
                TOLERANCE,
            )
            assertEquals(
                "y disagrees for " + id.name,
                fromBack.y(id),
                fromFront.y(id),
                TOLERANCE,
            )
            assertEquals(
                "visibility disagrees for " + id.name,
                fromBack.visibility(id),
                fromFront.visibility(id),
                TOLERANCE,
            )
        }
    }

    @Test
    fun the_anatomical_output_holds_the_side_the_subject_actually_used() {
        for (facing in CameraFacing.entries) {
            val frame = if (facing == CameraFacing.FRONT) frontCameraFrame() else backCameraFrame()
            val anatomical = adapter.toAnatomical(frame, facing)

            for (id in LandmarkId.entries) {
                assertEquals(
                    facing.name + ": x for " + id.name,
                    Scene.x(id),
                    anatomical.x(id),
                    TOLERANCE,
                )
                assertEquals(
                    facing.name + ": visibility for " + id.name,
                    Scene.visibility(id),
                    anatomical.visibility(id),
                    TOLERANCE,
                )
            }
        }
    }

    /**
     * The scene has to be capable of failing. If the staged pose were symmetric,
     * a broken adapter would still pass every assertion above.
     */
    @Test
    fun the_staged_scene_can_actually_detect_a_side_swap() {
        for (id in LandmarkId.entries) {
            if (id === id.mirrored) continue
            assertNotEquals(
                "a side swap would be invisible for " + id.name,
                Scene.x(id),
                Scene.x(id.mirrored),
                TOLERANCE,
            )
            assertNotEquals(
                "a side swap would be invisible in the visibility of " + id.name,
                Scene.visibility(id),
                Scene.visibility(id.mirrored),
                TOLERANCE,
            )
        }

        // And the front camera really does deliver the two sides transposed,
        // rather than the test handing the adapter identical input twice.
        val backPacked = backCameraFrame().copyPacked()
        val frontPacked = frontCameraFrame().copyPacked()
        assertTrue(
            "front and back raw frames are identical, so this test proves nothing",
            !backPacked.contentEquals(frontPacked),
        )
    }

    /**
     * Mirroring twice is the identity. This catches an adapter that flips the x
     * coordinate but forgets to swap the landmark slots, or vice versa - each of
     * which passes a naive round-trip check on its own.
     */
    @Test
    fun applying_the_front_correction_to_its_own_output_restores_the_input() {
        val front = frontCameraFrame()
        val once = adapter.toAnatomical(front, CameraFacing.FRONT)
        val twice = adapter.toAnatomical(RawLandmarks(once.copyPacked()), CameraFacing.FRONT)

        for (id in LandmarkId.entries) {
            assertEquals(
                "round trip failed for " + id.name,
                front.copyPacked()[id.index * Landmarks.STRIDE],
                twice.x(id),
                TOLERANCE,
            )
        }
    }

    @Test
    fun the_back_camera_path_changes_nothing() {
        val raw = backCameraFrame()
        val anatomical = adapter.toAnatomical(raw, CameraFacing.BACK)
        assertTrue(
            "the back camera frame is already anatomical and must pass through unchanged",
            raw.copyPacked().contentEquals(anatomical.copyPacked()),
        )
    }

    private companion object {
        /**
         * These are exact copies and reflections of literal inputs, not computed
         * kinematics, so the only error present is the representation of 1.0 - x.
         * CONTRACTS.md section 6 forbids asserting exact equality on continuous
         * values across platforms, so a tolerance is used even here.
         */
        const val TOLERANCE = 1e-12

        const val SQUARE_FRAME_PX = 640
    }
}
