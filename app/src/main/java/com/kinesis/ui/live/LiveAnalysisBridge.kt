package com.kinesis.ui.live

import com.kinesis.core.analysis.fps.FrameRateHealth
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.ui.theme.OverlayState

/**
 * The coarse state a Compose screen is allowed to observe.
 *
 * Everything here changes a few times a second at most - a repetition
 * completing, a phase transition, tracking degrading. Nothing here changes per
 * frame.
 */
data class LiveUiState(
    val exerciseId: String,
    val repCount: Int,
    val phase: MovementPhase,
    val frameValidity: FrameValidity,
    val lastRepValidity: RepValidity?,
    /**
     * Whether a repetition that did not count ended in the last
     * [LiveAnalysisBridge.ATTENTION_MS]. DECISIONS.md P3-23: the amber used to
     * stay until a repetition did count, which on the phone was almost always,
     * and while amber the figure said nothing about tracking.
     */
    val showingAttention: Boolean = false,
    /** The setup gate's line, or `null`. A constant string, debounced in the core (P3-59). */
    val setupMessage: String? = null,
    val frameRateHealth: FrameRateHealth = FrameRateHealth.HEALTHY,
    val currentFps: Double = Double.NaN,
    val fpsWarningMessage: String? = null,
) {
    /**
     * How the skeleton should be drawn, derived rather than stored so the
     * overlay and the readout cannot disagree.
     */
    val overlayState: OverlayState
        get() = when {
            showingAttention -> OverlayState.ATTENTION
            frameValidity == FrameValidity.INVALID -> OverlayState.UNTRACKED
            frameValidity == FrameValidity.DEGRADED -> OverlayState.DEGRADED
            else -> OverlayState.TRACKED
        }
}

/**
 * The boundary between the per-frame analysis path and Compose -
 * DECISIONS.md P3-12.
 *
 * ### The rule this exists to keep
 *
 * CLAUDE.md section 6: *"MUST NOT: drive Compose recomposition from pose frames;
 * skeleton rendering goes to a dedicated Canvas/SurfaceView, separated from
 * low-frequency UI state."* FINAL_ARCHITECTURE section 8 says the same thing in
 * budget terms: *"Skeleton and live envelope draw on a dedicated
 * Canvas/SurfaceView fed from the pose callback. Compose state updates only on
 * rep/phase change - a few times per second."*
 *
 * Both paths run through [onFrame], and they are deliberately different shapes:
 *
 * - **the draw path** is called on every frame, synchronously, and is handed the
 *   landmarks as they arrived. It allocates nothing here;
 * - **the Compose path** is called only when the coarse state actually changes.
 *   A thirty-second set at thirty frames a second is nine hundred frames and
 *   perhaps forty state changes.
 *
 * The saving is not the lambda call - it is that a `LiveUiState` is only
 * constructed when something a human could perceive has changed. Publishing per
 * frame would allocate nine hundred objects and ask Compose to diff a tree
 * thirty times a second, which is the failure mode the rule names.
 *
 * ### What this is not
 *
 * It holds no analysis logic. The phase, the count and the validity are all
 * computed in the shared core and handed here; this only decides *when Compose
 * is allowed to hear about them* - CLAUDE.md section 2.
 */
class LiveAnalysisBridge(
    private val exerciseId: String,
    private val onUiStateChanged: (LiveUiState) -> Unit,
) {

    // INVALID until a frame says otherwise: before the camera has delivered
    // anything, "tracked reliably" would be a claim with nothing behind it.
    // DECISIONS.md P3-15.
    private var current = LiveUiState(
        exerciseId = exerciseId,
        repCount = 0,
        phase = MovementPhase.IDLE,
        frameValidity = FrameValidity.INVALID,
        lastRepValidity = null,
    )

    /** How many times Compose has been asked to recompose. Instrumentation. */
    var publishCount: Int = 0
        private set

    /** How many frames have been offered. Instrumentation. */
    var frameCount: Int = 0
        private set

    init {
        publish(current)
    }

    /**
     * One analysed frame.
     *
     * Called at camera rate. The draw path is the caller's business - it feeds a
     * `SurfaceView` directly and never comes through Compose - and this decides
     * only whether the coarse state moved enough to be worth a recomposition.
     */
    fun onFrame(
        phase: MovementPhase,
        repCount: Int,
        frameValidity: FrameValidity,
        lastRepValidity: RepValidity?,
        /** Records the analysis has reported so far, valid or not. */
        recordsReported: Int = 0,
        /** This frame's time, which the attention flash is measured against. */
        timestampMs: Double = 0.0,
        setupMessage: String? = null,
        frameRateHealth: FrameRateHealth = FrameRateHealth.HEALTHY,
        currentFps: Double = Double.NaN,
        fpsWarningMessage: String? = null,
    ) {
        frameCount++

        // A new record: one that did not count flashes amber for ATTENTION_MS;
        // one that did clears it at once.
        if (recordsReported != recordsSeen) {
            recordsSeen = recordsReported
            attentionUntilMs = if (lastRepValidity != null && lastRepValidity != RepValidity.VALID) {
                timestampMs + ATTENTION_MS
            } else {
                Double.NaN
            }
        }
        val attention = !attentionUntilMs.isNaN() && timestampMs < attentionUntilMs

        // Compared field by field against the live values rather than by building
        // a candidate and comparing that - constructing one per frame is exactly
        // the allocation this class exists to avoid.
        val unchanged = phase == current.phase &&
            repCount == current.repCount &&
            frameValidity == current.frameValidity &&
            lastRepValidity == current.lastRepValidity &&
            attention == current.showingAttention &&
            setupMessage == current.setupMessage &&
            frameRateHealth == current.frameRateHealth
        if (unchanged) return

        publish(
            LiveUiState(
                exerciseId = exerciseId,
                repCount = repCount,
                phase = phase,
                frameValidity = frameValidity,
                lastRepValidity = lastRepValidity,
                showingAttention = attention,
                setupMessage = setupMessage,
                frameRateHealth = frameRateHealth,
                currentFps = currentFps,
                fpsWarningMessage = fpsWarningMessage,
            ),
        )
    }

    private var recordsSeen = 0
    private var attentionUntilMs = Double.NaN

    private fun publish(state: LiveUiState) {
        current = state
        publishCount++
        onUiStateChanged(state)
    }

    val state: LiveUiState get() = current

    companion object {
        /** How long a repetition that did not count shows amber. DECISIONS.md P3-23. */
        const val ATTENTION_MS: Double = 1_500.0
    }
}
