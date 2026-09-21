package com.kinesis.camera

import android.content.Context
import java.io.IOException

/**
 * Whether the pose model is actually on the device - DECISIONS.md P3-13.
 *
 * ### Why this is a type and not an assumption
 *
 * MediaPipe's Maven artifact **does not ship a model**. Inspecting
 * `tasks-vision`'s AAR finds no `.task` and no `.tflite`: the library is the
 * runtime, and the pose landmark model is a separate file that has to be
 * obtained and placed in `assets/` by hand. That is exactly why CLAUDE.md §17
 * asks for the model licence to be checked separately from the code licence -
 * they are genuinely two different artefacts with two different provenances.
 *
 * It also means the app can be built, installed and run by someone who does not
 * have the model, which makes its absence an ordinary runtime condition rather
 * than a packaging error. Treated the same way Health Connect's absence is
 * treated (P3-11): a named result, not an exception, so the screen can say
 * something true instead of crashing.
 */
sealed interface PoseModelState {

    /** The asset is present and can be loaded. */
    data class Present(val assetPath: String) : PoseModelState

    /**
     * No model in `assets/`. The app runs; the live screen reports that pose
     * estimation is unavailable and Kinesis's other screens are unaffected.
     */
    data object Absent : PoseModelState

    /** Present but unreadable - a truncated download, a corrupt file. */
    data class Unreadable(val reason: String) : PoseModelState
}

/**
 * Looks for the pose model without loading it.
 *
 * The three variants MediaPipe publishes differ in accuracy and cost; whichever
 * is present is used, preferring the lighter ones, because P4 has not yet
 * measured what this device can sustain. DECISIONS.md P3-13 records that the
 * choice between them is a P4 question and not a design-time one.
 */
object PoseModelAsset {

    /** Preferred first. Lite is the cheapest and the most likely to hold 30fps. */
    val CANDIDATES: List<String> = listOf(
        "pose_landmarker_lite.task",
        "pose_landmarker_full.task",
        "pose_landmarker_heavy.task",
    )

    fun locate(context: Context): PoseModelState {
        val assets = try {
            context.assets.list("")?.toSet().orEmpty()
        } catch (error: IOException) {
            return PoseModelState.Unreadable("could not list assets: " + error.message)
        }

        val found = CANDIDATES.firstOrNull { it in assets } ?: return PoseModelState.Absent

        return try {
            context.assets.open(found).use { stream ->
                // A truncated download is the realistic failure, and it presents
                // as a file that exists and cannot be parsed. Reading one byte
                // separates "missing" from "there but broken" without paying for
                // a full load.
                if (stream.read() == -1) {
                    PoseModelState.Unreadable(found + " is empty")
                } else {
                    PoseModelState.Present(found)
                }
            }
        } catch (error: IOException) {
            PoseModelState.Unreadable(found + ": " + error.message)
        }
    }
}
