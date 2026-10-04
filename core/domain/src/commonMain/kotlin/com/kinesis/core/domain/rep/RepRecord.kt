package com.kinesis.core.domain.rep

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side

/**
 * One completed repetition, as measured.
 *
 * CLAUDE.md section 15: this is measurement, not interpretation. Nothing here is
 * a judgement about whether the repetition was performed well - that is an
 * `AnalysisResult`, produced later by a specific rule or model version and kept
 * separate so historical repetitions can be re-analysed when the rules change.
 *
 * A `RepRecord` only exists once a repetition has completed, which is what makes
 * per-frame ML impossible by construction rather than by convention -
 * CONTRACTS.md section 7.
 *
 * ### Why the measurements are bilateral
 *
 * [rangeOfMotion], [peakValue] and [troughValue] are [BilateralSignal] for every
 * exercise, including alternating ones where only one side moved. The side that
 * did not contribute carries [Double.NaN], so the absence stays visible instead
 * of being filled in or averaged away. That is the distinction the old prototype
 * lost - see DECISIONS.md P2-2.
 */
class RepRecord(

    /** Position in the set, counting from 1. */
    val repIndex: Int,

    /**
     * The side that performed this repetition, for alternating exercises where
     * one arm's curl is one repetition.
     *
     * `null` for simultaneous exercises, where both sides performed the same
     * repetition together and attributing it to one would be false. See
     * DECISIONS.md P3-1.
     */
    val attributedSide: Side?,

    val startMs: Double,
    val endMs: Double,
    val validity: RepValidity,

    /** Peak-to-peak excursion of the driving signal, per side. */
    val rangeOfMotion: BilateralSignal,

    /** Signal value at the starting position, per side. */
    val peakValue: BilateralSignal,

    /** Signal value at the far position, per side. */
    val troughValue: BilateralSignal,

    /** Time spent moving away from the starting position. */
    val descentMs: Double,

    /** Time spent returning to it. */
    val ascentMs: Double,

    /**
     * The driving signal resampled to a fixed length, per side, so repetitions of
     * different durations can be compared point for point - CLAUDE.md section 15.
     * The non-contributing side of an alternating repetition is all [Double.NaN].
     */
    val resampledLeft: DoubleArray,
    val resampledRight: DoubleArray,

    /**
     * Joints other than the driving one, summarised over the repetition - DECISIONS.md P3-44.
     * Only the measures the exercise asked for; none for a record stored before P3-44, which
     * reads as not measured, never as zero.
     */
    val form: FormMeasures = FormMeasures.NONE,

    /**
     * 145-dimensional multi-signal context for exercise verification - DECISIONS.md P3-34, P3-52.
     * Evaluated post-rep; `null` when not captured.
     */
    val movementContext: MovementContext? = null,
) {

    val durationMs: Double get() = endMs - startMs

    /**
     * Tempo ratio, descent over ascent. [Double.NaN] when either phase was not
     * measured. A named derivation, not a stored field, because it is
     * interpretation of two measured quantities.
     */
    val tempoRatio: Double
        get() = if (ascentMs > 0.0) descentMs / ascentMs else Double.NaN

    override fun toString(): String =
        "RepRecord(index=" + repIndex +
            ", side=" + attributedSide +
            ", " + startMs + ".." + endMs + "ms" +
            ", " + validity +
            ", rom=" + rangeOfMotion + ")"
}

/**
 * One completed isometric hold, as measured.
 *
 * Deliberately **not** a [RepRecord] and deliberately carrying no repetition
 * count. CLAUDE.md section 11 forbids forcing an isometric exercise through the
 * repetition FSM, and a hold record with somewhere to put a rep count is a hold
 * record something will eventually put one in. A plank has a duration and a
 * steadiness; it does not have repetitions.
 */
class HoldRecord(

    val startMs: Double,
    val endMs: Double,
    val validity: HoldValidity,

    /** Total time the position was actually held, excluding breaks and gaps. */
    val totalHoldMs: Double,

    /** The longest single unbroken stretch. Equals [totalHoldMs] when unbroken. */
    val longestUnbrokenMs: Double,

    /** How many times the position was lost and regained. */
    val breakCount: Int,

    /** Mean absolute departure from the target, over the frames that held. */
    val meanAbsDeviation: Double,

    /** Worst absolute departure from the target while still within tolerance. */
    val maxAbsDeviation: Double,

    /** Fraction of the attempt in which the subject was measurable, 0..1. */
    val coverage: Double,

    /**
     * Mean of the driving signal over the frames that held - where the body line
     * actually was, signed, which [meanAbsDeviation] cannot say. `NaN` if nothing
     * held. Added in DECISIONS.md P3-22 for the hold baseline's typical posture;
     * holds recorded before it reload as `NaN`, unmeasured.
     */
    val meanHeldValue: Double,

    /**
     * Sample standard deviation of the driving signal over the frames that held:
     * how much the position swayed while within tolerance. The steadiness a hold
     * baseline is built from. `NaN` with fewer than two held frames, and for
     * holds recorded before DECISIONS.md P3-22.
     */
    val heldValueSd: Double,
) {

    val attemptDurationMs: Double get() = endMs - startMs

    override fun toString(): String =
        "HoldRecord(" + startMs + ".." + endMs + "ms" +
            ", held=" + totalHoldMs + "ms" +
            ", breaks=" + breakCount +
            ", " + validity + ")"
}
