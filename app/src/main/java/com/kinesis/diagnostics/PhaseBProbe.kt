package com.kinesis.diagnostics

import com.kinesis.core.domain.landmark.Landmarks

/**
 * A per-frame diagnostic hook for the Phase B device check - DECISIONS.md P3-17.
 *
 * Debug builds get a real one from `phaseBProbeOrNull` in `src/debug`; release
 * builds get `null` from the twin in `src/release`, so no probe code ships in a
 * release APK. Called from the live engines on MediaPipe's result thread, once
 * per frame, and must allocate nothing there. Nothing it computes is stored.
 */
interface PhaseBProbe {

    /** [leftAngle] and [rightAngle] are this frame's unfiltered joint angles. */
    fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double)

    fun onNoPose(timestampMs: Long)
}
