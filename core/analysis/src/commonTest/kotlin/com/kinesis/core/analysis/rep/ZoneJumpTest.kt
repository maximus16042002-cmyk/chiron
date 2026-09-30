package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A movement that crosses the whole zone between two results - DECISIONS.md P3-31, P3-32.
 *
 * On the phone at a throttled ~6 results a second, and in MM-Fit's fast starts, one result can
 * be at the start and the next already in the far zone. The machine used to take that only as
 * the start of a descent; if the result after was back at the start, the repetition was
 * recorded as turned back although it had reached full depth (P3-31: set 67 #13, 121 degrees of
 * range, "ABORTED"). Now a result that lands in the far zone from the start position reaches
 * the bottom, and one that lands back at the start from the bottom completes the repetition.
 *
 * Timed honestly: when the crossing happened between two results, the phase is timed from the
 * last result on one side to the first on the other - the most it can have taken.
 *
 * What must not change: the minimum duration still refuses a twitch, and the ordinary path is
 * timed between the zone edges as before.
 */
class ZoneJumpTest {

    /** The curl: at the start from 137 degrees (145 - 8), in the far zone from 68 (60 + 8), 0.5 s minimum. */
    private val curl = ArchetypeProfiles.bicepCurl.repSignal

    private fun run(fsm: RepFsm, samples: List<Pair<Double, Double>>): List<RepCycle> {
        val cycles = ArrayList<RepCycle>()
        for ((t, v) in samples) fsm.step(t, v)?.let { cycles += it }
        return cycles
    }

    @Test
    fun a_curl_that_reaches_the_far_zone_between_two_results_and_comes_back_counts() {
        // Results 300 ms apart: at the start, then deep in the far zone, then back at the start.
        val fsm = RepFsm(curl, 256)
        val cycles = run(fsm, listOf(0.0 to 170.0, 300.0 to 170.0, 600.0 to 40.0, 900.0 to 170.0, 1_200.0 to 170.0))
        val rep = cycles.single()
        assertEquals(RepValidity.VALID, rep.validity)
        assertEquals(1, fsm.completedReps)
        // Down: from the last result at the start (300) to the first in the far zone (600).
        assertEquals(300.0, rep.descentMs, 1e-9)
        // Up: from the last result in the far zone (600) to the first back at the start (900).
        assertEquals(300.0, rep.ascentMs, 1e-9)
        assertEquals(170.0, rep.peakValue, 1e-9)
        assertEquals(40.0, rep.troughValue, 1e-9)
        assertEquals(900.0, rep.endMs, 1e-9)
    }

    @Test
    fun a_jump_into_the_far_zone_that_stays_there_is_one_repetition() {
        val fsm = RepFsm(curl, 256)
        val cycles = run(
            fsm,
            listOf(0.0 to 170.0, 300.0 to 170.0, 600.0 to 40.0, 900.0 to 42.0, 1_200.0 to 41.0, 1_500.0 to 170.0, 1_800.0 to 170.0),
        )
        val rep = cycles.single()
        assertEquals(RepValidity.VALID, rep.validity)
        assertEquals(300.0, rep.descentMs, 1e-9)
        // Straight back to the start from the far zone: from the last far result (1200) to 1500.
        assertEquals(300.0, rep.ascentMs, 1e-9)
        assertEquals(1_500.0, rep.endMs, 1e-9)
    }

    /**
     * P3-28's fast start: the gate rejects the first fast samples, the start position is held
     * across the gap, and the first measured value is already in the far zone. The descent is
     * timed across the gap - it happened somewhere inside it - not from that first value.
     */
    @Test
    fun a_descent_hidden_in_a_bridged_gap_is_timed_across_the_gap() {
        val fsm = RepFsm(curl, 256, maxBridgedGapMs = 500.0)
        val nan = Double.NaN
        val cycles = run(
            fsm,
            listOf(0.0 to 170.0, 67.0 to 170.0, 134.0 to nan, 201.0 to nan, 268.0 to 40.0, 335.0 to 42.0) +
                (1..12).map { (335.0 + 67.0 * it) to (42.0 + 11.0 * it) } + // back up to 174
                listOf(1_200.0 to 174.0),
        )
        val rep = cycles.single()
        assertEquals(RepValidity.VALID, rep.validity)
        assertEquals(268.0 - 67.0, rep.descentMs, 1e-9)
    }

    /**
     * DECISIONS.md P3-39, P3-30 decision 5 - pinned as it is, deliberately. The same fast start,
     * but the first measured value lands just short of the far zone: the descent is timed from
     * that value, not from inside the gap. At 30 results a second this times MM-Fit's fastest
     * curls short (the one-result lifts). Timing it from the gap's first result was tried and
     * measured against the raw angle: better at 30 a second, but at the phone's 15 the gate also
     * holds back the arrival in the far zone, the two lags cancel today, and the change made
     * lifts 133 ms too long (median). A change here has to answer that measurement first.
     */
    @Test
    fun a_descent_that_starts_inside_a_bridged_gap_is_timed_from_the_first_result_after_it() {
        val fsm = RepFsm(curl, 256, maxBridgedGapMs = 500.0)
        val nan = Double.NaN
        val cycles = run(
            fsm,
            listOf(0.0 to 170.0, 67.0 to 170.0, 134.0 to nan, 201.0 to nan, 268.0 to 71.4, 335.0 to 60.0) +
                (1..12).map { (335.0 + 67.0 * it) to (60.0 + 10.0 * it) } + // back up to 180
                listOf(1_200.0 to 174.0),
        )
        val rep = cycles.single()
        assertEquals(RepValidity.VALID, rep.validity)
        assertEquals(335.0 - 268.0, rep.descentMs, 1e-9)
    }

    /** The way back, the same, and pinned for the same reason. */
    @Test
    fun an_ascent_that_starts_inside_a_bridged_gap_is_timed_from_the_first_result_after_it() {
        val fsm = RepFsm(curl, 256, maxBridgedGapMs = 500.0)
        val nan = Double.NaN
        val down = (0..10).map { 170.0 - 13.0 * it } // 170 .. 40, 100 ms apart
        val samples = (List(3) { 170.0 } + down + List(4) { 40.0 }).mapIndexed { i, v -> i * 100.0 to v } + // far zone to 1700
            listOf(1_800.0 to nan, 1_900.0 to nan, 2_000.0 to 132.1, 2_100.0 to 165.2, 2_200.0 to 170.0)
        val rep = run(fsm, samples).single()
        assertEquals(RepValidity.VALID, rep.validity)
        assertEquals(2_100.0 - 2_000.0, rep.ascentMs, 1e-9)
    }

    /** Without a gap, a value between the zones starts the phase where it is measured. */
    @Test
    fun without_a_gap_a_phase_starts_at_the_first_result_off_its_zone() {
        val fsm = RepFsm(curl, 256, maxBridgedGapMs = 500.0)
        val cycles = run(
            fsm,
            listOf(0.0 to 170.0, 67.0 to 170.0, 134.0 to 71.4, 201.0 to 60.0) +
                (1..12).map { (201.0 + 67.0 * it) to (60.0 + 10.0 * it) } +
                listOf(1_100.0 to 174.0),
        )
        assertEquals(201.0 - 134.0, cycles.single().descentMs, 1e-9)
    }

    @Test
    fun a_twitch_across_the_zone_is_still_too_short_to_count() {
        val fsm = RepFsm(curl, 256)
        val cycles = run(fsm, listOf(0.0 to 170.0, 100.0 to 170.0, 200.0 to 40.0, 300.0 to 170.0, 400.0 to 170.0))
        assertEquals(RepValidity.ABORTED, cycles.single().validity)
        assertEquals(0, fsm.completedReps)
    }

    @Test
    fun a_movement_that_never_reaches_the_far_zone_is_still_turned_back() {
        val fsm = RepFsm(curl, 256)
        val cycles = run(fsm, listOf(0.0 to 170.0, 300.0 to 170.0, 600.0 to 90.0, 900.0 to 170.0))
        assertEquals(RepValidity.ABORTED, cycles.single().validity)
    }

    /** The ordinary path, every result in between: timed between the zone edges, as before. */
    @Test
    fun an_ordinary_curl_is_timed_as_before() {
        val fsm = RepFsm(curl, 256)
        val down = (0..10).map { 170.0 - 13.0 * it } // 170 .. 40
        val up = (1..10).map { 40.0 + 13.0 * it } // .. 170
        val values = List(3) { 170.0 } + down + up + List(2) { 170.0 }
        val cycles = run(fsm, values.mapIndexed { i, v -> i * 100.0 to v })
        val rep = cycles.single()
        assertEquals(RepValidity.VALID, rep.validity)
        // First value off the start (131 at 600 ms) to the first in the far zone (66 at 1100 ms).
        assertEquals(500.0, rep.descentMs, 1e-9)
        // First value off the far zone (79 at 1600 ms) to the first back at the start (144 at 2100 ms).
        assertEquals(500.0, rep.ascentMs, 1e-9)
    }
}
