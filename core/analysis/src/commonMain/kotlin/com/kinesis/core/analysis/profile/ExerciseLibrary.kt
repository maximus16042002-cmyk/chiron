package com.kinesis.core.analysis.profile

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.TierAProfile

/**
 * Every exercise profile that exists, in one place.
 *
 * `ArchetypeProfiles.all` was the whole set while the three archetypes were the
 * whole set. It is not any more, and a guard sweep that still iterated it would
 * quietly stop covering everything added afterwards - which is the failure mode
 * the sweeps exist to prevent. This object is what they iterate; the archetypes
 * keep their own grouping because CLAUDE.md section 9 and DECISIONS.md refer to
 * them as a group.
 *
 * Growth is deliberate, not incidental: `TierAProfileTest` pins the contents, so
 * adding a profile without saying so in the same change is a test failure.
 *
 * **Every threshold in this file is provisional**, on the same footing as the
 * archetypes' and for the same reason (CLAUDE.md section 23). They are starting
 * points chosen so the engine can be exercised, not measured quantities, and
 * they support no claim. P6 personalises them from the user's own calibration
 * repetitions; P4 sanity-checks them on a device first. Nothing here has been
 * validated against a human being.
 */
object ExerciseLibrary {

    /** See `ArchetypeProfiles.squat`. */
    val squat: SquatProfile get() = ArchetypeProfiles.squat

    /** See `ArchetypeProfiles.bicepCurl`. */
    val bicepCurl: BicepCurlProfile get() = ArchetypeProfiles.bicepCurl

    /** See `ArchetypeProfiles.plank`. */
    val plank: PlankProfile get() = ArchetypeProfiles.plank

    val pushUp: PushUpProfile = PushUpProfile()

    val lateralRaise: LateralRaiseProfile = LateralRaiseProfile()

    val gluteBridge: GluteBridgeProfile = GluteBridgeProfile()

    /**
     * Goblet Squat — the same movement as [squat], under load, named separately
     * in the library.
     *
     * It declares nothing of its own beyond its name and its note: tier, claim
     * ceiling, camera view, posture, repetition semantics, thresholds and
     * required landmarks all come from the squat profile instance at runtime.
     * CONTRACTS.md section 3 and DECISIONS.md P3-6.
     *
     * **One assumption rides on this, and it is about people rather than code.**
     * The documents are explicit that a variant shares its parent's calibration
     * baseline. A goblet squat is performed holding a weight at the chest, which
     * plausibly changes trunk angle and achievable depth, so a Level 2 finding of
     * "shallower than your normal" here is measured against the user's unloaded
     * squats. That has not been measured against anything. P3-6 records it as the
     * entry to revisit if P6 calibration shows the two differ.
     */
    val gobletSquat: RepetitionVariant = RepetitionVariant(
        parent = ArchetypeProfiles.squat,
        id = "goblet_squat",
        displayName = "Goblet Squat",
        coachingNote = "Hold the weight at your chest and keep your elbows inside your knees.",
    )

    // --- Tier B: lower body and trunk (DECISIONS.md P3-7) -----------------------

    /** See [LungeProfile]. Filmed side-on, with the rear-leg caveat recorded there. */
    val lunge: LungeProfile = LungeProfile()

    /** See [HipAbductionProfile]. */
    val hipAbduction: HipAbductionProfile = HipAbductionProfile()

    /** See [KneeRaiseProfile]. */
    val kneeRaise: KneeRaiseProfile = KneeRaiseProfile()

    /** See [SitUpProfile]. Tier B, and the standing contingency for the glute bridge. */
    val situp: SitUpProfile = SitUpProfile()

    /** See [LegRaiseProfile]. The weakest semantics declaration in the library. */
    val legRaise: LegRaiseProfile = LegRaiseProfile()

    // --- Tier B: upper body (DECISIONS.md P3-8) ---------------------------------

    /** See [ShoulderPressProfile]. Seated, per P0-4. */
    val shoulderPress: ShoulderPressProfile = ShoulderPressProfile()

    /** See [RowProfile]. The library's only near-side-only profile. */
    val row: RowProfile = RowProfile()

    // --- Tier C: the remaining isometric holds (DECISIONS.md P3-9) --------------

    /** See [WallSitProfile]. */
    val wallSit: WallSitProfile = WallSitProfile()

    /** See [SidePlankProfile]. Filmed front-on, which is not the intuitive guess. */
    val sidePlank: SidePlankProfile = SidePlankProfile()

    /**
     * Reverse Lunge — the second variant the documents name, unblocked by
     * [lunge] arriving.
     *
     * Same delegation as [gobletSquat]: tier, claim ceiling, camera view,
     * posture, repetition semantics, thresholds and required landmarks all come
     * from the lunge profile instance. Being a variant of a Tier B parent, it
     * inherits a Level 2 ceiling and has no route to a technique authority - the
     * rule needs no special case, because a Tier B parent has none to lend.
     *
     * It shares the lunge's recorded rear-leg problem, for the same reason it
     * shares everything else.
     */
    val reverseLunge: RepetitionVariant = RepetitionVariant(
        parent = lunge,
        id = "reverse_lunge",
        displayName = "Reverse Lunge",
        coachingNote = "Step backwards into the lunge rather than forwards, and lower " +
            "under control.",
    )

    /**
     * Every library entry: profiles and variants together.
     *
     * The guard sweeps iterate this, so nothing escapes them. It is **not** the
     * profile count - see [profiles], and CLAUDE.md section 9 on why the two are
     * never conflated.
     */
    val all: List<ExerciseProfile> = listOf(
        ArchetypeProfiles.squat,
        ArchetypeProfiles.bicepCurl,
        ArchetypeProfiles.plank,
        pushUp,
        lateralRaise,
        gluteBridge,
        gobletSquat,
        lunge,
        hipAbduction,
        kneeRaise,
        situp,
        legRaise,
        shoulderPress,
        row,
        wallSit,
        sidePlank,
        reverseLunge,
    )

    /**
     * The entries that are profiles in their own right.
     *
     * CLAUDE.md section 9: "15 exercise profiles ... presented as 17 library
     * entries including 2 variants that share a parent profile and calibration.
     * Never '17 exercises'." Separating the two here is what keeps that count
     * honest structurally rather than editorially - a tier sweep over [all]
     * would read the goblet squat as a sixth Tier A exercise.
     */
    val profiles: List<ExerciseProfile> = all.filter { it !is ExerciseVariant }

    /** The entries that inherit another entry's profile. The complement of [profiles]. */
    val variants: List<ExerciseVariant> = all.filterIsInstance<ExerciseVariant>()
}

/**
 * Push-up — Tier A, bilateral simultaneous.
 *
 * Driven by elbow flexion. Both arms perform the same repetition, so it counts
 * once, with each arm's range of motion retained separately so asymmetry stays
 * measurable. Measured against MM-Fit's labels across 65 sets — DECISIONS.md
 * P3-5.
 *
 * **Filmed side-on.** Elbow flexion during a push-up is a sagittal-plane
 * movement, and so is the trunk line the Tier A technique rules will care about;
 * from the head end the elbow angle changes by foreshortening rather than by
 * flexion. The near-arm occlusion this costs is the accepted trade, and it is
 * why both arms are tracked rather than assumed symmetric.
 *
 * **One thing P4 should look at.** In MM-Fit's own data the push-up elbow angle
 * is a markedly noisier signal than torso height, and the noise is one-sided in
 * the way an occluded far arm looks (DECISIONS.md P3-5). That is a property of
 * MM-Fit's camera placement and its OpenPose estimates, and says nothing about
 * how MediaPipe behaves here - but if the elbow proves an unreliable driver on a
 * real device, torso height is the alternative that data already shows to be
 * cleaner.
 */
class PushUpProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "pushup"
    override val displayName: String = "Push-up"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.PRONE

    /**
     * Both arms, one repetition.
     *
     * Measured from MM-Fit's label files: each arm completes exactly one cycle
     * per labelled repetition, on both torso height and the elbow angle, with
     * the two sides in phase. Not one of the 65 sets reads half the label -
     * which is what the alternating case looks like, and what the bicep-curl
     * control does read under the same code. DECISIONS.md P3-5, pinned by
     * `TierAProfileTest`.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /**
     * **Near side only** - DECISIONS.md P3-28, by the owner's decision. It was
     * `BOTH`, on P3-8's premise that both arms are visible side-on; P7 measured
     * otherwise. On MM-Fit's side-on push-ups the far arm's angle was measurable in
     * 42% of frames, well measured (over 90%) in 3 sets of 65, and requiring both
     * arms to complete a repetition counted 5 of 649. Whichever arm faces the
     * camera is counted - the right in 54 of MM-Fit's 65 sets, the left in 10 - and
     * the core never learns which (CLAUDE.md section 4).
     *
     * What it gives up, as the row does: no per-side comparison and no symmetry
     * number for the push-up. A Tier A technique rule written for it in P8 has one
     * arm to work with.
     */
    override val analysedSides: AnalysedSides = AnalysedSides.NEAR_SIDE_ONLY

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.ELBOW,
        plane = MeasurementPlane.IMAGE_2D,
        // The top of a push-up is a nearly locked-out elbow; the bottom is deep
        // flexion with the chest near the floor. Provisional.
        topThreshold = 160.0,
        bottomThreshold = 90.0,
        hysteresis = 5.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 15_000.0,
    )

    /**
     * The torso, not the arms, as for the row (P3-8): `ValidityGate` invalidates a
     * frame on any required landmark below the floor, so requiring the far arm a
     * side-on camera cannot see would mark every frame invalid. P3-28.
     */
    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
    )
}

/**
 * Lateral Raise — Tier A, bilateral simultaneous, **seated**.
 *
 * Driven by shoulder abduction: the hip-shoulder-elbow angle, which is near zero
 * with the arms hanging and near ninety degrees with them held out horizontally.
 * Measured against MM-Fit's labels across 56 sets — DECISIONS.md P3-5, the
 * cleanest of the three signals measured there.
 *
 * **Seated, per DECISIONS.md P0-4.** MM-Fit performs it seated, and a technique
 * rule derived from seated data does not transfer to standing - seated removes
 * the lower body from the picture and changes the trunk reference. The posture
 * is a decision with consequences, not a description, which is why it is pinned
 * by test.
 *
 * **Filmed front-on.** Abduction is a frontal-plane movement: side-on, both arms
 * rise directly toward the camera and the angle barely changes. Front-on also
 * keeps both arms visible at once, which is what makes the asymmetry between
 * them measurable.
 */
class LateralRaiseProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "lateral_raise"
    override val displayName: String = "Lateral Raise"
    override val requiredView: CameraView = CameraView.FRONT_ON
    override val posture: Posture = Posture.SEATED

    /**
     * Both arms, one repetition.
     *
     * Measured from MM-Fit's label files across 56 sets, on hand height and on
     * the abduction angle: one cycle per side per labelled repetition, left and
     * right in phase at +0.99 or better, and no set at half the label.
     * DECISIONS.md P3-5, pinned by `TierAProfileTest`.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.SHOULDER,
        plane = MeasurementPlane.IMAGE_2D,
        // Starting position is arms hanging at the sides, a small angle; the far
        // position is arms out to shoulder height, near ninety. The starting
        // value is therefore the numerically smaller one, which the FSM handles
        // through `topIsGreater` without any exercise knowing about it.
        // Provisional, with no cited source. DECISIONS.md P3-29 raised it from
        // 20 (a start zone at or below 28) to 28 (at or below 36): seated, the
        // upper arm clears the thighs, and on MM-Fit's development workouts 95%
        // of returns between raises came down to 32.8 degrees or less, but about
        // one in five stopped at 28-33, merging two raises into one. 36 is that
        // 95th percentile plus a small margin, not a searched-for best score; the
        // far zone is unchanged, so a raise still sweeps at least 41 degrees.
        topThreshold = 28.0,
        bottomThreshold = 85.0,
        hysteresis = 8.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 12_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_ELBOW, LandmarkId.RIGHT_ELBOW,
    )
}

/**
 * Glute Bridge — Tier A, bilateral simultaneous.
 *
 * Driven by the shoulder-hip-knee angle: lying supine with the feet planted the
 * trunk and thigh meet at an angle well short of straight, and the bridge
 * extends the hip toward a straight line.
 *
 * **The repetition semantics here are an assumption, not a measurement.** MM-Fit
 * has no glute-bridge class, so unlike the push-up and the lateral raise there is
 * no label file to check against. DECISIONS.md P3-5 records the reasoning and
 * marks it as the weaker basis it is.
 *
 * **Filmed side-on.** Hip extension is sagittal; from above or front-on a bridged
 * hip and a flat one look much the same.
 *
 * **The Tier A exercise with the least behind it.** DECISIONS.md P0-3 keeps it in
 * Tier A while noting that MediaPipe is weaker on lying poses, with the
 * contingency that P4 may promote the Sit-up in its place. It now has neither
 * benchmark coverage nor device validation behind any of its declarations, which
 * is worth knowing before its thresholds are treated as settled.
 */
class GluteBridgeProfile : TierAProfile(), RepetitionExercise {

    override val id: String = "glute_bridge"
    override val displayName: String = "Glute Bridge"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.SUPINE

    /**
     * Both hips, one repetition.
     *
     * **Assumed from anatomy, with no dataset behind it.** The movement is a
     * bilateral hip extension with both feet planted; both hips extend through
     * the same movement at the same time, and there is no common alternating
     * variant. The single-leg glute bridge exists but is a different exercise
     * with its own loading, and would be its own profile rather than this one
     * performed differently. DECISIONS.md P3-5.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // Starting position is the hips resting on the floor with the knees bent,
        // which leaves the trunk and thigh meeting well short of straight; the
        // far position is a straight shoulder-hip-knee line. As with the lateral
        // raise, the starting value is the smaller one. Provisional, and less
        // grounded than the others - nothing in any dataset constrains these.
        topThreshold = 140.0,
        bottomThreshold = 172.0,
        hysteresis = 5.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 15_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}
