package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - CLAUDE.md section 6, FINAL_ARCHITECTURE section 8,
 * DECISIONS.md P3-12.
 *
 * *"MUST NOT: drive Compose recomposition from pose frames."*
 *
 * This is the one non-negotiable the UI layer can break, and it breaks quietly:
 * publishing state per frame does not crash, it just asks Compose to diff a tree
 * thirty times a second while the analysis path is trying to keep up with the
 * camera. The symptom is a dropped frame rate during a set, which looks like a
 * pose-estimation problem and is not one.
 *
 * So the ratio is asserted directly: hundreds of frames, a handful of
 * recompositions. A mutation that publishes unconditionally fails every test
 * here.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class LiveAnalysisBridgeTest {

    private fun bridge(): Pair<LiveAnalysisBridge, MutableList<LiveUiState>> {
        val seen = mutableListOf<LiveUiState>()
        return LiveAnalysisBridge("squat") { seen += it } to seen
    }

    // --- The budget ---------------------------------------------------------------------

    /**
     * Thirty seconds of a steady hold at the top of a movement - nine hundred
     * frames in which nothing a human could perceive changes. Compose should
     * hear about it once, when the screen opened.
     */
    @Test
    fun nine_hundred_unchanged_frames_do_not_recompose_anything() {
        val (bridge, seen) = bridge()

        // The first frame genuinely changes something - the bridge starts IDLE
        // and the subject is at the top - so it is allowed exactly one
        // recomposition. The 900 after it change nothing.
        bridge.onFrame(MovementPhase.TOP, repCount = 0, FrameValidity.VALID, null)
        val afterFirstFrame = bridge.publishCount

        repeat(900) {
            bridge.onFrame(MovementPhase.TOP, repCount = 0, FrameValidity.VALID, null)
        }

        assertEquals(901, bridge.frameCount)
        assertEquals(
            "a frame that changed nothing still asked Compose to recompose",
            afterFirstFrame,
            bridge.publishCount,
        )
        assertEquals(2, seen.size)
    }

    /**
     * The real shape of a set: ten repetitions over about twenty seconds. Six
     * hundred frames, and the only things worth telling Compose are the phase
     * transitions and the count.
     *
     * The bound is deliberately generous - the point is the order of magnitude,
     * not a precise number. Publishing per frame gives 600; this gives tens.
     */
    @Test
    fun a_ten_repetition_set_recomposes_tens_of_times_and_not_hundreds() {
        val (bridge, _) = bridge()

        var reps = 0
        repeat(10) {
            for (phase in listOf(
                MovementPhase.TOP,
                MovementPhase.DESCENDING,
                MovementPhase.BOTTOM,
                MovementPhase.ASCENDING,
            )) {
                // Fifteen frames in each phase - half a second at thirty frames
                // a second, which is a brisk repetition.
                repeat(15) { bridge.onFrame(phase, reps, FrameValidity.VALID, null) }
            }
            reps++
            bridge.onFrame(MovementPhase.TOP, reps, FrameValidity.VALID, RepValidity.VALID)
        }

        assertEquals(610, bridge.frameCount)
        assertTrue(
            "recomposed " + bridge.publishCount + " times for " + bridge.frameCount +
                " frames, which is per-frame recomposition in all but name",
            bridge.publishCount < 60,
        )
        assertTrue(bridge.publishCount > 1)
    }

    @Test
    fun the_frame_to_recomposition_ratio_stays_far_below_one_to_one() {
        val (bridge, _) = bridge()
        repeat(300) { index ->
            // Tracking flickers between valid and degraded every fifty frames -
            // roughly the worst realistic case short of genuine noise.
            val validity = if ((index / 50) % 2 == 0) FrameValidity.VALID else FrameValidity.DEGRADED
            bridge.onFrame(MovementPhase.DESCENDING, 3, validity, null)
        }

        assertTrue(
            "published " + bridge.publishCount + " times in 300 frames",
            bridge.publishCount <= 10,
        )
    }

    // --- It still reports what changed ------------------------------------------------------

    /**
     * The saving must not come from dropping information. Every distinct state
     * the set passed through has to reach Compose, just once each rather than
     * once per frame.
     */
    @Test
    fun every_phase_transition_reaches_compose_exactly_once() {
        val (bridge, seen) = bridge()

        val phases = listOf(
            MovementPhase.TOP,
            MovementPhase.DESCENDING,
            MovementPhase.BOTTOM,
            MovementPhase.ASCENDING,
            MovementPhase.TOP,
        )
        for (phase in phases) {
            repeat(20) { bridge.onFrame(phase, 0, FrameValidity.VALID, null) }
        }

        // The initial IDLE, then one per transition.
        assertEquals(
            listOf(
                MovementPhase.IDLE,
                MovementPhase.TOP,
                MovementPhase.DESCENDING,
                MovementPhase.BOTTOM,
                MovementPhase.ASCENDING,
                MovementPhase.TOP,
            ),
            seen.map { it.phase },
        )
    }

    @Test
    fun a_completed_repetition_reaches_compose_even_mid_phase() {
        val (bridge, seen) = bridge()
        repeat(30) { bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null) }
        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.VALID)
        repeat(30) { bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.VALID) }

        assertEquals(1, seen.last().repCount)
        assertTrue("the completed repetition never reached the UI", seen.any { it.repCount == 1 })
    }

    // --- The overlay state it derives ----------------------------------------------------------

    /**
     * The readout and the skeleton must agree, so the overlay state is derived
     * from the same value the count comes from rather than tracked separately.
     */
    @Test
    fun the_overlay_state_follows_the_measured_condition() {
        val (bridge, seen) = bridge()

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null)
        assertEquals(OverlayState.TRACKED, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.DEGRADED, null)
        assertEquals(OverlayState.DEGRADED, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.INVALID, null)
        assertEquals(OverlayState.UNTRACKED, seen.last().overlayState)
    }

    /**
     * The only route to the attention state today, and it is a real measurement -
     * the FSM abandoned a repetition. P8's technique findings join it here; until
     * they exist, nothing else turns a joint amber.
     */
    @Test
    fun an_abandoned_repetition_is_what_turns_the_overlay_amber() {
        val (bridge, seen) = bridge()

        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.VALID)
        assertEquals(OverlayState.TRACKED, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.ABORTED)
        assertEquals(
            "an abandoned repetition did not surface on the overlay",
            OverlayState.ATTENTION,
            seen.last().overlayState,
        )
    }
}
