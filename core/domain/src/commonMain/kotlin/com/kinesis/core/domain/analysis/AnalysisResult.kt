package com.kinesis.core.domain.analysis

import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.finding.Finding

/**
 * What the post-rep path made of one completed repetition or hold -
 * CLAUDE.md section 15, FINAL_ARCHITECTURE section 8, DECISIONS.md P3-22.
 *
 * ### Separate from the record, on purpose
 *
 * A `RepRecord` is measurement: what the camera saw. This is interpretation: what
 * a particular version of the analysis concluded from it, against a particular
 * baseline. Keeping them apart is what lets historical repetitions be analysed
 * again when the rules or the baseline change - the record never changes, and a
 * new `AnalysisResult` is simply computed from it. So nothing here is stored
 * inside a record, and no record field points here.
 *
 * ### What P6 puts in it, and what it does not
 *
 * - [baseline] names the baseline compared against, or is `null` when the user
 *   has not calibrated the exercise.
 * - The comparison is **numbers only**: the repetition's value next to the
 *   baseline's, per side. No difference, no percentage, no direction, no
 *   wording. Judgement - "shallower than your usual" - and the rules that
 *   produce it are P8, composed in the core with their own `ruleVersion`.
 * - [findings] was always empty in P6. It is the list P8's `Deviation` and
 *   `TechniqueFault` findings go in, each carrying its own claim level and rule
 *   version (FINAL_ARCHITECTURE section 8: "AnalysisResult carries claimLevel
 *   and ruleVersion per finding"). P8 Layer 1 fills it with Level 2 deviations
 *   for completed repetitions - DECISIONS.md P3-23.
 */
sealed interface AnalysisResult {

    /** The library entry analysed. For a variant, the variant's own id. */
    val exerciseId: String

    /** Which version of the post-rep analysis produced this, e.g. `p8-deviation-1`. */
    val analysisVersion: String

    /** The baseline compared against, or `null` if there is none. */
    val baseline: BaselineReference?

    val findings: List<Finding>
}

/** Enough to trace which baseline an analysis used, without copying it. */
class BaselineReference(
    val calibrationId: String,
    val estimatorVersion: String,
    val createdAtEpochMs: Double,
) {
    override fun equals(other: Any?): Boolean =
        other is BaselineReference &&
            calibrationId == other.calibrationId &&
            estimatorVersion == other.estimatorVersion &&
            createdAtEpochMs == other.createdAtEpochMs

    override fun hashCode(): Int = 31 * (31 * calibrationId.hashCode() + estimatorVersion.hashCode()) +
        createdAtEpochMs.hashCode()

    companion object {
        fun of(baseline: CalibrationBaseline): BaselineReference =
            BaselineReference(baseline.calibrationId, baseline.estimatorVersion, baseline.createdAtEpochMs)
    }
}

/**
 * One measured value next to the baseline's value for the same quantity. Numbers
 * only: this type deliberately has no difference, ratio or direction.
 *
 * Any of the three is `NaN` when not measured - an alternating repetition's idle
 * arm, or a baseline side the camera never saw.
 */
class BesideBaseline(

    /** This repetition's, or this hold's, value. */
    val measured: Double,

    /** The baseline's value: a median for repetitions, the calibration hold's value for holds. */
    val baseline: Double,

    /** The baseline's rep-to-rep spread (interquartile range). `NaN` for holds. */
    val baselineSpread: Double,
)

/** A repetition's analysis. */
class RepetitionAnalysisResult(
    override val exerciseId: String,

    /** The repetition's position in its set, as `RepRecord.repIndex`. */
    val repIndex: Int,

    override val analysisVersion: String,
    override val baseline: BaselineReference?,

    /** `null` when there is no baseline to compare against. */
    val comparison: RepetitionComparison?,

    override val findings: List<Finding>,
) : AnalysisResult

/**
 * A repetition's range of motion and tempo beside the baseline's, per side.
 *
 * Range of motion is per side, as the record measures it. Tempo is attributed
 * the way the record attributes it - see `PhaseTimingBaseline`: [timingSide] is
 * the side the repetition's timing belongs to, or `null` when both sides moved
 * together and the timing is the repetition's.
 */
class RepetitionComparison(
    val rangeOfMotionLeft: BesideBaseline,
    val rangeOfMotionRight: BesideBaseline,
    val timingSide: Side?,
    val descentMs: BesideBaseline,
    val ascentMs: BesideBaseline,
) {
    fun rangeOfMotion(side: Side): BesideBaseline = if (side == Side.LEFT) rangeOfMotionLeft else rangeOfMotionRight
}

/** A hold's analysis. */
class HoldAnalysisResult(
    override val exerciseId: String,
    override val analysisVersion: String,
    override val baseline: BaselineReference?,

    /** `null` when there is no baseline to compare against. */
    val comparison: HoldComparison?,

    override val findings: List<Finding>,
) : AnalysisResult

/** A hold's posture and steadiness beside the calibration hold's. */
class HoldComparison(
    val meanHeldValue: BesideBaseline,
    val meanAbsDeviation: BesideBaseline,
    val sway: BesideBaseline,
    val heldMs: BesideBaseline,
)
