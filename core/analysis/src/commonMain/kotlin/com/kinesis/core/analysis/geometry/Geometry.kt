package com.kinesis.core.analysis.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Pure geometric primitives. No anatomy, no exercise semantics, no thresholds.
 *
 * `Double` throughout, per CLAUDE.md section 5 and CONTRACTS.md section 6, and
 * no JVM-only maths - `kotlin.math` only, because these run on the `js()` target
 * too. The old prototype used `Float` and `Math.toDegrees`; both were replaced
 * rather than ported. See DECISIONS.md P2-2.
 *
 * ### How unavailability is reported
 *
 * Degenerate geometry returns [Double.NaN], and the KDoc on each function says
 * when. That is a deliberate choice over a nullable return, for two reasons.
 *
 * A nullable `Double?` boxes on every call, on both targets, and these run
 * thirty times a second for the length of a set - CLAUDE.md section 6.
 *
 * And NaN is the honest sentinel here. The failure this guards against is the
 * one the prototype named: returning 0 or 180 degrees when the angle could not
 * be computed, values that are indistinguishable from real measurements and
 * flow downstream as if measured. NaN cannot be mistaken for a measurement and
 * propagates through arithmetic rather than being quietly absorbed.
 */
object Geometry {

    /**
     * Vectors shorter than this are treated as having no direction.
     *
     * Normalised image coordinates run 0..1 across the frame, so this is far
     * below anything anatomically meaningful while staying well above the
     * rounding noise of a `Double`.
     */
    const val EPSILON: Double = 1e-12

    fun toDegrees(radians: Double): Double = radians * 180.0 / PI

    /**
     * The interior angle at vertex `b`, formed by the rays b→a and b→c, in
     * degrees over 0..180.
     *
     * `angle = acos(clamp((ba · bc) / (|ba| · |bc|), -1, 1))`
     *
     * The clamp absorbs floating-point drift: for nearly collinear points the
     * quotient can land a few ulps outside the domain of `acos`, which would
     * return NaN for geometry that is perfectly well defined.
     *
     * Returns [Double.NaN] when any coordinate is not finite, or when either ray
     * is shorter than [EPSILON] - coincident points, where the angle does not
     * exist rather than being zero.
     */
    fun angleAtVertex3dDegrees(
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
        cx: Double, cy: Double, cz: Double,
    ): Double {
        val baX = ax - bx
        val baY = ay - by
        val baZ = az - bz
        val bcX = cx - bx
        val bcY = cy - by
        val bcZ = cz - bz

        val magBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        if (!magBA.isFinite() || !magBC.isFinite()) return Double.NaN
        if (magBA < EPSILON || magBC < EPSILON) return Double.NaN

        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val cosine = (dot / (magBA * magBC)).coerceIn(-1.0, 1.0)
        return toDegrees(acos(cosine))
    }

    /**
     * [angleAtVertex3dDegrees] in the image plane, ignoring depth.
     *
     * Usually the one to reach for. MediaPipe's `z` is a relative depth estimate
     * rather than a measurement, and folding it into an angle adds its noise to
     * every frame. The old prototype had already reached the same conclusion,
     * and DECISIONS.md P1-4 made the matching choice for normalisation scale.
     */
    fun angleAtVertex2dDegrees(
        ax: Double, ay: Double,
        bx: Double, by: Double,
        cx: Double, cy: Double,
    ): Double {
        val baX = ax - bx
        val baY = ay - by
        val bcX = cx - bx
        val bcY = cy - by

        val magBA = sqrt(baX * baX + baY * baY)
        val magBC = sqrt(bcX * bcX + bcY * bcY)
        if (!magBA.isFinite() || !magBC.isFinite()) return Double.NaN
        if (magBA < EPSILON || magBC < EPSILON) return Double.NaN

        val dot = baX * bcX + baY * bcY
        val cosine = (dot / (magBA * magBC)).coerceIn(-1.0, 1.0)
        return toDegrees(acos(cosine))
    }

    /** Euclidean distance. [Double.NaN] if either point is not finite. */
    fun distance3d(
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
    ): Double {
        val dx = ax - bx
        val dy = ay - by
        val dz = az - bz
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        return if (distance.isFinite()) distance else Double.NaN
    }

    /** Euclidean distance in the image plane. [Double.NaN] if not finite. */
    fun distance2d(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = ax - bx
        val dy = ay - by
        val distance = sqrt(dx * dx + dy * dy)
        return if (distance.isFinite()) distance else Double.NaN
    }

    /**
     * True when [value] is a real measurement rather than an unavailability
     * marker. Reads better at call sites than a bare `!isNaN()`, and names what
     * the check is for.
     */
    fun isMeasured(value: Double): Boolean = !value.isNaN()

    /** True when [a] and [b] agree to within [tolerance]. NaN is never close. */
    fun isClose(a: Double, b: Double, tolerance: Double): Boolean {
        if (a.isNaN() || b.isNaN()) return false
        return abs(a - b) <= tolerance
    }
}
