package com.kinesis.core.analysis

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One text for one number, on every target - DECISIONS.md P3-25.
 *
 * The expected strings are what JavaScript's `Number.prototype.toString` writes
 * (ECMA-262 Number::toString). The same test runs on the JVM, where
 * `Double.toString` writes `100.0` and `1.7905376974852E12` for two of them, so
 * it only passes there if [JsonNumber] really re-lays the digits.
 */
class JsonNumberTest {

    @Test
    fun whole_numbers_have_no_trailing_point_zero() {
        assertEquals("100", JsonNumber.format(100.0))
        assertEquals("64", JsonNumber.format(64.0))
        assertEquals("-3", JsonNumber.format(-3.0))
    }

    @Test
    fun fractions_are_written_plainly() {
        assertEquals("0.5", JsonNumber.format(0.5))
        assertEquals("172.25", JsonNumber.format(172.25))
        assertEquals("-0.125", JsonNumber.format(-0.125))
        assertEquals("0.1", JsonNumber.format(0.1))
    }

    /** An epoch in milliseconds: the JVM writes it in E notation, JavaScript never does. */
    @Test
    fun epoch_milliseconds_are_written_without_an_exponent() {
        assertEquals("1790537697485.25", JsonNumber.format(1_790_537_697_485.25))
        assertEquals("1790537697485", JsonNumber.format(1_790_537_697_485.0))
        assertEquals("12345678", JsonNumber.format(12_345_678.0))
    }

    @Test
    fun small_numbers_switch_to_an_exponent_where_javascript_does() {
        assertEquals("0.00001", JsonNumber.format(1e-5))
        assertEquals("0.000001", JsonNumber.format(1e-6))
        assertEquals("1e-7", JsonNumber.format(1e-7))
        assertEquals("1.5e-9", JsonNumber.format(1.5e-9))
    }

    @Test
    fun large_numbers_switch_to_an_exponent_where_javascript_does() {
        assertEquals("100000000000000000000", JsonNumber.format(1e20))
        assertEquals("1e+21", JsonNumber.format(1e21))
        assertEquals("1.5e+300", JsonNumber.format(1.5e300))
    }

    /** JavaScript writes negative zero as `0`; so must every target. */
    @Test
    fun zero_is_zero_whatever_its_sign() {
        assertEquals("0", JsonNumber.format(0.0))
        assertEquals("0", JsonNumber.format(-0.0))
    }

    /** JSON has no NaN. "Not measured" is `null` - CONTRACTS.md section 5. */
    @Test
    fun values_json_cannot_hold_are_null() {
        assertEquals("null", JsonNumber.format(Double.NaN))
        assertEquals("null", JsonNumber.format(Double.POSITIVE_INFINITY))
        assertEquals("null", JsonNumber.format(Double.NEGATIVE_INFINITY))
    }

    /** Whatever the layout, the text must read back to exactly the same double. */
    @Test
    fun every_value_reads_back_exactly() {
        val random = Random(25)
        val values = List(2_000) {
            when (it % 4) {
                0 -> random.nextDouble() * 360.0
                1 -> random.nextDouble() * 2e12
                2 -> (random.nextDouble() - 0.5) * 1e-8
                else -> random.nextDouble() * 1e25
            }
        } + listOf(Double.MIN_VALUE, Double.MAX_VALUE, 5e-324, 2.2250738585072014E-308)
        for (value in values) {
            val text = JsonNumber.format(value)
            assertEquals(value, text.toDouble(), "$value written as $text")
        }
    }
}
