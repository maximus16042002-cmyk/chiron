package com.kinesis.core.analysis.temporal

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The smoother weighs a result by the time since the last one, not per result - DECISIONS.md
 * P3-33.
 *
 * `smoothingAlpha` 0.3 is the prototype's figure, and the prototype assumed 30 results a
 * second. The phone gives 12-16, and about 6 when hot (P3-31). Weighted per result, the same
 * filter lagged twice as long in time at 15 a second and five times as long at 6. That blunted
 * the peaks, and repetitions were lost (P3-32 section 3). Weighted per unit of time, a movement
 * is followed as far in the same time at any rate, and at 30 a second nothing changes.
 *
 * What must not change: a gap still resets the filter, and a result whose clock does not
 * advance is still an ordinary result.
 */
class SmoothingRateTest {

    private val interval = 1000.0 / 30.0

    /**
     * A step from 90 to 120 degrees, sampled every [every] intervals: 90 up to time 0, 120 after,
     * inside the outlier gate's 35 degrees so nothing is rejected. The smoothed value at
     * [intervals] intervals after the last 90.
     */
    private fun step(every: Int, intervals: Int): Double {
        val pipeline = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, bufferCapacity = 64)
        for (k in -5..0) pipeline.push(k * every * interval, 90.0)
        var smoothed = Double.NaN
        for (k in 1..intervals / every) smoothed = pipeline.push(k * every * interval, 120.0)
        return smoothed
    }

    @Test
    fun a_step_is_followed_as_far_in_the_same_time_at_any_result_rate() {
        // A third of a second: 10 results at 30 a second, 5 at 15, 2 at 6.
        val at30 = step(every = 1, intervals = 10)
        assertEquals(120.0 - 30.0 * 0.7.pow(10), at30, absoluteTolerance = 1e-9)
        assertEquals(at30, step(every = 2, intervals = 10), absoluteTolerance = 1e-9, "15 a second")
        assertEquals(at30, step(every = 5, intervals = 10), absoluteTolerance = 1e-9, "6 a second")
    }

    @Test
    fun at_thirty_results_a_second_the_weight_is_the_stated_alpha() {
        val pipeline = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, bufferCapacity = 8)
        pipeline.push(0.0, 90.0)
        // 0.3 * 120 + 0.7 * 90
        assertEquals(99.0, pipeline.push(interval, 120.0), absoluteTolerance = 1e-9)
    }

    @Test
    fun a_result_two_intervals_after_the_last_carries_the_weight_of_two() {
        val pipeline = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, bufferCapacity = 8)
        pipeline.push(0.0, 90.0)
        // 1 - 0.7^2 = 0.51: 0.51 * 120 + 0.49 * 90, what two results of 120 would have given.
        assertEquals(105.3, pipeline.push(2 * interval, 120.0), absoluteTolerance = 1e-9)
    }

    @Test
    fun a_gap_still_resets_the_smoother_however_long_it_was() {
        val pipeline = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, bufferCapacity = 8)
        pipeline.push(0.0, 90.0)
        pipeline.push(interval, Double.NaN)
        // Afresh, not smoothed from 90 across the gap.
        assertEquals(120.0, pipeline.push(10 * interval, 120.0), absoluteTolerance = 1e-12)
    }

    @Test
    fun a_result_whose_clock_does_not_advance_is_weighted_as_one_interval() {
        val pipeline = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, bufferCapacity = 8)
        pipeline.push(100.0, 90.0)
        assertEquals(99.0, pipeline.push(100.0, 120.0), absoluteTolerance = 1e-9, "the same time")
        assertEquals(99.0 * 0.7 + 36.0, pipeline.push(50.0, 120.0), absoluteTolerance = 1e-9, "an earlier time")
    }

    @Test
    fun one_interval_is_exactly_alpha_and_the_per_result_form_is_unchanged() {
        val smoother = EmaSmoother(alpha = 0.3)
        assertEquals(0.3, smoother.weightAfter(1.0))
        assertEquals(0.3, smoother.weightAfter(0.0))
        assertEquals(0.3, smoother.weightAfter(Double.NaN))
        assertEquals(1.0 - 0.7.pow(5), smoother.weightAfter(5.0), absoluteTolerance = 1e-12)
        assertEquals(1.0, EmaSmoother(alpha = 1.0).weightAfter(0.5), "no smoothing at any rate")
    }

    @Test
    fun the_interval_must_be_a_positive_finite_time() {
        val base = TemporalConfig.PROVISIONAL
        assertEquals(1000.0 / 30.0, base.smoothingIntervalMs)
        assertFailsWith<IllegalArgumentException> { base.copy(smoothingIntervalMs = 0.0) }
        assertFailsWith<IllegalArgumentException> { base.copy(smoothingIntervalMs = -33.0) }
        assertFailsWith<IllegalArgumentException> { base.copy(smoothingIntervalMs = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { base.copy(smoothingIntervalMs = Double.NaN) }
    }
}
