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

    @Test
    fun a_calibration_matches_the_golden_file_on_this_target() {
        val golden = EngineParityGolden.calibration
        assertTrue(golden.any { it.startsWith("baseline|0|{\"status\":\"fitted\"") }, "the golden calibration fitted nothing")
        assertTrue(golden.any { it.startsWith("analysis|") && (it.contains("\"BELOW\"") || it.contains("\"ABOVE\"")) }, "the golden calibration placed nothing outside the band")
        check("calibration", golden, EngineParity.calibration())
    }

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
        assertTrue(golden.any { it.startsWith("records|") || it.startsWith("finish|[{") }, "the golden $scenario file records nothing")
        val differences = EngineParity.differences(scenario, golden, actual)
        if (differences.isNotEmpty()) {
            fail(
                "$scenario differs from the golden file in ${differences.size} places:\n" +
                    differences.take(20).joinToString("\n"),
            )
        }
    }
}
