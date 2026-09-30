package com.kinesis.core.analysis.anomaly

import kotlin.math.ceil
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Layer 4's model: the Isolation Forest of Liu, Ting and Zhou (IEEE ICDM 2008), written in the
 * shared core - DECISIONS.md P3-34.
 *
 * A point that is easy to isolate by random splits is unusual. The expected constants are the
 * paper's, computed independently in Python.
 */
class IsolationForestTest {

    /** 60 points on a 10 by 6 grid, spacing 0.1. */
    private fun grid(): List<DoubleArray> = (0 until 60).map { doubleArrayOf((it % 10) * 0.1, (it / 10) * 0.1) }

    @Test
    fun the_normalising_constant_is_the_papers() {
        assertEquals(0.0, IsolationForest.averagePathLength(1))
        assertEquals(1.0, IsolationForest.averagePathLength(2))
        assertEquals(1.207392357586557, IsolationForest.averagePathLength(3), absoluteTolerance = 1e-12)
        assertEquals(3.7488804844724397, IsolationForest.averagePathLength(10), absoluteTolerance = 1e-12)
        assertEquals(10.244770920116851, IsolationForest.averagePathLength(256), absoluteTolerance = 1e-12)
    }

    @Test
    fun a_point_outside_the_rest_scores_above_their_middle() {
        val forest = IsolationForest.fit(grid(), seed = 1L)
        val middle = forest.score(doubleArrayOf(0.45, 0.25))
        val outside = forest.score(doubleArrayOf(5.0, 5.0))
        assertTrue(outside > middle + 0.1, "outside $outside, middle $middle")
        assertTrue(outside > 0.6, "outside $outside")
    }

    /**
     * The paper's algorithm, pinned. A split falls between the minimum and maximum of the points
     * it divides, so a point beyond the history follows the path of the history's own edge. It
     * scores exactly as that edge point does, however far out it is. DECISIONS.md P3-34.
     */
    @Test
    fun a_point_beyond_the_history_scores_exactly_as_the_historys_own_edge_does() {
        val forest = IsolationForest.fit(grid(), seed = 1L)
        val corner = forest.score(doubleArrayOf(0.9, 0.5))
        assertEquals(corner, forest.score(doubleArrayOf(5.0, 5.0)))
        assertEquals(corner, forest.score(doubleArrayOf(500.0, 500.0)))
    }

    @Test
    fun every_score_lies_between_zero_and_one() {
        val forest = IsolationForest.fit(grid(), seed = 2L)
        for (x in grid() + listOf(doubleArrayOf(-3.0, 9.0), doubleArrayOf(0.45, 0.25))) {
            val s = forest.score(x)
            assertTrue(s > 0.0 && s <= 1.0, "score $s")
        }
    }

    @Test
    fun a_history_of_identical_points_has_nothing_to_isolate_so_everything_scores_one_half() {
        val forest = IsolationForest.fit(List(30) { doubleArrayOf(1.0, 2.0) }, seed = 3L)
        assertEquals(0.5, forest.score(doubleArrayOf(1.0, 2.0)), absoluteTolerance = 1e-12)
        assertEquals(0.5, forest.score(doubleArrayOf(100.0, -5.0)), absoluteTolerance = 1e-12)
    }

    @Test
    fun a_feature_that_never_varied_is_never_split_on() {
        val rows = (0 until 40).map { doubleArrayOf(0.0, it * 0.1) }
        val forest = IsolationForest.fit(rows, seed = 4L)
        // Only the second feature can separate anything, so the first cannot move a score.
        assertEquals(forest.score(doubleArrayOf(0.0, 1.5)), forest.score(doubleArrayOf(1000.0, 1.5)))
    }

    @Test
    fun the_subsample_is_the_smaller_of_256_and_the_history() {
        assertEquals(60, IsolationForest.fit(grid(), seed = 5L).subsampleSize)
        val many = (0 until 300).map { doubleArrayOf(it.toDouble(), (it * 7 % 13).toDouble()) }
        assertEquals(256, IsolationForest.fit(many, seed = 5L).subsampleSize)
    }

    @Test
    fun no_tree_grows_past_the_papers_height_limit() {
        val forest = IsolationForest.fit(grid(), seed = 6L)
        assertEquals(100, forest.treeCount)
        assertTrue(forest.deepestLeaf <= ceil(log2(60.0)).toInt(), "deepest ${forest.deepestLeaf}")
    }

    @Test
    fun the_same_history_and_seed_grow_the_same_forest_and_another_seed_does_not() {
        val x = doubleArrayOf(0.95, 0.55)
        val a = IsolationForest.fit(grid(), seed = 8L).score(x)
        assertEquals(a, IsolationForest.fit(grid(), seed = 8L).score(x))
        assertNotEquals(a, IsolationForest.fit(grid(), seed = 9L).score(x))
    }

    @Test
    fun a_history_it_cannot_use_is_refused() {
        assertFailsWith<IllegalArgumentException> { IsolationForest.fit(listOf(doubleArrayOf(1.0)), seed = 1L) }
        assertFailsWith<IllegalArgumentException> {
            IsolationForest.fit(listOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0)), seed = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            IsolationForest.fit(listOf(doubleArrayOf(1.0, Double.NaN), doubleArrayOf(1.0, 2.0)), seed = 1L)
        }
        val forest = IsolationForest.fit(grid(), seed = 1L)
        assertFailsWith<IllegalArgumentException> { forest.score(doubleArrayOf(1.0)) }
    }
}
