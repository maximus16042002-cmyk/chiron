package com.kinesis.diagnostics

import com.kinesis.core.domain.landmark.Landmarks

/**
 * A per-frame diagnostic hook for the Phase B device check - DECISIONS.md P3-17.
 *
 * Debug builds get a real one from `phaseBProbeOrNull` in `src/debug`; release
 * builds get `null` from the twin in `src/release`, so no probe code ships in a
 * release APK. Called once per frame - from the live engines on MediaPipe's
 * result thread, and for the capture timings on CameraX's analysis thread - and
 * must allocate nothing on either. Nothing it computes is stored.
 */
interface PhaseBProbe {

    /** [leftAngle] and [rightAngle] are this frame's unfiltered joint angles. */
    fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double)

    fun onNoPose(timestampMs: Long)

    /**
     * A camera frame reached the analyser, before any is dropped. Analysis
     * thread. DECISIONS.md P3-19, as are the two below.
     */
    fun onCameraFrame(timestampMs: Long) = Unit

    /**
     * A frame was handed to MediaPipe: [convertNanos] turning it upright,
     * [handOverNanos] inside `detectAsync`. Analysis thread.
     */
    fun onSubmitted(timestampMs: Long, convertNanos: Long, handOverNanos: Long) = Unit

    /**
     * MediaPipe answered a frame [latencyNanos] after it was handed over.
     * MediaPipe's thread, just before that frame's [onFrame] or [onNoPose].
     */
    fun onAnswered(latencyNanos: Long) = Unit
}
