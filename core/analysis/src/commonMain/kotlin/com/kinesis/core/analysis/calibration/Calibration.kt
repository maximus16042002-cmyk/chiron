package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.BilateralStatistic
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SetRecord
import kotlin.math.sqrt

/**
 * Which baseline an exercise uses. A variant uses its parent's: CONTRACTS.md
 * section 3, "Variants inherit their parent's profile, calibration and claim
 * ceiling". DECISIONS.md P3-6 records the assumption this carries - a goblet
 * squat is measured against unloaded squats - as one to revisit if the two
 * turn out to differ.
 */
object CalibrationKey {
    fun of(profile: ExerciseProfile): String = if (profile is ExerciseVariant) profile.parent.id else profile.id
}

/** What a calibration set produced: a baseline, or the plain reason there is none. */
sealed interface CalibrationOutcome {

    /** What the user is told, composed here and never in the UI. */
    val displayText: String

    class Calibrated(val baseline: CalibrationBaseline, override val displayText: String) : CalibrationOutcome

    /** Too few valid repetitions on a side that needs them. */
    class TooFewRepetitions(
        val validLeft: Int,
        val validRight: Int,
        val requiredPerSide: Int,
        /** True for a near-side-only profile, which needs only the side it can see. */
        val oneSideSuffices: Boolean,
        override val displayText: String,
    ) : CalibrationOutcome

    /** The calibration hold cannot stand as a baseline. */
    class HoldNotUsable(
        val validity: HoldValidity?,
        val heldMs: Double,
        val requiredMs: Double,
        override val displayText: String,
    ) : CalibrationOutcome
}

/**
 * Builds a [CalibrationBaseline] from a calibration set - DECISIONS.md P3-22.
 *
 * **Post-rep path.** Runs once, after the calibration set has ended, over its
 * completed records. Nothing here is reachable from a frame.
 *
 * ### The rules
 *
 * - **Only valid repetitions count.** An aborted, incomplete or half-seen
 *   repetition is not the user's normal movement, or was not seen well enough to
 *   say what it was.
 * - **Per side, never averaged.** A repetition contributes to a side exactly
 *   when it measured that side. That one rule covers every shape in the library
 *   without asking which it is: a squat's repetitions measure both sides, an
 *   alternating curl's measure one arm each, and a near-side-only row's measure
 *   the side facing the camera.
 * - **The first [CalibrationPolicy.maxValidRepsPerSide] on each side**, in set
 *   order, and at least [CalibrationPolicy.minValidRepsPerSide] on every side
 *   the profile measures. A near-side-only profile needs them on one side.
 * - Too few means **no baseline**, and the reason is said in numbers.
 */
object Calibration {

    /**
     * Dispatches on the set's kind. The set must be of [profile] itself: a
     * variant's set calibrates through the variant, whose key is its parent's.
     * Of a hold set's holds, the first valid one is used, else the first - the
     * live screen records one per set.
     */
    fun fromSet(
        profile: ExerciseProfile,
        set: SetRecord,
        createdAtEpochMs: Double,
        sourceSessionId: String?,
        policy: CalibrationPolicy = CalibrationPolicy.PROVISIONAL,
    ): CalibrationOutcome {
        require(set.exerciseId == profile.id) {
            "a ${set.exerciseId} set cannot calibrate ${profile.id}"
        }
        return when (set) {
            is RepetitionSetRecord -> {
                require(profile is RepetitionExercise) { "${profile.id} is not a repetition exercise" }
                fromRepetitions(profile, set.reps, createdAtEpochMs, sourceSessionId, policy)
            }
            is HoldSetRecord -> {
                require(profile is IsometricExercise) { "${profile.id} is not a hold" }
                val hold = set.holds.firstOrNull { it.validity == HoldValidity.VALID } ?: set.holds.firstOrNull()
                fromHold(profile, hold, createdAtEpochMs, sourceSessionId, policy)
            }
        }
    }

    fun fromRepetitions(
        profile: RepetitionExercise,
        reps: List<RepRecord>,
        createdAtEpochMs: Double,
        sourceSessionId: String?,
        policy: CalibrationPolicy = CalibrationPolicy.PROVISIONAL,
    ): CalibrationOutcome {
        val valid = reps.filter { it.validity == RepValidity.VALID }.sortedBy { it.repIndex }
        val validLeft = valid.filter { it.rangeOfMotion.left.isFinite() }
        val validRight = valid.filter { it.rangeOfMotion.right.isFinite() }

        // A side with fewer than the minimum has no baseline at all, rather than
        // a weak one: two repetitions are not a normal.
        val left = validLeft.take(policy.maxValidRepsPerSide).takeIf { it.size >= policy.minValidRepsPerSide }.orEmpty()
        val right = validRight.take(policy.maxValidRepsPerSide).takeIf { it.size >= policy.minValidRepsPerSide }.orEmpty()

        val oneSideSuffices = profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY
        val enough = if (oneSideSuffices) left.isNotEmpty() || right.isNotEmpty() else left.isNotEmpty() && right.isNotEmpty()
        if (!enough) {
            return CalibrationOutcome.TooFewRepetitions(
                validLeft = validLeft.size,
                validRight = validRight.size,
                requiredPerSide = policy.minValidRepsPerSide,
                oneSideSuffices = oneSideSuffices,
                displayText = tooFewText(validLeft.size, validRight.size, policy.minValidRepsPerSide, oneSideSuffices),
            )
        }

        // Every repetition used on either side, once, in set order.
        val used = (left + right).distinct().sortedBy { it.repIndex }
        val curveLength = used.first().resampledLeft.size

        val rangeOfMotion = perSide(left, right) { rep, side -> rep.rangeOfMotion.on(side) }
        val meanLeft = meanCurve(left, Side.LEFT, curveLength)
        val meanRight = meanCurve(right, Side.RIGHT, curveLength)

        val baseline = RepetitionBaseline(
            calibrationId = CalibrationKey.of(profile),
            estimatorVersion = policy.version,
            createdAtEpochMs = createdAtEpochMs,
            sourceSessionId = sourceSessionId,
            rangeOfMotion = rangeOfMotion,
            peakValue = perSide(left, right) { rep, side -> rep.peakValue.on(side) },
            troughValue = perSide(left, right) { rep, side -> rep.troughValue.on(side) },
            descentMs = timing(used) { it.descentMs },
            ascentMs = timing(used) { it.ascentMs },
            asymmetry = asymmetry(used, rangeOfMotion, oneSideSuffices),
            meanCurveLeft = meanLeft,
            meanCurveRight = meanRight,
            curveSpread = BilateralStatistic(
                left = curveSpread(left, Side.LEFT, meanLeft),
                right = curveSpread(right, Side.RIGHT, meanRight),
            ),
        )
        return CalibrationOutcome.Calibrated(baseline, calibratedText(left.size, right.size, oneSideSuffices))
    }

    fun fromHold(
        profile: IsometricExercise,
        hold: HoldRecord?,
        createdAtEpochMs: Double,
        sourceSessionId: String?,
        policy: CalibrationPolicy = CalibrationPolicy.PROVISIONAL,
    ): CalibrationOutcome {
        val required = "Calibration needs one valid hold of at least ${seconds(policy.minHoldMs)} seconds"
        fun notUsable(why: String) = CalibrationOutcome.HoldNotUsable(
            validity = hold?.validity,
            heldMs = hold?.totalHoldMs ?: 0.0,
            requiredMs = policy.minHoldMs,
            displayText = "No baseline yet. $required; $why.",
        )

        if (hold == null) return notUsable("this set had none")
        when (hold.validity) {
            HoldValidity.VALID -> Unit
            HoldValidity.TOO_SHORT -> return notUsable("this hold was too short to count as one")
            HoldValidity.INSUFFICIENT_DATA -> return notUsable("too little of this hold was visible to measure")
            HoldValidity.BROKEN -> return notUsable("the position was lost and not regained")
        }
        if (hold.totalHoldMs < policy.minHoldMs) {
            return notUsable("this one was held for ${seconds(hold.totalHoldMs)} seconds")
        }
        // A hold recorded before steadiness was measured (P3-22) cannot supply it.
        if (!hold.meanHeldValue.isFinite() || !hold.heldValueSd.isFinite()) {
            return notUsable("its position and steadiness were not measured")
        }

        val baseline = HoldBaseline(
            calibrationId = CalibrationKey.of(profile),
            estimatorVersion = policy.version,
            createdAtEpochMs = createdAtEpochMs,
            sourceSessionId = sourceSessionId,
            targetValue = profile.holdSignal.targetValue,
            meanHeldValue = hold.meanHeldValue,
            meanAbsDeviation = hold.meanAbsDeviation,
            sway = hold.heldValueSd,
            heldMs = hold.totalHoldMs,
            breakCount = hold.breakCount,
            coverage = hold.coverage,
        )
        return CalibrationOutcome.Calibrated(
            baseline,
            "Baseline from one ${seconds(hold.totalHoldMs)}-second hold.",
        )
    }

    // --- Statistics per side ----------------------------------------------------------

    private inline fun perSide(
        left: List<RepRecord>,
        right: List<RepRecord>,
        value: (RepRecord, Side) -> Double,
    ): BilateralStatistic = BilateralStatistic(
        left = RobustStatistics.of(DoubleArray(left.size) { value(left[it], Side.LEFT) }),
        right = RobustStatistics.of(DoubleArray(right.size) { value(right[it], Side.RIGHT) }),
    )

    /**
     * One phase's timing, filed where the record attributes it: under the side a
     * one-sided repetition belongs to, or as the repetition's own when both sides
     * moved together. See `PhaseTimingBaseline`.
     */
    private inline fun timing(used: List<RepRecord>, phase: (RepRecord) -> Double): PhaseTimingBaseline {
        val leftTimes = ArrayList<Double>()
        val rightTimes = ArrayList<Double>()
        val repetitionTimes = ArrayList<Double>()
        for (rep in used) {
            when (timingSideOf(rep)) {
                Side.LEFT -> leftTimes += phase(rep)
                Side.RIGHT -> rightTimes += phase(rep)
                null -> repetitionTimes += phase(rep)
            }
        }
        return PhaseTimingBaseline(
            perSide = BilateralStatistic(RobustStatistics.of(leftTimes), RobustStatistics.of(rightTimes)),
            perRepetition = RobustStatistics.of(repetitionTimes),
        )
    }

    private fun asymmetry(
        used: List<RepRecord>,
        rangeOfMotion: BilateralStatistic,
        oneSideSuffices: Boolean,
    ): AsymmetryBaseline {
        if (oneSideSuffices || !rangeOfMotion.left.isMeasured || !rangeOfMotion.right.isMeasured) {
            return AsymmetryBaseline.NotMeasurable
        }
        val paired = used.filter { timingSideOf(it) == null }
        if (paired.isNotEmpty()) {
            return AsymmetryBaseline.Paired(RobustStatistics.of(paired.map { it.rangeOfMotion.signedDifference() }))
        }
        return AsymmetryBaseline.BetweenSideMedians(rangeOfMotion.medians.signedDifference())
    }

    /** The pointwise mean of one side's curves, skipping points a repetition did not measure. */
    private fun meanCurve(reps: List<RepRecord>, side: Side, length: Int): DoubleArray {
        val mean = DoubleArray(length) { Double.NaN }
        for (point in 0 until length) {
            var sum = 0.0
            var count = 0
            for (rep in reps) {
                val curve = curveOf(rep, side)
                require(curve.size == length) { "repetition ${rep.repIndex} has ${curve.size} points, expected $length" }
                val value = curve[point]
                if (value.isFinite()) {
                    sum += value
                    count++
                }
            }
            if (count > 0) mean[point] = sum / count
        }
        return mean
    }

    /** Each repetition's root-mean-square distance from the mean curve, summarised. */
    private fun curveSpread(reps: List<RepRecord>, side: Side, mean: DoubleArray): RobustStatistic {
        val distances = DoubleArray(reps.size) { index ->
            val curve = curveOf(reps[index], side)
            var sumOfSquares = 0.0
            var count = 0
            for (point in mean.indices) {
                val difference = curve[point] - mean[point]
                if (difference.isFinite()) {
                    sumOfSquares += difference * difference
                    count++
                }
            }
            if (count == 0) Double.NaN else sqrt(sumOfSquares / count)
        }
        return RobustStatistics.of(distances)
    }

    private fun curveOf(rep: RepRecord, side: Side): DoubleArray =
        if (side == Side.LEFT) rep.resampledLeft else rep.resampledRight

    // --- Words ------------------------------------------------------------------------

    private fun tooFewText(validLeft: Int, validRight: Int, required: Int, oneSideSuffices: Boolean): String {
        val need = if (oneSideSuffices) {
            "at least $required valid repetitions on the side nearer the camera; this set had " +
                (if (validLeft >= validRight) validLeft else validRight)
        } else {
            "at least $required valid repetitions on each side; this set had $validLeft on the left and " +
                "$validRight on the right"
        }
        return "No baseline yet. Calibration needs $need. Only repetitions counted as valid are used."
    }

    private fun calibratedText(usedLeft: Int, usedRight: Int, oneSideSuffices: Boolean): String = when {
        oneSideSuffices && usedLeft == 0 -> "Baseline from $usedRight valid repetitions on the right, the side nearer the camera."
        oneSideSuffices && usedRight == 0 -> "Baseline from $usedLeft valid repetitions on the left, the side nearer the camera."
        usedLeft == usedRight -> "Baseline from $usedLeft valid repetitions on each side."
        else -> "Baseline from $usedLeft valid repetitions on the left and $usedRight on the right."
    }

    private fun seconds(ms: Double): Int = (ms / 1000.0).toInt()
}

/**
 * The side a repetition's single descent and ascent time belongs to: the one
 * side it measured, or `null` when it measured both - then the timing is the
 * repetition's, taken by the tracker from the side seen longer (P3-2).
 */
internal fun timingSideOf(rep: RepRecord): Side? {
    val leftMeasured = rep.rangeOfMotion.left.isFinite()
    val rightMeasured = rep.rangeOfMotion.right.isFinite()
    return when {
        leftMeasured && !rightMeasured -> Side.LEFT
        rightMeasured && !leftMeasured -> Side.RIGHT
        else -> null
    }
}
