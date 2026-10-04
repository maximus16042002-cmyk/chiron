package com.kinesis.core.analysis.verifier

import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.MovementContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Parity and verification test for the Exercise Verifier - DECISIONS.md P3-52, CLAUDE.md §18.
 *
 * Verifies that pure Kotlin evaluation matches Python scikit-learn LogisticRegression
 * to <= 1e-9 relative parity across all classes on held-out test vectors.
 *
 * Also verifies Level 2 mismatch finding emissions and unverified exercise safeguards.
 */
class ExerciseVerifierParityTest {

    private val verifier = ExerciseVerifier()

    @Test
    fun evaluation_matches_python_logistic_regression_probabilities_to_within_1e9() {
        assertTrue(VerifierParityGolden.VECTORS.isNotEmpty(), "Golden vectors must not be empty")

        for (v in VerifierParityGolden.VECTORS) {
            val prediction = verifier.evaluate(v.rawFeatures)

            assertEquals(
                v.expectedPrediction,
                prediction.predictedClass,
                "Vector ${v.sampleIndex} (${v.trueExercise}) predicted class mismatch",
            )

            for (c in 0 until PretrainedExerciseVerifierModel.CLASSES.size) {
                val actual = prediction.probabilities[c]
                val expected = v.expectedProbabilities[c]
                val diff = abs(actual - expected)
                val tol = 1e-9

                assertTrue(
                    diff < tol,
                    "Vector ${v.sampleIndex} (${v.trueExercise}) class ${PretrainedExerciseVerifierModel.CLASSES[c]}: " +
                        "actual=$actual, expected=$expected, diff=$diff > $tol",
                )
            }
        }
    }

    @Test
    fun matched_movement_emits_verification_match() {
        // Find a confident push-up vector
        val pushupVector = VerifierParityGolden.VECTORS.first { it.trueExercise == "pushup" }
        val ctx = MovementContext(pushupVector.rawFeatures)

        val result = verifier.verify("pushup", ctx)
        val match = assertIs<VerificationResult.Match>(result)
        assertEquals("pushup", match.selectedExercise)
        assertEquals("pushup", match.predictedExercise)
        assertTrue(match.confidence >= 0.50)
    }

    @Test
    fun mismatched_movement_emits_level_two_deviation_warning_only() {
        // User selected bicep_curl, but performed push-up
        val pushupVector = VerifierParityGolden.VECTORS.first { it.trueExercise == "pushup" }
        val ctx = MovementContext(pushupVector.rawFeatures)

        val result = verifier.verify("bicep_curl", ctx)
        val mismatch = assertIs<VerificationResult.Mismatch>(result)

        assertEquals("bicep_curl", mismatch.selectedExercise)
        assertEquals("pushup", mismatch.predictedExercise)
        assertTrue(mismatch.confidence >= 0.50)
        assertTrue(mismatch.selectedProbability < 0.25)

        // Check finding details (CLAUDE.md §13: warning only, never auto-selects)
        val finding = mismatch.finding
        assertEquals(ClaimLevel.L2_DEVIATION, finding.level)
        assertEquals(DeviationDirection.UNUSUAL_FOR_YOU, finding.direction)
        assertEquals(ExerciseVerifier.METRIC_NAME, finding.metric)
        assertEquals(PretrainedExerciseVerifierModel.MODEL_VERSION, finding.ruleVersion)
        assertTrue("This looks like a different exercise: Push-up" in finding.displayText)
    }

    @Test
    fun unverified_exercises_receive_explicit_unverified_result() {
        val pushupVector = VerifierParityGolden.VECTORS.first()
        val ctx = MovementContext(pushupVector.rawFeatures)

        val unverifiedProfiles = listOf("plank", "side_plank", "wall_sit", "glute_bridge", "hip_abduction", "knee_raise", "leg_raise")
        for (profileId in unverifiedProfiles) {
            val result = verifier.verify(profileId, ctx)
            val unverified = assertIs<VerificationResult.Unverified>(result)
            assertEquals(profileId, unverified.selectedExercise)
            assertTrue("not covered" in unverified.reason)
        }
    }

    @Test
    fun null_movement_context_returns_unverified() {
        val result = verifier.verify("pushup", null as MovementContext?)
        val unverified = assertIs<VerificationResult.Unverified>(result)
        assertEquals("pushup", unverified.selectedExercise)
        assertTrue("No movement context" in unverified.reason)
    }

    @Test
    fun probabilities_sum_to_one() {
        for (v in VerifierParityGolden.VECTORS) {
            val prediction = verifier.evaluate(v.rawFeatures)
            val sum = prediction.probabilities.sum()
            assertTrue(abs(sum - 1.0) < 1e-12, "Probabilities sum ($sum) must be 1.0")
        }
    }
}
