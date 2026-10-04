package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture

/**
 * Judges one frame's setup for a profile - DECISIONS.md P3-55, corrected by P3-59.
 *
 * Implements CLAUDE.md section 10 and CONTRACTS.md section 8. The first matching verdict wins:
 * nobody in shot, then the camera's view (the one check that blocks scoring), then distance,
 * head and feet, which only advise.
 *
 * **Units.** Landmarks arrive with both axes in frame widths (P3-16): y runs from 0 to the
 * frame's height over its width, about 1.33 on the phone's portrait frame. So nothing here
 * compares a y against the bottom of the frame - the core does not know where that is. Feet are
 * judged by whether the ankles are seen, and only the top edge (y = 0) is a position.
 */
object SetupAssistant {

    /** Torso length bounds, in frame widths. Unmeasured on the phone, so they only advise. */
    const val MIN_TORSO_SCALE: Double = 0.12
    const val MAX_TORSO_SCALE: Double = 0.68

    /** The nose above this y is at the top edge of the frame. */
    const val TOP_MARGIN_Y: Double = 0.03

    fun evaluate(
        profile: ExerciseProfile,
        landmarks: Landmarks,
        policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    ): SetupVerdict {
        if (!LandmarkNormalizer.hasMeasurableTorso(landmarks)) return SetupVerdict.NoPerson
        val torsoScale = LandmarkNormalizer.torsoScale(landmarks)

        if (viewCanBeJudged(profile)) {
            val detected = CameraViewEstimator.estimate(landmarks, torsoScale)?.view
            if (detected != null && !suits(detected, profile.requiredView)) {
                return SetupVerdict.WrongView(detectedView = detected, requiredView = profile.requiredView)
            }
        }

        if (torsoScale < MIN_TORSO_SCALE) return SetupVerdict.TooFar
        if (torsoScale > MAX_TORSO_SCALE) return SetupVerdict.TooClose

        val noseY = landmarks.y(LandmarkId.NOSE)
        if (policy.isMeasurable(landmarks.visibility(LandmarkId.NOSE)) && noseY < TOP_MARGIN_Y) {
            return SetupVerdict.HeadOutOfFrame
        }

        // One hidden ankle is normal side-on; both unseen means the feet are cut off.
        if (profile.posture == Posture.STANDING &&
            !policy.isMeasurable(landmarks.visibility(LandmarkId.LEFT_ANKLE)) &&
            !policy.isMeasurable(landmarks.visibility(LandmarkId.RIGHT_ANKLE))
        ) {
            return SetupVerdict.FeetOutOfFrame
        }

        return SetupVerdict.Ready
    }

    /**
     * The estimator compares the left-right span *across* the frame with the torso. That
     * separates side-on from front-on for an upright body, and for a body lying side-on to the
     * camera (MM-Fit's push-ups and sit-ups, P3-55). It cannot see a lying body from the front:
     * a side plank's shoulders are stacked vertically, so their span across the frame is near
     * zero whichever way the camera faces. Those profiles are not judged by view (P3-59).
     */
    private fun viewCanBeJudged(profile: ExerciseProfile): Boolean =
        profile.requiredView == CameraView.SIDE_ON || profile.posture in UPRIGHT

    private fun suits(detected: CameraView, required: CameraView): Boolean = when (required) {
        CameraView.SIDE_ON, CameraView.FRONT_ON -> detected == required
        // No profile requires it today; an angled view sits between the two thresholds.
        CameraView.ANGLED_45 -> detected == CameraView.ANGLED_45
    }

    private val UPRIGHT = setOf(Posture.STANDING, Posture.SEATED, Posture.HINGED)
}
