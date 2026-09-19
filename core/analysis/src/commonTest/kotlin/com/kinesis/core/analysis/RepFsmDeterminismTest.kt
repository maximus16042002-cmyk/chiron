package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.analysis.rep.RepFsm
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent guard test - CLAUDE.md sections 6 and 18, CONTRACTS.md section 6.
 *
 * Repetition counts and movement phases are **discrete** outputs. CONTRACTS.md
 * section 6 requires those to match exactly across platforms, not merely to be
 * plausible, so this file asserts exact counts and an exact phase sequence
 * against a synthetic track built from straight lines. Nothing here is
 * approximate and nothing here is a tolerance.
 *
 * A rep counter is unusually easy to write so that it looks right. Off-by-one at
 * the set boundary, a phase that flickers on the threshold, a partial rep
 * counted as whole - none of them crash, and on a live camera all of them look
 * like the user moved oddly. Hence a fixed input with a hand-derived answer.
 *
 * ### Incremental, not batch
 *
 * The FSM is driven one frame at a time. The old prototype's RepEngine replayed
 * the whole signal from IDLE on every call, and its live adapter called it once
 * per frame over a growing history - see DECISIONS.md P2-2. The per-frame budget
 * guards from P2 already forbid that shape; these tests pin the behaviour that
 * replaced it.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class RepFsmDeterminismTest {

    /**
     * A squat-shaped knee angle. Standing is about 170 degrees, the bottom of
     * the squat about 70. Straight-line ramps rather than a sine, so every
     * expected count below is derived by hand rather than read off the
     * implementation.
     */
    private val spec = RepSignalSpec(
        joint = JointAngleId.KNEE,
        plane = MeasurementPlane.IMAGE_2D,
        topThreshold = 160.0,
        bottomThreshold = 90.0,
        hysteresis = 5.0,
        minRepDurationMs = 300.0,
        maxRepDurationMs = 10_000.0,
    )

    private val frameMs = 1000.0 / 30.0

    // --- Exact counts -----------------------------------------------------------

    @Test
    fun a_fixed_synthetic_track_produces_an_exactly_pinned_repetition_count() {
        for (intended in listOf(1, 3, 5, 12)) {
            val fsm = RepFsm(spec, bufferCapacity = 512)
            var counted = 0
            for ((timestamp, value) in squatTrack(intended)) {
                val completed = fsm.step(timestamp, value)
                if (completed != null && completed.validity == RepValidity.VALID) counted++
            }
            assertEquals(intended, counted, "counted the wrong number of repetitions")
            assertEquals(intended, fsm.completedReps)
        }
    }

    @Test
    fun the_phase_sequence_is_exactly_the_one_a_squat_passes_through() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        val observed = mutableListOf<MovementPhase>()
        observed += fsm.phase
        for ((timestamp, value) in squatTrack(2)) {
            fsm.step(timestamp, value)
            if (observed.isEmpty() || observed[observed.size - 1] != fsm.phase) {
                observed += fsm.phase
            }
        }

        assertEquals(
            listOf(
                MovementPhase.IDLE,
                MovementPhase.TOP,
                MovementPhase.DESCENDING,
                MovementPhase.BOTTOM,
                MovementPhase.ASCENDING,
                MovementPhase.TOP,
                MovementPhase.DESCENDING,
                MovementPhase.BOTTOM,
                MovementPhase.ASCENDING,
                MovementPhase.TOP,
            ),
            observed,
        )
    }

    @Test
    fun replaying_the_same_track_gives_byte_for_byte_the_same_answer() {
        fun run(): List<String> {
            val fsm = RepFsm(spec, bufferCapacity = 512)
            val trace = mutableListOf<String>()
            for ((timestamp, value) in squatTrack(4)) {
                val completed = fsm.step(timestamp, value)
                if (completed != null) {
                    trace += completed.startMs.toString() + "/" + completed.endMs.toString() +
                        "/" + completed.validity.name + "/" + completed.troughValue.toString()
                }
            }
            return trace
        }
        assertEquals(run(), run())
    }

    // --- Partial and abandoned movement -----------------------------------------

    @Test
    fun a_descent_that_never_reaches_the_bottom_is_not_a_repetition() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        var counted = 0
        // Down to 120 - past the top threshold, nowhere near the bottom one.
        for ((timestamp, value) in track(ramp(170.0, 120.0, 20) + ramp(120.0, 170.0, 20))) {
            val completed = fsm.step(timestamp, value)
            if (completed != null && completed.validity == RepValidity.VALID) counted++
        }
        assertEquals(0, counted, "a half-depth movement was counted as a repetition")
    }

    @Test
    fun a_repetition_abandoned_at_the_bottom_is_not_counted() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        var counted = 0
        for ((timestamp, value) in track(ramp(170.0, 70.0, 30) + flat(70.0, 60))) {
            val completed = fsm.step(timestamp, value)
            if (completed != null && completed.validity == RepValidity.VALID) counted++
        }
        assertEquals(0, counted)
        assertEquals(MovementPhase.BOTTOM, fsm.phase)
    }

    /**
     * Hysteresis exists for this case. A signal sitting exactly on a threshold
     * jitters by a fraction of a degree from frame to frame, and without a band
     * the phase would oscillate and manufacture repetitions out of noise.
     */
    @Test
    fun a_signal_dithering_on_the_threshold_produces_no_repetitions() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        var counted = 0
        val dither = ArrayList<Double>()
        for (step in 0 until 400) {
            dither += if (step % 2 == 0) 160.4 else 159.6
        }
        for ((timestamp, value) in track(dither)) {
            val completed = fsm.step(timestamp, value)
            if (completed != null && completed.validity == RepValidity.VALID) counted++
        }
        assertEquals(0, counted, "threshold dither was counted as repetitions")
    }

    @Test
    fun a_repetition_faster_than_the_minimum_duration_is_rejected() {
        val fast = RepSignalSpec(
            joint = JointAngleId.KNEE,
            plane = MeasurementPlane.IMAGE_2D,
            topThreshold = 160.0,
            bottomThreshold = 90.0,
            hysteresis = 5.0,
            minRepDurationMs = 5_000.0,
            maxRepDurationMs = 20_000.0,
        )
        val fsm = RepFsm(fast, bufferCapacity = 512)
        var aborted = 0
        var valid = 0
        for ((timestamp, value) in squatTrack(3)) {
            val completed = fsm.step(timestamp, value)
            if (completed != null) {
                if (completed.validity == RepValidity.VALID) valid++ else aborted++
            }
        }
        assertEquals(0, valid, "a repetition well under the minimum duration was called valid")
        assertEquals(3, aborted)
    }

    // --- Missing data -----------------------------------------------------------

    /**
     * A repetition interrupted by lost tracking is reported as
     * INSUFFICIENT_DATA rather than being silently completed or silently
     * dropped. The user moved; the camera stopped seeing it; those are different
     * facts and the record says which.
     */
    @Test
    fun losing_the_subject_mid_repetition_yields_an_insufficient_data_record() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        val completed = mutableListOf<RepValidity>()
        val samples = ramp(170.0, 70.0, 30) + List(20) { Double.NaN } + ramp(70.0, 170.0, 30)
        for ((timestamp, value) in track(samples)) {
            fsm.step(timestamp, value)?.let { completed += it.validity }
        }
        assertTrue(
            completed.contains(RepValidity.INSUFFICIENT_DATA),
            "expected an INSUFFICIENT_DATA record, got " + completed,
        )
    }

    @Test
    fun a_reset_fsm_counts_the_same_track_the_same_way() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        for ((timestamp, value) in squatTrack(3)) fsm.step(timestamp, value)
        val first = fsm.completedReps

        fsm.reset()
        assertEquals(0, fsm.completedReps)
        assertEquals(MovementPhase.IDLE, fsm.phase)

        for ((timestamp, value) in squatTrack(3)) fsm.step(timestamp, value)
        assertEquals(first, fsm.completedReps)
    }

    // --- The record a repetition produces ---------------------------------------

    @Test
    fun a_completed_repetition_reports_its_extremes_and_its_phase_durations() {
        val fsm = RepFsm(spec, bufferCapacity = 512)
        var record: com.kinesis.core.analysis.rep.RepCycle? = null
        for ((timestamp, value) in squatTrack(1)) {
            fsm.step(timestamp, value)?.let { record = it }
        }

        val completed = record ?: throw AssertionError("no repetition was produced")
        assertEquals(70.0, completed.troughValue, absoluteTolerance = 1e-9)
        assertEquals(170.0, completed.peakValue, absoluteTolerance = 1e-9)
        assertEquals(100.0, completed.rangeOfMotion, absoluteTolerance = 1e-9)
        assertTrue(completed.descentMs > 0.0, "descent took no time")
        assertTrue(completed.ascentMs > 0.0, "ascent took no time")
        assertTrue(completed.endMs > completed.startMs)
        assertEquals(RepValidity.VALID, completed.validity)
    }

    /**
     * CLAUDE.md section 15: completed repetitions carry a fixed-length resampled
     * signal, so that reps of different durations can be compared at all.
     */
    @Test
    fun a_completed_repetition_carries_a_fixed_length_resampled_signal() {
        val fsm = RepFsm(spec, bufferCapacity = 512, resampleLength = 64)
        var record: com.kinesis.core.analysis.rep.RepCycle? = null
        for ((timestamp, value) in squatTrack(1)) {
            fsm.step(timestamp, value)?.let { record = it }
        }
        val completed = record ?: throw AssertionError("no repetition was produced")
        assertEquals(64, completed.resampled.size)
    }

    /**
     * Two repetitions of the same shape but different speed resample to the same
     * curve. This is the property the whole fixed-length scheme exists for.
     */
    @Test
    fun a_slow_and_a_fast_repetition_of_the_same_shape_resample_alike() {
        fun shapeOf(framesPerPhase: Int): DoubleArray {
            val fsm = RepFsm(spec, bufferCapacity = 2048, resampleLength = 64)
            var record: com.kinesis.core.analysis.rep.RepCycle? = null
            val samples = flat(170.0, 5) +
                ramp(170.0, 70.0, framesPerPhase) +
                ramp(70.0, 170.0, framesPerPhase) +
                flat(170.0, 5)
            for ((timestamp, value) in track(samples)) {
                fsm.step(timestamp, value)?.let { record = it }
            }
            return (record ?: throw AssertionError("no repetition")).resampled
        }

        val fast = shapeOf(20)
        val slow = shapeOf(80)
        for (point in 0 until 64) {
            assertEquals(
                fast[point],
                slow[point],
                absoluteTolerance = 2.0,
                message = "point " + point + " differs between a fast and a slow repetition",
            )
        }
    }

    // --- Track construction -------------------------------------------------------

    /** [count] squats, each a descent, an ascent, and a pause at the top. */
    private fun squatTrack(count: Int): List<Pair<Double, Double>> {
        val samples = ArrayList<Double>()
        samples += flat(170.0, 10)
        for (rep in 0 until count) {
            samples += ramp(170.0, 70.0, 30)
            samples += ramp(70.0, 170.0, 30)
            samples += flat(170.0, 10)
        }
        return track(samples)
    }

    /** A straight line from [from] to [to] over [steps] samples, excluding [from]. */
    private fun ramp(from: Double, to: Double, steps: Int): List<Double> =
        (1..steps).map { from + (to - from) * it / steps }

    private fun flat(value: Double, steps: Int): List<Double> = List(steps) { value }

    private fun track(samples: List<Double>): List<Pair<Double, Double>> =
        samples.mapIndexed { index, value -> (index * frameMs) to value }
}
