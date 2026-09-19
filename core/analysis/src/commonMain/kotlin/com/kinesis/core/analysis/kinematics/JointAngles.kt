package com.kinesis.core.analysis.kinematics

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy

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
     * [VisibilityPolicy.marginal], or when the geometry is degenerate.
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
        if (landmarks.visibility(proximal) < policy.marginal) return Double.NaN
        if (landmarks.visibility(vertex) < policy.marginal) return Double.NaN
        if (landmarks.visibility(distal) < policy.marginal) return Double.NaN

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
