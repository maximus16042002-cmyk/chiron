package com.kinesis.ui.live

import com.kinesis.core.analysis.live.LiveHoldAnalysis
import com.kinesis.core.analysis.live.SetupEnforcement
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.HoldRecord
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

    // A wrong camera view is reported, not blocked - DECISIONS.md P3-68, replacing P3-59.
    private val analysis = LiveHoldAnalysis(profile, policy, setupEnforcement = SetupEnforcement.REPORT_ONLY)

    init {
        skeleton.measuring(profile.holdSignal.joint)
    }

    private var sawPose = false
    private var ended = false

    @Volatile
    private var holdListener: ((String, HoldRecord) -> Unit)? = null
    private var lastSnapshotMs = Double.NEGATIVE_INFINITY

    override fun observeHolds(listener: (exerciseId: String, hold: HoldRecord) -> Unit) {
        holdListener = listener
    }

    /** At most once a second of frame time, a snapshot for the draft (P3-72). Never per frame. */
    private fun maybeSnapshot(timestampMs: Double) {
        val listener = holdListener ?: return
        if (timestampMs - lastSnapshotMs < SNAPSHOT_INTERVAL_MS) return
        lastSnapshotMs = timestampMs
        val hold = analysis.snapshot()
        // Nothing measured yet: not a set (P3-70), so nothing to draft.
        if (hold.coverage > 0.0) listener(analysis.profile.id, hold)
    }

    @Synchronized
    override fun onLandmarks(landmarks: Landmarks, timestampMs: Long) {
        if (ended) return
        sawPose = true
        analysis.onFrame(timestampMs.toDouble(), landmarks)
        maybeSnapshot(timestampMs.toDouble())
        publish()
        skeleton.submit(landmarks, bridge.state.overlayState, analysis.policy, analysis.angleLeft, analysis.angleRight)
        probe?.onFrame(timestampMs, landmarks, analysis.rawAngleLeft, analysis.rawAngleRight)
    }

    @Synchronized
    override fun onNoPose(timestampMs: Long) {
        if (ended) return
        analysis.onNothingSeen(timestampMs.toDouble())
        maybeSnapshot(timestampMs.toDouble())
        publish()
        skeleton.clear()
        probe?.onNoPose(timestampMs)
    }

    @Synchronized
    override fun closeSetInto(recorder: SessionRecorder, startedAtEpochMs: Double): Boolean {
        if (ended) return false
        ended = true
        if (!sawPose) return false
        val hold = analysis.finish()
        // Seen, but never measured: no measurement, so nothing to save - DECISIONS.md P3-70.
        if (hold.coverage <= 0.0) return false
        recorder.closeHoldSet(analysis.profile.id, startedAtEpochMs, listOf(hold))
        return true
    }

    private fun publish() = bridge.onFrame(
        heldMs = analysis.heldMs,
        holdState = analysis.holdState,
        frameValidity = analysis.frameValidity,
        setupMessage = analysis.setupMessage,
        frameRateHealth = analysis.frameRateHealth,
        currentFps = analysis.currentFps,
        fpsWarningMessage = analysis.fpsWarningMessage,
    )

    private companion object {
        /** How often a hold's draft is refreshed: a kill loses at most this much (P3-72). */
        const val SNAPSHOT_INTERVAL_MS = 1_000.0
    }
}
