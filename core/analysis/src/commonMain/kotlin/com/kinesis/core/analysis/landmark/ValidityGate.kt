package com.kinesis.core.analysis.landmark

import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy

/**
 * Step two of the real-time path - CONTRACTS.md sections 7 and 8.
 *
 * Decides whether a frame is worth analysing, judged against what the profile
 * actually needs rather than against all 33 landmarks. CLAUDE.md section 10:
 * when required conditions are clearly invalid, scoring is blocked rather than
 * producing confident nonsense.
 *
 * O(1) per frame in the number of required landmarks, allocating nothing.
 */
object ValidityGate {

    /**
     * The verdict for one frame. The worst required landmark decides it, so a
     * single occluded knee is enough to degrade or invalidate a squat frame.
     *
     * Thresholds are inclusive: visibility exactly at [VisibilityPolicy.reliable]
     * counts as reliable, and exactly at [VisibilityPolicy.marginal] counts as
     * marginal. Pinned by test so a later change of comparison direction is
     * visible rather than silent.
     */
    fun evaluate(
        landmarks: Landmarks,
        required: RequiredLandmarks,
        policy: VisibilityPolicy,
    ): FrameValidity {
        var degraded = false
        for (index in 0 until required.size) {
            val visibility = landmarks.visibility(required[index])
            if (visibility < policy.marginal) return FrameValidity.INVALID
            if (visibility < policy.reliable) degraded = true
        }
        return if (degraded) FrameValidity.DEGRADED else FrameValidity.VALID
    }
}
