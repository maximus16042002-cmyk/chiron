package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.PairedKneeFixture
import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.analysis.verifier.ExerciseVerifier
import com.kinesis.core.analysis.verifier.VerificationResult
import com.kinesis.core.analysis.verifier.VerifierParityGolden
import com.kinesis.core.domain.analysis.BaselineReference
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.finding.Deviation
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
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(profile, reps, 1_000.0, "s", CalibrationFixtures.SMALL_SAMPLE_POLICY)).baseline,
        )

    private val squatBaseline by lazy { baseline(squat, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

    // The paired squat as it was until DECISIONS.md P3-68, for the paired asymmetry rule.
    private val pairedKnee = PairedKneeFixture.instance
    private val pairedBaseline by lazy { baseline(pairedKnee, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

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
        val findings = PostRepAnalysis.analyse(pairedKnee, paired(9, 95.0, 93.5), pairedBaseline).findings
        assertEquals(setOf("rom_left", "asymmetry_lr"), findings.map { (it as Deviation).metric }.toSet())
        assertEquals("p8-layer1-1", PostRepAnalysis.VERSION)
    }

    /**
     * DECISIONS.md P3-74, the owner's decision: Layer 2's cited rules run on the post-rep path again,
     * for Tier A. This lateral raise breaks the raise rule, and its result carries the fault with
     * its source.
     */
    @Test
    fun a_tier_a_repetition_that_breaks_a_layer_two_rule_carries_its_cited_fault() {
        val raise = ExerciseLibrary.lateralRaise
        val findings = PostRepAnalysis.analyse(raise, aboveShoulder(), null).findings
        val fault = findings.filterIsInstance<TechniqueFault>().single { it.faultId == TechniqueRules.RAISED_ABOVE_SHOULDER_LEVEL }
        assertEquals(TechniqueRules.SOURCE_LATERAL_RAISE, fault.sourceRef)
    }

    /** The same movement under a Tier B profile (the seated shoulder press) carries no Level 3 finding. */
    @Test
    fun a_tier_b_repetition_never_carries_a_technique_fault() {
        val findings = PostRepAnalysis.analyse(ExerciseLibrary.shoulderPress, aboveShoulder(), null).findings
        assertTrue(findings.none { it is TechniqueFault }, "a Tier B result carried a Level 3 finding: $findings")
    }

    private fun aboveShoulder() = RepRecord(
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

    /**
     * P3-59: the exercise verifier is built and evaluated, and wired to nothing. A push-up's
     * movement under a sit-up selection is a mismatch the verifier itself reports, and the
     * post-rep analysis must not pass it on.
     */
    @Test
    fun a_movement_the_verifier_calls_a_mismatch_carries_no_verifier_finding() {
        val pushup = VerifierParityGolden.VECTORS.first { it.trueExercise == "pushup" }
        val situp = ExerciseLibrary.situp
        val context = MovementContext(pushup.rawFeatures)
        assertIs<VerificationResult.Mismatch>(ExerciseVerifier().verify(situp.id, context))
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
            movementContext = context,
        )
        val findings = PostRepAnalysis.analyse(situp, rep, null).findings
        assertTrue(
            findings.none { it is Deviation && it.metric == ExerciseVerifier.METRIC_NAME },
            "a verifier warning reached the result: " + findings,
        )
    }
}
