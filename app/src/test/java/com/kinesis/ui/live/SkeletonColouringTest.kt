package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-13.
 *
 * The overlay's colours have to agree with what the engine measured. P3-12 built
 * the palette and asserted it is legible; this asserts the right state is chosen
 * in the first place, which is the half a legible palette cannot save you from.
 *
 * The specific failure: a skeleton drawn entirely in the tracked colour while
 * half its joints are below the visibility floor. Nothing crashes, the figure
 * tracks the user, and the user is told their movement was measured when it was
 * not - the same shape of quiet wrongness the validity model exists to surface.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SkeletonColouringTest {

    private val policy = VisibilityPolicy.PROVISIONAL // reliable 0.70, marginal 0.40
    private val nominal = OverlayState.TRACKED

    // --- Per joint ----------------------------------------------------------------

    @Test
    fun a_confidently_seen_joint_is_tracked() {
        assertEquals(OverlayState.TRACKED, SkeletonColouring.jointState(0.95, policy, nominal))
        assertEquals(OverlayState.TRACKED, SkeletonColouring.jointState(0.70, policy, nominal))
    }

    @Test
    fun a_marginally_seen_joint_is_degraded_rather_than_tracked() {
        assertEquals(OverlayState.DEGRADED, SkeletonColouring.jointState(0.69, policy, nominal))
        assertEquals(OverlayState.DEGRADED, SkeletonColouring.jointState(0.40, policy, nominal))
    }

    @Test
    fun a_joint_below_the_floor_is_drawn_as_absent() {
        assertEquals(OverlayState.UNTRACKED, SkeletonColouring.jointState(0.39, policy, nominal))
        assertEquals(OverlayState.UNTRACKED, SkeletonColouring.jointState(0.0, policy, nominal))
    }

    /**
     * The thresholds are inclusive in the validity gate, and they are inclusive
     * here. If the overlay and `ValidityGate` disagreed at the boundary, a frame
     * the engine measured would be drawn as unmeasured, or worse the reverse.
     */
    @Test
    fun the_boundaries_match_the_validity_gate_exactly() {
        assertEquals(OverlayState.TRACKED, SkeletonColouring.jointState(policy.reliable, policy, nominal))
        assertEquals(OverlayState.DEGRADED, SkeletonColouring.jointState(policy.marginal, policy, nominal))
    }

    /**
     * `NaN` reaches here whenever a landmark had no measurement at all. It must
     * not fall through a numeric comparison into the tracked branch - every
     * comparison against `NaN` is false, so an implementation written with the
     * conditions in the other order would silently call it untracked by accident
     * rather than by intent, and one written as `!(visibility < reliable)` would
     * call it tracked.
     */
    @Test
    fun an_unmeasured_joint_is_untracked_and_never_tracked() {
        assertEquals(
            OverlayState.UNTRACKED,
            SkeletonColouring.jointState(Double.NaN, policy, nominal),
        )
    }

    /**
     * An abandoned repetition is about the movement, not one landmark. It must
     * not be possible to miss it because the joints happened to track well.
     */
    @Test
    fun attention_overrides_every_joint_however_well_it_is_tracked() {
        for (visibility in listOf(1.0, 0.7, 0.5, 0.1, Double.NaN)) {
            assertEquals(
                "visibility " + visibility + " escaped the attention state",
                OverlayState.ATTENTION,
                SkeletonColouring.jointState(visibility, policy, OverlayState.ATTENTION),
            )
        }
    }

    // --- Per segment ---------------------------------------------------------------

    /**
     * Drawing a line between a tracked shoulder and an invisible elbow in the
     * tracked colour would claim a measurement for a joint that was never seen.
     */
    @Test
    fun a_segment_takes_the_worse_of_its_two_ends() {
        assertEquals(
            OverlayState.UNTRACKED,
            SkeletonColouring.segmentState(OverlayState.TRACKED, OverlayState.UNTRACKED),
        )
        assertEquals(
            OverlayState.UNTRACKED,
            SkeletonColouring.segmentState(OverlayState.UNTRACKED, OverlayState.TRACKED),
        )
        assertEquals(
            OverlayState.DEGRADED,
            SkeletonColouring.segmentState(OverlayState.TRACKED, OverlayState.DEGRADED),
        )
        assertEquals(
            OverlayState.TRACKED,
            SkeletonColouring.segmentState(OverlayState.TRACKED, OverlayState.TRACKED),
        )
    }

    @Test
    fun attention_wins_over_a_worse_tracking_state_on_a_segment() {
        assertEquals(
            OverlayState.ATTENTION,
            SkeletonColouring.segmentState(OverlayState.ATTENTION, OverlayState.UNTRACKED),
        )
        assertEquals(
            OverlayState.ATTENTION,
            SkeletonColouring.segmentState(OverlayState.UNTRACKED, OverlayState.ATTENTION),
        )
    }

    /**
     * The "worse of the two" rule reads the enum's declaration order, so that
     * order is load-bearing rather than cosmetic. Reordering the states would
     * silently change which colour a mixed segment gets.
     */
    @Test
    fun the_severity_order_the_segment_rule_depends_on_is_the_declared_one() {
        assertEquals(
            listOf(
                OverlayState.TRACKED,
                OverlayState.DEGRADED,
                OverlayState.UNTRACKED,
                OverlayState.ATTENTION,
            ),
            OverlayState.entries.toList(),
        )
    }
}
