package com.kinesis.core.analysis.profile

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.TierBProfile

/*
 * The Tier B lower-body and trunk profiles - CONTRACTS.md section 3,
 * DECISIONS.md P3-7.
 *
 * Lunge, Standing Hip Abduction, Standing Knee Raise, Sit-up and Lying Leg
 * Raise. Shoulder Press and Row complete the tier and are upper-body. The
 * instances live in ExerciseLibrary, as the Tier A ones do.
 *
 * What Tier B means here: no cited technique rules were written for these
 * exercises, so none can produce a Level 3 finding. TierBProfile has no
 * techniqueAuthority, and TechniqueFault.from demands one, so the call does not
 * compile - CONTRACTS.md section 3's "construction is gated, not filtered". It
 * is a scope boundary, not a statement that these are measured less accurately
 * (DECISIONS.md P0-7); measurement and personal deviation are available to every
 * tier.
 *
 * Two of the five had their repetition semantics measured against MM-Fit's
 * labels and three could not be; the difference is stated on each rather than
 * smoothed over. Every threshold below is provisional on the same footing as the
 * rest of the library - a starting point that lets the profile be exercised, not
 * a measured quantity. P6 personalises them from the user's own calibration; P4
 * sanity-checks them on a device first.
 */

/**
 * Lunge — Tier B, bilateral simultaneous.
 *
 * Driven by knee flexion. Both knees flex on every repetition whichever leg
 * steps forward, so one lunge is one repetition with each knee's range of motion
 * kept separately.
 *
 * **Filmed side-on**, per FINAL_ARCHITECTURE section 5's final list.
 *
 * **This profile carries a known, documented problem.** FINAL_ARCHITECTURE
 * section 4 rates the lunge as the most expensive exercise in the library to
 * validate - four days against one for the sit-up - with view difficulty "High
 * — rear leg occluded", landmark reliability "Low for rear leg", and a viva
 * rating of "Weak". It then places the lunge in the category headed "Fix the
 * view or cut — section 5", and section 5 examines three other exercises
 * instead and never returns to it. DECISIONS.md P3-7 records that gap and the
 * decision to build to the final list rather than invent a restriction.
 *
 * What that implies, stated so P4 can look for it: side-on, if the rear knee
 * tracks as badly as section 4 predicts, its state machine will not complete a
 * cycle, and under simultaneous semantics an unpaired side is reported as
 * `INSUFFICIENT_DATA` rather than counted (P3-2). The failure mode is therefore
 * **under-counting**, not a wrong angle. Nothing here says how often it happens.
 */
class LungeProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "lunge"
    override val displayName: String = "Lunge"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.STANDING

    /**
     * Both legs, one repetition.
     *
     * Measured from MM-Fit's label files across 62 sets on three signals. The
     * knee angle alone could not have settled it - both knees flex on every
     * lunge either way - so each ankle's distance from its hip was measured too,
     * which is large for the stepping leg and small for the trailing one and
     * would run at half the labelled rate if the legs swapped roles between
     * repetitions. It reads the full rate in 37 of 62 sets and half in 3.
     * DECISIONS.md P3-7, pinned by `TierBProfileTest`.
     *
     * Kinesis therefore counts a lunge once per repetition but does **not** model
     * which leg was forward; alternating and same-leg sets are indistinguishable
     * to it. That is a stated limitation, not a hidden one.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.KNEE,
        plane = MeasurementPlane.IMAGE_2D,
        // Standing is a nearly straight knee; the bottom of a lunge is deep
        // flexion on both legs. Provisional.
        topThreshold = 165.0,
        bottomThreshold = 100.0,
        hysteresis = 5.0,
        minRepDurationMs = 700.0,
        maxRepDurationMs = 15_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE,
    )
}

/**
 * Standing Hip Abduction — Tier B, one leg at a time.
 *
 * Driven by the shoulder-hip-knee angle read in the frontal plane: standing
 * upright the trunk and thigh are nearly in line, and lifting the leg out to the
 * side closes that angle.
 *
 * **Filmed front-on**, per FINAL_ARCHITECTURE section 5. Abduction is a
 * frontal-plane movement; side-on the leg swings toward the camera and the angle
 * is destroyed by foreshortening, which is the same reason the lateral raise is
 * filmed front-on.
 */
class HipAbductionProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "hip_abduction"
    override val displayName: String = "Standing Hip Abduction"
    override val requiredView: CameraView = CameraView.FRONT_ON
    override val posture: Posture = Posture.STANDING

    /**
     * One leg, one repetition.
     *
     * **Assumed from anatomy, with no dataset behind it.** MM-Fit has no hip
     * abduction class, so unlike the lunge there is nothing to check this
     * against. The assumption is close to physical necessity rather than a
     * judgement about how the exercise is usually performed: one leg lifts out to
     * the side while the other bears the body's weight, and both cannot go at
     * once from standing. `jumping_jacks` was considered as a proxy and rejected
     * - it abducts both legs off the floor together, which is the opposite case.
     * DECISIONS.md P3-7.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.PER_LIMB

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // Standing is trunk and thigh nearly in line; a comfortable standing
        // abduction reaches perhaps forty degrees out. Provisional.
        topThreshold = 172.0,
        bottomThreshold = 140.0,
        hysteresis = 5.0,
        minRepDurationMs = 500.0,
        maxRepDurationMs = 10_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}

/**
 * Standing Knee Raise — Tier B, one leg at a time.
 *
 * Driven by hip flexion in the sagittal plane: the shoulder-hip-knee angle
 * closes as the thigh comes up toward horizontal.
 *
 * **Filmed side-on**, per FINAL_ARCHITECTURE section 5, which is the only view
 * in which hip flexion is in-plane.
 *
 * Side-on means the far leg is partly occluded, as it is for the lunge. The
 * consequence is gentler here: per-limb semantics means each leg's state machine
 * reports on its own, so a poorly tracked far leg produces fewer records for
 * that leg rather than stalling the other one's count.
 */
class KneeRaiseProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "knee_raise"
    override val displayName: String = "Standing Knee Raise"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.STANDING

    /**
     * One leg, one repetition.
     *
     * **Assumed from anatomy, with no dataset behind it.** MM-Fit has no knee
     * raise class. As with the hip abduction, the assumption is close to physical
     * necessity: one knee lifts while the other leg stands. DECISIONS.md P3-7.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.PER_LIMB

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // Standing is trunk and thigh nearly in line; the top of the raise brings
        // the thigh toward horizontal. Provisional.
        topThreshold = 170.0,
        bottomThreshold = 100.0,
        hysteresis = 6.0,
        minRepDurationMs = 500.0,
        maxRepDurationMs = 10_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}

/**
 * Sit-up — Tier B, bilateral simultaneous.
 *
 * Driven by trunk flexion: the shoulder-hip-knee angle closes as the torso comes
 * up off the floor toward the thighs.
 *
 * **Filmed side-on**, per FINAL_ARCHITECTURE section 5.
 * ZERO_DATASET_BUILD_PLAN added it to the library on exactly this ground - "a
 * side-view exercise whose primary signal is trunk flexion and therefore clearly
 * visible."
 *
 * **Built here rather than with the upper-body batch**, because it drives off the
 * same joint and needs the same six landmarks as the knee raise and the leg
 * raise, which is the boundary that matters for device testing. DECISIONS.md
 * P3-7 records the call.
 *
 * **It is the standing contingency for the glute bridge.** DECISIONS.md P0-3 and
 * FINAL_ARCHITECTURE section 12 both say that if P4 shows the glute bridge
 * segments poorly, the sit-up is promoted to Tier A in its place. Building it now
 * makes that promotion a change of base class rather than a new profile written
 * under pressure.
 */
class SitUpProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "situp"
    override val displayName: String = "Sit-up"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.SUPINE

    /**
     * One trunk, one repetition.
     *
     * Measured from MM-Fit's label files across 65 sets, on the trunk angle and
     * on shoulder height: one cycle per side per labelled repetition, left and
     * right in phase at +0.99, and no set at half the label. Trunk flexion is a
     * midline movement, so this is the least surprising result of the batch and
     * the measurement is here because the alternative was to assume it.
     * DECISIONS.md P3-7, pinned by `TierBProfileTest`.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // Lying back with the knees bent leaves trunk and thigh well short of
        // straight; sitting up closes the angle sharply. Provisional.
        topThreshold = 140.0,
        bottomThreshold = 75.0,
        hysteresis = 8.0,
        minRepDurationMs = 500.0,
        maxRepDurationMs = 12_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}

/**
 * Lying Leg Raise — Tier B, bilateral simultaneous.
 *
 * Driven by hip flexion with the legs straight: lying supine the trunk and legs
 * are nearly in line, and raising the legs toward vertical closes the angle to
 * about ninety degrees.
 *
 * **Filmed side-on at floor level**, per FINAL_ARCHITECTURE section 5, which
 * brought it into the library as a replacement with "hip flexion, side view,
 * large clean ROM ... very reliable segmentation."
 */
class LegRaiseProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "leg_raise"
    override val displayName: String = "Lying Leg Raise"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.SUPINE

    /**
     * Both legs, one repetition — **and this is the weakest declaration in the
     * library.**
     *
     * MM-Fit has no leg raise class, so there is nothing to measure against. The
     * double-leg version is what FINAL_ARCHITECTURE names, and its "large clean
     * ROM" description fits that version. But a single-leg lying leg raise is a
     * common exercise in its own right, and no document says which is meant
     * beyond the name.
     *
     * If the single-leg version was intended, this is a factor-of-two error of
     * exactly the kind DECISIONS.md P3-1 was raised to prevent. It is the first
     * place to look if leg-raise counts ever read halved, and `TierBProfileTest`
     * pins it so the correction comes with a failing test attached. P3-7.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // Legs flat on the floor is trunk and thigh close to in line; legs
        // vertical is about ninety degrees. Provisional.
        topThreshold = 168.0,
        bottomThreshold = 100.0,
        hysteresis = 8.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 12_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}
