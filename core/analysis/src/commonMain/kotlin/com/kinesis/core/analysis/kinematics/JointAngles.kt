package com.kinesis.core.analysis.kinematics

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Current-frame joint angles, computed from anatomical landmarks.
 *
 * The first analysis step of CONTRACTS.md section 7's real-time path after
 * normalisation and validity. Reads one frame, touches no history, holds no
 * state - the same frame gives the same answer at any point in a session.
 *
 * ### Bilateral by construction
 *
 * Every function returns a [BilateralSignal]. There is no overload that computes
 * one side alone, because that is what made the old prototype's averaging
 * possible: it exposed `leftKnee()` and `rightKnee()` as independent scalars,
 * and its live detector combined them as `(left + right) / 2f`, falling back to
 * `left ?: right` when one side was occluded - so a two-sided measurement and a
 * one-sided one were indistinguishable downstream. DECISIONS.md P2-2 has the
 * detail.
 *
 * A side whose landmarks are not visible enough comes back as [Double.NaN] *on
 * that side*. The other side keeps its real value, and the absence stays visible
 * instead of being averaged away or silently substituted.
 */
object JointAngles {

    /**
     * The [joint] angle on both sides, in degrees over 0..180.
     *
     * A side is [Double.NaN] when any of its three landmarks falls below
     * [VisibilityPolicy.marginal] or has a `NaN` visibility, or when the
     * geometry is degenerate.
     */
    fun compute(
        landmarks: Landmarks,
        joint: JointAngleId,
        plane: MeasurementPlane,
        policy: VisibilityPolicy,
    ): BilateralSignal = BilateralSignal(
        left = angleFor(
            landmarks,
            joint.proximal.leftId,
            joint.vertex.leftId,
            joint.distal.leftId,
            plane,
            policy,
        ),
        right = angleFor(
            landmarks,
            joint.proximal.rightId,
            joint.vertex.rightId,
            joint.distal.rightId,
            plane,
            policy,
        ),
    )

    /**
     * The value of whichever [DrivingSignal] a profile names, on both sides - DECISIONS.md P3-45.
     * A segment's inclination is always measured in the image plane.
     */
    fun compute(
        landmarks: Landmarks,
        signal: DrivingSignal,
        plane: MeasurementPlane,
        policy: VisibilityPolicy,
    ): BilateralSignal = when (signal) {
        is JointAngleId -> compute(landmarks, signal, plane, policy)
        is SegmentInclination -> BilateralSignal(
            left = inclinationFor(landmarks, signal.lower.leftId, signal.upper.leftId, policy),
            right = inclinationFor(landmarks, signal.lower.rightId, signal.upper.rightId, policy),
        )
    }

    /**
     * The angle above horizontal of the segment from [lower] to [upper], for one side: image y
     * runs down the frame (CONTRACTS.md section 4), so [upper] above [lower] is a positive rise.
     */
    private fun inclinationFor(
        landmarks: Landmarks,
        lower: LandmarkId,
        upper: LandmarkId,
        policy: VisibilityPolicy,
    ): Double {
        if (!policy.isMeasurable(landmarks.visibility(lower))) return Double.NaN
        if (!policy.isMeasurable(landmarks.visibility(upper))) return Double.NaN
        val across = abs(landmarks.x(upper) - landmarks.x(lower))
        val rise = landmarks.y(lower) - landmarks.y(upper)
        if (!across.isFinite() || !rise.isFinite()) return Double.NaN
        if (across < SEGMENT_EPSILON && abs(rise) < SEGMENT_EPSILON) return Double.NaN
        return atan2(rise, across) * 180.0 / PI
    }

    /** Below this, in image widths, a segment has no direction worth measuring. */
    private const val SEGMENT_EPSILON: Double = 1e-6

    /**
     * The angle at [vertex] between [proximal] and [distal], for one side.
     *
     * Private on purpose. Exposing a single-sided angle is exactly the API shape
     * this file exists to avoid.
     */
    private fun angleFor(
        landmarks: Landmarks,
        proximal: LandmarkId,
        vertex: LandmarkId,
        distal: LandmarkId,
        plane: MeasurementPlane,
        policy: VisibilityPolicy,
    ): Double {
        if (!policy.isMeasurable(landmarks.visibility(proximal))) return Double.NaN
        if (!policy.isMeasurable(landmarks.visibility(vertex))) return Double.NaN
        if (!policy.isMeasurable(landmarks.visibility(distal))) return Double.NaN

        return when (plane) {
            MeasurementPlane.IMAGE_2D -> Geometry.angleAtVertex2dDegrees(
                landmarks.x(proximal), landmarks.y(proximal),
                landmarks.x(vertex), landmarks.y(vertex),
                landmarks.x(distal), landmarks.y(distal),
            )

            MeasurementPlane.SPATIAL_3D -> Geometry.angleAtVertex3dDegrees(
                landmarks.x(proximal), landmarks.y(proximal), landmarks.z(proximal),
                landmarks.x(vertex), landmarks.y(vertex), landmarks.z(vertex),
                landmarks.x(distal), landmarks.y(distal), landmarks.z(distal),
            )
        }
    }
}

/** True when both sides of this signal are real measurements. */
fun BilateralSignal.bothSidesMeasured(): Boolean =
    Geometry.isMeasured(left) && Geometry.isMeasured(right)

/** True when neither side of this signal was measurable. */
fun BilateralSignal.neitherSideMeasured(): Boolean =
    !Geometry.isMeasured(left) && !Geometry.isMeasured(right)
