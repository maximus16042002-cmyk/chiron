@file:OptIn(ExperimentalJsExport::class)

package com.kinesis.core.analysis

/**
 * The JavaScript facade - CONTRACTS.md section 5, DECISIONS.md P0-2.
 *
 * This is the only `@JsExport` surface in the project, and this file is the only
 * file in `jsMain`. If a second one appears here, something has gone wrong.
 *
 * **The boundary rule.** Everything crossing it is a primitive, a `DoubleArray`,
 * or a JSON string. No Kotlin collections, no sealed classes, no data classes,
 * no nullable rich types. Everything rich stays inside Kotlin, which keeps the
 * generated `.d.ts` small enough to read by hand and stops the JS target from
 * dictating the shape of the core's internal API.
 *
 * It is designed here in P1 rather than retrofitted later, because a facade
 * added after the core's public surface exists has to be reverse-engineered from
 * it. The JS target carries the cross-platform parity study, and this shape is
 * what makes that study cheap: identical JSON in, identical JSON out, compared
 * on both targets.
 *
 * **Not implemented in P1.** The signatures are the deliverable; the bodies
 * arrive with the engine they wrap, in P2 and P3. They throw rather than
 * returning plausible empty values, so a caller wiring this up early finds out
 * immediately instead of collecting silent zeroes.
 */
@JsExport
class KinesisEngine {

    /**
     * Selects the exercise profile to analyse against, as JSON.
     *
     * Call before [pushFrame]. Switching profiles mid-set discards any
     * repetition in progress.
     */
    fun configure(profileJson: String) {
        throw NotImplementedError(notYet("configure", profileJson.length))
    }

    /**
     * Feeds one frame of pose into the real-time path.
     *
     * @param timestampMs frame time in milliseconds. `Double`, not `Long`:
     *   CLAUDE.md section 5 keeps `Long` out of analysis paths where JS
     *   compatibility matters.
     * @param landmarks 99 doubles, `x, y, z` per landmark in MediaPipe order.
     *   **Anatomical already** - the caller's pose adapter has converted camera
     *   coordinates to anatomical left and right before this point, and the core
     *   neither knows nor asks which camera produced the frame.
     * @param visibility 33 doubles, one per landmark, in 0..1.
     *
     * @return `null` on the overwhelming majority of calls, and a JSON
     *   `RepRecord` on the frame that completes a repetition. Heavy analysis
     *   happens on that record, never per frame.
     */
    fun pushFrame(timestampMs: Double, landmarks: DoubleArray, visibility: DoubleArray): String? {
        throw NotImplementedError(notYet("pushFrame", landmarks.size + visibility.size))
    }

    /**
     * Runs the post-rep path over one completed repetition: features, deviation
     * rules, technique rules, reference comparison, explanation.
     *
     * Takes a `RepRecord` because a `RepRecord` only exists once a repetition has
     * completed. That is what makes per-frame ML impossible by construction
     * rather than by convention - CONTRACTS.md section 7.
     *
     * @return a JSON `AnalysisResult`.
     */
    fun analyzeRep(repJson: String, baselineJson: String): String {
        throw NotImplementedError(notYet("analyzeRep", repJson.length + baselineJson.length))
    }

    /**
     * Fits a personal baseline from a set of calibration repetitions.
     *
     * @return a JSON `CalibrationBaseline`.
     */
    fun computeBaseline(repsJson: String): String {
        throw NotImplementedError(notYet("computeBaseline", repsJson.length))
    }

    private fun notYet(method: String, inputSize: Int): String =
        "KinesisEngine." + method + " is a P1 signature stub; the engine lands in P2. " +
            "Received input of size " + inputSize + "."
}
