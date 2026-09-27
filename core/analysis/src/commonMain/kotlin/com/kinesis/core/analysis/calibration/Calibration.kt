package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.BilateralSummary
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepTiming
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import com.kinesis.core.domain.calibration.TimingBaseline
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity

/** What fitting a baseline produced: a baseline, or the honest reason there is none. */
sealed interface CalibrationOutcome<out B : CalibrationBaseline> {

    class Fitted<out B : CalibrationBaseline>(val baseline: B) : CalibrationOutcome<B>

    /**
     * Too few valid samples on every side. A baseline from three repetitions is
     * not reported as if it were established - CLAUDE.md section 10's "block
     * rather than produce confident nonsense", applied to calibration.
     */
    class Insufficient(
        val recordsOffered: Int,
        /** The most valid samples any one side - or the hold - had. */
        val bestSideSamples: Int,
        val minimumSamples: Int,
    ) : CalibrationOutcome<Nothing> {
        override fun toString(): String =
            "Insufficient(" + bestSideSamples + " of " + minimumSamples + " needed, " + recordsOffered + " offered)"
    }
}

/**
 * Fits a user's personal baseline from their own calibration records -
 * DECISIONS.md P3-21, ZERO_DATASET_BUILD_PLAN "Calibration is not training data".
 *
 * On-device, unsupervised, from that person's own repetitions, in the moment.
 * Nothing is labelled, nothing is retained as a dataset. Post-rep path: this
 * runs once over a finished calibration set, never per frame.
 *
 * ### What goes in
 *
 * **Valid records only.** An aborted, incomplete or half-seen repetition is not
 * the user's normal; it is a repetition that did not happen as intended, or one
 * the camera did not see. Letting it in drags the normal toward the failure.
 *
 * **Each side from its own values.** Left and right are summarised separately
 * (CLAUDE.md section 3), and a side that did not move - the idle arm of a curl -
 * contributes nothing rather than zeroes.
 *
 * ### Studies 2 and 3
 *
 * The fit is order-independent and takes whatever records it is given, so Study
 * 3's "first N repetitions for N from 3 to 20" is `reps.take(n)` and Study 2's
 * "first 8" is `reps.take(8)`, each fitted with `minimumSamples` lowered as the
 * study needs.
 */
object Calibration {

    /**
     * The fitting method. Bump it when anything here changes what a baseline
     * would contain, so stored baselines say which method made them.
     */
    const val METHOD_VERSION: String = "calibration-median-iqr-1"

    /**
     * The fewest valid repetitions per side before a baseline is established.
     *
     * **Provisional.** ZERO_DATASET_BUILD_PLAN describes calibration as "5-10
     * reps"; five is the bottom of that range. Study 3 exists to replace this
     * number with a measured one, and until it has, nothing claims five is
     * enough.
     */
    const val MIN_REPETITIONS: Int = 5

    /**
     * The fewest valid holds before a hold baseline is established.
     *
     * **Provisional**, and lower than [MIN_REPETITIONS] for a practical reason
     * rather than a statistical one: a hold lasts tens of seconds, and five
     * planks is a workout, not a calibration. No document or study sets it.
     */
    const val MIN_HOLDS: Int = 3

    /**
     * The id a profile is calibrated under: its own, or its parent's for a
     * variant. CONTRACTS.md section 3 - variants share their parent's
     * calibration.
     */
    fun calibrationId(profile: ExerciseProfile): String =
        if (profile is ExerciseVariant) profile.parent.id else profile.id

    fun fitRepetitions(
        profile: RepetitionExercise,
        reps: List<RepRecord>,
        minimumSamples: Int = MIN_REPETITIONS,
    ): CalibrationOutcome<RepetitionBaseline> {
        require(minimumSamples >= 1) { "minimumSamples must be at least 1, got " + minimumSamples }
        val valid = reps.filter { it.validity == RepValidity.VALID }

        val rangeOfMotion = BilateralSummary(
            left = RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].rangeOfMotion.left }),
            right = RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].rangeOfMotion.right }),
        )
        val best = if (rangeOfMotion.left.n >= rangeOfMotion.right.n) rangeOfMotion.left.n else rangeOfMotion.right.n
        if (best < minimumSamples) {
            return CalibrationOutcome.Insufficient(reps.size, best, minimumSamples)
        }

        val perLimb = profile.repetitionSemantics == RepetitionSemantics.PER_LIMB
        val timing = if (perLimb) {
            TimingBaseline.PerSide(
                left = timingOf(valid.filter { it.attributedSide == Side.LEFT }),
                right = timingOf(valid.filter { it.attributedSide == Side.RIGHT }),
            )
        } else {
            TimingBaseline.Together(timingOf(valid))
        }

        // Named reducer: left minus right, direction kept. A per-limb exercise's
        // sides never share a repetition, so it has no offset to fit.
        val offset = if (perLimb) {
            RobustSummary.ABSENT
        } else {
            RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].rangeOfMotion.signedDifference() })
        }

        return CalibrationOutcome.Fitted(
            RepetitionBaseline(
                exerciseId = calibrationId(profile),
                methodVersion = METHOD_VERSION,
                minimumSamples = minimumSamples,
                recordsOffered = reps.size,
                recordsUsed = valid.size,
                rangeOfMotion = rangeOfMotion,
                timing = timing,
                habitualOffset = offset,
            ),
        )
    }

    fun fitHolds(
        profile: IsometricExercise,
        holds: List<HoldRecord>,
        minimumSamples: Int = MIN_HOLDS,
    ): CalibrationOutcome<HoldBaseline> {
        require(minimumSamples >= 1) { "minimumSamples must be at least 1, got " + minimumSamples }
        val valid = holds.filter { it.validity == HoldValidity.VALID }
        if (valid.size < minimumSamples) {
            return CalibrationOutcome.Insufficient(holds.size, valid.size, minimumSamples)
        }
        return CalibrationOutcome.Fitted(
            HoldBaseline(
                exerciseId = calibrationId(profile),
                methodVersion = METHOD_VERSION,
                minimumSamples = minimumSamples,
                recordsOffered = holds.size,
                recordsUsed = valid.size,
                totalHoldMs = RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].totalHoldMs }),
                longestUnbrokenMs = RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].longestUnbrokenMs }),
                meanAbsDeviation = RobustStatistics.summarize(DoubleArray(valid.size) { valid[it].meanAbsDeviation }),
            ),
        )
    }

    private fun timingOf(reps: List<RepRecord>): RepTiming = RepTiming(
        descentMs = RobustStatistics.summarize(DoubleArray(reps.size) { reps[it].descentMs }),
        ascentMs = RobustStatistics.summarize(DoubleArray(reps.size) { reps[it].ascentMs }),
        durationMs = RobustStatistics.summarize(DoubleArray(reps.size) { reps[it].durationMs }),
        tempoRatio = RobustStatistics.summarize(DoubleArray(reps.size) { reps[it].tempoRatio }),
    )
}
