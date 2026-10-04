package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.analysis.landmark.ValidityGate
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture
import kotlin.math.sqrt

/**
 * Evaluates camera setup, framing, distance, and view orientation for an exercise profile.
 *
 * Implements CLAUDE.md section 10 and CONTRACTS.md section 8.
 *
 * Pure, allocating nothing beyond the verdict object.
 */
object SetupAssistant {

    const val MIN_TORSO_SCALE: Double = 0.12
    const val MAX_TORSO_SCALE: Double = 0.68

    const val TOP_MARGIN_Y: Double = 0.03
    const val BOTTOM_MARGIN_Y: Double = 0.98

    /**
     * Evaluates whether current [landmarks] satisfy the setup preconditions for [profile].
     */
    fun evaluate(
        profile: ExerciseProfile,
        landmarks: Landmarks,
        policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    ): SetupVerdict {
        // 1. Person presence & torso measurability
        if (!LandmarkNormalizer.hasMeasurableTorso(landmarks)) {
            return SetupVerdict.NoPerson
        }

        val torsoScale = calculateTorsoScale(landmarks)
        if (!torsoScale.isFinite() || torsoScale <= 0.0) {
            return SetupVerdict.NoPerson
        }

        // 2. Distance check (torsoScale)
        if (torsoScale < MIN_TORSO_SCALE) {
            return SetupVerdict.TooFar
        }
        if (torsoScale > MAX_TORSO_SCALE) {
            return SetupVerdict.TooClose
        }

        // 3. Head in-frame check
        val noseY = landmarks.y(LandmarkId.NOSE)
        val noseVis = landmarks.visibility(LandmarkId.NOSE)
        if (noseY.isFinite() && policy.isMeasurable(noseVis) && noseY < TOP_MARGIN_Y) {
            return SetupVerdict.HeadOutOfFrame
        }

        // 4. Standing feet in-frame check
        if (profile.posture == Posture.STANDING) {
            val leftAnkleY = landmarks.y(LandmarkId.LEFT_ANKLE)
            val rightAnkleY = landmarks.y(LandmarkId.RIGHT_ANKLE)
            if (leftAnkleY.isFinite() && leftAnkleY > BOTTOM_MARGIN_Y ||
                rightAnkleY.isFinite() && rightAnkleY > BOTTOM_MARGIN_Y) {
                return SetupVerdict.FeetOutOfFrame
            }
        }

        // 5. Required landmarks check
        val validity = ValidityGate.evaluate(landmarks, profile.requiredLandmarks, policy)
        if (validity == FrameValidity.INVALID) {
            return SetupVerdict.LandmarksMissing("required joints occluded")
        }

        // 6. Camera view angle check
        val orientation = CameraViewEstimator.estimate(landmarks, torsoScale)
        if (orientation != null) {
            val detected = orientation.view
            val required = profile.requiredView

            val isCompatible = when (required) {
                CameraView.SIDE_ON -> detected == CameraView.SIDE_ON
                CameraView.FRONT_ON -> detected == CameraView.FRONT_ON
                CameraView.ANGLED_45 -> detected == CameraView.ANGLED_45 || orientation.confidence < 0.70
            }

            if (!isCompatible) {
                return SetupVerdict.WrongView(detectedView = detected, requiredView = required)
            }

            return SetupVerdict.Ready(detectedView = detected, confidence = orientation.confidence)
        }

        return SetupVerdict.Ready(detectedView = profile.requiredView, confidence = 0.5)
    }

    private fun calculateTorsoScale(landmarks: Landmarks): Double {
        val midShoulderX = (landmarks.x(LandmarkId.LEFT_SHOULDER) + landmarks.x(LandmarkId.RIGHT_SHOULDER)) * 0.5
        val midShoulderY = (landmarks.y(LandmarkId.LEFT_SHOULDER) + landmarks.y(LandmarkId.RIGHT_SHOULDER)) * 0.5
        val midHipX = (landmarks.x(LandmarkId.LEFT_HIP) + landmarks.x(LandmarkId.RIGHT_HIP)) * 0.5
        val midHipY = (landmarks.y(LandmarkId.LEFT_HIP) + landmarks.y(LandmarkId.RIGHT_HIP)) * 0.5
        val dx = midShoulderX - midHipX
        val dy = midShoulderY - midHipY
        return sqrt(dx * dx + dy * dy)
    }
}
