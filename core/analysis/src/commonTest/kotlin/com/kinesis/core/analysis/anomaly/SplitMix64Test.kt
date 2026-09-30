package com.kinesis.core.analysis.anomaly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The seeded generator behind Layer 4's forest - DECISIONS.md P3-34.
 *
 * Identical on every target, or JVM, JS and Android would grow different trees from the same
 * history. The expected values are SplitMix64's reference outputs, computed independently in
 * Python.
 */
class SplitMix64Test {

    @Test
    fun the_reference_sequence_for_seed_1234567() {
        val rng = SplitMix64(1234567L)
        val expected = listOf(
            6457827717110365317uL, 3203168211198807973uL, 9817491932198370423uL,
            4593380528125082431uL, 16408922859458223821uL,
        )
        for (value in expected) assertEquals(value, rng.nextLong().toULong())
    }

    @Test
    fun a_double_takes_the_top_53_bits() {
        val rng = SplitMix64(42L)
        assertEquals(0.7415648787718233, rng.nextDouble())
        assertEquals(0.1599103928769201, rng.nextDouble())
        assertEquals(0.27860113025513866, rng.nextDouble())
    }

    @Test
    fun an_index_is_spread_evenly_below_its_bound_and_never_reaches_it() {
        val rng = SplitMix64(7L)
        val counts = IntArray(5)
        repeat(5000) { counts[rng.nextInt(5)]++ }
        for (count in counts) assertTrue(count in 850..1150, "counts " + counts.toList())
    }

    @Test
    fun the_same_seed_repeats_and_another_does_not() {
        val a = SplitMix64(99L)
        val b = SplitMix64(99L)
        val c = SplitMix64(100L)
        val first = a.nextLong()
        assertEquals(first, b.nextLong())
        assertNotEquals(first, c.nextLong())
    }
}
