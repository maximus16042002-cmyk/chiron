package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bicep curl's thresholds, from the phone - DECISIONS.md P3-23.
 *
 * In the 2026-09-27 side-on set, 9 deep curls timed out at the old 10 s limit
 * with the arm still on its way down, and the resting arm, which read 148-170
 * degrees side-on, flickered across the old "at the top" edge of 147 and made
 * about twenty junk records, each one an amber flash. Both settings stay
 * provisional.
 */
class CurlSettingsTest {

    private val spec = ExerciseLibrary.bicepCurl.repSignal
    private val frameMs = 67.0

    private fun fsm() = RepFsm(spec, 1024, maxBridgedGapMs = TemporalConfig.PROVISIONAL.maxBridgedGapMs)

    private fun run(values: List<Double>): List<RepCycle> {
        val machine = fsm()
        val cycles = ArrayList<RepCycle>()
        values.forEachIndexed { i, v -> machine.step(i * frameMs, v)?.let { cycles += it } }
        return cycles
    }

    private fun ramp(from: Double, to: Double, seconds: Double): List<Double> {
        val frames = (seconds * 1000 / frameMs).toInt()
        return (1..frames).map { from + (to - from) * it / frames }
    }

    private fun hold(value: Double, seconds: Double) = List((seconds * 1000 / frameMs).toInt()) { value }

    /** 5 s up, 3 s held at the shoulder, 5 s down: as slow as the curls that timed out on the phone. */
    @Test
    fun a_slow_curl_with_a_pause_at_the_top_counts() {
        val values = hold(160.0, 1.0) + ramp(160.0, 40.0, 5.0) + hold(40.0, 3.0) + ramp(40.0, 160.0, 5.0) + hold(160.0, 1.0)
        assertEquals(listOf(RepValidity.VALID), run(values).map { it.validity })
    }

    /** A straight arm resting side-on reads 148 to 170; its wobble is not a curl. */
    @Test
    fun a_resting_arm_that_wobbles_makes_no_records() {
        val wobble = (0 until 150).map { 146.0 + 6.0 * ((it * 7) % 5) / 4.0 } // 146 .. 152
        assertTrue(run(hold(160.0, 1.0) + wobble).isEmpty())
    }

    /** The far end is unchanged: a half curl still does not count. */
    @Test
    fun a_half_curl_still_does_not_count() {
        val values = hold(160.0, 1.0) + ramp(160.0, 100.0, 2.0) + ramp(100.0, 160.0, 2.0) + hold(160.0, 1.0)
        assertTrue(run(values).none { it.validity == RepValidity.VALID })
    }
}
