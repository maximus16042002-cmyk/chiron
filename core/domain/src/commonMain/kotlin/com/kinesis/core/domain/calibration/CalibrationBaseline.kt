package com.kinesis.core.domain.calibration

/**
 * The robust summary of one metric over a user's calibration samples: how many
 * there were, and their quartiles.
 *
 * FINAL_ARCHITECTURE section 7 fixes the method: calibration baselines are
 * **statistical, median and IQR** - robust estimation, not learning. The
 * quartiles are computed by `RobustStatistics` in `:core:analysis`, with the
 * definition pinned there so the P7/P12 Python pipeline computes the same
 * numbers. This type only holds the result.
 *
 * A metric with no samples is [ABSENT]: `n = 0` and every statistic [Double.NaN].
 * It is never zero, because zero is a measurement and none was made.
 */
class RobustSummary(
    val n: Int,
    val median: Double,
    val q1: Double,
    val q3: Double,
) {
    init {
        require(n >= 0) { "a sample count cannot be negative, got " + n }
        require(n > 0 || (median.isNaN() && q1.isNaN() && q3.isNaN())) {
            "a summary of no samples has no statistics; got median=" + median
        }
    }

    /** Interquartile range, `q3 - q1`. [Double.NaN] when [n] is 0. */
    val iqr: Double get() = q3 - q1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RobustSummary) return false
        return n == other.n && same(median, other.median) && same(q1, other.q1) && same(q3, other.q3)
    }

    override fun hashCode(): Int {
        var result = n
        result = 31 * result + median.hashCode()
        result = 31 * result + q1.hashCode()
        result = 31 * result + q3.hashCode()
        return result
    }

    override fun toString(): String = "RobustSummary(n=" + n + ", median=" + median + ", q1=" + q1 + ", q3=" + q3 + ")"

    private fun same(a: Double, b: Double): Boolean = a == b || (a.isNaN() && b.isNaN())

    companion object {
        val ABSENT: RobustSummary = RobustSummary(0, Double.NaN, Double.NaN, Double.NaN)
    }
}

/**
 * One metric summarised separately for each side of the body - CLAUDE.md
 * section 3.
 *
 * The bilateral counterpart of `BilateralSignal` for calibration. The two sides
 * are fitted from their own samples and never meet: there is no combined
 * median, and no way to ask for one. A side that was never measured is
 * [RobustSummary.ABSENT], so an alternating exercise's right arm cannot
 * silently borrow the left arm's normal.
 */
data class BilateralSummary(val left: RobustSummary, val right: RobustSummary)

/**
 * The timing of a repetition, summarised: descent, ascent, whole duration and
 * the descent/ascent tempo ratio.
 */
data class RepTiming(
    val descentMs: RobustSummary,
    val ascentMs: RobustSummary,
    val durationMs: RobustSummary,
    val tempoRatio: RobustSummary,
)

/**
 * Whose timing a baseline holds, which depends on what a repetition *is* for the
 * exercise - DECISIONS.md P3-1.
 *
 * `sealed` because the two cases are genuinely different measurements, not one
 * measurement stored two ways. In a per-limb exercise each arm's curl has its own
 * duration, so each side gets its own timing. In a simultaneous exercise both
 * sides perform one repetition together and the record holds one timing for it;
 * writing that one number into a left and a right field would claim two
 * measurements where one was made.
 */
sealed interface TimingBaseline {

    /** Per-limb repetitions: each side timed from the repetitions it performed. */
    data class PerSide(val left: RepTiming, val right: RepTiming) : TimingBaseline

    /** Simultaneous repetitions: one timing, for both sides moving together. */
    data class Together(val both: RepTiming) : TimingBaseline
}

/**
 * A user's personal normal for one exercise - CLAUDE.md section 15, DECISIONS.md
 * P3-21.
 *
 * Fitted on the device from that user's own valid calibration repetitions or
 * holds. Nothing here is a population norm and nothing here is a judgement of
 * correct form: a baseline is what *this person* usually does, and a Level 2
 * claim is a comparison against it.
 *
 * `sealed`, with a variant per analysis mode, for the reason `SetRecord` is:
 * a hold baseline with somewhere to put a range of motion is one something will
 * eventually put one in - CLAUDE.md section 11.
 *
 * ### Variants share it
 *
 * [exerciseId] is the **parent** profile's id for a variant: a goblet squat is
 * calibrated, and compared, as a squat (CONTRACTS.md section 3, DECISIONS.md
 * P3-6). Whether that is the right comparison for a loaded squat is recorded as
 * an open question in P3-6, not settled here.
 */
sealed interface CalibrationBaseline {

    /** The calibrated profile's id; a variant's parent's id. */
    val exerciseId: String

    /** The fitting method's version, so a stored baseline stays re-derivable. */
    val methodVersion: String

    /**
     * The fewest samples a metric needs before it counts as established. Stored
     * with the baseline so a later change to the constant does not quietly
     * re-judge an old baseline.
     */
    val minimumSamples: Int

    /** Records offered to the fit, valid or not. */
    val recordsOffered: Int

    /** Records the fit actually used: the valid ones. */
    val recordsUsed: Int
}

/** The baseline of a repetition exercise. */
class RepetitionBaseline(
    override val exerciseId: String,
    override val methodVersion: String,
    override val minimumSamples: Int,
    override val recordsOffered: Int,
    override val recordsUsed: Int,

    /** Range of motion, each side from its own measured repetitions. */
    val rangeOfMotion: BilateralSummary,

    val timing: TimingBaseline,

    /**
     * The user's habitual left-minus-right range of motion, over repetitions in
     * which both sides were measured - FINAL_ARCHITECTURE section 6's
     * "(L - R) - habitual offset". Taken with the named reducer
     * `BilateralSignal.signedDifference()`, so direction is kept.
     *
     * [RobustSummary.ABSENT] for a per-limb exercise, whose sides never move in
     * the same repetition and so have no offset to speak of.
     */
    val habitualOffset: RobustSummary,
) : CalibrationBaseline {

    override fun toString(): String =
        "RepetitionBaseline(" + exerciseId + ", " + recordsUsed + "/" + recordsOffered + " reps, " + methodVersion + ")"
}

/**
 * The baseline of an isometric hold. Carries no range of motion and no
 * repetition timing, deliberately.
 */
class HoldBaseline(
    override val exerciseId: String,
    override val methodVersion: String,
    override val minimumSamples: Int,
    override val recordsOffered: Int,
    override val recordsUsed: Int,
    val totalHoldMs: RobustSummary,
    val longestUnbrokenMs: RobustSummary,
    val meanAbsDeviation: RobustSummary,
) : CalibrationBaseline {

    override fun toString(): String =
        "HoldBaseline(" + exerciseId + ", " + recordsUsed + "/" + recordsOffered + " holds, " + methodVersion + ")"
}
