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
        for (part in listOf("usual", "whole body", "side-on", "10", "15", "valid")) {
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

    // --- Placement, on every exercise's page - DECISIONS.md P3-48 --------------------------------

    /**
     * The view in the setup text is the profile's own `requiredView` (CLAUDE.md section 10: every
     * profile declares its view, and nothing is analysed from an unsuitable one), for all 17
     * entries, variants included.
     */
    @Test
    fun every_entry_s_placement_names_its_own_required_view() {
        for (entry in ExerciseLibrary.all) {
            val text = CalibrationGuide.placement(entry)
            val view = when (entry.requiredView.name) {
                "FRONT_ON" -> "front-on"
                "SIDE_ON" -> "side-on"
                else -> "45 degrees"
            }
            assertTrue(text.contains(view), "${entry.id} wants $view: $text")
        }
    }

    /** The pose model card's out-of-scope list (P3-17): the whole body, one person, within about 4 m. */
    @Test
    fun every_placement_carries_the_model_card_s_limits() {
        for (entry in ExerciseLibrary.all) {
            val text = CalibrationGuide.placement(entry)
            for (part in listOf("whole body", "head to feet", "4 metres", "only you")) {
                assertTrue(text.contains(part), "'$part' missing for ${entry.id}: $text")
            }
        }
    }

    @Test
    fun placement_says_to_turn_round_exactly_where_the_instructions_do() {
        for (entry in ExerciseLibrary.all) {
            assertTrue(
                CalibrationGuide.placement(entry).contains("turn round") ==
                    CalibrationGuide.instructions(entry).contains("turn round"),
                entry.id,
            )
        }
        assertTrue(CalibrationGuide.placement(ExerciseLibrary.row).contains("the side nearer the camera"))
    }

    @Test
    fun postures_are_said_in_plain_words() {
        for (entry in ExerciseLibrary.all) {
            val text = CalibrationGuide.placement(entry) + " " + CalibrationGuide.instructions(entry)
            for (jargon in listOf("supine", "prone", "hinged", "side lying", "side_lying")) {
                assertTrue(!text.contains(jargon), "'$jargon' in ${entry.id}: $text")
            }
        }
    }

    @Test
    fun no_placement_makes_a_claim_about_correctness() {
        for (entry in ExerciseLibrary.all) {
            val text = CalibrationGuide.placement(entry).lowercase()
            for (word in listOf("correct", "diagnos", "injur", "proper form", "perfect")) {
                assertTrue(!text.contains(word), "'$word' in ${entry.id}: $text")
            }
        }
    }
}
