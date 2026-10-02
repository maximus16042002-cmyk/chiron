package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.calibration.CalibrationFixtures.curve
import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Building a personal baseline from a calibration set - DECISIONS.md P3-22.
 *
 * The guards that matter most here are the project's oldest ones, applied to a
 * new place: left and right are never averaged (CLAUDE.md section 3), only valid
 * repetitions count, and "not measured" is `NaN` rather than a number.
 */
class CalibrationTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl
    private val row = ExerciseLibrary.row
    private val plank = ExerciseLibrary.plank

    private fun repetitionBaseline(outcome: CalibrationOutcome): RepetitionBaseline {
        val calibrated = assertIs<CalibrationOutcome.Calibrated>(outcome, outcome.displayText)
        return assertIs<RepetitionBaseline>(calibrated.baseline)
    }

    private fun calibrate(profile: com.kinesis.core.analysis.profile.RepetitionExercise, reps: List<RepRecord>) =
        Calibration.fromRepetitions(profile, reps, createdAtEpochMs = 1_000.0, sourceSessionId = "s1")

    private fun eightSquats() = (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }

    // --- Left and right ------------------------------------------------------------

    @Test
    fun each_side_keeps_its_own_median_range_of_motion() {
        val baseline = repetitionBaseline(calibrate(squat, eightSquats()))
        assertEquals(RobustStatistic(103.5, 3.5, 8), baseline.rangeOfMotion.left)
        assertEquals(RobustStatistic(93.5, 3.5, 8), baseline.rangeOfMotion.right)
        // 98.5 would be the two sides averaged - CLAUDE.md section 3.
        assertTrue(baseline.rangeOfMotion.left.median != 98.5 && baseline.rangeOfMotion.right.median != 98.5)
    }

    @Test
    fun peak_and_trough_angles_are_per_side_too() {
        val baseline = repetitionBaseline(calibrate(squat, eightSquats()))
        assertEquals(RobustStatistic(CalibrationFixtures.TOP_LEFT, 0.0, 8), baseline.peakValue.left)
        assertEquals(RobustStatistic(CalibrationFixtures.TOP_RIGHT, 0.0, 8), baseline.peakValue.right)
        assertEquals(CalibrationFixtures.TOP_LEFT - 103.5, baseline.troughValue.left.median, 1e-12)
        assertEquals(CalibrationFixtures.TOP_RIGHT - 93.5, baseline.troughValue.right.median, 1e-12)
    }

    // --- Only valid repetitions -----------------------------------------------------

    @Test
    fun only_valid_repetitions_count() {
        val reps = listOf(
            paired(1, 100.0, 90.0),
            paired(2, 100.0, 90.0),
            paired(3, 40.0, 40.0, RepValidity.ABORTED),
            paired(4, 100.0, 90.0),
            paired(5, 100.0, 90.0),
            paired(6, 10.0, 10.0, RepValidity.INCOMPLETE),
            paired(7, 100.0, 90.0),
            paired(8, 100.0, 90.0),
            oneSided(9, Side.LEFT, 150.0, attributed = null, validity = RepValidity.INSUFFICIENT_DATA),
        )
        val baseline = repetitionBaseline(calibrate(squat, reps))
        assertEquals(RobustStatistic(100.0, 0.0, 6), baseline.rangeOfMotion.left)
        assertEquals(RobustStatistic(90.0, 0.0, 6), baseline.rangeOfMotion.right)
    }

    @Test
    fun too_few_valid_repetitions_means_no_baseline_and_says_so_plainly() {
        val reps = (1..4).map { paired(it, 100.0, 90.0) } +
            (5..7).map { paired(it, 30.0, 30.0, RepValidity.ABORTED) }
        val outcome = assertIs<CalibrationOutcome.TooFewRepetitions>(calibrate(squat, reps))
        assertEquals(4, outcome.validLeft)
        assertEquals(4, outcome.validRight)
        assertEquals(5, outcome.requiredPerSide)
        assertTrue(!outcome.oneSideSuffices)
        assertTrue(outcome.displayText.contains("No baseline"), outcome.displayText)
        assertTrue(outcome.displayText.contains("5"), outcome.displayText)
        assertTrue(outcome.displayText.contains("4"), outcome.displayText)
    }

    @Test
    fun an_empty_set_has_no_baseline() {
        assertIs<CalibrationOutcome.TooFewRepetitions>(calibrate(squat, emptyList()))
    }

    @Test
    fun only_the_first_eight_valid_repetitions_on_a_side_are_used() {
        val reps = eightSquats() + (9..12).map { paired(it, 60.0, 50.0) }
        val baseline = repetitionBaseline(calibrate(squat, reps))
        assertEquals(8, baseline.repetitionsUsedLeft)
        assertEquals(103.5, baseline.rangeOfMotion.left.median)
        // In set order, by repetition index, whatever order the list arrives in.
        assertEquals(baseline.rangeOfMotion.left, repetitionBaseline(calibrate(squat, reps.reversed())).rangeOfMotion.left)
    }

    // --- Tempo, as the records attribute it ------------------------------------------

    @Test
    fun both_sides_moving_together_time_the_repetition_not_each_side() {
        val reps = (1..6).map { paired(it, 100.0, 90.0, descentMs = 1_000.0 + 10 * it, ascentMs = 800.0) }
        val baseline = repetitionBaseline(calibrate(squat, reps))
        assertEquals(1_035.0, baseline.descentMs.perRepetition.median, 1e-12)
        assertEquals(6, baseline.descentMs.perRepetition.count)
        assertEquals(800.0, baseline.ascentMs.perRepetition.median)
        // Filing the one timing under both sides would claim two measurements never made.
        assertTrue(!baseline.descentMs.perSide.left.isMeasured && !baseline.descentMs.perSide.right.isMeasured)
    }

    // --- Alternating limbs -----------------------------------------------------------

    private fun alternatingCurls(leftCount: Int, rightCount: Int): List<RepRecord> {
        val reps = ArrayList<RepRecord>()
        var index = 1
        var left = 0
        var right = 0
        while (left < leftCount || right < rightCount) {
            if (left < leftCount) {
                reps += oneSided(index++, Side.LEFT, 110.0, descentMs = 900.0, ascentMs = 700.0); left++
            }
            if (right < rightCount) {
                reps += oneSided(index++, Side.RIGHT, 100.0, descentMs = 1_200.0, ascentMs = 1_000.0); right++
            }
        }
        return reps
    }

    @Test
    fun an_alternating_exercise_calibrates_each_arm_from_its_own_repetitions() {
        val baseline = repetitionBaseline(calibrate(curl, alternatingCurls(5, 5)))
        assertEquals(RobustStatistic(110.0, 0.0, 5), baseline.rangeOfMotion.left)
        assertEquals(RobustStatistic(100.0, 0.0, 5), baseline.rangeOfMotion.right)
        assertEquals(900.0, baseline.descentMs.perSide.left.median)
        assertEquals(1_200.0, baseline.descentMs.perSide.right.median)
        assertEquals(1_000.0, baseline.ascentMs.perSide.right.median)
        assertTrue(!baseline.descentMs.perRepetition.isMeasured)
    }

    @Test
    fun an_alternating_exercise_needs_five_on_each_arm() {
        val outcome = assertIs<CalibrationOutcome.TooFewRepetitions>(calibrate(curl, alternatingCurls(6, 4)))
        assertEquals(6, outcome.validLeft)
        assertEquals(4, outcome.validRight)
        assertTrue(outcome.displayText.contains("right"), outcome.displayText)
    }

    /** The idle arm's `NaN` curve must not poison the working arm's mean. */
    @Test
    fun an_alternating_exercises_mean_curves_are_each_arms_own() {
        val baseline = repetitionBaseline(calibrate(curl, alternatingCurls(5, 5)))
        val expectedLeft = curve(150.0, 110.0)
        val expectedRight = curve(150.0, 100.0)
        for (i in 0 until CalibrationFixtures.POINTS) {
            assertEquals(expectedLeft[i], baseline.meanCurveLeft[i], 1e-9)
            assertEquals(expectedRight[i], baseline.meanCurveRight[i], 1e-9)
        }
    }

    // --- Asymmetry -------------------------------------------------------------------

    @Test
    fun asymmetry_is_paired_when_both_sides_share_each_repetition() {
        val baseline = repetitionBaseline(calibrate(squat, eightSquats()))
        val paired = assertIs<AsymmetryBaseline.Paired>(baseline.asymmetry)
        assertEquals(RobustStatistic(10.0, 0.0, 8), paired.leftMinusRight)
    }

    @Test
    fun asymmetry_between_alternating_arms_is_the_difference_of_their_medians() {
        val baseline = repetitionBaseline(calibrate(curl, alternatingCurls(5, 5)))
        assertEquals(AsymmetryBaseline.BetweenSideMedians(10.0), baseline.asymmetry)
    }

    // --- One side only ---------------------------------------------------------------

    @Test
    fun a_near_side_only_profile_calibrates_the_side_it_can_see() {
        val reps = (1..6).map { oneSided(it, Side.RIGHT, 70.0, attributed = null) }
        val baseline = repetitionBaseline(calibrate(row, reps))
        assertEquals(RobustStatistic.NOT_MEASURED, baseline.rangeOfMotion.left)
        assertEquals(RobustStatistic(70.0, 0.0, 6), baseline.rangeOfMotion.right)
        assertEquals(900.0, baseline.descentMs.perSide.right.median)
        // FINAL_ARCHITECTURE section 5: no symmetry claim for the row.
        assertEquals(AsymmetryBaseline.NotMeasurable, baseline.asymmetry)
        assertTrue(baseline.meanCurveLeft.all { it.isNaN() })
    }

    @Test
    fun a_near_side_only_profile_still_needs_five_on_the_side_it_sees() {
        val reps = (1..4).map { oneSided(it, Side.RIGHT, 70.0, attributed = null) }
        val outcome = assertIs<CalibrationOutcome.TooFewRepetitions>(calibrate(row, reps))
        assertTrue(outcome.oneSideSuffices)
        assertEquals(4, outcome.validRight)
    }

    // --- The curve -------------------------------------------------------------------

    @Test
    fun the_mean_curve_is_pointwise_and_skips_unmeasured_points() {
        val reps = (0 until 5).map { paired(it + 1, 100.0 + it, 90.0) }
        reps[0].resampledLeft[10] = Double.NaN
        val baseline = repetitionBaseline(calibrate(squat, reps))
        val mean = curve(CalibrationFixtures.TOP_LEFT, 102.0)
        for (i in 0 until CalibrationFixtures.POINTS) {
            if (i == 10) continue
            assertEquals(mean[i], baseline.meanCurveLeft[i], 1e-9)
        }
        // Point 10 is the mean of the four repetitions that measured it: 101 to 104.
        assertEquals(curve(CalibrationFixtures.TOP_LEFT, 102.5)[10], baseline.meanCurveLeft[10], 1e-9)
    }

    @Test
    fun identical_repetitions_have_no_curve_spread_and_different_ones_do() {
        val same = repetitionBaseline(calibrate(squat, (1..5).map { paired(it, 100.0, 90.0) }))
        assertEquals(0.0, same.curveSpread.left.median, 1e-9)
        assertEquals(5, same.curveSpread.left.count)
        val varied = repetitionBaseline(calibrate(squat, eightSquats()))
        assertTrue(varied.curveSpread.left.median > 0.0)
    }

    // --- Identity ---------------------------------------------------------------------

    @Test
    fun the_baseline_records_its_estimator_and_where_it_came_from() {
        val baseline = repetitionBaseline(calibrate(squat, eightSquats()))
        assertEquals("squat", baseline.calibrationId)
        assertEquals(CalibrationPolicy.PROVISIONAL.version, baseline.estimatorVersion)
        assertEquals(1_000.0, baseline.createdAtEpochMs)
        assertEquals("s1", baseline.sourceSessionId)
    }

    /**
     * CONTRACTS.md section 3: a variant shares its parent's calibration. Every key belongs to its
     * exercise (or its parent); the sit-up's alone carries a revision (DECISIONS.md P3-46).
     */
    @Test
    fun every_variant_uses_its_parents_baseline_and_every_profile_its_own() {
        for (entry in ExerciseLibrary.all) {
            val expected = if (entry is ExerciseVariant) entry.parent.id else entry.id
            assertEquals(expected, CalibrationKey.exerciseIdOf(CalibrationKey.of(entry)), entry.id)
            if (expected != "situp") assertEquals(expected, CalibrationKey.of(entry), entry.id)
        }
        val fromGoblet = repetitionBaseline(calibrate(ExerciseLibrary.gobletSquat, eightSquats()))
        assertEquals("squat", fromGoblet.calibrationId)
    }

    /**
     * DECISIONS.md P3-46: the sit-up changed driver, so its key changed with it. A baseline stored
     * under the old key is never used on a trunk-driven repetition: it is refused, not reinterpreted.
     */
    @Test
    fun a_sit_up_baseline_from_the_hip_angle_driver_is_not_used() {
        assertEquals("situp@trunk", CalibrationKey.of(ExerciseLibrary.situp))
        assertEquals("situp", CalibrationKey.exerciseIdOf("situp@trunk"))
        val old = repetitionBaseline(calibrate(squat, eightSquats()))
        val stale = com.kinesis.core.domain.calibration.RepetitionBaseline(
            calibrationId = "situp", estimatorVersion = old.estimatorVersion, createdAtEpochMs = old.createdAtEpochMs,
            sourceSessionId = old.sourceSessionId, rangeOfMotion = old.rangeOfMotion, peakValue = old.peakValue,
            troughValue = old.troughValue, descentMs = old.descentMs, ascentMs = old.ascentMs, asymmetry = old.asymmetry,
            meanCurveLeft = old.meanCurveLeft, meanCurveRight = old.meanCurveRight, curveSpread = old.curveSpread,
        )
        val anyRep = eightSquats().first()
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            com.kinesis.core.analysis.deviation.DeviationRules.deviations(ExerciseLibrary.situp, anyRep, stale)
        }
    }

    @Test
    fun a_set_of_another_exercise_is_refused() {
        val curlSet = RepetitionSetRecord("bicep_curl", 0.0, alternatingCurls(5, 5))
        assertFailsWith<IllegalArgumentException> {
            Calibration.fromSet(squat, curlSet, createdAtEpochMs = 0.0, sourceSessionId = null)
        }
    }

    @Test
    fun a_repetition_set_and_a_hold_set_each_go_to_their_own_calibrator() {
        val squatSet = RepetitionSetRecord("squat", 0.0, eightSquats())
        assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromSet(squat, squatSet, 0.0, null)).baseline,
        )
        val plankSet = HoldSetRecord("plank", 0.0, listOf(hold()))
        assertIs<HoldBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromSet(plank, plankSet, 0.0, null)).baseline,
        )
    }

    // --- Holds ------------------------------------------------------------------------

    private fun holdOutcome(record: com.kinesis.core.domain.rep.HoldRecord?) =
        Calibration.fromHold(plank, record, createdAtEpochMs = 5_000.0, sourceSessionId = "h1")

    @Test
    fun a_valid_hold_long_enough_calibrates_posture_and_steadiness() {
        val calibrated = assertIs<CalibrationOutcome.Calibrated>(holdOutcome(hold()))
        val baseline: CalibrationBaseline = calibrated.baseline
        val holdBaseline = assertIs<HoldBaseline>(baseline)
        assertEquals("plank", holdBaseline.calibrationId)
        assertEquals(172.0, holdBaseline.meanHeldValue)
        assertEquals(180.0, holdBaseline.targetValue)
        assertEquals(-8.0, holdBaseline.postureOffset)
        assertEquals(2.5, holdBaseline.sway)
        assertEquals(8.0, holdBaseline.meanAbsDeviation)
        assertEquals(20_000.0, holdBaseline.heldMs)
    }

    @Test
    fun a_hold_shorter_than_the_calibration_minimum_is_not_used() {
        val outcome = assertIs<CalibrationOutcome.HoldNotUsable>(holdOutcome(hold(heldMs = 6_000.0)))
        assertEquals(6_000.0, outcome.heldMs)
        assertEquals(10_000.0, outcome.requiredMs)
        assertTrue(outcome.displayText.contains("10"), outcome.displayText)
        assertTrue(outcome.displayText.contains("6"), outcome.displayText)
    }

    @Test
    fun a_hold_that_did_not_count_is_not_used() {
        for (validity in listOf(HoldValidity.TOO_SHORT, HoldValidity.INSUFFICIENT_DATA, HoldValidity.BROKEN)) {
            val outcome = assertIs<CalibrationOutcome.HoldNotUsable>(holdOutcome(hold(validity = validity)))
            assertEquals(validity, outcome.validity)
        }
        assertIs<CalibrationOutcome.HoldNotUsable>(holdOutcome(null))
    }

    /** A hold recorded before steadiness was measured cannot supply it. */
    @Test
    fun a_hold_without_a_measured_sway_is_not_used() {
        assertIs<CalibrationOutcome.HoldNotUsable>(holdOutcome(hold(heldValueSd = Double.NaN)))
        assertIs<CalibrationOutcome.HoldNotUsable>(holdOutcome(hold(meanHeldValue = Double.NaN)))
    }

    // --- The provisional numbers --------------------------------------------------------

    /** DECISIONS.md P3-22 gives the reason for each. Changing one is a new decision. */
    @Test
    fun the_provisional_policy_is_five_to_eight_repetitions_and_ten_seconds() {
        val policy = CalibrationPolicy.PROVISIONAL
        assertEquals(5, policy.minValidRepsPerSide)
        assertEquals(8, policy.maxValidRepsPerSide)
        assertEquals(10_000.0, policy.minHoldMs)
        assertEquals("p6-1", policy.version)
    }
}
