package com.kinesis.core.analysis.parity

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden-file parity, run on every target: JVM, JS and both Android variants -
 * DECISIONS.md P3-19, CONTRACTS.md section 6, CLAUDE.md sections 5 and 18.
 *
 * The same synthetic landmark sequences go through the same [KinesisEngine]
 * facade on each target, and what comes back must match the golden lines the
 * JVM wrote: rep counts, phases, validities and indices exactly; every
 * continuous value within 1e-9 relative. On the JVM this is also a regression
 * guard - an engine change that moves any output fails it until the golden file
 * is deliberately regenerated (see `EngineParityGoldenWriter`).
 */
class EngineParityTest {

    @Test
    fun a_squat_set_matches_the_golden_file_on_this_target() =
        check("squat", EngineParityGolden.squat, EngineParity.squat())

    /**
     * DECISIONS.md P3-68: the squat counts its near knee only, so the paired path - both sides
     * waiting for each other, one side alone, a lagging side - is run through the lunge, which
     * still pairs, with the squat scenario's movements.
     */
    @Test
    fun a_lunge_set_matches_the_golden_file_on_this_target() =
        check("lunge", EngineParityGolden.lunge, EngineParity.lunge())

    /** DECISIONS.md P3-71: the paired path's parity, through the lateral raise. */
    @Test
    fun a_paired_raise_set_matches_the_golden_file_on_this_target() =
        check("pairedRaise", EngineParityGolden.pairedRaise, EngineParity.pairedRaise())

    /**
     * The paired scenario must really exercise pairing, or its parity proves nothing about it:
     * counted raises measured on both arms, and one measured on the left alone.
     */
    @Test
    fun the_paired_raise_scenario_really_pairs_and_really_goes_one_sided() {
        val records = EngineParity.pairedRaise().filter { it.startsWith("records|") }.joinToString(" ")
        assertTrue(Regex("\"validity\":\"VALID\"").findAll(records).count() >= 3, "fewer than three counted raises")
        assertTrue(records.contains("\"right\":null"), "no raise was measured on one side alone")
    }

    @Test
    fun a_plank_hold_matches_the_golden_file_on_this_target() =
        check("plank", EngineParityGolden.plank, EngineParity.plank())

    // DECISIONS.md P3-22: calibration and the post-rep comparison, through the facade.

    @Test
    fun a_squat_calibration_matches_the_golden_file_on_this_target() =
        check("squatCalibration", EngineParityGolden.squatCalibration, EngineParity.squatCalibration())

    @Test
    fun a_curl_calibration_matches_the_golden_file_on_this_target() =
        check("curlCalibration", EngineParityGolden.curlCalibration, EngineParity.curlCalibration())

    @Test
    fun a_plank_calibration_matches_the_golden_file_on_this_target() =
        check("plankCalibration", EngineParityGolden.plankCalibration, EngineParity.plankCalibration())

    // DECISIONS.md P3-30: Layer 1's findings, their words and levels, through the facade.

    @Test
    fun layer_one_findings_match_the_golden_file_on_this_target() =
        check("layerOneFindings", EngineParityGolden.layerOneFindings, EngineParity.layerOneFindings())

    // DECISIONS.md P3-25: P5's export, over records the facade produced on this target.

    @Test
    fun an_exported_session_matches_the_golden_file_on_this_target() =
        check("sessionExport", EngineParityGolden.sessionExport, EngineParity.sessionExport())

    /** The comparison must be able to fail, or a passing run proves nothing. */
    /** Layer 4's forest, cut and decisions on every target - DECISIONS.md P3-35. */
    @Test
    fun layer_four_scores_match_the_golden_file_on_this_target() =
        check("layerFourScores", EngineParityGolden.layerFourScores, EngineParity.layerFourScores())

    @Test
    fun layer_two_findings_match_the_golden_file_on_this_target() =
        check("layerTwoFindings", EngineParityGolden.layerTwoFindings, EngineParity.layerTwoFindings())

    @Test
    fun the_comparison_sees_a_discrete_and_a_continuous_difference() {
        val golden = EngineParityGolden.squat
        val index = golden.indexOfFirst { it.startsWith("records|") }
        val line = golden[index]
        val countChanged = golden.toMutableList().also {
            it[index] = line.replaceFirst("\"repIndex\":1", "\"repIndex\":2")
        }
        val left = Regex("\"left\":([0-9.]+)").find(line) ?: error("no finite left value in: $line")
        val nudged = left.groupValues[1].toDouble() * (1 + 1e-8)
        val angleNudged = golden.toMutableList().also {
            it[index] = line.replaceRange(left.range, "\"left\":$nudged")
        }
        assertTrue(EngineParity.differences("squat", golden, countChanged).isNotEmpty(), "a changed index passed")
        assertTrue(EngineParity.differences("squat", golden, angleNudged).isNotEmpty(), "a 1e-8 relative change passed")
    }

    private fun check(scenario: String, golden: List<String>, actual: List<String>) {
        assertTrue(
            golden.any {
                it.startsWith("records|") || it.startsWith("finish|[{") ||
                    (it.startsWith("export|") && "\"reps\":[{" in it) ||
                    // DECISIONS.md P3-35: Layer 4 records a fitted model and its scores.
                    (it.startsWith("score|") && "\"score\":" in it) ||
                    // DECISIONS.md P3-43: Layer 2 records the technique faults it found.
                    (it.startsWith("finding|") && "\"faultId\":" in it)
            },
            "the golden $scenario file records nothing",
        )
        val differences = EngineParity.differences(scenario, golden, actual)
        if (differences.isNotEmpty()) {
            fail(
                "$scenario differs from the golden file in ${differences.size} places:\n" +
                    differences.take(20).joinToString("\n"),
            )
        }
    }
}
