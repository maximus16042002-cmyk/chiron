package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState

/**
 * Which state each part of the skeleton is drawn in - DECISIONS.md P3-13.
 *
 * Pure functions, kept out of the `View` so they can be tested on the JVM. The
 * view draws; this decides. A bug here is invisible in review and shows up as a
 * skeleton that looks fine while reporting the wrong confidence, which is
 * exactly the class of fault the overlay exists to make visible.
 */
object SkeletonColouring {

    /**
     * One joint, from the same [VisibilityPolicy] the validity gate uses - so
     * what the user sees and what the engine measured cannot disagree.
     *
     * [overall] wins when it is [OverlayState.ATTENTION]: an abandoned
     * repetition is a fact about the movement rather than about one landmark,
     * and it must not be possible to miss it because the joints happened to
     * track well.
     */
    fun jointState(
        visibility: Double,
        policy: VisibilityPolicy,
        overall: OverlayState,
    ): OverlayState {
        if (overall == OverlayState.ATTENTION) return OverlayState.ATTENTION
        return when {
            visibility.isNaN() -> OverlayState.UNTRACKED
            visibility >= policy.reliable -> OverlayState.TRACKED
            visibility >= policy.marginal -> OverlayState.DEGRADED
            else -> OverlayState.UNTRACKED
        }
    }

    /**
     * A segment is only as good as its worse end.
     *
     * Drawing a line between a tracked shoulder and an invisible elbow in the
     * tracked colour would claim a measurement for a joint that was never seen.
     */
    fun segmentState(from: OverlayState, to: OverlayState): OverlayState = when {
        from == OverlayState.ATTENTION || to == OverlayState.ATTENTION -> OverlayState.ATTENTION
        from.ordinal > to.ordinal -> from
        else -> to
    }
}
