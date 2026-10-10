package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.storage.SessionRecorder
import com.kinesis.ui.theme.OverlayState

/** Where the skeleton is drawn. [SkeletonOverlayView] in the app; a fake in tests. */
interface SkeletonSink {
    fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy)
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
     *   the camera never saw anyone, or nothing was recorded (no repetition record; a hold
     *   never measured, DECISIONS.md P3-70). A set with nothing in it is not a set.
     */
    fun closeSetInto(recorder: SessionRecorder, startedAtEpochMs: Double): Boolean

    /**
     * Calls [listener] with the exercise and every record so far, each time a repetition record
     * completes - DECISIONS.md P3-66, for the draft that lets a set survive the app being killed.
     * Called on the camera thread, so the listener must hand any real work elsewhere. A hold has
     * no record until it ends, so a hold engine never calls it.
     */
    fun observeRepetitions(listener: (exerciseId: String, records: List<RepRecord>) -> Unit) {}
}
