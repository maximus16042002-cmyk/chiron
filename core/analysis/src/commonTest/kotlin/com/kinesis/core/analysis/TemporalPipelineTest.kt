package com.kinesis.core.analysis

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.analysis.temporal.EmaSmoother
import com.kinesis.core.analysis.temporal.GapInterpolator
import com.kinesis.core.analysis.temporal.MedianOutlierGate
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.SignalMetrics
import com.kinesis.core.analysis.temporal.SignalResampler
import com.kinesis.core.analysis.temporal.TemporalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The temporal filter chain, both halves.
 *
 * The real-time half - outlier gate, EMA - is causal and incremental. The
 * post-rep half - gap interpolation, fixed-length resampling - sees a completed
 * repetition and may walk all of it. Runs on JVM and JS.
 */
class TemporalPipelineTest {

    private val config = TemporalConfig.PROVISIONAL

    // --- EMA -------------------------------------------------------------------

    @Test
    fun the_first_sample_passes_through_unsmoothed() {
        val smoother = EmaSmoother(alpha = 0.3)
        assertEquals(100.0, smoother.update(100.0))
    }

    @Test
    fun smoothing_follows_the_stated_recurrence() {
        val smoother = EmaSmoother(alpha = 0.25)
        smoother.update(100.0)
        // 0.25 * 200 + 0.75 * 100
        assertEquals(125.0, smoother.update(200.0), absoluteTolerance = 1e-12)
        // 0.25 * 200 + 0.75 * 125
        assertEquals(143.75, smoother.update(200.0), absoluteTolerance = 1e-12)
    }

    @Test
    fun an_alpha_of_one_is_no_smoothing_at_all() {
        val smoother = EmaSmoother(alpha = 1.0)
        smoother.update(10.0)
        assertEquals(500.0, smoother.update(500.0))
    }

    /**
     * Carried over from the prototype unchanged, because it was right: a gap
     * clears the filter rather than letting it drag a pre-occlusion value across
     * the hole and invent a trajectory through it.
     */
    @Test
    fun a_gap_resets_the_smoother_instead_of_dragging_across_it() {
        val smoother = EmaSmoother(alpha = 0.3)
        smoother.update(100.0)
        smoother.update(100.0)
        assertFalse(Geometry.isMeasured(smoother.update(Double.NaN)))
        // The sample after the gap starts afresh, not partway back at 100.
        assertEquals(20.0, smoother.update(20.0))
    }

    @Test
    fun an_alpha_outside_its_range_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> { EmaSmoother(alpha = 0.0) }
        assertFailsWith<IllegalArgumentException> { EmaSmoother(alpha = 1.5) }
    }

    // --- Outlier gate ----------------------------------------------------------

    @Test
    fun a_single_wild_sample_is_rejected_and_its_neighbours_are_not() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        for (value in listOf(90.0, 91.0, 90.5, 89.5, 90.0)) {
            assertEquals(value, gate.accept(value))
        }
        assertFalse(Geometry.isMeasured(gate.accept(300.0)), "the spike was not rejected")
        assertEquals(90.5, gate.accept(90.5), "a good sample after a spike was rejected")
        assertEquals(1L, gate.rejectedCount)
    }

    @Test
    fun a_sample_inside_the_threshold_is_kept() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        for (value in listOf(90.0, 90.0, 90.0)) gate.accept(value)
        assertEquals(105.0, gate.accept(105.0), "a 15 degree move is movement, not a spike")
    }

    @Test
    fun the_first_sample_is_accepted_because_there_is_nothing_to_judge_it_against() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 1.0, readmitSamples = 3)
        assertEquals(1000.0, gate.accept(1000.0))
    }

    /**
     * A rejected sample on its own is not evidence: it does not move the median
     * that judges the next one, so a spike cannot talk the gate into following
     * it. Only a run of rejected samples that agree with one another can - see
     * the re-admission tests below.
     *
     * Rewritten in DECISIONS.md P3-17. This test used to assert that a sustained
     * excursion stayed rejected for ten frames, and so pinned the very lock-out
     * P3-16 flagged: a real change of more than the threshold was refused for the
     * rest of the set. The owner directed that it be fixed.
     */
    @Test
    fun a_rejected_sample_is_not_evidence_until_a_run_of_them_agrees() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        for (value in listOf(90.0, 90.0, 90.0, 90.0, 90.0)) gate.accept(value)
        assertFalse(Geometry.isMeasured(gate.accept(300.0)), "a one-frame excursion was accepted")
        assertFalse(Geometry.isMeasured(gate.accept(300.0)), "a two-frame excursion was accepted")
        assertEquals(300.0, gate.accept(300.0), "a sustained, consistent change was never re-admitted")
    }

    // --- Re-admission of a sustained change - DECISIONS.md P3-17 ------------------

    @Test
    fun a_sustained_change_beyond_the_threshold_is_readmitted_after_a_short_consistent_run() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }

        // A plank that drops 28 degrees in one frame and stays there.
        val seen = (0 until 10).map { gate.accept(if (it % 2 == 0) 62.0 else 61.0) }

        assertTrue(seen[0].isNaN() && seen[1].isNaN(), "the first two frames of the change should be withheld")
        assertEquals(62.0, seen[2], "the third consistent frame should be re-admitted")
        assertTrue(seen.drop(2).none { it.isNaN() }, "the new level was rejected after re-admission: $seen")
    }

    @Test
    fun one_frame_spikes_are_still_rejected_however_many_there_are() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }
        repeat(50) {
            assertFalse(Geometry.isMeasured(gate.accept(300.0)), "a spike was accepted")
            assertEquals(90.0, gate.accept(90.0), "the true signal was rejected after a spike")
        }
    }

    @Test
    fun a_burst_shorter_than_the_run_is_rejected_whole() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }
        assertFalse(Geometry.isMeasured(gate.accept(300.0)))
        assertFalse(Geometry.isMeasured(gate.accept(301.0)))
        assertEquals(90.0, gate.accept(90.0), "the true signal was rejected after a two-frame burst")
        // The accepted sample ended the run; one more wild frame starts afresh.
        assertFalse(Geometry.isMeasured(gate.accept(300.0)), "a burst was re-admitted across an accepted sample")
    }

    /** Wild values that disagree with each other are noise, not a new level. */
    @Test
    fun rejections_that_disagree_with_each_other_never_add_up_to_a_readmission() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }
        repeat(30) {
            assertFalse(Geometry.isMeasured(gate.accept(if (it % 2 == 0) 300.0 else 10.0)))
        }
    }

    /** A run is consecutive measured frames; an unmeasured one breaks it. */
    @Test
    fun a_gap_restarts_the_run() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }
        assertFalse(Geometry.isMeasured(gate.accept(130.0)))
        assertFalse(Geometry.isMeasured(gate.accept(130.0)))
        assertFalse(Geometry.isMeasured(gate.accept(Double.NaN)))
        assertFalse(Geometry.isMeasured(gate.accept(130.0)), "a run was carried across a gap")
        assertFalse(Geometry.isMeasured(gate.accept(130.0)))
        assertEquals(130.0, gate.accept(130.0))
    }

    /** After re-admission the new level is what a spike is judged against. */
    @Test
    fun after_readmission_the_new_level_is_the_evidence() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(5) { gate.accept(90.0) }
        repeat(3) { gate.accept(130.0) }
        assertFalse(Geometry.isMeasured(gate.accept(90.0)), "a jump back to the old level is a spike now")
        assertEquals(131.0, gate.accept(131.0))
    }

    @Test
    fun a_run_of_one_would_readmit_every_spike_so_it_is_refused() {
        assertFailsWith<IllegalArgumentException> {
            MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 6)
        }
    }

    @Test
    fun a_gap_passes_through_the_gate_without_diluting_the_median() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        for (value in listOf(90.0, 90.0, 90.0)) gate.accept(value)
        assertFalse(Geometry.isMeasured(gate.accept(Double.NaN)))
        // The median is still 90, so a spike is still a spike.
        assertFalse(Geometry.isMeasured(gate.accept(300.0)))
        assertEquals(91.0, gate.accept(91.0))
    }

    @Test
    fun the_gate_never_looks_beyond_its_window() {
        val gate = MedianOutlierGate(windowSamples = 5, threshold = 20.0, readmitSamples = 3)
        repeat(10_000) { step ->
            gate.accept(90.0 + (step % 3))
            assertTrue(
                gate.lastCallSamplesVisited <= 5,
                "the gate visited " + gate.lastCallSamplesVisited + " samples",
            )
        }
    }

    // --- The assembled real-time pipeline --------------------------------------

    @Test
    fun the_pipeline_rejects_a_spike_before_it_reaches_the_smoother() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 32)
        repeat(6) { pipeline.push(it * 33.0, 90.0) }
        val settled = pipeline.current

        pipeline.push(200.0, 400.0)
        assertEquals(1L, pipeline.rejectedSampleCount)

        // The spike was gated out, so it never entered the average. What it did
        // do is clear the filter, exactly as a gap does - the sample is
        // unmeasurable either way, and pretending otherwise would be inventing
        // continuity across it.
        assertFalse(Geometry.isMeasured(pipeline.current))

        pipeline.push(233.0, 90.0)
        assertEquals(90.0, pipeline.current, absoluteTolerance = 1e-12)
        assertTrue(Geometry.isMeasured(settled))
    }

    @Test
    fun the_buffer_keeps_unmeasurable_frames_so_a_gap_keeps_its_place_in_time() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 8)
        pipeline.push(0.0, 90.0)
        pipeline.push(33.0, Double.NaN)
        pipeline.push(66.0, 90.0)

        assertEquals(3, pipeline.buffer.size)
        assertFalse(Geometry.isMeasured(pipeline.buffer.valueAt(1)))
        assertEquals(33.0, pipeline.buffer.timestampAt(1))
    }

    // --- Post-rep: gap interpolation -------------------------------------------

    @Test
    fun a_short_gap_is_bridged_linearly() {
        val timestamps = doubleArrayOf(0.0, 33.0, 66.0, 99.0, 132.0)
        val values = doubleArrayOf(10.0, Double.NaN, Double.NaN, Double.NaN, 50.0)

        val filled = GapInterpolator.interpolate(timestamps, values, maxGapMs = 300.0)
        assertEquals(20.0, filled[1], absoluteTolerance = 1e-12)
        assertEquals(30.0, filled[2], absoluteTolerance = 1e-12)
        assertEquals(40.0, filled[3], absoluteTolerance = 1e-12)
    }

    /**
     * The line between repairing a signal and fabricating one. A long occlusion
     * stays missing; filling it would produce a smooth, plausible trajectory that
     * everything downstream would treat as observation.
     */
    @Test
    fun a_long_gap_is_left_missing_rather_than_invented() {
        val timestamps = doubleArrayOf(0.0, 500.0, 1000.0)
        val values = doubleArrayOf(10.0, Double.NaN, 50.0)

        val filled = GapInterpolator.interpolate(timestamps, values, maxGapMs = 300.0)
        assertFalse(Geometry.isMeasured(filled[1]))
    }

    @Test
    fun leading_and_trailing_gaps_are_never_bridged() {
        val timestamps = doubleArrayOf(0.0, 33.0, 66.0, 99.0)
        val values = doubleArrayOf(Double.NaN, 10.0, 20.0, Double.NaN)

        val filled = GapInterpolator.interpolate(timestamps, values, maxGapMs = 300.0)
        assertFalse(Geometry.isMeasured(filled[0]))
        assertFalse(Geometry.isMeasured(filled[3]))
    }

    @Test
    fun interpolation_does_not_modify_the_array_it_was_given() {
        val timestamps = doubleArrayOf(0.0, 33.0, 66.0)
        val values = doubleArrayOf(10.0, Double.NaN, 30.0)
        GapInterpolator.interpolate(timestamps, values, maxGapMs = 300.0)
        assertFalse(Geometry.isMeasured(values[1]), "the caller's array was mutated")
    }

    // --- Post-rep: fixed-length resampling -------------------------------------

    @Test
    fun resampling_produces_exactly_the_requested_number_of_points() {
        val timestamps = DoubleArray(47) { it * 31.7 }
        val values = DoubleArray(47) { it.toDouble() }

        val resampled = SignalResampler.resampleToFixedLength(
            timestamps, values, length = 64, maxGapMs = 300.0,
        )
        assertEquals(64, resampled.size)
    }

    @Test
    fun resampling_pins_the_endpoints_and_interpolates_between_them() {
        val timestamps = doubleArrayOf(0.0, 100.0, 200.0)
        val values = doubleArrayOf(0.0, 10.0, 20.0)

        val resampled = SignalResampler.resampleToFixedLength(
            timestamps, values, length = 5, maxGapMs = 300.0,
        )
        // A linear ramp resampled onto five points stays a linear ramp.
        assertEquals(0.0, resampled[0], absoluteTolerance = 1e-9)
        assertEquals(5.0, resampled[1], absoluteTolerance = 1e-9)
        assertEquals(10.0, resampled[2], absoluteTolerance = 1e-9)
        assertEquals(15.0, resampled[3], absoluteTolerance = 1e-9)
        assertEquals(20.0, resampled[4], absoluteTolerance = 1e-9)
    }

    /**
     * The reason for fixed length rather than fixed interval: two repetitions of
     * different durations have to line up point for point before their shapes can
     * be compared at all.
     */
    @Test
    fun a_fast_and_a_slow_repetition_of_the_same_shape_resample_identically() {
        fun ramp(durationMs: Double, samples: Int): DoubleArray =
            SignalResampler.resampleToFixedLength(
                DoubleArray(samples) { it * durationMs / (samples - 1) },
                DoubleArray(samples) { it * 90.0 / (samples - 1) },
                length = 64,
                maxGapMs = durationMs,
            )

        val fast = ramp(durationMs = 900.0, samples = 28)
        val slow = ramp(durationMs = 3600.0, samples = 109)

        for (point in 0 until 64) {
            assertEquals(
                fast[point],
                slow[point],
                absoluteTolerance = 1e-9,
                message = "point " + point + " differs between a fast and a slow repetition",
            )
        }
    }

    @Test
    fun a_grid_point_spanning_a_long_gap_is_left_unmeasured() {
        val timestamps = doubleArrayOf(0.0, 1000.0)
        val values = doubleArrayOf(0.0, 100.0)

        val resampled = SignalResampler.resampleToFixedLength(
            timestamps, values, length = 4, maxGapMs = 300.0,
        )
        for (point in resampled) assertFalse(Geometry.isMeasured(point))
    }

    @Test
    fun resampling_rejects_input_it_cannot_honestly_handle() {
        assertFailsWith<IllegalArgumentException> {
            SignalResampler.resampleToFixedLength(
                doubleArrayOf(0.0), doubleArrayOf(1.0), length = 64, maxGapMs = 300.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SignalResampler.resampleToFixedLength(
                doubleArrayOf(0.0, 0.0), doubleArrayOf(1.0, 2.0), length = 64, maxGapMs = 300.0,
            )
        }
    }

    // --- Signal metrics --------------------------------------------------------

    @Test
    fun metrics_describe_coverage_and_spread_ignoring_missing_samples() {
        val metrics = SignalMetrics.of(doubleArrayOf(10.0, Double.NaN, 20.0, 30.0))
        assertEquals(4, metrics.totalSamples)
        assertEquals(3, metrics.measuredSamples)
        assertEquals(1, metrics.missingSamples)
        assertEquals(0.75, metrics.coverage, absoluteTolerance = 1e-12)
        assertEquals(20.0, metrics.mean, absoluteTolerance = 1e-12)
        assertEquals(10.0, metrics.standardDeviation, absoluteTolerance = 1e-12)
        assertEquals(20.0, metrics.range, absoluteTolerance = 1e-12)
    }

    @Test
    fun a_single_sample_has_an_unknown_spread_rather_than_a_zero_one() {
        val metrics = SignalMetrics.of(doubleArrayOf(42.0, Double.NaN))
        assertEquals(42.0, metrics.mean)
        assertFalse(
            Geometry.isMeasured(metrics.standardDeviation),
            "one sample cannot support a spread of zero",
        )
    }

    @Test
    fun a_signal_with_nothing_measured_reports_zero_coverage() {
        val metrics = SignalMetrics.of(doubleArrayOf(Double.NaN, Double.NaN))
        assertEquals(0.0, metrics.coverage)
        assertFalse(Geometry.isMeasured(metrics.mean))
        assertFalse(Geometry.isMeasured(metrics.range))
    }
}
