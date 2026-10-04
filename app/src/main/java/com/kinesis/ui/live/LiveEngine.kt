package com.kinesis.ui.live

import com.kinesis.core.analysis.zoom.FocusZoomTarget
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.storage.SessionRecorder
import com.kinesis.ui.theme.OverlayState

/** Where the skeleton is drawn. [SkeletonOverlayView] in the app; a fake in tests. */
interface SkeletonSink {
    fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy)
    fun submit(
        landmarks: Landmarks,
        state: OverlayState,
        policy: VisibilityPolicy,
        zoomTarget: FocusZoomTarget,
    ) {
        submit(landmarks, state, policy)
    }
    fun clear()
}

/**
 * What the camera host drives, whichever analysis mode the exercise uses -
 * DECISIONS.md P3-16. [LiveRepetitionEngine] and [LiveHoldEngine] implement it;
 * neither decides anything, both hand frames to the shared core.
 *
 * Frames arrive on MediaPipe's result thread and the set is closed from the main
 * thread, so implementations synchronise every method.
 */
interface LiveEngine {

    fun onLandmarks(landmarks: Landmarks, timestampMs: Long)

    fun onNoPose(timestampMs: Long)

    /**
     * Ends the set: finishes the analyser, so work still under way is recorded
     * as unfinished rather than lost, and hands the set to [recorder]. Frames
     * that arrive afterwards are ignored.
     *
     * @return `false` if nothing was handed over - the set had already ended, or
     *   the camera never saw anyone. A set nobody was seen in is not a set.
     */
    fun closeSetInto(recorder: SessionRecorder, startedAtEpochMs: Double): Boolean
}
