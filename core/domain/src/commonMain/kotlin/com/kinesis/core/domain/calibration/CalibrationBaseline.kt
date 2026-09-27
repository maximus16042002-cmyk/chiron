package com.kinesis.core.domain.calibration

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side

/**
 * One user's normal execution of one exercise, learned from their own
 * calibration set - CLAUDE.md section 15, DECISIONS.md P3-22.
 *
 * This is what Level 2 claims are measured against: "below your baseline",
 * "unusual for you". It is **measurement of the user**, not a standard: nothing
 * here says what correct movement is, and no population norm is involved.
 *
 * ### Per user, per exercise
 *
 * The core type knows the exercise, not the user. Which user a baseline belongs
 * to is a storage key, kept by the platform, because the core has no notion of
 * an account. Variants share their parent's baseline (CONTRACTS.md section 3),
 * so [calibrationId] is always a parent profile's id - a goblet squat is
 * measured against the squat baseline.
 *
 * ### Provisional
 *
 * Every baseline records the [estimatorVersion] that produced it, and the
 * calibration counts behind that version are provisional until the MM-Fit
 * convergence study (ZERO_DATASET_BUILD_PLAN.md, Study 3) sets them.
 */
sealed interface CalibrationBaseline {

    /** The profile whose baseline this is. A parent profile's id, never a variant's. */
    val calibrationId: String

    /**
     * Which estimator and which calibration counts produced this, e.g. `p6-1`,
     * so a baseline can be recomputed, or discarded, when either changes.
     */
    val estimatorVersion: String

    /** When the calibration set was performed, epoch milliseconds. */
    val createdAtEpochMs: Double

    /** The session holding the calibration set, if it was saved. Provenance only. */
    val sourceSessionId: String?
}

/**
 * A robust summary of one quantity over the calibration repetitions: the median,
 * and the interquartile range as the repetition-to-repetition spread.
 *
 * FINAL_ARCHITECTURE section 7: "Calibration baselines - Statistical
 * (median/IQR) - Robust estimation, not learning." The quartile definition is
 * fixed in `RobustStatistics` so every platform, and the Python study, compute
 * the same number.
 */
class RobustStatistic(

    val median: Double,

    /** Q3 minus Q1. The rep-to-rep variability of this quantity. */
    val interquartileRange: Double,

    /** How many values it was computed from. Zero means not measured. */
    val count: Int,
) {

    val isMeasured: Boolean get() = count > 0

    override fun equals(other: Any?): Boolean =
        other is RobustStatistic &&
            sameDouble(median, other.median) &&
            sameDouble(interquartileRange, other.interquartileRange) &&
            count == other.count

    override fun hashCode(): Int = 31 * (31 * median.hashCode() + interquartileRange.hashCode()) + count

    override fun toString(): String = "RobustStatistic(median=$median, iqr=$interquartileRange, n=$count)"

    companion object {
        /** Nothing was measured. `NaN`, never zero: zero is a measurement. */
        val NOT_MEASURED: RobustStatistic = RobustStatistic(Double.NaN, Double.NaN, 0)
    }
}

/**
 * A [RobustStatistic] per side. The two sides are never merged, here or
 * anywhere downstream - CLAUDE.md section 3.
 */
class BilateralStatistic(val left: RobustStatistic, val right: RobustStatistic) {

    fun on(side: Side): RobustStatistic = if (side == Side.LEFT) left else right

    /** Each side's median, still two values. Not a reduction. */
    val medians: BilateralSignal get() = BilateralSignal(left.median, right.median)

    override fun equals(other: Any?): Boolean =
        other is BilateralStatistic && left == other.left && right == other.right

    override fun hashCode(): Int = 31 * left.hashCode() + right.hashCode()

    override fun toString(): String = "BilateralStatistic(left=$left, right=$right)"

    companion object {
        val NOT_MEASURED: BilateralStatistic =
            BilateralStatistic(RobustStatistic.NOT_MEASURED, RobustStatistic.NOT_MEASURED)
    }
}

/**
 * One phase's timing - descent or ascent - as the records attribute it.
 *
 * A `RepRecord` carries one descent time and one ascent time. Where the
 * repetition belongs to one side - an alternating arm, or the one side a
 * near-side-only view can see - that timing is that side's, and it goes in
 * [perSide]. Where both sides moved together, the tracker took the timing from
 * the side it measured for longer (DECISIONS.md P3-2): it describes the
 * repetition, not either side, and it goes in [perRepetition]. Filing it under
 * both sides would claim two per-side measurements that were never made.
 */
class PhaseTimingBaseline(
    val perSide: BilateralStatistic,
    val perRepetition: RobustStatistic,
) {
    override fun equals(other: Any?): Boolean =
        other is PhaseTimingBaseline && perSide == other.perSide && perRepetition == other.perRepetition

    override fun hashCode(): Int = 31 * perSide.hashCode() + perRepetition.hashCode()
}

/**
 * The user's habitual left-minus-right range of motion - the offset
 * FINAL_ARCHITECTURE section 2's asymmetry rule, "(L - R) - habitual offset",
 * subtracts. Signed degrees. How it can be known depends on how the sides were
 * seen, so the three cases are three types rather than one number whose meaning
 * shifts.
 */
sealed interface AsymmetryBaseline {

    /**
     * Both sides were measured in the same repetitions, so each repetition has
     * its own left-minus-right and they have a median and a spread.
     */
    class Paired(val leftMinusRight: RobustStatistic) : AsymmetryBaseline {
        override fun equals(other: Any?): Boolean = other is Paired && leftMinusRight == other.leftMinusRight
        override fun hashCode(): Int = leftMinusRight.hashCode()
    }

    /**
     * The sides were measured in different repetitions - alternating limbs - so
     * there is no per-repetition difference. The offset is the difference of the
     * two sides' median ranges of motion, and it has no spread of its own.
     */
    class BetweenSideMedians(val leftMinusRight: Double) : AsymmetryBaseline {
        override fun equals(other: Any?): Boolean =
            other is BetweenSideMedians && sameDouble(leftMinusRight, other.leftMinusRight)

        override fun hashCode(): Int = leftMinusRight.hashCode()
    }

    /**
     * Only one side was measured, or the profile's view can only ever see one -
     * the near-side-only row. FINAL_ARCHITECTURE section 5: "no per-side
     * comparison, no symmetry claim."
     */
    data object NotMeasurable : AsymmetryBaseline
}

/**
 * The baseline of a repetition exercise, per side.
 *
 * Every per-side quantity is a [BilateralStatistic], and a side that was not
 * measured - the far arm of a row - is [RobustStatistic.NOT_MEASURED] rather than
 * zero or a copy of the other side.
 */
class RepetitionBaseline(
    override val calibrationId: String,
    override val estimatorVersion: String,
    override val createdAtEpochMs: Double,
    override val sourceSessionId: String?,

    /** Peak-to-peak excursion of the driving joint angle, degrees. */
    val rangeOfMotion: BilateralStatistic,

    /** The driving angle at the starting position, degrees. */
    val peakValue: BilateralStatistic,

    /** The driving angle at the far position, degrees. */
    val troughValue: BilateralStatistic,

    /** Time moving away from the starting position, milliseconds. */
    val descentMs: PhaseTimingBaseline,

    /** Time returning to it, milliseconds. */
    val ascentMs: PhaseTimingBaseline,

    val asymmetry: AsymmetryBaseline,

    /**
     * The pointwise mean of the used repetitions' resampled curves, per side -
     * `RepRecord`'s fixed-length signal (64 points). All `NaN` for a side that
     * was not measured.
     */
    val meanCurveLeft: DoubleArray,
    val meanCurveRight: DoubleArray,

    /**
     * How far each used repetition's curve lies from the mean curve - the
     * root-mean-square difference over their points, degrees - as a median and
     * spread per side. The shape's repetition-to-repetition variability.
     */
    val curveSpread: BilateralStatistic,
) : CalibrationBaseline {

    /** Repetitions used on each side. The same count stands behind every per-side statistic. */
    val repetitionsUsedLeft: Int get() = rangeOfMotion.left.count
    val repetitionsUsedRight: Int get() = rangeOfMotion.right.count

    fun meanCurve(side: Side): DoubleArray = if (side == Side.LEFT) meanCurveLeft else meanCurveRight

    override fun toString(): String =
        "RepetitionBaseline($calibrationId, $estimatorVersion, rom=$rangeOfMotion)"
}

/**
 * The baseline of an isometric hold: typical posture and steadiness.
 *
 * **One value, not one per side, on purpose.** A hold is analysed on the body
 * line, which both sides' landmarks describe together (DECISIONS.md P3-9), and
 * `HoldReading.furthestFromTarget` reduces the two to one signal by a named rule
 * before the hold analyser sees it (P3-16). So the hold record carries no
 * per-side quantity, and neither can a baseline built from it.
 *
 * Built from one calibration hold - a hold set is one hold - so these are that
 * hold's values rather than statistics across several.
 */
class HoldBaseline(
    override val calibrationId: String,
    override val estimatorVersion: String,
    override val createdAtEpochMs: Double,
    override val sourceSessionId: String?,

    /** The profile's detection target, recorded so the offset below can be read. */
    val targetValue: Double,

    /** Where the user held the body line, degrees: the mean over the frames that held. */
    val meanHeldValue: Double,

    /** Mean absolute distance from [targetValue] while holding, degrees. */
    val meanAbsDeviation: Double,

    /**
     * Steadiness: the standard deviation of the body-line angle over the frames
     * that held, degrees. Smaller is steadier.
     */
    val sway: Double,

    /** Time held in the calibration hold, milliseconds. */
    val heldMs: Double,

    val breakCount: Int,

    /** Fraction of the calibration attempt in which the user was measurable. */
    val coverage: Double,
) : CalibrationBaseline {

    /** Signed: held above the target is positive. The typical posture deviation. */
    val postureOffset: Double get() = meanHeldValue - targetValue

    override fun toString(): String =
        "HoldBaseline($calibrationId, held at $meanHeldValue, sway $sway)"
}

/** `NaN` equals `NaN` here: two "not measured" values are the same value. */
private fun sameDouble(a: Double, b: Double): Boolean = a == b || (a.isNaN() && b.isNaN())
