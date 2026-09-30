package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.anomaly.AnomalyFeatures
import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.CalibrationPolicy
import com.kinesis.core.analysis.deviation.DeviationRules
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The anomaly study's own pieces - DECISIONS.md P3-34, P3-36, P3-37. What it measures is the core's;
 * these check that the plants are what the protocol says, that each keeps the record self-consistent,
 * and that the histories are split as written.
 */
class AnomalyStudyTest {

    private val press = ExerciseLibrary.shoulderPress
    private val median = BilateralSignal(80.0, 70.0)

    /** A triangle from [start], furthest at point 32; the press's far position is the greater angle. */
    private fun curve(start: Double, excursion: Double) = DoubleArray(64) { i -> start + excursion * (1.0 - abs(i - 32) / 32.0) }

    private fun rep(index: Int, left: Double, right: Double, descentMs: Double = 900.0, ascentMs: Double = 1_100.0) = RepRecord(
        repIndex = index, attributedSide = null, startMs = 0.0, endMs = 2_000.0, validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(left, right),
        peakValue = BilateralSignal(90.0, 92.0),
        troughValue = BilateralSignal(90.0 + left, 92.0 + right),
        descentMs = descentMs, ascentMs = ascentMs,
        resampledLeft = curve(95.0, 0.8 * left), resampledRight = curve(97.0, 0.8 * right),
    )

    private fun shape(rep: RepRecord) = AnomalyFeatures.of(press, rep, withShape = true)!!.drop(5)

    @Test
    fun a_range_cut_scales_the_curve_towards_its_start_and_leaves_its_shape() {
        val before = rep(9, 82.0, 71.0)
        val cut = AnomalyPlanting.cutRange(press, before, median, 0.3, null)!!
        assertEquals(82.0 - 0.3 * 80.0, cut.rangeOfMotion.left, 1e-12)
        assertEquals(71.0 - 0.3 * 70.0, cut.rangeOfMotion.right, 1e-12)
        assertEquals(90.0 + cut.rangeOfMotion.left, cut.troughValue.left, 1e-12)
        val share = cut.rangeOfMotion.left / 82.0
        for (i in 0 until 64) {
            assertEquals((before.resampledLeft[i] - 95.0) * share, cut.resampledLeft[i] - 95.0, 1e-9)
        }
        // Size and shape are kept apart (P3-34): a range cut changes F1, not F2.
        val a = shape(before)
        val b = shape(cut)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-12)
        assertEquals(before.descentMs, cut.descentMs)
    }

    @Test
    fun one_side_shortened_changes_that_side_and_its_curve_only() {
        val before = rep(9, 82.0, 71.0)
        val cut = AnomalyPlanting.cutRange(press, before, median, 0.4, Side.RIGHT)!!
        assertEquals(82.0, cut.rangeOfMotion.left)
        assertContentEquals(before.resampledLeft, cut.resampledLeft)
        assertEquals(71.0 - 0.4 * 70.0, cut.rangeOfMotion.right, 1e-12)
        assertTrue(cut.resampledRight[32] < before.resampledRight[32])
        assertEquals(before.resampledRight[0], cut.resampledRight[0], 1e-12)
    }

    @Test
    fun a_range_cut_that_leaves_no_range_is_not_planted() {
        assertNull(AnomalyPlanting.cutRange(press, rep(9, 20.0, 71.0), median, 0.4, null))
    }

    @Test
    fun a_stall_holds_the_far_value_for_that_share_of_the_repetition() {
        val before = rep(9, 82.0, 71.0)
        val stalled = AnomalyPlanting.stall(press, before, 0.25)
        val far = before.resampledLeft.max()
        val held = stalled.resampledLeft.count { abs(it - far) < 1e-9 }
        assertTrue(abs(held - 0.25 * 63) <= 1.0, "held for $held of 64 points")
        assertEquals(far, stalled.resampledLeft.max(), 1e-12)
        assertEquals(before.resampledLeft.first(), stalled.resampledLeft.first(), 1e-12)
        assertEquals(before.resampledLeft.last(), stalled.resampledLeft.last(), 1e-12)
        // The curve only: F1 and the phase times are as they were.
        assertEquals(before.rangeOfMotion, stalled.rangeOfMotion)
        assertEquals(before.troughValue, stalled.troughValue)
        assertEquals(before.descentMs, stalled.descentMs)
        assertEquals(before.ascentMs, stalled.ascentMs)
        assertTrue(shape(stalled).zip(shape(before)).any { (a, b) -> abs(a - b) > 0.05 }, "a stall must change the shape")
    }

    /** So the phase times still hold: the original far point lies inside the plateau. */
    @Test
    fun a_stall_keeps_the_far_point_inside_its_plateau() {
        val curve = DoubleArray(64) { i -> 100.0 + 60.0 * (1.0 - abs(i - 12) / 51.0).coerceAtLeast(0.0) }
        val stalled = AnomalyPlanting.stalled(curve, towardsFar = 1.0, share = 0.15)
        assertEquals(curve[12], stalled[12], 1e-12)
        val plateau = stalled.indices.filter { abs(stalled[it] - curve[12]) < 1e-9 }
        assertTrue(plateau.first() < 12 && plateau.last() > 12, "plateau $plateau")
        assertEquals(plateau.size, plateau.last() - plateau.first() + 1)
    }

    @Test
    fun the_combined_plant_is_a_tenth_of_the_range_and_a_quarter_faster() {
        val combined = AnomalyPlanting.combined(press, rep(9, 82.0, 71.0), median)!!
        assertEquals(82.0 - 8.0, combined.rangeOfMotion.left, 1e-12)
        assertEquals(71.0 - 7.0, combined.rangeOfMotion.right, 1e-12)
        assertEquals(900.0 / 1.25, combined.descentMs, 1e-12)
        assertEquals(1_100.0 / 1.25, combined.ascentMs, 1e-12)
    }

    @Test
    fun a2_fits_on_the_first_forty_and_scores_the_rest() {
        assertEquals((0 until 40) to (40 until 91), AnomalyWindows.a2(91))
        assertEquals((0 until 30) to (30 until 30), AnomalyWindows.a2(30))
    }

    @Test
    fun a1_uses_a_history_only_when_twenty_follow_it() {
        assertEquals(listOf(10, 20, 40, 80), AnomalyWindows.a1(100))
        assertEquals(listOf(10, 20, 40), AnomalyWindows.a1(99))
        assertEquals(listOf(10), AnomalyWindows.a1(30))
        assertEquals(emptyList(), AnomalyWindows.a1(29))
    }

    /** Detector 2 on F1 is Layer 1's own z, summed in squares rather than maximised. */
    @Test
    fun detector_two_on_f1_uses_layer_ones_own_z() {
        val history = (1..40).map { rep(it, 78.0 + (it * 7 % 9), 68.0 + (it * 5 % 7), 850.0 + (it * 13 % 11) * 10.0, 1_050.0 + (it * 3 % 5) * 20.0) }
        val policy = CalibrationPolicy(
            minValidRepsPerSide = CalibrationPolicy.PROVISIONAL.minValidRepsPerSide,
            maxValidRepsPerSide = 40,
            minHoldMs = CalibrationPolicy.PROVISIONAL.minHoldMs,
            version = "test",
        )
        val baseline = (Calibration.fromRepetitions(press, history, 0.0, null, policy) as CalibrationOutcome.Calibrated).baseline as RepetitionBaseline
        val key = AnomalyFeatures.keyOf(press, history.first())
        val z = RobustZ(history.map { AnomalyFeatures.of(press, it, false)!! }, AnomalyFeatures.floorsOf(key, false))
        val probe = rep(41, 60.0, 75.0, 600.0, 1_400.0)
        val d = DeviationRules.deviations(press, probe, baseline)
        val layerOne = doubleArrayOf(d.rangeOfMotion.left, d.rangeOfMotion.right, d.lifting, d.lowering, d.asymmetry)
        val ours = z.z(AnomalyFeatures.of(press, probe, false)!!)
        for (i in layerOne.indices) assertEquals(layerOne[i], ours[i], 1e-9)
        assertEquals(ours.sumOf { it * it }, z.sumOfSquares(AnomalyFeatures.of(press, probe, false)!!), 1e-9)
    }
}
