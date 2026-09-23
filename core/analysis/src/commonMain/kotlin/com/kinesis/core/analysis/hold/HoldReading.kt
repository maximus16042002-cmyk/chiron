package com.kinesis.core.analysis.hold

import com.kinesis.core.domain.bilateral.BilateralSignal
import kotlin.math.abs

/**
 * The one sanctioned way to turn a bilateral hold angle into the scalar
 * [HoldAnalyser] takes - CLAUDE.md section 3, DECISIONS.md P3-9 and P3-16.
 *
 * P3-9 left this open deliberately: "When a landmark-driven hold tracker is
 * eventually built, it must reduce the `BilateralSignal` ... through an explicit
 * named reducer - never an average, and never a silent fallback." This is that
 * reducer.
 *
 * ### The rule: the measured side furthest from the target
 *
 * - **Both sides measured:** the one further from the target is read. The hold is
 *   then credited only while every side that was seen agrees the body is within
 *   tolerance; one side sagging out of it is a break, not something the other
 *   side can outvote.
 * - **One side measured:** that side is read. P3-9 established that a hold's
 *   signal is a property of the body **line**, which both sides' triples
 *   describe, and measured them agreeing within a degree. So a single measured
 *   side is a measurement of the line, not a substitute for a different limb -
 *   and it is the normal case, since a side-on plank sees its near side only.
 * - **Neither measured:** `NaN`, which `HoldAnalyser` treats as unseen.
 *
 * The result is always exactly one side's value, never a blend of the two, and
 * swapping the sides never changes it.
 */
object HoldReading {

    fun furthestFromTarget(signal: BilateralSignal, target: Double): Double {
        val left = signal.left
        val right = signal.right
        if (left.isNaN()) return right
        if (right.isNaN()) return left
        val leftDistance = abs(left - target)
        val rightDistance = abs(right - target)
        return when {
            rightDistance > leftDistance -> right
            leftDistance > rightDistance -> left
            // Equally far, on opposite sides of the target: the lower value, so
            // the answer never depends on which side is called left.
            else -> minOf(left, right)
        }
    }
}
