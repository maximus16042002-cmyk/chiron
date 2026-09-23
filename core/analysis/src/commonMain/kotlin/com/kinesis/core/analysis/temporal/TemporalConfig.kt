package com.kinesis.core.analysis.temporal

/**
 * Every tunable number in the temporal pipeline, in one immutable object.
 *
 * The pattern is carried over from the old prototype, which had it right:
 * consolidating the magic numbers makes them reviewable, makes an ablation study
 * possible, and stops thresholds being scattered through the code as literals.
 *
 * The values are not.
 */
data class TemporalConfig(

    /** How many recent samples the outlier gate judges against. Odd is preferable. */
    val outlierWindowSamples: Int,

    /**
     * How far a sample may sit from the local median before it is treated as a
     * spike, in the units of the signal being filtered - degrees, for a joint
     * angle.
     */
    val outlierThreshold: Double,

    /**
     * How many consecutive, mutually consistent rejected samples re-admit a
     * sustained change beyond [outlierThreshold]. At least 2, so a one-frame
     * spike can never re-admit itself. DECISIONS.md P3-17.
     */
    val outlierReadmitSamples: Int,

    /**
     * EMA smoothing factor, in (0, 1]. 1.0 is no smoothing; lower is smoother
     * and laggier. Lag matters: it delays the frame on which a repetition is
     * detected.
     */
    val smoothingAlpha: Double,

    /**
     * The longest run of missing data that may be bridged by interpolation, in
     * milliseconds. Longer gaps stay missing, because inventing a trajectory
     * across a second of occlusion is fabricating measurement.
     */
    val maxInterpolationGapMs: Double,

    /**
     * How many samples a completed repetition is resampled to.
     *
     * CLAUDE.md section 15 requires fixed-length resampled signals for completed
     * reps, and CONTRACTS.md section 7 names 64. A fixed length is what lets two
     * repetitions of different durations be compared at all.
     */
    val resampleLength: Int,
) {

    init {
        require(outlierWindowSamples >= 1) {
            "outlierWindowSamples must be at least 1, got " + outlierWindowSamples
        }
        require(outlierThreshold >= 0.0) {
            "outlierThreshold must be non-negative, got " + outlierThreshold
        }
        require(outlierReadmitSamples in 2..outlierWindowSamples) {
            "outlierReadmitSamples must be in 2.." + outlierWindowSamples + ", got " + outlierReadmitSamples
        }
        require(smoothingAlpha > 0.0 && smoothingAlpha <= 1.0) {
            "smoothingAlpha must be in (0, 1], got " + smoothingAlpha
        }
        require(maxInterpolationGapMs >= 0.0) {
            "maxInterpolationGapMs must be non-negative, got " + maxInterpolationGapMs
        }
        require(resampleLength >= 2) {
            "resampleLength must be at least 2, got " + resampleLength
        }
    }

    companion object {

        /**
         * **Provisional. Not measured on this system, and supporting no claim.**
         *
         * Starting values so the pipeline can be built, tested and ablated before
         * a device exists to calibrate it against - the same status as
         * `VisibilityPolicy.PROVISIONAL`, and for the same reason. CLAUDE.md
         * section 23 is measure, test, document, then claim, and none of that has
         * happened for these.
         *
         * Where each came from, so a reader knows how little weight they carry:
         *
         * - `outlierWindowSamples = 5` — about 165 ms at 30 fps, long enough to
         *   contain a single-frame spike and short enough not to smear a fast
         *   movement.
         * - `outlierThreshold = 20.0` — degrees, the prototype's figure, itself
         *   unvalidated.
         * - `outlierReadmitSamples = 3` — about 100 ms at 30 fps: long enough
         *   that a one- or two-frame estimator failure is still rejected whole,
         *   short enough that a real sudden change is seen within a tenth of a
         *   second. Chosen, not measured - DECISIONS.md P3-17.
         * - `smoothingAlpha = 0.3` — the prototype's figure. Its own filter
         *   comparison printed results for 0.2 to 0.5 without concluding.
         * - `maxInterpolationGapMs = 300.0` — the prototype's figure, roughly nine
         *   frames at 30 fps.
         * - `resampleLength = 64` — from CONTRACTS.md section 7, and the one value
         *   here that is a decision rather than a guess.
         *
         * P4 device testing and the ablation harness replace the first four. When
         * they do, the outcome is recorded in DECISIONS.md and this constant cites
         * it.
         */
        val PROVISIONAL: TemporalConfig = TemporalConfig(
            outlierWindowSamples = 5,
            outlierThreshold = 20.0,
            outlierReadmitSamples = 3,
            smoothingAlpha = 0.3,
            maxInterpolationGapMs = 300.0,
            resampleLength = 64,
        )
    }
}
