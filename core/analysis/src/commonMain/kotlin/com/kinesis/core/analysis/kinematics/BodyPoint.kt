package com.kinesis.core.analysis.kinematics

import com.kinesis.core.domain.landmark.LandmarkId

/**
 * A body location that exists on both sides, paired with the anatomical landmark
 * on each side.
 *
 * The pairing is what lets kinematics be bilateral by construction rather than
 * by discipline: a caller names a body point, and gets a
 * [com.kinesis.core.domain.bilateral.BilateralSignal] back. There is no call
 * shape that returns one side on its own, so there is no call shape that quietly
 * loses the other.
 *
 * The properties are `leftId` and `rightId`, not `left` and `right`, so that a
 * landmark identifier never reads like the left value of a bilateral
 * measurement.
 */
enum class BodyPoint(val leftId: LandmarkId, val rightId: LandmarkId) {
    SHOULDER(LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER),
    ELBOW(LandmarkId.LEFT_ELBOW, LandmarkId.RIGHT_ELBOW),
    WRIST(LandmarkId.LEFT_WRIST, LandmarkId.RIGHT_WRIST),
    HIP(LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP),
    KNEE(LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE),
    ANKLE(LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE),
    HEEL(LandmarkId.LEFT_HEEL, LandmarkId.RIGHT_HEEL),
    FOOT_INDEX(LandmarkId.LEFT_FOOT_INDEX, LandmarkId.RIGHT_FOOT_INDEX),
    ;

    /** The landmark on [side]. */
    fun idOn(side: com.kinesis.core.domain.bilateral.Side): LandmarkId =
        when (side) {
            com.kinesis.core.domain.bilateral.Side.LEFT -> leftId
            com.kinesis.core.domain.bilateral.Side.RIGHT -> rightId
        }
}

/**
 * A joint angle, defined by the three body points that form it.
 *
 * The angle is the interior angle at [vertex], between the ray toward [proximal]
 * and the ray toward [distal]. A fully extended limb approaches 180 degrees; a
 * fully flexed one approaches 0.
 *
 * These are anatomical definitions and carry no exercise semantics. What counts
 * as enough knee flexion for a squat is an exercise profile's business, in P3,
 * and deliberately not encoded here.
 */
enum class JointAngleId(
    val proximal: BodyPoint,
    val vertex: BodyPoint,
    val distal: BodyPoint,
) {
    /** Shoulder → elbow ← wrist. */
    ELBOW(BodyPoint.SHOULDER, BodyPoint.ELBOW, BodyPoint.WRIST),

    /** Hip → shoulder ← elbow. Abduction, as used by raises and presses. */
    SHOULDER(BodyPoint.HIP, BodyPoint.SHOULDER, BodyPoint.ELBOW),

    /** Shoulder → hip ← knee. Trunk-to-thigh. */
    HIP(BodyPoint.SHOULDER, BodyPoint.HIP, BodyPoint.KNEE),

    /** Hip → knee ← ankle. */
    KNEE(BodyPoint.HIP, BodyPoint.KNEE, BodyPoint.ANKLE),
}

/**
 * Which plane a measurement is taken in.
 *
 * Always chosen explicitly at the call site. The prototype defaulted to 3D and
 * offered 2D as an alternative; here neither is a default, because the right
 * answer depends on the exercise and the camera view, and a silent default is
 * how the wrong one gets used for a year.
 */
enum class MeasurementPlane {

    /**
     * The image plane, ignoring MediaPipe's `z`.
     *
     * The usual choice. `z` is a relative depth estimate rather than a
     * measurement, so including it adds noise to every frame without adding
     * information - the same reasoning as DECISIONS.md P1-4.
     */
    IMAGE_2D,

    /**
     * Full 3D, including `z`.
     *
     * For movements whose signal is genuinely out of the image plane and which
     * cannot be filmed from a view that puts it in-plane. Carries `z`'s noise
     * with it; that is the trade being made.
     */
    SPATIAL_3D,
}
