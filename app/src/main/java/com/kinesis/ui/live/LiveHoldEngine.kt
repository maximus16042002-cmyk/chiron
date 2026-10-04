package com.kinesis.ui.live

import com.kinesis.core.analysis.live.LiveHoldAnalysis
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.storage.SessionRecorder

/**
 * [LiveRepetitionEngine]'s counterpart for holds - DECISIONS.md P3-16.
 *
 * Takes only an [IsometricExercise], and hands frames only to
 * [LiveHoldAnalysis]: a hold never reaches the repetition tracker, and the type
 * system is what makes that so - CLAUDE.md section 11.
 */
class LiveHoldEngine(
    profile: IsometricExercise,
    private val bridge: LiveHoldBridge,
    private val skeleton: SkeletonSink,
    policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    /** Debug builds' Phase B readout; `null` in release. DECISIONS.md P3-17. */
    private val probe: PhaseBProbe? = null,
) : LiveEngine {

    private val analysis = LiveHoldAnalysis(profile, policy)

    private var sawPose = false
    private var ended = false

    @Synchronized
    override fun onLandmarks(landmarks: Landmarks, timestampMs: Long) {
        if (ended) return
        sawPose = true
        analysis.onFrame(timestampMs.toDouble(), landmarks)
        publish()
        skeleton.submit(landmarks, bridge.state.overlayState, analysis.policy, analysis.zoomTarget)
        probe?.onFrame(timestampMs, landmarks, analysis.rawAngleLeft, analysis.rawAngleRight)
    }

    @Synchronized
    override fun onNoPose(timestampMs: Long) {
        if (ended) return
        analysis.onNothingSeen(timestampMs.toDouble())
        publish()
        skeleton.clear()
        probe?.onNoPose(timestampMs)
    }

    @Synchronized
    override fun closeSetInto(recorder: SessionRecorder, startedAtEpochMs: Double): Boolean {
        if (ended) return false
        ended = true
        if (!sawPose) return false
        recorder.closeHoldSet(analysis.profile.id, startedAtEpochMs, listOf(analysis.finish()))
        return true
    }

    private fun publish() = bridge.onFrame(
        heldMs = analysis.heldMs,
        holdState = analysis.holdState,
        frameValidity = analysis.frameValidity,
        setupVerdict = analysis.setupVerdict,
        frameRateHealth = analysis.frameRateHealth,
        currentFps = analysis.currentFps,
        fpsWarningMessage = analysis.fpsWarningMessage,
        isZoomEngaged = analysis.isZoomEngaged,
    )
}
