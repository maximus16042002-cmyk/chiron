package com.kinesis.core.analysis.calibration

import com.kinesis.core.domain.calibration.RobustStatistic
import kotlin.math.floor
import kotlin.math.min

/**
 * Median and interquartile range, defined once - DECISIONS.md P3-22.
 *
 * **Post-rep path only.** Runs once per calibration, over a handful of completed
 * repetitions, and may allocate and sort.
 *
 * ### The quartile definition, fixed
 *
 * Quartiles have several textbook definitions that give different numbers on
 * small samples, and calibration samples are small. This uses linear
 * interpolation between order statistics - Hyndman and Fan's type 7, which is
 * NumPy's and R's default: for sorted `x[0..n-1]` the `q`-quantile sits at
 * `h = (n - 1) q`, between `x[floor h]` and the next value.
 *
 * NumPy's default matters: the MM-Fit convergence study that will set the final
 * calibration count (ZERO_DATASET_BUILD_PLAN.md, Study 3) is Python, and it must
 * compute the same baseline the app does, or its answer is about a different
 * estimator.
 */
object RobustStatistics {

    /**
     * The median, IQR and count of [values]. Values that are not finite are not
     * measurements - `NaN` is Kinesis's "not measured" - and are skipped, not
     * counted. With nothing measured the result is [RobustStatistic.NOT_MEASURED].
     */
    fun of(values: DoubleArray): RobustStatistic {
        var measured = 0
        for (value in values) if (value.isFinite()) measured++
        if (measured == 0) return RobustStatistic.NOT_MEASURED

        val sorted = DoubleArray(measured)
        var next = 0
        for (value in values) if (value.isFinite()) sorted[next++] = value
        sorted.sort()

        return RobustStatistic(
            median = quantile(sorted, 0.5),
            interquartileRange = quantile(sorted, 0.75) - quantile(sorted, 0.25),
            count = measured,
        )
    }

    fun of(values: List<Double>): RobustStatistic = of(values.toDoubleArray())

    /** The type-7 quantile of already-sorted, finite values. */
    fun quantile(sorted: DoubleArray, q: Double): Double {
        require(sorted.isNotEmpty()) { "a quantile of nothing is undefined" }
        require(q in 0.0..1.0) { "q must be within 0..1, got $q" }
        val position = (sorted.size - 1) * q
        val lower = floor(position).toInt()
        val upper = min(lower + 1, sorted.size - 1)
        return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower])
    }
}
