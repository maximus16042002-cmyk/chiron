package com.kinesis.core.analysis.verifier

import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Result of exercise verification for a completed repetition (P3-34, P3-52).
 */
sealed interface VerificationResult {

    /**
     * The movement was verified to match the user's selected exercise.
     */
    data class Match(
        val selectedExercise: String,
        val predictedExercise: String,
        val confidence: Double,
        val probabilities: DoubleArray,
    ) : VerificationResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Match) return false
            return selectedExercise == other.selectedExercise &&
                predictedExercise == other.predictedExercise &&
                confidence == other.confidence &&
                probabilities.contentEquals(other.probabilities)
        }

        override fun hashCode(): Int =
            31 * (31 * selectedExercise.hashCode() + predictedExercise.hashCode()) + confidence.hashCode()
    }

    /**
     * A movement mismatch was detected: user selected [selectedExercise], but kinematic context
     * matches [predictedExercise] with high confidence.
     *
     * Produces a Level 2 [finding] (warning only). Must NEVER auto-select or change the exercise (CLAUDE.md §13).
     */
    data class Mismatch(
        val selectedExercise: String,
        val predictedExercise: String,
        val confidence: Double,
        val selectedProbability: Double,
        val probabilities: DoubleArray,
        val finding: Deviation,
    ) : VerificationResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Mismatch) return false
            return selectedExercise == other.selectedExercise &&
                predictedExercise == other.predictedExercise &&
                confidence == other.confidence &&
                selectedProbability == other.selectedProbability &&
                probabilities.contentEquals(other.probabilities) &&
                finding == other.finding
        }

        override fun hashCode(): Int =
            31 * (31 * selectedExercise.hashCode() + predictedExercise.hashCode()) + confidence.hashCode()
    }

    /**
     * The movement or exercise could not be verified (e.g. unverified exercise profile, missing context).
     */
    data class Unverified(
        val selectedExercise: String,
        val reason: String,
    ) : VerificationResult
}

/**
 * Raw multiclass prediction output across all model classes.
 */
data class VerifierPrediction(
    val predictedClass: String,
    val confidence: Double,
    val probabilities: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerifierPrediction) return false
        return predictedClass == other.predictedClass &&
            confidence == other.confidence &&
            probabilities.contentEquals(other.probabilities)
    }

    override fun hashCode(): Int =
        31 * (31 * predictedClass.hashCode() + confidence.hashCode()) + probabilities.contentHashCode()
}

/**
 * Real-time post-rep Exercise Verifier (P3-34, P3-52).
 *
 * Consumes the 145-dimensional [MovementContext] on the post-rep path and evaluates multinomial
 * logistic regression probabilities across MM-Fit's ten movement classes.
 *
 * Strictly adheres to research protocol rules:
 * - Emits mismatch warnings only; NEVER automatically switches the exercise.
 * - Runs only upon repetition completion ($O(1)$ per rep evaluation, never per-frame ML).
 * - Declares unsupported exercises unverified.
 */
class ExerciseVerifier(
    val model: ExerciseVerifierModel = PretrainedExerciseVerifierModel.INSTANCE,
) {

    /**
     * Evaluates raw multiclass probabilities over a 145-feature vector.
     */
    fun evaluate(features: DoubleArray): VerifierPrediction {
        require(features.size == ExerciseVerifierModel.FEATURE_COUNT) {
            "features size (${features.size}) must be ${ExerciseVerifierModel.FEATURE_COUNT}"
        }

        val classCount = model.classes.size
        val logits = DoubleArray(classCount)

        // 1. Compute standardized features and linear logits: z_c = b_c + W_c * ((x - mu) / sigma)
        for (c in 0 until classCount) {
            var z = model.intercepts[c]
            val w = model.weights[c]
            for (i in 0 until ExerciseVerifierModel.FEATURE_COUNT) {
                val xStd = (features[i] - model.scalerMean[i]) / model.scalerScale[i]
                z += w[i] * xStd
            }
            logits[c] = z
        }

        // 2. Numerically stable softmax probabilities
        var maxLogit = Double.NEGATIVE_INFINITY
        for (z in logits) {
            if (z > maxLogit) maxLogit = z
        }

        var sumExp = 0.0
        val exps = DoubleArray(classCount)
        for (c in 0 until classCount) {
            val e = exp(logits[c] - maxLogit)
            exps[c] = e
            sumExp += e
        }

        val probabilities = DoubleArray(classCount)
        var bestIdx = 0
        var maxProb = -1.0
        for (c in 0 until classCount) {
            val p = exps[c] / sumExp
            probabilities[c] = p
            if (p > maxProb) {
                maxProb = p
                bestIdx = c
            }
        }

        return VerifierPrediction(
            predictedClass = model.classes[bestIdx],
            confidence = maxProb,
            probabilities = probabilities,
        )
    }

    /**
     * Verifies a completed repetition's [MovementContext] against the user's selected exercise.
     */
    fun verify(selectedExerciseId: String, movementContext: MovementContext?): VerificationResult {
        if (!VERIFIED_EXERCISES.contains(selectedExerciseId)) {
            return VerificationResult.Unverified(
                selectedExercise = selectedExerciseId,
                reason = "Exercise '$selectedExerciseId' is not covered by the exercise verifier (unverified).",
            )
        }

        if (movementContext == null) {
            return VerificationResult.Unverified(
                selectedExercise = selectedExerciseId,
                reason = "No movement context available for repetition.",
            )
        }

        val prediction = evaluate(movementContext.features)
        val selectedIdx = model.classes.indexOf(selectedExerciseId)
        val pSelected = if (selectedIdx >= 0) prediction.probabilities[selectedIdx] else 0.0

        // Mismatch condition: selected probability is very low, predicted probability is high, and classes differ
        val isMismatch = pSelected < model.selectedPMax &&
            prediction.confidence >= model.predictedPMin &&
            prediction.predictedClass != selectedExerciseId

        return if (isMismatch) {
            val predictedDisplay = displayNameOf(prediction.predictedClass)
            val confidencePct = (prediction.confidence * 100).roundToInt()
            val finding = Deviation(
                metric = METRIC_NAME,
                direction = DeviationDirection.UNUSUAL_FOR_YOU,
                displayText = "This looks like a different exercise: $predictedDisplay ($confidencePct%)",
                ruleVersion = model.modelVersion,
            )
            VerificationResult.Mismatch(
                selectedExercise = selectedExerciseId,
                predictedExercise = prediction.predictedClass,
                confidence = prediction.confidence,
                selectedProbability = pSelected,
                probabilities = prediction.probabilities,
                finding = finding,
            )
        } else {
            VerificationResult.Match(
                selectedExercise = selectedExerciseId,
                predictedExercise = prediction.predictedClass,
                confidence = prediction.confidence,
                probabilities = prediction.probabilities,
            )
        }
    }

    /**
     * Convenience method for verifying a [RepRecord].
     */
    fun verify(selectedExerciseId: String, rep: RepRecord): VerificationResult =
        verify(selectedExerciseId, rep.movementContext)

    companion object {
        const val METRIC_NAME: String = "exercise_verifier"

        /**
         * Exercises covered and validated by MM-Fit and the verifier model.
         * Uncovered exercises (plank, side plank, wall sit, glute bridge, etc.) receive no verification.
         */
        val VERIFIED_EXERCISES: Set<String> = setOf(
            "bicep_curl",
            "lateral_raise",
            "lunge",
            "pushup",
            "row",
            "shoulder_press",
            "situp",
            "squat",
        )

        fun displayNameOf(classId: String): String = when (classId) {
            "bicep_curl" -> "Bicep Curl"
            "jumping_jacks" -> "Jumping Jacks"
            "lateral_raise" -> "Lateral Raise"
            "lunge" -> "Lunge"
            "pushup" -> "Push-up"
            "row" -> "Row"
            "shoulder_press" -> "Shoulder Press"
            "situp" -> "Sit-up"
            "squat" -> "Squat"
            "tricep_extension" -> "Tricep Extension"
            else -> classId
        }
    }
}
