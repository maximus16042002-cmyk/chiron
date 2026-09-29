package com.kinesis.core.analysis.deviation

/**
 * Layer 1's settings - DECISIONS.md P3-30.
 *
 * **Provisional**, as P3-22's calibration counts are: starting values with a
 * stated reason, pinned by a test so a change is a visible change. The study in
 * P3-30 (S2, S3) measures what they do; nothing here is a measured optimum.
 *
 * The floors stop a tiny calibration spread from flagging everything: a
 * baseline of eight nearly identical repetitions has an IQR near zero, and
 * without a floor any ordinary repetition would read as many spreads away.
 */
class DeviationPolicy(

    /** Stamped on every Layer 1 finding as its `ruleVersion`. */
    val version: String,

    /** A personal deviation is reported beyond this many baseline-SD units. */
    val threshold: Double,

    /** The smallest spread a range of motion is scaled by, degrees. */
    val rangeOfMotionFloorDeg: Double,

    /**
     * The smallest spread a lifting or lowering time is scaled by, milliseconds:
     * one result interval at the phone's ~14 results a second (P3-24, P3-26).
     */
    val timeFloorMs: Double,

    /** The smallest spread a left-minus-right difference is scaled by, degrees. */
    val asymmetryFloorDeg: Double,

    /** The smallest spread a curve distance is scaled by, degrees. */
    val consistencyFloorDeg: Double,
) {
    companion object {
        val PROVISIONAL: DeviationPolicy = DeviationPolicy(
            version = "p8-l1-1",
            threshold = 2.0,
            rangeOfMotionFloorDeg = 3.0,
            timeFloorMs = 70.0,
            asymmetryFloorDeg = 3.0,
            consistencyFloorDeg = 2.0,
        )
    }
}
