package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldReading
import com.kinesis.core.domain.bilateral.BilateralSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the reduction P3-9 said a live hold tracker would need - CLAUDE.md
 * section 3, DECISIONS.md P3-9 and P3-16: "an explicit named reducer - never an
 * average, and never a silent fallback".
 */
class HoldReadingTest {

    private val target = 180.0

    @Test
    fun with_both_sides_measured_the_side_further_from_the_target_is_read() {
        assertEquals(165.0, HoldReading.furthestFromTarget(BilateralSignal(175.0, 165.0), target))
        assertEquals(160.0, HoldReading.furthestFromTarget(BilateralSignal(160.0, 178.0), target))
        // Either side of the target: distance is what counts, not direction.
        assertEquals(84.0, HoldReading.furthestFromTarget(BilateralSignal(84.0, 93.0), 90.0))
    }

    /** Never a value between the two sides - that would be an average by another name. */
    @Test
    fun the_reading_is_always_one_side_exactly() {
        val pairs = listOf(170.0 to 176.0, 150.0 to 190.0, 179.9 to 180.1, 120.0 to 120.0)
        for ((left, right) in pairs) {
            val read = HoldReading.furthestFromTarget(BilateralSignal(left, right), target)
            assertTrue(read == left || read == right, "read $read from $left / $right")
        }
    }

    @Test
    fun the_reading_does_not_depend_on_which_side_is_which() {
        val pairs = listOf(170.0 to 176.0, 150.0 to 190.0, 181.0 to 179.0)
        for ((a, b) in pairs) {
            assertEquals(
                HoldReading.furthestFromTarget(BilateralSignal(a, b), target),
                HoldReading.furthestFromTarget(BilateralSignal(b, a), target),
            )
        }
    }

    /**
     * One body line, one measurement of it: a side-on plank sees its near side
     * only, and that side's reading is the line's. P3-9 measured the two sides
     * agreeing to within a degree when both are visible.
     */
    @Test
    fun one_measured_side_is_read_as_the_line() {
        assertEquals(172.0, HoldReading.furthestFromTarget(BilateralSignal(172.0, Double.NaN), target))
        assertEquals(168.0, HoldReading.furthestFromTarget(BilateralSignal(Double.NaN, 168.0), target))
    }

    @Test
    fun nothing_measured_reads_as_nothing() {
        assertTrue(HoldReading.furthestFromTarget(BilateralSignal(Double.NaN, Double.NaN), target).isNaN())
    }
}
