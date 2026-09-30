package com.kinesis.core.analysis.anomaly

import com.kinesis.core.analysis.anomaly.AnomalyTestReps.rep
import com.kinesis.core.analysis.anomaly.AnomalyTestReps.squatHistory
import com.kinesis.core.analysis.profile.ExerciseLibrary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Detector 4 of the anomaly study: the robust covariance distance - DECISIONS.md P3-36.
 *
 * Median and MAD scales floored by Layer 1's floors, Spearman correlation shrunk towards the
 * identity by lambda = min(1, p / n), and d^2 = z' R^-1 z. It exists to see what P3-35 measured
 * the forest cannot: a repetition beyond the history's range, and an unusual combination.
 */
class RobustDistanceTest {

    private val squat = ExerciseLibrary.squat

    /** One feature: the distance is the squared robust z, with the MAD scaled by 1.4826. */
    @Test
    fun one_feature_is_the_squared_robust_z() {
        // Median 5, absolute deviations 4 3 2 1 0 1 2 3 4 -> MAD 2.
        val history = (1..9).map { doubleArrayOf(it.toDouble()) }
        val model = RobustDistance.fit(history, doubleArrayOf(0.001))
        val z = (8.0 - 5.0) / (2.0 * 1.4826)
        assertEquals(z * z, model.distanceSquared(doubleArrayOf(8.0)), 1e-12)
        assertEquals(0.0, model.distanceSquared(doubleArrayOf(5.0)), 1e-15)
    }

    /** A feature that never varied in the history is judged on its floor, not on zero. */
    @Test
    fun a_scale_below_its_floor_is_the_floor() {
        val history = List(10) { doubleArrayOf(90.0) }
        val model = RobustDistance.fit(history, doubleArrayOf(3.0))
        assertEquals(1.0, model.distanceSquared(doubleArrayOf(93.0)), 1e-12)
    }

    @Test
    fun ranks_average_their_ties() {
        assertContentEquals(doubleArrayOf(1.0, 2.5, 2.5, 4.0), RobustDistance.ranks(doubleArrayOf(10.0, 20.0, 20.0, 30.0)))
        assertContentEquals(doubleArrayOf(3.0, 1.0, 2.0), RobustDistance.ranks(doubleArrayOf(7.0, -1.0, 0.5)))
    }

    @Test
    fun shrinkage_is_features_over_history_and_never_above_one() {
        val floors = DoubleArray(4) { 0.01 }
        assertEquals(4.0 / 20.0, RobustDistance.fit(grid(20, 4), floors).shrinkage, 1e-15)
        assertEquals(1.0, RobustDistance.fit(grid(3, 4), floors).shrinkage, 1e-15)
    }

    /** Unlike the forest (P3-35), nothing caps it at the history's own edge. */
    @Test
    fun the_distance_keeps_growing_beyond_the_historys_range() {
        val history = (1..40).map { doubleArrayOf(80.0 + it * 0.5, 1000.0 + it * 5.0) }
        val model = RobustDistance.fit(history, doubleArrayOf(3.0, 70.0))
        val inSampleMax = history.maxOf { model.distanceSquared(it) }
        val near = model.distanceSquared(doubleArrayOf(110.0, 1300.0))
        val far = model.distanceSquared(doubleArrayOf(140.0, 1600.0))
        assertTrue(near > inSampleMax, "just beyond the range ($near) must exceed every history point ($inSampleMax)")
        assertTrue(far > 2.0 * near, "farther out must be much farther: $far against $near")
    }

    /**
     * Two features that always went together: a point that keeps their relation is nearer than
     * one that breaks it, at the same distance from the median in each feature.
     */
    @Test
    fun a_combination_the_history_never_had_is_farther_than_one_it_had() {
        val history = (0 until 40).map { i -> val u = i / 39.0; doubleArrayOf(80.0 + 20.0 * u, 1000.0 + 400.0 * u) }
        val model = RobustDistance.fit(history, doubleArrayOf(3.0, 70.0))
        val together = model.distanceSquared(doubleArrayOf(98.0, 1360.0)) // deep and slow: as always
        val apart = model.distanceSquared(doubleArrayOf(98.0, 1040.0)) // deep and quick: never
        assertTrue(apart > 3.0 * together, "a broken relation ($apart) must stand out from a kept one ($together)")
    }

    /**
     * The cases the forest missed in P3-35, under the same device rule: above the nearest-rank
     * 95th percentile of the history's own distances. With F1 and with F1 + F2.
     */
    @Test
    fun it_flags_what_the_forest_missed_in_p3_35() {
        val history = squatHistory(40)
        val key = AnomalyFeatures.keyOf(squat, history.first())
        val cases = listOf(
            "half as deep" to rep(50.0, 48.0, descentMs = 1200.0, ascentMs = 1000.0),
            "far outside" to rep(130.0, 128.0, descentMs = 2600.0, ascentMs = 2300.0),
            "one side short" to rep(90.0, 60.0, descentMs = 1200.0, ascentMs = 1000.0),
        )
        for (withShape in listOf(false, true)) {
            val rows = history.map { AnomalyFeatures.of(squat, it, withShape)!! }
            val model = RobustDistance.fit(rows, AnomalyFeatures.floorsOf(key, withShape))
            val cut = nearestRank(rows.map { model.distanceSquared(it) }, 0.95)
            for ((name, probe) in cases) {
                val d2 = model.distanceSquared(AnomalyFeatures.of(squat, probe, withShape)!!)
                assertTrue(d2 > cut, "withShape=$withShape, $name: $d2 not above the cut $cut")
            }
            val usual = model.distanceSquared(AnomalyFeatures.of(squat, rep(90.0, 88.0, 1200.0, 1000.0), withShape)!!)
            assertTrue(usual <= cut, "withShape=$withShape: a usual repetition ($usual) above the cut $cut")
        }
    }

    /** The deep-and-quick squat P3-35 pinned as missed by the forest. */
    @Test
    fun it_flags_the_combination_the_forest_could_not_isolate() {
        val history = AnomalyTestReps.deepIsSlowHistory(40)
        val key = AnomalyFeatures.keyOf(squat, history.first())
        val rows = history.map { AnomalyFeatures.of(squat, it, withShape = false)!! }
        val model = RobustDistance.fit(rows, AnomalyFeatures.floorsOf(key, withShape = false))
        val cut = nearestRank(rows.map { model.distanceSquared(it) }, 0.95)
        val deepAndQuick = model.distanceSquared(AnomalyFeatures.of(squat, rep(98.0, 96.0, 1030.0, 920.0), false)!!)
        assertTrue(deepAndQuick > cut, "deep and quick ($deepAndQuick) not above the cut $cut")
    }

    @Test
    fun a_history_it_cannot_use_is_refused() {
        assertFailsWith<IllegalArgumentException> { RobustDistance.fit(listOf(doubleArrayOf(1.0)), doubleArrayOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { RobustDistance.fit(grid(5, 2), doubleArrayOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { RobustDistance.fit(grid(5, 2), doubleArrayOf(1.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> {
            RobustDistance.fit(listOf(doubleArrayOf(1.0, Double.NaN), doubleArrayOf(2.0, 3.0)), doubleArrayOf(1.0, 1.0))
        }
    }

    private fun grid(rows: Int, columns: Int): List<DoubleArray> =
        List(rows) { r -> DoubleArray(columns) { c -> ((r * 7 + c * 3) % 11).toDouble() + c } }
}
