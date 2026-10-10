package com.kinesis.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flash on a counted repetition - DECISIONS.md P3-76. */
class RepFlashTest {

    @Test
    fun full_at_the_moment_of_counting_fading_and_gone_after() {
        val at = 10_000L
        assertEquals(1f, RepFlash.intensity(at, at), 0f)
        var last = 1f
        for (t in 20L..RepFlash.DURATION_MS step 20L) {
            val now = RepFlash.intensity(at + t, at)
            assertTrue("not fading at $t ms: $now after $last", now < last)
            last = now
        }
        assertEquals(0f, RepFlash.intensity(at + RepFlash.DURATION_MS, at), 0f)
        assertEquals(0f, RepFlash.intensity(at + 60_000L, at), 0f)
    }

    @Test
    fun nothing_before_any_repetition_or_before_the_moment() {
        assertEquals(0f, RepFlash.intensity(10_000L, RepFlash.NEVER), 0f)
        assertEquals(0f, RepFlash.intensity(9_000L, 10_000L), 0f)
    }

    @Test
    fun progress_runs_from_nought_to_one_over_the_flash() {
        val at = 5_000L
        assertEquals(0f, RepFlash.progress(at, at), 0f)
        assertEquals(0.5f, RepFlash.progress(at + RepFlash.DURATION_MS / 2, at), 1e-6f)
        assertEquals(1f, RepFlash.progress(at + RepFlash.DURATION_MS * 3, at), 0f)
        assertEquals(1f, RepFlash.progress(at, RepFlash.NEVER), 0f)
    }
}
