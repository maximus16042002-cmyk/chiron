package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldAnalyser
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a hold was held, and how steadily - DECISIONS.md P3-22.
 *
 * `HoldRecord.meanAbsDeviation` says how far from the target the body line was,
 * but not which way, and nothing said how much it swayed. A hold baseline needs
 * both: its typical posture and its steadiness. Both are Level 1 measurements
 * over the frames that held, and nothing else.
 */
class HoldSteadinessTest {

    private val plank = ArchetypeProfiles.plank // target 180, tolerance 20

    @Test
    fun the_mean_and_the_sway_are_over_the_frames_that_held_only() {
        val analyser = HoldAnalyser(plank)
        var t = 0.0
        repeat(10) { analyser.step(t, 150.0); t += 33.0 } // not yet in position
        repeat(100) { i -> analyser.step(t, if (i % 2 == 0) 176.0 else 180.0); t += 33.0 }
        repeat(30) { analyser.step(t, 140.0); t += 33.0 } // broken: out of the band
        val record = analyser.finish()

        assertEquals(178.0, record.meanHeldValue, 1e-9)
        // 100 values, each 2 from the mean: sample variance 400 / 99.
        assertEquals(sqrt(400.0 / 99.0), record.heldValueSd, 1e-9)
    }

    @Test
    fun unseen_frames_enter_neither() {
        val analyser = HoldAnalyser(plank)
        var t = 0.0
        repeat(60) { i ->
            analyser.step(t, if (i % 10 == 5) Double.NaN else 175.0)
            t += 33.0
        }
        val record = analyser.finish()
        assertEquals(175.0, record.meanHeldValue, 1e-12)
        assertEquals(0.0, record.heldValueSd, 1e-12)
    }

    /** One held frame has a position but no spread: unknown, not zero. */
    @Test
    fun a_single_held_frame_has_a_mean_and_no_sway() {
        val analyser = HoldAnalyser(plank)
        analyser.step(0.0, 150.0)
        analyser.step(33.0, 179.0)
        analyser.step(66.0, 150.0)
        val record = analyser.finish()
        assertEquals(179.0, record.meanHeldValue)
        assertTrue(record.heldValueSd.isNaN())
    }

    @Test
    fun a_hold_that_never_held_has_neither() {
        val analyser = HoldAnalyser(plank)
        repeat(30) { analyser.step(it * 33.0, 120.0) }
        val record = analyser.finish()
        assertTrue(record.meanHeldValue.isNaN())
        assertTrue(record.heldValueSd.isNaN())
    }

    /**
     * Computed as it goes, in constant space - CLAUDE.md section 6 - and without
     * the cancellation a naive sum of squares suffers at an angle near 180.
     */
    @Test
    fun a_tiny_sway_around_a_large_angle_is_measured_accurately() {
        val analyser = HoldAnalyser(plank)
        repeat(1_000) { i -> analyser.step(i * 33.0, 179.0 + if (i % 2 == 0) 1e-6 else -1e-6) }
        val record = analyser.finish()
        assertEquals(179.0, record.meanHeldValue, 1e-9)
        assertEquals(1e-6 * sqrt(1_000.0 / 999.0), record.heldValueSd, 1e-12)
    }

    @Test
    fun reset_forgets_both() {
        val analyser = HoldAnalyser(plank)
        repeat(20) { analyser.step(it * 33.0, 170.0) }
        analyser.reset()
        repeat(20) { analyser.step(1_000.0 + it * 33.0, 178.0) }
        assertEquals(178.0, analyser.finish().meanHeldValue, 1e-12)
    }
}
