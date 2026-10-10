package com.kinesis.core.analysis.profile

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.profile.TierCProfile
import com.kinesis.core.domain.rep.FormMeasure

/**
 * The three archetype profiles - CLAUDE.md section 9.
 *
 * One per analysis shape, built and tested before the remaining twelve are
 * scaffolded:
 *
 * | Archetype | Shape it proves |
 * |---|---|
 * | Squat | Bilateral simultaneous repetition |
 * | Bicep curl | Alternating single-limb repetition |
 * | Plank | Isometric hold |
 *
 * CLAUDE.md section 9 required all fifteen not to be written in one pass, on the
 * grounds that fifteen untested profiles are fifteen things wrong in the same
 * way. **DECISIONS.md P3-4 reversed that ordering** at the project owner's
 * direction: the remaining profiles are now built before these three have been
 * through P4 device testing, accepting the rework risk P3-4 states. What did not
 * change is that each still arrives with its guard tests written first.
 *
 * These three keep their own object because CLAUDE.md section 9 and the decision
 * record refer to them as a group. The registry every guard sweep iterates is
 * [ExerciseLibrary], which holds these and everything added since.
 *
 * **Every threshold below is provisional.** They are starting points that let
 * the engine be built and tested, not measured quantities, and they support no
 * claim - CLAUDE.md section 23. P6 personalises them from each user's own
 * calibration repetitions, which is where the values that matter come from.
 * Nothing here has been validated against a human being.
 */
object ArchetypeProfiles {

    /**
     * Squat — Tier A, bilateral simultaneous.
     *
     * Driven by knee flexion. Both knees perform the same repetition, so it
     * counts once, with each knee's range of motion retained separately so
     * asymmetry stays measurable.
     *
     * **Filmed side-on.** Knee and hip flexion are sagittal-plane movements; from
     * the front, the knee angle barely changes through a full squat and what
     * little it does is foreshortening rather than flexion. The near-side
     * occlusion this costs is the accepted trade, and it is why both knees are
     * tracked rather than assumed symmetric.
     */
    val squat: SquatProfile = SquatProfile()

    /**
     * Bicep curl — Tier A, alternating single-limb.
     *
     * One arm's curl is one repetition, matching MM-Fit's label definition
     * measured in DECISIONS.md P3-1.
     *
     * **Filmed side-on, one arm at a time, turning round for the other**
     * (DECISIONS.md P3-32). It was front-on so alternating arms could both be
     * seen; on the phone the front-on elbow angle snapped to about 0 degrees and
     * counted arm swings as curls (P3-31). See [BicepCurlProfile.requiredView].
     */
    val bicepCurl: BicepCurlProfile = BicepCurlProfile()

    /**
     * Plank — Tier C, isometric hold.
     *
     * Driven by the shoulder-hip-knee angle, which is the body line: straight is
     * about 180 degrees, and the failure mode is the hips dropping or piking.
     *
     * **Filmed side-on.** The body line is only visible in profile; from the
     * front or above, a sagging hip is invisible.
     *
     * Tier C, so no technique judgement is possible for it at all - there is no
     * route by which this profile can construct a Level 3 finding.
     */
    val plank: PlankProfile = PlankProfile()

    /** All archetypes, for the sweeps that must cover every profile. */
    val all: List<ExerciseProfile> = listOf(squat, bicepCurl, plank)
}

/** See [ArchetypeProfiles.squat]. */
class SquatProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "squat"
    override val displayName: String = "Squat"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.STANDING

    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /**
     * The knee nearer the camera counts - DECISIONS.md P3-68, the owner's decision on P3-23's open
     * question. Side-on, the far knee is often hidden behind the near leg, and a squat counted
     * only when both knees completed. Near-side counting is the push-up's and the row's: whichever
     * knee completes counts, and the same squat seen by both is counted once. No left-right
     * comparison is claimed for the squat.
     */
    override val analysedSides: AnalysedSides = AnalysedSides.NEAR_SIDE_ONLY

    /** Moving away from the start is the lowering - DECISIONS.md P3-30. */
    override val awayFromStart: Contraction = Contraction.ECCENTRIC

    /**
     * DECISIONS.md P3-44: the trunk's lean against the shin's at the deepest frame, for Layer 2's
     * rule that they stay parallel (ACE, Bodyweight Squat). The goblet squat asks for it too,
     * through its parent.
     */
    override val formMeasures: List<FormMeasure> = listOf(FormMeasure.TRUNK_MINUS_SHIN_LEAN)

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.KNEE,
        plane = MeasurementPlane.IMAGE_2D,
        // Standing is a nearly straight knee; the bottom of a squat is deep
        // flexion. Provisional.
        topThreshold = 160.0,
        bottomThreshold = 90.0,
        hysteresis = 5.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 15_000.0,
    )

    /**
     * The torso, as for the push-up and the row (P3-8, P3-28): `ValidityGate` invalidates a frame
     * on any required landmark below the floor, so requiring the far knee and ankle a side-on
     * camera often cannot see would discard exactly the frames near-side counting exists to keep.
     * DECISIONS.md P3-68.
     */
    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
    )
}

/** See [ArchetypeProfiles.bicepCurl]. */
class BicepCurlProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "bicep_curl"
    override val displayName: String = "Bicep Curl"

    /**
     * Side-on, one arm at a time with that arm nearer the camera - DECISIONS.md
     * P3-32, the owner's decision after P3-31.
     *
     * It was front-on (P3-9), so both alternating arms could be seen at once. On
     * the phone, front-on made the flat-image elbow angle snap from straight to
     * about 0 degrees in one or two results as the forearm came towards the
     * camera, and a forward arm swing did the same: one set counted 7 curls more
     * than were done, another counted 5 with no curls at all. Side-on, elbow
     * flexion is in the image plane, an arm swing leaves the elbow straight, and
     * the owner's sideways sets counted 16 of 16 and 15 of 15 (P3-31).
     *
     * The user turns round for the other arm. The semantics stay per limb and
     * both sides stay analysed, as for the side-on knee raise: each arm's machine
     * reports on its own, and the resting far arm makes no repetitions (P3-31).
     * MM-Fit films its curls front-on, so its curl counts are no longer evidence
     * for this profile (CLAUDE.md section 22).
     */
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.STANDING

    /**
     * One arm, one repetition.
     *
     * Measured from MM-Fit's own label files: each arm completes exactly five
     * cycles in every labelled ten-repetition set, and the two arms move in
     * antiphase. Matching that definition is what keeps the rep-count comparison
     * one to one. DECISIONS.md P3-1, pinned by `ArchetypeProfileTest`.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.PER_LIMB

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

    /** Moving away from the start is the lifting - DECISIONS.md P3-30. */
    override val awayFromStart: Contraction = Contraction.CONCENTRIC

    /**
     * DECISIONS.md P3-44: the upper arm against the trunk, for Layer 2's rule that the upper
     * arms stay close to the ribs (NSCA Coach 7(4), Table 4).
     */
    override val formMeasures: List<FormMeasure> = listOf(FormMeasure.UPPER_ARM_TRUNK_MAX)

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.ELBOW,
        plane = MeasurementPlane.IMAGE_2D,
        // Arm hanging is a nearly straight elbow; the top of the curl is deep
        // flexion. Provisional.
        //
        // DECISIONS.md P3-23, from the 2026-09-27 side-on set on the phone: the
        // resting arm read 148-170 degrees, so the old top of 155 (at rest from
        // 147) sat on it and every wobble made a junk record; 145 puts the edge
        // at 137, below it. Nine deep curls timed out at the old 10 s with the
        // arm still on its way down, so a slow curl with a pause now has 20 s.
        topThreshold = 145.0,
        bottomThreshold = 60.0,
        hysteresis = 8.0,
        minRepDurationMs = 500.0,
        maxRepDurationMs = 20_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_ELBOW, LandmarkId.RIGHT_ELBOW,
        LandmarkId.LEFT_WRIST, LandmarkId.RIGHT_WRIST,
    )
}

/** See [ArchetypeProfiles.plank]. */
class PlankProfile : TierCProfile(), IsometricExercise {

    override val id: String = "plank"
    override val displayName: String = "Plank"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.PRONE

    override val holdSignal: HoldSignalSpec = HoldSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // A straight shoulder-hip-knee line. Provisional.
        targetValue = 180.0,
        toleranceBand = 20.0,
        minHoldDurationMs = 3_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE,
    )
}
