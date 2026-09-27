package com.kinesis.core.domain.calibration

import com.kinesis.core.domain.bilateral.Side

/**
 * Where one measured value falls against the user's personal band.
 */
enum class BandPosition {

    /** Below the band's lower edge. */
    BELOW,

    WITHIN,

    /** Above the band's upper edge. */
    ABOVE,

    /** The record has no value here - the side did not move, or was not seen. */
    NOT_MEASURED,

    /**
     * The baseline has too few samples for this metric to have a band - for
     * example the right arm of a curl calibration performed mostly with the left.
     */
    NO_BASELINE,
}

/**
 * One metric of one record, placed against the user's personal band.
 *
 * A placement is interpretation, not a sentence. It says *where* the value fell;
 * turning that into "18% below your normal" is P8's Layer 1 deviation rules,
 * which produce `Deviation` findings with their own rule versions. Keeping the
 * placement free of wording means P8 can change how it speaks without
 * re-deriving where anything fell. DECISIONS.md P3-21.
 */
class MetricPlacement(

    /** Stable metric id, e.g. `rom_deg`, `descent_ms`. */
    val metric: String,

    /**
     * The side measured, or `null` when the metric belongs to both sides at once:
     * the timing of a simultaneous repetition, or the left-minus-right offset.
     */
    val side: Side?,

    /** The record's value; [Double.NaN] when not measured. */
    val value: Double,

    /** The band's edges; [Double.NaN] when there is no band. */
    val lower: Double,
    val upper: Double,

    val position: BandPosition,
) {
    override fun toString(): String =
        "MetricPlacement(" + metric + (if (side == null) "" else "/" + side) +
            ", " + value + " in " + lower + ".." + upper + " -> " + position + ")"
}

/**
 * A record interpreted against a baseline - CLAUDE.md section 15.
 *
 * Kept apart from `RepRecord` and `HoldRecord` because those are measurement and
 * this is interpretation by a particular method: [baselineVersion] and
 * [ruleVersion] say which, so historical records can be re-analysed when the
 * rules change without touching what was measured.
 *
 * `sealed`, per analysis mode, for the same reason as `CalibrationBaseline`.
 */
sealed interface AnalysisResult {

    /** The profile the baseline belongs to; a variant's parent. */
    val exerciseId: String

    /** The fitting method of the baseline this was compared against. */
    val baselineVersion: String

    /** The version of the band rule that produced [placements]. */
    val ruleVersion: String

    val placements: List<MetricPlacement>
}

/** A repetition interpreted against a [RepetitionBaseline]. */
class RepAnalysisResult(
    /** The record's `repIndex` in its set. */
    val repIndex: Int,
    override val exerciseId: String,
    override val baselineVersion: String,
    override val ruleVersion: String,
    override val placements: List<MetricPlacement>,
) : AnalysisResult {
    override fun toString(): String =
        "RepAnalysisResult(rep " + repIndex + ", " + exerciseId + ", " + placements.size + " placements)"
}

/** A hold interpreted against a [HoldBaseline]. */
class HoldAnalysisResult(
    /** The hold record's start, which identifies it within its set. */
    val startMs: Double,
    override val exerciseId: String,
    override val baselineVersion: String,
    override val ruleVersion: String,
    override val placements: List<MetricPlacement>,
) : AnalysisResult {
    override fun toString(): String =
        "HoldAnalysisResult(" + startMs + "ms, " + exerciseId + ", " + placements.size + " placements)"
}
