package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.Tier
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.profile.TierBProfile
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Tier B lower-body and trunk profiles - CONTRACTS.md section 3,
 * DECISIONS.md P3-7.
 *
 * Lunge, Hip Abduction, Knee Raise, Sit-up and Leg Raise. Shoulder Press and Row
 * are the remaining two and are upper-body; the sit-up is here rather than with
 * them because it drives off the same joint and needs the same landmarks as the
 * knee raise and the leg raise, which is the boundary that matters for device
 * testing. P3-7 records that call.
 *
 * ### The mirror image of the Tier A ceiling test
 *
 * `ClaimLevelEnforcementTest` and `ArchetypeProfileTest` prove that a Tier A
 * profile *can* reach Level 3 and that the Tier C archetype cannot. This proves
 * the same for Tier B, on real profiles rather than test doubles: each of these
 * is a [TierBProfile], none is a [TierAProfile], and therefore none has a
 * `techniqueAuthority` property to pass to `TechniqueFault.from`. The call does
 * not compile, which is what CONTRACTS.md section 3 means by "construction is
 * gated, not filtered".
 *
 * ### What Tier B is, and is not
 *
 * DECISIONS.md P0-7: with no fault labels anywhere, Tier A means "cited
 * technique rules were written for this exercise" rather than "validated". Tier B
 * means no such rules were written. It is a scope boundary, honestly drawn and
 * enforced by types - not a statement that these exercises are measured less
 * accurately, which would be a claim nothing here supports.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class TierBProfileTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    private val tierB: List<TierBProfile>
        get() = ExerciseLibrary.profiles.filterIsInstance<TierBProfile>()

    // --- Scope --------------------------------------------------------------------

    @Test
    fun the_tier_b_profiles_built_so_far_are_the_lower_body_and_trunk_subset() {
        assertEquals(
            listOf("hip_abduction", "knee_raise", "leg_raise", "lunge", "situp"),
            ExerciseLibrary.profiles
                .filter { it.tier == Tier.B }
                .map { it.id }
                .sorted(),
            "CONTRACTS.md section 3 also assigns Shoulder Press and Row to Tier B; they " +
                "are upper-body and arrive in a later batch",
        )
    }

    // --- The claim ceiling, from the other side --------------------------------------

    @Test
    fun every_tier_b_profile_declares_tier_b_and_the_ceiling_that_follows_from_it() {
        assertTrue(tierB.isNotEmpty(), "no Tier B profiles; this test is guarding nothing")
        for (profile in tierB) {
            assertEquals(Tier.B, profile.tier, profile.id + " is not Tier B")
            assertEquals(
                ClaimLevel.L2_DEVIATION,
                profile.maxClaimLevel,
                profile.id + " has a ceiling that disagrees with its tier",
            )
        }
    }

    /**
     * The structural half. A Tier B profile is not a [TierAProfile], so it has no
     * `techniqueAuthority`, so it cannot be passed to `TechniqueFault.from` -
     * there is no unsafe path left to filter afterwards.
     *
     * Swept over every profile in the library rather than the five new ones, so a
     * later batch cannot quietly introduce a Tier B profile that is somehow also
     * a Tier A one.
     */
    @Test
    fun no_profile_below_tier_a_can_reach_a_level_three_finding() {
        for (profile in ExerciseLibrary.all) {
            if (profile.tier == Tier.A) continue
            assertFalse(
                profile is TierAProfile,
                profile.id + " is not Tier A but extends TierAProfile, so it can mint a " +
                    "technique authority",
            )
            assertFalse(
                profile.maxClaimLevel == ClaimLevel.L3_TECHNIQUE,
                profile.id + " claims a Level 3 ceiling without being Tier A",
            )
        }

        // For any of these, the following does not compile, and must never be
        // made to:
        //
        //     TechniqueFault.from(ExerciseLibrary.lunge.techniqueAuthority, ...)
        //         // unresolved reference: techniqueAuthority
    }

    // --- The camera-view contract --------------------------------------------------------

    /**
     * CLAUDE.md section 10, CONTRACTS.md section 8, and the views
     * FINAL_ARCHITECTURE section 5's final list assigns each entry. The reasoning
     * for each sits beside its declaration in the profile source.
     */
    @Test
    fun each_new_profile_declares_the_camera_view_and_posture_the_documents_assign() {
        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.lunge.requiredView)
        assertEquals(Posture.STANDING, ExerciseLibrary.lunge.posture)

        // Abduction is frontal-plane; side-on the leg swings toward the camera.
        assertEquals(CameraView.FRONT_ON, ExerciseLibrary.hipAbduction.requiredView)
        assertEquals(Posture.STANDING, ExerciseLibrary.hipAbduction.posture)

        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.kneeRaise.requiredView)
        assertEquals(Posture.STANDING, ExerciseLibrary.kneeRaise.posture)

        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.situp.requiredView)
        assertEquals(Posture.SUPINE, ExerciseLibrary.situp.posture)

        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.legRaise.requiredView)
        assertEquals(Posture.SUPINE, ExerciseLibrary.legRaise.posture)
    }

    @Test
    fun each_new_profile_drives_off_the_joint_that_defines_its_movement() {
        assertEquals(JointAngleId.KNEE, ExerciseLibrary.lunge.repSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.hipAbduction.repSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.kneeRaise.repSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.situp.repSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.legRaise.repSignal.joint)
    }

    // --- Repetition semantics: measured ----------------------------------------------------

    /**
     * Permanent guard - DECISIONS.md P3-7, CLAUDE.md section 18.
     *
     * Measured against MM-Fit's label files by the method P3-1 established. The
     * lunge needed a third signal the earlier passes did not use: both knees flex
     * on every repetition whichever leg is forward, so a knee angle reads one
     * cycle per side either way. Each ankle's distance from its hip separates
     * them - large for the stepping leg, small for the trailing one - and would
     * run at half the labelled rate if the legs swapped roles between
     * repetitions. Across 62 sets it reads the full rate in 37 and half in 3.
     *
     * The sit-up is unambiguous: trunk flexion is a midline movement and both
     * sides track it at +0.99 phase across 65 sets.
     */
    @Test
    fun lunge_and_situp_are_simultaneous_matching_the_mmfit_label_definition() {
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.lunge.repetitionSemantics,
        )
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.situp.repetitionSemantics,
        )
    }

    // --- Repetition semantics: assumed -----------------------------------------------------

    /**
     * Permanent guard - DECISIONS.md P3-7.
     *
     * **None of these three has any dataset behind it.** MM-Fit's ten classes
     * include no hip abduction, no knee raise and no leg raise, so unlike the
     * lunge and the sit-up above there is nothing to check them against. They are
     * kept in a separate test from the measured pin so the weaker basis stays
     * visible, exactly as P3-5 did for the glute bridge.
     *
     * The first two are close to physical necessity: one leg lifts while the
     * other bears the body's weight, and both cannot go at once from standing.
     *
     * **The leg raise is the weak one.** FINAL_ARCHITECTURE names the double-leg
     * version and describes its "large clean ROM", but a single-leg lying leg
     * raise is a common exercise and no document says which is meant beyond the
     * name. If the intent was the single-leg version this is a factor-of-two
     * error of precisely the kind P3-1 exists to prevent. This is the first place
     * to look if leg-raise counts ever read halved.
     */
    @Test
    fun hip_abduction_and_knee_raise_are_per_limb_by_anatomy_with_no_dataset_behind_them() {
        assertEquals(
            RepetitionSemantics.PER_LIMB,
            ExerciseLibrary.hipAbduction.repetitionSemantics,
        )
        assertEquals(
            RepetitionSemantics.PER_LIMB,
            ExerciseLibrary.kneeRaise.repetitionSemantics,
        )
    }

    @Test
    fun the_leg_raise_is_simultaneous_on_the_weakest_basis_of_any_profile_in_the_library() {
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.legRaise.repetitionSemantics,
            "DECISIONS.md P3-7: the double-leg lying leg raise is assumed, with no " +
                "dataset and no document naming which version is meant",
        )
    }

    // --- The behaviour, not just the declaration ---------------------------------------------

    @Test
    fun ten_lunges_count_as_ten_repetitions_and_not_twenty() {
        val counted = run(
            ExerciseLibrary.lunge,
            track(reps = 10, start = 172.0, far = 88.0) { l, r -> legFrame(l, r) },
        )
        assertEquals(
            10,
            counted.count { it.validity == RepValidity.VALID },
            "both knees flex on every lunge, so a lunge counts once and not once per leg",
        )
        assertTrue(counted.all { it.attributedSide == null })
    }

    @Test
    fun ten_situps_count_as_ten_repetitions_and_not_twenty() {
        val counted = run(
            ExerciseLibrary.situp,
            track(reps = 10, start = 142.0, far = 62.0) { l, r -> hipFrame(l, r) },
        )
        assertEquals(10, counted.count { it.validity == RepValidity.VALID })
        assertTrue(counted.all { it.attributedSide == null })
    }

    /**
     * The counterpart, and the reason the two semantics have to be declared
     * rather than defaulted. Five abductions on each leg, alternating, is ten
     * repetitions - and each one is attributed to the leg that performed it.
     */
    @Test
    fun five_abductions_on_each_leg_alternating_counts_as_ten_repetitions() {
        val counted = run(ExerciseLibrary.hipAbduction, alternatingTrack(perSide = 5))
        val valid = counted.filter { it.validity == RepValidity.VALID }

        assertEquals(
            10,
            valid.size,
            "five abductions per leg, alternating, must count as ten repetitions",
        )
        assertEquals(5, valid.count { it.attributedSide == Side.LEFT })
        assertEquals(5, valid.count { it.attributedSide == Side.RIGHT })
    }

    // --- Library bookkeeping -------------------------------------------------------------------

    /**
     * A variant is an entry and not a profile. With the Reverse Lunge added,
     * getting this wrong would report seven Tier B profiles where CONTRACTS.md
     * assigns the tier to six built so far - the same miscount CLAUDE.md section
     * 9 forbids, one tier down.
     */
    @Test
    fun the_reverse_lunge_is_counted_as_an_entry_and_never_as_a_tier_b_profile() {
        assertTrue(ExerciseLibrary.all.any { it.id == "reverse_lunge" })
        assertFalse(
            ExerciseLibrary.profiles.any { it.id == "reverse_lunge" },
            "the reverse lunge is counted among the profiles",
        )
        assertTrue(
            ExerciseLibrary.variants.any { it.id == "reverse_lunge" },
            "the reverse lunge is not counted among the variants",
        )
        for (profile in tierB) {
            assertFalse(
                profile is ExerciseVariant,
                profile.id + " is a variant counted as a Tier B profile",
            )
        }
    }

    // --- Driving the tracker ---------------------------------------------------------------------

    private fun run(
        profile: RepetitionExercise,
        frames: List<Pair<Double, Landmarks>>,
    ): List<RepRecord> {
        val tracker = BilateralRepTracker(profile)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in frames) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }
        return counted
    }

    // --- Synthetic movement -------------------------------------------------------------------------

    /** Both sides together: dwell, travel out, dwell, travel back, dwell. */
    private fun track(
        reps: Int,
        start: Double,
        far: Double,
        frame: (Double, Double) -> Landmarks,
    ): List<Pair<Double, Landmarks>> {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(left: Double, right: Double) {
            frames += (index * frameMs) to frame(left, right)
            index++
        }

        repeat(12) { emit(start, start) }
        repeat(reps) {
            for (step in 1..30) emit(
                start + (far - start) * step / 30.0,
                start + (far - start) * step / 30.0,
            )
            repeat(10) { emit(far, far) }
            for (step in 1..30) emit(
                far - (far - start) * step / 30.0,
                far - (far - start) * step / 30.0,
            )
            repeat(12) { emit(start, start) }
        }
        return frames
    }

    /**
     * One leg abducts and returns while the other stands, then they swap. This is
     * the movement the `PER_LIMB` declaration describes, and the antiphase is the
     * point.
     */
    private fun alternatingTrack(perSide: Int): List<Pair<Double, Landmarks>> {
        val standing = 176.0
        val out = 132.0
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(left: Double, right: Double) {
            frames += (index * frameMs) to hipFrame(left, right)
            index++
        }

        repeat(12) { emit(standing, standing) }
        repeat(perSide) {
            for (step in 1..25) emit(standing - 44.0 * step / 25.0, standing)
            repeat(8) { emit(out, standing) }
            for (step in 1..25) emit(out + 44.0 * step / 25.0, standing)
            repeat(10) { emit(standing, standing) }

            for (step in 1..25) emit(standing, standing - 44.0 * step / 25.0)
            repeat(8) { emit(standing, out) }
            for (step in 1..25) emit(standing, out + 44.0 * step / 25.0)
            repeat(10) { emit(standing, standing) }
        }
        return frames
    }

    // --- Frame construction ------------------------------------------------------------------------------

    private fun put(packed: DoubleArray, id: LandmarkId, x: Double, y: Double) {
        val base = id.index * Landmarks.STRIDE
        packed[base] = x
        packed[base + 1] = y
        packed[base + 2] = 0.0
        packed[base + 3] = 0.95
    }

    /**
     * Places a three-landmark joint whose interior angle at the vertex is exactly
     * [degrees], by putting the distal landmark on a circle about the vertex at
     * that angle from the proximal ray.
     */
    private fun joint(
        packed: DoubleArray,
        proximal: LandmarkId,
        vertex: LandmarkId,
        distal: LandmarkId,
        vertexX: Double,
        vertexY: Double,
        dirX: Double,
        dirY: Double,
        degrees: Double,
        limb: Double = 0.20,
    ) {
        put(packed, vertex, vertexX, vertexY)
        put(packed, proximal, vertexX + dirX * limb, vertexY + dirY * limb)
        val radians = degrees * PI / 180.0
        val rx = dirX * cos(radians) - dirY * sin(radians)
        val ry = dirX * sin(radians) + dirY * cos(radians)
        put(packed, distal, vertexX + rx * limb, vertexY + ry * limb)
    }

    /** Hip - knee - ankle on both legs. */
    private fun legFrame(leftDeg: Double, rightDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        joint(
            packed, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE,
            0.44, 0.70, 0.0, -1.0, leftDeg,
        )
        joint(
            packed, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE,
            0.56, 0.70, 0.0, -1.0, rightDeg,
        )
        return Landmarks.fromPacked(packed)
    }

    /** Shoulder - hip - knee on both sides: trunk to thigh. */
    private fun hipFrame(leftDeg: Double, rightDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        joint(
            packed, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE,
            0.46, 0.55, 0.0, -1.0, leftDeg,
        )
        joint(
            packed, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE,
            0.54, 0.55, 0.0, -1.0, rightDeg,
        )
        return Landmarks.fromPacked(packed)
    }
}
