package com.kinesis.core.analysis.anomaly

/**
 * SplitMix64 (Steele, Lea and Flood, "Fast splittable pseudorandom number generators", OOPSLA
 * 2014; Vigna's reference form). Layer 4's only source of randomness - DECISIONS.md P3-34.
 *
 * Written here rather than taken from a platform, so the JVM, JS and Android draw the same
 * numbers from the same seed and grow the same forest. `Long` arithmetic wraps the same way on
 * every target, so nothing here depends on one.
 */
class SplitMix64(seed: Long) {

    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1): the top 53 bits, exactly representable. */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * DOUBLE_UNIT

    /** Uniform in [0, [bound]), for the small bounds a forest needs. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, got $bound" }
        return (nextDouble() * bound).toInt()
    }

    private companion object {
        val GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_1: Long = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_2: Long = 0x94D049BB133111EBuL.toLong()
        const val DOUBLE_UNIT: Double = 1.0 / 9007199254740992.0 // 2^-53
    }
}
