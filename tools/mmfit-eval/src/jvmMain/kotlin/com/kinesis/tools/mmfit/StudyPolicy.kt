package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.calibration.CalibrationPolicy

/**
 * The calibration policy every recorded study here ran under: 5 to 8 valid repetitions a side,
 * version `p6-1` - DECISIONS.md P3-22, used by P3-30 to P3-42.
 *
 * DECISIONS.md P3-83 moved the shipped policy to 10 to 15. The studies' figures were measured under
 * 5 to 8, so they pin it here rather than follow `CalibrationPolicy.PROVISIONAL`: a rerun then
 * reproduces what the report cites, and a number is never quietly re-measured under a different
 * rule. A study of the new policy is its own protocol.
 */
object StudyPolicy {
    val P3_22: CalibrationPolicy = CalibrationPolicy(
        minValidRepsPerSide = 5,
        maxValidRepsPerSide = 8,
        minHoldMs = 10_000.0,
        version = "p6-1",
    )
}
