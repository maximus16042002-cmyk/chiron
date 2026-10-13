package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.KinesisEngine
import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.parity.MiniJson
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `computeBaseline` and `analyzeRep` on the JS facade - CONTRACTS.md section 5,
 * which said both wait for P6. They run the same calibrator and the same
 * post-rep analysis as the app, and add nothing of their own (CLAUDE.md
 * section 2), so the tests compare the facade with the core directly.
 */
class FacadeCalibrationTest {

    private fun engine(id: String) = KinesisEngine().also { it.configure("""{"id": "$id"}""") }

    private fun json(reps: List<RepRecord>) = reps.joinToString(",", "[", "]") { EngineJson.rep(it) }

    private fun map(json: String): Map<*, *> = MiniJson.parse(json) as Map<*, *>

    // Fifteen valid repetitions, the shipped policy's most (P3-83): left 100..107 then 100..106, so the
    // median is 103 and the quartiles 101.5 and 105; right is ten below. One aborted repetition after.
    private val squats = (0 until 15).map { paired(it + 1, 100.0 + it % 8, 90.0 + it % 8) } +
        paired(16, 30.0, 30.0, RepValidity.ABORTED)

    @Test
    fun the_facade_computes_the_baseline_the_core_computes() {
        val outcome = map(engine("squat").computeBaseline(json(squats)))
        assertEquals("calibrated", outcome["outcome"])
        val viaFacade = assertIs<RepetitionBaseline>(BaselineJson.read(extractBaseline(engine("squat").computeBaseline(json(squats)))))
        val direct = assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(ExerciseLibrary.squat, squats, Double.NaN, null)).baseline,
        )
        assertEquals(direct.rangeOfMotion, viaFacade.rangeOfMotion)
        assertEquals(direct.descentMs, viaFacade.descentMs)
        assertEquals(direct.asymmetry, viaFacade.asymmetry)
        // The facade has no clock and no session: it says so rather than inventing either.
        assertTrue(viaFacade.createdAtEpochMs.isNaN())
        assertNull(viaFacade.sourceSessionId)
    }

    @Test
    fun too_few_repetitions_is_an_answer_not_an_exception() {
        val outcome = map(engine("squat").computeBaseline(json(squats.take(3))))
        assertEquals("tooFewRepetitions", outcome["outcome"])
        assertEquals(3.0, outcome["validLeft"])
        assertEquals(10.0, outcome["requiredPerSide"])
        assertTrue((outcome["displayText"] as String).startsWith("No baseline yet."))
    }

    @Test
    fun analyze_rep_puts_a_repetition_beside_the_baseline() {
        val baseline = extractBaseline(engine("squat").computeBaseline(json(squats)))
        val result = map(engine("squat").analyzeRep(EngineJson.rep(paired(4, 95.0, 88.0)), baseline))
        assertEquals("repetition", result["kind"])
        assertEquals(4.0, result["repIndex"])
        assertEquals(PostRepAnalysis.VERSION, result["analysisVersion"])
        val comparison = result["comparison"] as Map<*, *>
        val left = comparison["rangeOfMotionLeft"] as Map<*, *>
        assertEquals(95.0, left["measured"])
        assertEquals(103.0, left["baseline"])
        assertNull(comparison["timingSide"])
        // P3-30: left 95 against its usual 103 is Layer 1's one finding here.
        val finding = (result["findings"] as List<*>).single() as Map<*, *>
        assertEquals("deviation", finding["kind"])
        assertEquals("L2_DEVIATION", finding["level"])
        assertEquals("rom_left", finding["metric"])
        assertEquals("BELOW_BASELINE", finding["direction"])
        assertEquals("p8-l1-1", finding["ruleVersion"])
        assertTrue((finding["displayText"] as String).startsWith("Shallower than your usual on the left"))
    }

    @Test
    fun analyze_rep_writes_a_cited_technique_finding_with_its_source() {
        val fast = oneSided(1, Side.RIGHT, 130.0, descentMs = 100.0, ascentMs = 100.0, top = 160.0)
        val result = map(engine("bicep_curl").analyzeRep(EngineJson.rep(fast), "null"))
        val finding = (result["findings"] as List<*>).single() as Map<*, *>
        assertEquals("technique", finding["kind"])
        assertEquals("L3_TECHNIQUE", finding["level"])
        assertEquals("faster_than_acsm_novice_tempo", finding["faultId"])
        assertEquals(com.kinesis.core.analysis.deviation.AcsmTempoRule.SOURCE_REF, finding["sourceRef"])
        assertEquals("p8-acsm-tempo-1", finding["ruleVersion"])
    }

    @Test
    fun analyze_rep_without_a_baseline_compares_nothing() {
        val result = map(engine("bicep_curl").analyzeRep(EngineJson.rep(oneSided(1, Side.LEFT, 100.0)), "null"))
        assertNull(result["baseline"])
        assertNull(result["comparison"])
    }

    @Test
    fun a_hold_calibrates_and_is_analysed_through_the_facade() {
        val holdJson = "[" + EngineJson.hold(hold()) + "]"
        val baselineJson = extractBaseline(engine("plank").computeBaseline(holdJson))
        assertIs<HoldBaseline>(BaselineJson.read(baselineJson))
        val result = map(engine("plank").analyzeRep(EngineJson.hold(hold(meanHeldValue = 169.0)), baselineJson))
        assertEquals("hold", result["kind"])
        val comparison = result["comparison"] as Map<*, *>
        assertEquals(169.0, (comparison["meanHeldValue"] as Map<*, *>)["measured"])
        assertEquals(172.0, (comparison["meanHeldValue"] as Map<*, *>)["baseline"])

        val short = map(engine("plank").computeBaseline("[" + EngineJson.hold(hold(heldMs = 4_000.0)) + "]"))
        assertEquals("holdNotUsable", short["outcome"])
    }

    @Test
    fun a_record_or_baseline_of_the_wrong_shape_is_refused() {
        assertFailsWith<IllegalArgumentException> { engine("squat").computeBaseline("{}") }
        assertFailsWith<IllegalArgumentException> { engine("squat").computeBaseline("[{\"repIndex\": 1}]") }
        assertFailsWith<IllegalArgumentException> { engine("squat").analyzeRep("[]", "null") }
        // A curl baseline cannot analyse a squat.
        val curlBaseline = extractBaseline(
            engine("bicep_curl").computeBaseline(json((0 until 20).map { oneSided(it + 1, if (it % 2 == 0) Side.LEFT else Side.RIGHT, 100.0) })),
        )
        assertFailsWith<IllegalArgumentException> { engine("squat").analyzeRep(EngineJson.rep(paired(1, 90.0, 90.0)), curlBaseline) }
    }

    @Test
    fun nothing_is_computed_before_an_exercise_is_configured() {
        assertFailsWith<IllegalStateException> { KinesisEngine().computeBaseline("[]") }
        assertFailsWith<IllegalStateException> { KinesisEngine().analyzeRep("{}", "null") }
    }

    /** The `baseline` member of a `calibrated` outcome, as JSON text, for passing back in. */
    private fun extractBaseline(outcomeJson: String): String {
        val start = outcomeJson.indexOf("\"baseline\":") + "\"baseline\":".length
        var depth = 0
        for (i in start until outcomeJson.length) {
            when (outcomeJson[i]) {
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return outcomeJson.substring(start, i + 1)
                }
            }
        }
        error("no baseline in $outcomeJson")
    }
}
