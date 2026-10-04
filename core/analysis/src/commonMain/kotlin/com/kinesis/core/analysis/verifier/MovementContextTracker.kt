package com.kinesis.core.analysis.verifier

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.analysis.temporal.DoubleRingBuffer
import com.kinesis.core.analysis.temporal.SignalResampler
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Real-time tracker for the 9-signal multi-joint kinematic context - DECISIONS.md P3-34, P3-52.
 *
 * **Per frame** ([push], real-time path): computes 9 joint angles and inclination in the image plane
 * directly from landmarks using primitive arithmetic, pushing them into fixed-size ring buffers.
 * Strictly O(1) with zero object allocation per frame.
 *
 * **Per repetition** ([extract], post-rep path): resamples each of the 9 signals to 16 points over
 * [startMs]..[endMs], imputes missing/unmeasured points with neutral posture defaults, and appends
 * the repetition duration to produce a 145-dimensional [MovementContext].
 */
class MovementContextTracker(capacity: Int) {

    private val leftElbow = DoubleRingBuffer(capacity)
    private val rightElbow = DoubleRingBuffer(capacity)
    private val leftShoulder = DoubleRingBuffer(capacity)
    private val rightShoulder = DoubleRingBuffer(capacity)
    private val leftHip = DoubleRingBuffer(capacity)
    private val rightHip = DoubleRingBuffer(capacity)
    private val leftKnee = DoubleRingBuffer(capacity)
    private val rightKnee = DoubleRingBuffer(capacity)
    private val trunkLean = DoubleRingBuffer(capacity)

    private val allBuffers = listOf(
        leftElbow,
        rightElbow,
        leftShoulder,
        rightShoulder,
        leftHip,
        rightHip,
        leftKnee,
        rightKnee,
        trunkLean,
    )

    fun push(timestampMs: Double, landmarks: Landmarks, policy: VisibilityPolicy) {
        leftElbow.push(timestampMs, angle3(landmarks, policy, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST))
        rightElbow.push(timestampMs, angle3(landmarks, policy, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST))
        leftShoulder.push(timestampMs, angle3(landmarks, policy, LandmarkId.LEFT_HIP, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW))
        rightShoulder.push(timestampMs, angle3(landmarks, policy, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW))
        leftHip.push(timestampMs, angle3(landmarks, policy, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE))
        rightHip.push(timestampMs, angle3(landmarks, policy, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE))
        leftKnee.push(timestampMs, angle3(landmarks, policy, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE))
        rightKnee.push(timestampMs, angle3(landmarks, policy, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE))
        trunkLean.push(timestampMs, trunkLeanAngle(landmarks, policy))
    }

    fun extract(startMs: Double, endMs: Double, maxGapMs: Double = 500.0): MovementContext? {
        if (endMs <= startMs) return null
        val durationMs = endMs - startMs
        val features = DoubleArray(MovementContext.FEATURE_COUNT)

        for (s in 0 until MovementContext.SIGNAL_COUNT) {
            val buffer = allBuffers[s]
            val defaultAngle = DEFAULT_ANGLES[s]
            val resampled = resampleWindow(buffer, startMs, endMs, MovementContext.POINTS_PER_SIGNAL, maxGapMs, defaultAngle)
            val offset = s * MovementContext.POINTS_PER_SIGNAL
            for (p in 0 until MovementContext.POINTS_PER_SIGNAL) {
                features[offset + p] = resampled[p]
            }
        }
        features[MovementContext.FEATURE_COUNT - 1] = durationMs
        return MovementContext(features)
    }

    fun reset() {
        for (b in allBuffers) b.clear()
    }

    private fun resampleWindow(
        buffer: DoubleRingBuffer,
        startMs: Double,
        endMs: Double,
        points: Int,
        maxGapMs: Double,
        defaultAngle: Double,
    ): DoubleArray {
        var count = 0
        for (i in 0 until buffer.size) {
            val t = buffer.timestampAt(i)
            if (t in startMs..endMs) count++
        }
        if (count < 2) {
            return DoubleArray(points) { defaultAngle }
        }

        val times = DoubleArray(count)
        val values = DoubleArray(count)
        var idx = 0
        for (i in 0 until buffer.size) {
            val t = buffer.timestampAt(i)
            if (t in startMs..endMs) {
                times[idx] = t
                values[idx] = buffer.valueAt(i)
                idx++
            }
        }

        val resampled = try {
            SignalResampler.resampleToFixedLength(times, values, points, maxGapMs)
        } catch (_: Exception) {
            DoubleArray(points) { defaultAngle }
        }

        for (i in 0 until points) {
            if (resampled[i].isNaN()) {
                resampled[i] = defaultAngle
            }
        }
        return resampled
    }

    private fun angle3(
        landmarks: Landmarks,
        policy: VisibilityPolicy,
        proximal: LandmarkId,
        vertex: LandmarkId,
        distal: LandmarkId,
    ): Double {
        if (!policy.isMeasurable(landmarks.visibility(proximal)) ||
            !policy.isMeasurable(landmarks.visibility(vertex)) ||
            !policy.isMeasurable(landmarks.visibility(distal))
        ) {
            return Double.NaN
        }
        return Geometry.angleAtVertex2dDegrees(
            landmarks.x(proximal), landmarks.y(proximal),
            landmarks.x(vertex), landmarks.y(vertex),
            landmarks.x(distal), landmarks.y(distal),
        )
    }

    private fun trunkLeanAngle(landmarks: Landmarks, policy: VisibilityPolicy): Double {
        val leftMeasurable = policy.isMeasurable(landmarks.visibility(LandmarkId.LEFT_SHOULDER)) &&
            policy.isMeasurable(landmarks.visibility(LandmarkId.LEFT_HIP))
        val rightMeasurable = policy.isMeasurable(landmarks.visibility(LandmarkId.RIGHT_SHOULDER)) &&
            policy.isMeasurable(landmarks.visibility(LandmarkId.RIGHT_HIP))

        val (shoulder, hip) = when {
            leftMeasurable -> LandmarkId.LEFT_SHOULDER to LandmarkId.LEFT_HIP
            rightMeasurable -> LandmarkId.RIGHT_SHOULDER to LandmarkId.RIGHT_HIP
            else -> return Double.NaN
        }
        val across = landmarks.x(shoulder) - landmarks.x(hip)
        val up = landmarks.y(hip) - landmarks.y(shoulder)
        if (!across.isFinite() || !up.isFinite() || (abs(across) < MIN_RUN && abs(up) < MIN_RUN)) return Double.NaN
        return atan2(abs(across), up) * 180.0 / PI
    }

    companion object {
        private const val MIN_RUN = 1e-6

        /** Anatomical neutral angle defaults (in degrees) used when landmarks are occluded/unmeasurable. */
        val DEFAULT_ANGLES = doubleArrayOf(
            160.0, // left_elbow
            160.0, // right_elbow
            20.0,  // left_shoulder
            20.0,  // right_shoulder
            170.0, // left_hip
            170.0, // right_hip
            175.0, // left_knee
            175.0, // right_knee
            10.0,  // trunk_lean
        )
    }
}
