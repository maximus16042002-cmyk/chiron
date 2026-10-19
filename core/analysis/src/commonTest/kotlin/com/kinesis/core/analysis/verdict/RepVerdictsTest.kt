package com.kinesis.core.analysis.verdict

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.analysis.verifier.MovementContextTracker
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each attempt's verdict - DECISIONS.md P3-78. The count the user sees is the number of `COUNTED`
 * verdicts; everything else says why not, or says nothing when the user did not cause it.
 */
class RepVerdictsTest {

    private val squat = ArchetypeProfiles.squat // knee, 160 -> 90, near side
    private val curl = ArchetypeProfiles.bicepCurl // elbow, 145 -> 60, per limb
    private val lunge = ExerciseLibrary.all.first { it.id == "lunge" } as RepetitionExercise // knee, 165 -> 100
    private val shoulderPress = ExerciseLibrary.all.first { it.id == "shoulder_press" } as RepetitionExercise // 100 -> 160

    // --- Each outcome ------------------------------------------------------------------------

    @Test
    fun a_full_steady_repetition_counts() {
        val v = judge(squat, rep(RepValidity.VALID, durationMs = 3_000.0, rightPeak = 178.0, rightTrough = 62.0))
        assertEquals(RepOutcome.COUNTED, v.outcome)
        assertTrue(v.outcome.counts)
        assertNull(v.message)
    }

    @Test
    fun a_full_repetition_faster_than_the_profiles_limit_is_too_fast() {
        // The curl's limit is 630 ms: MM-Fit's development subjects' fastest real curl took 633 ms.
        assertEquals(630.0, curl.tooFastBelowMs)
        val fast = judge(curl, rep(RepValidity.VALID, durationMs = 600.0, side = Side.RIGHT, rightPeak = 170.0, rightTrough = 45.0))
        assertEquals(RepOutcome.TOO_FAST, fast.outcome)
        assertFalse(fast.outcome.counts)
        assertTrue(fast.outcome.blamesMovement)
        assertEquals(RepVerdicts.TOO_FAST_MESSAGE, fast.message)

        val atLimit = judge(curl, rep(RepValidity.VALID, durationMs = 630.0, side = Side.RIGHT, rightPeak = 170.0, rightTrough = 45.0))
        assertEquals(RepOutcome.COUNTED, atLimit.outcome)
    }

    /** Where MM-Fit has too little of an exercise, the limit is the state machine's own minimum. */
    @Test
    fun without_evidence_the_limit_is_the_state_machines_minimum() {
        assertEquals(squat.repSignal.minRepDurationMs, squat.tooFastBelowMs)
        val pushUp = ExerciseLibrary.all.first { it.id == "pushup" } as RepetitionExercise
        assertEquals(pushUp.repSignal.minRepDurationMs, pushUp.tooFastBelowMs)
        for (profile in ExerciseLibrary.all.filterIsInstance<RepetitionExercise>()) {
            assertTrue(profile.tooFastBelowMs >= profile.repSignal.minRepDurationMs, profile.id + "'s limit is under its own minimum")
        }
    }

    /** The state machine's own minimum turns a too-short full cycle into ABORTED; that is "too fast" too. */
    @Test
    fun an_aborted_attempt_that_reached_the_far_position_was_too_fast() {
        val v = judge(squat, rep(RepValidity.ABORTED, durationMs = 400.0, rightPeak = 175.0, rightTrough = 70.0))
        assertEquals(RepOutcome.TOO_FAST, v.outcome)
    }

    @Test
    fun an_attempt_turned_back_past_halfway_is_not_a_full_repetition() {
        // Squat: top 160, bottom 90, halfway 125.
        val v = judge(squat, rep(RepValidity.ABORTED, durationMs = 2_000.0, rightPeak = 176.0, rightTrough = 110.0))
        assertEquals(RepOutcome.NOT_DEEP_ENOUGH, v.outcome)
        assertTrue(v.outcome.blamesMovement)
        assertEquals(RepVerdicts.NOT_FULL_MESSAGE, v.message)
    }

    /** Past halfway and under the too-fast limit, speed is the reason worth saying. */
    @Test
    fun a_very_fast_attempt_short_of_the_far_position_is_too_fast() {
        // Curl: top 145, bottom 60. A 0.33 s curl whose smoothed trough stopped at 61.
        val v = judge(curl, rep(RepValidity.ABORTED, durationMs = 333.0, side = Side.LEFT, leftPeak = 168.0, leftTrough = 61.0))
        assertEquals(RepOutcome.TOO_FAST, v.outcome)
        val slow = judge(curl, rep(RepValidity.ABORTED, durationMs = 1_500.0, side = Side.LEFT, leftPeak = 168.0, leftTrough = 61.0))
        assertEquals(RepOutcome.NOT_DEEP_ENOUGH, slow.outcome)
    }

    /** The owner's records: wobbles of 15-40 degrees at the top, 0.1-0.5 s each. Not attempts. */
    @Test
    fun a_wobble_that_never_got_halfway_says_nothing() {
        val v = judge(lunge, rep(RepValidity.ABORTED, durationMs = 300.0, leftPeak = 179.0, leftTrough = 157.0))
        assertEquals(RepOutcome.NOISE, v.outcome)
        assertNull(v.message)
        assertFalse(v.outcome.blamesMovement)
        assertEquals(RepOutcome.NOISE, judge(squat, rep(RepValidity.INCOMPLETE, durationMs = 900.0, rightPeak = 170.0, rightTrough = 95.0)).outcome)
    }

    /** The direction of "far" follows the profile: a press goes up, from 100 to 160. */
    @Test
    fun the_far_position_follows_the_profiles_direction() {
        // As RepFsm records an upward profile: peak = the start (low), trough = the far extreme (high).
        fun press(far: Double, ms: Double, validity: RepValidity = RepValidity.ABORTED) =
            rep(validity, durationMs = ms, leftPeak = 95.0, leftTrough = far, rightPeak = 95.0, rightTrough = far)
        assertEquals(RepOutcome.NOT_DEEP_ENOUGH, judge(shoulderPress, press(far = 140.0, ms = 1_500.0)).outcome)
        assertEquals(RepOutcome.NOISE, judge(shoulderPress, press(far = 120.0, ms = 1_500.0)).outcome)
        assertEquals(RepOutcome.TOO_FAST, judge(shoulderPress, press(far = 165.0, ms = 500.0)).outcome)
        assertEquals(RepOutcome.LOST_TRACKING, judge(shoulderPress, press(far = 140.0, ms = 800.0, validity = RepValidity.INSUFFICIENT_DATA)).outcome)
    }

    /** A paired record is ABORTED when one side fell short: slow, that is not "too fast". */
    @Test
    fun a_slow_attempt_where_one_side_reached_the_far_position_is_not_too_fast() {
        val v = judge(curl, rep(RepValidity.ABORTED, durationMs = 3_000.0, leftPeak = 168.0, leftTrough = 55.0, rightPeak = 168.0, rightTrough = 90.0))
        assertEquals(RepOutcome.NOT_DEEP_ENOUGH, v.outcome)
    }

    @Test
    fun tracking_lost_past_halfway_says_so_without_blaming_the_movement() {
        val v = judge(squat, rep(RepValidity.INSUFFICIENT_DATA, durationMs = 800.0, rightPeak = 176.0, rightTrough = 95.0))
        assertEquals(RepOutcome.LOST_TRACKING, v.outcome)
        assertFalse(v.outcome.blamesMovement)
        assertEquals(RepVerdicts.LOST_TRACKING_MESSAGE, v.message)
        assertEquals(RepOutcome.NOISE, judge(squat, rep(RepValidity.INSUFFICIENT_DATA, durationMs = 300.0, rightPeak = 176.0, rightTrough = 150.0)).outcome)
    }

    /** The knee cannot fold past 30 degrees standing: below it, MediaPipe had lost the legs. */
    @Test
    fun a_knee_below_the_plausibility_floor_is_lost_tracking_however_valid_it_looked() {
        assertEquals(30.0, squat.trackingFloorDeg)
        assertEquals(30.0, lunge.trackingFloorDeg)
        assertTrue(curl.trackingFloorDeg.isNaN(), "the elbow has no floor: front-on curls legitimately read near 0")
        val phantom = judge(lunge, rep(RepValidity.VALID, durationMs = 2_600.0, leftPeak = 174.7, leftTrough = 10.8))
        assertEquals(RepOutcome.LOST_TRACKING, phantom.outcome)
        assertEquals(RepOutcome.COUNTED, judge(lunge, rep(RepValidity.VALID, durationMs = 2_600.0, leftPeak = 174.7, leftTrough = 30.0)).outcome)
    }

    // --- Form ---------------------------------------------------------------------------------

    /**
     * DECISIONS.md P3-88: a cited form fault is said in red, with the rule's short reason, and does not count.
     */
    @Test
    fun a_form_fault_is_said_in_red_and_does_not_count() {
        // ACE's curl starts from a straightened elbow; Chiron says so below 180 - tolerance.
        val bent = rep(RepValidity.VALID, durationMs = 2_500.0, side = Side.RIGHT, rightPeak = 140.0, rightTrough = 40.0)
        val v = judge(curl, bent)
        assertEquals(RepOutcome.FORM_FAULT, v.outcome)
        assertFalse(v.outcome.counts)
        assertTrue(v.outcome.blamesMovement)
        assertEquals(TechniqueRules.CURL_STARTED_ELBOW_BENT, v.faultId)
        assertEquals(RepVerdicts.FORM_SHORT_TEXT.getValue(TechniqueRules.CURL_STARTED_ELBOW_BENT) + RepVerdicts.NOT_COUNTED_SUFFIX, v.message)
    }

    /** Every fault FormNotes shows has a short live reason, and no other rule is said live. */
    @Test
    fun every_shown_rule_has_a_short_reason() {
        assertEquals(com.kinesis.core.analysis.technique.FormNotes.SHOWN.toSet(), RepVerdicts.FORM_SHORT_TEXT.keys)
    }

    /** CLAUDE.md section 8: a Tier B profile has no authority, so no form fault can be said of its repetition. */
    @Test
    fun a_tier_b_repetition_is_never_cancelled_for_form() {
        for (profile in ExerciseLibrary.all.filterIsInstance<RepetitionExercise>()) {
            if (profile.tier.name == "A") continue
            val v = judge(profile, rep(RepValidity.VALID, durationMs = 5_000.0, leftPeak = profile.repSignal.topThreshold, leftTrough = profile.repSignal.bottomThreshold, rightPeak = profile.repSignal.topThreshold, rightTrough = profile.repSignal.bottomThreshold))
            assertTrue(v.outcome != RepOutcome.FORM_FAULT, profile.id + " was judged for form")
        }
    }

    // --- The other knee -----------------------------------------------------------------------

    /**
     * A counted repetition is never taken away for the other side: on MM-Fit, real alternating
     * lunges follow each other on the other knee as close as 0.4 s (P3-78).
     */
    @Test
    fun alternating_repetitions_on_the_other_side_all_count_however_close() {
        val left = rep(RepValidity.VALID, index = 1, startMs = 0.0, durationMs = 1_400.0, leftPeak = 175.0, leftTrough = 95.0)
        val right = rep(RepValidity.VALID, index = 2, startMs = 1_800.0, durationMs = 1_400.0, rightPeak = 175.0, rightTrough = 95.0)
        val overlapping = rep(RepValidity.VALID, index = 3, startMs = 3_000.0, durationMs = 1_400.0, leftPeak = 175.0, leftTrough = 95.0)
        assertEquals(3, RepVerdicts.counted(lunge, listOf(left, right, overlapping)))
    }

    /** A failed cycle on the far knee during a counted squat is that squat, not a red flash. */
    @Test
    fun the_other_sides_failure_alongside_a_counted_repetition_is_silent() {
        val farFirst = rep(RepValidity.ABORTED, index = 17, startMs = 52_100.0, durationMs = 2_400.0, rightPeak = 177.8, rightTrough = 110.4)
        val near = rep(RepValidity.VALID, index = 18, startMs = 52_100.0, durationMs = 2_600.0, leftPeak = 179.4, leftTrough = 91.3)
        assertEquals(listOf(RepOutcome.DUPLICATE, RepOutcome.COUNTED), RepVerdicts.judgeSet(squat, listOf(farFirst, near)).map { it.outcome })
        // Live, the far side's record arrives first; the near side is still mid-squat, so it is silent then too.
        assertEquals(RepOutcome.DUPLICATE, RepVerdicts.judge(squat, farFirst, emptyList(), otherSideMidRepetition = true).outcome)
    }

    /** Same side, back to back: two repetitions. The window is only about the other side. */
    @Test
    fun consecutive_repetitions_on_the_same_side_both_count() {
        val a = rep(RepValidity.VALID, index = 1, startMs = 0.0, durationMs = 2_800.0, rightPeak = 179.0, rightTrough = 60.0)
        val b = rep(RepValidity.VALID, index = 2, startMs = 2_900.0, durationMs = 2_800.0, rightPeak = 179.0, rightTrough = 61.0)
        assertEquals(2, RepVerdicts.counted(squat, listOf(a, b)))
    }

    /** A curl is per limb: each arm's repetition is its own, whatever the other arm did. */
    @Test
    fun per_limb_repetitions_are_never_duplicates() {
        val left = rep(RepValidity.VALID, index = 1, side = Side.LEFT, startMs = 0.0, durationMs = 2_000.0, leftPeak = 172.0, leftTrough = 40.0)
        val right = rep(RepValidity.VALID, index = 2, side = Side.RIGHT, startMs = 500.0, durationMs = 2_000.0, rightPeak = 172.0, rightTrough = 40.0)
        assertEquals(2, RepVerdicts.counted(curl, listOf(left, right)))
    }

    // --- The owner's session, 2026-10-11 (values from local/p4-2026-10-11, not in git) ----------

    @Test
    fun the_owners_session_counts_what_they_did() {
        // Five slow squats, right knee: all count.
        val squats = listOf(
            0.0 to 65.2, 4_800.0 to 58.6, 12_600.0 to 60.9, 18_200.0 to 62.5, 24_800.0 to 59.7,
        ).mapIndexed { i, (start, trough) -> rep(RepValidity.VALID, index = i + 1, startMs = start, durationMs = 3_000.0, rightPeak = 179.0, rightTrough = trough) }
        assertEquals(5, RepVerdicts.counted(squat, squats))

        // One lunge (as the owner tells it), seen as two knees 0.9 s apart, and three phantoms at
        // 10.8, 15.5 and 26.6 degrees. The phantoms go; the two knees both count, because real
        // alternating lunges come as close (P3-78). Not fixed here: it needs recorded landmarks (a later entry).
        val lunges = listOf(
            rep(RepValidity.VALID, index = 7, startMs = 37_000.0, durationMs = 1_700.0, leftPeak = 179.1, leftTrough = 94.6),
            rep(RepValidity.VALID, index = 9, startMs = 39_600.0, durationMs = 3_300.0, rightPeak = 165.6, rightTrough = 56.9),
            rep(RepValidity.VALID, index = 12, startMs = 85_900.0, durationMs = 2_600.0, leftPeak = 174.7, leftTrough = 10.8),
            rep(RepValidity.VALID, index = 16, startMs = 88_900.0, durationMs = 900.0, leftPeak = 174.3, leftTrough = 15.5),
            rep(RepValidity.VALID, index = 19, startMs = 91_500.0, durationMs = 1_100.0, leftPeak = 177.3, leftTrough = 26.6),
        )
        assertEquals(2, RepVerdicts.counted(lunge, lunges))

        // The fast curls: 0.50, 0.50, 0.57, 0.77 and 0.60 s. Four too fast; 0.77 s is over the limit.
        val fast = listOf(500.0, 500.0, 570.0, 770.0, 600.0).mapIndexed { i, ms ->
            rep(RepValidity.VALID, index = i + 7, side = Side.RIGHT, startMs = 50_000.0 + i * 1_500.0, durationMs = ms, rightPeak = 168.0, rightTrough = 46.0)
        }
        assertEquals(listOf(RepOutcome.TOO_FAST, RepOutcome.TOO_FAST, RepOutcome.TOO_FAST, RepOutcome.COUNTED, RepOutcome.TOO_FAST), RepVerdicts.judgeSet(curl, fast).map { it.outcome })

        // Facing the camera: a squat to 12 degrees in 1.1 s does not count.
        assertEquals(RepOutcome.LOST_TRACKING, judge(squat, rep(RepValidity.VALID, durationMs = 1_100.0, rightPeak = 172.9, rightTrough = 12.0)).outcome)
    }

    // --- DECISIONS.md P3-86: rejecting non-squat movements on the squat profile ---------------

    @Test
    fun a_squat_with_no_hip_flexion_is_rejected_as_wrong_exercise() {
        // A standing leg curl: knee flexes to 70 deg, but hip stays extended (> 135 deg)
        val context = movementContextTrajectory(
            nearHip = List(16) { 165.0 - (it % 3) },
            trunkLean = List(16) { 5.0 },
        )
        val v = judge(
            squat,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 175.0,
                rightTrough = 70.0,
                movementContext = context,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
        assertTrue(v.outcome.blamesMovement)
        assertEquals(RepVerdicts.WRONG_EXERCISE_MESSAGE, v.message)
    }

    @Test
    fun a_lunge_during_squat_is_rejected_as_wrong_exercise() {
        // A lunge with leading leg near camera: near hip flexes to 105 deg, but torso stays upright (6 deg)
        // and trailing far hip is in extension (155 deg)
        val context = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 165.0 - (60.0 * it / 8.0) else 105.0 + (60.0 * (it - 8) / 7.0) },
            farHip = List(16) { 155.0 },
            trunkLean = List(16) { 6.0 },
        )
        val v = judge(
            squat,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 75.0,
                movementContext = context,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
        assertTrue(v.outcome.blamesMovement)
        assertEquals(RepVerdicts.WRONG_EXERCISE_MESSAGE, v.message)
    }

    @Test
    fun a_standing_knee_raise_during_squat_is_rejected_as_wrong_exercise() {
        // A single-leg knee raise: raised knee flexes to 80 deg, hip flexes to 85 deg, but torso stays upright (4 deg)
        // and supporting far hip stays straight (170 deg)
        val context = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (85.0 * it / 8.0) else 85.0 + (85.0 * (it - 8) / 7.0) },
            farHip = List(16) { 170.0 - (it % 2) },
            trunkLean = List(16) { 4.0 },
        )
        val v = judge(
            squat,
            rep(
                RepValidity.VALID,
                durationMs = 1_800.0,
                side = Side.RIGHT,
                rightPeak = 175.0,
                rightTrough = 80.0,
                movementContext = context,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
        assertTrue(v.outcome.blamesMovement)
        assertEquals(RepVerdicts.WRONG_EXERCISE_MESSAGE, v.message)
    }

    @Test
    fun a_valid_squat_with_proper_hip_and_trunk_kinematics_counts() {
        // A true squat: near hip flexes to 95 deg, torso leans forward to 25 deg, far hip flexes symmetrically
        val context = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (75.0 * it / 8.0) else 95.0 + (75.0 * (it - 8) / 7.0) },
            farHip = (0..15).map { if (it < 8) 170.0 - (75.0 * it / 8.0) else 95.0 + (75.0 * (it - 8) / 7.0) },
            trunkLean = (0..15).map { if (it < 8) 5.0 + (20.0 * it / 8.0) else 25.0 - (20.0 * (it - 8) / 7.0) },
        )
        val v = judge(
            squat,
            rep(
                RepValidity.VALID,
                durationMs = 2_200.0,
                side = Side.RIGHT,
                rightPeak = 175.0,
                rightTrough = 75.0,
                movementContext = context,
            ),
        )
        assertEquals(RepOutcome.COUNTED, v.outcome)
        assertTrue(v.outcome.counts)
        assertNull(v.message)
    }

    @Test
    fun a_squat_without_movement_context_still_counts_for_backwards_compatibility() {
        val rep = rep(
            RepValidity.VALID,
            durationMs = 2_000.0,
            rightPeak = 175.0,
            rightTrough = 75.0,
            movementContext = null,
        )
        val v = judge(squat, rep)
        assertEquals(RepOutcome.COUNTED, v.outcome)
        assertTrue(v.outcome.counts)
    }

    @Test
    fun frozen_synthetic_test_fixtures_are_not_rejected_as_wrong_exercise() {
        // Synthetic test fixtures where hip and torso are static frozen
        val context = movementContextTrajectory(
            nearHip = List(16) { 180.0 },
            farHip = List(16) { 180.0 },
            trunkLean = List(16) { 0.0 },
        )
        val v = judge(
            squat,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 70.0,
                movementContext = context,
            ),
        )
        assertEquals(RepOutcome.COUNTED, v.outcome)
    }

    @Test
    fun fast_repetition_with_short_active_duration_is_rejected_as_too_fast() {
        // Total duration is 2.0s, but active movement (descent + ascent) is only 600ms (< 930ms for lunge)
        val fastRep = RepRecord(
            repIndex = 1,
            attributedSide = Side.RIGHT,
            startMs = 0.0,
            endMs = 2000.0,
            validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(Double.NaN, 95.0),
            peakValue = BilateralSignal(Double.NaN, 175.0),
            troughValue = BilateralSignal(Double.NaN, 80.0),
            descentMs = 400.0,
            ascentMs = 200.0,
            resampledLeft = DoubleArray(64) { Double.NaN },
            resampledRight = DoubleArray(64) { Double.NaN },
        )
        val v = judge(ExerciseLibrary.lunge, fastRep)
        assertEquals(RepOutcome.TOO_FAST, v.outcome)
        assertFalse(v.outcome.counts)
        assertEquals(RepVerdicts.TOO_FAST_MESSAGE, v.message)
    }

    @Test
    fun lunge_rejects_squats_as_wrong_exercise() {
        // Squat: trunk leans forward (20 deg), both hips flex deeply (75 deg) and symmetrically
        val squatContext = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (95.0 * it / 8.0) else 75.0 + (95.0 * (it - 8) / 7.0) },
            farHip = (0..15).map { if (it < 8) 170.0 - (92.0 * it / 8.0) else 78.0 + (92.0 * (it - 8) / 7.0) },
            trunkLean = (0..15).map { if (it < 8) 5.0 + (15.0 * it / 8.0) else 20.0 - (15.0 * (it - 8) / 7.0) },
        )
        val v = judge(
            ExerciseLibrary.lunge,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 70.0,
                movementContext = squatContext,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
    }

    @Test
    fun lunge_rejects_standing_leg_curls_as_wrong_exercise() {
        // Leg curl: hips remain extended (> 130 deg), but knee flexes
        val curlContext = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (5.0 * it / 8.0) else 165.0 + (5.0 * (it - 8) / 7.0) },
            farHip = List(16) { 170.0 },
            trunkLean = (0..15).map { if (it < 8) 2.0 + (6.0 * it / 8.0) else 8.0 - (6.0 * (it - 8) / 7.0) },
        )
        val v = judge(
            ExerciseLibrary.lunge,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 70.0,
                movementContext = curlContext,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
    }

    @Test
    fun lunge_rejects_collapsed_torso_as_wrong_exercise() {
        // Torso bent forward excessively (> 28 deg)
        val collapsedContext = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (85.0 * it / 8.0) else 85.0 + (85.0 * (it - 8) / 7.0) },
            farHip = (0..15).map { if (it < 8) 170.0 - (20.0 * it / 8.0) else 150.0 + (20.0 * (it - 8) / 7.0) },
            trunkLean = (0..15).map { if (it < 8) 5.0 + (30.0 * it / 8.0) else 35.0 - (30.0 * (it - 8) / 7.0) },
        )
        val v = judge(
            ExerciseLibrary.lunge,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 70.0,
                movementContext = collapsedContext,
            ),
        )
        assertEquals(RepOutcome.WRONG_EXERCISE, v.outcome)
        assertFalse(v.outcome.counts)
    }

    @Test
    fun clean_lunge_is_counted() {
        // True lunge: upright torso (< 20 deg), front hip flexes (85 deg), rear hip stays extended (150 deg)
        val lungeContext = movementContextTrajectory(
            nearHip = (0..15).map { if (it < 8) 170.0 - (85.0 * it / 8.0) else 85.0 + (85.0 * (it - 8) / 7.0) },
            farHip = (0..15).map { if (it < 8) 170.0 - (20.0 * it / 8.0) else 150.0 + (20.0 * (it - 8) / 7.0) },
            trunkLean = (0..15).map { if (it < 8) 3.0 + (5.0 * it / 8.0) else 8.0 - (5.0 * (it - 8) / 7.0) },
        )
        val v = judge(
            ExerciseLibrary.lunge,
            rep(
                RepValidity.VALID,
                durationMs = 2_000.0,
                side = Side.RIGHT,
                rightPeak = 170.0,
                rightTrough = 70.0,
                movementContext = lungeContext,
            ),
        )
        assertEquals(RepOutcome.COUNTED, v.outcome)
        assertTrue(v.outcome.counts)
    }

    // --- Helpers -------------------------------------------------------------------------------

    private fun judge(profile: RepetitionExercise, rep: RepRecord): RepVerdict = RepVerdicts.judge(profile, rep, emptyList())

    private fun rep(
        validity: RepValidity,
        durationMs: Double,
        index: Int = 1,
        side: Side? = null,
        startMs: Double = 0.0,
        leftPeak: Double = Double.NaN,
        leftTrough: Double = Double.NaN,
        rightPeak: Double = Double.NaN,
        rightTrough: Double = Double.NaN,
        movementContext: MovementContext? = null,
    ) = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = startMs,
        endMs = startMs + durationMs,
        validity = validity,
        rangeOfMotion = BilateralSignal(leftPeak - leftTrough, rightPeak - rightTrough),
        peakValue = BilateralSignal(leftPeak, rightPeak),
        troughValue = BilateralSignal(leftTrough, rightTrough),
        descentMs = durationMs / 2.0,
        ascentMs = durationMs / 2.0,
        resampledLeft = DoubleArray(64) { Double.NaN },
        resampledRight = DoubleArray(64) { Double.NaN },
        movementContext = movementContext,
    )

    private fun movementContextTrajectory(
        nearHip: List<Double>,
        farHip: List<Double> = List(16) { 170.0 },
        trunkLean: List<Double> = List(16) { 10.0 },
        durationMs: Double = 2_000.0,
        nearSide: Side = Side.RIGHT,
    ): MovementContext {
        val f = DoubleArray(MovementContext.FEATURE_COUNT)
        for (s in 0 until 9) {
            val defaultVal = MovementContextTracker.DEFAULT_ANGLES[s]
            for (p in 0 until 16) {
                f[s * 16 + p] = defaultVal
            }
        }
        val nearSignal = if (nearSide == Side.LEFT) 4 else 5
        val farSignal = if (nearSide == Side.LEFT) 5 else 4
        for (p in 0 until 16) {
            f[nearSignal * 16 + p] = nearHip[p]
            f[farSignal * 16 + p] = farHip[p]
            f[8 * 16 + p] = trunkLean[p]
        }
        f[144] = durationMs
        return MovementContext(f)
    }
}
