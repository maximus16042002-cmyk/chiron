package com.kinesis.core.analysis.anomaly

import com.kinesis.core.analysis.anomaly.AnomalyTestReps.rep
import com.kinesis.core.analysis.anomaly.AnomalyTestReps.sine
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.Side
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What Layer 4 sees of a repetition - DECISIONS.md P3-34.
 *
 * F1 is Layer 1's scalars: each measured side's range of motion, the lifting and lowering time,
 * and left minus right when both sides were measured. F2 is each measured side's curve, from its
 * start value over its own range, in 16 points. A repetition belongs to the history of its
 * exercise, the side it was attributed to, and the sides it measured, so an alternating curl's
 * arms and a near-side exercise filmed from either side never share a model.
 */
class AnomalyFeaturesTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl
    private val pushUp = ExerciseLibrary.pushUp

    @Test
    fun a_squat_gives_both_ranges_both_times_and_the_balance() {
        // The squat lowers on the way down (eccentric away from the start): lowering is the descent.
        val features = AnomalyFeatures.of(squat, rep(90.0, 86.0, descentMs = 1200.0, ascentMs = 900.0), withShape = false)
        assertContentEquals(doubleArrayOf(90.0, 86.0, 900.0, 1200.0, 4.0), assertNotNull(features))
    }

    @Test
    fun the_shape_is_each_sides_curve_from_its_start_over_its_range_in_sixteen_points() {
        val features = assertNotNull(AnomalyFeatures.of(squat, rep(90.0, 86.0, 1200.0, 900.0), withShape = true))
        assertEquals(5 + 2 * AnomalyFeatures.SHAPE_POINTS, features.size)
        for (side in 0..1) {
            for (k in 0 until 16) {
                val expected = (0 until 4).sumOf { sine(4 * k + it) } / 4.0
                assertEquals(expected, features[5 + 16 * side + k], absoluteTolerance = 1e-12, "side $side point $k")
            }
        }
    }

    @Test
    fun the_shape_rises_towards_the_far_position_whichever_way_the_angle_moves() {
        // A curve that climbs from 40 degrees by 60 has the same shape as one that falls from 170 by 60.
        val falling = assertNotNull(AnomalyFeatures.of(squat, rep(60.0, 60.0, 1000.0, 1000.0, peak = 170.0), withShape = true))
        val rising = rep(60.0, 60.0, 1000.0, 1000.0, peak = 40.0)
        val climbing = com.kinesis.core.domain.rep.RepRecord(
            repIndex = 1, attributedSide = null, startMs = 0.0, endMs = 2000.0, validity = rising.validity,
            rangeOfMotion = rising.rangeOfMotion, peakValue = rising.peakValue, troughValue = rising.troughValue,
            descentMs = 1000.0, ascentMs = 1000.0,
            resampledLeft = DoubleArray(64) { 40.0 + 60.0 * sine(it) }, resampledRight = DoubleArray(64) { 40.0 + 60.0 * sine(it) },
        )
        val lateral = ExerciseLibrary.lateralRaise
        assertEquals(!squat.repSignal.topIsGreater, lateral.repSignal.topIsGreater, "one falls, the other climbs")
        val climbed = assertNotNull(AnomalyFeatures.of(lateral, climbing, withShape = true))
        for (k in 0 until 32) assertEquals(falling[5 + k], climbed[5 + k], absoluteTolerance = 1e-12, "point $k")
    }

    @Test
    fun an_alternating_curl_uses_only_its_own_side() {
        val left = rep(110.0, Double.NaN, descentMs = 700.0, ascentMs = 900.0, side = Side.LEFT)
        // The curl lifts away from the start (concentric): lifting is the descent.
        assertContentEquals(doubleArrayOf(110.0, 700.0, 900.0), AnomalyFeatures.of(curl, left, withShape = false))
        assertEquals(3 + 16, assertNotNull(AnomalyFeatures.of(curl, left, withShape = true)).size)
        assertEquals(AnomalyKey("bicep_curl", Side.LEFT, measuresLeft = true, measuresRight = false), AnomalyFeatures.keyOf(curl, left))
    }

    @Test
    fun each_arm_and_each_filmed_side_keeps_its_own_history() {
        val left = rep(110.0, Double.NaN, 700.0, 900.0, side = Side.LEFT)
        val right = rep(Double.NaN, 110.0, 700.0, 900.0, side = Side.RIGHT)
        assertNotEquals(AnomalyFeatures.keyOf(curl, left), AnomalyFeatures.keyOf(curl, right))

        val nearRight = rep(Double.NaN, 70.0, 800.0, 800.0)
        val nearLeft = rep(70.0, Double.NaN, 800.0, 800.0)
        assertEquals(AnomalyKey("pushup", null, measuresLeft = false, measuresRight = true), AnomalyFeatures.keyOf(pushUp, nearRight))
        assertNotEquals(AnomalyFeatures.keyOf(pushUp, nearRight), AnomalyFeatures.keyOf(pushUp, nearLeft))
        assertContentEquals(doubleArrayOf(70.0, 800.0, 800.0), AnomalyFeatures.of(pushUp, nearRight, withShape = false))
    }

    @Test
    fun a_repetition_missing_one_of_its_own_features_has_none() {
        assertNull(AnomalyFeatures.of(squat, rep(90.0, 86.0, Double.NaN, 900.0), withShape = false))
        val gapInCurve = rep(90.0, 86.0, 1200.0, 900.0)
        gapInCurve.resampledLeft[30] = Double.NaN
        assertNotNull(AnomalyFeatures.of(squat, gapInCurve, withShape = false), "the curve is not an F1 feature")
        assertNull(AnomalyFeatures.of(squat, gapInCurve, withShape = true))
    }
}
