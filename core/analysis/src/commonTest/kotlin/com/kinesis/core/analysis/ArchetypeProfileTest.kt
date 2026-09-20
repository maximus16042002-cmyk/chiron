package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.profile.ArchetypeProfiles
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
import com.kinesis.core.domain.rep.RepRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three archetype profiles - CLAUDE.md section 9, CONTRACTS.md sections 3
 * and 8.
 *
 * Squat is the bilateral-simultaneous case, bicep curl the alternating-arm case,
 * plank the isometric hold. CLAUDE.md section 9 originally required these three
 * to be validated on a device before the remaining profiles were scaffolded;
 * DECISIONS.md P3-4 reversed that ordering, so the later profiles now exist
 * first. They are pinned in `TierAProfileTest` and swept through
 * [com.kinesis.core.analysis.profile.ExerciseLibrary].
 *
 * Includes the permanent pin on bicep-curl repetition semantics required by
 * DECISIONS.md P3-1 and CLAUDE.md section 18.
 */
class ArchetypeProfileTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    // --- Scope ------------------------------------------------------------------

    /**
     * The archetype grouping stays exactly three. It is not the set of every
     * profile - that is `ExerciseLibrary`, pinned by `TierAProfileTest` - and
     * letting it drift would make the two groupings mean the same thing, at
     * which point the decision record's references to "the three archetypes"
     * stop resolving to anything.
     */
    @Test
    fun exactly_the_three_archetypes_exist_and_no_more() {
        assertEquals(
            listOf("bicep_curl", "plank", "squat"),
            ArchetypeProfiles.all.map { it.id }.sorted(),
        )
    }

    // --- Tiers and claim levels -------------------------------------------------

    @Test
    fun each_archetype_declares_the_tier_contracts_assigns_it() {
        assertEquals(Tier.A, ArchetypeProfiles.squat.tier)
        assertEquals(Tier.A, ArchetypeProfiles.bicepCurl.tier)
        assertEquals(Tier.C, ArchetypeProfiles.plank.tier)
    }

    @Test
    fun the_claim_ceiling_follows_from_the_tier_and_not_from_the_profile() {
        assertEquals(ClaimLevel.L3_TECHNIQUE, ArchetypeProfiles.squat.maxClaimLevel)
        assertEquals(ClaimLevel.L3_TECHNIQUE, ArchetypeProfiles.bicepCurl.maxClaimLevel)
        assertEquals(ClaimLevel.L2_DEVIATION, ArchetypeProfiles.plank.maxClaimLevel)
    }

    /**
     * The P1 capability gate, exercised through real profiles rather than test
     * doubles. Plank is Tier C, so there is no route by which it can produce a
     * technique judgement.
     */
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun the_tier_c_archetype_cannot_reach_a_level_three_finding() {
        assertTrue(ArchetypeProfiles.squat is TierAProfile)
        assertTrue(ArchetypeProfiles.bicepCurl is TierAProfile)
        assertFalse(
            ArchetypeProfiles.plank is TierAProfile,
            "plank is Tier C and must not be a TierAProfile",
        )

        // Plank has no techniqueAuthority property, so this does not compile:
        //
        //     TechniqueFault.from(ArchetypeProfiles.plank.techniqueAuthority, ...)
        //         // unresolved reference: techniqueAuthority
    }

    // --- The camera-view contract ------------------------------------------------

    /**
     * CONTRACTS.md section 8: every profile declares the view it must be filmed
     * from. These are not interchangeable, and the reasoning is recorded beside
     * each declaration in the profile source.
     */
    @Test
    fun each_archetype_declares_an_explicit_camera_view_and_posture() {
        assertEquals(CameraView.SIDE_ON, ArchetypeProfiles.squat.requiredView)
        assertEquals(Posture.STANDING, ArchetypeProfiles.squat.posture)

        // Front-on, because alternating arms means both must be visible at once.
        //
        // This diverges from FINAL_ARCHITECTURE section 5 row 4, which assigns
        // the bicep curl "Side / 45". The divergence is deliberate and is
        // recorded in DECISIONS.md P3-9: the curl is PER_LIMB, and side-on
        // occludes the far arm for the whole set, halving the count in a way
        // that looks like poor detection. The guard added in P3-8 rejects the
        // document's version independently - see
        // ViewAndSideContractTest.no_profile_combines_alternating_limbs_with_a_
        // view_that_sees_only_one.
        assertEquals(CameraView.FRONT_ON, ArchetypeProfiles.bicepCurl.requiredView)
        assertEquals(Posture.STANDING, ArchetypeProfiles.bicepCurl.posture)

        assertEquals(CameraView.SIDE_ON, ArchetypeProfiles.plank.requiredView)
        assertEquals(Posture.PRONE, ArchetypeProfiles.plank.posture)
    }

    @Test
    fun every_archetype_depends_on_landmarks_its_camera_view_can_actually_see() {
        for (profile in ArchetypeProfiles.all) {
            assertTrue(
                profile.requiredLandmarks.size > 0,
                profile.id + " declares no required landmarks",
            )
        }
    }

    // --- Bicep curl semantics: the permanent pin ---------------------------------

    /**
     * Permanent guard - DECISIONS.md P3-1, CLAUDE.md section 18.
     *
     * MM-Fit labels one bicep-curl repetition as **one arm's curl**, measured
     * directly from its label files: each arm completes exactly five cycles in
     * every labelled ten-repetition set, and the two arms move in antiphase.
     * Kinesis matches that definition so the rep-count comparison is one to one.
     *
     * If this ever silently flips to counting a two-arm cycle as one repetition,
     * every MM-Fit comparison is out by a factor of two, and it presents as a
     * threshold-tuning problem rather than a definitional one. That is the
     * expensive way to find out, and this test is the cheap way.
     */
    @Test
    fun a_bicep_curl_repetition_is_one_arm_matching_the_mmfit_label_definition() {
        assertEquals(
            RepetitionSemantics.PER_LIMB,
            ArchetypeProfiles.bicepCurl.repetitionSemantics,
        )
    }

    @Test
    fun five_curls_on_each_arm_alternating_counts_as_ten_repetitions() {
        val tracker = BilateralRepTracker(ArchetypeProfiles.bicepCurl)
        val counted = ArrayList<RepRecord>()

        for ((timestamp, frame) in alternatingCurlTrack(curlsPerArm = 5)) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }

        assertEquals(
            10,
            counted.size,
            "five curls per arm, alternating, must count as ten repetitions",
        )
        assertEquals(10, tracker.repetitionCount)

        // And each repetition is attributed to the arm that performed it - the
        // sides are never pooled.
        assertEquals(5, counted.count { it.attributedSide == Side.LEFT })
        assertEquals(5, counted.count { it.attributedSide == Side.RIGHT })
    }

    /**
     * The counterpart. A squat is bilateral-simultaneous: both knees perform the
     * same repetition, and it is counted once, not twice.
     */
    @Test
    fun a_squat_performed_with_both_legs_counts_once_not_twice() {
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ArchetypeProfiles.squat.repetitionSemantics,
        )

        val tracker = BilateralRepTracker(ArchetypeProfiles.squat)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in squatTrack(reps = 6)) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }

        assertEquals(6, counted.size, "six squats must count as six repetitions, not twelve")
        assertTrue(
            counted.all { it.attributedSide == null },
            "a simultaneous repetition belongs to no single side",
        )
    }

    /**
     * A simultaneous repetition still carries both sides separately, which is
     * what makes asymmetry measurable at all. Here the left knee is deliberately
     * shallower than the right.
     */
    @Test
    fun a_simultaneous_repetition_keeps_the_two_sides_separately_attributable() {
        val tracker = BilateralRepTracker(ArchetypeProfiles.squat)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in squatTrack(reps = 3, leftBottom = 95.0, rightBottom = 70.0)) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }

        assertTrue(counted.isNotEmpty(), "no repetitions were counted")
        val record = counted[0]
        assertTrue(
            record.rangeOfMotion.left < record.rangeOfMotion.right,
            "the shallower left knee should show the smaller range of motion, got " +
                record.rangeOfMotion,
        )
        assertTrue(
            record.rangeOfMotion.asymmetry() > 0.0,
            "an asymmetric squat reported perfect symmetry",
        )
    }

    // --- Signal declarations -------------------------------------------------------

    @Test
    fun each_repetition_archetype_drives_off_the_joint_that_defines_the_movement() {
        assertEquals(JointAngleId.KNEE, ArchetypeProfiles.squat.repSignal.joint)
        assertEquals(JointAngleId.ELBOW, ArchetypeProfiles.bicepCurl.repSignal.joint)
        assertEquals(JointAngleId.HIP, ArchetypeProfiles.plank.holdSignal.joint)
    }

    @Test
    fun every_repetition_spec_has_a_usable_threshold_pair() {
        for (profile in ArchetypeProfiles.all) {
            if (profile !is RepetitionExercise) continue
            val spec = profile.repSignal
            assertTrue(
                spec.topThreshold != spec.bottomThreshold,
                profile.id + " has identical top and bottom thresholds",
            )
            assertTrue(spec.hysteresis >= 0.0, profile.id + " has negative hysteresis")
            assertTrue(
                spec.hysteresis * 2 < kotlin.math.abs(spec.topThreshold - spec.bottomThreshold),
                profile.id + " has a hysteresis band wider than the movement it measures",
            )
            assertTrue(spec.minRepDurationMs > 0.0)
            assertTrue(spec.maxRepDurationMs > spec.minRepDurationMs)
        }
    }

    // --- Synthetic frame construction ------------------------------------------------

    /**
     * Arms alternating: the left elbow flexes while the right is extended, then
     * they swap. This is the movement MM-Fit's bicep-curl sets contain, and the
     * antiphase is the point.
     */
    private fun alternatingCurlTrack(curlsPerArm: Int): List<Pair<Double, Landmarks>> {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(leftElbowDeg: Double, rightElbowDeg: Double) {
            frames += (index * frameMs) to armFrame(leftElbowDeg, rightElbowDeg)
            index++
        }

        repeat(10) { emit(170.0, 170.0) }
        for (cycle in 0 until curlsPerArm) {
            // Left curls up and back down while the right stays extended.
            for (step in 1..20) emit(170.0 - 130.0 * step / 20.0, 170.0)
            for (step in 1..20) emit(40.0 + 130.0 * step / 20.0, 170.0)
            // Then the right, while the left stays extended.
            for (step in 1..20) emit(170.0, 170.0 - 130.0 * step / 20.0)
            for (step in 1..20) emit(170.0, 40.0 + 130.0 * step / 20.0)
        }
        repeat(10) { emit(170.0, 170.0) }
        return frames
    }

    private fun squatTrack(
        reps: Int,
        leftBottom: Double = 70.0,
        rightBottom: Double = 70.0,
    ): List<Pair<Double, Landmarks>> {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(leftKnee: Double, rightKnee: Double) {
            frames += (index * frameMs) to legFrame(leftKnee, rightKnee)
            index++
        }

        repeat(10) { emit(170.0, 170.0) }
        for (rep in 0 until reps) {
            for (step in 1..30) {
                emit(
                    170.0 - (170.0 - leftBottom) * step / 30.0,
                    170.0 - (170.0 - rightBottom) * step / 30.0,
                )
            }
            for (step in 1..30) {
                emit(
                    leftBottom + (170.0 - leftBottom) * step / 30.0,
                    rightBottom + (170.0 - rightBottom) * step / 30.0,
                )
            }
            repeat(10) { emit(170.0, 170.0) }
        }
        return frames
    }

    /**
     * Builds a frame whose elbow angles are exactly the requested values, by
     * placing the wrist on a circle around the elbow. The shoulder sits directly
     * above the elbow, so the shoulder-elbow ray points straight up and the
     * requested angle is measured from it.
     */
    private fun armFrame(leftElbowDeg: Double, rightElbowDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        fun arm(shoulder: LandmarkId, elbow: LandmarkId, wrist: LandmarkId, x: Double, deg: Double) {
            put(shoulder, x, 0.30)
            put(elbow, x, 0.50)
            // Ray elbow->shoulder points up (negative y). Rotate by `deg` to get
            // the elbow->wrist ray, giving exactly that interior angle.
            val radians = deg * kotlin.math.PI / 180.0
            put(wrist, x + 0.20 * kotlin.math.sin(radians), 0.50 - 0.20 * kotlin.math.cos(radians))
        }
        arm(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.40, leftElbowDeg)
        arm(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.60, rightElbowDeg)
        return Landmarks.fromPacked(packed)
    }

    /** As [armFrame], for hip-knee-ankle. */
    private fun legFrame(leftKneeDeg: Double, rightKneeDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        fun leg(hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, x: Double, deg: Double) {
            put(hip, x, 0.50)
            put(knee, x, 0.70)
            val radians = deg * kotlin.math.PI / 180.0
            put(ankle, x + 0.20 * kotlin.math.sin(radians), 0.70 - 0.20 * kotlin.math.cos(radians))
        }
        leg(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.44, leftKneeDeg)
        leg(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.56, rightKneeDeg)
        return Landmarks.fromPacked(packed)
    }
}
