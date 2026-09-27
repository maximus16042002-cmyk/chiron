package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.temporal.DoubleRingBuffer
import com.kinesis.core.analysis.temporal.SignalResampler
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity

/**
 * One completed cycle of a single signal, before it is attributed to a side.
 *
 * Side-agnostic on purpose: an [RepFsm] tracks one scalar and does not know
 * whether it is watching a left knee or a right elbow. `BilateralRepTracker`
 * pairs two of them and decides what a repetition means.
 */
class RepCycle(
    val startMs: Double,
    val endMs: Double,
    val validity: RepValidity,

    /** Signal value at the starting position. */
    val peakValue: Double,

    /** Signal value at the far position. */
    val troughValue: Double,

    val descentMs: Double,
    val ascentMs: Double,

    /** The cycle resampled to a fixed length - CLAUDE.md section 15. */
    val resampled: DoubleArray,
) {
    val durationMs: Double get() = endMs - startMs

    /** Peak-to-peak excursion. Always non-negative, whichever way the signal ran. */
    val rangeOfMotion: Double
        get() = if (peakValue.isNaN() || troughValue.isNaN()) {
            Double.NaN
        } else if (peakValue >= troughValue) {
            peakValue - troughValue
        } else {
            troughValue - peakValue
        }
}

/**
 * Counts repetitions in one scalar signal, one frame at a time.
 *
 * ### Incremental, which is the whole point
 *
 * [step] consumes a single frame and returns either nothing or the repetition
 * that frame completed. The machine holds its own state between calls: a phase,
 * two extremes, three timestamps and a bounded buffer. It never looks at the
 * session.
 *
 * The old prototype's `RepEngine` took the whole signal and replayed it from
 * IDLE, and its live adapter called that once per frame over a list that grew
 * for the length of the set. Its state machine was sound and is the basis for
 * this one; the way it was driven is what DECISIONS.md P2-2 records as not
 * ported. The P2 per-frame budget guards enforce the difference.
 *
 * ### Direction-agnostic
 *
 * Nothing assumes the signal falls during the working phase. A squat's knee
 * angle decreases from standing; a bicep curl's elbow angle also decreases; a
 * lateral raise's shoulder angle increases. [RepSignalSpec.topIsGreater] settles
 * it once, and the comparisons below read the same either way.
 *
 * ### Hysteresis
 *
 * A threshold is a band, not a line. A signal resting exactly on one jitters
 * across it every frame, and without the band the phase would oscillate and
 * manufacture repetitions out of estimator noise.
 */
class RepFsm(
    val spec: RepSignalSpec,
    bufferCapacity: Int,
    private val resampleLength: Int = DEFAULT_RESAMPLE_LENGTH,
    /**
     * How long a repetition in progress may go unmeasured and carry on -
     * DECISIONS.md P3-23. Zero abandons it on the first unmeasurable frame, as
     * before.
     */
    private val maxBridgedGapMs: Double = 0.0,
) {

    init {
        require(bufferCapacity >= resampleLength) {
            "the cycle buffer (" + bufferCapacity + ") must hold at least as many samples as " +
                "the resampled output (" + resampleLength + ")"
        }
        require(resampleLength >= 2) { "resampleLength must be at least 2" }
    }

    /**
     * Samples of the repetition currently in progress.
     *
     * Bounded, so a movement that never finishes cannot grow without limit. If a
     * repetition overruns the buffer its oldest samples fall out, which is
     * detected through [RepSignalSpec.maxRepDurationMs] rather than by letting
     * the memory grow.
     */
    private val cycle = DoubleRingBuffer(bufferCapacity)

    var phase: MovementPhase = MovementPhase.IDLE
        private set

    var completedReps: Int = 0
        private set

    private var repStartMs: Double = Double.NaN
    private var descentStartMs: Double = Double.NaN
    private var ascentStartMs: Double = Double.NaN
    private var descentMs: Double = Double.NaN
    private var peakValue: Double = Double.NaN
    private var troughValue: Double = Double.NaN

    /** When this repetition was last measured. A bridged gap is measured from here. */
    private var lastMeasuredMs: Double = Double.NaN

    /**
     * Feeds one frame to the machine.
     *
     * @param value the driving signal for this frame, or [Double.NaN] if it could
     *   not be measured.
     * @return the repetition this frame completed, or `null` - which is the
     *   answer on the overwhelming majority of frames.
     */
    fun step(timestampMs: Double, value: Double): RepCycle? {
        if (value.isNaN()) return onUnmeasurableFrame(timestampMs)
        lastMeasuredMs = timestampMs

        if (phase != MovementPhase.IDLE) {
            cycle.push(timestampMs, value)
            recordExtremes(value)
            // Only a repetition actually under way can overrun. Someone standing
            // at the top between sets is not a slow repetition, and timing them
            // out would emit a phantom record every maxRepDurationMs.
            if (phase != MovementPhase.TOP &&
                !repStartMs.isNaN() &&
                timestampMs - repStartMs > spec.maxRepDurationMs
            ) {
                return abandon(timestampMs, RepValidity.INCOMPLETE)
            }
        }

        return when (phase) {
            MovementPhase.IDLE -> {
                // Only a subject already at the starting position begins a
                // repetition. Entering mid-movement would count a partial one.
                if (isAtTop(value)) {
                    phase = MovementPhase.TOP
                    beginRepetition(timestampMs, value)
                }
                null
            }

            MovementPhase.TOP -> {
                if (isAtTop(value)) {
                    rebaseAtTop(timestampMs, value)
                } else {
                    phase = MovementPhase.DESCENDING
                    descentStartMs = timestampMs
                }
                null
            }

            MovementPhase.DESCENDING -> {
                if (isAtBottom(value)) {
                    phase = MovementPhase.BOTTOM
                    descentMs = timestampMs - descentStartMs
                    ascentStartMs = timestampMs
                } else if (isAtTop(value)) {
                    // Turned back without reaching depth.
                    val aborted = emit(timestampMs, RepValidity.ABORTED)
                    phase = MovementPhase.TOP
                    beginRepetition(timestampMs, value)
                    return aborted
                }
                null
            }

            MovementPhase.BOTTOM -> {
                if (!isAtBottom(value)) {
                    phase = MovementPhase.ASCENDING
                    ascentStartMs = timestampMs
                }
                null
            }

            MovementPhase.ASCENDING -> {
                if (isAtTop(value)) {
                    val duration = timestampMs - repStartMs
                    val validity = if (duration < spec.minRepDurationMs) {
                        RepValidity.ABORTED
                    } else {
                        RepValidity.VALID
                    }
                    val completed = emit(timestampMs, validity)
                    if (validity == RepValidity.VALID) completedReps++
                    phase = MovementPhase.TOP
                    beginRepetition(timestampMs, value)
                    completed
                } else {
                    null
                }
            }
        }
    }

    fun reset() {
        phase = MovementPhase.IDLE
        completedReps = 0
        cycle.clear()
        repStartMs = Double.NaN
        descentStartMs = Double.NaN
        ascentStartMs = Double.NaN
        descentMs = Double.NaN
        peakValue = Double.NaN
        troughValue = Double.NaN
        lastMeasuredMs = Double.NaN
    }

    /**
     * Ends any repetition in progress, for the end of a set.
     *
     * @return the unfinished repetition, marked [RepValidity.INCOMPLETE], or
     *   `null` if nothing was in progress.
     */
    fun finish(timestampMs: Double): RepCycle? {
        if (phase == MovementPhase.IDLE || phase == MovementPhase.TOP) return null
        return abandon(timestampMs, RepValidity.INCOMPLETE)
    }

    // --- Internals ---------------------------------------------------------------

    /**
     * Tracking was lost for a frame.
     *
     * A repetition in progress carries on across a short gap - up to
     * [maxBridgedGapMs] since it was last measured - and continues from the next
     * measured frame. Nothing is filled in: the phase changes only on measured
     * values, so a gap can never count a repetition by itself, and the resampled
     * curve interpolates between the samples either side as it always has.
     * DECISIONS.md P3-23: on the phone one unmeasurable frame, or one sample the
     * outlier gate rejected, threw away almost every repetition.
     *
     * Past the limit the repetition becomes INSUFFICIENT_DATA rather than being
     * completed on guesswork or dropped without trace, ending at the last frame
     * that was actually measured.
     */
    private fun onUnmeasurableFrame(timestampMs: Double): RepCycle? {
        if (phase == MovementPhase.IDLE || phase == MovementPhase.TOP) {
            phase = MovementPhase.IDLE
            return null
        }
        if (!lastMeasuredMs.isNaN() && timestampMs - lastMeasuredMs <= maxBridgedGapMs) return null
        return abandon(if (lastMeasuredMs.isNaN()) timestampMs else lastMeasuredMs, RepValidity.INSUFFICIENT_DATA)
    }

    private fun abandon(timestampMs: Double, validity: RepValidity): RepCycle {
        val record = emit(timestampMs, validity)
        phase = MovementPhase.IDLE
        repStartMs = Double.NaN
        return record
    }

    private fun beginRepetition(timestampMs: Double, value: Double) {
        cycle.clear()
        cycle.push(timestampMs, value)
        repStartMs = timestampMs
        descentStartMs = Double.NaN
        ascentStartMs = Double.NaN
        descentMs = Double.NaN
        peakValue = value
        troughValue = value
    }

    /**
     * Restarts the clock and the cycle buffer while the subject waits at the
     * starting position, so a pause between repetitions belongs to neither the
     * one before nor the one after.
     *
     * It also keeps resampled repetitions comparable. If the dwell accumulated,
     * a fixed pause would be a large fraction of a fast repetition and a small
     * fraction of a slow one, and the two would stop lining up point for point -
     * which is the whole purpose of resampling to a fixed length.
     *
     * The peak is **not** reset to the current value, only widened. Hysteresis
     * means the first part of the descent is still counted as "at top", so
     * taking the latest in-band value as the peak would shave the width of the
     * band off every range-of-motion measurement - here, reporting a 100-degree
     * squat as 87. Range of motion is a Level 1 measurement; the hysteresis band
     * is a detection device and has no business altering it.
     */
    private fun rebaseAtTop(timestampMs: Double, value: Double) {
        cycle.clear()
        cycle.push(timestampMs, value)
        repStartMs = timestampMs
        descentStartMs = Double.NaN
        ascentStartMs = Double.NaN
        descentMs = Double.NaN
        if (peakValue.isNaN()) peakValue = value else recordExtremes(value)
        troughValue = value
    }

    private fun recordExtremes(value: Double) {
        if (spec.topIsGreater) {
            if (peakValue.isNaN() || value > peakValue) peakValue = value
            if (troughValue.isNaN() || value < troughValue) troughValue = value
        } else {
            if (peakValue.isNaN() || value < peakValue) peakValue = value
            if (troughValue.isNaN() || value > troughValue) troughValue = value
        }
    }

    private fun emit(endMs: Double, validity: RepValidity): RepCycle {
        val ascent = if (ascentStartMs.isNaN()) Double.NaN else endMs - ascentStartMs
        return RepCycle(
            startMs = repStartMs,
            endMs = endMs,
            validity = validity,
            peakValue = peakValue,
            troughValue = troughValue,
            descentMs = descentMs,
            ascentMs = ascent,
            resampled = resampleCycle(),
        )
    }

    /**
     * Resamples the repetition just finished to a fixed length.
     *
     * Post-rep work, running about once every few seconds, so it may allocate -
     * CONTRACTS.md section 7. A cycle too short to resample honestly comes back
     * as all [Double.NaN] rather than as an interpolation of two points.
     */
    private fun resampleCycle(): DoubleArray {
        val held = cycle.size
        if (held < 2) return DoubleArray(resampleLength) { Double.NaN }

        val timestamps = DoubleArray(held)
        val values = DoubleArray(held)
        cycle.copyTimestampsInto(timestamps)
        cycle.copyValuesInto(values)
        if (!(timestamps[held - 1] > timestamps[0])) {
            return DoubleArray(resampleLength) { Double.NaN }
        }

        return SignalResampler.resampleToFixedLength(
            timestampsMs = timestamps,
            values = values,
            length = resampleLength,
            // The cycle is already one repetition, so any internal gap is
            // bridgeable; the gate against fabricating data across a long
            // occlusion is INSUFFICIENT_DATA, applied above.
            maxGapMs = spec.maxRepDurationMs,
        )
    }

    private fun isAtTop(value: Double): Boolean = if (spec.topIsGreater) {
        value >= spec.topThreshold - spec.hysteresis
    } else {
        value <= spec.topThreshold + spec.hysteresis
    }

    private fun isAtBottom(value: Double): Boolean = if (spec.topIsGreater) {
        value <= spec.bottomThreshold + spec.hysteresis
    } else {
        value >= spec.bottomThreshold - spec.hysteresis
    }

    companion object {
        /** CONTRACTS.md section 7 names 64 points for a completed repetition. */
        const val DEFAULT_RESAMPLE_LENGTH: Int = 64
    }
}
