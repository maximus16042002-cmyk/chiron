package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.ExerciseProfile

/**
 * Stateful gate that monitors setup readiness with debounce and hysteresis.
 *
 * Implements CLAUDE.md section 10: blocks scoring when setup is clearly invalid.
 *
 * Requires [REQUIRED_READY_FRAMES] consecutive valid frames (~300ms at 30fps)
 * before declaring [isReady] true. Once ready, brief momentary glitches do not
 * immediately drop readiness; [UNREADY_DEBOUNCE_FRAMES] consecutive invalid
 * frames are required to revoke readiness.
 */
class SetupGate(
    val profile: ExerciseProfile,
    val policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
) {
    var latestVerdict: SetupVerdict = SetupVerdict.NoPerson
        private set

    var isReady: Boolean = false
        private set

    private var consecutiveReadyFrames: Int = 0
    private var consecutiveUnreadyFrames: Int = 0

    fun onFrame(landmarks: Landmarks): SetupVerdict {
        val verdict = SetupAssistant.evaluate(profile, landmarks, policy)
        latestVerdict = verdict

        if (verdict.isScoringPermitted) {
            consecutiveReadyFrames++
            consecutiveUnreadyFrames = 0
            if (consecutiveReadyFrames >= REQUIRED_READY_FRAMES) {
                isReady = true
            }
        } else {
            consecutiveUnreadyFrames++
            consecutiveReadyFrames = 0
            if (consecutiveUnreadyFrames >= UNREADY_DEBOUNCE_FRAMES) {
                isReady = false
            }
        }

        return verdict
    }

    fun onNothingSeen(): SetupVerdict {
        latestVerdict = SetupVerdict.NoPerson
        consecutiveReadyFrames = 0
        consecutiveUnreadyFrames++
        if (consecutiveUnreadyFrames >= UNREADY_DEBOUNCE_FRAMES) {
            isReady = false
        }
        return SetupVerdict.NoPerson
    }

    fun reset() {
        latestVerdict = SetupVerdict.NoPerson
        isReady = false
        consecutiveReadyFrames = 0
        consecutiveUnreadyFrames = 0
    }

    companion object {
        const val REQUIRED_READY_FRAMES: Int = 10
        const val UNREADY_DEBOUNCE_FRAMES: Int = 15
    }
}
