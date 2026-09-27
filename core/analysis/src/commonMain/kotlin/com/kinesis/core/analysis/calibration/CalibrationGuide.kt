package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile

/**
 * What the user is told before a calibration set - DECISIONS.md P3-22.
 *
 * Composed here rather than in the UI so the numbers it quotes are the policy's
 * own and the view is the profile's own; the instructions cannot promise one
 * thing while the calibrator requires another. It asks for the user's usual
 * movement and says nothing about good or bad form: a baseline is a record of
 * what the user normally does, not a standard.
 */
object CalibrationGuide {

    fun instructions(profile: ExerciseProfile, policy: CalibrationPolicy = CalibrationPolicy.PROVISIONAL): String {
        val filmed = "filmed " + view(profile.requiredView) + ", " + profile.posture.name.lowercase().replace('_', ' ')
        return when (profile) {
            is IsometricExercise ->
                "Hold the position the way you usually do, for at least ${seconds(policy.minHoldMs)} seconds, " +
                    "with your whole body in frame, $filmed. Kinesis records where you usually hold it and " +
                    "how steadily. These numbers are provisional."

            is RepetitionExercise -> {
                val where = when {
                    profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY -> " on the side nearer the camera"
                    profile.repetitionSemantics == RepetitionSemantics.PER_LIMB -> " on each side"
                    else -> ""
                }
                "Do your usual repetitions, the way you normally train, with your whole body in frame, " +
                    "$filmed. Then end the set. Kinesis learns your baseline from your first " +
                    "${policy.maxValidRepsPerSide} valid repetitions$where, and needs at least " +
                    "${policy.minValidRepsPerSide}$where. Repetitions that are cut short or not seen clearly " +
                    "are not used. These numbers are provisional."
            }

            else -> "Kinesis has no calibration for ${profile.displayName}."
        }
    }

    private fun view(view: CameraView): String = when (view) {
        CameraView.FRONT_ON -> "front-on, facing the camera"
        CameraView.SIDE_ON -> "side-on, your side to the camera"
        CameraView.ANGLED_45 -> "at about 45 degrees to the camera"
    }

    private fun seconds(ms: Double): Int = (ms / 1000.0).toInt()
}
