package com.kinesis.core.analysis.temporal

import kotlin.math.sqrt

/**
 * How much of a signal was actually measured, and how much it moved.
 *
 * **Post-rep path only.** These describe signal quality, not movement quality -
 * they say how well the camera saw the repetition, not how well it was
 * performed. Nothing here is a finding, and none of these numbers may be
 * presented as one.
 *
 * [coverage] is the honest one to surface: a repetition analysed from 40 percent
 * of its frames is a weaker measurement than one analysed from 95 percent, and
 * the difference should reach the user rather than being averaged into silence.
 */
data class SignalMetrics(
    val totalSamples: Int,
    val measuredSamples: Int,
    val mean: Double,
    val standardDeviation: Double,
    val minimum: Double,
    val maximum: Double,
) {

    val missingSamples: Int get() = totalSamples - measuredSamples

    /** Fraction of samples that were measured, 0..1. Zero for an empty signal. */
    val coverage: Double
        get() = if (totalSamples == 0) 0.0 else measuredSamples.toDouble() / totalSamples

    /** Peak-to-peak excursion. [Double.NaN] when nothing was measured. */
    val range: Double
        get() = if (measuredSamples == 0) Double.NaN else maximum - minimum

    companion object {

        /**
         * Computes metrics over [values], ignoring [Double.NaN] entries.
         *
         * The standard deviation is the sample form, dividing by `n - 1`, and is
         * [Double.NaN] when fewer than two samples were measured - with one
         * sample the spread is not zero, it is unknown, and reporting zero would
         * be a claim the data does not support.
         */
        fun of(values: DoubleArray): SignalMetrics {
            var measured = 0
            var sum = 0.0
            var minimum = Double.NaN
            var maximum = Double.NaN

            for (index in values.indices) {
                val value = values[index]
                if (value.isNaN()) continue
                measured++
                sum += value
                if (minimum.isNaN() || value < minimum) minimum = value
                if (maximum.isNaN() || value > maximum) maximum = value
            }

            if (measured == 0) {
                return SignalMetrics(
                    totalSamples = values.size,
                    measuredSamples = 0,
                    mean = Double.NaN,
                    standardDeviation = Double.NaN,
                    minimum = Double.NaN,
                    maximum = Double.NaN,
                )
            }

            val mean = sum / measured
            val standardDeviation = if (measured < 2) {
                Double.NaN
            } else {
                var sumOfSquares = 0.0
                for (index in values.indices) {
                    val value = values[index]
                    if (value.isNaN()) continue
                    val deviation = value - mean
                    sumOfSquares += deviation * deviation
                }
                sqrt(sumOfSquares / (measured - 1))
            }

            return SignalMetrics(
                totalSamples = values.size,
                measuredSamples = measured,
                mean = mean,
                standardDeviation = standardDeviation,
                minimum = minimum,
                maximum = maximum,
            )
        }
    }
}
