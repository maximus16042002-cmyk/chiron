package com.kinesis.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Phase B readout's MediaPipe latency lookup - DECISIONS.md P3-19. */
class HandOverTimesTest {

    @Test
    fun a_frame_is_found_by_its_timestamp() {
        val times = HandOverTimes(capacity = 4)
        times.record(100L, 1_000L)
        times.record(133L, 2_000L)
        assertEquals(1_000L, times.at(100L))
        assertEquals(2_000L, times.at(133L))
    }

    @Test
    fun a_frame_never_handed_over_is_unknown() {
        val times = HandOverTimes(capacity = 4)
        times.record(100L, 1_000L)
        assertEquals(HandOverTimes.UNKNOWN, times.at(166L))
    }

    /** A slot is reused only after [capacity] more frames; the oldest is then forgotten. */
    @Test
    fun the_oldest_frame_is_forgotten_once_the_ring_wraps() {
        val times = HandOverTimes(capacity = 4)
        for (i in 0 until 5) times.record(100L + i * 33, 1_000L * i)
        assertEquals(HandOverTimes.UNKNOWN, times.at(100L))
        assertEquals(4_000L, times.at(100L + 4 * 33))
    }
}
