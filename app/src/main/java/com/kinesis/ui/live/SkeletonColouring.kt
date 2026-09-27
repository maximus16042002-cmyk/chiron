package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState
import kotlin.math.PI
import kotlin.math.cos

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
        // Amber covers every joint the camera saw, never one it did not: side-on,
        // MediaPipe guesses the hidden arm, and in amber the guess looked like a
        // real arm lifting. DECISIONS.md P3-23.
        if (overall == OverlayState.ATTENTION && policy.isMeasurable(visibility)) return OverlayState.ATTENTION
        // The policy's own predicates, which the validity gate and the
        // kinematics also use - one definition, NaN included (P3-16).
        return when {
            policy.isReliable(visibility) -> OverlayState.TRACKED
            policy.isMeasurable(visibility) -> OverlayState.DEGRADED
            else -> OverlayState.UNTRACKED
        }
    }

    /** One full pulse. 1 Hz: visible, and well under WCAG 2.3.1's three flashes a second. */
    const val PULSE_PERIOD_MS: Long = 1000L

    /** The widest a pulsing stroke gets, as a multiple of its palette width. */
    const val PULSE_PEAK_STROKE_SCALE: Float = 1.5f

    /**
     * How wide to draw [state]'s stroke at [elapsedMs], as a multiple of its
     * palette width - DECISIONS.md P3-12 and P3-16.
     *
     * Only a state whose palette entry says it pulses - today, `ATTENTION` - ever
     * returns anything but 1. The pulse **widens** the stroke, from its own width
     * to half again, rather than fading it: fading would drop the colour's
     * contrast below the floor `SkeletonPaletteLegibilityTest` guarantees for
     * part of every cycle, and the width channel P3-12 made load-bearing would be
     * the one giving way. Widening only ever adds.
     */
    fun strokeScale(state: OverlayState, elapsedMs: Long): Float {
        if (!state.pulses) return 1f
        val phase = elapsedMs.mod(PULSE_PERIOD_MS).toDouble() / PULSE_PERIOD_MS
        val wave = 0.5 - 0.5 * cos(2.0 * PI * phase)
        return (1.0 + (PULSE_PEAK_STROKE_SCALE - 1.0) * wave).toFloat()
    }

    /**
     * A segment is only as good as its worse end.
     *
     * Drawing a line between a tracked shoulder and an invisible elbow in the
     * tracked colour would claim a measurement for a joint that was never seen.
     */
    fun segmentState(from: OverlayState, to: OverlayState): OverlayState = when {
        // A line to a joint that was not seen is not a limb anyone measured, amber
        // or not - DECISIONS.md P3-23.
        from == OverlayState.UNTRACKED || to == OverlayState.UNTRACKED -> OverlayState.UNTRACKED
        from == OverlayState.ATTENTION || to == OverlayState.ATTENTION -> OverlayState.ATTENTION
        from.ordinal > to.ordinal -> from
        else -> to
    }
}
