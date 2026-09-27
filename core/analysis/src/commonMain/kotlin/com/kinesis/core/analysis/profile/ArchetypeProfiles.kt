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
     * **Filmed front-on.** Alternating arms means both must be visible at the
     * same time; side-on would occlude the far arm for the whole set and halve
     * the repetition count in a way that looks like poor detection.
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

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

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

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE,
    )
}

/** See [ArchetypeProfiles.bicepCurl]. */
class BicepCurlProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "bicep_curl"
    override val displayName: String = "Bicep Curl"
    override val requiredView: CameraView = CameraView.FRONT_ON
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
