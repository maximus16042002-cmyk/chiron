package com.kinesis.ui.live

import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attention pulse - DECISIONS.md P3-12 and P3-16.
 *
 * P3-12 gave the attention state three channels, one of them "the only pulse in
 * the overlay". The palette declared it and nothing drew it. The timing lives in
 * a pure function so it can be pinned here rather than looked at.
 */
class SkeletonPulseTest {

    private val samples = (0L until 3 * SkeletonColouring.PULSE_PERIOD_MS step 7L)

    @Test
    fun a_state_that_does_not_pulse_never_changes_width() {
        for (state in OverlayState.entries) {
            if (state.pulses) continue
            for (t in samples) {
                assertEquals(state.name + " at " + t, 1f, SkeletonColouring.strokeScale(state, t), 0f)
            }
        }
    }

    @Test
    fun attention_pulses_between_its_own_width_and_half_again() {
        val scales = samples.map { SkeletonColouring.strokeScale(OverlayState.ATTENTION, it) }
        val low = scales.min()
        val high = scales.max()

        // Never thinner than the width SkeletonPaletteLegibilityTest pins: the
        // pulse adds to that channel and never takes from it.
        assertTrue("pulsed below its base width: " + low, low >= 1f)
        assertEquals(1f, low, 0.01f)
        assertEquals(SkeletonColouring.PULSE_PEAK_STROKE_SCALE, high, 0.01f)
    }

    @Test
    fun the_pulse_repeats_every_period() {
        for (t in samples) {
            assertEquals(
                SkeletonColouring.strokeScale(OverlayState.ATTENTION, t),
                SkeletonColouring.strokeScale(OverlayState.ATTENTION, t + SkeletonColouring.PULSE_PERIOD_MS),
                1e-5f,
            )
        }
    }

    /** WCAG 2.3.1: nothing may flash more than three times a second. */
    @Test
    fun the_pulse_is_slower_than_three_per_second() {
        assertTrue(SkeletonColouring.PULSE_PERIOD_MS > 1000L / 3)
    }
}
