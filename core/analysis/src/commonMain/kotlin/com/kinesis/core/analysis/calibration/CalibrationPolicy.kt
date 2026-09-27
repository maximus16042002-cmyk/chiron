package com.kinesis.core.analysis.calibration

/**
 * How much calibration a baseline needs - DECISIONS.md P3-22.
 *
 * **Every number here is provisional.** None is measured. The repetition counts
 * wait for the MM-Fit convergence study (ZERO_DATASET_BUILD_PLAN.md, Study 3:
 * "compute their personal baseline from the first N for N from 3 to 20, and
 * measure how the baseline estimate converges"), and the hold duration for the
 * camera session, since MM-Fit has no holds. DECISIONS.md P3-21: anything tuned
 * to real movement stays provisional until then.
 */
class CalibrationPolicy(

    /**
     * Fewest valid repetitions on a side for that side to have a baseline.
     *
     * Provisional **5**, the smallest sample in which the type-7 quartiles land
     * exactly on the second-smallest and second-largest values, so a single
     * extreme repetition at either end moves neither the median nor the
     * interquartile range (`RobustStatisticsTest` shows both, and shows 4
     * failing it). It is also the low end of ZERO_DATASET_BUILD_PLAN.md's "5-10
     * reps".
     */
    val minValidRepsPerSide: Int,

    /**
     * Most valid repetitions per side the baseline uses; later ones are ignored.
     *
     * Provisional **8**, the number ZERO_DATASET_BUILD_PLAN.md's Study 2 holds
     * out per subject to build a personal baseline ("hold out their first 8
     * repetitions"), so the app and the study compute the same estimator. Using
     * the first eight also keeps the end of a long set, where fatigue sets in,
     * out of the baseline.
     */
    val maxValidRepsPerSide: Int,

    /**
     * Shortest calibration hold, milliseconds.
     *
     * Provisional **10 s**. At the 12-16 results a second measured on the
     * owner's phone (DECISIONS.md P3-18, P3-19) that is some 120-160 held frames
     * behind the sway, and it is short enough to ask of someone new to a plank,
     * wall sit or side plank. No dataset can set it: MM-Fit has no holds.
     */
    val minHoldMs: Double,

    /** Stored on every baseline this policy produces, as its `estimatorVersion`. */
    val version: String,
) {
    init {
        require(minValidRepsPerSide >= 1) { "a baseline needs at least one repetition" }
        require(maxValidRepsPerSide >= minValidRepsPerSide) { "maxValidRepsPerSide below minValidRepsPerSide" }
        require(minHoldMs > 0.0) { "minHoldMs must be positive" }
        require(version.isNotBlank()) { "a policy needs a version" }
    }

    companion object {
        /** The only policy in use. Provisional, as above. */
        val PROVISIONAL: CalibrationPolicy = CalibrationPolicy(
            minValidRepsPerSide = 5,
            maxValidRepsPerSide = 8,
            minHoldMs = 10_000.0,
            version = "p6-1",
        )
    }
}
