package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.curlRep
import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.hold
import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.squatRep
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.BandPosition
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.MetricPlacement
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The personal band and where a record falls against it - DECISIONS.md P3-21.
 *
 * The band is Tukey's fences over the user's own calibration: `q1 - k * IQR` to
 * `q3 + k * IQR`, with `k = 1.5`. That is a statistical convention for "outside
 * the usual spread", not a measured threshold for anything about movement, and
 * [PersonalBands.FENCE_MULTIPLIER] stays provisional until Study 2 has looked at
 * flag rates on MM-Fit.
 */
class PersonalBandsTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    @Test
    fun the_band_is_tukey_s_fences_around_the_quartiles() {
        val summary = RobustSummary(n = 8, median = 100.0, q1 = 95.0, q3 = 105.0)
        val below = PersonalBands.place("rom_deg", Side.LEFT, 79.9, summary, minimumSamples = 5)
        assertEquals(80.0, below.lower) // 95 - 1.5 * 10
        assertEquals(120.0, below.upper) // 105 + 1.5 * 10
        assertEquals(BandPosition.BELOW, below.position)
        assertEquals(BandPosition.WITHIN, PersonalBands.place("rom_deg", Side.LEFT, 80.0, summary, 5).position)
        assertEquals(BandPosition.WITHIN, PersonalBands.place("rom_deg", Side.LEFT, 120.0, summary, 5).position)
        assertEquals(BandPosition.ABOVE, PersonalBands.place("rom_deg", Side.LEFT, 120.1, summary, 5).position)
    }

    @Test
    fun an_unmeasured_value_is_not_placed() {
        val summary = RobustSummary(8, 100.0, 95.0, 105.0)
        assertEquals(BandPosition.NOT_MEASURED, PersonalBands.place("rom_deg", Side.LEFT, Double.NaN, summary, 5).position)
    }

    @Test
    fun a_metric_with_too_few_samples_has_no_band() {
        val thin = RobustSummary(3, 100.0, 95.0, 105.0)
        val placement = PersonalBands.place("rom_deg", Side.RIGHT, 100.0, thin, minimumSamples = 5)
        assertEquals(BandPosition.NO_BASELINE, placement.position)
        assertTrue(placement.lower.isNaN() && placement.upper.isNaN())
    }

    @Test
    fun a_shallow_squat_on_the_left_only_is_placed_below_on_the_left_only() {
        val baseline = squatBaseline()
        val shallowLeft = squatRep(20, left = 40.0, right = 92.0)
        val result = PersonalBands.analyseRep(squat, shallowLeft, baseline)
        assertEquals(BandPosition.BELOW, find(result.placements, PersonalBands.ROM, Side.LEFT).position)
        assertEquals(BandPosition.WITHIN, find(result.placements, PersonalBands.ROM, Side.RIGHT).position)
        // The left/right gap is far outside the user's habitual offset too.
        assertEquals(BandPosition.BELOW, find(result.placements, PersonalBands.ROM_OFFSET, null).position)
    }

    @Test
    fun a_simultaneous_repetition_s_timing_is_placed_once_for_both_sides() {
        val result = PersonalBands.analyseRep(squat, squatRep(20, 103.0, 93.0), squatBaseline())
        val timing = result.placements.filter { it.metric == PersonalBands.DURATION }
        assertEquals(1, timing.size)
        assertEquals(null, timing.single().side)
    }

    @Test
    fun a_curl_places_only_the_arm_that_moved() {
        val reps = (1..5).map { curlRep(2 * it - 1, Side.LEFT, 118.0 + it, 2000.0 + 10 * it) } +
            (1..5).map { curlRep(2 * it, Side.RIGHT, 98.0 + it, 2500.0 + 10 * it) }
        val baseline = assertIs<CalibrationOutcome.Fitted<RepetitionBaseline>>(Calibration.fitRepetitions(curl, reps)).baseline
        val result = PersonalBands.analyseRep(curl, curlRep(11, Side.RIGHT, 100.0, 2530.0), baseline)
        assertEquals(BandPosition.WITHIN, find(result.placements, PersonalBands.ROM, Side.RIGHT).position)
        assertEquals(BandPosition.NOT_MEASURED, find(result.placements, PersonalBands.ROM, Side.LEFT).position)
        assertEquals(BandPosition.WITHIN, find(result.placements, PersonalBands.DURATION, Side.RIGHT).position)
        assertEquals(BandPosition.NOT_MEASURED, find(result.placements, PersonalBands.DURATION, Side.LEFT).position)
        assertTrue(result.placements.none { it.metric == PersonalBands.ROM_OFFSET }, "arms never move together")
    }

    @Test
    fun the_result_names_the_versions_that_produced_it() {
        val result = PersonalBands.analyseRep(squat, squatRep(20, 103.0, 93.0), squatBaseline())
        assertEquals(20, result.repIndex)
        assertEquals(Calibration.METHOD_VERSION, result.baselineVersion)
        assertEquals(PersonalBands.RULE_VERSION, result.ruleVersion)
    }

    @Test
    fun a_hold_is_placed_against_a_hold_baseline() {
        val plank = ExerciseLibrary.plank
        val holds = listOf(30_000.0, 31_000.0, 32_000.0, 33_000.0).map { hold(it) }
        val baseline = assertIs<CalibrationOutcome.Fitted<HoldBaseline>>(Calibration.fitHolds(plank, holds)).baseline
        val result = PersonalBands.analyseHold(plank, hold(10_000.0), baseline)
        assertEquals(BandPosition.BELOW, find(result.placements, PersonalBands.TOTAL_HOLD, null).position)
    }

    private fun squatBaseline(): RepetitionBaseline {
        val reps = (1..8).map { squatRep(it, left = 100.0 + it % 4, right = 90.0 + it % 3) }
        return assertIs<CalibrationOutcome.Fitted<RepetitionBaseline>>(Calibration.fitRepetitions(squat, reps)).baseline
    }

    private fun find(placements: List<MetricPlacement>, metric: String, side: Side?): MetricPlacement =
        placements.single { it.metric == metric && it.side == side }
}
