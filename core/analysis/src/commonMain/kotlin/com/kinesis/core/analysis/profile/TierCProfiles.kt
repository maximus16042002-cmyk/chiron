package com.kinesis.core.analysis.profile

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.TierCProfile

/*
 * The two Tier C holds that complete the library - CONTRACTS.md section 3 line
 * 104, DECISIONS.md P3-9. Plank arrived earlier as a P3 archetype and stays
 * where it is.
 *
 * Why neither threshold below carries a citation, despite CLAUDE.md section 12a
 * requiring one for published thresholds:
 *
 * Section 12a scopes that requirement to "Layer 2 technique rules, Tier A only",
 * and sourceRef is a field of TechniqueFault, reachable only through
 * TierAProfile.techniqueAuthority. A Tier C profile cannot construct one, so
 * there is no rule for a citation to justify and nowhere for it to live.
 * HoldSignalSpec.targetValue is a DETECTION parameter - it decides whether the
 * subject is in the position at all - on exactly the same footing as every
 * RepSignalSpec threshold in the library. P3-2 recorded that a detection
 * threshold must not leak into the quantities being measured; the same boundary
 * read the other way is that it must not be dressed as a validated one.
 *
 * The literature was searched rather than assumed. It offers no published angle
 * threshold for either exercise: wall-sit work characterises muscle activation
 * and joint loading AT a knee angle rather than publishing a pass/fail criterion,
 * and the standard side-plank protocol is a DURATION test whose failure criteria
 * are qualitative - hips drop, trunk rotates. P3-9 records what was found. What
 * that literature does support is the shape of what a hold reports here:
 * duration, unbroken duration and break count, which is what
 * ZERO_DATASET_BUILD_PLAN already specifies for Tier C - "Hold duration and
 * stability; no repetition claim applies."
 *
 * Both thresholds are therefore provisional, like every other in the library.
 * P6 personalises them from the user's own calibration; P4 sanity-checks them on
 * a device first. Nothing here has been validated against a human being.
 */

/**
 * Wall Sit — Tier C, isometric hold.
 *
 * Driven by knee flexion: the position is a knee held near a right angle with
 * the back against a wall, and the failure is the hips rising as the legs tire.
 *
 * **Filmed side-on**, per FINAL_ARCHITECTURE section 5's final list, row 15.
 * Knee flexion is a sagittal-plane movement, so it is the only view in which the
 * angle is measurable rather than foreshortened - the same reasoning as the
 * squat.
 *
 * **Posture is [Posture.SEATED]**, which is a statement about body configuration
 * rather than about furniture: hips and knees near ninety with the trunk
 * upright, with the wall in place of a chair. It does **not** carry the
 * implication P0-4 attached to the seated lateral raise and shoulder press -
 * that seating removes the lower body from the picture - because for a wall sit
 * the lower body is the entire measurement. DECISIONS.md P3-9.
 *
 * FINAL_ARCHITECTURE section 4 rates it the cheapest kind of entry there is:
 * "Low" view difficulty, "High" landmark reliability, one day of testing.
 */
class WallSitProfile : TierCProfile(), IsometricExercise {

    override val id: String = "wall_sit"
    override val displayName: String = "Wall Sit"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.SEATED

    override val holdSignal: HoldSignalSpec = HoldSignalSpec(
        joint = JointAngleId.KNEE,
        plane = MeasurementPlane.IMAGE_2D,
        // Thighs parallel to the floor puts the knee near a right angle. A
        // detection parameter, not a cited technique threshold - see the note at
        // the top of this file. Provisional.
        targetValue = 90.0,
        toleranceBand = 15.0,
        minHoldDurationMs = 3_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE,
    )
}

/**
 * Side Plank — Tier C, isometric hold.
 *
 * Driven by the shoulder-hip-knee angle, read in the frontal plane: the position
 * is a straight body line held on one forearm, and the failure is the hip
 * dropping toward the floor.
 *
 * **Filmed front-on**, per FINAL_ARCHITECTURE section 5's final list, row 17 -
 * and this is worth reading twice, because "side plank, side view" is the
 * intuitive guess and it is wrong. In a side plank the body's frontal plane
 * faces sideways, so a camera facing the subject sees the whole body line and
 * the drop that breaks it; a camera at the subject's side would be looking
 * along that line and would see nothing.
 *
 * **Not the plank's rule reused.** FINAL_ARCHITECTURE section 3 lists body-line
 * deviation among the things that cannot be shared across exercises: "push-up
 * sag, plank sag, bridge extension and wall-sit posture are different geometries
 * sharing a word". The plank's sag is sagittal and this one is frontal, so the
 * two are separate specifications that happen to use the same joint, and
 * `TierCProfileTest` pins that they do not share a spec instance.
 *
 * **One-sidedness.** A side plank is performed on one side of the body, but that
 * does not make its signal one-sided: the shoulder-hip-knee angle is a property
 * of the body **line**, and both sides' triples describe the same line. This is
 * why [IsometricExercise] has no `analysedSides` counterpart to the one P3-8
 * added for repetition exercises - there is no pairing decision for it to feed,
 * since `HoldAnalyser` consumes a scalar. DECISIONS.md P3-9 has the reasoning
 * and the test that verifies it.
 *
 * Added in FINAL_ARCHITECTURE section 5 as a replacement entry - "second
 * isometric, pairs naturally with plank and wall sit, tests the hold analyser on
 * a different geometry, trivially reliable" - and named there and in CLAUDE.md
 * section 9 as one of the two entries cut first under schedule pressure.
 */
class SidePlankProfile : TierCProfile(), IsometricExercise {

    override val id: String = "side_plank"
    override val displayName: String = "Side Plank"
    override val requiredView: CameraView = CameraView.FRONT_ON
    override val posture: Posture = Posture.SIDE_LYING

    override val holdSignal: HoldSignalSpec = HoldSignalSpec(
        joint = JointAngleId.HIP,
        plane = MeasurementPlane.IMAGE_2D,
        // A straight shoulder-hip-knee line, as with the plank, but measured in
        // the frontal plane against a hip that drops sideways rather than
        // sagging. A detection parameter, not a cited technique threshold.
        // Provisional, and deliberately its own number rather than the plank's.
        targetValue = 180.0,
        toleranceBand = 15.0,
        minHoldDurationMs = 3_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
    )
}
