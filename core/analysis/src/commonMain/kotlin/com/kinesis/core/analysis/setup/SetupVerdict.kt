package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.profile.CameraView

/**
 * What the setup assistant makes of one frame - DECISIONS.md P3-55, corrected by P3-59.
 *
 * Implements CLAUDE.md section 10 and CONTRACTS.md section 8. Every message is composed here, in
 * the shared core.
 *
 * Only two verdicts block scoring: nobody in shot, and a camera view the profile cannot be
 * measured from - P3-31's curls filmed facing the phone, which counted arm swings. The rest are
 * advice: their thresholds have not been measured on the phone, and the tracker already refuses
 * to measure a side it cannot see (P3-2, P3-15).
 *
 * No verdict carries a value that changes from frame to frame. P3-55's `Ready` carried a
 * confidence, and the live screen, comparing verdicts, recomposed on every frame (P3-59).
 */
sealed class SetupVerdict {

    /** Whether, held long enough, this verdict stops repetitions and holds being scored. */
    abstract val blocksScoring: Boolean
    abstract val coachingMessage: String

    /** Framing and view suit the profile. */
    object Ready : SetupVerdict() {
        override val blocksScoring: Boolean get() = false
        override val coachingMessage: String get() = "Ready"
    }

    /** No measurable torso in the frame. */
    object NoPerson : SetupVerdict() {
        override val blocksScoring: Boolean get() = true
        override val coachingMessage: String get() = "Position yourself in camera view"
    }

    /** The torso fills too much of the frame. Advice only. */
    object TooClose : SetupVerdict() {
        override val blocksScoring: Boolean get() = false
        override val coachingMessage: String get() = "Step back from the camera"
    }

    /** The torso is too small for reliable tracking. Advice only. */
    object TooFar : SetupVerdict() {
        override val blocksScoring: Boolean get() = false
        override val coachingMessage: String get() = "Move closer to the camera"
    }

    /** The head is cut off at the top of the frame. Advice only. */
    object HeadOutOfFrame : SetupVerdict() {
        override val blocksScoring: Boolean get() = false
        override val coachingMessage: String get() = "Step back — head is out of frame"
    }

    /** Neither ankle is seen on a standing movement. Advice only. */
    object FeetOutOfFrame : SetupVerdict() {
        override val blocksScoring: Boolean get() = false
        override val coachingMessage: String get() = "Step back — feet must be in frame"
    }

    /** The camera sees the body from a view the profile cannot be measured from. Blocks scoring. */
    data class WrongView(
        val detectedView: CameraView,
        val requiredView: CameraView,
    ) : SetupVerdict() {
        override val blocksScoring: Boolean get() = true
        override val coachingMessage: String get() = when (requiredView) {
            CameraView.SIDE_ON -> "Turn side-on to the camera — counting paused"
            CameraView.FRONT_ON -> "Face the camera — counting paused"
            CameraView.ANGLED_45 -> "Stand at a 45° angle to the camera — counting paused"
        }
    }
}
