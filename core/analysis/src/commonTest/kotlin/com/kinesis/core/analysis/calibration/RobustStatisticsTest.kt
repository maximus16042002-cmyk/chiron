package com.kinesis.core.analysis.calibration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The quartile definition every baseline is built on - DECISIONS.md P3-22.
 *
 * The expected values are NumPy's `np.percentile(x, [25, 50, 75])` with its
 * default linear method, worked by hand, because the Python convergence study
 * must compute the same numbers the app does.
 */
class RobustStatisticsTest {

    @Test
    fun five_values_quartile_on_the_second_and_fourth() {
        val stat = RobustStatistics.of(doubleArrayOf(5.0, 1.0, 4.0, 2.0, 3.0))
        assertEquals(3.0, stat.median)
        assertEquals(2.0, stat.interquartileRange) // Q1 = 2, Q3 = 4
        assertEquals(5, stat.count)
    }

    @Test
    fun four_values_interpolate_as_numpy_does() {
        // h = 3q: Q1 at 0.75 -> 1.75, median at 1.5 -> 2.5, Q3 at 2.25 -> 3.25.
        val sorted = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.75, RobustStatistics.quantile(sorted, 0.25), 1e-12)
        assertEquals(2.5, RobustStatistics.quantile(sorted, 0.5), 1e-12)
        assertEquals(3.25, RobustStatistics.quantile(sorted, 0.75), 1e-12)
        assertEquals(1.5, RobustStatistics.of(sorted).interquartileRange, 1e-12)
    }

    @Test
    fun two_values_and_one_value() {
        val two = RobustStatistics.of(doubleArrayOf(20.0, 10.0))
        assertEquals(15.0, two.median)
        assertEquals(5.0, two.interquartileRange, 1e-12) // 17.5 - 12.5
        val one = RobustStatistics.of(doubleArrayOf(7.0))
        assertEquals(7.0, one.median)
        assertEquals(0.0, one.interquartileRange)
        assertEquals(1, one.count)
    }

    /** Not measured is `NaN` and a count of zero - never a zero that reads as a measurement. */
    @Test
    fun nothing_measured_is_not_measured() {
        val none = RobustStatistics.of(doubleArrayOf(Double.NaN, Double.NaN))
        assertTrue(none.median.isNaN())
        assertTrue(none.interquartileRange.isNaN())
        assertEquals(0, none.count)
        assertTrue(!none.isMeasured)
        assertEquals(0, RobustStatistics.of(DoubleArray(0)).count)
    }

    @Test
    fun unmeasured_values_are_skipped_not_counted() {
        val stat = RobustStatistics.of(doubleArrayOf(1.0, Double.NaN, 2.0, 3.0, Double.NaN, 4.0, 5.0))
        assertEquals(3.0, stat.median)
        assertEquals(2.0, stat.interquartileRange)
        assertEquals(5, stat.count)
    }

    /**
     * Why the provisional minimum is five: with five, the quartiles are the
     * second-smallest and second-largest values, so one wild repetition at
     * either end moves neither the median nor the spread.
     */
    @Test
    fun with_five_one_extreme_repetition_at_either_end_changes_nothing() {
        val steady = RobustStatistics.of(doubleArrayOf(90.0, 91.0, 92.0, 93.0, 94.0))
        val wild = RobustStatistics.of(doubleArrayOf(10.0, 91.0, 92.0, 93.0, 200.0))
        assertEquals(steady.median, wild.median)
        assertEquals(steady.interquartileRange, wild.interquartileRange)
    }

    /** And with four it does - the lower quartile reaches the extreme value. */
    @Test
    fun with_four_an_extreme_repetition_moves_the_spread() {
        val steady = RobustStatistics.of(doubleArrayOf(91.0, 92.0, 93.0, 94.0))
        val wild = RobustStatistics.of(doubleArrayOf(10.0, 92.0, 93.0, 200.0))
        assertTrue(steady.interquartileRange != wild.interquartileRange)
    }

    /** Discrete order statistics must not depend on the order the values arrived in. */
    @Test
    fun the_order_of_the_input_does_not_matter() {
        val a = RobustStatistics.of(doubleArrayOf(3.5, 1.25, 9.0, 4.0, 2.0, 7.5))
        val b = RobustStatistics.of(doubleArrayOf(9.0, 7.5, 4.0, 3.5, 2.0, 1.25))
        assertEquals(a, b)
    }
}
