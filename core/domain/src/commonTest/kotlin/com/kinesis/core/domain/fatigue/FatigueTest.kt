package com.kinesis.core.domain.fatigue

import com.kinesis.core.domain.bilateral.BilateralSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FatigueTest {

    @Test
    fun fatigue_zones_map_loss_percentages_accurately() {
        assertEquals(FatigueZone.FRESH, FatigueZone.of(0.0))
        assertEquals(FatigueZone.FRESH, FatigueZone.of(12.5))
        assertEquals(FatigueZone.OPTIMAL_STIMULUS, FatigueZone.of(15.0))
        assertEquals(FatigueZone.OPTIMAL_STIMULUS, FatigueZone.of(22.0))
        assertEquals(FatigueZone.HIGH_FATIGUE, FatigueZone.of(25.0))
        assertEquals(FatigueZone.HIGH_FATIGUE, FatigueZone.of(32.0))
        assertEquals(FatigueZone.EXHAUSTION, FatigueZone.of(35.0))
        assertEquals(FatigueZone.EXHAUSTION, FatigueZone.of(50.0))
        assertEquals(FatigueZone.FRESH, FatigueZone.of(Double.NaN))
    }

    @Test
    fun repetition_fatigue_holds_bilateral_signals_independently() {
        val rep = RepetitionFatigue(
            repIndex = 5,
            concentricVelocity = BilateralSignal(120.0, 100.0),
            peakConcentricVelocity = BilateralSignal(150.0, 150.0),
            velocityLossPercent = BilateralSignal(20.0, 33.3),
            zone = FatigueZone.HIGH_FATIGUE,
            isTerminationRecommended = true,
            lossAsymmetryPercent = -13.3,
        )
        assertEquals(120.0, rep.concentricVelocity.left)
        assertEquals(100.0, rep.concentricVelocity.right)
        assertEquals(20.0, rep.velocityLossPercent.left)
        assertEquals(33.3, rep.velocityLossPercent.right)
        assertTrue(rep.isTerminationRecommended)
    }
}
