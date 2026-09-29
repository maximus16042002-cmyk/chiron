package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.calibration.CurveDistance
import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One repetition's personal deviations, in baseline-SD units - the numbers behind
 * Layer 1's findings (DECISIONS.md P3-30). `NaN` where there is nothing to compare:
 * a side not measured, a baseline side not calibrated, no paired asymmetry.
 */
class PersonalDeviations(
    val rangeOfMotion: BilateralSignal,
    val lifting: Double,
    val lowering: Double,
    val asymmetry: Double,
    /** How far each side's curve lay from the user's usual curve, against its usual distance. One-sided. */
    val consistency: BilateralSignal,
)

/**
 * Layer 1: the personal deviation rules - CLAUDE.md section 12a, DECISIONS.md P3-30.
 *
 * Level 2 only. Every finding is a [Deviation] against the user's own baseline,
 * worded as personal ("shallower than your usual"), never as correct or incorrect.
 * Without a baseline there is nothing to deviate from, and nothing is said.
 *
 * Only a VALID repetition is judged. One that did not count is already shown as
 * not counting (amber, P3-23), and its shallowness is the reason, not news.
 *
 * **One message per side for depth.** The baseline's curve distance is measured on
 * raw curves (P3-22's `curveSpread`), so it includes depth: a shallower repetition
 * is also further from the usual curve. Where range of motion has spoken for a side,
 * consistency stays quiet there; [deviations] still reports both numbers.
 *
 * Post-rep path: once per completed repetition.
 */
object DeviationRules {

    fun deviations(
        profile: RepetitionExercise,
        rep: RepRecord,
        baseline: RepetitionBaseline,
        policy: DeviationPolicy = DeviationPolicy.PROVISIONAL,
    ): PersonalDeviations {
        require(baseline.calibrationId == CalibrationKey.of(profile)) {
            "${profile.id} uses the ${CalibrationKey.of(profile)} baseline, not ${baseline.calibrationId}"
        }
        val features = RepFeatures.of(profile, rep)
        val asymmetry = when (val habitual = baseline.asymmetry) {
            is AsymmetryBaseline.Paired ->
                PersonalReference.of(habitual.leftMinusRight).scaled(features.leftMinusRight, policy.asymmetryFloorDeg)
            is AsymmetryBaseline.BetweenSideMedians, AsymmetryBaseline.NotMeasurable -> Double.NaN
        }
        return PersonalDeviations(
            rangeOfMotion = BilateralSignal(
                left = rangeOfMotion(features, baseline, Side.LEFT, policy),
                right = rangeOfMotion(features, baseline, Side.RIGHT, policy),
            ),
            lifting = PersonalReference.of(phase(profile, baseline, Contraction.CONCENTRIC, features.timingSide))
                .scaled(features.liftingMs, policy.timeFloorMs),
            lowering = PersonalReference.of(phase(profile, baseline, Contraction.ECCENTRIC, features.timingSide))
                .scaled(features.loweringMs, policy.timeFloorMs),
            asymmetry = asymmetry,
            consistency = BilateralSignal(
                left = consistency(rep, baseline, Side.LEFT, policy),
                right = consistency(rep, baseline, Side.RIGHT, policy),
            ),
        )
    }

    fun evaluate(
        profile: RepetitionExercise,
        rep: RepRecord,
        baseline: RepetitionBaseline,
        policy: DeviationPolicy = DeviationPolicy.PROVISIONAL,
    ): List<Deviation> {
        if (rep.validity != RepValidity.VALID) return emptyList()
        val scaled = deviations(profile, rep, baseline, policy)
        val features = RepFeatures.of(profile, rep)
        val found = ArrayList<Deviation>()
        fun beyond(z: Double) = abs(z) > policy.threshold

        for (side in Side.entries) {
            val z = scaled.rangeOfMotion.on(side)
            if (!beyond(z)) continue
            val words = if (z < 0) "Shallower" else "Further"
            found += Deviation(
                metric = "rom_" + side.name.lowercase(),
                direction = direction(z),
                displayText = "$words than your usual on the ${sideName(side)}: " +
                    "${degrees(features.rangeOfMotion.on(side))} against your usual " +
                    "${degrees(baseline.rangeOfMotion.on(side).median)}.",
                ruleVersion = policy.version,
            )
        }

        for (contraction in Contraction.entries) {
            val z = if (contraction == Contraction.CONCENTRIC) scaled.lifting else scaled.lowering
            if (!beyond(z)) continue
            val phaseName = if (contraction == Contraction.CONCENTRIC) "lifting" else "lowering"
            val measured = if (contraction == Contraction.CONCENTRIC) features.liftingMs else features.loweringMs
            val usual = phase(profile, baseline, contraction, features.timingSide).median
            val where = features.timingSide?.let { " on the " + sideName(it) } ?: ""
            found += Deviation(
                metric = phaseName + "_time",
                direction = direction(z),
                displayText = "${if (z < 0) "Faster" else "Slower"} than your usual $phaseName$where: " +
                    "${seconds(measured)} against your usual ${seconds(usual)}.",
                ruleVersion = policy.version,
            )
        }

        if (beyond(scaled.asymmetry)) {
            val usual = (baseline.asymmetry as AsymmetryBaseline.Paired).leftMinusRight.median
            found += Deviation(
                metric = "asymmetry_lr",
                direction = direction(scaled.asymmetry),
                displayText = "Left and right differed from your usual balance: left minus right was " +
                    "${degrees(features.leftMinusRight)}, against your usual ${degrees(usual)}.",
                ruleVersion = policy.version,
            )
        }

        for (side in Side.entries) {
            val z = scaled.consistency.on(side)
            if (!(z > policy.threshold) || beyond(scaled.rangeOfMotion.on(side))) continue
            val distance = CurveDistance.rms(curveOf(rep, side), baseline.meanCurve(side))
            found += Deviation(
                metric = "shape_" + side.name.lowercase(),
                direction = DeviationDirection.ABOVE_BASELINE,
                displayText = "Moved differently from your usual on the ${sideName(side)}: " +
                    "${tenths(distance)}° from your usual path, against a usual ${tenths(baseline.curveSpread.on(side).median)}°.",
                ruleVersion = policy.version,
            )
        }
        return found
    }

    // --- Features against the baseline ---------------------------------------------------

    private fun rangeOfMotion(features: RepFeatures, baseline: RepetitionBaseline, side: Side, policy: DeviationPolicy) =
        PersonalReference.of(baseline.rangeOfMotion.on(side)).scaled(features.rangeOfMotion.on(side), policy.rangeOfMotionFloorDeg)

    /**
     * The baseline of one phase, attributed as the repetition's own timing is: the
     * side's for a one-sided repetition, the repetition's when both sides moved.
     */
    private fun phase(profile: RepetitionExercise, baseline: RepetitionBaseline, contraction: Contraction, timingSide: Side?): RobustStatistic {
        val timing: PhaseTimingBaseline = if (contraction == profile.awayFromStart) baseline.descentMs else baseline.ascentMs
        return if (timingSide == null) timing.perRepetition else timing.perSide.on(timingSide)
    }

    private fun consistency(rep: RepRecord, baseline: RepetitionBaseline, side: Side, policy: DeviationPolicy): Double {
        if (!rep.rangeOfMotion.on(side).isFinite()) return Double.NaN
        val distance = CurveDistance.rms(curveOf(rep, side), baseline.meanCurve(side))
        return PersonalReference.of(baseline.curveSpread.on(side)).scaled(distance, policy.consistencyFloorDeg)
    }

    private fun curveOf(rep: RepRecord, side: Side): DoubleArray =
        if (side == Side.LEFT) rep.resampledLeft else rep.resampledRight

    // --- Words -----------------------------------------------------------------------------
    //
    // Numbers are written from integers only: a Double's own toString differs between
    // the JVM and JavaScript, and the parity suite compares these sentences.

    private fun direction(z: Double) = if (z < 0) DeviationDirection.BELOW_BASELINE else DeviationDirection.ABOVE_BASELINE

    private fun sideName(side: Side) = if (side == Side.LEFT) "left" else "right"

    private fun degrees(value: Double): String = value.roundToInt().toString() + "°"

    private fun seconds(ms: Double): String = tenths(ms / 1_000.0) + " s"
}

/** A non-negative-or-negative value to one decimal place, written from integers. */
internal fun tenths(value: Double): String {
    val scaled = (abs(value) * 10.0).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return sign + (scaled / 10).toString() + "." + (scaled % 10).toString()
}
