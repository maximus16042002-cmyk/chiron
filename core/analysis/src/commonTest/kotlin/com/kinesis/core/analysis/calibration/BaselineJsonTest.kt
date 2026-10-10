package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.PairedKneeFixture
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The serialised baseline - DECISIONS.md P3-22. It is what the app stores and
 * what the facade returns, so the two rules storage has always kept apply to it:
 * left and right never share a field, and `NaN` - not measured - survives as
 * `null` rather than turning into a zero.
 */
class BaselineJsonTest {

    private fun calibrated(outcome: CalibrationOutcome) = assertIs<CalibrationOutcome.Calibrated>(outcome).baseline

    private fun squatBaseline() = assertIs<RepetitionBaseline>(
        calibrated(Calibration.fromRepetitions(ExerciseLibrary.squat, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }, 1_234.5, "session-1")),
    )

    // The paired squat as it was until DECISIONS.md P3-68: the shipped squat no longer pairs.
    private fun pairedBaseline() = assertIs<RepetitionBaseline>(
        calibrated(Calibration.fromRepetitions(PairedKneeFixture.instance, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }, 1_234.5, "session-1")),
    )

    private fun rowBaseline() = assertIs<RepetitionBaseline>(
        calibrated(Calibration.fromRepetitions(ExerciseLibrary.row, (1..6).map { oneSided(it, Side.RIGHT, 70.0 + it, attributed = null) }, 9.0, null)),
    )

    private fun curlBaseline() = assertIs<RepetitionBaseline>(
        calibrated(
            Calibration.fromRepetitions(
                ExerciseLibrary.bicepCurl,
                (0 until 10).map { i -> oneSided(i + 1, if (i % 2 == 0) Side.LEFT else Side.RIGHT, 100.0 + i) },
                2.0,
                "c",
            ),
        ),
    )

    private fun assertSameRepetitionBaseline(expected: RepetitionBaseline, actual: RepetitionBaseline) {
        assertEquals(expected.calibrationId, actual.calibrationId)
        assertEquals(expected.estimatorVersion, actual.estimatorVersion)
        assertEquals(expected.createdAtEpochMs, actual.createdAtEpochMs)
        assertEquals(expected.sourceSessionId, actual.sourceSessionId)
        assertEquals(expected.rangeOfMotion, actual.rangeOfMotion)
        assertEquals(expected.peakValue, actual.peakValue)
        assertEquals(expected.troughValue, actual.troughValue)
        assertEquals(expected.descentMs, actual.descentMs)
        assertEquals(expected.ascentMs, actual.ascentMs)
        assertEquals(expected.asymmetry, actual.asymmetry)
        assertEquals(expected.curveSpread, actual.curveSpread)
        assertSameCurve(expected.meanCurveLeft, actual.meanCurveLeft)
        assertSameCurve(expected.meanCurveRight, actual.meanCurveRight)
    }

    /** Bit for bit, with `NaN` equal to `NaN`: a round trip may not move a value at all. */
    private fun assertSameCurve(expected: DoubleArray, actual: DoubleArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue(expected[i] == actual[i] || (expected[i].isNaN() && actual[i].isNaN()), "point $i: ${expected[i]} vs ${actual[i]}")
        }
    }

    @Test
    fun a_repetition_baseline_survives_the_round_trip_exactly() {
        for (original in listOf(squatBaseline(), rowBaseline(), curlBaseline())) {
            val back = assertIs<RepetitionBaseline>(BaselineJson.read(BaselineJson.write(original)))
            assertSameRepetitionBaseline(original, back)
        }
    }

    @Test
    fun all_three_kinds_of_asymmetry_survive() {
        assertIs<AsymmetryBaseline.Paired>(BaselineJson.read(BaselineJson.write(pairedBaseline())).let { (it as RepetitionBaseline).asymmetry })
        assertIs<AsymmetryBaseline.BetweenSideMedians>((BaselineJson.read(BaselineJson.write(curlBaseline())) as RepetitionBaseline).asymmetry)
        assertEquals(AsymmetryBaseline.NotMeasurable, (BaselineJson.read(BaselineJson.write(rowBaseline())) as RepetitionBaseline).asymmetry)
    }

    /** The row's far arm was never measured, and reloads as never measured - not as zero. */
    @Test
    fun an_unmeasured_side_reloads_unmeasured() {
        val json = BaselineJson.write(rowBaseline())
        assertTrue(!json.contains("NaN"), "JSON has no NaN; it must be null: $json")
        val back = assertIs<RepetitionBaseline>(BaselineJson.read(json))
        assertTrue(back.rangeOfMotion.left.median.isNaN())
        assertEquals(0, back.rangeOfMotion.left.count)
        assertTrue(back.meanCurveLeft.all { it.isNaN() })
    }

    /** Left and right stay two fields in the stored form, as they are two columns in Room. */
    @Test
    fun left_and_right_are_separate_fields() {
        val json = BaselineJson.write(squatBaseline())
        assertTrue(json.contains("\"rangeOfMotion\":{\"left\":{\"median\":103.5"), json)
        assertTrue(json.contains("\"right\":{\"median\":93.5"), json)
    }

    @Test
    fun a_hold_baseline_survives_the_round_trip_exactly() {
        val original = assertIs<HoldBaseline>(
            calibrated(Calibration.fromHold(ExerciseLibrary.plank, CalibrationFixtures.hold(meanHeldValue = 171.25, heldValueSd = 1.875), 77.0, "h")),
        )
        val back = assertIs<HoldBaseline>(BaselineJson.read(BaselineJson.write(original)))
        assertEquals(original.calibrationId, back.calibrationId)
        assertEquals(original.estimatorVersion, back.estimatorVersion)
        assertEquals(original.createdAtEpochMs, back.createdAtEpochMs)
        assertEquals(original.sourceSessionId, back.sourceSessionId)
        assertEquals(original.targetValue, back.targetValue)
        assertEquals(original.meanHeldValue, back.meanHeldValue)
        assertEquals(original.meanAbsDeviation, back.meanAbsDeviation)
        assertEquals(original.sway, back.sway)
        assertEquals(original.heldMs, back.heldMs)
        assertEquals(original.breakCount, back.breakCount)
        assertEquals(original.coverage, back.coverage)
    }

    @Test
    fun a_curve_with_a_gap_keeps_its_gap() {
        val reps = (0 until 5).map { paired(it + 1, 100.0, 90.0) }
        for (rep in reps) rep.resampledLeft[7] = Double.NaN
        val original = assertIs<RepetitionBaseline>(calibrated(Calibration.fromRepetitions(ExerciseLibrary.squat, reps, 0.0, null)))
        assertTrue(original.meanCurveLeft[7].isNaN())
        val back = assertIs<RepetitionBaseline>(BaselineJson.read(BaselineJson.write(original)))
        assertContentEquals(original.meanCurveRight, back.meanCurveRight)
        assertTrue(back.meanCurveLeft[7].isNaN())
    }

    /** A damaged stored baseline is reported, never half-read into something plausible. */
    @Test
    fun malformed_or_unknown_json_is_refused_with_a_reason() {
        for (bad in listOf("", "{", "[]", "{\"kind\":\"weird\"}", "{\"kind\":\"repetition\"}", "not json")) {
            assertFailsWith<IllegalArgumentException>(bad) { BaselineJson.read(bad) }
        }
    }
}
