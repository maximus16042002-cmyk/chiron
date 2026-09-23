package com.kinesis.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/**
 * The offset a session is filed under - DECISIONS.md P3-10 and P3-16.
 *
 * `TimeZone.rawOffset` ignores daylight saving. Filing a summer session with it
 * puts anything recorded in the hour after midnight on the previous day, and
 * the streak with it. The recorder and the dashboard both read this instead.
 */
class UtcOffsetTest {

    @Test
    fun the_offset_includes_daylight_saving_when_it_is_in_force() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            assertEquals(60, utcOffsetMinutesAt(JULY_1_2026_NOON_UTC))
            assertEquals(0, utcOffsetMinutesAt(JANUARY_15_2026_NOON_UTC))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            assertEquals(330, utcOffsetMinutesAt(JULY_1_2026_NOON_UTC))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private companion object {
        const val JULY_1_2026_NOON_UTC = 1_782_907_200_000L
        const val JANUARY_15_2026_NOON_UTC = 1_768_478_400_000L
    }
}
