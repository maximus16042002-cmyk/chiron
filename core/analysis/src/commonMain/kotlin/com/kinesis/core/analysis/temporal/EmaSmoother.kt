package com.kinesis.core.analysis.temporal

import kotlin.math.pow

/**
 * An exponential moving average, applied one sample at a time.
 *
 * `smoothed = alpha * value + (1 - alpha) * previousSmoothed`
 *
 * Causal, so it can run live: it uses the current sample and its own single
 * stored value, never a future one. State is one `Double`, so the cost and the
 * memory are the same on the first frame and the hundred-thousandth.
 *
 * ### Why incremental rather than batch
 *
 * The old prototype's EMA took a whole signal and returned a whole signal, and
 * its live path re-ran it over the entire session on every frame. The filter
 * itself was correct; the way it was driven was not. Restructuring it to hold
 * its own state is the port - DECISIONS.md P2-2.
 *
 * ### Gaps
 *
 * An unmeasurable sample, [Double.NaN], clears the state rather than being
 * folded in. The next real sample starts the average afresh instead of the
 * filter dragging a value from before the occlusion across it, which would
 * invent a trajectory through the gap. The prototype had this right and it is
 * kept exactly.
 *
 * ### Per unit of time - DECISIONS.md P3-33
 *
 * [alpha] is the weight a result gets one interval after the last. A result k
 * intervals after the last gets `1 - (1 - alpha)^k`, what k results of the same
 * value would have given, so a movement is followed as far in the same time at
 * any result rate. Weighted per result, the phone's 15 results a second lagged
 * twice as long as MM-Fit's 30, and 6 a second five times as long.
 */
class EmaSmoother(val alpha: Double) {

    init {
        require(alpha > 0.0 && alpha <= 1.0) {
            "alpha must be in (0, 1]; 1.0 is no smoothing, lower is smoother and laggier. Got " + alpha
        }
    }

    /** The current smoothed value, or [Double.NaN] before the first sample. */
    var current: Double = Double.NaN
        private set

    /**
     * Folds [value] into the average and returns the new smoothed value.
     *
     * A [Double.NaN] input resets the filter and returns [Double.NaN].
     */
    fun update(value: Double): Double = update(value, 1.0)

    /**
     * Folds [value], arriving [intervals] intervals after the last result, into the
     * average and returns the new smoothed value. An interval that is not positive,
     * a clock that stood still or went back, counts as one: the result is real even
     * if its timestamp is not.
     *
     * A [Double.NaN] input resets the filter and returns [Double.NaN].
     */
    fun update(value: Double, intervals: Double): Double {
        if (value.isNaN()) {
            current = Double.NaN
            return Double.NaN
        }
        if (current.isNaN()) {
            current = value
        } else {
            val weight = weightAfter(intervals)
            current = weight * value + (1.0 - weight) * current
        }
        return current
    }

    /** The weight of a result [intervals] intervals after the last. Exactly [alpha] for one. */
    fun weightAfter(intervals: Double): Double =
        if (intervals == 1.0 || !(intervals > 0.0)) alpha else 1.0 - (1.0 - alpha).pow(intervals)

    fun reset() {
        current = Double.NaN
    }
}
