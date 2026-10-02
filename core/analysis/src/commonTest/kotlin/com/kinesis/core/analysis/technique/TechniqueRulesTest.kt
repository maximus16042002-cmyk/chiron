package com.kinesis.core.analysis.technique

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.FormMeasures
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * P8 Layer 2, step A - DECISIONS.md P3-43. Three rules from the angles each repetition already
 * records, each with its cited source and the app's own 20-degree tolerance. Sides are judged
 * apart, a repetition that did not count is not judged, and no message says "wrong".
 */
class TechniqueRulesTest {

    private val raise = ExerciseLibrary.lateralRaise
    private val curl = ExerciseLibrary.bicepCurl
    private val pushUp = ExerciseLibrary.pushUp
    private val nan = Double.NaN

    private fun rep(
        peak: BilateralSignal,
        trough: BilateralSignal,
        attributedSide: Side? = null,
        validity: RepValidity = RepValidity.VALID,
    ) = RepRecord(
        repIndex = 1, attributedSide = attributedSide, startMs = 0.0, endMs = 2_000.0, validity = validity,
        rangeOfMotion = BilateralSignal(
            if (peak.left.isFinite()) kotlin.math.abs(peak.left - trough.left) else nan,
            if (peak.right.isFinite()) kotlin.math.abs(peak.right - trough.right) else nan,
        ),
        peakValue = peak, troughValue = trough, descentMs = 900.0, ascentMs = 1_100.0,
        resampledLeft = DoubleArray(64) { if (peak.left.isFinite()) peak.left else nan },
        resampledRight = DoubleArray(64) { if (peak.right.isFinite()) peak.right else nan },
    )

    // --- The lateral raise: above shoulder level (S1) --------------------------------------------

    @Test
    fun a_raise_past_110_degrees_is_said_on_that_side_only() {
        val faults = TechniqueRules.evaluate(raise.techniqueAuthority, raise, rep(BilateralSignal(20.0, 22.0), BilateralSignal(124.0, 100.0)))
        val fault = faults.single()
        assertEquals(TechniqueRules.RAISED_ABOVE_SHOULDER_LEVEL, fault.faultId)
        assertEquals(ClaimLevel.L3_TECHNIQUE, fault.level)
        assertEquals(TechniqueRules.SOURCE_LATERAL_RAISE, fault.sourceRef)
        assertEquals(TechniqueRules.RULE_VERSION, fault.ruleVersion)
        assertTrue(fault.displayText.startsWith("Left arm"), fault.displayText)
        assertTrue("124°" in fault.displayText && "90°" in fault.displayText && "110°" in fault.displayText, fault.displayText)
    }

    @Test
    fun a_raise_within_the_tolerance_is_not_judged() {
        val faults = TechniqueRules.evaluate(raise.techniqueAuthority, raise, rep(BilateralSignal(20.0, 22.0), BilateralSignal(110.0, 95.0)))
        assertEquals(emptyList(), faults)
    }

    /** CLAUDE.md section 3: one message per side, never an average of the two. */
    @Test
    fun both_arms_high_are_two_messages_left_then_right() {
        val faults = TechniqueRules.evaluate(raise.techniqueAuthority, raise, rep(BilateralSignal(20.0, 22.0), BilateralSignal(130.0, 115.0)))
        assertEquals(2, faults.size)
        assertTrue(faults[0].displayText.startsWith("Left arm") && "130°" in faults[0].displayText)
        assertTrue(faults[1].displayText.startsWith("Right arm") && "115°" in faults[1].displayText)
    }

    // --- The curl: started with the elbow bent (S2) ----------------------------------------------

    @Test
    fun a_curl_started_below_160_degrees_is_said_for_the_working_arm() {
        val faults = TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep(BilateralSignal(nan, 148.0), BilateralSignal(nan, 45.0), Side.RIGHT))
        val fault = faults.single()
        assertEquals(TechniqueRules.CURL_STARTED_ELBOW_BENT, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_CURL, fault.sourceRef)
        assertTrue(fault.displayText.startsWith("Right arm") && "148°" in fault.displayText && "160°" in fault.displayText, fault.displayText)
    }

    @Test
    fun a_curl_started_from_a_straight_arm_is_not_judged() {
        assertEquals(emptyList(), TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep(BilateralSignal(172.0, nan), BilateralSignal(40.0, nan), Side.LEFT)))
        assertEquals(emptyList(), TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep(BilateralSignal(160.0, nan), BilateralSignal(40.0, nan), Side.LEFT)))
    }

    /** Only the arm that did the repetition is judged; the resting arm's angle says nothing about it. */
    @Test
    fun the_resting_arm_of_a_curl_is_not_judged() {
        val faults = TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep(BilateralSignal(170.0, 120.0), BilateralSignal(45.0, 118.0), Side.LEFT))
        assertEquals(emptyList(), faults)
    }

    // --- The push-up: arms not extended at the top (S3) ------------------------------------------

    @Test
    fun a_push_up_topping_out_below_160_degrees_is_said() {
        val fault = TechniqueRules.evaluate(pushUp.techniqueAuthority, pushUp, rep(BilateralSignal(nan, 151.0), BilateralSignal(nan, 80.0))).single()
        assertEquals(TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_PUSHUP, fault.sourceRef)
        assertTrue(fault.displayText.startsWith("Right arm") && "151°" in fault.displayText, fault.displayText)
    }

    @Test
    fun a_push_up_from_straight_arms_is_not_judged() {
        assertEquals(emptyList(), TechniqueRules.evaluate(pushUp.techniqueAuthority, pushUp, rep(BilateralSignal(176.0, nan), BilateralSignal(78.0, nan))))
    }

    // --- Step B: joints beside the driving one (P3-44) --------------------------------------------

    private fun withForm(rep: RepRecord, vararg measures: Pair<FormMeasure, BilateralSignal>) = RepRecord(
        repIndex = rep.repIndex, attributedSide = rep.attributedSide, startMs = rep.startMs, endMs = rep.endMs,
        validity = rep.validity, rangeOfMotion = rep.rangeOfMotion, peakValue = rep.peakValue, troughValue = rep.troughValue,
        descentMs = rep.descentMs, ascentMs = rep.ascentMs, resampledLeft = rep.resampledLeft, resampledRight = rep.resampledRight,
        form = FormMeasures(mapOf(*measures)),
    )

    @Test
    fun a_curl_whose_upper_arm_swung_past_20_degrees_is_said_for_the_working_arm_only() {
        val rep = withForm(
            rep(BilateralSignal(nan, 170.0), BilateralSignal(nan, 45.0), Side.RIGHT),
            FormMeasure.UPPER_ARM_TRUNK_MAX to BilateralSignal(nan, 34.0),
        )
        val fault = TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep).single()
        assertEquals(TechniqueRules.CURL_UPPER_ARM_MOVED_FORWARD, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_CURL_UPPER_ARM, fault.sourceRef)
        assertEquals(TechniqueRules.RULE_VERSION_FORM, fault.ruleVersion)
        assertTrue(fault.displayText.startsWith("Right upper arm") && "34°" in fault.displayText && "20°" in fault.displayText, fault.displayText)
        val still = withForm(rep(BilateralSignal(nan, 170.0), BilateralSignal(nan, 45.0), Side.RIGHT), FormMeasure.UPPER_ARM_TRUNK_MAX to BilateralSignal(nan, 20.0))
        assertEquals(emptyList(), TechniqueRules.evaluate(curl.techniqueAuthority, curl, still))
    }

    @Test
    fun a_push_up_hip_past_20_degrees_off_the_line_is_said_as_a_sag_or_a_raise() {
        val sag = withForm(
            rep(BilateralSignal(nan, 172.0), BilateralSignal(nan, 80.0)),
            FormMeasure.BODY_LINE_SAG_MAX to BilateralSignal(nan, 26.0),
            FormMeasure.BODY_LINE_HIKE_MAX to BilateralSignal(nan, 4.0),
        )
        val fault = TechniqueRules.evaluate(pushUp.techniqueAuthority, pushUp, sag).single()
        assertEquals(TechniqueRules.PUSHUP_HIPS_SAGGED, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_PUSHUP_LINE, fault.sourceRef)
        assertTrue(fault.displayText.startsWith("Right side") && "26°" in fault.displayText && "sagged" in fault.displayText, fault.displayText)
        val raised = withForm(
            rep(BilateralSignal(nan, 172.0), BilateralSignal(nan, 80.0)),
            FormMeasure.BODY_LINE_SAG_MAX to BilateralSignal(nan, 0.0),
            FormMeasure.BODY_LINE_HIKE_MAX to BilateralSignal(nan, 23.0),
        )
        assertEquals(TechniqueRules.PUSHUP_HIPS_RAISED, TechniqueRules.evaluate(pushUp.techniqueAuthority, pushUp, raised).single().faultId)
    }

    @Test
    fun a_squat_torso_more_than_20_degrees_ahead_of_the_shin_is_said_per_side() {
        val squat = ExerciseLibrary.squat
        val rep = withForm(
            rep(BilateralSignal(170.0, 170.0), BilateralSignal(80.0, 80.0)),
            FormMeasure.TRUNK_MINUS_SHIN_LEAN to BilateralSignal(28.0, 12.0),
        )
        val fault = TechniqueRules.evaluate(squat.techniqueAuthority, squat, rep).single()
        assertEquals(TechniqueRules.SQUAT_TRUNK_AHEAD_OF_SHIN, fault.faultId)
        assertEquals(TechniqueRules.SOURCE_SQUAT_TRUNK, fault.sourceRef)
        assertTrue(fault.displayText.startsWith("Left side") && "28°" in fault.displayText, fault.displayText)
        // P3-44: the other direction - the shin ahead of the trunk - is not judged.
        val upright = withForm(rep(BilateralSignal(170.0, 170.0), BilateralSignal(80.0, 80.0)), FormMeasure.TRUNK_MINUS_SHIN_LEAN to BilateralSignal(-35.0, -35.0))
        assertEquals(emptyList(), TechniqueRules.evaluate(squat.techniqueAuthority, squat, upright))
    }

    /** A record stored before P3-44 has no form: the step B rules say nothing, never as if it were 0. */
    @Test
    fun a_record_without_a_form_is_not_judged_by_the_step_b_rules() {
        val squat = ExerciseLibrary.squat
        assertEquals(emptyList(), TechniqueRules.evaluate(squat.techniqueAuthority, squat, rep(BilateralSignal(170.0, 170.0), BilateralSignal(80.0, 80.0))))
        assertEquals(emptyList(), TechniqueRules.evaluate(curl.techniqueAuthority, curl, rep(BilateralSignal(170.0, nan), BilateralSignal(45.0, nan), Side.LEFT)))
    }

    // --- What is never judged --------------------------------------------------------------------

    @Test
    fun a_repetition_that_did_not_count_is_not_judged() {
        for (validity in RepValidity.entries.filter { it != RepValidity.VALID }) {
            val faults = TechniqueRules.evaluate(raise.techniqueAuthority, raise, rep(BilateralSignal(20.0, 22.0), BilateralSignal(140.0, 140.0), validity = validity))
            assertEquals(emptyList(), faults, "$validity")
        }
    }

    /** P3-43: the glute bridge has no rule; it says nothing. */
    @Test
    fun a_tier_a_exercise_without_a_rule_says_nothing() {
        val bridge = ExerciseLibrary.gluteBridge
        assertEquals(emptyList(), TechniqueRules.evaluate(bridge.techniqueAuthority, bridge, rep(BilateralSignal(120.0, 120.0), BilateralSignal(165.0, 165.0))))
    }

    @Test
    fun one_exercises_authority_cannot_judge_another() {
        assertFailsWith<IllegalArgumentException> {
            TechniqueRules.evaluate(curl.techniqueAuthority, raise, rep(BilateralSignal(20.0, 22.0), BilateralSignal(130.0, 130.0)))
        }
    }

    /** The words a rule rests on are in the code, not only in the report (ZERO_DATASET_BUILD_PLAN). */
    @Test
    fun every_rule_names_its_source() {
        for (source in listOf(
            TechniqueRules.SOURCE_LATERAL_RAISE, TechniqueRules.SOURCE_CURL, TechniqueRules.SOURCE_PUSHUP,
            TechniqueRules.SOURCE_CURL_UPPER_ARM, TechniqueRules.SOURCE_PUSHUP_LINE, TechniqueRules.SOURCE_SQUAT_TRUNK,
        )) {
            assertTrue(source.contains("acefitness.org") || source.contains("NSCA Coach"), source)
            assertTrue(source.contains("\""), "the source quotes the description it implements: $source")
        }
    }
}
