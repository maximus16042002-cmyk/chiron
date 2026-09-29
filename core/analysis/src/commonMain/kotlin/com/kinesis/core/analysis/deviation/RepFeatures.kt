package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.timingSideOf
import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.RepRecord

/**
 * One completed repetition's Layer 1 features - DECISIONS.md P3-30. Per side, and
 * never averaged: a side the repetition did not measure is `NaN`.
 *
 * Post-rep path: built from a finished record, once.
 */
class RepFeatures(

    /** Range of motion, per side, degrees - the record's own. */
    val rangeOfMotion: BilateralSignal,

    /**
     * The side the timing belongs to, or `null` when both sides moved together
     * and the timing is the repetition's - exactly as the record attributes it
     * (DECISIONS.md P3-2, P3-22).
     */
    val timingSide: Side?,

    /** Time in the concentric phase - the lift - milliseconds. */
    val liftingMs: Double,

    /** Time in the eccentric phase - the lowering - milliseconds. */
    val loweringMs: Double,

    /**
     * Left minus right range of motion, degrees, when this repetition measured
     * both sides; `NaN` otherwise. An alternating or near-side-only repetition
     * has no per-repetition asymmetry (P3-22).
     */
    val leftMinusRight: Double,
) {
    companion object {
        fun of(profile: RepetitionExercise, rep: RepRecord): RepFeatures {
            val liftsAway = profile.awayFromStart == Contraction.CONCENTRIC
            val paired = rep.rangeOfMotion.left.isFinite() && rep.rangeOfMotion.right.isFinite()
            return RepFeatures(
                rangeOfMotion = rep.rangeOfMotion,
                timingSide = timingSideOf(rep),
                liftingMs = if (liftsAway) rep.descentMs else rep.ascentMs,
                loweringMs = if (liftsAway) rep.ascentMs else rep.descentMs,
                leftMinusRight = if (paired) rep.rangeOfMotion.signedDifference() else Double.NaN,
            )
        }
    }
}
