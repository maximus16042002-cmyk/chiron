package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.deviation.AcsmTempoRule
import com.kinesis.core.analysis.deviation.DeviationRules
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.finding.Finding
import com.kinesis.core.domain.profile.TechniqueAuthority
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.analysis.BaselineReference
import com.kinesis.core.domain.analysis.BesideBaseline
import com.kinesis.core.domain.analysis.HoldAnalysisResult
import com.kinesis.core.domain.analysis.HoldComparison
import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.analysis.RepetitionComparison
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord

/**
 * The post-rep analysis - CONTRACTS.md section 7, DECISIONS.md P3-22 and P3-30.
 *
 * Takes a completed record and, if there is one, the user's baseline, and
 * returns an `AnalysisResult`: the record's values beside the baseline's, per
 * side, as numbers (P6), and the findings of P8's Layer 1 (P3-30) - personal
 * deviations when there is a baseline, and the cited ACSM tempo rule for Tier A.
 * Each finding is composed in the core with its own rule version. A hold gets
 * the comparison and no finding.
 *
 * Because the result is computed from the record and the baseline rather than
 * stored with the record, analysing an old repetition again after the baseline
 * or the rules change is simply calling this again - CLAUDE.md section 15.
 *
 * **Post-rep path.** Once per completed record, never per frame: it takes a
 * record, and a record only exists once the movement has finished.
 */
object PostRepAnalysis {

    /**
     * `p8-layer1-1` since DECISIONS.md P3-30: the comparison is P6's, and the findings
     * are Layer 1's personal deviations (with a baseline) and the cited Tier A tempo
     * rule (with or without one).
     */
    const val VERSION: String = "p8-layer1-1"

    fun analyse(profile: RepetitionExercise, rep: RepRecord, baseline: RepetitionBaseline?): RepetitionAnalysisResult {
        requireOwnBaseline(profile, baseline)
        val findings = ArrayList<Finding>()
        if (baseline != null) findings += DeviationRules.evaluate(profile, rep, baseline)
        techniqueAuthorityOf(profile)?.let { authority -> AcsmTempoRule.evaluate(authority, profile, rep)?.let { findings += it } }
        val comparison = baseline?.let {
            val timingSide = timingSideOf(rep)
            val descent = if (timingSide == null) it.descentMs.perRepetition else it.descentMs.perSide.on(timingSide)
            val ascent = if (timingSide == null) it.ascentMs.perRepetition else it.ascentMs.perSide.on(timingSide)
            RepetitionComparison(
                rangeOfMotionLeft = beside(rep.rangeOfMotion.left, it.rangeOfMotion.left),
                rangeOfMotionRight = beside(rep.rangeOfMotion.right, it.rangeOfMotion.right),
                timingSide = timingSide,
                descentMs = beside(rep.descentMs, descent),
                ascentMs = beside(rep.ascentMs, ascent),
            )
        }
        return RepetitionAnalysisResult(
            exerciseId = profile.id,
            repIndex = rep.repIndex,
            analysisVersion = VERSION,
            baseline = baseline?.let(BaselineReference::of),
            comparison = comparison,
            findings = findings,
        )
    }

    /**
     * The authority for a Level 3 finding: a Tier A profile's own, or for a variant its
     * Tier A parent's - "a variant can be analysed by its parent's rules; it cannot
     * author its own" (P3-6). `null` for every other profile, so no Level 3 rule runs.
     */
    private fun techniqueAuthorityOf(profile: ExerciseProfile): TechniqueAuthority? {
        val owner = if (profile is ExerciseVariant) profile.parent else profile
        return (owner as? TierAProfile)?.techniqueAuthority
    }

    fun analyse(profile: IsometricExercise, hold: HoldRecord, baseline: HoldBaseline?): HoldAnalysisResult {
        requireOwnBaseline(profile, baseline)
        val comparison = baseline?.let {
            HoldComparison(
                meanHeldValue = BesideBaseline(hold.meanHeldValue, it.meanHeldValue, Double.NaN),
                meanAbsDeviation = BesideBaseline(hold.meanAbsDeviation, it.meanAbsDeviation, Double.NaN),
                sway = BesideBaseline(hold.heldValueSd, it.sway, Double.NaN),
                heldMs = BesideBaseline(hold.totalHoldMs, it.heldMs, Double.NaN),
            )
        }
        return HoldAnalysisResult(
            exerciseId = profile.id,
            analysisVersion = VERSION,
            baseline = baseline?.let(BaselineReference::of),
            comparison = comparison,
            findings = emptyList(),
        )
    }

    private fun beside(measured: Double, baseline: RobustStatistic) =
        BesideBaseline(measured, baseline.median, baseline.interquartileRange)

    /** A squat is not measured against a curl baseline, however the two were looked up. */
    private fun requireOwnBaseline(profile: ExerciseProfile, baseline: CalibrationBaseline?) {
        if (baseline == null) return
        require(baseline.calibrationId == CalibrationKey.of(profile)) {
            "${profile.id} uses the ${CalibrationKey.of(profile)} baseline, not ${baseline.calibrationId}"
        }
    }
}
