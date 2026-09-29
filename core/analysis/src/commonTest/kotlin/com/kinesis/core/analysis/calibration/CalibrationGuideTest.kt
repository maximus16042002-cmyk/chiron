package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The words before a calibration set - DECISIONS.md P3-22. They quote the
 * policy's own numbers and the profile's own view, so they cannot promise one
 * thing while the calibrator requires another.
 */
class CalibrationGuideTest {

    @Test
    fun repetition_instructions_quote_the_policy_and_the_required_view() {
        val text = CalibrationGuide.instructions(ExerciseLibrary.squat)
        for (part in listOf("usual", "whole body", "side-on", "5", "8", "valid")) {
            assertTrue(text.contains(part), "'$part' missing: $text")
        }
    }

    @Test
    fun an_alternating_exercise_asks_for_the_count_on_each_side() {
        val text = CalibrationGuide.instructions(ExerciseLibrary.bicepCurl)
        assertTrue(text.contains("each side"), text)
        assertTrue(text.contains("side-on"), text)
    }

    /**
     * DECISIONS.md P3-32: filmed side-on, one limb at a time, the far limb is behind the
     * body, so the user turns round for the other side. The curl and the knee raise.
     */
    @Test
    fun a_side_on_one_limb_at_a_time_exercise_says_to_turn_round_for_the_other_side() {
        for (profile in listOf(ExerciseLibrary.bicepCurl, ExerciseLibrary.kneeRaise)) {
            val text = CalibrationGuide.instructions(profile)
            assertTrue(text.contains("turn round"), "${profile.id}: $text")
            assertTrue(text.contains("nearer the camera"), "${profile.id}: $text")
        }
        // Front-on or both-together exercises are not told to turn.
        for (profile in listOf(ExerciseLibrary.hipAbduction, ExerciseLibrary.squat, ExerciseLibrary.lateralRaise)) {
            assertTrue(!CalibrationGuide.instructions(profile).contains("turn round"), profile.id)
        }
    }

    @Test
    fun a_near_side_only_exercise_asks_for_the_count_on_the_side_it_sees() {
        val text = CalibrationGuide.instructions(ExerciseLibrary.row)
        assertTrue(text.contains("the side nearer the camera"), text)
    }

    @Test
    fun hold_instructions_ask_for_one_hold_of_the_policy_duration() {
        for (entry in ExerciseLibrary.all.filterIsInstance<IsometricExercise>()) {
            val text = CalibrationGuide.instructions(entry)
            assertTrue(text.contains("10 seconds"), text)
            assertTrue(text.contains("whole body"), text)
        }
    }

    /** The provisional status is said to the user, not only to the reader of this code. */
    @Test
    fun every_instruction_says_the_numbers_are_provisional() {
        for (entry in ExerciseLibrary.profiles) {
            assertTrue(CalibrationGuide.instructions(entry).contains("provisional"), entry.id)
        }
    }

    /** CLAUDE.md: Kinesis is not a medical tool, and its calibration makes no judgement. */
    @Test
    fun no_instruction_makes_a_claim_about_correctness() {
        for (entry in ExerciseLibrary.profiles) {
            val text = CalibrationGuide.instructions(entry).lowercase()
            for (word in listOf("correct", "diagnos", "injur", "proper form", "perfect")) {
                assertTrue(!text.contains(word), "'$word' in ${entry.id}: $text")
            }
        }
    }
}
