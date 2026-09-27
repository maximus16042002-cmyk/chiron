package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.BandPosition
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import com.kinesis.core.domain.calibration.TimingBaseline
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Permanent guard - DECISIONS.md P3-21, CLAUDE.md sections 3, 9, 11 and 18.
 *
 * Calibration is where the research question lives - *do personal baselines
 * outperform population thresholds?* - so a baseline that is quietly wrong
 * corrupts every Level 2 claim and every personalisation study built on it.
 * None of these failures would crash. Each would produce a plausible number:
 *
 * - left and right fitted together, so a user's weaker side reads as normal;
 * - aborted or half-seen repetitions in the fit, dragging the normal down;
 * - a curl's idle arm contributing zeroes, or the other arm's values;
 * - a variant calibrated separately from its parent, against P3-6;
 * - a baseline from too few repetitions presented as if it were established.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class CalibrationGuardTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    // --- Left and right stay apart -------------------------------------------------

    @Test
    fun each_side_is_fitted_from_its_own_values() {
        val reps = (1..6).map { squatRep(it, left = 100.0 + it, right = 80.0 + it) }
        val baseline = fitted(Calibration.fitRepetitions(squat, reps))
        assertEquals(103.5, baseline.rangeOfMotion.left.median)
        assertEquals(83.5, baseline.rangeOfMotion.right.median)
    }

    /**
     * The calibration counterpart of BilateralIntegrityTest's most important
     * assertion. If a refactor ever pooled the sides, these two baselines would
     * be equal and this would fail.
     */
    @Test
    fun swapping_the_sides_swaps_the_baseline() {
        val reps = (1..6).map { squatRep(it, left = 100.0 + it, right = 80.0 + it) }
        val swapped = (1..6).map { squatRep(it, left = 80.0 + it, right = 100.0 + it) }
        val a = fitted(Calibration.fitRepetitions(squat, reps))
        val b = fitted(Calibration.fitRepetitions(squat, swapped))
        assertNotEquals(a.rangeOfMotion, b.rangeOfMotion)
        assertEquals(a.rangeOfMotion.left, b.rangeOfMotion.right)
        assertEquals(a.rangeOfMotion.right, b.rangeOfMotion.left)
    }

    /** The habitual offset keeps its direction: a user whose left goes deeper has a positive one. */
    @Test
    fun the_habitual_offset_is_left_minus_right_with_its_sign() {
        val reps = (1..6).map { squatRep(it, left = 100.0 + it, right = 80.0 + it) }
        val baseline = fitted(Calibration.fitRepetitions(squat, reps))
        assertEquals(20.0, baseline.habitualOffset.median)
        val mirrored = fitted(Calibration.fitRepetitions(squat, (1..6).map { squatRep(it, left = 80.0 + it, right = 100.0 + it) }))
        assertEquals(-20.0, mirrored.habitualOffset.median)
    }

    // --- Only valid repetitions count ------------------------------------------------

    @Test
    fun invalid_repetitions_do_not_move_the_baseline() {
        val valid = (1..6).map { squatRep(it, left = 100.0 + it, right = 90.0 + it) }
        val noise = listOf(RepValidity.ABORTED, RepValidity.INCOMPLETE, RepValidity.INSUFFICIENT_DATA)
            .mapIndexed { i, validity -> squatRep(7 + i, left = 10.0, right = 400.0, validity = validity) }
        val clean = fitted(Calibration.fitRepetitions(squat, valid))
        val dirty = fitted(Calibration.fitRepetitions(squat, valid + noise))
        assertEquals(clean.rangeOfMotion, dirty.rangeOfMotion)
        assertEquals(clean.timing, dirty.timing)
        assertEquals(9, dirty.recordsOffered)
        assertEquals(6, dirty.recordsUsed)
    }

    @Test
    fun too_few_valid_repetitions_is_reported_not_fitted() {
        val reps = (1..Calibration.MIN_REPETITIONS - 1).map { squatRep(it, 100.0, 95.0) } +
            squatRep(99, 100.0, 95.0, validity = RepValidity.ABORTED)
        val outcome = Calibration.fitRepetitions(squat, reps)
        val insufficient = assertIs<CalibrationOutcome.Insufficient>(outcome)
        assertEquals(Calibration.MIN_REPETITIONS, insufficient.minimumSamples)
        assertEquals(Calibration.MIN_REPETITIONS - 1, insufficient.bestSideSamples)
        assertEquals(Calibration.MIN_REPETITIONS, insufficient.recordsOffered)
    }

    @Test
    fun exactly_the_minimum_is_enough() {
        val reps = (1..Calibration.MIN_REPETITIONS).map { squatRep(it, 100.0, 95.0) }
        assertIs<CalibrationOutcome.Fitted<*>>(Calibration.fitRepetitions(squat, reps))
    }

    // --- Per-limb exercises: each arm is its own calibration -------------------------

    @Test
    fun a_curl_s_arms_are_fitted_and_timed_separately() {
        val reps = (1..5).map { curlRep(2 * it - 1, Side.LEFT, rom = 120.0, durationMs = 2000.0) } +
            (1..5).map { curlRep(2 * it, Side.RIGHT, rom = 100.0, durationMs = 3000.0) }
        val baseline = fitted(Calibration.fitRepetitions(curl, reps))
        assertEquals(120.0, baseline.rangeOfMotion.left.median)
        assertEquals(100.0, baseline.rangeOfMotion.right.median)
        assertEquals(5, baseline.rangeOfMotion.left.n)
        val timing = assertIs<TimingBaseline.PerSide>(baseline.timing)
        assertEquals(2000.0, timing.left.durationMs.median)
        assertEquals(3000.0, timing.right.durationMs.median)
        assertEquals(RobustSummary.ABSENT, baseline.habitualOffset, "arms that never move together have no offset")
    }

    /** The idle arm is absent, not zero, and does not borrow the other arm's normal. */
    @Test
    fun an_arm_never_curled_has_no_baseline_of_its_own() {
        val reps = (1..6).map { curlRep(it, Side.LEFT, rom = 120.0, durationMs = 2000.0) }
        val baseline = fitted(Calibration.fitRepetitions(curl, reps))
        assertEquals(RobustSummary.ABSENT, baseline.rangeOfMotion.right)
        val timing = assertIs<TimingBaseline.PerSide>(baseline.timing)
        assertEquals(0, timing.right.durationMs.n)

        val rightCurl = curlRep(7, Side.RIGHT, rom = 60.0, durationMs = 2000.0)
        val result = PersonalBands.analyseRep(curl, rightCurl, baseline)
        val rightRom = result.placements.single { it.metric == PersonalBands.ROM && it.side == Side.RIGHT }
        assertEquals(BandPosition.NO_BASELINE, rightRom.position)
    }

    @Test
    fun arms_that_each_fall_short_are_insufficient_even_if_the_total_is_not() {
        val reps = (1..3).map { curlRep(2 * it - 1, Side.LEFT, 120.0, 2000.0) } +
            (1..3).map { curlRep(2 * it, Side.RIGHT, 100.0, 2000.0) }
        val insufficient = assertIs<CalibrationOutcome.Insufficient>(Calibration.fitRepetitions(curl, reps))
        assertEquals(3, insufficient.bestSideSamples)
    }

    // --- Variants share their parent's calibration -----------------------------------

    @Test
    fun a_variant_is_calibrated_as_its_parent() {
        val reps = (1..6).map { squatRep(it, 100.0 + it, 95.0 + it) }
        val asSquat = fitted(Calibration.fitRepetitions(squat, reps))
        val asGoblet = fitted(Calibration.fitRepetitions(ExerciseLibrary.gobletSquat, reps))
        assertEquals("squat", asGoblet.exerciseId)
        assertEquals(asSquat.rangeOfMotion, asGoblet.rangeOfMotion)
        assertEquals(asSquat.timing, asGoblet.timing)

        // And a goblet squat is compared against the squat baseline without complaint.
        val result = PersonalBands.analyseRep(ExerciseLibrary.gobletSquat, squatRep(7, 103.0, 98.0), asSquat)
        assertEquals("squat", result.exerciseId)
    }

    @Test
    fun a_repetition_is_never_compared_against_another_exercise_s_baseline() {
        val squatBaseline = fitted(Calibration.fitRepetitions(squat, (1..6).map { squatRep(it, 100.0, 95.0) }))
        assertFailsWith<IllegalArgumentException> {
            PersonalBands.analyseRep(ExerciseLibrary.pushUp, squatRep(1, 100.0, 95.0), squatBaseline)
        }
    }

    // --- Holds are calibrated as holds -------------------------------------------------

    @Test
    fun holds_are_fitted_from_valid_holds_only() {
        val plank = ExerciseLibrary.plank
        val holds = listOf(30_000.0, 32_000.0, 34_000.0).map { hold(it) } +
            hold(500.0, HoldValidity.TOO_SHORT) + hold(1_000.0, HoldValidity.BROKEN)
        val outcome = Calibration.fitHolds(plank, holds)
        val baseline = assertIs<CalibrationOutcome.Fitted<*>>(outcome).baseline
        val holdBaseline = assertIs<com.kinesis.core.domain.calibration.HoldBaseline>(baseline)
        assertEquals(32_000.0, holdBaseline.totalHoldMs.median)
        assertEquals(3, holdBaseline.recordsUsed)
        assertEquals(5, holdBaseline.recordsOffered)
    }

    // --- Determinism ------------------------------------------------------------------

    @Test
    fun the_same_repetitions_in_another_order_give_the_same_baseline() {
        val reps = (1..8).map { squatRep(it, 100.0 + (it * 37 % 11), 90.0 + (it * 13 % 7)) }
        val a = fitted(Calibration.fitRepetitions(squat, reps))
        val b = fitted(Calibration.fitRepetitions(squat, reps.reversed()))
        assertEquals(a.rangeOfMotion, b.rangeOfMotion)
        assertEquals(a.timing, b.timing)
        assertEquals(a.habitualOffset, b.habitualOffset)
    }

    @Test
    fun the_minimum_is_stored_with_the_baseline() {
        val baseline = fitted(Calibration.fitRepetitions(squat, (1..6).map { squatRep(it, 100.0, 95.0) }))
        assertEquals(Calibration.MIN_REPETITIONS, baseline.minimumSamples)
        assertTrue(baseline.methodVersion.isNotBlank())
    }

    // --- Helpers -----------------------------------------------------------------------

    private fun fitted(outcome: CalibrationOutcome<RepetitionBaseline>): RepetitionBaseline =
        assertIs<CalibrationOutcome.Fitted<RepetitionBaseline>>(outcome).baseline

    companion object {

        fun squatRep(
            index: Int,
            left: Double,
            right: Double,
            validity: RepValidity = RepValidity.VALID,
            descentMs: Double = 1000.0 + index,
            ascentMs: Double = 800.0 + index,
        ): RepRecord = RepRecord(
            repIndex = index,
            attributedSide = null,
            startMs = index * 5000.0,
            endMs = index * 5000.0 + descentMs + ascentMs,
            validity = validity,
            rangeOfMotion = BilateralSignal(left, right),
            peakValue = BilateralSignal(170.0, 170.0),
            troughValue = BilateralSignal(170.0 - left, 170.0 - right),
            descentMs = descentMs,
            ascentMs = ascentMs,
            resampledLeft = DoubleArray(4) { 170.0 },
            resampledRight = DoubleArray(4) { 170.0 },
        )

        fun curlRep(index: Int, side: Side, rom: Double, durationMs: Double): RepRecord {
            fun sided(value: Double) = BilateralSignal(
                if (side == Side.LEFT) value else Double.NaN,
                if (side == Side.RIGHT) value else Double.NaN,
            )
            return RepRecord(
                repIndex = index,
                attributedSide = side,
                startMs = index * 5000.0,
                endMs = index * 5000.0 + durationMs,
                validity = RepValidity.VALID,
                rangeOfMotion = sided(rom),
                peakValue = sided(160.0),
                troughValue = sided(160.0 - rom),
                descentMs = durationMs / 2.0,
                ascentMs = durationMs / 2.0,
                resampledLeft = DoubleArray(4) { if (side == Side.LEFT) 160.0 else Double.NaN },
                resampledRight = DoubleArray(4) { if (side == Side.RIGHT) 160.0 else Double.NaN },
            )
        }

        fun hold(heldMs: Double, validity: HoldValidity = HoldValidity.VALID): HoldRecord = HoldRecord(
            startMs = 0.0,
            endMs = heldMs + 1000.0,
            validity = validity,
            totalHoldMs = heldMs,
            longestUnbrokenMs = heldMs,
            breakCount = 0,
            meanAbsDeviation = 2.0,
            maxAbsDeviation = 4.0,
            coverage = 1.0,
        )
    }
}
