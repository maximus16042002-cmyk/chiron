package com.kinesis.core.analysis.calibration

import kotlin.math.sqrt

/**
 * How far one repetition's resampled curve lies from a mean curve: the
 * root-mean-square difference over the points both measured, degrees, or `NaN`
 * when they share none. Defined once, because calibration summarises it over the
 * calibration repetitions (`curveSpread`, DECISIONS.md P3-22) and Layer 1 compares a
 * new repetition's against that summary (P3-30) - two uses of one quantity.
 *
 * Post-rep path: over a finished, fixed-length curve.
 */
object CurveDistance {

    fun rms(curve: DoubleArray, mean: DoubleArray): Double {
        require(curve.size == mean.size) { "a curve of ${curve.size} points against a mean of ${mean.size}" }
        var sumOfSquares = 0.0
        var count = 0
        for (point in mean.indices) {
            val difference = curve[point] - mean[point]
            if (difference.isFinite()) {
                sumOfSquares += difference * difference
                count++
            }
        }
        return if (count == 0) Double.NaN else sqrt(sumOfSquares / count)
    }
}
