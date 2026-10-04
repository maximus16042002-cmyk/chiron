package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.analysis.verifier.VerifierParityGolden
import com.kinesis.core.domain.analysis.BaselineReference
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
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

    /**
     * The guard for this phase's boundary. P3-22's version said no finding of any level
     * may come out of the post-rep analysis until P8's rules existed, and that P8 would
     * replace it deliberately. This is that replacement (DECISIONS.md P3-30). For every
     * exercise in the library:
     * - an ordinary repetition says nothing, with or without a baseline - no Level 2
     *   without a baseline, and the cited Level 3 tempo rule only past its threshold;
     * - an ordinary repetition against its own baseline says nothing either;
     * - a hold says nothing: Layer 1 judges repetitions.
     *
     * That Tier B and C never produce Level 3, even for a very fast repetition, is
     * `AcsmTempoRuleTest.only_tier_a_can_say_it_and_it_needs_no_baseline`.
     */
    @Test
    fun p8_says_nothing_about_an_ordinary_repetition_or_a_hold() {
        for (entry in ExerciseLibrary.all) {
            when (entry) {
                is RepetitionExercise -> {
                    // 1.0 s down and 0.8 s up: well within ACSM's tempo for every profile.
                    assertEquals(emptyList(), PostRepAnalysis.analyse(entry, paired(1, 100.0, 90.0), null).findings, entry.id)
                    if (CalibrationKey.of(entry) == "squat") {
                        // The baseline's own medians: no deviation on any feature.
                        assertEquals(emptyList(), PostRepAnalysis.analyse(entry, paired(1, 103.5, 93.5), squatBaseline).findings, entry.id)
                    }
                }
                is IsometricExercise ->
                    assertTrue(PostRepAnalysis.analyse(entry, hold(), null).findings.isEmpty(), entry.id)
            }
        }
    }

    @Test
    fun a_repetition_unlike_its_baseline_carries_layer_ones_findings() {
        // Left 8.5° short of its usual: shallower, and left-minus-right 8.5° off its habitual 10.
        val findings = PostRepAnalysis.analyse(squat, paired(9, 95.0, 93.5), squatBaseline).findings
        assertEquals(setOf("rom_left", "asymmetry_lr"), findings.map { (it as Deviation).metric }.toSet())
        assertEquals("p8-layer1-1", PostRepAnalysis.VERSION)
    }

    @Test
    fun tier_a_repetition_with_technique_fault_produces_level_3_technique_fault_with_citation() {
        val raise = ExerciseLibrary.lateralRaise
        // Arm raised above shoulder level (trough > 90° + 20° = 110°)
        val rep = RepRecord(
            repIndex = 1,
            attributedSide = null,
            startMs = 0.0,
            endMs = 2_000.0,
            validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(104.0, 78.0),
            peakValue = BilateralSignal(20.0, 22.0),
            troughValue = BilateralSignal(124.0, 100.0),
            descentMs = 1_000.0,
            ascentMs = 1_000.0,
            resampledLeft = DoubleArray(64) { 20.0 },
            resampledRight = DoubleArray(64) { 22.0 },
        )
        val result = PostRepAnalysis.analyse(raise, rep, null)
        val fault = assertIs<TechniqueFault>(result.findings.single { it is TechniqueFault })
        assertEquals(ClaimLevel.L3_TECHNIQUE, fault.level)
        assertEquals(TechniqueRules.RAISED_ABOVE_SHOULDER_LEVEL, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_LATERAL_RAISE, fault.sourceRef)
        assertTrue(fault.sourceRef.isNotEmpty())
        assertTrue(fault.displayText.contains("raised above shoulder level"))
    }

    @Test
    fun verifier_mismatch_produces_level_2_deviation_warning() {
        // Sample 0 from golden is a bicep curl. We test analyzing it under squat profile.
        val curlVector = VerifierParityGolden.VECTORS[0]
        assertEquals("bicep_curl", curlVector.trueExercise)
        val rep = RepRecord(
            repIndex = 1,
            attributedSide = null,
            startMs = 0.0,
            endMs = 2_000.0,
            validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(90.0, 90.0),
            peakValue = BilateralSignal(170.0, 170.0),
            troughValue = BilateralSignal(80.0, 80.0),
            descentMs = 1_000.0,
            ascentMs = 1_000.0,
            resampledLeft = DoubleArray(64) { 170.0 },
            resampledRight = DoubleArray(64) { 170.0 },
            movementContext = MovementContext(curlVector.rawFeatures),
        )
        val result = PostRepAnalysis.analyse(squat, rep, null)
        val warning = assertIs<Deviation>(result.findings.single { it is Deviation && it.metric == "exercise_verifier" })
        assertEquals(ClaimLevel.L2_DEVIATION, warning.level)
        assertEquals(DeviationDirection.UNUSUAL_FOR_YOU, warning.direction)
        assertTrue(warning.displayText.contains("This looks like a different exercise"))
        assertTrue(warning.displayText.contains("Bicep Curl"))
    }

    @Test
    fun verifier_match_and_unverified_produce_no_verifier_findings() {
        val curlVector = VerifierParityGolden.VECTORS[0]
        val rep = RepRecord(
            repIndex = 1,
            attributedSide = null,
            startMs = 0.0,
            endMs = 2_000.0,
            validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(90.0, 90.0),
            peakValue = BilateralSignal(170.0, 170.0),
            troughValue = BilateralSignal(80.0, 80.0),
            descentMs = 1_000.0,
            ascentMs = 1_000.0,
            resampledLeft = DoubleArray(64) { 170.0 },
            resampledRight = DoubleArray(64) { 170.0 },
            movementContext = MovementContext(curlVector.rawFeatures),
        )
        // 1. Matches selected exercise (bicep_curl) -> no warning
        val matchResult = PostRepAnalysis.analyse(curl, rep, null)
        assertTrue(matchResult.findings.none { it is Deviation && it.metric == "exercise_verifier" })

        // 2. Unverified exercise (e.g. gluteBridge) -> no warning
        val unverified = ExerciseLibrary.gluteBridge
        val unverifiedResult = PostRepAnalysis.analyse(unverified, rep, null)
        assertTrue(unverifiedResult.findings.none { it is Deviation && it.metric == "exercise_verifier" })
    }
}
