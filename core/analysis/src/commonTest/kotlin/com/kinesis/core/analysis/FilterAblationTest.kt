package com.kinesis.core.analysis

import com.kinesis.core.analysis.temporal.EmaSmoother
import com.kinesis.core.analysis.temporal.MedianOutlierGate
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The filter ablation harness - FINAL_ARCHITECTURE section 13, week 3.
 *
 * Measures what each stage of the filter chain costs and what it buys, against a
 * synthetic signal whose true value is known exactly at every sample. Knowing the
 * truth is the point: on real pose data there is nothing to compare a filter
 * against, so the question of whether smoothing helps cannot even be asked.
 *
 * ### Why this is a test and not a report
 *
 * The old prototype had a `FilterComparisonTest` that printed a table of EMA
 * alphas and asserted nothing, so it could not fail and nobody would notice if a
 * filter change made things worse. The idea was right and the form was not. Here
 * each comparison is an assertion, so a regression breaks the build.
 * DECISIONS.md P2-2.
 *
 * ### What it does and does not establish
 *
 * It separates the **benefit** of smoothing - noise suppression on a stationary
 * signal - from its **cost** - lag on a moving one, which on this signal is the
 * larger of the two at the provisional alpha. That trade-off is real and is
 * exactly why `TemporalConfig.PROVISIONAL.smoothingAlpha` stays provisional
 * until P4 measures it on a device.
 *
 * None of these numbers describes Kinesis's accuracy on human movement. They
 * describe filter behaviour on a synthetic signal, which is a different claim,
 * and CLAUDE.md section 22 forbids blurring the two.
 *
 * Deterministic on every target: the noise comes from an integer generator
 * defined here, not from a platform random source.
 */
class FilterAblationTest {

    // --- The benefit: noise suppression on a stationary signal -----------------

    /**
     * With nothing moving, there is no lag to pay and smoothing is pure gain.
     * This isolates the benefit.
     */
    @Test
    fun smoothing_reduces_noise_and_more_smoothing_reduces_it_further() {
        val errors = ALPHAS.map { alpha -> alpha to rootMeanSquareError(stationarySignal(alpha)) }

        for ((alpha, error) in errors) {
            assertTrue(error > 0.0, "alpha " + alpha + " produced no error at all, which is suspect")
        }

        // Monotonic: heavier smoothing, less residual noise.
        for (index in 1 until errors.size) {
            val (heavierAlpha, heavierError) = errors[index - 1]
            val (lighterAlpha, lighterError) = errors[index]
            assertTrue(
                heavierError < lighterError,
                "alpha " + heavierAlpha + " gave RMSE " + heavierError +
                    " but the lighter alpha " + lighterAlpha + " gave " + lighterError +
                    "; heavier smoothing should suppress more noise on a still signal",
            )
        }

        // And it is worth having: the provisional alpha beats no smoothing.
        val unsmoothed = rootMeanSquareError(stationarySignal(alpha = 1.0))
        val provisional = rootMeanSquareError(
            stationarySignal(TemporalConfig.PROVISIONAL.smoothingAlpha),
        )
        assertTrue(
            provisional < unsmoothed / 2.0,
            "the provisional alpha should at least halve stationary noise: " +
                provisional + " vs " + unsmoothed,
        )
    }

    // --- The cost: lag on a moving signal --------------------------------------

    /**
     * An exponential moving average following a constant-slope ramp settles a
     * fixed distance behind it. That distance is `slope * (1 - alpha) / alpha`
     * samples' worth, derivable from the recurrence, so this is checked against
     * the algebra rather than against whatever the code happened to produce.
     *
     * At the provisional alpha of 0.3 the lag is 2.33 samples - about 78 ms at
     * 30 fps. That is the delay before a repetition boundary can be recognised,
     * and it is the reason alpha cannot simply be lowered until the traces look
     * tidy.
     */
    @Test
    fun the_lag_a_smoother_introduces_matches_the_algebra() {
        val slopePerSample = 1.5

        for (alpha in ALPHAS) {
            val smoother = EmaSmoother(alpha)
            var truth = 0.0
            var smoothed = 0.0
            // Long enough to reach steady state at even the heaviest alpha.
            repeat(4000) {
                truth += slopePerSample
                smoothed = smoother.update(truth)
            }

            val expectedLag = slopePerSample * (1.0 - alpha) / alpha
            assertEquals(
                expectedLag,
                truth - smoothed,
                absoluteTolerance = 1e-6,
                message = "steady-state lag at alpha " + alpha,
            )
        }
    }

    @Test
    fun heavier_smoothing_costs_more_lag_on_a_moving_signal() {
        val errors = ALPHAS.map { alpha -> alpha to rootMeanSquareError(movingSignal(alpha, noisy = false)) }

        // The mirror image of the stationary case: on a clean moving signal every
        // bit of smoothing is pure cost.
        for (index in 1 until errors.size) {
            val (heavierAlpha, heavierError) = errors[index - 1]
            val (lighterAlpha, lighterError) = errors[index]
            assertTrue(
                heavierError > lighterError,
                "alpha " + heavierAlpha + " gave RMSE " + heavierError +
                    " but the lighter alpha " + lighterAlpha + " gave " + lighterError +
                    "; heavier smoothing should lag more on a moving signal",
            )
        }
    }

    // --- The outlier gate ------------------------------------------------------

    /**
     * Unlike smoothing, the gate is not a trade-off on this signal: spikes are
     * pure corruption and removing them is pure gain.
     *
     * The comparison has to be made against the error the chain already carries
     * without any spikes at all, because at the provisional alpha that floor is
     * large - lag on a moving signal dominates, as the test above shows. Comparing
     * the two spiked arms directly understates the gate badly: it looks like a
     * factor of under two, when what it actually removes is almost all of the
     * damage the spikes do. Measuring the excess over the clean floor is the
     * honest form of the question.
     */
    @Test
    fun the_outlier_gate_removes_spikes_that_smoothing_alone_cannot() {
        val floor = rootMeanSquareError(spikedSignal(gateEnabled = true, spikesEnabled = false))
        val withGate = rootMeanSquareError(spikedSignal(gateEnabled = true, spikesEnabled = true))
        val withoutGate = rootMeanSquareError(spikedSignal(gateEnabled = false, spikesEnabled = true))

        val excessWithGate = withGate - floor
        val excessWithoutGate = withoutGate - floor

        assertTrue(
            excessWithoutGate > 1.0,
            "the harness injected spikes that cost almost nothing (" + excessWithoutGate +
                " degrees RMSE over the clean floor of " + floor + "), so this proves nothing",
        )
        assertTrue(
            excessWithGate < excessWithoutGate / 10.0,
            "the gate should remove almost all of the spike damage, but left " +
                excessWithGate + " degrees RMSE of excess against " + excessWithoutGate +
                " ungated, over a clean floor of " + floor,
        )
    }

    @Test
    fun the_gate_rejects_the_spikes_and_leaves_the_movement_alone() {
        val gate = MedianOutlierGate(
            windowSamples = TemporalConfig.PROVISIONAL.outlierWindowSamples,
            threshold = TemporalConfig.PROVISIONAL.outlierThreshold,
        )
        var accepted = 0
        for (frame in 0 until FRAMES) {
            val value = truthAt(frame) + spikeAt(frame)
            if (!gate.accept(value).isNaN()) accepted++
        }

        val injected = (0 until FRAMES).count { spikeAt(it) != 0.0 }
        assertTrue(injected > 0, "the harness injected no spikes, so it proves nothing")
        assertEquals(
            injected.toLong(),
            gate.rejectedCount,
            "the gate should reject exactly the injected spikes, no more and no fewer",
        )
        assertEquals(FRAMES - injected, accepted)
    }

    // --- Reproducibility --------------------------------------------------------

    /**
     * An ablation result that is not reproducible is an anecdote. The generator
     * is a plain integer recurrence rather than a platform random source, so the
     * same comparison runs identically on JVM and JS - which is also what makes
     * this usable as a cross-platform parity fixture later.
     */
    @Test
    fun the_harness_produces_identical_numbers_on_every_run() {
        val first = rootMeanSquareError(movingSignal(0.3, noisy = true))
        val second = rootMeanSquareError(movingSignal(0.3, noisy = true))
        assertEquals(first, second, absoluteTolerance = 0.0)

        val noise = DeterministicNoise(SEED)
        val replay = DeterministicNoise(SEED)
        repeat(500) {
            assertEquals(noise.next(), replay.next(), absoluteTolerance = 0.0)
        }
    }

    // --- Signal construction ----------------------------------------------------

    /** Error of each filtered sample against the truth it was meant to recover. */
    private class Trial(val errors: DoubleArray)

    private fun rootMeanSquareError(trial: Trial): Double {
        var sumOfSquares = 0.0
        var counted = 0
        for (error in trial.errors) {
            if (error.isNaN()) continue
            sumOfSquares += error * error
            counted++
        }
        return if (counted == 0) Double.NaN else sqrt(sumOfSquares / counted)
    }

    /** A joint held still, seen through a noisy estimator. */
    private fun stationarySignal(alpha: Double): Trial {
        val smoother = EmaSmoother(alpha)
        val noise = DeterministicNoise(SEED)
        val errors = DoubleArray(FRAMES)
        for (frame in 0 until FRAMES) {
            val observed = HELD_ANGLE + noise.next() * NOISE_DEGREES
            errors[frame] = smoother.update(observed) - HELD_ANGLE
        }
        return Trial(errors)
    }

    /** A joint moving through a repetition cycle. */
    private fun movingSignal(alpha: Double, noisy: Boolean): Trial {
        val smoother = EmaSmoother(alpha)
        val noise = DeterministicNoise(SEED)
        val errors = DoubleArray(FRAMES)
        for (frame in 0 until FRAMES) {
            val truth = truthAt(frame)
            val observed = if (noisy) truth + noise.next() * NOISE_DEGREES else truth
            errors[frame] = smoother.update(observed) - truth
        }
        return Trial(errors)
    }

    /** A moving, noisy joint, optionally with single-frame estimator failures. */
    private fun spikedSignal(gateEnabled: Boolean, spikesEnabled: Boolean): Trial {
        val config = TemporalConfig.PROVISIONAL
        val pipeline = RealTimeSignalPipeline(
            // Disabling the gate is expressed as a threshold nothing can exceed,
            // so both arms run the identical code path.
            if (gateEnabled) config else config.copy(outlierThreshold = Double.MAX_VALUE),
            bufferCapacity = 64,
        )
        val noise = DeterministicNoise(SEED)
        val errors = DoubleArray(FRAMES)
        for (frame in 0 until FRAMES) {
            val truth = truthAt(frame)
            val spike = if (spikesEnabled) spikeAt(frame) else 0.0
            val observed = truth + noise.next() * NOISE_DEGREES + spike
            errors[frame] = pipeline.push(frame * FRAME_INTERVAL_MS, observed) - truth
        }
        return Trial(errors)
    }

    /**
     * The true joint angle: a repetition cycle of about two seconds at 30 fps,
     * swinging 45 degrees either side of 90. Chosen to resemble a squat or a
     * curl in period and amplitude, and it is a synthetic stand-in, not a
     * recording of one.
     */
    private fun truthAt(frame: Int): Double =
        HELD_ANGLE + AMPLITUDE_DEGREES * sin(2.0 * PI * frame / FRAMES_PER_CYCLE)

    /** A single-frame estimator failure, of the kind a pose model produces. */
    private fun spikeAt(frame: Int): Double =
        if (frame > 0 && frame % SPIKE_EVERY == 0) SPIKE_DEGREES else 0.0

    /**
     * A linear congruential generator, returning values in -1..1.
     *
     * Hand-rolled rather than taken from `kotlin.random` so the sequence is
     * pinned by the arithmetic written here, and an ablation result recorded on
     * one target means the same on another.
     */
    private class DeterministicNoise(seed: Int) {
        private var state: Int = seed

        fun next(): Double {
            state = (state * 1103515245 + 12345) and 0x7FFFFFFF
            return (state % 20001) / 10000.0 - 1.0
        }
    }

    private companion object {
        const val SEED = 20260920
        const val FRAMES = 1200
        const val FRAME_INTERVAL_MS = 33.0
        const val FRAMES_PER_CYCLE = 60.0
        const val HELD_ANGLE = 90.0
        const val AMPLITUDE_DEGREES = 45.0
        const val NOISE_DEGREES = 3.0
        const val SPIKE_EVERY = 37
        const val SPIKE_DEGREES = 150.0

        /** Heaviest smoothing first, so "index - 1 is heavier" reads correctly. */
        val ALPHAS = listOf(0.1, 0.2, 0.3, 0.5, 0.8)
    }
}
