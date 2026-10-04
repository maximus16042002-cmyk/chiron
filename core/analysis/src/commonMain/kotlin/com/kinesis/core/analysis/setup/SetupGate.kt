package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.ExerciseProfile

/**
 * The setup assistant's verdicts, held steady over frames - DECISIONS.md P3-55, corrected by
 * P3-59. On the real-time path: O(1) per frame, counters only.
 *
 * Two outputs, each debounced on its own:
 * - [isScoringBlocked]: true until [OPEN_FRAMES] frames in a row have no blocking reason, and
 *   true again after [CLOSE_FRAMES] frames in a row that do. Only nobody in shot and a wrong
 *   camera view block (see [SetupVerdict]). The live analysis decides what blocking does.
 * - [message]: the one line the screen shows, or `null` when there is nothing to say. It changes
 *   only after a new reason has held for [MESSAGE_FRAMES] frames, so a reason that comes and goes
 *   never reaches the screen. The strings are constants, so the screen's equality check on them
 *   is exact and a steady message never recomposes it.
 */
class SetupGate(
    val profile: ExerciseProfile,
    val policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
) {
    /** This frame's verdict, undebounced. Diagnostic; the screen reads [message]. */
    var latestVerdict: SetupVerdict = SetupVerdict.NoPerson
        private set

    var isScoringBlocked: Boolean = true
        private set

    var message: String? = SetupVerdict.NoPerson.coachingMessage
        private set

    private var clearFrames = 0
    private var blockingFrames = 0
    private var candidate: String? = message
    private var candidateFrames = 0

    fun onFrame(landmarks: Landmarks) = step(SetupAssistant.evaluate(profile, landmarks, policy))

    fun onNothingSeen() = step(SetupVerdict.NoPerson)

    fun reset() {
        latestVerdict = SetupVerdict.NoPerson
        isScoringBlocked = true
        message = SetupVerdict.NoPerson.coachingMessage
        clearFrames = 0
        blockingFrames = 0
        candidate = message
        candidateFrames = 0
    }

    private fun step(verdict: SetupVerdict) {
        latestVerdict = verdict

        if (verdict.blocksScoring) {
            blockingFrames++
            clearFrames = 0
            if (blockingFrames >= CLOSE_FRAMES) isScoringBlocked = true
        } else {
            clearFrames++
            blockingFrames = 0
            if (clearFrames >= OPEN_FRAMES) isScoringBlocked = false
        }

        val said = if (verdict === SetupVerdict.Ready) null else verdict.coachingMessage
        if (said == candidate) {
            candidateFrames++
        } else {
            candidate = said
            candidateFrames = 1
        }
        if (candidateFrames >= MESSAGE_FRAMES) message = candidate
    }

    companion object {
        /** About a third of a second at 30 frames a second. P3-55's figure. */
        const val OPEN_FRAMES: Int = 10

        /** Half a second: a brief turn or a dropped detection does not stop a set. P3-55's figure. */
        const val CLOSE_FRAMES: Int = 15

        const val MESSAGE_FRAMES: Int = 10
    }
}
