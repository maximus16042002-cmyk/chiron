package com.kinesis.core.analysis.kinematics

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy

/**
 * Speed of a tracked body point between two consecutive frames.
 *
 * ### Units
 *
 * Frame widths per second - the same coordinate space the landmarks arrive in,
 * where both axes are measured in widths of the analysis frame (DECISIONS.md
 * P3-16). **These are not metres per second.** A
 * single camera with no scale reference cannot produce physical velocity, and
 * reporting one would be inventing a measurement the system never made
 * (CLAUDE.md section 22).
 *
 * Divide by torso scale, or work from normalised landmarks, to get something
 * comparable between users and between recording distances.
 *
 * ### Timestamps
 *
 * Taken from the frames, never assumed from a nominal frame rate - real capture
 * jitters, drops frames, and occasionally repeats a timestamp. A non-positive
 * or zero interval yields [Double.NaN] rather than a division by zero or an
 * enormous spike. This discipline is carried over from the old prototype, which
 * had it right.
 */
object LandmarkVelocity {

    /**
     * Speed of [point] on each side, between [previous] and [current].
     *
     * A side is [Double.NaN] when its landmark is below
     * [VisibilityPolicy.marginal] in either frame, or when the interval between
     * the frames is not positive.
     */
    fun compute(
        previous: Landmarks,
        previousTimestampMs: Double,
        current: Landmarks,
        currentTimestampMs: Double,
        point: BodyPoint,
        plane: MeasurementPlane,
        policy: VisibilityPolicy,
    ): BilateralSignal {
        val intervalMs = currentTimestampMs - previousTimestampMs
        if (!(intervalMs > 0.0)) return UNAVAILABLE

        val seconds = intervalMs / 1000.0
        return BilateralSignal(
            left = speedFor(previous, current, point.leftId, plane, policy, seconds),
            right = speedFor(previous, current, point.rightId, plane, policy, seconds),
        )
    }

    private fun speedFor(
        previous: Landmarks,
        current: Landmarks,
        id: LandmarkId,
        plane: MeasurementPlane,
        policy: VisibilityPolicy,
        seconds: Double,
    ): Double {
        if (!policy.isMeasurable(previous.visibility(id))) return Double.NaN
        if (!policy.isMeasurable(current.visibility(id))) return Double.NaN

        val displacement = when (plane) {
            MeasurementPlane.IMAGE_2D -> Geometry.distance2d(
                previous.x(id), previous.y(id),
                current.x(id), current.y(id),
            )

            MeasurementPlane.SPATIAL_3D -> Geometry.distance3d(
                previous.x(id), previous.y(id), previous.z(id),
                current.x(id), current.y(id), current.z(id),
            )
        }
        return displacement / seconds
    }

    /** Both sides unmeasurable. Held as a constant so the common case allocates once. */
    private val UNAVAILABLE = BilateralSignal(Double.NaN, Double.NaN)
}
