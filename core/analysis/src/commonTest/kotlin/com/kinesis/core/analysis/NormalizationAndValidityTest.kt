package com.kinesis.core.analysis

import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.analysis.landmark.ValidityGate
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the head of the real-time path - CONTRACTS.md section 7:
 *
 *     landmarks -> normalise -> validity -> current-frame kinematics
 *
 * P1 implements the first two steps. Both run once per frame, so both are O(1)
 * in the number of landmarks the profile asks for, and neither looks at session
 * history. Runs on JVM and JS.
 */
class NormalizationAndValidityTest {

    // --- Building frames ------------------------------------------------------

    /**
     * A standing subject, upright, facing the camera. Coordinates are normalised
     * image coordinates, y increasing downward as MediaPipe reports it.
     */
    private fun standingFrame(
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
        scale: Double = 1.0,
        visibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        fun put(id: LandmarkId, x: Double, y: Double, v: Double = visibility) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = offsetX + x * scale
            packed[base + 1] = offsetY + y * scale
            packed[base + 2] = 0.0
            packed[base + 3] = v
        }
        put(LandmarkId.LEFT_SHOULDER, 0.40, 0.30)
        put(LandmarkId.RIGHT_SHOULDER, 0.60, 0.30)
        put(LandmarkId.LEFT_HIP, 0.44, 0.55)
        put(LandmarkId.RIGHT_HIP, 0.56, 0.55)
        put(LandmarkId.LEFT_KNEE, 0.44, 0.75)
        put(LandmarkId.RIGHT_KNEE, 0.56, 0.75)
        put(LandmarkId.LEFT_ANKLE, 0.44, 0.95)
        put(LandmarkId.RIGHT_ANKLE, 0.56, 0.95)
        return Landmarks.fromPacked(packed)
    }

    /** Exactly the landmarks [standingFrame] writes. */
    private val populatedLandmarks = listOf(
        LandmarkId.LEFT_SHOULDER,
        LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP,
        LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE,
        LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE,
        LandmarkId.RIGHT_ANKLE,
    )

    private val squatLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP,
        LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE,
        LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE,
        LandmarkId.RIGHT_ANKLE,
    )

    // --- Normalisation --------------------------------------------------------

    @Test
    fun normalisation_puts_the_origin_at_the_mid_hip() {
        val normalized = assertNotNull(LandmarkNormalizer.normalize(standingFrame()))
        // Mid-hip of (0.44, 0.55) and (0.56, 0.55).
        assertEquals(0.50, normalized.originX, 1e-12)
        assertEquals(0.55, normalized.originY, 1e-12)
        // Which therefore sits at zero in the normalised frame.
        assertEquals(0.0, normalized.x(LandmarkId.LEFT_HIP) + normalized.x(LandmarkId.RIGHT_HIP), 1e-12)
        assertEquals(0.0, normalized.y(LandmarkId.LEFT_HIP), 1e-12)
        assertEquals(0.0, normalized.y(LandmarkId.RIGHT_HIP), 1e-12)
    }

    @Test
    fun normalisation_scales_by_the_torso_so_one_unit_is_one_torso_length() {
        val normalized = assertNotNull(LandmarkNormalizer.normalize(standingFrame()))
        // Mid-shoulder is at y 0.30, mid-hip at 0.55, so the torso is 0.25 long.
        assertEquals(0.25, normalized.torsoScale, 1e-12)

        // Mid-shoulder therefore lands exactly one unit above the origin. y grows
        // downward in image coordinates, so upward is negative.
        val midShoulderY =
            (normalized.y(LandmarkId.LEFT_SHOULDER) + normalized.y(LandmarkId.RIGHT_SHOULDER)) / 2.0
        assertEquals(-1.0, midShoulderY, 1e-12)
    }

    /**
     * The point of normalising at all: the same pose at a different distance from
     * the camera, or in a different part of the frame, has to produce the same
     * numbers. Without this, every threshold in the system is a function of how
     * far away the user happened to stand.
     */
    @Test
    fun the_same_pose_normalises_identically_regardless_of_position_and_distance() {
        val near = assertNotNull(LandmarkNormalizer.normalize(standingFrame()))
        val farAndOffCentre = assertNotNull(
            LandmarkNormalizer.normalize(standingFrame(offsetX = 0.17, offsetY = -0.08, scale = 0.6)),
        )

        // Only the landmarks the staged pose actually populates; the rest sit at
        // the array default and carry no pose information.
        for (id in populatedLandmarks) {
            assertEquals(near.x(id), farAndOffCentre.x(id), 1e-9, "x drifted for " + id.name)
            assertEquals(near.y(id), farAndOffCentre.y(id), 1e-9, "y drifted for " + id.name)
        }
    }

    @Test
    fun a_frame_with_no_measurable_torso_does_not_normalise() {
        // Shoulders and hips coincident: torso length zero, scale undefined.
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        for (id in listOf(
            LandmarkId.LEFT_SHOULDER,
            LandmarkId.RIGHT_SHOULDER,
            LandmarkId.LEFT_HIP,
            LandmarkId.RIGHT_HIP,
        )) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = 0.5
            packed[base + 1] = 0.5
            packed[base + 3] = 0.99
        }
        assertNull(
            LandmarkNormalizer.normalize(Landmarks.fromPacked(packed)),
            "a degenerate torso must be reported, not divided by",
        )
    }

    @Test
    fun normalisation_leaves_the_sides_where_it_found_them() {
        val normalized = assertNotNull(LandmarkNormalizer.normalize(standingFrame()))
        // The left hip is at x 0.44, left of the 0.50 mid-hip, so it stays negative.
        assertTrue(normalized.x(LandmarkId.LEFT_HIP) < 0.0)
        assertTrue(normalized.x(LandmarkId.RIGHT_HIP) > 0.0)
        assertEquals(
            abs(normalized.x(LandmarkId.LEFT_HIP)),
            abs(normalized.x(LandmarkId.RIGHT_HIP)),
            1e-12,
        )
    }

    // --- Validity -------------------------------------------------------------

    @Test
    fun a_clearly_seen_frame_is_valid() {
        assertEquals(
            FrameValidity.VALID,
            ValidityGate.evaluate(standingFrame(visibility = 0.95), squatLandmarks, VisibilityPolicy.PROVISIONAL),
        )
    }

    @Test
    fun a_marginally_seen_frame_is_degraded_rather_than_thrown_away() {
        val policy = VisibilityPolicy(reliable = 0.70, marginal = 0.40)
        val frame = standingFrame(visibility = 0.55)
        assertEquals(FrameValidity.DEGRADED, ValidityGate.evaluate(frame, squatLandmarks, policy))
    }

    @Test
    fun a_frame_missing_a_required_landmark_is_invalid() {
        val policy = VisibilityPolicy(reliable = 0.70, marginal = 0.40)
        val frame = standingFrame(visibility = 0.10)
        assertEquals(FrameValidity.INVALID, ValidityGate.evaluate(frame, squatLandmarks, policy))
    }

    /**
     * Validity is judged against what the profile asked for, not against all 33
     * landmarks. A squat does not care that the wrists are out of frame, and a
     * gate that insisted otherwise would block scoring on perfectly good reps.
     */
    @Test
    fun validity_ignores_landmarks_the_profile_did_not_ask_for() {
        val frame = standingFrame(visibility = 0.95) // wrists left at visibility 0.0
        assertEquals(
            FrameValidity.VALID,
            ValidityGate.evaluate(frame, squatLandmarks, VisibilityPolicy.PROVISIONAL),
        )
        assertEquals(
            FrameValidity.INVALID,
            ValidityGate.evaluate(
                frame,
                RequiredLandmarks.of(LandmarkId.LEFT_WRIST, LandmarkId.RIGHT_WRIST),
                VisibilityPolicy.PROVISIONAL,
            ),
        )
    }

    @Test
    fun the_worst_required_landmark_decides_the_verdict() {
        val policy = VisibilityPolicy(reliable = 0.70, marginal = 0.40)
        val packed = standingFrame(visibility = 0.95).copyPacked()
        // One knee drops to marginal; everything else is clear.
        packed[LandmarkId.RIGHT_KNEE.index * Landmarks.STRIDE + 3] = 0.50
        assertEquals(
            FrameValidity.DEGRADED,
            ValidityGate.evaluate(Landmarks.fromPacked(packed), squatLandmarks, policy),
        )

        // And one landmark below marginal is enough to invalidate the frame.
        packed[LandmarkId.RIGHT_KNEE.index * Landmarks.STRIDE + 3] = 0.20
        assertEquals(
            FrameValidity.INVALID,
            ValidityGate.evaluate(Landmarks.fromPacked(packed), squatLandmarks, policy),
        )
    }

    @Test
    fun the_thresholds_are_inclusive_at_their_boundary() {
        // Pinned so a later change to the comparison direction is visible.
        val policy = VisibilityPolicy(reliable = 0.70, marginal = 0.40)
        assertEquals(
            FrameValidity.VALID,
            ValidityGate.evaluate(standingFrame(visibility = 0.70), squatLandmarks, policy),
        )
        assertEquals(
            FrameValidity.DEGRADED,
            ValidityGate.evaluate(standingFrame(visibility = 0.40), squatLandmarks, policy),
        )
    }
}
