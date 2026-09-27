package com.kinesis.core.analysis.calibration

import com.kinesis.core.domain.calibration.RobustSummary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the quartile definition - DECISIONS.md P3-21.
 *
 * There are at least nine textbook definitions of a sample quantile and they
 * disagree on small samples, which is exactly where calibration lives (five
 * repetitions). The P7/P12 studies compute baselines in Python; if the two
 * sides used different definitions, the report's convergence curve would
 * describe a baseline the app never computes. So the definition is Hyndman and
 * Fan's type 7 - linear interpolation between order statistics - which is
 * numpy's and R's default, and the expected values below are what
 * `numpy.percentile(x, [25, 50, 75])` returns for the same input.
 */
class RobustStatisticsTest {

    @Test
    fun four_values_match_numpy_default_percentiles() {
        // numpy.percentile([1, 2, 3, 4], [25, 50, 75]) -> [1.75, 2.5, 3.25]
        val summary = RobustStatistics.summarize(doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(4, summary.n)
        assertEquals(1.75, summary.q1)
        assertEquals(2.5, summary.median)
        assertEquals(3.25, summary.q3)
        assertEquals(1.5, summary.iqr)
    }

    @Test
    fun five_values_match_numpy_default_percentiles() {
        // numpy.percentile([10, 20, 30, 40, 100], [25, 50, 75]) -> [20, 30, 40]
        val summary = RobustStatistics.summarize(doubleArrayOf(100.0, 30.0, 10.0, 40.0, 20.0))
        assertEquals(20.0, summary.q1)
        assertEquals(30.0, summary.median)
        assertEquals(40.0, summary.q3)
    }

    @Test
    fun six_values_interpolate_between_order_statistics() {
        // numpy.percentile([2, 4, 4, 5, 7, 9], [25, 50, 75]) -> [4.0, 4.5, 6.5]
        val summary = RobustStatistics.summarize(doubleArrayOf(9.0, 4.0, 2.0, 7.0, 4.0, 5.0))
        assertEquals(4.0, summary.q1)
        assertEquals(4.5, summary.median)
        assertEquals(6.5, summary.q3)
    }

    @Test
    fun the_median_resists_one_wild_value() {
        val tame = RobustStatistics.summarize(doubleArrayOf(90.0, 91.0, 92.0, 93.0, 94.0))
        val wild = RobustStatistics.summarize(doubleArrayOf(90.0, 91.0, 92.0, 93.0, 400.0))
        assertEquals(tame.median, wild.median)
    }

    @Test
    fun one_value_is_its_own_quartiles() {
        val summary = RobustStatistics.summarize(doubleArrayOf(42.0))
        assertEquals(RobustSummary(1, 42.0, 42.0, 42.0), summary)
    }

    /** A missing side is absent, never zero: zero is a measurement and none was made. */
    @Test
    fun no_finite_values_is_absent_not_zero() {
        assertEquals(RobustSummary.ABSENT, RobustStatistics.summarize(doubleArrayOf()))
        val unmeasured = RobustStatistics.summarize(doubleArrayOf(Double.NaN, Double.NaN))
        assertEquals(0, unmeasured.n)
        assertTrue(unmeasured.median.isNaN())
    }

    @Test
    fun unmeasured_values_are_left_out_rather_than_counted() {
        val summary = RobustStatistics.summarize(
            doubleArrayOf(Double.NaN, 1.0, Double.POSITIVE_INFINITY, 2.0, 3.0, 4.0),
        )
        assertEquals(4, summary.n)
        assertEquals(2.5, summary.median)
    }

    @Test
    fun the_order_of_the_samples_does_not_matter() {
        val a = RobustStatistics.summarize(doubleArrayOf(5.0, 1.0, 4.0, 2.0, 3.0, 8.0, 7.0))
        val b = RobustStatistics.summarize(doubleArrayOf(8.0, 7.0, 3.0, 2.0, 1.0, 5.0, 4.0))
        assertEquals(a, b)
    }

    @Test
    fun the_caller_s_array_is_not_reordered() {
        val values = doubleArrayOf(3.0, 1.0, 2.0)
        RobustStatistics.summarize(values)
        assertContentEquals(doubleArrayOf(3.0, 1.0, 2.0), values)
    }

    @Test
    fun a_quantile_outside_zero_to_one_is_refused() {
        assertFailsWith<IllegalArgumentException> { RobustStatistics.quantile(doubleArrayOf(1.0), 1.5) }
    }
}
