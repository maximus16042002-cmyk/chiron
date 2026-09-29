package com.kinesis.core.analysis.deviation

import com.kinesis.core.domain.calibration.RobustStatistic
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The normal-consistent robust standard deviation: an IQR divided by this is an
 * SD for normally distributed values. The "baseline-SD unit" of DECISIONS.md P3-30.
 */
const val IQR_PER_SD: Double = 1.349

/**
 * Where a value lies against a reference, in the reference's own spread - the one
 * scaled-deviation rule both comparators use (DECISIONS.md P3-30):
 * `(value - centre) / max(spread, floor)`.
 *
 * `NaN` in any input - not measured - gives `NaN`, which no threshold ever flags.
 */
fun scaledDeviation(value: Double, centre: Double, spread: Double, floor: Double): Double {
    if (value.isNaN() || centre.isNaN() || spread.isNaN()) return Double.NaN
    return (value - centre) / max(spread, floor)
}

/** The user's own baseline for one quantity: its median, and its IQR as a robust SD. */
class PersonalReference(val centre: Double, val spread: Double) {

    fun scaled(value: Double, floor: Double): Double = scaledDeviation(value, centre, spread, floor)

    companion object {
        fun of(statistic: RobustStatistic): PersonalReference =
            PersonalReference(statistic.median, statistic.interquartileRange / IQR_PER_SD)
    }
}

/**
 * A population threshold's reference: **an evaluation instrument, never a finding.**
 *
 * CLAUDE.md section 12a gives Layer 1 the user's own calibration as its only
 * evidence, and Level 2 is personal. A population norm with no citation has no
 * claim level it could honestly carry, so nothing in the core turns one of these
 * into a `Finding` - the study uses it to compare against the personal comparator
 * (DECISIONS.md P3-30). The one cited population threshold, ACSM's tempo, is a
 * Level 3 rule of its own ([AcsmTempoRule]).
 *
 * [provenance] says where it came from, e.g. "derived from MM-Fit development
 * subjects, P3-30".
 */
class PopulationReference(val centre: Double, val spread: Double, val provenance: String) {

    fun scaled(value: Double, floor: Double): Double = scaledDeviation(value, centre, spread, floor)

    companion object {
        /**
         * From several people's own statistics: centred on the mean of their
         * medians, with the population's total spread - between people and within
         * them - `sqrt(sd(medians)^2 + mean(withinSpread^2))`. The between-people
         * SD is the sample SD (n - 1), zero for one person.
         */
        fun fromSubjects(medians: DoubleArray, withinSpreads: DoubleArray, provenance: String): PopulationReference {
            require(medians.isNotEmpty() && medians.size == withinSpreads.size) {
                "one median and one within-person spread per person; got ${medians.size} and ${withinSpreads.size}"
            }
            val count = medians.size
            var sum = 0.0
            for (median in medians) sum += median
            val centre = sum / count
            var squares = 0.0
            for (median in medians) squares += (median - centre) * (median - centre)
            val betweenVariance = if (count > 1) squares / (count - 1) else 0.0
            var within = 0.0
            for (spread in withinSpreads) within += spread * spread
            val withinVariance = within / count
            return PopulationReference(centre, sqrt(betweenVariance + withinVariance), provenance)
        }
    }
}
