package com.kinesis.core.analysis.calibration

import com.kinesis.core.domain.calibration.RobustSummary
import kotlin.math.floor

/**
 * Median and quartiles, with one pinned definition - DECISIONS.md P3-21.
 *
 * FINAL_ARCHITECTURE section 7: calibration baselines are statistical (median
 * and IQR), robust estimation rather than learning.
 *
 * ### Which quantile
 *
 * Hyndman and Fan's **type 7**: sort, then interpolate linearly between the
 * order statistics either side of `h = (n - 1) * p`. It is the default in numpy
 * and R, so the P7/P12 Python pipeline computes the same quartiles as the phone
 * without anyone having to remember a flag. Small samples are where the nine
 * textbook definitions disagree most, and five calibration repetitions is a
 * small sample. `RobustStatisticsTest` pins the values against numpy's.
 *
 * Post-rep path only (CONTRACTS.md section 7): this sorts a copy of its input.
 */
object RobustStatistics {

    /**
     * Median and quartiles of the finite values in [values]. Non-finite values
     * are unmeasured and left out, never counted as zero; with none left, the
     * result is [RobustSummary.ABSENT]. [values] itself is not modified.
     */
    fun summarize(values: DoubleArray): RobustSummary {
        var finite = 0
        for (value in values) if (value.isFinite()) finite++
        if (finite == 0) return RobustSummary.ABSENT
        val sorted = DoubleArray(finite)
        var at = 0
        for (value in values) if (value.isFinite()) sorted[at++] = value
        sorted.sort()
        return RobustSummary(
            n = finite,
            median = quantile(sorted, 0.5),
            q1 = quantile(sorted, 0.25),
            q3 = quantile(sorted, 0.75),
        )
    }

    /** The type 7 quantile [p] of [sorted], which must already be in ascending order. */
    fun quantile(sorted: DoubleArray, p: Double): Double {
        require(p in 0.0..1.0) { "a quantile lies in 0..1, got " + p }
        require(sorted.isNotEmpty()) { "no samples to take a quantile of" }
        val h = (sorted.size - 1) * p
        val below = floor(h).toInt()
        if (below >= sorted.size - 1) return sorted[sorted.size - 1]
        val fraction = h - below
        return sorted[below] + fraction * (sorted[below + 1] - sorted[below])
    }
}
