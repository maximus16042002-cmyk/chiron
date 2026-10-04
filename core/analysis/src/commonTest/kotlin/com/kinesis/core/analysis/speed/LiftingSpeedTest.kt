package com.kinesis.core.analysis.speed

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lifting speed - DECISIONS.md P3-59, the measurement kept from P3-54's fatigue engine with its
 * zones, cutoff and asymmetry alert taken out. A Level 1 measurement: each side's own range over
 * the lifting phase's time, and how much slower than the set's fastest repetition so far.
 */
class LiftingSpeedTest {

    private val squat = ExerciseLibrary.squat
    private val curl = ExerciseLibrary.bicepCurl

    private fun rep(
        index: Int,
        romLeft: Double,
        romRight: Double,
        ascentMs: Double = 1_000.0,
        descentMs: Double = 1_000.0,
        validity: RepValidity = RepValidity.VALID,
        side: Side? = null,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = index * 3_000.0,
        endMs = index * 3_000.0 + descentMs + ascentMs,
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
    fun the_lifting_phase_follows_the_profiles_contraction_direction() {
        // A squat lifts on the way back up; a curl lifts on the way away from the start.
        assertEquals(600.0, LiftingSpeed.liftingPhaseMs(squat, rep(1, 90.0, 90.0, ascentMs = 600.0, descentMs = 1_200.0)))
        assertEquals(500.0, LiftingSpeed.liftingPhaseMs(curl, rep(1, 100.0, 100.0, ascentMs = 1_200.0, descentMs = 500.0)))
    }

    @Test
    fun the_speed_is_each_sides_own_range_over_the_lifting_time() {
        // 90 degrees in half a second on the left, 45 on the right.
        val speed = LiftingSpeed.of(squat, rep(1, 90.0, 45.0, ascentMs = 500.0))
        assertEquals(180.0, speed.left, 1e-9)
        assertEquals(90.0, speed.right, 1e-9)
    }

    @Test
    fun the_slowdown_is_measured_from_the_fastest_counted_repetition_so_far() {
        val summary = LiftingSpeed.summarise(
            squat,
            listOf(
                rep(1, 100.0, 100.0, ascentMs = 500.0), // 200 deg/s
                rep(2, 100.0, 100.0, ascentMs = 500.0), // 200
                rep(3, 100.0, 100.0, ascentMs = 625.0), // 160: 20% slower
                rep(4, 100.0, 100.0, ascentMs = 1_000.0), // 100: 50% slower
            ),
        )
        assertEquals(listOf(0.0, 0.0, 20.0, 50.0), summary.repetitions.map { it.slowdownPercent.left })
        assertEquals(200.0, summary.fastest.left, 1e-9)
        assertEquals(100.0, summary.last.left, 1e-9)
        assertEquals(50.0, summary.largestSlowdownPercent.left, 1e-9)
    }

    /** CLAUDE.md section 3: one side slowing down is never blended into the other. */
    @Test
    fun the_sides_are_measured_separately_and_never_averaged() {
        val summary = LiftingSpeed.summarise(
            squat,
            listOf(
                rep(1, 100.0, 100.0, ascentMs = 500.0), // 200 and 200
                rep(2, 70.0, 100.0, ascentMs = 500.0), // 140 and 200
            ),
        )
        assertEquals(30.0, summary.largestSlowdownPercent.left, 1e-9)
        assertEquals(0.0, summary.largestSlowdownPercent.right, 1e-9)
        assertEquals(140.0, summary.last.left, 1e-9)
        assertEquals(200.0, summary.last.right, 1e-9)
    }

    @Test
    fun a_repetition_that_did_not_count_has_no_speed_and_never_sets_the_fastest() {
        val summary = LiftingSpeed.summarise(
            squat,
            listOf(
                rep(1, 100.0, 100.0, ascentMs = 200.0, validity = RepValidity.ABORTED), // would be 500
                rep(2, 5.0, 5.0, ascentMs = 10.0), // counted, but a twitch: too small to be the fastest
                rep(3, 100.0, 100.0, ascentMs = 1_000.0), // 100
            ),
        )
        assertTrue(summary.repetitions[0].speed.left.isNaN(), "a repetition that did not count shows no speed")
        assertTrue(summary.repetitions[0].slowdownPercent.left.isNaN())
        assertEquals(100.0, summary.fastest.left, 1e-9)
        assertEquals(0.0, summary.repetitions[2].slowdownPercent.left, 1e-9)
    }

    /**
     * Alternating curls measure one side per repetition. A side that was never measured has no
     * slowdown - a dash on screen, not 0%, which P3-54 showed.
     */
    @Test
    fun a_side_never_measured_has_no_slowdown_rather_than_zero() {
        val summary = LiftingSpeed.summarise(
            curl,
            listOf(
                rep(1, 100.0, Double.NaN, descentMs = 500.0, side = Side.LEFT),
                rep(2, 80.0, Double.NaN, descentMs = 500.0, side = Side.LEFT),
            ),
        )
        assertEquals(20.0, summary.largestSlowdownPercent.left, 1e-9)
        assertTrue(summary.largestSlowdownPercent.right.isNaN(), "the right side was never measured")
        assertTrue(summary.fastest.right.isNaN())
        assertTrue(summary.last.right.isNaN())
    }

    @Test
    fun a_missing_or_zero_lifting_time_gives_no_speed() {
        assertTrue(LiftingSpeed.of(squat, rep(1, 90.0, 90.0, ascentMs = 0.0)).left.isNaN())
        assertTrue(LiftingSpeed.of(squat, rep(1, 90.0, 90.0, ascentMs = Double.NaN)).right.isNaN())
    }
}
