package com.kinesis.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trend chart's axis - DECISIONS.md P3-51: round ticks, about four, covering every value. */
class NiceAxisTest {

    @Test
    fun ranges_of_motion_get_fifteen_degree_ticks() {
        val axis = NiceAxis.of(62.0, 118.0)
        assertEquals(listOf(60.0, 75.0, 90.0, 105.0, 120.0), axis.ticks)
    }

    @Test
    fun small_counts_get_steps_of_five_from_zero() {
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0), NiceAxis.of(0.0, 12.0).ticks)
    }

    @Test
    fun every_value_is_inside_and_a_flat_series_still_has_a_span() {
        for ((low, high) in listOf(3.0 to 177.0, 90.0 to 90.0, 0.0 to 0.0, 41.5 to 43.2)) {
            val axis = NiceAxis.of(low, high)
            assertTrue("$low..$high in ${axis.ticks}", axis.min <= low && axis.max >= high && axis.max > axis.min)
            assertTrue("${axis.ticks} has ${axis.ticks.size} ticks", axis.ticks.size in 2..6)
        }
    }
}
