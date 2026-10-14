package com.kinesis.core.analysis.verifier

import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The experimental exercise check on the set summary - DECISIONS.md P3-85. A synthetic model
 * whose class k answers to feature k, so every verdict here is certain and none depends on the
 * trained weights.
 */
class VerifierSetCheckTest {

    private val classes = listOf("pushup", "situp", "squat")
    private val n = ExerciseVerifierModel.FEATURE_COUNT

    private val verifier = ExerciseVerifier(
        ExerciseVerifierModel(
            modelVersion = "test",
            classes = classes,
            featureNames = List(n) { "f$it" },
            scalerMean = DoubleArray(n),
            scalerScale = DoubleArray(n) { 1.0 },
            intercepts = DoubleArray(classes.size),
            weights = Array(classes.size) { c -> DoubleArray(n) { i -> if (i == c) 10.0 else 0.0 } },
        ),
    )

    /** A counted repetition the model reads as [looksLike]. */
    private fun rep(index: Int, looksLike: String, validity: RepValidity = RepValidity.VALID): RepRecord {
        val base = oneSided(index, Side.RIGHT, 60.0, attributed = null, validity = validity)
        val features = DoubleArray(n).also { it[classes.indexOf(looksLike)] = 1.0 }
        return RepRecord(
            base.repIndex, base.attributedSide, base.startMs, base.endMs, base.validity,
            base.rangeOfMotion, base.peakValue, base.troughValue, base.descentMs, base.ascentMs,
            base.resampledLeft, base.resampledRight, base.form, MovementContext(features),
        )
    }

    @Test
    fun every_counted_repetition_matching_says_so() {
        val line = VerifierSetCheck.summarise("pushup", List(4) { rep(it + 1, "pushup") }, verifier)
        assertEquals("All 4 counted repetitions looked like a Push-up.", line)
    }

    @Test
    fun repetitions_that_look_like_another_exercise_are_counted_and_named() {
        val reps = listOf(rep(1, "pushup"), rep(2, "squat"), rep(3, "squat"), rep(4, "situp"))
        val line = VerifierSetCheck.summarise("pushup", reps, verifier)
        assertEquals("3 of 4 counted repetitions looked like a different exercise, most often Squat.", line)
    }

    /** It judges only counted repetitions: attempts that were not counted say nothing. */
    @Test
    fun only_counted_repetitions_are_judged() {
        val reps = listOf(rep(1, "pushup"), rep(2, "squat", RepValidity.ABORTED), rep(3, "squat", RepValidity.INCOMPLETE))
        assertEquals("The counted repetition looked like a Push-up.", VerifierSetCheck.summarise("pushup", reps, verifier))
    }

    /** A repetition read back from storage has no movement context, so there is nothing to judge. */
    @Test
    fun repetitions_without_movement_data_give_no_line() {
        val stored = listOf(oneSided(1, Side.RIGHT, 60.0, attributed = null))
        assertNull(VerifierSetCheck.summarise("pushup", stored, verifier))
        assertNull(VerifierSetCheck.summarise("pushup", emptyList(), verifier))
    }

    /** Never a verdict on an exercise the model was not measured on from the app's view (P3-59). */
    @Test
    fun an_uncovered_exercise_is_told_what_the_check_covers_and_is_not_judged() {
        for (id in listOf("squat", "bicep_curl", "plank", "glute_bridge")) {
            val line = VerifierSetCheck.summarise(id, listOf(rep(1, "squat")), verifier)
            assertEquals(VerifierSetCheck.NOT_COVERED, line, id)
        }
        assertTrue(VerifierSetCheck.NOT_COVERED.contains("push-ups"))
    }

    /** The shipped model runs on a covered exercise's repetitions without failing. */
    @Test
    fun the_shipped_model_gives_a_line_for_a_covered_exercise() {
        val reps = listOf(rep(1, "pushup"))
        assertTrue(VerifierSetCheck.summarise("pushup", reps)!!.contains("counted repetition"))
    }
}
