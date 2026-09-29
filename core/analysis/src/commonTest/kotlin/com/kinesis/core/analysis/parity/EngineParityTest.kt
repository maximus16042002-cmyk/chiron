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
            golden.any { it.startsWith("records|") || it.startsWith("finish|[{") || (it.startsWith("export|") && "\"reps\":[{" in it) },
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
