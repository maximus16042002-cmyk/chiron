package com.kinesis.core.analysis.anomaly

import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import kotlin.math.PI
import kotlin.math.sin

/** Hand-built repetitions for Layer 4's tests - DECISIONS.md P3-34. */
internal object AnomalyTestReps {

    /** 0 at the start, 1 at the far position halfway, 0 again at the end: an ordinary repetition's shape. */
    fun sine(i: Int): Double = sin(PI * i / 63.0)

    /** Reaches the far position a quarter of the way through and holds it until three quarters: a stall at depth. */
    fun stall(i: Int): Double = when {
        i < 16 -> sin(PI / 2 * i / 16.0)
        i < 48 -> 1.0
        else -> sin(PI / 2 * (63 - i) / 15.0)
    }

    /** A curve from [peak] down by [rom] along [shape], or all NaN for a side that did not move. */
    fun curve(peak: Double, rom: Double, shape: (Int) -> Double, jitter: Double = 0.0, seed: Long = 0L): DoubleArray {
        if (rom.isNaN()) return DoubleArray(64) { Double.NaN }
        val rng = SplitMix64(seed)
        return DoubleArray(64) { peak - rom * (shape(it) + jitter * (rng.nextDouble() - 0.5)) }
    }

    fun rep(
        romLeft: Double,
        romRight: Double,
        descentMs: Double,
        ascentMs: Double,
        side: Side? = null,
        validity: RepValidity = RepValidity.VALID,
        peak: Double = 170.0,
        shape: (Int) -> Double = ::sine,
        jitter: Double = 0.0,
        seed: Long = 0L,
    ): RepRecord = RepRecord(
        repIndex = 1,
        attributedSide = side,
        startMs = 0.0,
        endMs = descentMs + ascentMs,
        validity = validity,
        rangeOfMotion = BilateralSignal(romLeft, romRight),
        peakValue = BilateralSignal(if (romLeft.isNaN()) Double.NaN else peak, if (romRight.isNaN()) Double.NaN else peak),
        troughValue = BilateralSignal(peak - romLeft, peak - romRight),
        descentMs = descentMs,
        ascentMs = ascentMs,
        resampledLeft = curve(peak, romLeft, shape, jitter, seed),
        resampledRight = curve(peak, romRight, shape, jitter, seed + 1),
    )

    /**
     * [count] squats in which depth and pace go together, as they often do: the deeper, the slower.
     * Ranges 80-100 degrees, lowering 1.0-1.4 s, rising 0.9-1.2 s.
     */
    fun deepIsSlowHistory(count: Int, seed: Long = 13L): List<RepRecord> {
        val rng = SplitMix64(seed)
        fun noise(size: Double) = size * (rng.nextDouble() - 0.5)
        return List(count) {
            val u = rng.nextDouble()
            val rom = 80.0 + 20.0 * u + noise(1.0)
            rep(
                romLeft = rom, romRight = rom - 2.0 + noise(1.0),
                descentMs = 1000.0 + 400.0 * u + noise(20.0), ascentMs = 900.0 + 300.0 * u + noise(20.0),
                jitter = 0.04, seed = seed * 1000 + it,
            )
        }
    }

    /** [count] ordinary squats, each a little different: ranges near 90, times near 1.2 and 1.0 s. */
    fun squatHistory(count: Int, seed: Long = 11L): List<RepRecord> {
        val rng = SplitMix64(seed)
        fun near(centre: Double, spread: Double) = centre + spread * (rng.nextDouble() - 0.5)
        return List(count) {
            rep(
                romLeft = near(90.0, 8.0), romRight = near(88.0, 8.0),
                descentMs = near(1200.0, 200.0), ascentMs = near(1000.0, 200.0),
                jitter = 0.04, seed = seed * 1000 + it,
            )
        }
    }
}
