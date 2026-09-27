package com.kinesis.core.analysis.deviation

import com.kinesis.core.domain.analysis.BesideBaseline
import com.kinesis.core.domain.analysis.RepetitionComparison
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The band a Layer 1 rule compares against - DECISIONS.md P3-23.
 *
 * A value is flagged when it lies further from the user's own median than
 * [SPREAD_MULTIPLIER] times their own rep-to-rep spread (the interquartile range
 * from calibration). With symmetric quartiles, median ± 2 IQR is Tukey's
 * 1.5 IQR fence.
 *
 * Each band also has a floor, so a calibration whose repetitions happened to be
 * identical - spread zero - does not turn every later repetition into a finding.
 *
 * **Every number here is provisional.** None was measured on this system. They
 * are the parameters the personal-versus-population study (P12) and the camera
 * session tune; changing one changes [DeviationRules.RULE_VERSION].
 */
object DeviationPolicy {

    const val SPREAD_MULTIPLIER: Double = 2.0

    /** Range of motion: the band is at least this fraction of the user's median range. */
    const val RANGE_MIN_BAND_FRACTION: Double = 0.05

    /** Phase timing: at least this fraction of the median, which also covers a frame or two of quantisation. */
    const val TEMPO_MIN_BAND_FRACTION: Double = 0.10

    /** Left-minus-right: at least this many degrees. */
    const val ASYMMETRY_MIN_BAND_DEG: Double = 3.0

    /** Movement path: at least this fraction of that side's median range, in degrees of RMS difference. */
    const val PATH_MIN_BAND_FRACTION: Double = 0.05

    /** Fewest completed repetitions on a side before a within-set trend is read. */
    const val DRIFT_MIN_REPETITIONS: Int = 4
}

/**
 * Layer 1 - the deviation rules, P8.1-8.3 (FINAL_ARCHITECTURE section 2,
 * DECISIONS.md P3-23).
 *
 * Each rule compares one measured quantity with the same quantity in the user's
 * own calibration and, when it falls outside the user's usual band
 * ([DeviationPolicy]), says so as a [Deviation]: Level 2, by construction. No
 * rule says whether a movement was correct; "below your usual" is the whole of
 * the claim. The sentence is composed here, in the core - the UI writes none.
 *
 * | Rule | Compares | Metric |
 * |---|---|---|
 * | Range of motion | each side's range with that side's median | `rom_left`, `rom_right` |
 * | Tempo | each phase's time with the timing baseline the repetition belongs to | `descent_ms`, `ascent_ms` |
 * | Asymmetry | left minus right with the user's habitual offset | `rom_asymmetry` |
 * | Movement path | each side's resampled curve, RMS distance from the mean calibration curve | `path_left`, `path_right` |
 * | Within-set drift | each side's range-of-motion trend across a completed set | `rom_drift_left`, `rom_drift_right` |
 *
 * **Only completed repetitions are judged.** An aborted repetition is shallow by
 * definition; calling it "below your usual" would restate its validity as a
 * finding.
 *
 * **Sides stay sides.** A side that was not measured - the idle arm of an
 * alternating curl - is `NaN` and produces no finding. Asymmetry is read only
 * where both sides were measured in the same repetition and the baseline has a
 * paired offset; the difference is taken with the named reducer
 * `signedDifference()`.
 *
 * **Holds have no rules yet.** A hold baseline comes from one calibration hold,
 * so it has no spread to set a band from.
 *
 * **Post-rep only.** Called once per completed record, or once per completed
 * set for [drift]; never per frame.
 */
object DeviationRules {

    const val RULE_VERSION: String = "p8-l1-1"

    /** The per-repetition rules. Empty for a repetition that did not complete. */
    fun forRepetition(rep: RepRecord, baseline: RepetitionBaseline, comparison: RepetitionComparison): List<Deviation> {
        if (rep.validity != RepValidity.VALID) return emptyList()
        val out = ArrayList<Deviation>(4)
        for (side in Side.entries) range(side, comparison.rangeOfMotion(side))?.let(out::add)
        tempo("descent_ms", "Moving out", comparison.timingSide, comparison.descentMs)?.let(out::add)
        tempo("ascent_ms", "Coming back", comparison.timingSide, comparison.ascentMs)?.let(out::add)
        asymmetry(rep, baseline.asymmetry)?.let(out::add)
        for (side in Side.entries) path(side, rep, baseline)?.let(out::add)
        return out
    }

    /**
     * Within-set drift of range of motion, per side, over a completed set: the
     * least-squares change from the first to the last completed repetition on
     * that side, against that side's band.
     */
    fun drift(reps: List<RepRecord>, baseline: RepetitionBaseline): List<Deviation> {
        val out = ArrayList<Deviation>(2)
        for (side in Side.entries) {
            val values = reps.filter { it.validity == RepValidity.VALID }
                .map { it.rangeOfMotion.on(side) }
                .filter { it.isFinite() }
            if (values.size < DeviationPolicy.DRIFT_MIN_REPETITIONS) continue
            val usual = baseline.rangeOfMotion.on(side)
            if (!usual.isMeasured) continue
            val change = slope(values) * (values.size - 1)
            val band = band(usual.interquartileRange, DeviationPolicy.RANGE_MIN_BAND_FRACTION * abs(usual.median))
            if (!(abs(change) > band)) continue
            val direction = if (change < 0) DeviationDirection.BELOW_BASELINE else DeviationDirection.ABOVE_BASELINE
            val verb = if (change < 0) "fell" else "rose"
            out += Deviation(
                metric = "rom_drift_" + side.key,
                direction = direction,
                displayText = side.label + " range of motion " + verb + " by about " + deg(abs(change)) +
                    " across this set, more than your usual variation of " + deg(band) + ".",
                ruleVersion = RULE_VERSION,
            )
        }
        return out
    }

    private fun range(side: Side, value: BesideBaseline): Deviation? {
        val direction = outside(value, DeviationPolicy.RANGE_MIN_BAND_FRACTION) ?: return null
        return Deviation(
            metric = "rom_" + side.key,
            direction = direction,
            displayText = side.label + " range of motion " + deg(value.measured) + " is " +
                (if (direction == DeviationDirection.BELOW_BASELINE) "below" else "above") +
                " your usual " + deg(value.baseline) + " (" + percent(value) + ").",
            ruleVersion = RULE_VERSION,
        )
    }

    private fun tempo(metric: String, phase: String, side: Side?, value: BesideBaseline): Deviation? {
        val direction = outside(value, DeviationPolicy.TEMPO_MIN_BAND_FRACTION) ?: return null
        val whose = if (side == null) "" else " (" + side.label.lowercase() + ")"
        return Deviation(
            metric = metric,
            direction = direction,
            displayText = phase + whose + " took " + seconds(value.measured) + ", " +
                (if (direction == DeviationDirection.BELOW_BASELINE) "quicker" else "slower") +
                " than your usual " + seconds(value.baseline) + ".",
            ruleVersion = RULE_VERSION,
        )
    }

    private fun asymmetry(rep: RepRecord, baseline: AsymmetryBaseline): Deviation? {
        if (baseline !is AsymmetryBaseline.Paired || !baseline.leftMinusRight.isMeasured) return null
        val measured = rep.rangeOfMotion.signedDifference()
        if (!measured.isFinite()) return null
        val usual = baseline.leftMinusRight
        val half = band(usual.interquartileRange, DeviationPolicy.ASYMMETRY_MIN_BAND_DEG)
        val offset = measured - usual.median
        if (!(abs(offset) > half)) return null
        return Deviation(
            metric = "rom_asymmetry",
            direction = if (offset < 0) DeviationDirection.BELOW_BASELINE else DeviationDirection.ABOVE_BASELINE,
            displayText = "Left and right ranges differed by " + lopsided(measured) +
                "; your usual is " + lopsided(usual.median) + ".",
            ruleVersion = RULE_VERSION,
        )
    }

    private fun path(side: Side, rep: RepRecord, baseline: RepetitionBaseline): Deviation? {
        val spread = baseline.curveSpread.on(side)
        val range = baseline.rangeOfMotion.on(side)
        if (!spread.isMeasured || !range.isMeasured) return null
        val distance = rms(rep.resampled(side), baseline.meanCurve(side))
        if (!distance.isFinite()) return null
        val limit = spread.median + band(spread.interquartileRange, DeviationPolicy.PATH_MIN_BAND_FRACTION * abs(range.median))
        if (!(distance > limit)) return null
        return Deviation(
            metric = "path_" + side.key,
            direction = DeviationDirection.ABOVE_BASELINE,
            displayText = side.label + " movement path differed from your usual one by " + deg(distance) +
                " on average, more than your usual " + deg(spread.median) + ".",
            ruleVersion = RULE_VERSION,
        )
    }

    /** Which way [value] left the band around the baseline median, or `null` if it did not, or cannot be told. */
    private fun outside(value: BesideBaseline, minFraction: Double): DeviationDirection? {
        if (!value.measured.isFinite() || !value.baseline.isFinite() || !value.baselineSpread.isFinite()) return null
        val half = band(value.baselineSpread, minFraction * abs(value.baseline))
        val offset = value.measured - value.baseline
        return when {
            offset < -half -> DeviationDirection.BELOW_BASELINE
            offset > half -> DeviationDirection.ABOVE_BASELINE
            else -> null
        }
    }

    private fun band(spread: Double, floor: Double): Double = max(DeviationPolicy.SPREAD_MULTIPLIER * spread, floor)

    /** Root-mean-square difference over points both curves measured; `NaN` if any point is missing. */
    private fun rms(curve: DoubleArray, reference: DoubleArray): Double {
        if (curve.isEmpty() || curve.size != reference.size) return Double.NaN
        var sum = 0.0
        for (i in curve.indices) {
            val d = curve[i] - reference[i]
            if (!d.isFinite()) return Double.NaN
            sum += d * d
        }
        return sqrt(sum / curve.size)
    }

    /** Least-squares slope of [values] against their position. */
    private fun slope(values: List<Double>): Double {
        val n = values.size
        val meanX = (n - 1) / 2.0
        val meanY = values.sum() / n
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (i - meanX) * (values[i] - meanY)
            den += (i - meanX) * (i - meanX)
        }
        return num / den
    }

    private fun RepRecord.resampled(side: Side): DoubleArray = if (side == Side.LEFT) resampledLeft else resampledRight

    private val Side.key: String get() = if (this == Side.LEFT) "left" else "right"
    private val Side.label: String get() = if (this == Side.LEFT) "Left" else "Right"

    private fun deg(value: Double): String = value.roundToInt().toString() + "°"

    private fun seconds(ms: Double): String {
        val tenths = (ms / 100.0).roundToInt()
        return (tenths / 10).toString() + "." + (tenths % 10).toString() + " s"
    }

    private fun percent(value: BesideBaseline): String {
        val pct = ((value.measured / value.baseline - 1.0) * 100.0).roundToInt()
        return (if (pct > 0) "+" else "") + pct + "%"
    }

    private fun lopsided(leftMinusRight: Double): String {
        val size = abs(leftMinusRight).roundToInt()
        return when {
            size == 0 -> "0°"
            leftMinusRight > 0 -> size.toString() + "°, left larger"
            else -> size.toString() + "°, right larger"
        }
    }
}
