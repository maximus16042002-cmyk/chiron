package com.kinesis.core.analysis.calibration

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.sin

/**
 * Completed records with known values, for the calibration tests. Built by hand
 * rather than through the tracker, so each test states exactly the numbers the
 * baseline must be computed from.
 */
internal object CalibrationFixtures {

    /**
     * The calibration policy as it was until DECISIONS.md P3-83 (5 to 8 repetitions a side), for the
     * tests whose subject is not the policy - the JSON round trip, the deviation rules, the
     * post-repetition comparison. They build a baseline from a handful of synthetic repetitions with
     * hand-computed medians, and the numbers they assert would only move if those sets grew. The
     * shipped policy has its own tests in `CalibrationTest`.
     */
    val SMALL_SAMPLE_POLICY: CalibrationPolicy = CalibrationPolicy(
        minValidRepsPerSide = 5,
        maxValidRepsPerSide = 8,
        minHoldMs = 10_000.0,
        version = "p6-1",
    )

    const val POINTS = 64
    const val TOP_LEFT = 165.0
    const val TOP_RIGHT = 163.0

    /** A down-and-up repetition curve: [top] at both ends, [top] - [rom] in the middle. */
    fun curve(top: Double, rom: Double): DoubleArray =
        DoubleArray(POINTS) { i -> top - rom * sin(PI * i / (POINTS - 1)) }

    fun blank(): DoubleArray = DoubleArray(POINTS) { Double.NaN }

    /** Both sides measured together - a squat or a push-up. */
    fun paired(
        index: Int,
        romLeft: Double,
        romRight: Double,
        validity: RepValidity = RepValidity.VALID,
        descentMs: Double = 1_000.0,
        ascentMs: Double = 800.0,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = index * 3_000.0,
        endMs = index * 3_000.0 + descentMs + ascentMs,
        validity = validity,
        rangeOfMotion = BilateralSignal(romLeft, romRight),
        peakValue = BilateralSignal(TOP_LEFT, TOP_RIGHT),
        troughValue = BilateralSignal(TOP_LEFT - romLeft, TOP_RIGHT - romRight),
        descentMs = descentMs,
        ascentMs = ascentMs,
        resampledLeft = curve(TOP_LEFT, romLeft),
        resampledRight = curve(TOP_RIGHT, romRight),
    )

    /**
     * One side measured: an alternating curl when [attributed] names the side, a
     * near-side-only row when it is `null`.
     */
    fun oneSided(
        index: Int,
        side: Side,
        rom: Double,
        attributed: Side? = side,
        validity: RepValidity = RepValidity.VALID,
        descentMs: Double = 900.0,
        ascentMs: Double = 700.0,
        top: Double = 150.0,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = attributed,
        startMs = index * 2_000.0,
        endMs = index * 2_000.0 + descentMs + ascentMs,
        validity = validity,
        rangeOfMotion = sided(side, rom),
        peakValue = sided(side, top),
        troughValue = sided(side, top - rom),
        descentMs = descentMs,
        ascentMs = ascentMs,
        resampledLeft = if (side == Side.LEFT) curve(top, rom) else blank(),
        resampledRight = if (side == Side.RIGHT) curve(top, rom) else blank(),
    )

    private fun sided(side: Side, value: Double) = BilateralSignal(
        left = if (side == Side.LEFT) value else Double.NaN,
        right = if (side == Side.RIGHT) value else Double.NaN,
    )

    fun hold(
        validity: HoldValidity = HoldValidity.VALID,
        heldMs: Double = 20_000.0,
        meanHeldValue: Double = 172.0,
        heldValueSd: Double = 2.5,
        meanAbsDeviation: Double = 8.0,
        breakCount: Int = 0,
        coverage: Double = 0.97,
    ): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = heldMs + 2_000.0,
        validity = validity,
        totalHoldMs = heldMs,
        longestUnbrokenMs = heldMs,
        breakCount = breakCount,
        meanAbsDeviation = meanAbsDeviation,
        maxAbsDeviation = meanAbsDeviation + 5.0,
        coverage = coverage,
        meanHeldValue = meanHeldValue,
        heldValueSd = heldValueSd,
    )
}
