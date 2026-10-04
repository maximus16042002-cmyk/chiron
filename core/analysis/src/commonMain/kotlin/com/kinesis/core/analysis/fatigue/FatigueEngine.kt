package com.kinesis.core.analysis.fatigue

import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.fatigue.FatigueZone
import com.kinesis.core.domain.fatigue.RepetitionFatigue
import com.kinesis.core.domain.fatigue.SetFatigueSummary
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs
import kotlin.math.max

/**
 * Velocity-Based Training (VBT) Fatigue Engine (P10).
 *
 * Measures concentric angular velocity loss across repetitions in a set, detecting
 * neuromuscular fatigue onset and recommending set termination before form breakdown occurs
 * (grounded in González-Badillo & Sánchez-Medina 2010, Pareja-Blanco et al. 2017).
 *
 * Bilateral signals are never averaged: velocity and fatigue are tracked independently
 * for left and right sides. Runs on the O(1) post-rep path; zero per-frame ML.
 */
object FatigueEngine {

    /** Default velocity loss threshold for set termination (30% loss). */
    const val DEFAULT_CUTOFF_PERCENT: Double = 30.0

    /** Asymmetry threshold between limbs indicating uneven neuromuscular fatigue (15% difference). */
    const val ASYMMETRY_ALERT_THRESHOLD: Double = 15.0

    /** Minimum range of motion in degrees required to establish a valid peak velocity. */
    const val MIN_ROM_FOR_PEAK_DEG: Double = 20.0

    /**
     * Derives the concentric phase duration in milliseconds for a repetition.
     */
    fun concentricDurationMs(profile: RepetitionExercise, rep: RepRecord): Double {
        val liftsAway = profile.awayFromStart == Contraction.CONCENTRIC
        return if (liftsAway) rep.descentMs else rep.ascentMs
    }

    /**
     * Calculates concentric angular velocity in degrees per second for each side.
     */
    fun concentricVelocity(profile: RepetitionExercise, rep: RepRecord): BilateralSignal {
        val durationMs = concentricDurationMs(profile, rep)
        if (durationMs <= 0.0 || !durationMs.isFinite()) {
            return BilateralSignal(Double.NaN, Double.NaN)
        }
        val durationSec = durationMs / 1000.0
        val vLeft = if (rep.rangeOfMotion.left.isFinite()) rep.rangeOfMotion.left / durationSec else Double.NaN
        val vRight = if (rep.rangeOfMotion.right.isFinite()) rep.rangeOfMotion.right / durationSec else Double.NaN
        return BilateralSignal(vLeft, vRight)
    }

    /**
     * Analyzes an entire set of repetitions and produces a comprehensive [SetFatigueSummary].
     */
    fun analyzeSet(
        profile: RepetitionExercise,
        reps: List<RepRecord>,
        cutoffPercent: Double = DEFAULT_CUTOFF_PERCENT,
    ): SetFatigueSummary {
        val tracker = FatigueTracker(cutoffPercent = cutoffPercent)
        val repFatigues = ArrayList<RepetitionFatigue>(reps.size)
        for (rep in reps) {
            repFatigues += tracker.track(profile, rep)
        }
        return tracker.summary(profile)
    }
}

/**
 * Stateful accumulator tracking velocity loss across repetitions in a set.
 */
class FatigueTracker(
    val cutoffPercent: Double = FatigueEngine.DEFAULT_CUTOFF_PERCENT,
) {
    private var peakLeft: Double = Double.NaN
    private var peakRight: Double = Double.NaN
    private val records = ArrayList<RepetitionFatigue>()
    private var firstCutoffRepIndex: Int? = null

    /**
     * Updates running fatigue state with a completed [rep].
     */
    fun track(profile: RepetitionExercise, rep: RepRecord): RepetitionFatigue {
        val velocity = FatigueEngine.concentricVelocity(profile, rep)

        // Update peak velocity on valid reps with sufficient range of motion
        if (rep.validity == RepValidity.VALID) {
            if (velocity.left.isFinite() && rep.rangeOfMotion.left >= FatigueEngine.MIN_ROM_FOR_PEAK_DEG) {
                if (!peakLeft.isFinite() || velocity.left > peakLeft) {
                    peakLeft = velocity.left
                }
            }
            if (velocity.right.isFinite() && rep.rangeOfMotion.right >= FatigueEngine.MIN_ROM_FOR_PEAK_DEG) {
                if (!peakRight.isFinite() || velocity.right > peakRight) {
                    peakRight = velocity.right
                }
            }
        }

        val peakSignal = BilateralSignal(peakLeft, peakRight)

        // Compute velocity loss percentage: max(0, (peak - v) / peak * 100)
        val lossLeft = computeLoss(velocity.left, peakLeft)
        val lossRight = computeLoss(velocity.right, peakRight)
        val lossSignal = BilateralSignal(lossLeft, lossRight)

        // Find highest fatigue zone between measured sides
        val maxLoss = maxOfFinite(lossLeft, lossRight)
        val zone = FatigueZone.of(maxLoss)

        val terminationRecommended = maxLoss >= cutoffPercent
        if (terminationRecommended && firstCutoffRepIndex == null) {
            firstCutoffRepIndex = rep.repIndex
        }

        val asymmetry = if (lossLeft.isFinite() && lossRight.isFinite()) lossLeft - lossRight else Double.NaN

        val repFatigue = RepetitionFatigue(
            repIndex = rep.repIndex,
            concentricVelocity = velocity,
            peakConcentricVelocity = peakSignal,
            velocityLossPercent = lossSignal,
            zone = zone,
            isTerminationRecommended = terminationRecommended,
            lossAsymmetryPercent = asymmetry,
        )

        records += repFatigue
        return repFatigue
    }

    /**
     * Generates a final [SetFatigueSummary] from all tracked repetitions.
     */
    fun summary(profile: RepetitionExercise): SetFatigueSummary {
        var maxLossL = 0.0
        var maxLossR = 0.0
        var hasAsymmetry = false

        for (rf in records) {
            if (rf.velocityLossPercent.left.isFinite() && rf.velocityLossPercent.left > maxLossL) {
                maxLossL = rf.velocityLossPercent.left
            }
            if (rf.velocityLossPercent.right.isFinite() && rf.velocityLossPercent.right > maxLossR) {
                maxLossR = rf.velocityLossPercent.right
            }
            if (rf.lossAsymmetryPercent.isFinite() && abs(rf.lossAsymmetryPercent) >= FatigueEngine.ASYMMETRY_ALERT_THRESHOLD) {
                hasAsymmetry = true
            }
        }

        val finalVelocity = records.lastOrNull()?.concentricVelocity ?: BilateralSignal(Double.NaN, Double.NaN)
        val endingMaxLoss = maxOfFinite(maxLossL, maxLossR)

        return SetFatigueSummary(
            repetitions = records.toList(),
            setPeakVelocity = BilateralSignal(peakLeft, peakRight),
            finalVelocity = finalVelocity,
            maxVelocityLossPercent = BilateralSignal(maxLossL, maxLossR),
            endingZone = FatigueZone.of(endingMaxLoss),
            recommendedCutoffRepIndex = firstCutoffRepIndex,
            hasFatigueAsymmetry = hasAsymmetry,
        )
    }

    private fun computeLoss(current: Double, peak: Double): Double {
        if (!current.isFinite() || !peak.isFinite() || peak <= 0.0) return Double.NaN
        return max(0.0, (peak - current) / peak * 100.0)
    }

    private fun maxOfFinite(a: Double, b: Double): Double = when {
        a.isFinite() && b.isFinite() -> max(a, b)
        a.isFinite() -> a
        b.isFinite() -> b
        else -> 0.0
    }
}
