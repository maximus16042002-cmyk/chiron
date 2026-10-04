package com.kinesis.core.analysis.fatigue

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.fatigue.FatigueZone
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FatigueEngineTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    private fun rep(
        index: Int,
        romLeft: Double,
        romRight: Double,
        ascentMs: Double = 1000.0,
        descentMs: Double = 1000.0,
        validity: RepValidity = RepValidity.VALID,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = index * 3000.0,
        endMs = index * 3000.0 + descentMs + ascentMs,
        validity = validity,
        rangeOfMotion = BilateralSignal(romLeft, romRight),
        peakValue = BilateralSignal(160.0, 160.0),
        troughValue = BilateralSignal(160.0 - romLeft, 160.0 - romRight),
        descentMs = descentMs,
        ascentMs = ascentMs,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )

    @Test
    fun concentric_duration_matches_contraction_direction() {
        // Squat: ascent is lifting (concentric)
        val squatRep = rep(1, 90.0, 90.0, ascentMs = 600.0, descentMs = 1200.0)
        assertEquals(600.0, FatigueEngine.concentricDurationMs(squat, squatRep))

        // Curl: descent is moving away from start (concentric)
        val curlRep = rep(1, 100.0, 100.0, ascentMs = 1200.0, descentMs = 500.0)
        assertEquals(500.0, FatigueEngine.concentricDurationMs(curl, curlRep))
    }

    @Test
    fun concentric_velocity_computes_deg_per_second_per_side() {
        // 90 degrees in 0.5s -> 180 deg/s
        val rep = rep(1, 90.0, 45.0, ascentMs = 500.0)
        val v = FatigueEngine.concentricVelocity(squat, rep)
        assertEquals(180.0, v.left, 0.001)
        assertEquals(90.0, v.right, 0.001)
    }

    @Test
    fun velocity_loss_tracks_across_reps_and_flags_cutoff() {
        val reps = listOf(
            rep(1, 100.0, 100.0, ascentMs = 500.0), // v = 200 deg/s (Peak)
            rep(2, 100.0, 100.0, ascentMs = 500.0), // v = 200 deg/s (0% loss)
            rep(3, 100.0, 100.0, ascentMs = 625.0), // v = 160 deg/s (20% loss, OPTIMAL)
            rep(4, 100.0, 100.0, ascentMs = 720.0), // v = 138.89 deg/s (30.55% loss, CUTOFF)
            rep(5, 100.0, 100.0, ascentMs = 833.333), // v = 120 deg/s (40% loss, EXHAUSTION)
        )

        val summary = FatigueEngine.analyzeSet(squat, reps, cutoffPercent = 30.0)

        assertEquals(5, summary.repetitions.size)
        assertEquals(200.0, summary.setPeakVelocity.left, 0.01)
        assertEquals(200.0, summary.setPeakVelocity.right, 0.01)
        assertEquals(40.0, summary.maxVelocityLossPercent.left, 0.5)
        assertEquals(FatigueZone.EXHAUSTION, summary.endingZone)
        assertEquals(4, summary.recommendedCutoffRepIndex) // First hit 30% cutoff at rep 4

        // Check rep 3 (optimal stimulus)
        val rep3 = summary.repetitions[2]
        assertEquals(FatigueZone.OPTIMAL_STIMULUS, rep3.zone)
        assertFalse(rep3.isTerminationRecommended)

        // Check rep 4 (cutoff recommendation)
        val rep4 = summary.repetitions[3]
        assertEquals(FatigueZone.HIGH_FATIGUE, rep4.zone)
        assertTrue(rep4.isTerminationRecommended)
    }

    @Test
    fun bilateral_fatigue_asymmetry_detected() {
        val reps = listOf(
            rep(1, 100.0, 100.0, ascentMs = 500.0), // Both 200 deg/s
            // Left stays fast (180 deg/s = 10% loss), Right drops (140 deg/s = 30% loss) -> Asymmetry = 20%
            rep(2, 90.0, 70.0, ascentMs = 500.0),
        )

        val summary = FatigueEngine.analyzeSet(squat, reps)
        assertTrue(summary.hasFatigueAsymmetry)
    }

    @Test
    fun aborted_or_twitch_reps_do_not_establish_peak() {
        val reps = listOf(
            // Twitch rep: tiny ROM (5 deg), fast time -> should not become peak
            rep(1, 5.0, 5.0, ascentMs = 10.0, validity = RepValidity.VALID),
            // Aborted rep: should not become peak
            rep(2, 100.0, 100.0, ascentMs = 200.0, validity = RepValidity.ABORTED),
            // Real first valid rep
            rep(3, 100.0, 100.0, ascentMs = 1000.0, validity = RepValidity.VALID), // v = 100 deg/s
        )

        val summary = FatigueEngine.analyzeSet(squat, reps)
        assertEquals(100.0, summary.setPeakVelocity.left, 0.01)
        assertEquals(100.0, summary.setPeakVelocity.right, 0.01)
    }
}
