package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationFixtures.hold
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.finding.Finding
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P8 Layer 1 - the deviation rules, DECISIONS.md P3-23.
 *
 * Every finding here is Level 2: a departure from this user's own calibration,
 * never a statement about correct movement. What each test pins is a way the
 * rules could be silently wrong: the wrong side named, an idle arm flagged, a
 * judgement on a repetition that never completed, a perfectly consistent
 * calibration turning every later repetition into a finding.
 */
class DeviationRulesTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    private fun baseline(profile: RepetitionExercise, reps: List<RepRecord>) =
        assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(profile, reps, 1_000.0, "s")).baseline,
        )

    /** Left 100..107 (median 103.5, IQR 3.5), right 90..97, left always 10 more. */
    private val squatBaseline by lazy { baseline(squat, (0 until 8).map { paired(it + 1, 100.0 + it, 90.0 + it) }) }

    /** Eight identical repetitions: every spread is zero. */
    private val flatBaseline by lazy { baseline(squat, (1..8).map { paired(it, 100.0, 90.0) }) }

    private val curlBaseline by lazy {
        baseline(
            curl,
            (0 until 10).map { i ->
                if (i % 2 == 0) oneSided(i + 1, Side.LEFT, 110.0, descentMs = 900.0, ascentMs = 700.0)
                else oneSided(i + 1, Side.RIGHT, 100.0, descentMs = 1_200.0, ascentMs = 1_000.0)
            },
        )
    }

    private fun squatFindings(rep: RepRecord, base: RepetitionBaseline = squatBaseline) =
        PostRepAnalysis.analyse(squat, rep, base).findings

    private fun List<Finding>.metrics() = map { assertIs<Deviation>(it).metric }.toSet()

    private fun List<Finding>.on(metric: String) = filterIsInstance<Deviation>().single { it.metric == metric }

    @Test
    fun a_repetition_inside_the_users_usual_spread_says_nothing() {
        assertEquals(emptyList(), squatFindings(paired(1, 104.0, 93.0)))
    }

    @Test
    fun a_shallow_side_is_named_and_the_other_side_is_left_alone() {
        val findings = squatFindings(paired(1, 80.0, 93.0))
        val left = findings.on("rom_left")
        assertEquals(DeviationDirection.BELOW_BASELINE, left.direction)
        assertTrue(left.displayText.startsWith("Left range of motion"), left.displayText)
        assertFalse("rom_right" in findings.metrics())
    }

    @Test
    fun the_right_side_is_reported_as_the_right_side() {
        val findings = squatFindings(paired(1, 104.0, 110.0))
        val right = findings.on("rom_right")
        assertEquals(DeviationDirection.ABOVE_BASELINE, right.direction)
        assertTrue(right.displayText.startsWith("Right range of motion"), right.displayText)
        assertFalse("rom_left" in findings.metrics())
    }

    @Test
    fun a_band_is_never_narrower_than_its_floor_so_a_flat_calibration_does_not_flag_everything() {
        // Spread zero; 3% off is inside the 5% floor, 8% off is outside it.
        assertFalse("rom_left" in squatFindings(paired(1, 97.0, 90.0), flatBaseline).metrics())
        assertTrue("rom_left" in squatFindings(paired(1, 92.0, 90.0), flatBaseline).metrics())
    }

    @Test
    fun a_repetition_that_did_not_complete_is_not_judged() {
        for (validity in RepValidity.entries.filter { it != RepValidity.VALID }) {
            assertEquals(emptyList(), squatFindings(paired(1, 40.0, 30.0, validity = validity)), validity.name)
        }
    }

    @Test
    fun slow_phases_are_flagged_against_the_repetitions_own_timing_baseline() {
        val findings = squatFindings(paired(1, 104.0, 93.0, descentMs = 1_600.0, ascentMs = 800.0))
        assertEquals(DeviationDirection.ABOVE_BASELINE, findings.on("descent_ms").direction)
        assertFalse("ascent_ms" in findings.metrics())
    }

    @Test
    fun an_alternating_arm_is_timed_and_measured_against_its_own_side_only() {
        // Right arm, 1,200 ms out is its own usual; the left arm's usual 900 would flag it.
        val rep = oneSided(2, Side.RIGHT, 100.0, descentMs = 1_200.0, ascentMs = 1_000.0)
        assertEquals(emptyList(), PostRepAnalysis.analyse(curl, rep, curlBaseline).findings)
        // The idle arm is not measured, so it is never "below baseline".
        val shallow = PostRepAnalysis.analyse(curl, oneSided(3, Side.RIGHT, 70.0), curlBaseline).findings
        assertEquals(setOf("rom_right"), shallow.metrics().filter { it.startsWith("rom") }.toSet())
        assertTrue(shallow.on("rom_right").displayText.startsWith("Right"))
    }

    @Test
    fun asymmetry_is_measured_against_the_users_habitual_offset_not_against_zero() {
        // Usual: left 10 more than right. Equal sides are a 10-degree departure from that.
        assertEquals(emptyList(), squatFindings(paired(1, 104.0, 94.0)))
        val even = squatFindings(paired(1, 102.0, 102.0), flatBaseline)
        assertEquals(DeviationDirection.BELOW_BASELINE, even.on("rom_asymmetry").direction)
    }

    @Test
    fun asymmetry_is_not_claimed_where_the_sides_were_never_measured_together() {
        for (arm in Side.entries) {
            val findings = PostRepAnalysis.analyse(curl, oneSided(1, arm, 60.0), curlBaseline).findings
            assertFalse("rom_asymmetry" in findings.metrics(), arm.name)
        }
    }

    @Test
    fun a_movement_path_unlike_the_usual_one_is_flagged_per_side() {
        // Same range, but the right side's curve is flat for its first half.
        val odd = paired(1, 104.0, 93.0).let { r ->
            RepRecord(
                r.repIndex, r.attributedSide, r.startMs, r.endMs, r.validity, r.rangeOfMotion,
                r.peakValue, r.troughValue, r.descentMs, r.ascentMs, r.resampledLeft,
                DoubleArray(r.resampledRight.size) { i -> if (i < r.resampledRight.size / 2) r.resampledRight[0] else r.resampledRight[i] },
            )
        }
        val findings = squatFindings(odd)
        assertEquals(DeviationDirection.ABOVE_BASELINE, findings.on("path_right").direction)
        assertFalse("path_left" in findings.metrics())
    }

    @Test
    fun within_set_drift_is_found_per_side_and_needs_enough_repetitions() {
        val fading = (1..8).map { paired(it, 106.0 - 2.5 * it, 93.0) }
        val drift = DeviationRules.drift(fading, squatBaseline)
        assertEquals(setOf("rom_drift_left"), drift.metrics())
        assertEquals(DeviationDirection.BELOW_BASELINE, drift.on("rom_drift_left").direction)
        assertEquals(emptyList(), DeviationRules.drift(fading.take(DeviationPolicy.DRIFT_MIN_REPETITIONS - 1), squatBaseline))
        val steady = (1..8).map { paired(it, 103.0 + (it % 2), 93.0) }
        assertEquals(emptyList(), DeviationRules.drift(steady, squatBaseline))
    }

    @Test
    fun drift_ignores_repetitions_that_did_not_complete() {
        val set = (1..8).map { paired(it, 104.0, 93.0) } +
            (9..12).map { paired(it, 20.0, 20.0, validity = RepValidity.ABORTED) }
        assertEquals(emptyList(), DeviationRules.drift(set, squatBaseline))
    }

    @Test
    fun every_finding_is_level_2_and_carries_the_rule_version() {
        val findings = squatFindings(paired(1, 60.0, 130.0, descentMs = 3_000.0, ascentMs = 100.0)) +
            DeviationRules.drift((1..8).map { paired(it, 110.0 - 3 * it, 93.0) }, squatBaseline)
        assertTrue(findings.size >= 5, findings.toString())
        for (finding in findings) {
            assertEquals(ClaimLevel.L2_DEVIATION, finding.level)
            assertEquals(DeviationRules.RULE_VERSION, finding.ruleVersion)
        }
    }

    @Test
    fun the_wording_stays_personal_and_makes_no_correctness_claim() {
        val findings = squatFindings(paired(1, 60.0, 130.0, descentMs = 3_000.0, ascentMs = 100.0))
        for (finding in findings) {
            val text = finding.displayText.lowercase()
            assertTrue("your usual" in text, text)
            for (banned in listOf("correct", "wrong", "bad", "injur", "diagnos", "error", "fault")) {
                assertFalse(banned in text, "\"$banned\" in: $text")
            }
        }
    }

    /**
     * Replaces P6's `p6_makes_no_claim_beyond_numbers_for_any_exercise`, as that
     * test said it would be. Across the whole library: no baseline, no finding;
     * holds, none yet (a one-hold calibration has no spread to judge against);
     * and nothing Layer 1 produces is ever above Level 2.
     */
    @Test
    fun layer_1_claims_nothing_without_a_baseline_nothing_for_holds_and_nothing_above_level_2() {
        val plankBaseline = assertIs<HoldBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromHold(ExerciseLibrary.plank, hold(), 0.0, null)).baseline,
        )
        for (entry in ExerciseLibrary.all) {
            when (entry) {
                is RepetitionExercise -> {
                    assertTrue(PostRepAnalysis.analyse(entry, paired(1, 100.0, 90.0), null).findings.isEmpty(), entry.id)
                    if (CalibrationKey.of(entry) == "squat") {
                        val findings = PostRepAnalysis.analyse(entry, paired(1, 40.0, 150.0), squatBaseline).findings
                        assertTrue(findings.isNotEmpty(), entry.id)
                        assertTrue(findings.all { it.level == ClaimLevel.L2_DEVIATION }, entry.id)
                    }
                }
                is IsometricExercise -> {
                    assertTrue(PostRepAnalysis.analyse(entry, hold(), null).findings.isEmpty(), entry.id)
                    if (CalibrationKey.of(entry) == "plank") {
                        val far = hold(meanHeldValue = 140.0, heldValueSd = 20.0, heldMs = 2_000.0)
                        assertTrue(PostRepAnalysis.analyse(entry, far, plankBaseline).findings.isEmpty(), entry.id)
                    }
                }
            }
        }
    }
}
