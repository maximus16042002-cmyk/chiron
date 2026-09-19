package com.kinesis.core.domain.bilateral

import kotlin.math.abs
import kotlin.math.max

/** Which side of the body a measurement belongs to. */
enum class Side {
    LEFT,
    RIGHT,
    ;

    val opposite: Side
        get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * A quantity measured separately on the left and right sides of the body.
 *
 * Guards CLAUDE.md section 3 and CONTRACTS.md section 1. Left/right asymmetry is
 * one of the few things this system can honestly measure, and averaging the two
 * sides destroys it silently - nothing crashes, the number still looks
 * plausible, and the finding it was supposed to support quietly disappears.
 *
 * So there is no implicit way out of this type. There is no `toDouble()`, no
 * property that reads as "the value", and no arithmetic operator. Every path to
 * a single `Double` is a function whose name says which side won and why:
 * [asymmetry], [signedDifference], [meanExplicit], [dominant], [weaker].
 *
 * Anything derived from a bilateral quantity is itself bilateral until one of
 * those is called, which is what [map] and [zip] are for. Angles, ranges,
 * velocities and tempos are all bilateral by default.
 *
 * Reducing to a single number is not forbidden - it is required to be visible.
 * A reader of a call site must be able to see it happen.
 */
data class BilateralSignal(val left: Double, val right: Double) {

    /** The value on [side], without choosing one for the caller. */
    fun on(side: Side): Double = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
    }

    /** Applies [transform] to each side. The result is still bilateral. */
    inline fun map(transform: (Double) -> Double): BilateralSignal =
        BilateralSignal(transform(left), transform(right))

    /**
     * Combines two bilateral quantities side by side - left with left, right
     * with right. The result is still bilateral, and the sides cannot cross.
     */
    inline fun zip(other: BilateralSignal, transform: (Double, Double) -> Double): BilateralSignal =
        BilateralSignal(transform(left, other.left), transform(right, other.right))

    // --- Named reducers -------------------------------------------------------
    //
    // Each one collapses two numbers into one. Each one is named for what it
    // keeps and what it throws away.

    /**
     * Symmetric asymmetry index: `|left - right| / max(|left|, |right|)`.
     *
     * Zero when the sides agree, and larger as they diverge. The range is 0..2,
     * reaching 2 only when the sides are equal in magnitude and opposite in
     * sign. Direction is discarded - use [signedDifference] to keep it.
     *
     * A pair of exact zeroes is defined as perfectly symmetric rather than
     * undefined, so this never returns NaN for a stationary joint.
     *
     * This is a definition, not a measured threshold. What counts as too much
     * asymmetry for a given user is a Level 2 question answered against that
     * user's own calibration baseline, not by this function.
     */
    fun asymmetry(): Double {
        val scale = max(abs(left), abs(right))
        return if (scale == 0.0) 0.0 else abs(left - right) / scale
    }

    /** `left - right`. Keeps the direction that [asymmetry] discards. */
    fun signedDifference(): Double = left - right

    /**
     * The arithmetic mean of the two sides.
     *
     * Named to be conspicuous. CLAUDE.md section 22 forbids averaging bilateral
     * signals silently; this is how it is done deliberately, at a call site
     * where a reader can see it and ask why.
     */
    fun meanExplicit(): Double = (left + right) / 2.0

    /** The side with the larger magnitude. Ties resolve to [Side.LEFT]. */
    fun dominantSide(): Side = if (abs(left) >= abs(right)) Side.LEFT else Side.RIGHT

    /** The side with the smaller magnitude. Ties resolve to [Side.RIGHT]. */
    fun weakerSide(): Side = dominantSide().opposite

    /**
     * The value on the side with the larger magnitude, sign preserved.
     *
     * Magnitude, not signed value: a velocity of -12.0 is a larger movement than
     * one of +5.0. Ties resolve to the left side, deterministically, because
     * CONTRACTS.md section 6 requires discrete outputs to match exactly across
     * platforms.
     */
    fun dominant(): Double = on(dominantSide())

    /** The value on the side with the smaller magnitude, sign preserved. */
    fun weaker(): Double = on(weakerSide())
}
