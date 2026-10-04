package com.kinesis.ui.live

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.storage.SessionRecorder

/**
 * Connects the pose callback to a repetition exercise's two consumers -
 * DECISIONS.md P3-15 and P3-16.
 *
 * The frame goes to [LiveRepetitionAnalysis] in the shared core, the four values
 * that come back go to [LiveAnalysisBridge], which decides whether Compose hears
 * about them, and the frame itself goes straight to the [SkeletonSink] -
 * CLAUDE.md section 6, Compose never sees a frame. It decides nothing.
 *
 * The overlay's state is read back from the bridge rather than derived again
 * here, so the skeleton's colour and the readout come from the same value.
 *
 * Completed repetitions are collected here, off the per-frame path, for the
 * session recorder: collecting is storage, not analysis.
 */
class LiveRepetitionEngine(
    profile: RepetitionExercise,
    private val bridge: LiveAnalysisBridge,
    private val skeleton: SkeletonSink,
    policy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
    /** Debug builds' Phase B readout; `null` in release. DECISIONS.md P3-17. */
    private val probe: PhaseBProbe? = null,
) : LiveEngine {

    private val reps = ArrayList<RepRecord>()
    private val analysis = LiveRepetitionAnalysis(profile, policy, onRepetition = { reps += it })

    private var lastTimestampMs = Double.NaN
    private var sawPose = false
    private var ended = false

    @Synchronized
    override fun onLandmarks(landmarks: Landmarks, timestampMs: Long) {
        if (ended) return
        sawPose = true
        lastTimestampMs = timestampMs.toDouble()
        analysis.onFrame(lastTimestampMs, landmarks)
        publish()
        skeleton.submit(landmarks, bridge.state.overlayState, analysis.policy)
        probe?.onFrame(timestampMs, landmarks, analysis.rawAngleLeft, analysis.rawAngleRight)
    }

    @Synchronized
    override fun onNoPose(timestampMs: Long) {
        if (ended) return
        lastTimestampMs = timestampMs.toDouble()
        analysis.onNothingSeen(lastTimestampMs)
        publish()
        skeleton.clear()
        probe?.onNoPose(timestampMs)
    }

    @Synchronized
    override fun closeSetInto(recorder: SessionRecorder, startedAtEpochMs: Double): Boolean {
        if (ended) return false
        ended = true
        if (!sawPose) return false
        // A repetition still under way becomes an INCOMPLETE record here.
        analysis.finish(lastTimestampMs)
        recorder.closeRepetitionSet(analysis.profile.id, startedAtEpochMs, reps.toList())
        return true
    }

    private fun publish() = bridge.onFrame(
        phase = analysis.displayPhase,
        repCount = analysis.repCount,
        frameValidity = analysis.frameValidity,
        lastRepValidity = analysis.lastRepValidity,
        recordsReported = analysis.recordsReported,
        timestampMs = lastTimestampMs,
        setupVerdict = analysis.setupVerdict,
        frameRateHealth = analysis.frameRateHealth,
        currentFps = analysis.currentFps,
        fpsWarningMessage = analysis.fpsWarningMessage,
    )
}
