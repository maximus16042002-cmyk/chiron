package com.kinesis.core.analysis.speed

import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity

/**
 * One repetition's lifting speed - DECISIONS.md P3-59.
 *
 * A Level 1 measurement and nothing more: no zone, no advice to stop, no judgement of the two
 * sides' difference. P3-54 attached zones and a 30% "cutoff" taken from barbell research in m/s
 * under load; nothing here has tested whether they carry over to a joint angle from a phone
 * camera, so P3-59 took them out.
 */
data class RepetitionSpeed(
    val repIndex: Int,
    /** Each side's range of motion over the lifting phase's time, degrees per second. */
    val speed: BilateralSignal,
    /** The fastest speed of a counted repetition so far in the set, per side. */
    val fastestSoFar: BilateralSignal,
    /** How much slower than [fastestSoFar] this repetition was, in percent, per side. */
    val slowdownPercent: BilateralSignal,
)

/** A set's lifting speeds, per side. A side that was never measured is `NaN` throughout. */
data class SetSpeedSummary(
    val repetitions: List<RepetitionSpeed>,
    val fastest: BilateralSignal,
    /** Each side's speed on its last counted, measured repetition. */
    val last: BilateralSignal,
    val largestSlowdownPercent: BilateralSignal,
)

/**
 * Lifting speed per side - DECISIONS.md P3-59.
 *
 * **Post-rep path.** It reads completed records, once per set summary.
 *
 * The lifting phase is chosen per profile by the rule `RepFeatures` uses: a profile that lifts
 * on the way away from its start (a curl) lifts during `descentMs`, one that lowers first (a
 * squat) during `ascentMs`. Both sides share the repetition's phase time; each divides its own
 * range by it, and the two are never combined - CLAUDE.md section 3.
 *
 * Only repetitions that counted have a speed. A twitch smaller than
 * [MIN_RANGE_FOR_FASTEST_DEG] can count without becoming the speed every later repetition is
 * measured against.
 */
object LiftingSpeed {

    const val MIN_RANGE_FOR_FASTEST_DEG: Double = 20.0

    fun liftingPhaseMs(profile: RepetitionExercise, rep: RepRecord): Double =
        if (profile.awayFromStart == Contraction.CONCENTRIC) rep.descentMs else rep.ascentMs

    fun of(profile: RepetitionExercise, rep: RepRecord): BilateralSignal {
        val phaseMs = liftingPhaseMs(profile, rep)
        if (rep.validity != RepValidity.VALID || !phaseMs.isFinite() || phaseMs <= 0.0) return UNMEASURED
        val seconds = phaseMs / 1_000.0
        return BilateralSignal(rep.rangeOfMotion.left / seconds, rep.rangeOfMotion.right / seconds)
    }

    fun summarise(profile: RepetitionExercise, reps: List<RepRecord>): SetSpeedSummary {
        val left = SideTally()
        val right = SideTally()
        val repetitions = ArrayList<RepetitionSpeed>(reps.size)
        for (rep in reps) {
            val speed = of(profile, rep)
            left.offer(speed.left, rep.rangeOfMotion.left)
            right.offer(speed.right, rep.rangeOfMotion.right)
            repetitions += RepetitionSpeed(
                repIndex = rep.repIndex,
                speed = speed,
                fastestSoFar = BilateralSignal(left.fastest, right.fastest),
                slowdownPercent = BilateralSignal(left.slowdown, right.slowdown),
            )
        }
        return SetSpeedSummary(
            repetitions = repetitions,
            fastest = BilateralSignal(left.fastest, right.fastest),
            last = BilateralSignal(left.last, right.last),
            largestSlowdownPercent = BilateralSignal(left.largestSlowdown, right.largestSlowdown),
        )
    }

    /** One side's running figures. `NaN` until the side is first measured. */
    private class SideTally {
        var fastest = Double.NaN
        var last = Double.NaN
        var slowdown = Double.NaN
        var largestSlowdown = Double.NaN

        fun offer(speed: Double, rangeDeg: Double) {
            if (!speed.isFinite()) {
                slowdown = Double.NaN
                return
            }
            last = speed
            if (rangeDeg >= MIN_RANGE_FOR_FASTEST_DEG && !(speed <= fastest)) fastest = speed
            slowdown = if (fastest.isFinite() && fastest > 0.0) maxOf(0.0, (fastest - speed) / fastest * 100.0) else Double.NaN
            if (slowdown.isFinite() && !(slowdown <= largestSlowdown)) largestSlowdown = slowdown
        }
    }

    private val UNMEASURED = BilateralSignal(Double.NaN, Double.NaN)
}
