package com.kinesis.core.analysis.history

import com.kinesis.core.analysis.history.HistoryFixtures.holds
import com.kinesis.core.analysis.history.HistoryFixtures.reps
import com.kinesis.core.analysis.history.HistoryFixtures.session
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each exercise's trend, set by set - DECISIONS.md P3-51. Left and right are each their own mean
 * over the set's counted repetitions, never combined (CLAUDE.md section 3); a side not measured is
 * a gap, not a zero.
 */
class TrendsTest {

    @Test
    fun one_point_per_set_oldest_first_and_the_limit_keeps_the_newest() {
        val history = (1..5).map { session(-10 + it, reps("squat", it)) }.reversed()
        val all = Trends.of(history, "squat", limit = 12)
        assertEquals(listOf(1, 2, 3, 4, 5), all.map { it.countedRepetitions })

        val last3 = Trends.of(history, "squat", limit = 3)
        assertEquals(listOf(3, 4, 5), last3.map { it.countedRepetitions })
        assertTrue(last3.zipWithNext().all { (a, b) -> a.startedAtEpochMs <= b.startedAtEpochMs })
    }

    @Test
    fun each_side_s_mean_is_its_own() {
        val point = Trends.of(listOf(session(0, reps("squat", 4, left = 100.0, right = 80.0))), "squat", 12).single()
        assertEquals(100.0, point.rangeOfMotion.left)
        assertEquals(80.0, point.rangeOfMotion.right)
    }

    /** An alternating curl set measured only the right arm: the left is a gap, never 0. */
    @Test
    fun a_side_not_measured_is_a_gap() {
        val point = Trends.of(
            listOf(session(0, reps("bicep_curl", 3, left = Double.NaN, right = 120.0, side = Side.RIGHT))),
            "bicep_curl",
            12,
        ).single()
        assertTrue(point.rangeOfMotion.left.isNaN(), "an unmeasured side read ${point.rangeOfMotion.left}")
        assertEquals(120.0, point.rangeOfMotion.right)
    }

    @Test
    fun turned_back_repetitions_are_not_counted_or_averaged() {
        val point = Trends.of(listOf(session(0, reps("squat", 2, aborted = 5, left = 90.0, right = 90.0))), "squat", 12).single()
        assertEquals(2, point.countedRepetitions)
    }

    @Test
    fun a_hold_gives_seconds_and_no_range() {
        val point = Trends.of(
            listOf(session(0, holds("plank", listOf(30_000.0, 20_000.0)), holds("plank", listOf(50_000.0), HoldValidity.BROKEN))),
            "plank",
            12,
        )
        assertEquals(2, point.size, "one point per set")
        assertEquals(50_000.0, point[0].heldMs, "completed holds in the first set")
        assertEquals(0.0, point[1].heldMs, "a broken hold holds nothing")
        assertTrue(point.all { it.rangeOfMotion.left.isNaN() && it.rangeOfMotion.right.isNaN() })
    }

    @Test
    fun other_exercises_are_left_out() {
        val history = listOf(session(-1, reps("lunge", 6)), session(0, reps("squat", 4)))
        assertEquals(listOf(4), Trends.of(history, "squat", 12).map { it.countedRepetitions })
        assertEquals(emptyList(), Trends.of(history, "plank", 12))
    }
}
