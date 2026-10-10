package com.kinesis.core.analysis.live

import com.kinesis.core.analysis.fps.FrameRateHealth
import com.kinesis.core.analysis.fps.FrameRateMonitor
import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.analysis.landmark.ValidityGate
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.rep.BilateralPhase
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.analysis.rep.RepSink
import com.kinesis.core.analysis.setup.SetupGate
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity

/**
 * CONTRACTS.md section 7's real-time path for a repetition exercise, run one
 * frame at a time - DECISIONS.md P3-15.
 *
 *     landmarks -> normalise -> validity -> kinematics -> buffer -> FSM -> phase + count
 *
 * In the shared core because deciding what a frame's validity is, and which
 * repetition was "last", is analysis - CLAUDE.md section 2. The platform feeds
 * it frames and reads four values back; it computes none of them.
 *
 * ### Validity is reported, not used to gate the tracker
 *
 * Every frame reaches [BilateralRepTracker], including `INVALID` ones. The
 * tracker already refuses to measure a side whose landmarks fall below
 * `policy.marginal` - that side's angle is `NaN` and a repetition in progress on
 * it ends as `INSUFFICIENT_DATA` - and it does so **per side**. Withholding the
 * whole frame instead would let an occluded right wrist abandon a left-arm curl
 * that was being measured perfectly well, which is exactly the coupling
 * DECISIONS.md P3-2 built two independent machines to prevent. For every
 * archetype the required landmarks are the joint chain itself, so an `INVALID`
 * frame and a `NaN` side coincide.
 *
 * ### Normalisation, and what consumes it
 *
 * Nothing on the real-time path consumes normalised coordinates yet: joint
 * angles are invariant to translation and uniform scale and are computed from
 * the anatomical frame directly. What normalisation contributes here is its
 * degenerate-torso check - a frame with no measurable torso has collapsed and is
 * `INVALID` whatever its visibility scores say. That check is asked directly,
 * through `LandmarkNormalizer.hasMeasurableTorso`, rather than by building a
 * normalised frame and discarding it. DECISIONS.md P3-15 and P3-16.
 *
 * Not thread-safe. The platform must call it from one thread.
 */
class LiveRepetitionAnalysis(
    val profile: RepetitionExercise,
    val policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    temporalConfig: TemporalConfig = TemporalConfig.PROVISIONAL,
    /** Whether a wrong camera view stops scoring. The app blocks; see [SetupEnforcement]. */
    val setupEnforcement: SetupEnforcement = SetupEnforcement.REPORT_ONLY,
    /**
     * Every completed repetition record, as it completes - post-rep path, a few
     * times a minute. The platform collects them for the session recorder.
     */
    private val onRepetition: RepSink = RepSink { },
) {

    private val tracker = BilateralRepTracker(profile, temporalConfig)
    private val setupGate = SetupGate(profile, policy, blocks = setupEnforcement == SetupEnforcement.BLOCK_SCORING)
    private val fpsMonitor = FrameRateMonitor()

    /** The setup line for the screen, or `null` when there is nothing to say. Debounced. */
    val setupMessage: String? get() = setupGate.message

    /** True while scoring is stopped by the setup gate. Always false when only reporting. */
    val isScoringBlocked: Boolean
        get() = setupEnforcement == SetupEnforcement.BLOCK_SCORING && setupGate.isScoringBlocked

    val frameRateHealth: FrameRateHealth get() = fpsMonitor.health
    val currentFps: Double get() = fpsMonitor.currentFps
    val fpsWarningMessage: String? get() = fpsMonitor.warningMessage

    /** Nothing has been seen before the first frame, so nothing is valid. */
    var frameValidity: FrameValidity = FrameValidity.INVALID
        private set

    /** Validity of the most recent repetition record, or `null` before the first. */
    var lastRepValidity: RepValidity? = null
        private set

    /**
     * Records reported so far, valid or not. Lets a display tell a new record
     * from the last one repeated - two failures in a row have the same validity.
     * DECISIONS.md P3-23.
     */
    var recordsReported: Int = 0
        private set

    private val sink = RepSink { record ->
        lastRepValidity = record.validity
        recordsReported++
        onRepetition.onRepetition(record)
    }

    val repCount: Int get() = tracker.repetitionCount

    /** Each side's phase, separately - the facade reports both. DECISIONS.md P3-19. */
    val leftPhase: MovementPhase get() = tracker.leftPhase
    val rightPhase: MovementPhase get() = tracker.rightPhase

    /** The last frame's unfiltered angle, per side. Diagnostic only - see the tracker. */
    val rawAngleLeft: Double get() = tracker.rawAngleLeft
    val rawAngleRight: Double get() = tracker.rawAngleRight

    /**
     * The angle the live skeleton shows, per side: the filtered value each side's repetition
     * machine stepped on, never averaged across sides - DECISIONS.md P3-76.
     */
    val angleLeft: Double get() = tracker.angleLeft
    val angleRight: Double get() = tracker.angleRight

    /**
     * One phase for the readout, reduced from the two sides by a named rule.
     * Display only - see [BilateralPhase] for what this loses and why nothing
     * downstream may consume it.
     */
    val displayPhase: MovementPhase
        get() = BilateralPhase.furthestThroughRepetition(tracker.leftPhase, tracker.rightPhase)

    /** One frame of anatomical landmarks, from the platform pose adapter. */
    fun onFrame(timestampMs: Double, landmarks: Landmarks) {
        fpsMonitor.onFrame(timestampMs)
        setupGate.onFrame(landmarks)
        frameValidity = if (!LandmarkNormalizer.hasMeasurableTorso(landmarks)) {
            FrameValidity.INVALID
        } else {
            ValidityGate.evaluate(landmarks, profile.requiredLandmarks, policy)
        }
        // A blocked frame reaches the tracker as one in which nothing was seen, so a repetition
        // in progress ends as an occlusion would end it, and nothing counts (P3-59).
        tracker.onFrame(timestampMs, if (isScoringBlocked) NOTHING_SEEN else landmarks, policy, sink)
    }

    /**
     * A frame in which the pose estimator found nobody.
     *
     * Fed to the tracker as a frame in which nothing was seen, so a repetition in
     * progress when the user steps out of shot ends as `INSUFFICIENT_DATA` -
     * what the tracker already does for an occlusion - rather than being resumed
     * later as though no time had passed.
     */
    fun onNothingSeen(timestampMs: Double) {
        fpsMonitor.onFrame(timestampMs)
        setupGate.onNothingSeen()
        frameValidity = FrameValidity.INVALID
        tracker.onFrame(timestampMs, NOTHING_SEEN, policy, sink)
    }

    /**
     * Ends the set. A repetition still under way is reported `INCOMPLETE` - not
     * counted, not dropped - through the same sink as every other record.
     */
    fun finish(timestampMs: Double) {
        fpsMonitor.reset()
        tracker.finish(timestampMs, sink)
    }

    private companion object {
        /**
         * Every landmark unseen: zero visibility and no coordinates. Built once.
         * Visibility 0 is below any marginal threshold, and `NaN` coordinates keep
         * even a zero threshold from producing an angle.
         */
        val NOTHING_SEEN: Landmarks = Landmarks.fromPacked(
            DoubleArray(Landmarks.PACKED_SIZE) { index ->
                if (index % Landmarks.STRIDE == Landmarks.STRIDE - 1) 0.0 else Double.NaN
            },
        )
    }
}

/**
 * What the live analyses do with the setup gate - DECISIONS.md P3-59, CLAUDE.md section 10.
 *
 * - [BLOCK_SCORING] is the app's: while the gate is closed (a wrong camera view, or nobody in
 *   shot, held for half a second), the tracker and the hold analyser see nothing.
 * - [REPORT_ONLY] computes the same message and changes nothing else. It is the default because
 *   the MM-Fit harness and the JS facade must not block: MM-Fit films its curls front-on, and
 *   blocking there would erase every result P3-27 to P3-46 recorded.
 */
enum class SetupEnforcement {
    REPORT_ONLY,
    BLOCK_SCORING,
}
