package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.calibration.timingSideOf
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.profile.TechniqueAuthority
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.max

/**
 * "Faster than the beginner tempo ACSM recommends" - the one cited population
 * threshold in Layer 1's reach. Level 3, Tier A only. DECISIONS.md P3-26, P3-30.
 *
 * **The source**, read in full (P3-26): the ACSM position stand recommends, for
 * novices, "a moderate contraction velocity (1-2 s CON; 1-2 s ECC)". So the message
 * says a repetition was faster than that tempo, and names its source. It never says
 * the repetition was wrong, unsafe or ineffective: the same source reports faster
 * training giving larger strength gains in untrained people.
 *
 * **Tier A only, by type.** [evaluate] demands a [TechniqueAuthority], which only a
 * `TierAProfile` can supply (CLAUDE.md section 8); a variant speaks under its
 * parent's.
 *
 * **The timer sees part of each phase** (P3-26's first problem). A record's phase
 * times run between the repetition machine's zone edges, not over the whole
 * movement, so they read short. The whole phase is estimated as
 * `measured / f`, `f` being the share of a movement's duration spent between the
 * two edges' positions on this repetition's own path from peak to trough, under a
 * minimum-jerk profile. Among the standard smooth profiles, minimum-jerk crosses the
 * middle fastest and so gives the longest estimate: the conservative choice for a
 * "too fast" message. Unchecked against hand timing on the phone, which P3-26 asks
 * for.
 *
 * **No accuracy figure** is attached to this rule, anywhere (CLAUDE.md section 12a).
 */
object AcsmTempoRule {

    const val RULE_VERSION: String = "p8-acsm-tempo-1"

    const val FAULT_ID: String = "faster_than_acsm_novice_tempo"

    const val SOURCE_REF: String =
        "Ratamess NA et al. American College of Sports Medicine position stand: Progression models in " +
            "resistance training for healthy adults. Med Sci Sports Exerc. 2009;41(3):687-708. " +
            "Novices: moderate contraction velocity, 1-2 s concentric, 1-2 s eccentric."

    /** ACSM's lower bound for a phase, milliseconds. */
    const val SHORTEST_RECOMMENDED_PHASE_MS: Double = 1_000.0

    fun evaluate(authority: TechniqueAuthority, profile: RepetitionExercise, rep: RepRecord): TechniqueFault? {
        require(authority.describeProfile() == CalibrationKey.of(profile)) {
            "${authority.describeProfile()}'s authority cannot judge ${profile.id}"
        }
        if (rep.validity != RepValidity.VALID) return null
        val features = RepFeatures.of(profile, rep)
        val lifting = estimatedPhaseMs(profile, rep, features.liftingMs)
        val lowering = estimatedPhaseMs(profile, rep, features.loweringMs)
        val fastLift = lifting < SHORTEST_RECOMMENDED_PHASE_MS
        val fastLower = lowering < SHORTEST_RECOMMENDED_PHASE_MS
        if (!fastLift && !fastLower) return null
        val what = when {
            fastLift && fastLower ->
                "lifting took about ${tenths(lifting / 1_000.0)} s and lowering about ${tenths(lowering / 1_000.0)} s"
            fastLift -> "lifting took about ${tenths(lifting / 1_000.0)} s"
            else -> "lowering took about ${tenths(lowering / 1_000.0)} s"
        }
        return TechniqueFault.from(
            authority = authority,
            faultId = FAULT_ID,
            displayText = "Faster than the beginner tempo the ACSM recommends: $what, " +
                "against 1–2 s for each phase (Ratamess et al., 2009).",
            ruleVersion = RULE_VERSION,
            sourceRef = SOURCE_REF,
        )
    }

    /**
     * The whole phase estimated from the part the timer saw, milliseconds, or `NaN`
     * when the repetition's path does not allow an estimate.
     *
     * Where both sides moved together the timing is the repetition's (P3-2), and each
     * side's path gives its own estimate. The longer is used: the more generous
     * reading for a "too fast" message. A named choice, never an average.
     */
    fun estimatedPhaseMs(profile: RepetitionExercise, rep: RepRecord, measuredMs: Double): Double {
        if (!measuredMs.isFinite()) return Double.NaN
        val side = timingSideOf(rep)
        if (side != null) return estimateOn(profile, rep, side, measuredMs)
        val left = estimateOn(profile, rep, Side.LEFT, measuredMs)
        val right = estimateOn(profile, rep, Side.RIGHT, measuredMs)
        return when {
            left.isNaN() -> right
            right.isNaN() -> left
            else -> max(left, right)
        }
    }

    /**
     * The share of a minimum-jerk movement's duration spent between two positions,
     * each given as a fraction of the way along the path (0 at the start, 1 at the
     * end).
     */
    fun minimumJerkShare(from: Double, to: Double): Double = minimumJerkTime(to) - minimumJerkTime(from)

    /**
     * The one path position's time on a minimum-jerk movement, both as fractions of
     * the whole: the inverse of `s(t) = 10t^3 - 15t^4 + 6t^5`, which rises
     * monotonically from 0 to 1. Found by bisection - a fixed number of halvings, the
     * same on every platform.
     *
     * Only the lower half is bisected; the upper half follows from the symmetry
     * `s(1 - t) = 1 - s(t)`. Near t = 1, s(t) rounds to exactly 1.0 over a stretch
     * of about 2^-18, so bisecting there directly stops short of the end.
     */
    private fun minimumJerkTime(position: Double): Double {
        val target = position.coerceIn(0.0, 1.0)
        if (target > 0.5) return 1.0 - minimumJerkTime(1.0 - target)
        var low = 0.0
        var high = 0.5
        repeat(BISECTIONS) {
            val t = (low + high) / 2.0
            val s = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
            if (s < target) low = t else high = t
        }
        return (low + high) / 2.0
    }

    private fun estimateOn(profile: RepetitionExercise, rep: RepRecord, side: Side, measuredMs: Double): Double {
        val peak = rep.peakValue.on(side)
        val trough = rep.troughValue.on(side)
        if (!peak.isFinite() || !trough.isFinite() || peak == trough) return Double.NaN
        val spec = profile.repSignal
        val startEdge = if (spec.topIsGreater) spec.topThreshold - spec.hysteresis else spec.topThreshold + spec.hysteresis
        val farEdge = if (spec.topIsGreater) spec.bottomThreshold + spec.hysteresis else spec.bottomThreshold - spec.hysteresis
        val from = (peak - startEdge) / (peak - trough)
        val to = (peak - farEdge) / (peak - trough)
        // The timer ran from one edge to the other only if the path crossed both.
        if (!(from >= 0.0 && to <= 1.0 && from < to)) return Double.NaN
        val share = minimumJerkShare(from, to)
        return if (share > 0.0) measuredMs / share else Double.NaN
    }

    private const val BISECTIONS: Int = 60
}
