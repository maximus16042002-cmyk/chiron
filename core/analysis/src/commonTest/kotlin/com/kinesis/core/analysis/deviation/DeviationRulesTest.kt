package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.PairedKneeFixture
import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationFixtures.TOP_LEFT
import com.kinesis.core.analysis.calibration.CalibrationFixtures.TOP_RIGHT
import com.kinesis.core.analysis.calibration.CalibrationFixtures.curve
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Layer 1's personal deviation rules - DECISIONS.md P3-30. Level 2 only, per side,
 * against the user's own baseline, worded as personal.
 */
class DeviationRulesTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    private fun baseline(profile: RepetitionExercise, reps: List<RepRecord>) = assertIs<RepetitionBaseline>(
        assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(profile, reps, 0.0, null)).baseline,
    )

    /**
     * Left 100..107 (median 103.5, IQR 3.5 - a robust SD of 2.6, under the 3° floor),
     * right 90..97 (median 93.5); every repetition 1.0 s down, 0.8 s up; left minus
     * right always 10.
     */
    private val squatBaseline by lazy { baseline(squat, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

    // The paired squat as it was until DECISIONS.md P3-68, for the paired asymmetry rule.
    private val pairedKnee = PairedKneeFixture.instance
    private val pairedBaseline by lazy { baseline(pairedKnee, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

    /** Left arm 0.9 s bending, 0.7 s straightening; right arm 1.2 s and 1.0 s. No spread: the floor rules. */
    private val curlBaseline by lazy {
        baseline(
            curl,
            (0 until 10).map { i ->
                if (i % 2 == 0) oneSided(i + 1, Side.LEFT, 110.0, descentMs = 900.0, ascentMs = 700.0)
                else oneSided(i + 1, Side.RIGHT, 100.0, descentMs = 1_200.0, ascentMs = 1_000.0)
            },
        )
    }

    private fun metrics(findings: List<Deviation>) = findings.map { it.metric }.toSet()

    // --- Range of motion ---------------------------------------------------------------

    @Test
    fun a_shallower_side_is_flagged_on_that_side_alone() {
        // Left 95 against 103.5: (95 - 103.5) / 3 = -2.83. Right 93.5 is its own median.
        val findings = DeviationRules.evaluate(squat, paired(9, 95.0, 93.5), squatBaseline)
        val rom = findings.filter { it.metric.startsWith("rom_") }
        assertEquals(listOf("rom_left"), rom.map { it.metric })
        assertEquals(DeviationDirection.BELOW_BASELINE, rom.single().direction)
        assertTrue(rom.single().displayText.startsWith("Shallower than your usual on the left"), rom.single().displayText)
    }

    @Test
    fun each_side_is_judged_against_its_own_baseline_never_the_others() {
        // 95 on the right is 1.5 above the right's median; on the left it would be far below.
        val deviations = DeviationRules.deviations(squat, paired(9, 103.5, 95.0), squatBaseline)
        assertEquals(0.0, deviations.rangeOfMotion.left, 1e-12)
        assertEquals(0.5, deviations.rangeOfMotion.right, 1e-12)
    }

    @Test
    fun within_the_band_nothing_is_said() {
        assertEquals(emptyList(), DeviationRules.evaluate(squat, paired(9, 102.0, 92.0), squatBaseline))
    }

    @Test
    fun a_repetition_further_than_usual_is_said_to_be_further() {
        val rom = DeviationRules.evaluate(squat, paired(9, 112.0, 93.5), squatBaseline).filter { it.metric == "rom_left" }
        assertEquals(DeviationDirection.ABOVE_BASELINE, rom.single().direction)
        assertTrue(rom.single().displayText.startsWith("Further than your usual on the left"), rom.single().displayText)
    }

    @Test
    fun the_floor_stops_a_tiny_spread_from_flagging_an_ordinary_repetition() {
        val identical = baseline(squat, (1..8).map { paired(it, 100.0, 90.0) })
        // 3° short: one floor unit, not infinitely many IQRs.
        assertEquals(-1.0, DeviationRules.deviations(squat, paired(9, 97.0, 90.0), identical).rangeOfMotion.left, 1e-12)
        assertEquals(emptyList(), DeviationRules.evaluate(squat, paired(9, 97.0, 90.0), identical))
        // 7° short: flagged (it also moves the curve and the left-right offset, flagged in their own right).
        assertTrue("rom_left" in metrics(DeviationRules.evaluate(squat, paired(9, 93.0, 90.0), identical)))
    }

    // --- Tempo -------------------------------------------------------------------------

    @Test
    fun a_squat_lifts_on_the_way_up_so_a_quick_ascent_is_a_quick_lift() {
        val lift = DeviationRules.evaluate(squat, paired(9, 103.5, 93.5, descentMs = 1_000.0, ascentMs = 500.0), squatBaseline)
        assertEquals(setOf("lifting_time"), metrics(lift))
        assertEquals(DeviationDirection.BELOW_BASELINE, lift.single().direction)
        assertTrue(lift.single().displayText.startsWith("Faster than your usual lifting"), lift.single().displayText)

        val lower = DeviationRules.evaluate(squat, paired(9, 103.5, 93.5, descentMs = 600.0, ascentMs = 800.0), squatBaseline)
        assertEquals(setOf("lowering_time"), metrics(lower))
        assertTrue(lower.single().displayText.startsWith("Faster than your usual lowering"), lower.single().displayText)
    }

    @Test
    fun a_slower_phase_than_usual_is_said_to_be_slower() {
        val slow = DeviationRules.evaluate(squat, paired(9, 103.5, 93.5, descentMs = 1_000.0, ascentMs = 1_300.0), squatBaseline)
        assertEquals(DeviationDirection.ABOVE_BASELINE, slow.single().direction)
        assertTrue(slow.single().displayText.startsWith("Slower than your usual lifting"), slow.single().displayText)
    }

    @Test
    fun an_alternating_curl_is_timed_against_its_own_arm() {
        // 1.15 s bending: ordinary for the right arm (usual 1.2 s), slow for the left (usual 0.9 s).
        val right = DeviationRules.evaluate(curl, oneSided(11, Side.RIGHT, 100.0, descentMs = 1_150.0, ascentMs = 1_000.0), curlBaseline)
        assertEquals(emptyList(), right)
        val left = DeviationRules.evaluate(curl, oneSided(11, Side.LEFT, 110.0, descentMs = 1_150.0, ascentMs = 700.0), curlBaseline)
        assertEquals(setOf("lifting_time"), metrics(left))
        assertEquals(DeviationDirection.ABOVE_BASELINE, left.single().direction)
    }

    // --- Asymmetry ---------------------------------------------------------------------

    @Test
    fun paired_asymmetry_is_judged_against_the_habitual_offset() {
        // Left minus right 18.5 against a habitual 10 with no spread: (18.5 - 10) / 3 = 2.83.
        val deviations = DeviationRules.deviations(pairedKnee, paired(9, 103.5, 85.0), pairedBaseline)
        assertEquals(8.5 / 3.0, deviations.asymmetry, 1e-12)
        val asymmetry = DeviationRules.evaluate(pairedKnee, paired(9, 103.5, 85.0), pairedBaseline).filter { it.metric == "asymmetry_lr" }
        assertEquals(DeviationDirection.ABOVE_BASELINE, asymmetry.single().direction)
    }

    @Test
    fun a_habitual_difference_between_the_sides_is_not_an_asymmetry_finding() {
        // Left minus right is 10, exactly the habitual offset.
        assertEquals(0.0, DeviationRules.deviations(pairedKnee, paired(9, 103.5, 93.5), pairedBaseline).asymmetry, 1e-12)
    }

    @Test
    fun an_alternating_exercise_has_no_per_repetition_asymmetry() {
        val deviations = DeviationRules.deviations(curl, oneSided(11, Side.LEFT, 60.0), curlBaseline)
        assertTrue(deviations.asymmetry.isNaN())
        assertTrue("asymmetry_lr" !in metrics(DeviationRules.evaluate(curl, oneSided(11, Side.LEFT, 60.0), curlBaseline)))
    }

    // --- Consistency -------------------------------------------------------------------

    @Test
    fun a_curve_unlike_the_usual_one_is_flagged_and_the_usual_one_is_not() {
        val usual = paired(9, 103.5, 93.5)
        assertTrue(DeviationRules.deviations(squat, usual, squatBaseline).consistency.left < 2.0)

        // Same range of motion, a different path: straight down in the first quarter, then held.
        val odd = DoubleArray(64) { i -> TOP_LEFT - 103.5 * sin(PI / 2 * min(1.0, 4.0 * i / 63)) }
        val rep = RepRecord(
            repIndex = 9, attributedSide = null, startMs = 0.0, endMs = 1_800.0, validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(103.5, 93.5),
            peakValue = BilateralSignal(TOP_LEFT, TOP_RIGHT),
            troughValue = BilateralSignal(TOP_LEFT - 103.5, TOP_RIGHT - 93.5),
            descentMs = 1_000.0, ascentMs = 800.0,
            resampledLeft = odd, resampledRight = curve(TOP_RIGHT, 93.5),
        )
        val shape = DeviationRules.evaluate(squat, rep, squatBaseline).filter { it.metric.startsWith("shape_") }
        assertEquals(listOf("shape_left"), shape.map { it.metric })
        assertEquals(DeviationDirection.ABOVE_BASELINE, shape.single().direction)
    }

    /**
     * The baseline's curve distance includes depth (P3-22's `curveSpread` is on raw
     * curves), so a shallower repetition is also further from the usual curve. One
     * message per side: the range of motion speaks, and consistency stays quiet there.
     */
    @Test
    fun consistency_is_not_said_where_range_of_motion_already_was() {
        val shallow = paired(9, 95.0, 93.5)
        assertTrue(DeviationRules.deviations(squat, shallow, squatBaseline).consistency.left > 2.0, "the number is still there")
        val metrics = metrics(DeviationRules.evaluate(squat, shallow, squatBaseline))
        assertTrue("rom_left" in metrics && "shape_left" !in metrics, metrics.toString())
    }

    // --- What is judged, and how it is said --------------------------------------------

    @Test
    fun a_repetition_that_did_not_count_is_not_judged() {
        val aborted = paired(9, 60.0, 50.0, RepValidity.ABORTED, descentMs = 200.0, ascentMs = 200.0)
        assertEquals(emptyList(), DeviationRules.evaluate(squat, aborted, squatBaseline))
    }

    @Test
    fun every_finding_is_level_2_versioned_and_says_nothing_about_correctness() {
        val findings = DeviationRules.evaluate(squat, paired(9, 80.0, 60.0, descentMs = 300.0, ascentMs = 2_000.0), squatBaseline) +
            DeviationRules.evaluate(curl, oneSided(11, Side.LEFT, 150.0, descentMs = 300.0, ascentMs = 2_000.0), curlBaseline)
        assertTrue(findings.size >= 6, "enough findings to check: $findings")
        val forbidden = Regex("wrong|incorrect|unsafe|injur|error|bad|diagnos|correct", RegexOption.IGNORE_CASE)
        for (finding in findings) {
            assertEquals(ClaimLevel.L2_DEVIATION, finding.level)
            assertEquals("p8-l1-1", finding.ruleVersion)
            assertTrue(!forbidden.containsMatchIn(finding.displayText), finding.displayText)
            assertTrue(finding.displayText.contains("usual"), finding.displayText)
        }
    }

    @Test
    fun the_policy_is_pinned() {
        val policy = DeviationPolicy.PROVISIONAL
        assertEquals("p8-l1-1", policy.version)
        assertEquals(2.0, policy.threshold)
        assertEquals(3.0, policy.rangeOfMotionFloorDeg)
        assertEquals(70.0, policy.timeFloorMs)
        assertEquals(3.0, policy.asymmetryFloorDeg)
        assertEquals(2.0, policy.consistencyFloorDeg)
    }
}
