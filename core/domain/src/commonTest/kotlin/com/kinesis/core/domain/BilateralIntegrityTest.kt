package com.kinesis.core.domain

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Permanent guard test — `CLAUDE.md` §3, §18 and `CONTRACTS.md` §1.
 *
 * This is the type-level half of bilateral integrity: it pins the shape of
 * [BilateralSignal] so that no scalar can fall out of it by accident. The
 * source-level half, which proves no *call site* averages left and right, is
 * `SharedCoreSourceGuardTest` in `:core:analysis`.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class BilateralIntegrityTest {

    // --- Left and right survive construction, separately ---------------------

    @Test
    fun sides_are_stored_and_read_back_independently() {
        val signal = BilateralSignal(left = 112.5, right = 97.25)
        assertEquals(112.5, signal.left)
        assertEquals(97.25, signal.right)
        assertEquals(112.5, signal.on(Side.LEFT))
        assertEquals(97.25, signal.on(Side.RIGHT))
    }

    /**
     * The single most important assertion in this file. If a future refactor
     * ever collapses [BilateralSignal] to something scalar-like, these two
     * become equal and this test fails.
     */
    @Test
    fun swapping_the_sides_produces_a_different_signal() {
        assertNotEquals(BilateralSignal(10.0, 20.0), BilateralSignal(20.0, 10.0))
    }

    /**
     * And the reason a mean must be spelled out at the call site: it cannot
     * tell those two signals apart. Anything that reads as "the value" would
     * silently do this.
     */
    @Test
    fun the_mean_cannot_distinguish_signals_that_a_bilateral_signal_can() {
        assertEquals(
            BilateralSignal(10.0, 20.0).meanExplicit(),
            BilateralSignal(20.0, 10.0).meanExplicit(),
        )
    }

    // --- Derived quantities stay bilateral -----------------------------------

    /**
     * `CONTRACTS.md` §1: "anything derived from a bilateral quantity is itself
     * bilateral until an explicit reducer is applied." These assertions are
     * enforced by the compiler — the test only has to compile for the contract
     * to hold. The runtime checks confirm the sides did not get crossed.
     */
    @Test
    fun map_returns_a_bilateral_signal_and_keeps_the_sides_apart() {
        val doubled: BilateralSignal = BilateralSignal(3.0, 4.0).map { it * 2.0 }
        assertEquals(6.0, doubled.left)
        assertEquals(8.0, doubled.right)
    }

    @Test
    fun zip_returns_a_bilateral_signal_and_pairs_like_with_like() {
        val rom = BilateralSignal(90.0, 80.0)
        val peak = BilateralSignal(100.0, 100.0)
        val deficit: BilateralSignal = rom.zip(peak) { measured, reference -> reference - measured }
        assertEquals(10.0, deficit.left)
        assertEquals(20.0, deficit.right)
    }

    // --- Every reduction to a scalar is named, and says what it means ---------

    @Test
    fun asymmetry_is_zero_for_a_symmetric_signal() {
        assertEquals(0.0, BilateralSignal(90.0, 90.0).asymmetry())
    }

    @Test
    fun asymmetry_scales_by_the_larger_magnitude() {
        // |100 - 80| / max(100, 80) = 0.2
        assertEquals(0.2, BilateralSignal(100.0, 80.0).asymmetry(), absoluteTolerance = 1e-12)
        // Direction-free: swapping the sides gives the same index.
        assertEquals(
            BilateralSignal(100.0, 80.0).asymmetry(),
            BilateralSignal(80.0, 100.0).asymmetry(),
            absoluteTolerance = 1e-12,
        )
    }

    @Test
    fun asymmetry_of_a_pair_of_zeroes_is_defined_as_zero_rather_than_nan() {
        assertEquals(0.0, BilateralSignal(0.0, 0.0).asymmetry())
    }

    @Test
    fun signed_difference_keeps_the_direction_that_asymmetry_discards() {
        assertEquals(20.0, BilateralSignal(100.0, 80.0).signedDifference())
        assertEquals(-20.0, BilateralSignal(80.0, 100.0).signedDifference())
    }

    @Test
    fun dominant_and_weaker_pick_by_magnitude_and_name_the_side() {
        val signal = BilateralSignal(left = 80.0, right = 100.0)
        assertEquals(100.0, signal.dominant())
        assertEquals(80.0, signal.weaker())
        assertEquals(Side.RIGHT, signal.dominantSide())
        assertEquals(Side.LEFT, signal.weakerSide())
    }

    @Test
    fun dominant_compares_magnitude_not_sign() {
        // A velocity of -12.0 is a larger movement than one of +5.0.
        val signal = BilateralSignal(left = -12.0, right = 5.0)
        assertEquals(-12.0, signal.dominant())
        assertEquals(Side.LEFT, signal.dominantSide())
    }

    @Test
    fun an_exact_tie_resolves_to_the_left_side_deterministically() {
        // Documented, not accidental: discrete outputs must match exactly
        // across platforms (CONTRACTS.md §6), so the tie-break is pinned.
        val signal = BilateralSignal(42.0, 42.0)
        assertEquals(Side.LEFT, signal.dominantSide())
        assertEquals(Side.RIGHT, signal.weakerSide())
    }

    @Test
    fun mean_is_available_but_only_under_a_name_that_admits_what_it_does() {
        assertEquals(90.0, BilateralSignal(100.0, 80.0).meanExplicit())
    }

    // --- The absence check ----------------------------------------------------

    /**
     * There is deliberately no `toDouble()`, no `value` property, and no
     * arithmetic operator on [BilateralSignal]. Those would each be an
     * implicit collapse. The compiler enforces their absence — the following
     * do not compile, and must never be made to:
     *
     * ```
     * val d: Double = BilateralSignal(1.0, 2.0)            // type mismatch
     * val d: Double = BilateralSignal(1.0, 2.0).toDouble() // unresolved reference
     * val d: Double = BilateralSignal(1.0, 2.0).value      // unresolved reference
     * val s = BilateralSignal(1.0, 2.0) + BilateralSignal(3.0, 4.0) // no plus operator
     * ```
     *
     * What this test can check at runtime is that destructuring — the one
     * built-in way a `data class` hands out its contents — hands out *both*
     * sides and never one standing in for the pair.
     */
    @Test
    fun destructuring_yields_both_sides_and_never_a_single_value() {
        val (left, right) = BilateralSignal(11.0, 22.0)
        assertEquals(11.0, left)
        assertEquals(22.0, right)
        assertTrue(left != right, "destructuring must not produce the same value twice")
    }
}
