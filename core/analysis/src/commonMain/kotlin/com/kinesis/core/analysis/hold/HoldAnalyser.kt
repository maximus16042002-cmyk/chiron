package com.kinesis.core.analysis.hold

import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.math.abs
import kotlin.math.sqrt

/** Where an isometric attempt currently stands. */
enum class HoldState {

    /** The position has not been reached yet. */
    NOT_STARTED,

    /** Currently within tolerance of the target. */
    HOLDING,

    /** The position was reached and has been lost. */
    DEVIATING,

    /** The attempt is over. */
    ENDED,
}

/**
 * Measures how long a position is held, and how steadily.
 *
 * Accepts only an [IsometricExercise]. A repetition exercise cannot be passed
 * here - the call does not compile - which together with
 * `BilateralRepTracker`'s matching restriction is how CLAUDE.md section 11 is
 * enforced structurally rather than by convention.
 *
 * ### Why this is not the repetition FSM with different thresholds
 *
 * It would be easy to reuse the state machine and call each sag-and-recover a
 * repetition. That is precisely the failure section 11 forbids. A plank pushed
 * through rep counting reports a number, the number looks like a measurement,
 * and the user is told they performed fourteen repetitions of holding still.
 * What a hold has is a duration, a count of breaks, and a steadiness; it has no
 * repetitions, and [HoldRecord] has nowhere to put one.
 *
 * ### Per frame
 *
 * [step] is O(1) and holds fixed state: a handful of accumulators and
 * timestamps, the held-value mean and sway (P3-22) among them.
 * No history, nothing that grows with the length of the hold.
 */
class HoldAnalyser(val profile: IsometricExercise) {

    private val spec = profile.holdSignal

    var state: HoldState = HoldState.NOT_STARTED
        private set

    private var firstTimestampMs: Double = Double.NaN
    private var lastTimestampMs: Double = Double.NaN
    private var currentStretchStartMs: Double = Double.NaN
    private var lastHeldTimestampMs: Double = Double.NaN

    private var totalHoldMs: Double = 0.0

    /** Time held so far, for a live readout. The same figure [finish] reports. */
    val heldMs: Double get() = totalHoldMs

    private var longestUnbrokenMs: Double = 0.0
    private var breakCount: Int = 0

    private var deviationSum: Double = 0.0
    private var heldFrames: Int = 0

    // Where the body line was while held, and how much it swayed - DECISIONS.md
    // P3-22. Welford's running mean and sum of squared deviations: constant space,
    // one update per held frame, and none of the cancellation a plain sum of
    // squares suffers when the angle sits near 180 and moves by fractions of a
    // degree.
    private var heldMean: Double = 0.0
    private var heldSquaredDeviations: Double = 0.0
    private var maxAbsDeviation: Double = 0.0

    private var totalFrames: Int = 0
    private var measuredFrames: Int = 0

    /**
     * Feeds one frame to the analyser.
     *
     * @param value the driving signal, or [Double.NaN] if it could not be
     *   measured. An unmeasurable frame does not extend the hold and does not
     *   count as a break: the subject may well still be holding, and the honest
     *   statement is that it was not seen. It lowers [HoldRecord.coverage], which
     *   is where that shows up.
     */
    fun step(timestampMs: Double, value: Double) {
        if (state == HoldState.ENDED) return

        if (firstTimestampMs.isNaN()) firstTimestampMs = timestampMs
        lastTimestampMs = timestampMs
        totalFrames++

        if (value.isNaN()) {
            closeCurrentStretch()
            return
        }
        measuredFrames++

        val deviation = abs(value - spec.targetValue)
        if (deviation <= spec.toleranceBand) {
            if (state != HoldState.HOLDING) {
                state = HoldState.HOLDING
                currentStretchStartMs = timestampMs
            } else if (!lastHeldTimestampMs.isNaN()) {
                totalHoldMs += timestampMs - lastHeldTimestampMs
            }
            lastHeldTimestampMs = timestampMs

            deviationSum += deviation
            heldFrames++
            val fromMean = value - heldMean
            heldMean += fromMean / heldFrames
            heldSquaredDeviations += fromMean * (value - heldMean)
            if (deviation > maxAbsDeviation) maxAbsDeviation = deviation
        } else {
            // Only a position that was actually reached can be broken.
            if (state == HoldState.HOLDING) {
                breakCount++
                closeCurrentStretch()
                state = HoldState.DEVIATING
            }
        }
    }

    /** Ends the attempt and reports it. */
    fun finish(): HoldRecord {
        closeCurrentStretch()
        state = HoldState.ENDED

        val coverage = if (totalFrames == 0) 0.0 else measuredFrames.toDouble() / totalFrames
        val validity = when {
            totalFrames == 0 -> HoldValidity.INSUFFICIENT_DATA
            coverage < MIN_COVERAGE -> HoldValidity.INSUFFICIENT_DATA
            totalHoldMs < spec.minHoldDurationMs -> HoldValidity.TOO_SHORT
            breakCount > 0 -> HoldValidity.VALID
            else -> HoldValidity.VALID
        }

        return HoldRecord(
            startMs = if (firstTimestampMs.isNaN()) 0.0 else firstTimestampMs,
            endMs = if (lastTimestampMs.isNaN()) 0.0 else lastTimestampMs,
            validity = validity,
            totalHoldMs = totalHoldMs,
            longestUnbrokenMs = longestUnbrokenMs,
            breakCount = breakCount,
            meanAbsDeviation = if (heldFrames == 0) Double.NaN else deviationSum / heldFrames,
            maxAbsDeviation = if (heldFrames == 0) Double.NaN else maxAbsDeviation,
            coverage = coverage,
            meanHeldValue = if (heldFrames == 0) Double.NaN else heldMean,
            // Sample form, as SignalMetrics: with one held frame the spread is
            // unknown, not zero.
            heldValueSd = if (heldFrames < 2) Double.NaN else sqrt(heldSquaredDeviations / (heldFrames - 1)),
        )
    }

    fun reset() {
        state = HoldState.NOT_STARTED
        firstTimestampMs = Double.NaN
        lastTimestampMs = Double.NaN
        currentStretchStartMs = Double.NaN
        lastHeldTimestampMs = Double.NaN
        totalHoldMs = 0.0
        longestUnbrokenMs = 0.0
        breakCount = 0
        deviationSum = 0.0
        heldFrames = 0
        heldMean = 0.0
        heldSquaredDeviations = 0.0
        maxAbsDeviation = 0.0
        totalFrames = 0
        measuredFrames = 0
    }

    private fun closeCurrentStretch() {
        if (state == HoldState.HOLDING && !currentStretchStartMs.isNaN() && !lastHeldTimestampMs.isNaN()) {
            val stretch = lastHeldTimestampMs - currentStretchStartMs
            if (stretch > longestUnbrokenMs) longestUnbrokenMs = stretch
        }
        currentStretchStartMs = Double.NaN
        lastHeldTimestampMs = Double.NaN
    }

    private companion object {
        /**
         * Below this fraction of measurable frames, a duration would be more
         * guess than measurement, so the attempt is reported as insufficient
         * rather than given a number. Provisional, like every other threshold at
         * this stage; P4 sets it from device testing.
         */
        const val MIN_COVERAGE = 0.5
    }
}
