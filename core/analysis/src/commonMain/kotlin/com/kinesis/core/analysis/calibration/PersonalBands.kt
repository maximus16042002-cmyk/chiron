package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.BandPosition
import com.kinesis.core.domain.calibration.HoldAnalysisResult
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.MetricPlacement
import com.kinesis.core.domain.calibration.RepAnalysisResult
import com.kinesis.core.domain.calibration.RepTiming
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import com.kinesis.core.domain.calibration.TimingBaseline
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord

/**
 * Personal thresholds, and where a record falls against them - DECISIONS.md
 * P3-21.
 *
 * ### The band
 *
 * Tukey's fences over the user's own calibration: `q1 - k * IQR` to
 * `q3 + k * IQR`, edges inclusive, with `k` = [FENCE_MULTIPLIER]. It is the
 * standard robust rule for "outside the usual spread" and it is built from the
 * same median/IQR statistics FINAL_ARCHITECTURE section 7 specifies for the
 * baseline. **It is a statistical convention, not a measured threshold** for
 * anything about movement. Study 2 compares its flag rate against population
 * thresholds on MM-Fit; until then `k` is provisional.
 *
 * A baseline whose calibration was perfectly repeatable has an IQR of zero, and
 * its band is exactly `q1..q3`. That is what the rule says and it is kept; a
 * floor would be a threshold with no source.
 *
 * ### What this does not do
 *
 * It places values. It does not write "below your normal". That sentence is a
 * `Deviation` finding, and composing findings is P8's Layer 1 deviation rules -
 * which will read these placements rather than re-derive them.
 */
object PersonalBands {

    /** Bump when the band rule or the set of placed metrics changes. */
    const val RULE_VERSION: String = "personal-band-tukey-1"

    /** Tukey's `k`. **Provisional** until Study 2. */
    const val FENCE_MULTIPLIER: Double = 1.5

    // Stable metric ids, shared with the facade's JSON and the P7 pipeline.
    const val ROM: String = "rom_deg"
    const val DESCENT: String = "descent_ms"
    const val ASCENT: String = "ascent_ms"
    const val DURATION: String = "duration_ms"
    const val TEMPO_RATIO: String = "tempo_ratio"
    const val ROM_OFFSET: String = "rom_left_minus_right_deg"
    const val TOTAL_HOLD: String = "total_hold_ms"
    const val LONGEST_UNBROKEN: String = "longest_unbroken_ms"
    const val MEAN_ABS_DEVIATION: String = "mean_abs_deviation_deg"

    /** One value against one metric's summary. */
    fun place(
        metric: String,
        side: Side?,
        value: Double,
        summary: RobustSummary,
        minimumSamples: Int,
        fenceMultiplier: Double = FENCE_MULTIPLIER,
    ): MetricPlacement {
        if (summary.n < minimumSamples) {
            val position = if (value.isFinite()) BandPosition.NO_BASELINE else BandPosition.NOT_MEASURED
            return MetricPlacement(metric, side, value, Double.NaN, Double.NaN, position)
        }
        val lower = summary.q1 - fenceMultiplier * summary.iqr
        val upper = summary.q3 + fenceMultiplier * summary.iqr
        val position = when {
            !value.isFinite() -> BandPosition.NOT_MEASURED
            value < lower -> BandPosition.BELOW
            value > upper -> BandPosition.ABOVE
            else -> BandPosition.WITHIN
        }
        return MetricPlacement(metric, side, value, lower, upper, position)
    }

    /**
     * Places one repetition against the user's baseline for its exercise.
     *
     * @throws IllegalArgumentException if [baseline] belongs to another exercise.
     *   A variant and its parent share one, so a goblet squat against the squat
     *   baseline is accepted; a push-up against it is not.
     */
    fun analyseRep(profile: RepetitionExercise, rep: RepRecord, baseline: RepetitionBaseline): RepAnalysisResult {
        requireSameExercise(profile, baseline.exerciseId)
        val min = baseline.minimumSamples
        val placements = ArrayList<MetricPlacement>(12)
        placements += place(ROM, Side.LEFT, rep.rangeOfMotion.left, baseline.rangeOfMotion.left, min)
        placements += place(ROM, Side.RIGHT, rep.rangeOfMotion.right, baseline.rangeOfMotion.right, min)

        when (val timing = baseline.timing) {
            is TimingBaseline.Together -> placeTiming(placements, null, rep, timing.both, min)
            is TimingBaseline.PerSide -> {
                // A per-limb repetition's timing belongs to the side that performed
                // it; the other side's timing was not measured by this record.
                placeTiming(placements, Side.LEFT, rep.takeIf { it.attributedSide == Side.LEFT }, timing.left, min)
                placeTiming(placements, Side.RIGHT, rep.takeIf { it.attributedSide == Side.RIGHT }, timing.right, min)
            }
        }

        if (baseline.timing is TimingBaseline.Together) {
            // Named reducer, as in the fit: left minus right, direction kept.
            placements += place(ROM_OFFSET, null, rep.rangeOfMotion.signedDifference(), baseline.habitualOffset, min)
        }

        return RepAnalysisResult(
            repIndex = rep.repIndex,
            exerciseId = baseline.exerciseId,
            baselineVersion = baseline.methodVersion,
            ruleVersion = RULE_VERSION,
            placements = placements,
        )
    }

    /** Places one hold against the user's hold baseline. */
    fun analyseHold(profile: IsometricExercise, hold: HoldRecord, baseline: HoldBaseline): HoldAnalysisResult {
        requireSameExercise(profile, baseline.exerciseId)
        val min = baseline.minimumSamples
        return HoldAnalysisResult(
            startMs = hold.startMs,
            exerciseId = baseline.exerciseId,
            baselineVersion = baseline.methodVersion,
            ruleVersion = RULE_VERSION,
            placements = listOf(
                place(TOTAL_HOLD, null, hold.totalHoldMs, baseline.totalHoldMs, min),
                place(LONGEST_UNBROKEN, null, hold.longestUnbrokenMs, baseline.longestUnbrokenMs, min),
                place(MEAN_ABS_DEVIATION, null, hold.meanAbsDeviation, baseline.meanAbsDeviation, min),
            ),
        )
    }

    private fun placeTiming(
        into: MutableList<MetricPlacement>,
        side: Side?,
        rep: RepRecord?,
        timing: RepTiming,
        min: Int,
    ) {
        into += place(DESCENT, side, rep?.descentMs ?: Double.NaN, timing.descentMs, min)
        into += place(ASCENT, side, rep?.ascentMs ?: Double.NaN, timing.ascentMs, min)
        into += place(DURATION, side, rep?.durationMs ?: Double.NaN, timing.durationMs, min)
        into += place(TEMPO_RATIO, side, rep?.tempoRatio ?: Double.NaN, timing.tempoRatio, min)
    }

    private fun requireSameExercise(profile: ExerciseProfile, baselineExerciseId: String) {
        val id = Calibration.calibrationId(profile)
        require(id == baselineExerciseId) {
            "a " + profile.id + " record cannot be compared against a " + baselineExerciseId + " baseline"
        }
    }
}
