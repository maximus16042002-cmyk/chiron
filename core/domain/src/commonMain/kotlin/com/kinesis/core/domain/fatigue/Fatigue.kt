package com.kinesis.core.domain.fatigue

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side

/**
 * Exercise science velocity-loss fatigue zones (González-Badillo & Sánchez-Medina 2010,
 * Pareja-Blanco et al. 2017).
 *
 * Grounded in Velocity-Based Training (VBT) research:
 *  - FRESH (<15%): Maximal neuromuscular power, crisp technique.
 *  - OPTIMAL_STIMULUS (15–25%): High motor unit recruitment with low metabolic/technique degradation.
 *  - HIGH_FATIGUE (25–35%): Significant neuromuscular fatigue, velocity deceleration, impending form breakdown.
 *  - EXHAUSTION (>=35%): Mechanical breakdown territory, severe velocity loss.
 */
enum class FatigueZone(val minLossPercent: Double, val maxLossPercent: Double, val label: String) {
    FRESH(0.0, 15.0, "Fresh"),
    OPTIMAL_STIMULUS(15.0, 25.0, "Optimal Stimulus"),
    HIGH_FATIGUE(25.0, 35.0, "High Fatigue"),
    EXHAUSTION(35.0, 100.0, "Exhaustion");

    companion object {
        fun of(lossPercent: Double): FatigueZone = when {
            !lossPercent.isFinite() || lossPercent < 15.0 -> FRESH
            lossPercent < 25.0 -> OPTIMAL_STIMULUS
            lossPercent < 35.0 -> HIGH_FATIGUE
            else -> EXHAUSTION
        }
    }
}

/**
 * Velocity and fatigue metrics for a single completed repetition.
 *
 * Bilateral signals are never averaged: velocities and velocity losses are computed
 * and preserved per side.
 */
data class RepetitionFatigue(
    val repIndex: Int,
    /** Mean concentric angular velocity in degrees per second, per side. */
    val concentricVelocity: BilateralSignal,
    /** Fastest concentric angular velocity observed in the set up to this rep, per side. */
    val peakConcentricVelocity: BilateralSignal,
    /** Velocity loss percentage relative to the set's peak, per side (0% - 100%). */
    val velocityLossPercent: BilateralSignal,
    /** The fatigue zone of the most fatigued side (or single measured side). */
    val zone: FatigueZone,
    /**
     * Whether set termination is recommended (e.g. loss exceeds cutoff, default 30%).
     */
    val isTerminationRecommended: Boolean,
    /**
     * Signed difference in velocity loss percentage between left and right (left minus right),
     * or Double.NaN if the repetition was one-sided.
     */
    val lossAsymmetryPercent: Double,
)

/**
 * Set-level fatigue summary across all completed repetitions in a set.
 */
data class SetFatigueSummary(
    val repetitions: List<RepetitionFatigue>,
    /** Peak concentric velocity achieved in the set, per side (deg/s). */
    val setPeakVelocity: BilateralSignal,
    /** Final repetition's concentric velocity, per side (deg/s). */
    val finalVelocity: BilateralSignal,
    /** Maximum velocity loss percentage observed across the set, per side. */
    val maxVelocityLossPercent: BilateralSignal,
    /** The overall ending fatigue zone. */
    val endingZone: FatigueZone,
    /** The rep index where set termination was first recommended, or null if cutoff was not reached. */
    val recommendedCutoffRepIndex: Int?,
    /** Whether significant bilateral fatigue asymmetry occurred (e.g. >= 15% difference in loss). */
    val hasFatigueAsymmetry: Boolean,
)
