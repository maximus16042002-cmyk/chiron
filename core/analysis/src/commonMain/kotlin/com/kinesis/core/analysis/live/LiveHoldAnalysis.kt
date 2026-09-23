package com.kinesis.core.analysis.live

import com.kinesis.core.analysis.hold.HoldAnalyser
import com.kinesis.core.analysis.hold.HoldReading
import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.analysis.landmark.ValidityGate
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.HoldRecord

/**
 * CONTRACTS.md section 7's real-time path for an isometric hold - the
 * counterpart of [LiveRepetitionAnalysis], DECISIONS.md P3-16.
 *
 *     landmarks -> torso check -> validity -> kinematics -> per-side filter
 *               -> HoldReading -> HoldAnalyser -> held time + state
 *
 * Accepts only an [IsometricExercise], so a hold can never reach the repetition
 * tracker - CLAUDE.md section 11, enforced by the type rather than by a check.
 *
 * Built the way [BilateralRepTracker] is: one filter pipeline per side, so an
 * occlusion on one side cannot disturb the other's filter state, and the two
 * are reduced to the scalar [HoldAnalyser] wants only at the end, by the named
 * [HoldReading] rule. Validity is reported and does not gate the analyser, for
 * the reason [LiveRepetitionAnalysis] gives: the kinematics already refuse to
 * measure a side that was not seen, per side.
 *
 * Not thread-safe. The platform must call it from one thread.
 */
class LiveHoldAnalysis(
    val profile: IsometricExercise,
    val policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    temporalConfig: TemporalConfig = TemporalConfig.PROVISIONAL,
) {

    private val spec = profile.holdSignal
    private val analyser = HoldAnalyser(profile)
    private val leftPipeline = RealTimeSignalPipeline(temporalConfig, BilateralRepTracker.DEFAULT_BUFFER_CAPACITY)
    private val rightPipeline = RealTimeSignalPipeline(temporalConfig, BilateralRepTracker.DEFAULT_BUFFER_CAPACITY)

    /** Nothing has been seen before the first frame, so nothing is valid. */
    var frameValidity: FrameValidity = FrameValidity.INVALID
        private set

    val holdState: HoldState get() = analyser.state

    val heldMs: Double get() = analyser.heldMs

    fun onFrame(timestampMs: Double, landmarks: Landmarks) {
        frameValidity = if (!LandmarkNormalizer.hasMeasurableTorso(landmarks)) {
            FrameValidity.INVALID
        } else {
            ValidityGate.evaluate(landmarks, profile.requiredLandmarks, policy)
        }
        val angle = JointAngles.compute(landmarks, spec.joint, spec.plane, policy)
        val filtered = BilateralSignal(
            left = leftPipeline.push(timestampMs, angle.left),
            right = rightPipeline.push(timestampMs, angle.right),
        )
        analyser.step(timestampMs, HoldReading.furthestFromTarget(filtered, spec.targetValue))
    }

    /** A frame in which the pose estimator found nobody: unseen, not broken. */
    fun onNothingSeen(timestampMs: Double) {
        frameValidity = FrameValidity.INVALID
        leftPipeline.push(timestampMs, Double.NaN)
        rightPipeline.push(timestampMs, Double.NaN)
        analyser.step(timestampMs, Double.NaN)
    }

    /** Ends the attempt and reports it. */
    fun finish(): HoldRecord = analyser.finish()
}
