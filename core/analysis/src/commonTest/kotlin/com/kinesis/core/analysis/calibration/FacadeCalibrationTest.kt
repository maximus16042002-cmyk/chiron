package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.EngineJsonReader
import com.kinesis.core.analysis.KinesisEngine
import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.curlRep
import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.hold
import com.kinesis.core.analysis.calibration.CalibrationGuardTest.Companion.squatRep
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `computeBaseline` and `analyzeRep` through the facade - CONTRACTS.md section 5,
 * DECISIONS.md P3-21. Only primitives and JSON strings cross it.
 */
class FacadeCalibrationTest {

    private fun engine(id: String) = KinesisEngine().also { it.configure("""{"id": "$id"}""") }

    private val squatReps = (1..7).map { squatRep(it, 100.0 + it % 3, 92.0 + it % 2) } +
        squatRep(8, 20.0, 400.0, validity = RepValidity.ABORTED)

    private fun json(reps: List<com.kinesis.core.domain.rep.RepRecord>) =
        reps.joinToString(",", "[", "]") { EngineJson.rep(it) }

    private fun baselineOf(result: String): String {
        val baseline = (EngineJsonReader.parse(result) as Map<*, *>)["baseline"]
        assertTrue(baseline is Map<*, *>, "no baseline in $result")
        // Re-serialise through the reader and writer: the round trip must be exact.
        val start = result.indexOf("\"baseline\":") + "\"baseline\":".length
        return result.substring(start, result.length - 1)
    }

    @Test
    fun a_baseline_fitted_through_the_facade_matches_the_engine_s_own() {
        val result = engine("squat").computeBaseline(json(squatReps))
        assertTrue(result.startsWith("""{"status":"fitted","""), result)
        val direct = Calibration.fitRepetitions(com.kinesis.core.analysis.profile.ExerciseLibrary.squat, squatReps)
        val expected = EngineJson.baseline((direct as CalibrationOutcome.Fitted).baseline)
        assertEquals(expected, baselineOf(result))
    }

    @Test
    fun a_baseline_survives_the_json_round_trip_exactly() {
        val baseline = baselineOf(engine("squat").computeBaseline(json(squatReps)))
        assertEquals(baseline, EngineJson.baseline(EngineJsonReader.baseline(baseline)))

        val curls = (1..5).map { curlRep(it, Side.LEFT, 120.0 + it, 2000.0) }
        val perSide = baselineOf(engine("bicep_curl").computeBaseline(json(curls)))
        assertTrue(perSide.contains(""""kind":"perSide""""), perSide)
        assertTrue(perSide.contains(""""right":{"n":0,"median":null"""), "the idle arm must be absent, not zero: $perSide")
        assertEquals(perSide, EngineJson.baseline(EngineJsonReader.baseline(perSide)))
    }

    @Test
    fun too_few_records_says_so() {
        val result = engine("squat").computeBaseline(json(squatReps.take(3)))
        assertEquals(
            """{"status":"insufficient","recordsOffered":3,"bestSideSamples":3,"minimumSamples":${Calibration.MIN_REPETITIONS}}""",
            result,
        )
    }

    @Test
    fun a_repetition_is_placed_against_the_baseline() {
        val engine = engine("squat")
        val baseline = baselineOf(engine.computeBaseline(json(squatReps)))
        val result = engine.analyzeRep(EngineJson.rep(squatRep(9, 40.0, 93.0)), baseline)
        val left = placement(result, "rom_deg", "LEFT")
        assertEquals(40.0, left["value"])
        assertEquals("BELOW", left["position"])
        assertTrue(result.contains(""""ruleVersion":"${PersonalBands.RULE_VERSION}""""), result)
    }

    @Test
    fun a_variant_uses_its_parent_s_baseline_through_the_facade() {
        val baseline = baselineOf(engine("squat").computeBaseline(json(squatReps)))
        val result = engine("goblet_squat").analyzeRep(EngineJson.rep(squatRep(9, 101.0, 93.0)), baseline)
        assertTrue(result.contains(""""exerciseId":"squat""""), result)
    }

    @Test
    fun another_exercise_s_baseline_is_refused() {
        val baseline = baselineOf(engine("squat").computeBaseline(json(squatReps)))
        assertFailsWith<IllegalArgumentException> {
            engine("pushup").analyzeRep(EngineJson.rep(squatRep(9, 101.0, 93.0)), baseline)
        }
    }

    @Test
    fun a_hold_is_calibrated_and_placed_as_a_hold() {
        val engine = engine("plank")
        val holds = listOf(30_000.0, 31_000.0, 33_000.0).joinToString(",", "[", "]") { EngineJson.hold(hold(it)) }
        val baseline = baselineOf(engine.computeBaseline(holds))
        assertTrue(baseline.startsWith("""{"kind":"hold","exerciseId":"plank""""), baseline)
        val result = engine.analyzeRep(EngineJson.hold(hold(31_000.0)), baseline)
        val held = placement(result, "total_hold_ms", null)
        assertEquals(31_000.0, held["value"])
        assertEquals("WITHIN", held["position"])
    }

    /** Parsed rather than matched as text: JavaScript writes `40.0` as `40`. */
    private fun placement(result: String, metric: String, side: String?): Map<*, *> {
        val placements = (EngineJsonReader.parse(result) as Map<*, *>)["placements"] as List<*>
        return placements.map { it as Map<*, *> }.single { it["metric"] == metric && it["side"] == side }
    }

    @Test
    fun nothing_is_fitted_before_an_exercise_is_chosen() {
        assertFailsWith<IllegalStateException> { KinesisEngine().computeBaseline("[]") }
    }

    @Test
    fun malformed_records_are_refused_by_name() {
        val error = assertFailsWith<IllegalArgumentException> {
            engine("squat").computeBaseline("""[{"repIndex":1}]""")
        }
        assertTrue(error.message!!.contains("\""), error.message)
    }
}
