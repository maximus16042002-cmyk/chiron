package com.kinesis.ui.live

import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.analysis.setup.SetupVerdict
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.ui.theme.OverlayState

/**
 * The coarse state a Compose screen may observe for a hold - DECISIONS.md P3-16.
 *
 * Held time is kept to whole seconds, which is what the readout shows and what
 * keeps recomposition to about once a second through a steady hold.
 */
data class LiveHoldUiState(
    val exerciseId: String,
    val heldSeconds: Int,
    val holdState: HoldState,
    val frameValidity: FrameValidity,
    val setupVerdict: SetupVerdict = SetupVerdict.NoPerson,
) {
    /**
     * Derived, as in [LiveUiState], so the overlay and the readout cannot
     * disagree. A hold that has been reached and lost is the hold's counterpart
     * of an abandoned repetition - a measured event, the body line out of its
     * band - so it takes the attention state while it lasts.
     */
    val overlayState: OverlayState
        get() = when {
            holdState == HoldState.DEVIATING -> OverlayState.ATTENTION
            frameValidity == FrameValidity.INVALID -> OverlayState.UNTRACKED
            frameValidity == FrameValidity.DEGRADED -> OverlayState.DEGRADED
            else -> OverlayState.TRACKED
        }
}

/**
 * [LiveAnalysisBridge]'s counterpart for holds: every frame in, a published
 * state only when something a person could perceive has changed - CLAUDE.md
 * section 6. Holds no analysis logic.
 */
class LiveHoldBridge(
    private val exerciseId: String,
    private val onUiStateChanged: (LiveHoldUiState) -> Unit,
) {

    // INVALID until a frame says otherwise, as in LiveAnalysisBridge.
    private var current = LiveHoldUiState(exerciseId, 0, HoldState.NOT_STARTED, FrameValidity.INVALID, SetupVerdict.NoPerson)

    /** How many times Compose has been asked to recompose. Instrumentation. */
    var publishCount: Int = 0
        private set

    /** How many frames have been offered. Instrumentation. */
    var frameCount: Int = 0
        private set

    init {
        publish(current)
    }

    fun onFrame(
        heldMs: Double,
        holdState: HoldState,
        frameValidity: FrameValidity,
        setupVerdict: SetupVerdict = SetupVerdict.NoPerson,
    ) {
        frameCount++
        val heldSeconds = (heldMs / 1000.0).toInt()
        val unchanged = heldSeconds == current.heldSeconds &&
            holdState == current.holdState &&
            frameValidity == current.frameValidity &&
            setupVerdict == current.setupVerdict
        if (unchanged) return
        publish(LiveHoldUiState(exerciseId, heldSeconds, holdState, frameValidity, setupVerdict))
    }

    private fun publish(state: LiveHoldUiState) {
        current = state
        publishCount++
        onUiStateChanged(state)
    }

    val state: LiveHoldUiState get() = current
}
