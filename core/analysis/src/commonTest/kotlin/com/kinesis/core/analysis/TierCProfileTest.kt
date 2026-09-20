package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldAnalyser
import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.Tier
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.profile.TierCProfile
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three Tier C isometric holds - CONTRACTS.md section 3, DECISIONS.md P3-9.
 *
 * Plank arrived in P3 as an archetype; Wall Sit and Side Plank complete the tier
 * and, with them, the library. New file rather than an addition to
 * [IsometricSeparationTest], which is about keeping the two analysis modes
 * apart rather than about what any one profile declares - the same split as
 * `TierAProfileTest` and `TierBProfileTest`.
 *
 * ### What a Tier C threshold is, and what it is not
 *
 * `HoldSignalSpec.targetValue` is a **detection** parameter: it decides whether
 * the subject is in the position at all. It is not a Layer 2 technique rule and
 * carries no `sourceRef`, because there is nowhere for one to go and nothing for
 * it to justify - `sourceRef` is a field of `TechniqueFault`, reachable only
 * through `TierAProfile.techniqueAuthority`, and none of these is a
 * `TierAProfile`. CLAUDE.md section 12a scopes cited thresholds to "Layer 2
 * technique rules, Tier A only", and a Tier C profile has none by construction.
 *
 * P3-2 recorded the converse of this as a general lesson: a detection threshold
 * must not leak into the quantities being measured. The same boundary read the
 * other way is that a detection threshold must not be dressed as a validated
 * one.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class TierCProfileTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    private val tierC: List<IsometricExercise>
        get() = ExerciseLibrary.profiles.filterIsInstance<IsometricExercise>()

    // --- Scope -------------------------------------------------------------------

    @Test
    fun tier_c_is_exactly_the_three_holds_contracts_assigns_it() {
        assertEquals(
            listOf("plank", "side_plank", "wall_sit"),
            ExerciseLibrary.profiles
                .filter { it.tier == Tier.C }
                .map { it.id }
                .sorted(),
            "CONTRACTS.md section 3 line 104: | C | Wall Sit, Plank, Side Plank |",
        )
    }

    /**
     * Every Tier C entry is an isometric hold and every isometric hold is Tier C.
     * The two are not the same statement, and nothing in the type system ties
     * them together - `TierCProfile` and `IsometricExercise` are independent.
     */
    @Test
    fun tier_c_and_isometric_hold_describe_the_same_three_profiles() {
        assertEquals(
            ExerciseLibrary.profiles.filter { it.tier == Tier.C }.map { it.id }.sorted(),
            tierC.map { it.id }.sorted(),
        )
    }

    // --- The claim ceiling ----------------------------------------------------------

    @Test
    fun every_tier_c_profile_declares_tier_c_and_the_ceiling_that_follows_from_it() {
        assertTrue(tierC.isNotEmpty(), "no Tier C profiles; this test is guarding nothing")
        for (profile in tierC) {
            assertEquals(Tier.C, profile.tier, profile.id + " is not Tier C")
            assertEquals(
                ClaimLevel.L2_DEVIATION,
                profile.maxClaimLevel,
                profile.id + " has a ceiling that disagrees with its tier",
            )
        }
    }

    /**
     * The structural half, and the reason none of these thresholds carries a
     * citation: there is no route from any of them to a Level 3 finding, so there
     * is no technique rule for a `sourceRef` to justify. DECISIONS.md P3-9.
     */
    @Test
    fun no_tier_c_profile_can_reach_the_finding_a_source_reference_would_belong_to() {
        for (profile in tierC) {
            assertFalse(
                profile is TierAProfile,
                profile.id + " is Tier C but extends TierAProfile, so it can mint a " +
                    "technique authority",
            )
            assertTrue(profile is TierCProfile, profile.id + " does not extend TierCProfile")
        }

        // For any of these, both of the following do not compile, and must never
        // be made to:
        //
        //     ExerciseLibrary.wallSit.techniqueAuthority
        //         // unresolved reference: techniqueAuthority
        //     ExerciseLibrary.wallSit.holdSignal.sourceRef
        //         // unresolved reference: sourceRef
    }

    // --- The camera-view contract -------------------------------------------------------

    /**
     * The views FINAL_ARCHITECTURE section 5's final list assigns, rows 15-17.
     * The side plank's is the one worth reading twice: **Front**, not Side. In a
     * side plank the body's frontal plane faces sideways, so a camera facing the
     * subject sees the whole body line and the hip drop that breaks it. Filming
     * it "from the side" would look along the line and see nothing.
     */
    @Test
    fun each_new_profile_declares_the_camera_view_and_posture_the_documents_assign() {
        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.wallSit.requiredView)
        assertEquals(
            Posture.SEATED,
            ExerciseLibrary.wallSit.posture,
            "a wall sit's body configuration is sitting - hips and knees near ninety, " +
                "trunk upright - with the wall in place of a chair",
        )

        assertEquals(
            CameraView.FRONT_ON,
            ExerciseLibrary.sidePlank.requiredView,
            "FINAL_ARCHITECTURE section 5 row 17 assigns Front. A side plank's body " +
                "line is only visible from the direction the subject faces.",
        )
        assertEquals(Posture.SIDE_LYING, ExerciseLibrary.sidePlank.posture)

        // Unchanged, re-pinned here so the three holds are described in one place.
        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.plank.requiredView)
        assertEquals(Posture.PRONE, ExerciseLibrary.plank.posture)
    }

    @Test
    fun each_hold_drives_off_the_joint_that_defines_its_position() {
        assertEquals(JointAngleId.KNEE, ExerciseLibrary.wallSit.holdSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.sidePlank.holdSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.plank.holdSignal.joint)
    }

    /**
     * FINAL_ARCHITECTURE section 3 is explicit that these are "different
     * geometries sharing a word": a plank's sag and a side plank's hip drop are
     * not the same rule applied twice. They happen to share a driving joint, and
     * this pins that they are nonetheless separate specifications, so a later
     * refactor cannot quietly make one profile borrow the other's numbers.
     */
    @Test
    fun the_plank_and_the_side_plank_are_separate_specifications() {
        assertFalse(
            ExerciseLibrary.plank.holdSignal === ExerciseLibrary.sidePlank.holdSignal,
            "the two holds share one spec instance, so a change to either changes both",
        )
        assertEquals(
            CameraView.SIDE_ON,
            ExerciseLibrary.plank.requiredView,
        )
        assertEquals(
            CameraView.FRONT_ON,
            ExerciseLibrary.sidePlank.requiredView,
        )
    }

    @Test
    fun every_hold_spec_in_the_library_has_a_usable_tolerance_band() {
        for (profile in tierC) {
            val spec = profile.holdSignal
            assertTrue(spec.toleranceBand > 0.0, profile.id + " has a non-positive tolerance")
            assertTrue(
                spec.toleranceBand < 90.0,
                profile.id + " tolerates more than a right angle of deviation, which would " +
                    "accept almost any position as the held one",
            )
            assertTrue(
                spec.minHoldDurationMs > 0.0,
                profile.id + " has no minimum hold duration",
            )
        }
    }

    // --- Behaviour ---------------------------------------------------------------------

    @Test
    fun a_wall_sit_held_at_the_target_knee_angle_is_measured_as_one_unbroken_hold() {
        val analyser = HoldAnalyser(ExerciseLibrary.wallSit)
        val target = ExerciseLibrary.wallSit.holdSignal.targetValue
        for ((timestamp, value) in flat(target + 3.0, seconds = 20.0)) {
            analyser.step(timestamp, value)
        }
        val record = analyser.finish()

        assertEquals(HoldValidity.VALID, record.validity)
        assertEquals(0, record.breakCount)
        assertTrue(record.totalHoldMs > 19_000.0, "measured only " + record.totalHoldMs + " ms")
    }

    /**
     * The failure mode the side plank exists to measure, and the one a repetition
     * FSM would have reported as repetitions - CLAUDE.md section 11. Two hip
     * drops and two recoveries are two breaks in one hold.
     */
    @Test
    fun a_side_plank_whose_hips_drop_reports_breaks_and_never_repetitions() {
        val analyser = HoldAnalyser(ExerciseLibrary.sidePlank)
        val spec = ExerciseLibrary.sidePlank.holdSignal
        val held = spec.targetValue - 4.0
        val dropped = spec.targetValue - spec.toleranceBand - 12.0

        val samples = ArrayList<Double>()
        samples += List(150) { held }
        samples += List(45) { dropped }
        samples += List(150) { held }
        samples += List(45) { dropped }
        samples += List(150) { held }

        for ((timestamp, value) in track(samples)) analyser.step(timestamp, value)
        val record = analyser.finish()

        assertEquals(2, record.breakCount, "expected two hip drops")
        assertTrue(
            record.longestUnbrokenMs < record.totalHoldMs,
            "a broken hold cannot have its longest period equal its total",
        )

        // HoldRecord has no repetition count to report, and the following does
        // not compile:
        //
        //     record.repCount        // unresolved reference
    }

    // --- The side plank's one-sidedness, verified rather than assumed ------------------

    /**
     * DECISIONS.md P3-9. A side plank is performed on one side of the body, which
     * is a different kind of one-sidedness from the row's in P3-8 - there the
     * camera could not see a limb, here there is only one limb doing the work.
     * `AnalysedSides` was deliberately **not** added to [IsometricExercise], and
     * this is the evidence for why.
     *
     * The hold signal is the shoulder-hip-knee angle, which is a property of the
     * **body line** rather than of a limb. Both sides' triples describe
     * essentially the same line, so there is no pairing decision to make and
     * nothing an `AnalysedSides` declaration could have been consumed by -
     * `HoldAnalyser.step` takes a scalar, not a
     * [com.kinesis.core.domain.bilateral.BilateralSignal].
     *
     * This test shows the premise holds rather than asserting it: with the
     * down-side landmarks occluded, the angle comes back measured on one side and
     * `NaN` on the other, exactly as `JointAngles` already does everywhere else.
     */
    @Test
    fun a_side_plank_with_the_down_side_occluded_yields_one_measured_side_and_a_nan() {
        val angle = JointAngles.compute(
            sidePlankFrame(degrees = 176.0, downSideVisible = false),
            ExerciseLibrary.sidePlank.holdSignal.joint,
            MeasurementPlane.IMAGE_2D,
            policy,
        )

        assertTrue(
            angle.left.isNaN() != angle.right.isNaN(),
            "expected exactly one side to be measurable, got " + angle,
        )
        assertTrue(
            angle.asymmetry().isNaN(),
            "an occluded side produced a finite asymmetry of " + angle.asymmetry(),
        )
    }

    /**
     * And the other half: when both sides are visible, a body-line angle reads
     * the same on both, because it is one line and not two limbs. That is what
     * makes a near-side-only declaration meaningless for this exercise rather
     * than merely unnecessary.
     */
    @Test
    fun a_body_line_angle_reads_the_same_on_both_sides_because_it_is_one_line() {
        val angle = JointAngles.compute(
            sidePlankFrame(degrees = 176.0, downSideVisible = true),
            ExerciseLibrary.sidePlank.holdSignal.joint,
            MeasurementPlane.IMAGE_2D,
            policy,
        )

        assertFalse(angle.left.isNaN(), "left side unmeasured")
        assertFalse(angle.right.isNaN(), "right side unmeasured")
        assertEquals(
            angle.left,
            angle.right,
            absoluteTolerance = 1.0,
            message = "the two sides of a body-line angle disagree by more than a degree, " +
                "which would mean it is not measuring one line",
        )
    }

    /**
     * The hold analyser already copes with an unmeasurable signal - it is how
     * lost tracking is reported. Confirmed here on a Tier C profile so the
     * P3-9 reasoning rests on an observation rather than on the existence of a
     * test elsewhere.
     */
    @Test
    fun a_hold_survives_the_signal_dropping_out_and_reports_the_gap_as_coverage() {
        val analyser = HoldAnalyser(ExerciseLibrary.sidePlank)
        val held = ExerciseLibrary.sidePlank.holdSignal.targetValue - 4.0
        val samples = List(150) { held } + List(60) { Double.NaN } + List(150) { held }

        for ((timestamp, value) in track(samples)) analyser.step(timestamp, value)
        val record = analyser.finish()

        assertTrue(record.coverage < 1.0, "coverage did not register the lost frames")
        assertTrue(record.totalHoldMs > 0.0, "the hold either side of the gap was discarded")
    }

    // --- Track and frame construction ------------------------------------------------------

    private fun flat(value: Double, seconds: Double): List<Pair<Double, Double>> =
        track(List((seconds * 30.0).toInt()) { value })

    private fun track(samples: List<Double>): List<Pair<Double, Double>> =
        samples.mapIndexed { index, value -> (index * frameMs) to value }

    /**
     * A side plank seen front-on: shoulder, hip and knee lie along one nearly
     * straight line, with [degrees] the angle at the hip. When
     * [downSideVisible] is false the down-side landmarks sit below the
     * visibility floor, as they do when the torso hides them.
     */
    private fun sidePlankFrame(degrees: Double, downSideVisible: Boolean): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)

        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = visibility
        }

        fun line(
            shoulder: LandmarkId,
            hip: LandmarkId,
            knee: LandmarkId,
            hipY: Double,
            visibility: Double,
        ) {
            put(hip, 0.50, hipY, visibility)
            // Ray hip -> shoulder runs along the body toward the head.
            put(shoulder, 0.50 - 0.22, hipY, visibility)
            val radians = degrees * PI / 180.0
            put(
                knee,
                0.50 - 0.22 * cos(radians),
                hipY - 0.22 * sin(radians),
                visibility,
            )
        }

        // Up side, always clear.
        line(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, 0.48, 0.95)
        // Down side, hidden behind the torso unless stated otherwise.
        line(
            LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE,
            0.52, if (downSideVisible) 0.95 else 0.05,
        )
        return Landmarks.fromPacked(packed)
    }
}
