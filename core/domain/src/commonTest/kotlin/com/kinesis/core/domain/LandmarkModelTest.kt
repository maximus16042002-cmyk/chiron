package com.kinesis.core.domain

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the landmark model - CONTRACTS.md sections 4 and 7.
 *
 * The indices are not arbitrary: they are MediaPipe Pose's own ordering, and
 * the facade in CONTRACTS.md section 5 takes flat DoubleArrays in that order.
 * If this enum is ever reordered, every recorded session and every golden file
 * silently changes meaning, so the ordering is asserted here explicitly rather
 * than left to the declaration order of the enum.
 */
class LandmarkModelTest {

    @Test
    fun the_landmark_set_is_mediapipe_pose_with_its_own_indices() {
        assertEquals(33, LandmarkId.COUNT)
        assertEquals(33, LandmarkId.entries.size)

        // Spot-checked against MediaPipe Pose landmark indices. These are the
        // ones the analysis engine actually uses; the rest are pinned by the
        // ordinal check below.
        assertEquals(0, LandmarkId.NOSE.index)
        assertEquals(11, LandmarkId.LEFT_SHOULDER.index)
        assertEquals(12, LandmarkId.RIGHT_SHOULDER.index)
        assertEquals(13, LandmarkId.LEFT_ELBOW.index)
        assertEquals(14, LandmarkId.RIGHT_ELBOW.index)
        assertEquals(15, LandmarkId.LEFT_WRIST.index)
        assertEquals(16, LandmarkId.RIGHT_WRIST.index)
        assertEquals(23, LandmarkId.LEFT_HIP.index)
        assertEquals(24, LandmarkId.RIGHT_HIP.index)
        assertEquals(25, LandmarkId.LEFT_KNEE.index)
        assertEquals(26, LandmarkId.RIGHT_KNEE.index)
        assertEquals(27, LandmarkId.LEFT_ANKLE.index)
        assertEquals(28, LandmarkId.RIGHT_ANKLE.index)
        assertEquals(31, LandmarkId.LEFT_FOOT_INDEX.index)
        assertEquals(32, LandmarkId.RIGHT_FOOT_INDEX.index)
    }

    @Test
    fun index_and_ordinal_agree_and_round_trip() {
        for (id in LandmarkId.entries) {
            assertEquals(id.ordinal, id.index)
            assertSame(id, LandmarkId.fromIndex(id.index))
        }
        assertFailsWith<IllegalArgumentException> { LandmarkId.fromIndex(-1) }
        assertFailsWith<IllegalArgumentException> { LandmarkId.fromIndex(LandmarkId.COUNT) }
    }

    /**
     * The mirror pairing is anatomy, not camera geometry - it says which
     * landmark is the same body part on the other side. The pose adapter uses it
     * to undo front-camera mirroring, but the pairing itself carries no
     * knowledge of any camera.
     */
    @Test
    fun every_landmark_has_a_mirror_and_mirroring_twice_is_the_identity() {
        for (id in LandmarkId.entries) {
            assertSame(id, id.mirrored.mirrored, id.name + " does not round-trip through its mirror")
        }
        assertSame(LandmarkId.RIGHT_WRIST, LandmarkId.LEFT_WRIST.mirrored)
        assertSame(LandmarkId.LEFT_WRIST, LandmarkId.RIGHT_WRIST.mirrored)
        assertSame(LandmarkId.RIGHT_HIP, LandmarkId.LEFT_HIP.mirrored)
        assertSame(LandmarkId.MOUTH_RIGHT, LandmarkId.MOUTH_LEFT.mirrored)

        // Midline landmarks are their own mirror.
        assertSame(LandmarkId.NOSE, LandmarkId.NOSE.mirrored)
    }

    @Test
    fun a_sided_landmark_never_mirrors_to_itself() {
        val sided = LandmarkId.entries.filter { it.name.contains("LEFT") || it.name.contains("RIGHT") }
        assertEquals(32, sided.size, "only NOSE sits on the midline in MediaPipe Pose")
        for (id in sided) {
            assertTrue(id !== id.mirrored, id.name + " mirrors to itself")
        }
    }

    // --- Landmarks ------------------------------------------------------------

    @Test
    fun landmarks_read_back_what_was_packed_into_them() {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        val base = LandmarkId.LEFT_KNEE.index * Landmarks.STRIDE
        packed[base] = 0.41
        packed[base + 1] = 0.62
        packed[base + 2] = -0.13
        packed[base + 3] = 0.88

        val landmarks = Landmarks.fromPacked(packed)
        assertEquals(0.41, landmarks.x(LandmarkId.LEFT_KNEE))
        assertEquals(0.62, landmarks.y(LandmarkId.LEFT_KNEE))
        assertEquals(-0.13, landmarks.z(LandmarkId.LEFT_KNEE))
        assertEquals(0.88, landmarks.visibility(LandmarkId.LEFT_KNEE))

        val point = landmarks[LandmarkId.LEFT_KNEE]
        assertEquals(0.41, point.x)
        assertEquals(0.88, point.visibility)
    }

    @Test
    fun landmarks_do_not_alias_the_array_they_were_built_from() {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        val landmarks = Landmarks.fromPacked(packed)
        packed[LandmarkId.NOSE.index * Landmarks.STRIDE] = 99.0
        assertEquals(0.0, landmarks.x(LandmarkId.NOSE), "a caller mutating its buffer must not change a frame")
    }

    @Test
    fun a_wrongly_sized_buffer_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> { Landmarks.fromPacked(DoubleArray(10)) }
        assertFailsWith<IllegalArgumentException> {
            Landmarks.fromXyzAndVisibility(DoubleArray(99), DoubleArray(32))
        }
    }

    /**
     * The facade in CONTRACTS.md section 5 takes x/y/z in one array and
     * visibility in another. This is the seam between that shape and the
     * internal one, so it is checked directly.
     */
    @Test
    fun the_facade_shaped_constructor_interleaves_the_two_arrays_correctly() {
        val xyz = DoubleArray(3 * LandmarkId.COUNT)
        val visibility = DoubleArray(LandmarkId.COUNT)
        xyz[LandmarkId.RIGHT_ANKLE.index * 3] = 0.7
        xyz[LandmarkId.RIGHT_ANKLE.index * 3 + 1] = 0.9
        xyz[LandmarkId.RIGHT_ANKLE.index * 3 + 2] = 0.05
        visibility[LandmarkId.RIGHT_ANKLE.index] = 0.76

        val landmarks = Landmarks.fromXyzAndVisibility(xyz, visibility)
        assertEquals(0.7, landmarks.x(LandmarkId.RIGHT_ANKLE))
        assertEquals(0.9, landmarks.y(LandmarkId.RIGHT_ANKLE))
        assertEquals(0.05, landmarks.z(LandmarkId.RIGHT_ANKLE))
        assertEquals(0.76, landmarks.visibility(LandmarkId.RIGHT_ANKLE))
    }

    // --- Required landmarks and the visibility policy -------------------------

    @Test
    fun required_landmarks_keep_their_order_and_reject_an_empty_set() {
        val required = RequiredLandmarks.of(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE)
        assertEquals(2, required.size)
        assertSame(LandmarkId.LEFT_HIP, required[0])
        assertSame(LandmarkId.LEFT_KNEE, required[1])
        assertFailsWith<IllegalArgumentException> { RequiredLandmarks.of() }
    }

    @Test
    fun a_visibility_policy_cannot_be_declared_upside_down() {
        assertFailsWith<IllegalArgumentException> {
            VisibilityPolicy(reliable = 0.3, marginal = 0.8)
        }
        assertFailsWith<IllegalArgumentException> {
            VisibilityPolicy(reliable = 1.4, marginal = 0.2)
        }
    }

    @Test
    fun the_provisional_policy_is_ordered_and_within_range() {
        val policy = VisibilityPolicy.PROVISIONAL
        assertTrue(policy.marginal <= policy.reliable)
        assertTrue(policy.marginal in 0.0..1.0)
        assertTrue(policy.reliable in 0.0..1.0)
    }
}
