package com.kinesis.ui.live

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState

/** Where the skeleton is drawn. [SkeletonOverlayView] in the app; a fake in tests. */
interface SkeletonSink {
    fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy)
    fun clear()
}

/**
 * Connects the pose callback to the two consumers it has - DECISIONS.md P3-15.
 *
 * Called on MediaPipe's result thread, once per analysed frame. It decides
 * nothing: the frame goes to [LiveRepetitionAnalysis] in the shared core, the
 * four values that come back go to [LiveAnalysisBridge], which decides whether
 * Compose hears about them, and the frame itself goes straight to the
 * [SkeletonSink] - CLAUDE.md section 6, Compose never sees a frame.
 *
 * The overlay's state is read back from the bridge rather than derived again
 * here, so the skeleton's colour and the readout come from the same value.
 */
class LiveSessionEngine(
    private val analysis: LiveRepetitionAnalysis,
    private val bridge: LiveAnalysisBridge,
    private val skeleton: SkeletonSink,
) {

    fun onLandmarks(landmarks: Landmarks, timestampMs: Long) {
        analysis.onFrame(timestampMs.toDouble(), landmarks)
        publish()
        skeleton.submit(landmarks, bridge.state.overlayState, analysis.policy)
    }

    fun onNoPose(timestampMs: Long) {
        analysis.onNothingSeen(timestampMs.toDouble())
        publish()
        skeleton.clear()
    }

    private fun publish() = bridge.onFrame(
        phase = analysis.displayPhase,
        repCount = analysis.repCount,
        frameValidity = analysis.frameValidity,
        lastRepValidity = analysis.lastRepValidity,
    )
}
