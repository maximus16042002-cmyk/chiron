package com.kinesis.core.analysis.verifier

import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity

/**
 * The exercise verifier as an experimental, off-by-default check on the set summary -
 * DECISIONS.md P3-85.
 *
 * Once per finished set, never per frame (CLAUDE.md sections 6 and 13): each counted repetition's
 * [com.kinesis.core.domain.rep.MovementContext] goes through [ExerciseVerifier], and the set gets
 * one line. It only reports. It never changes the exercise, the count or any record.
 *
 * Only the four exercises the model saw from the app's camera view are judged
 * ([ExerciseVerifier.VERIFIED_EXERCISES]). Even there, some people's correct repetitions were
 * warned 33-100% of the time (P3-59), which is why it is experimental and off by default.
 */
object VerifierSetCheck {

    const val NOT_COVERED: String =
        "This check covers only push-ups, sit-ups, lateral raises and shoulder presses."

    /**
     * One line for the set summary, or `null` when nothing could be judged: no counted
     * repetition, or none carrying movement data (records read back from storage have none).
     */
    fun summarise(exerciseId: String, reps: List<RepRecord>, verifier: ExerciseVerifier = ExerciseVerifier()): String? {
        if (exerciseId !in ExerciseVerifier.VERIFIED_EXERCISES) return NOT_COVERED
        val judged = reps
            .filter { it.validity == RepValidity.VALID && it.movementContext != null }
            .map { verifier.verify(exerciseId, it) }
            .filter { it !is VerificationResult.Unverified }
        if (judged.isEmpty()) return null

        val name = ExerciseVerifier.displayNameOf(exerciseId)
        val mismatches = judged.filterIsInstance<VerificationResult.Mismatch>()
        if (mismatches.isEmpty()) {
            return if (judged.size == 1) {
                "The counted repetition looked like a $name."
            } else {
                "All ${judged.size} counted repetitions looked like a $name."
            }
        }
        val mostOften = mismatches.groupingBy { it.predictedExercise }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .first().key
        return "${mismatches.size} of ${judged.size} counted repetitions looked like a different exercise, " +
            "most often ${ExerciseVerifier.displayNameOf(mostOften)}."
    }
}
