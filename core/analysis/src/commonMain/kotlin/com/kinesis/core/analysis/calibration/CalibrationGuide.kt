package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture

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
        val filmed = "filmed " + view(profile.requiredView) + ", " + posture(profile.posture)
        return when (profile) {
            is IsometricExercise ->
                "Hold the position the way you usually do, for at least ${seconds(policy.minHoldMs)} seconds, " +
                    "with your whole body in frame, $filmed. Chiron records where you usually hold it and " +
                    "how steadily. These numbers are provisional."

            is RepetitionExercise -> {
                val where = when {
                    profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY -> " on the side nearer the camera"
                    profile.repetitionSemantics == RepetitionSemantics.PER_LIMB -> " on each side"
                    else -> ""
                }
                // DECISIONS.md P3-32: side-on, the far limb is behind the body, so one side
                // at a time, each nearer the camera in turn.
                val turn = if (turnsRound(profile)) {
                    " Work one side at a time with that side nearer the camera, then turn round for the other side."
                } else {
                    ""
                }
                "Do your usual repetitions, the way you normally train, with your whole body in frame, " +
                    "$filmed.$turn Then end the set. Chiron learns your baseline from your first " +
                    "${policy.maxValidRepsPerSide} valid repetitions$where, and needs at least " +
                    "${policy.minValidRepsPerSide}$where. Repetitions that are cut short or not seen clearly " +
                    "are not used. These numbers are provisional."
            }

            else -> "Chiron has no calibration for ${profile.displayName}."
        }
    }

    /**
     * How to set the phone up for [profile] - shown on every exercise's page (DECISIONS.md P3-48).
     *
     * The view is the profile's own `requiredView`, so the page cannot ask for one view while the
     * analysis assumes another (CLAUDE.md section 10). The limits are the pose model card's
     * out-of-scope list (P3-17): the whole body with the head visible, within about 4 m, one person.
     * The side-on, one-side-at-a-time advice is the same sentence the calibration instructions give.
     */
    fun placement(profile: ExerciseProfile): String {
        val turn = if (profile is RepetitionExercise && turnsRound(profile)) {
            " Work one side at a time with that side nearer the camera, then turn round for the other side."
        } else {
            ""
        }
        val near = if (profile is RepetitionExercise && profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY) {
            " Chiron measures the side nearer the camera."
        } else {
            ""
        }
        return "Film yourself " + view(profile.requiredView) + ", " + posture(profile.posture) + ". " +
            "Stand the phone up so your whole body is in the frame, head to feet, within about 4 metres, " +
            "with only you in view and good light." + turn + near
    }

    // DECISIONS.md P3-32: side-on, the far limb is behind the body, so one side at a time.
    private fun turnsRound(profile: RepetitionExercise): Boolean =
        profile.requiredView == CameraView.SIDE_ON && profile.repetitionSemantics == RepetitionSemantics.PER_LIMB

    private fun posture(posture: Posture): String = when (posture) {
        Posture.STANDING -> "standing"
        Posture.SEATED -> "seated"
        Posture.HINGED -> "bent forward at the hips"
        Posture.PRONE -> "face down"
        Posture.SUPINE -> "lying on your back"
        Posture.SIDE_LYING -> "lying on your side"
    }

    private fun view(view: CameraView): String = when (view) {
        CameraView.FRONT_ON -> "front-on, facing the camera"
        CameraView.SIDE_ON -> "side-on, your side to the camera"
        CameraView.ANGLED_45 -> "at about 45 degrees to the camera"
    }

    private fun seconds(ms: Double): Int = (ms / 1000.0).toInt()
}
