package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.profile.Tier
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ACSM tempo rule - DECISIONS.md P3-26, P3-30. Level 3, Tier A only, with its
 * source, and never "wrong".
 */
class AcsmTempoRuleTest {

    private val curl = ExerciseLibrary.bicepCurl

    // --- The minimum-jerk share, against values computed independently ---------------

    @Test
    fun the_minimum_jerk_share_matches_an_independent_computation() {
        // Bisection on s(t) = 10t^3 - 15t^4 + 6t^5 to 200 steps, in Python.
        assertEquals(0.5067270934230668, AcsmTempoRule.minimumJerkShare(0.1, 0.9), 1e-9)
        assertEquals(0.28112767042070574, AcsmTempoRule.minimumJerkShare(0.25, 0.75), 1e-9)
        assertEquals(1.0, AcsmTempoRule.minimumJerkShare(0.0, 1.0), 1e-12)
        assertEquals(0.5, AcsmTempoRule.minimumJerkShare(0.0, 0.5), 1e-9)
    }

    @Test
    fun the_middle_of_a_movement_takes_less_time_than_its_share_of_the_path() {
        // The reason the rule divides by it: the timer sees the fast middle.
        assertTrue(AcsmTempoRule.minimumJerkShare(0.1, 0.9) < 0.8)
    }

    // --- The estimate --------------------------------------------------------------

    @Test
    fun the_whole_phase_is_estimated_from_the_part_between_the_zone_edges() {
        // Curl edges 137 and 68 on a path from 160 to 30: positions 23/130 and 92/130.
        val rep = oneSided(1, Side.RIGHT, 130.0, descentMs = 250.0, ascentMs = 900.0, top = 160.0)
        assertEquals(250.0 / 0.3044682313055367, AcsmTempoRule.estimatedPhaseMs(curl, rep, 250.0), 1e-6)
    }

    @Test
    fun a_path_that_does_not_cross_both_edges_has_no_estimate() {
        // A lateral raise's edges (36 and 77) are nowhere on a path from 165 down to 65.
        assertTrue(AcsmTempoRule.estimatedPhaseMs(ExerciseLibrary.lateralRaise, paired(1, 100.0, 90.0), 800.0).isNaN())
    }

    // --- The rule ------------------------------------------------------------------

    @Test
    fun a_curl_whose_estimated_lift_is_under_a_second_is_flagged_with_its_source() {
        // 250 ms between the edges estimates to about 0.82 s.
        val rep = oneSided(1, Side.RIGHT, 130.0, descentMs = 250.0, ascentMs = 900.0, top = 160.0)
        val fault = assertNotNull(AcsmTempoRule.evaluate(curl.techniqueAuthority, curl, rep))
        assertEquals(ClaimLevel.L3_TECHNIQUE, fault.level)
        assertEquals(AcsmTempoRule.SOURCE_REF, fault.sourceRef)
        assertEquals(AcsmTempoRule.FAULT_ID, fault.faultId)
        assertEquals("p8-acsm-tempo-1", fault.ruleVersion)
        assertEquals("bicep_curl", fault.profileId)
        assertTrue(fault.displayText.contains("ACSM"), fault.displayText)
        assertTrue(fault.displayText.contains("1–2 s"), fault.displayText)
        assertTrue(fault.displayText.contains("lifting"), fault.displayText)
        assertTrue(!Regex("wrong|incorrect|unsafe|injur|bad", RegexOption.IGNORE_CASE).containsMatchIn(fault.displayText))
    }

    @Test
    fun a_controlled_curl_is_not_flagged() {
        // 900 ms between the edges estimates to about 2.96 s; lowering likewise.
        val rep = oneSided(1, Side.RIGHT, 130.0, descentMs = 900.0, ascentMs = 900.0, top = 160.0)
        assertNull(AcsmTempoRule.evaluate(curl.techniqueAuthority, curl, rep))
    }

    @Test
    fun a_quick_lowering_is_flagged_as_lowering() {
        val rep = oneSided(1, Side.RIGHT, 130.0, descentMs = 900.0, ascentMs = 250.0, top = 160.0)
        val fault = assertNotNull(AcsmTempoRule.evaluate(curl.techniqueAuthority, curl, rep))
        assertTrue(fault.displayText.contains("lowering") && !fault.displayText.contains("lifting"), fault.displayText)
    }

    @Test
    fun a_repetition_that_did_not_count_is_not_judged() {
        val rep = oneSided(1, Side.RIGHT, 130.0, validity = RepValidity.ABORTED, descentMs = 100.0, ascentMs = 100.0, top = 160.0)
        assertNull(AcsmTempoRule.evaluate(curl.techniqueAuthority, curl, rep))
    }

    @Test
    fun an_authority_for_another_exercise_is_refused() {
        assertFailsWith<IllegalArgumentException> {
            AcsmTempoRule.evaluate(ExerciseLibrary.squat.techniqueAuthority, curl, oneSided(1, Side.RIGHT, 130.0))
        }
    }

    // --- Through the post-rep analysis: Tier A only -----------------------------------

    private fun fast(profile: RepetitionExercise) = when (profile.id) {
        "bicep_curl" -> oneSided(1, Side.RIGHT, 130.0, descentMs = 100.0, ascentMs = 100.0, top = 160.0)
        else -> paired(1, 100.0, 90.0, descentMs = 100.0, ascentMs = 100.0)
    }

    @Test
    fun only_tier_a_can_say_it_and_it_needs_no_baseline() {
        for (profile in ExerciseLibrary.all.filterIsInstance<RepetitionExercise>()) {
            val findings = PostRepAnalysis.analyse(profile, fast(profile), null).findings
            val technique = findings.filter { it.level == ClaimLevel.L3_TECHNIQUE }
            if (profile.tier != Tier.A) assertEquals(emptyList(), technique, profile.id)
            assertTrue(findings.all { it.level == ClaimLevel.L3_TECHNIQUE }, "no baseline, so no Level 2: ${profile.id}")
        }
        val curlFaults = PostRepAnalysis.analyse(curl, fast(curl), null).findings
        assertEquals(AcsmTempoRule.FAULT_ID, assertIs<TechniqueFault>(curlFaults.single()).faultId)
    }

    @Test
    fun a_variant_speaks_under_its_parents_authority() {
        val goblet = ExerciseLibrary.gobletSquat
        val fault = assertIs<TechniqueFault>(PostRepAnalysis.analyse(goblet, fast(goblet), null).findings.single())
        assertEquals("squat", fault.profileId)
    }
}
