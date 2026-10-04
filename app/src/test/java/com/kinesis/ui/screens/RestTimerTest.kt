package com.kinesis.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rest timer's arithmetic - DECISIONS.md P3-49. Read from the clock, so a late tick never drifts it. */
class RestTimerTest {

    private val start = 1_000_000L

    @Test
    fun the_full_length_at_the_start_and_rounded_up_until_a_second_has_passed() {
        assertEquals(60, RestTimer.remainingSeconds(start, start, 60))
        assertEquals(60, RestTimer.remainingSeconds(start, start + 500, 60))
        assertEquals(59, RestTimer.remainingSeconds(start, start + 1_000, 60))
    }

    @Test
    fun zero_at_the_end_and_after_it() {
        assertEquals(0, RestTimer.remainingSeconds(start, start + 60_000, 60))
        assertEquals(0, RestTimer.remainingSeconds(start, start + 90_000, 60))
    }

    /** A clock set back mid-rest shows the full length, never more. */
    @Test
    fun never_more_than_the_length() {
        assertEquals(60, RestTimer.remainingSeconds(start, start - 5_000, 60))
    }
}
