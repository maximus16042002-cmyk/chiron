package com.kinesis.ui.live

import com.kinesis.core.analysis.fps.FrameRateHealth
import com.kinesis.core.analysis.verdict.RepVerdict
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
     * Whether the last attempt was the movement's fault - too fast, not a full repetition, a form
     * fault - in the last [LiveAnalysisBridge.VERDICT_MS]: the figure is red while it is.
     * DECISIONS.md P3-78, replacing P3-23's amber for any record that did not count.
     */
    val showingAttention: Boolean = false,
    /**
     * What was wrong with the last attempt, for [LiveAnalysisBridge.VERDICT_MS], or `null`: why it
     * did not count, or the form fault of one that did. The core's words (P3-78). Red when
     * [showingAttention], amber otherwise (tracking was lost).
     */
    val verdictMessage: String? = null,
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
        /** The last attempt's verdict, from the core (P3-78), or `null` before the first. */
        verdict: RepVerdict? = null,
        /** Verdicts the analysis has given so far: a change is a new attempt. */
        verdictsReported: Int = 0,
        /** This frame's time, which the verdict's message is measured against. */
        timestampMs: Double = 0.0,
        setupMessage: String? = null,
        frameRateHealth: FrameRateHealth = FrameRateHealth.HEALTHY,
        currentFps: Double = Double.NaN,
        fpsWarningMessage: String? = null,
    ) {
        frameCount++

        // A new verdict (P3-78): one with something to say shows it for VERDICT_MS - red if the
        // movement is to blame, a counted form fault included; a plainly counted one clears it at
        // once; a silent one changes nothing.
        if (verdictsReported != verdictsSeen) {
            verdictsSeen = verdictsReported
            when {
                verdict == null -> Unit
                verdict.message != null -> {
                    messageUntilMs = timestampMs + VERDICT_MS
                    message = verdict.message
                    fault = verdict.outcome.blamesMovement
                }
                verdict.outcome.counts -> {
                    messageUntilMs = Double.NaN
                    message = null
                }
            }
        }
        val showing = !messageUntilMs.isNaN() && timestampMs < messageUntilMs
        val attention = showing && fault
        val shownMessage = if (showing) message else null

        // Compared field by field against the live values rather than by building
        // a candidate and comparing that - constructing one per frame is exactly
        // the allocation this class exists to avoid.
        val unchanged = phase == current.phase &&
            repCount == current.repCount &&
            frameValidity == current.frameValidity &&
            lastRepValidity == current.lastRepValidity &&
            attention == current.showingAttention &&
            shownMessage == current.verdictMessage &&
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
                verdictMessage = shownMessage,
                setupMessage = setupMessage,
                frameRateHealth = frameRateHealth,
                currentFps = currentFps,
                fpsWarningMessage = fpsWarningMessage,
            ),
        )
    }

    private var verdictsSeen = 0
    private var messageUntilMs = Double.NaN
    private var message: String? = null
    private var fault = false

    private fun publish(state: LiveUiState) {
        current = state
        publishCount++
        onUiStateChanged(state)
    }

    val state: LiveUiState get() = current

    companion object {
        /** How long an attempt that did not count shows why. DECISIONS.md P3-78 (P3-23's was 1.5 s). */
        const val VERDICT_MS: Double = 2_000.0
    }
}
