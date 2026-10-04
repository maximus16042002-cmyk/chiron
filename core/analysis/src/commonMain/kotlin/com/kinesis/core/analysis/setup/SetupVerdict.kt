package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.profile.CameraView

/**
 * The evaluation verdict for user setup and framing before and during exercise scoring.
 *
 * Implements CLAUDE.md section 10 and CONTRACTS.md section 8.
 * All coaching messages are composed in the shared core.
 */
sealed class SetupVerdict {

    abstract val isScoringPermitted: Boolean
    abstract val coachingMessage: String

    /** Framing and view match the exercise profile's requirements. Scoring permitted. */
    data class Ready(
        val detectedView: CameraView,
        val confidence: Double,
    ) : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = true
        override val coachingMessage: String get() = "Ready"
    }

    /** No person detected in the camera frame. */
    object NoPerson : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Position yourself in camera view"
    }

    /** User is too close to the camera (torso fills too much of the screen). */
    object TooClose : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Step back from the camera"
    }

    /** User is too far from the camera (torso is too small for reliable tracking). */
    object TooFar : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Move closer to the camera"
    }

    /** The head/face is cropped at the top of the screen. */
    object HeadOutOfFrame : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Step back — head is out of frame"
    }

    /** Feet are cropped at the bottom of the screen on a standing movement. */
    object FeetOutOfFrame : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Step back — feet must be in frame"
    }

    /** One or more required landmarks for this exercise are occluded or unmeasurable. */
    data class LandmarksMissing(val missingSummary: String) : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = "Full body must be visible"
    }

    /** Camera view angle does not match what the profile requires. */
    data class WrongView(
        val detectedView: CameraView,
        val requiredView: CameraView,
    ) : SetupVerdict() {
        override val isScoringPermitted: Boolean get() = false
        override val coachingMessage: String get() = when (requiredView) {
            CameraView.SIDE_ON -> "Turn sideways to camera (side view required)"
            CameraView.FRONT_ON -> "Face the camera directly (front view required)"
            CameraView.ANGLED_45 -> "Stand at a 45° angle to the camera"
        }
    }
}
