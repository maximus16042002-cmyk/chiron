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

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

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

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

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

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

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

    /**
     * **Near side only** - DECISIONS.md P3-28, by the owner's decision. It was
     * `BOTH`; on MM-Fit's side-on sit-ups the far side's hip angle was measurable
     * in 53% of frames, well measured in 11 sets of 65, and requiring both sides to
     * complete a repetition counted 101 of 640. Whichever side faces the camera is
     * counted - the left in 51 of the 65 sets, the right in 8 - and the core never
     * learns which. No per-side comparison and no symmetry number, as for the row.
     */
    override val analysedSides: AnalysedSides = AnalysedSides.NEAR_SIDE_ONLY

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

    /**
     * The torso only, as for the row: requiring the far knee, which a side-on
     * camera often cannot see, would mark every frame invalid (P3-8, P3-28).
     */
    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
    )
}

/**
 * Shoulder Press — Tier B, **seated**, bilateral simultaneous.
 *
 * Driven by shoulder abduction: the hip-shoulder-elbow angle opens from about
 * ninety degrees with the upper arms horizontal to nearly one-eighty at lockout.
 *
 * **Seated, per DECISIONS.md P0-4** and CONTRACTS.md section 3, which writes the
 * tier row as "Shoulder Press *(seated)*". MM-Fit performs it seated, and
 * ZERO_DATASET_BUILD_PLAN is explicit that the Kinesis profile is "measured only
 * if your profile is seated too; otherwise deviation only". Seated removes the
 * lower body from the picture and changes the trunk reference, so this is a
 * decision with consequences rather than a description.
 *
 * **Filmed front-on**, per FINAL_ARCHITECTURE section 5's final list.
 *
 * **Why the driving joint is the shoulder and not the elbow.** FINAL_ARCHITECTURE
 * section 4 rates this exercise's view difficulty as "Medium — wrists exit
 * frame": at lockout the hands are the highest thing in the picture and are the
 * first to leave it. The elbow angle needs the wrist; shoulder abduction needs
 * only hip, shoulder and elbow. Measured on MM-Fit, the two signals are
 * indistinguishable in quality - one cycle per side per labelled repetition
 * either way, both at SD 0.05 - so nothing is given up by choosing the one that
 * does not depend on a landmark the documents say leaves the frame.
 * DECISIONS.md P3-8.
 */
class ShoulderPressProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "shoulder_press"
    override val displayName: String = "Shoulder Press"
    override val requiredView: CameraView = CameraView.FRONT_ON
    override val posture: Posture = Posture.SEATED

    /**
     * Both arms, one repetition.
     *
     * Measured from MM-Fit's label files across 60 sets on three signals - hand
     * height, shoulder abduction and elbow angle - each giving one cycle per side
     * per labelled repetition at SD 0.05, with the arms in phase at +0.99 or
     * better and no set at half the label. DECISIONS.md P3-8, pinned by
     * `TierBProfileTest`.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /** Both arms are visible front-on, which is why that view was chosen. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.SHOULDER,
        plane = MeasurementPlane.IMAGE_2D,
        // Starting position is the upper arms roughly horizontal; lockout is
        // overhead, close to in line with the trunk. The start is the smaller
        // value, which the FSM handles through `topIsGreater`. Provisional.
        topThreshold = 100.0,
        bottomThreshold = 160.0,
        hysteresis = 8.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 12_000.0,
    )

    /**
     * Deliberately no wrists - see the note on the driving joint above. These are
     * the six the shoulder angle is built from, and all six are visible front-on
     * throughout the movement.
     */
    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_ELBOW, LandmarkId.RIGHT_ELBOW,
    )
}

/**
 * Bent-over Row — Tier B, **near side only**.
 *
 * Driven by elbow flexion as the hand is pulled up toward the hip.
 *
 * **Only one arm is ever measured.** FINAL_ARCHITECTURE section 5:
 *
 * > **Bent-over Row — the far arm is invisible.** With the torso roughly
 * > horizontal and the camera to one side, the near arm is clean and the far arm
 * > is behind the body for most of the movement. ... **Keep it, but near-side
 * > only, and say so explicitly** — no per-side comparison, no symmetry claim.
 *
 * That restriction is carried by [analysedSides], not by a comment, and the
 * tracker acts on it. The movement itself is two-armed - measured as such on
 * MM-Fit - so the semantics stay `SIMULTANEOUS` and the view restriction is a
 * separate axis. DECISIONS.md P3-8 explains why the two could not be collapsed
 * into one enum.
 *
 * **The document does not name a camera side**, only "the camera to one side",
 * so nothing here fixes left or right. Whichever arm is nearer is the one
 * measured, and the core never learns which that is - CLAUDE.md section 4.
 *
 * **Posture is [Posture.HINGED], not standing.** MM-Fit's row is a *standing*
 * one, and ZERO_DATASET_BUILD_PLAN records the "posture mismatch too large to
 * claim measurement". Declaring both as STANDING would erase the difference that
 * makes the benchmark inapplicable.
 */
class RowProfile : TierBProfile(), RepetitionExercise {

    override val id: String = "row"
    override val displayName: String = "Bent-over Row"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.HINGED

    /**
     * Both arms, one repetition — a fact about the movement, not about what the
     * camera can see.
     *
     * Measured from MM-Fit's label files across 64 sets on three signals - hand
     * height, elbow angle and wrist-to-shoulder distance - each giving one cycle
     * per side per labelled repetition, arms in phase, and **not one of the 64
     * sets at half the label**. DECISIONS.md P3-8, pinned by `TierBProfileTest`.
     *
     * MM-Fit's rows are performed standing and this profile is bent-over, so what
     * transfers is the definition of a repetition, not any measurement of how
     * well Kinesis detects one. P3-8 states that boundary.
     */
    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS

    /**
     * The far arm is behind the torso for the whole movement from the required
     * view. Without this the tracker waits forever for a cycle that never
     * arrives: verified by test before the handling existed, a ten-repetition set
     * produced **no counted repetitions at all** and a single
     * `INSUFFICIENT_DATA` record, losing nine of the ten without a trace. P3-8.
     */
    override val analysedSides: AnalysedSides = AnalysedSides.NEAR_SIDE_ONLY

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.ELBOW,
        plane = MeasurementPlane.IMAGE_2D,
        // Starting position is the arm hanging, elbow nearly straight; the top of
        // the pull brings the hand to the hip with the elbow deeply flexed.
        // Provisional.
        topThreshold = 160.0,
        bottomThreshold = 75.0,
        hysteresis = 8.0,
        minRepDurationMs = 500.0,
        maxRepDurationMs = 12_000.0,
    )

    /**
     * The torso, not the arms — and that is not an oversight.
     *
     * `ValidityGate` returns `INVALID` if **any** required landmark falls below
     * the visibility floor, so requiring both elbows and both wrists on a profile
     * whose far arm is invisible by construction would mark every single frame
     * invalid and block the exercise entirely. The arm requirement is carried by
     * [analysedSides] instead, and what the gate checks is the hinged torso -
     * which is also the thing FINAL_ARCHITECTURE section 5 warns "varies
     * enormously between people". DECISIONS.md P3-8.
     */
    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
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

    /** Both limbs are visible from the required view. DECISIONS.md P3-8. */
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH

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
