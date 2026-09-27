package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.analysis.BaselineReference
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AnalysisResult` as P6 builds it - DECISIONS.md P3-22: a record's values beside
 * the baseline's, per side, as numbers, and nothing else.
 */
class PostRepAnalysisTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    private fun baseline(profile: RepetitionExercise, reps: List<com.kinesis.core.domain.rep.RepRecord>) =
        assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(profile, reps, 1_000.0, "s")).baseline,
        )

    private val squatBaseline by lazy { baseline(squat, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

    private val curlBaseline by lazy {
        baseline(
            curl,
            (0 until 10).map { i ->
                if (i % 2 == 0) oneSided(i + 1, Side.LEFT, 110.0, descentMs = 900.0, ascentMs = 700.0)
                else oneSided(i + 1, Side.RIGHT, 100.0, descentMs = 1_200.0, ascentMs = 1_000.0)
            },
        )
    }

    @Test
    fun a_repetition_is_shown_beside_each_sides_own_baseline() {
        val result = PostRepAnalysis.analyse(squat, paired(3, 95.0, 88.0), squatBaseline)
        val comparison = assertNotNull(result.comparison)
        assertEquals(95.0, comparison.rangeOfMotionLeft.measured)
        assertEquals(103.5, comparison.rangeOfMotionLeft.baseline)
        assertEquals(3.5, comparison.rangeOfMotionLeft.baselineSpread)
        assertEquals(88.0, comparison.rangeOfMotionRight.measured)
        assertEquals(93.5, comparison.rangeOfMotionRight.baseline)
        assertEquals(3, result.repIndex)
        assertEquals("squat", result.exerciseId)
        assertEquals(PostRepAnalysis.VERSION, result.analysisVersion)
        assertEquals(BaselineReference.of(squatBaseline), result.baseline)
    }

    @Test
    fun a_repetition_both_sides_performed_is_timed_against_the_repetition_baseline() {
        val comparison = assertNotNull(PostRepAnalysis.analyse(squat, paired(1, 100.0, 90.0, descentMs = 1_300.0), squatBaseline).comparison)
        assertNull(comparison.timingSide)
        assertEquals(1_300.0, comparison.descentMs.measured)
        assertEquals(1_000.0, comparison.descentMs.baseline)
    }

    @Test
    fun an_alternating_repetition_is_timed_against_its_own_arm() {
        val rep = oneSided(4, Side.RIGHT, 97.0, descentMs = 1_150.0, ascentMs = 990.0)
        val comparison = assertNotNull(PostRepAnalysis.analyse(curl, rep, curlBaseline).comparison)
        assertEquals(Side.RIGHT, comparison.timingSide)
        assertEquals(1_150.0, comparison.descentMs.measured)
        assertEquals(1_200.0, comparison.descentMs.baseline)
        assertEquals(1_000.0, comparison.ascentMs.baseline)
        // The idle arm was not measured in this repetition, and stays unmeasured.
        assertTrue(comparison.rangeOfMotionLeft.measured.isNaN())
        assertEquals(110.0, comparison.rangeOfMotionLeft.baseline)
    }

    @Test
    fun without_a_baseline_there_is_nothing_to_compare_against() {
        val result = PostRepAnalysis.analyse(squat, paired(1, 100.0, 90.0), null)
        assertNull(result.comparison)
        assertNull(result.baseline)
    }

    @Test
    fun a_variant_is_compared_against_its_parents_baseline() {
        val result = PostRepAnalysis.analyse(ExerciseLibrary.gobletSquat, paired(1, 101.0, 91.0), squatBaseline)
        assertEquals("goblet_squat", result.exerciseId)
        assertEquals("squat", result.baseline?.calibrationId)
    }

    @Test
    fun a_baseline_of_another_exercise_is_refused() {
        assertFailsWith<IllegalArgumentException> {
            PostRepAnalysis.analyse(curl, oneSided(1, Side.LEFT, 100.0), squatBaseline)
        }
    }

    @Test
    fun a_hold_is_shown_beside_the_calibration_hold() {
        val plank = ExerciseLibrary.plank
        val holdBaseline = assertIs<HoldBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromHold(plank, hold(), 0.0, null)).baseline,
        )
        val result = PostRepAnalysis.analyse(plank, hold(meanHeldValue = 169.0, heldValueSd = 3.5, heldMs = 30_000.0), holdBaseline)
        val comparison = assertNotNull(result.comparison)
        assertEquals(169.0, comparison.meanHeldValue.measured)
        assertEquals(172.0, comparison.meanHeldValue.baseline)
        assertEquals(3.5, comparison.sway.measured)
        assertEquals(2.5, comparison.sway.baseline)
        assertEquals(30_000.0, comparison.heldMs.measured)
        assertEquals(20_000.0, comparison.heldMs.baseline)
    }

    // P6's `p6_makes_no_claim_beyond_numbers_for_any_exercise` was replaced, as it said
    // it would be, by DeviationRulesTest's library-wide guard - DECISIONS.md P3-24.
}
