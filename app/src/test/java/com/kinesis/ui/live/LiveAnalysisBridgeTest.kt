package com.kinesis.ui.live

import com.kinesis.core.analysis.fps.FrameRateHealth
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
     * Before the first frame nothing has been seen, and the readout must not say
     * otherwise. DECISIONS.md P3-15.
     */
    @Test
    fun before_any_frame_the_readout_does_not_claim_tracking() {
        val (_, seen) = bridge()
        assertEquals(1, seen.size)
        assertEquals(OverlayState.UNTRACKED, seen.single().overlayState)
    }

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

        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.VALID, recordsReported = 1, timestampMs = 0.0)
        assertEquals(OverlayState.TRACKED, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.ABORTED, recordsReported = 2, timestampMs = 100.0)
        assertEquals(
            "an abandoned repetition did not surface on the overlay",
            OverlayState.ATTENTION,
            seen.last().overlayState,
        )
    }

    /**
     * DECISIONS.md P3-23. The amber used to stay until a repetition counted,
     * which on the phone was almost never, so the figure stopped saying anything
     * about tracking. It now flashes for ATTENTION_MS and goes back.
     */
    @Test
    fun the_amber_lasts_a_moment_then_the_figure_shows_tracking_again() {
        val (bridge, seen) = bridge()
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.INSUFFICIENT_DATA, recordsReported = 1, timestampMs = 1_000.0)
        assertEquals(OverlayState.ATTENTION, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.INSUFFICIENT_DATA, recordsReported = 1, timestampMs = 2_400.0)
        assertEquals(OverlayState.ATTENTION, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.INSUFFICIENT_DATA, recordsReported = 1, timestampMs = 2_600.0)
        assertEquals("the amber stuck", OverlayState.TRACKED, seen.last().overlayState)
    }

    /** Two failures in a row have the same validity; the second must still flash. */
    @Test
    fun every_repetition_that_does_not_count_flashes_again() {
        val (bridge, seen) = bridge()
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.ABORTED, recordsReported = 1, timestampMs = 0.0)
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.ABORTED, recordsReported = 1, timestampMs = 2_000.0)
        assertEquals(OverlayState.TRACKED, seen.last().overlayState)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.ABORTED, recordsReported = 2, timestampMs = 3_000.0)
        assertEquals(OverlayState.ATTENTION, seen.last().overlayState)
    }

    @Test
    fun a_repetition_that_counts_clears_the_amber_at_once() {
        val (bridge, seen) = bridge()
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, RepValidity.ABORTED, recordsReported = 1, timestampMs = 0.0)
        bridge.onFrame(MovementPhase.TOP, 1, FrameValidity.VALID, RepValidity.VALID, recordsReported = 2, timestampMs = 300.0)
        assertEquals(OverlayState.TRACKED, seen.last().overlayState)
    }

    /**
     * DECISIONS.md P3-59: the screen hears the gate's message, a constant string, not P3-55's
     * verdict, whose per-frame confidence made every set-up frame a recomposition.
     */
    @Test
    fun a_change_in_the_setup_message_reaches_compose_once_and_a_repeated_one_never() {
        val (bridge, seen) = bridge()
        val turn = "Turn side-on to the camera — counting paused"
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null, setupMessage = turn)
        val afterFirst = bridge.publishCount
        assertEquals(turn, seen.last().setupMessage)

        repeat(30) { bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null, setupMessage = turn) }
        assertEquals(afterFirst, bridge.publishCount)

        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null, setupMessage = null)
        assertEquals(afterFirst + 1, bridge.publishCount)
        assertNull(seen.last().setupMessage)
        repeat(30) { bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null, setupMessage = null) }
        assertEquals(afterFirst + 1, bridge.publishCount)
    }

    @Test
    fun frame_rate_health_transition_recomposes_once_and_throttles() {
        val (bridge, seen) = bridge()
        bridge.onFrame(MovementPhase.TOP, 0, FrameValidity.VALID, null, frameRateHealth = FrameRateHealth.HEALTHY)
        val initialCount = bridge.publishCount

        // 30 frames with continuous FPS updates but same HEALTHY health level do not recompose
        repeat(30) { i ->
            bridge.onFrame(
                MovementPhase.TOP, 0, FrameValidity.VALID, null,
                frameRateHealth = FrameRateHealth.HEALTHY,
                currentFps = 28.0 + (i % 3),
            )
        }
        assertEquals(initialCount, bridge.publishCount)

        // Health degrades: Compose is notified exactly once
        bridge.onFrame(
            MovementPhase.TOP, 0, FrameValidity.VALID, null,
            frameRateHealth = FrameRateHealth.DEGRADED,
            currentFps = 9.5,
            fpsWarningMessage = "Camera frame rate low (10 FPS) — tracking accuracy reduced",
        )
        assertEquals(initialCount + 1, bridge.publishCount)
        assertEquals(FrameRateHealth.DEGRADED, seen.last().frameRateHealth)
        assertEquals("Camera frame rate low (10 FPS) — tracking accuracy reduced", seen.last().fpsWarningMessage)

        // 30 subsequent degraded frames with slight FPS fluctuations do not recompose
        repeat(30) { i ->
            bridge.onFrame(
                MovementPhase.TOP, 0, FrameValidity.VALID, null,
                frameRateHealth = FrameRateHealth.DEGRADED,
                currentFps = 9.0 + (i % 2),
                fpsWarningMessage = "Camera frame rate low (10 FPS) — tracking accuracy reduced",
            )
        }
        assertEquals(initialCount + 1, bridge.publishCount)
    }
}
