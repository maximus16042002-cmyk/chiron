package com.kinesis.core.analysis.temporal

import kotlin.math.abs

/**
 * Rejects isolated measurement spikes by comparing each sample against the median
 * of the samples just before it.
 *
 * Pose estimators occasionally place a landmark somewhere impossible for a single
 * frame - a wrist jumping to the other side of the body and back. Left alone,
 * one such frame drags the smoother and can trip a repetition threshold. A
 * median is used rather than a mean precisely because a single wild value does
 * not move it.
 *
 * ### Causal, unlike the prototype's
 *
 * The old prototype centred its window on the sample, looking equally far
 * forward and back. That is a better filter and it cannot run live, because the
 * forward half has not happened yet. This version looks only backward, which
 * costs a little sensitivity on the first sample of a spike and is the only
 * version that works on a camera stream. The prototype's form remains available
 * for offline work over a completed repetition.
 *
 * ### Cost
 *
 * Fixed. The window and the held-aside run are preallocated, the median is
 * found by insertion-sorting into a preallocated scratch array, and nothing here
 * allocates once constructed. Insertion sort rather than [DoubleArray.sort] because the window
 * is small, insertion sort is faster at this size, and it is provably in-place
 * on every target rather than relying on a platform library's promise.
 */
class MedianOutlierGate(
    val windowSamples: Int,
    val threshold: Double,
    /**
     * How many consecutive rejected samples, each within [threshold] of the one
     * before, it takes to re-admit a sustained change. DECISIONS.md P3-17.
     */
    val readmitSamples: Int,
) {

    init {
        require(windowSamples >= 1) { "windowSamples must be at least 1, got " + windowSamples }
        require(threshold >= 0.0) { "threshold must be non-negative, got " + threshold }
        require(readmitSamples >= 2) {
            "readmitSamples must be at least 2, or every one-frame spike would re-admit itself; got " +
                readmitSamples
        }
        require(readmitSamples <= windowSamples) {
            "readmitSamples (" + readmitSamples + ") must fit in the window (" + windowSamples + ")"
        }
    }

    private val window = DoubleArray(windowSamples)
    private val scratch = DoubleArray(windowSamples)
    private var writeIndex: Int = 0
    private var held: Int = 0

    /** Consecutive rejected samples that agree with one another, oldest first. */
    private val pending = DoubleArray(readmitSamples)
    private var pendingCount: Int = 0

    /** How many sustained changes have been re-admitted. Diagnostic; never drives logic. */
    var readmittedCount: Long = 0L
        private set

    /** Samples examined by the last [accept] call. Bounded by [windowSamples]. */
    var lastCallSamplesVisited: Int = 0
        private set

    /** How many samples this gate has rejected. Diagnostic; never drives logic. */
    var rejectedCount: Long = 0L
        private set

    /**
     * Returns [value] if it is consistent with the recent past, or [Double.NaN]
     * if it looks like a spike.
     *
     * A [Double.NaN] input passes straight through as [Double.NaN] - it is
     * already marked unmeasurable and there is nothing to judge. It is not added
     * to the window, so a gap does not dilute the median that judges the samples
     * after it.
     *
     * The first sample, and any sample with no measured predecessors, is accepted
     * unconditionally. There is nothing yet to be an outlier from, and rejecting
     * on no evidence would discard the start of every set.
     *
     * ### A sustained change is re-admitted - DECISIONS.md P3-17
     *
     * A rejected sample does not join the window, so one wild frame cannot move
     * the median that judges the next. But that alone meant a real change of more
     * than [threshold] - a plank that sags 28 degrees in a frame and stays there,
     * a landmark that jumps and stays - was rejected on every frame for the rest
     * of the set. So rejected samples are also held aside: when [readmitSamples]
     * of them arrive in a row, each within [threshold] of the one before, the
     * change is taken as real. The window is re-seeded with that run and the
     * sample that completed it is accepted.
     *
     * A one-frame spike never completes a run, because the sample after it is
     * accepted and an accepted sample ends the run. Nor do wild values that
     * disagree with each other, or a run broken by an unmeasured frame.
     */
    fun accept(value: Double): Double {
        if (value.isNaN()) {
            lastCallSamplesVisited = 0
            // A run is consecutive measured frames.
            pendingCount = 0
            return Double.NaN
        }

        val measured = collectMeasured()
        lastCallSamplesVisited = held

        if (measured == 0) {
            remember(value)
            return value
        }

        val median = medianOfScratch(measured)
        if (abs(value - median) > threshold) {
            // Held aside, not remembered: on its own it is not evidence.
            if (pendingCount > 0 && abs(value - pending[pendingCount - 1]) > threshold) pendingCount = 0
            pending[pendingCount] = value
            pendingCount++
            if (pendingCount == readmitSamples) {
                readmit()
                return value
            }
            rejectedCount++
            return Double.NaN
        }

        pendingCount = 0
        remember(value)
        return value
    }

    fun reset() {
        writeIndex = 0
        held = 0
        pendingCount = 0
        lastCallSamplesVisited = 0
        rejectedCount = 0L
        readmittedCount = 0L
    }

    /** The run becomes the evidence: the window is re-seeded with it, oldest first. */
    private fun readmit() {
        writeIndex = 0
        held = 0
        for (index in 0 until pendingCount) remember(pending[index])
        pendingCount = 0
        readmittedCount++
    }

    private fun remember(value: Double) {
        window[writeIndex] = value
        writeIndex = writeIndex + 1
        if (writeIndex == windowSamples) writeIndex = 0
        if (held < windowSamples) held++
    }

    /** Copies the measured values from the window into [scratch]. Returns how many. */
    private fun collectMeasured(): Int {
        var count = 0
        for (index in 0 until held) {
            val candidate = window[index]
            if (!candidate.isNaN()) {
                scratch[count] = candidate
                count++
            }
        }
        return count
    }

    /** Sorts `scratch[0 until count)` in place and returns its median. */
    private fun medianOfScratch(count: Int): Double {
        for (index in 1 until count) {
            val candidate = scratch[index]
            var position = index - 1
            while (position >= 0 && scratch[position] > candidate) {
                scratch[position + 1] = scratch[position]
                position--
            }
            scratch[position + 1] = candidate
        }
        val middle = count / 2
        return if (count % 2 == 1) {
            scratch[middle]
        } else {
            (scratch[middle - 1] + scratch[middle]) / 2.0
        }
    }
}
