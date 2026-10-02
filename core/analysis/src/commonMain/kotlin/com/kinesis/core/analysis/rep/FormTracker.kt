package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.analysis.kinematics.BodyPoint
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.temporal.DoubleRingBuffer
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.FormMeasure
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * The form summary's two halves - DECISIONS.md P3-44.
 *
 * **Per frame** ([push], real-time path): for each side, the angles the exercise's
 * [RepetitionExercise.formMeasures] need, from that frame's landmarks, pushed into fixed-size
 * ring buffers beside the raw driving angle. O(1), and no allocation: plain arithmetic on
 * doubles into preallocated buffers. An exercise that asks for nothing does nothing here.
 *
 * **Per repetition** ([summarise], post-rep path): each measure reduced over the samples inside
 * one side's repetition window - the largest value, or the value at the repetition's deepest
 * frame. The two sides are never combined (CLAUDE.md section 3).
 *
 * A side whose landmarks for a measure fall below the visibility policy's marginal level is
 * `NaN` for that frame, and a measure with no measured frame in the window is `NaN`.
 */
internal class FormTracker(private val profile: RepetitionExercise, capacity: Int) {

    private val measures: List<FormMeasure> = profile.formMeasures
    private val active = measures.isNotEmpty()

    private val needsUpperArm = FormMeasure.UPPER_ARM_TRUNK_MAX in measures
    private val needsBodyLine = FormMeasure.BODY_LINE_SAG_MAX in measures || FormMeasure.BODY_LINE_HIKE_MAX in measures
    private val needsTrunkShin = FormMeasure.TRUNK_MINUS_SHIN_LEAN in measures

    private val leftDriving = DoubleRingBuffer(if (active) capacity else 1)
    private val rightDriving = DoubleRingBuffer(if (active) capacity else 1)
    private val leftUpperArm = DoubleRingBuffer(if (needsUpperArm) capacity else 1)
    private val rightUpperArm = DoubleRingBuffer(if (needsUpperArm) capacity else 1)
    private val leftBodyLine = DoubleRingBuffer(if (needsBodyLine) capacity else 1)
    private val rightBodyLine = DoubleRingBuffer(if (needsBodyLine) capacity else 1)
    private val leftTrunkShin = DoubleRingBuffer(if (needsTrunkShin) capacity else 1)
    private val rightTrunkShin = DoubleRingBuffer(if (needsTrunkShin) capacity else 1)

    /** One frame. [drivingLeft] and [drivingRight] are the raw driving angles the tracker measured. */
    fun push(timestampMs: Double, landmarks: Landmarks, policy: VisibilityPolicy, drivingLeft: Double, drivingRight: Double) {
        if (!active) return
        leftDriving.push(timestampMs, drivingLeft)
        rightDriving.push(timestampMs, drivingRight)
        if (needsUpperArm) {
            leftUpperArm.push(timestampMs, upperArmToTrunk(landmarks, Side.LEFT, policy))
            rightUpperArm.push(timestampMs, upperArmToTrunk(landmarks, Side.RIGHT, policy))
        }
        if (needsBodyLine) {
            leftBodyLine.push(timestampMs, bodyLineBend(landmarks, Side.LEFT, policy))
            rightBodyLine.push(timestampMs, bodyLineBend(landmarks, Side.RIGHT, policy))
        }
        if (needsTrunkShin) {
            leftTrunkShin.push(timestampMs, trunkMinusShinLean(landmarks, Side.LEFT, policy))
            rightTrunkShin.push(timestampMs, trunkMinusShinLean(landmarks, Side.RIGHT, policy))
        }
    }

    /** This side's measures over its repetition window, [startMs]..[endMs]. Post-rep: allocates. */
    fun summarise(side: Side, startMs: Double, endMs: Double): Map<FormMeasure, Double> {
        if (!active) return emptyMap()
        val left = side == Side.LEFT
        val result = LinkedHashMap<FormMeasure, Double>()
        for (measure in measures) {
            result[measure] = when (measure) {
                FormMeasure.UPPER_ARM_TRUNK_MAX -> largest(if (left) leftUpperArm else rightUpperArm, startMs, endMs, 1.0)
                FormMeasure.BODY_LINE_SAG_MAX -> largestOrZero(if (left) leftBodyLine else rightBodyLine, startMs, endMs, 1.0)
                FormMeasure.BODY_LINE_HIKE_MAX -> largestOrZero(if (left) leftBodyLine else rightBodyLine, startMs, endMs, -1.0)
                FormMeasure.TRUNK_MINUS_SHIN_LEAN ->
                    atDeepest(if (left) leftDriving else rightDriving, if (left) leftTrunkShin else rightTrunkShin, startMs, endMs)
            }
        }
        return result
    }

    fun reset() {
        for (buffer in listOf(leftDriving, rightDriving, leftUpperArm, rightUpperArm, leftBodyLine, rightBodyLine, leftTrunkShin, rightTrunkShin)) {
            buffer.clear()
        }
    }

    // --- Post-rep reductions ------------------------------------------------------------------

    /** The largest of [sign] times the value inside the window; `NaN` if none was measured. */
    private fun largest(buffer: DoubleRingBuffer, startMs: Double, endMs: Double, sign: Double): Double {
        var best = Double.NaN
        for (i in 0 until buffer.size) {
            val t = buffer.timestampAt(i)
            if (t < startMs || t > endMs) continue
            val v = sign * buffer.valueAt(i)
            if (!v.isNaN() && (best.isNaN() || v > best)) best = v
        }
        return best
    }

    /** As [largest], but 0 when every measured value was on the other side of the line. */
    private fun largestOrZero(buffer: DoubleRingBuffer, startMs: Double, endMs: Double, sign: Double): Double {
        val best = largest(buffer, startMs, endMs, sign)
        return if (best.isNaN()) best else if (best > 0.0) best else 0.0
    }

    /**
     * The value at the frame where the raw driving angle is furthest towards the far zone,
     * among frames inside the window where both are measured.
     */
    private fun atDeepest(driving: DoubleRingBuffer, values: DoubleRingBuffer, startMs: Double, endMs: Double): Double {
        val towardsFar = if (profile.repSignal.topIsGreater) -1.0 else 1.0
        var deepest = Double.NaN
        var value = Double.NaN
        for (i in 0 until driving.size) {
            val t = driving.timestampAt(i)
            if (t < startMs || t > endMs) continue
            val d = driving.valueAt(i) * towardsFar
            val v = values.valueAt(i)
            if (d.isNaN() || v.isNaN()) continue
            if (deepest.isNaN() || d > deepest) {
                deepest = d
                value = v
            }
        }
        return value
    }

    // --- Per-frame measures: doubles only --------------------------------------------------------

    private fun seen(landmarks: Landmarks, policy: VisibilityPolicy, point: BodyPoint, side: Side): Boolean =
        policy.isMeasurable(landmarks.visibility(point.idOn(side)))

    /** The angle at the shoulder between hip and elbow, in the image. */
    private fun upperArmToTrunk(landmarks: Landmarks, side: Side, policy: VisibilityPolicy): Double {
        if (!seen(landmarks, policy, BodyPoint.HIP, side) || !seen(landmarks, policy, BodyPoint.SHOULDER, side) ||
            !seen(landmarks, policy, BodyPoint.ELBOW, side)
        ) {
            return Double.NaN
        }
        val hip = BodyPoint.HIP.idOn(side)
        val shoulder = BodyPoint.SHOULDER.idOn(side)
        val elbow = BodyPoint.ELBOW.idOn(side)
        return Geometry.angleAtVertex2dDegrees(
            landmarks.x(hip), landmarks.y(hip),
            landmarks.x(shoulder), landmarks.y(shoulder),
            landmarks.x(elbow), landmarks.y(elbow),
        )
    }

    /**
     * The hip's bend off the line from shoulder to ankle: 180 degrees minus the angle at the hip,
     * positive when the hip is lower in the image than the line (image y runs down, CONTRACTS.md
     * section 4), negative when higher. `NaN` when shoulder and ankle stand one above the other,
     * where "below the line" has no meaning.
     */
    private fun bodyLineBend(landmarks: Landmarks, side: Side, policy: VisibilityPolicy): Double {
        if (!seen(landmarks, policy, BodyPoint.SHOULDER, side) || !seen(landmarks, policy, BodyPoint.HIP, side) ||
            !seen(landmarks, policy, BodyPoint.ANKLE, side)
        ) {
            return Double.NaN
        }
        val shoulder = BodyPoint.SHOULDER.idOn(side)
        val hip = BodyPoint.HIP.idOn(side)
        val ankle = BodyPoint.ANKLE.idOn(side)
        val sx = landmarks.x(shoulder)
        val sy = landmarks.y(shoulder)
        val hx = landmarks.x(hip)
        val hy = landmarks.y(hip)
        val ax = landmarks.x(ankle)
        val ay = landmarks.y(ankle)
        val run = ax - sx
        if (!(abs(run) > MIN_RUN)) return Double.NaN
        val bend = 180.0 - Geometry.angleAtVertex2dDegrees(sx, sy, hx, hy, ax, ay)
        val lineY = sy + (ay - sy) * (hx - sx) / run
        return if (hy > lineY) bend else -bend
    }

    /** The trunk's lean from vertical minus the shin's, each unsigned, in the image. */
    private fun trunkMinusShinLean(landmarks: Landmarks, side: Side, policy: VisibilityPolicy): Double {
        if (!seen(landmarks, policy, BodyPoint.SHOULDER, side) || !seen(landmarks, policy, BodyPoint.HIP, side) ||
            !seen(landmarks, policy, BodyPoint.KNEE, side) || !seen(landmarks, policy, BodyPoint.ANKLE, side)
        ) {
            return Double.NaN
        }
        val shoulder = BodyPoint.SHOULDER.idOn(side)
        val hip = BodyPoint.HIP.idOn(side)
        val knee = BodyPoint.KNEE.idOn(side)
        val ankle = BodyPoint.ANKLE.idOn(side)
        val trunk = leanFromVertical(landmarks.x(shoulder) - landmarks.x(hip), landmarks.y(hip) - landmarks.y(shoulder))
        val shin = leanFromVertical(landmarks.x(knee) - landmarks.x(ankle), landmarks.y(ankle) - landmarks.y(knee))
        return trunk - shin
    }

    /** A segment's lean from vertical, unsigned, given how far it runs across and up the image. */
    private fun leanFromVertical(across: Double, up: Double): Double {
        if (!across.isFinite() || !up.isFinite() || (abs(across) < MIN_RUN && abs(up) < MIN_RUN)) return Double.NaN
        return atan2(abs(across), up) * 180.0 / PI
    }

    private companion object {
        /** Below this, in image widths, a segment has no direction worth measuring. */
        const val MIN_RUN: Double = 1e-6
    }
}
