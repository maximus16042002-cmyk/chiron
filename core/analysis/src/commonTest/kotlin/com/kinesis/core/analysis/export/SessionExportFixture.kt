package com.kinesis.core.analysis.export

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.BilateralStatistic
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord

/**
 * A fixed history for the export's golden file - DECISIONS.md P3-25.
 *
 * Hand-built rather than taken from the engine, so every value is chosen and the
 * golden file can be checked by eye. It holds every shape the export must carry:
 * an alternating set whose idle arm is unmeasured, a simultaneous set with a
 * half-seen repetition, a set that recorded nothing, both hold kinds, a hold from
 * before P3-22's two fields, a negative UTC offset, and one baseline of each
 * kind, one with a side that was never measured.
 *
 * Every number is exactly representable or short, so its text is the same on
 * every runtime - the golden file is compared byte for byte.
 */
internal object SessionExportFixture {

    const val EXPORTED_AT: Double = 1_790_600_000_000.0

    private const val POINTS = 64

    /** A curl's elbow curve: straight, bending to [far], straightening again. */
    private fun curve(top: Double, far: Double): DoubleArray = DoubleArray(POINTS) { i ->
        val half = POINTS / 2
        val step = (top - far) / half
        if (i < half) top - step * i else far + step * (i - half)
    }

    private fun unmeasured(): DoubleArray = DoubleArray(POINTS) { Double.NaN }

    private fun curl(index: Int, side: Side, validity: RepValidity, startMs: Double, top: Double, far: Double, descentMs: Double, ascentMs: Double): RepRecord {
        val measured = BilateralSignal(if (side == Side.LEFT) 1.0 else Double.NaN, if (side == Side.RIGHT) 1.0 else Double.NaN)
        fun onSide(value: Double) = BilateralSignal(value * measured.left, value * measured.right)
        return RepRecord(
            repIndex = index,
            attributedSide = side,
            startMs = startMs,
            endMs = startMs + descentMs + (if (ascentMs.isNaN()) 0.0 else ascentMs) + 250.0,
            validity = validity,
            rangeOfMotion = onSide(top - far),
            peakValue = onSide(top),
            troughValue = onSide(far),
            descentMs = descentMs,
            ascentMs = ascentMs,
            resampledLeft = if (side == Side.LEFT) curve(top, far) else unmeasured(),
            resampledRight = if (side == Side.RIGHT) curve(top, far) else unmeasured(),
        )
    }

    private val curls = RepetitionSetRecord(
        exerciseId = "bicep_curl",
        startedAtEpochMs = 1_790_537_445_250.0,
        reps = listOf(
            curl(1, Side.LEFT, RepValidity.VALID, 5_004_340.5, 160.25, 47.75, 1_140.0, 1_500.0),
            curl(2, Side.RIGHT, RepValidity.VALID, 5_011_060.0, 168.5, 33.125, 1_470.0, 1_440.0),
            curl(3, Side.RIGHT, RepValidity.ABORTED, 5_018_570.25, 158.75, 73.75, 900.0, Double.NaN),
            curl(4, Side.LEFT, RepValidity.INCOMPLETE, 5_025_000.0, 173.0, 18.75, 6_950.0, 12_800.0),
        ),
    )

    private val squats = RepetitionSetRecord(
        exerciseId = "squat",
        startedAtEpochMs = 1_790_537_630_000.0,
        reps = listOf(
            RepRecord(
                repIndex = 1,
                attributedSide = null,
                startMs = 5_190_000.0,
                endMs = 5_194_312.5,
                validity = RepValidity.VALID,
                rangeOfMotion = BilateralSignal(85.75, 101.25),
                peakValue = BilateralSignal(179.5, 179.25),
                troughValue = BilateralSignal(93.75, 78.0),
                descentMs = 2_125.0,
                ascentMs = 1_937.5,
                resampledLeft = curve(179.5, 93.75),
                resampledRight = curve(179.25, 78.0),
            ),
            // Half-seen: the right knee unmeasured throughout.
            RepRecord(
                repIndex = 2,
                attributedSide = null,
                startMs = 5_200_400.0,
                endMs = 5_200_575.0,
                validity = RepValidity.INSUFFICIENT_DATA,
                rangeOfMotion = BilateralSignal(24.5, Double.NaN),
                peakValue = BilateralSignal(178.5, Double.NaN),
                troughValue = BilateralSignal(154.0, Double.NaN),
                descentMs = Double.NaN,
                ascentMs = Double.NaN,
                resampledLeft = curve(178.5, 154.0),
                resampledRight = unmeasured(),
            ),
        ),
    )

    /** Started and ended with nothing recorded: a set with zero repetitions is still a set. */
    private val emptySquats = RepetitionSetRecord("squat", 1_790_537_700_000.0, emptyList())

    private val plank = HoldSetRecord(
        exerciseId = "plank",
        startedAtEpochMs = 1_790_537_800_000.0,
        holds = listOf(
            HoldRecord(
                startMs = 5_380_000.0,
                endMs = 5_415_500.0,
                validity = HoldValidity.VALID,
                totalHoldMs = 33_250.0,
                longestUnbrokenMs = 20_125.0,
                breakCount = 1,
                meanAbsDeviation = 3.5,
                maxAbsDeviation = 12.25,
                coverage = 0.96875,
                meanHeldValue = 176.5,
                heldValueSd = 1.25,
            ),
        ),
    )

    private val session1 = SessionRecord(
        sessionId = "3f1c2a9e-0b7d-4c55-9a64-1e2d3c4b5a69",
        startedAtEpochMs = 1_790_537_401_000.0,
        utcOffsetMinutes = 330,
        sets = listOf(curls, squats, emptySquats, plank),
    )

    /** Recorded before P3-22: the two hold fields it added are unmeasured, never zero. */
    private val oldWallSit = HoldSetRecord(
        exerciseId = "wall_sit",
        startedAtEpochMs = 1_789_900_000_000.0,
        holds = listOf(
            HoldRecord(
                startMs = 120_000.0,
                endMs = 128_000.0,
                validity = HoldValidity.TOO_SHORT,
                totalHoldMs = 6_500.0,
                longestUnbrokenMs = 6_500.0,
                breakCount = 0,
                meanAbsDeviation = Double.NaN,
                maxAbsDeviation = Double.NaN,
                coverage = 0.8125,
                meanHeldValue = Double.NaN,
                heldValueSd = Double.NaN,
            ),
        ),
    )

    private val session2 = SessionRecord(
        sessionId = "9d8e7f60-1a2b-4c3d-8e9f-0a1b2c3d4e5f",
        startedAtEpochMs = 1_789_899_990_000.0,
        utcOffsetMinutes = -300,
        sets = listOf(oldWallSit),
    )

    val sessions: List<SessionRecord> = listOf(session1, session2)

    private fun stat(median: Double, iqr: Double, count: Int) = RobustStatistic(median, iqr, count)

    /** Calibrated on the left arm only: the right is not measured, never zero. */
    private val curlBaseline = RepetitionBaseline(
        calibrationId = "bicep_curl",
        estimatorVersion = "p6-1",
        createdAtEpochMs = 1_790_537_450_000.0,
        sourceSessionId = "3f1c2a9e-0b7d-4c55-9a64-1e2d3c4b5a69",
        rangeOfMotion = BilateralStatistic(stat(112.5, 6.25, 5), RobustStatistic.NOT_MEASURED),
        peakValue = BilateralStatistic(stat(160.25, 2.5, 5), RobustStatistic.NOT_MEASURED),
        troughValue = BilateralStatistic(stat(47.75, 4.0, 5), RobustStatistic.NOT_MEASURED),
        descentMs = PhaseTimingBaseline(BilateralStatistic(stat(1_140.0, 120.0, 5), RobustStatistic.NOT_MEASURED), stat(1_140.0, 120.0, 5)),
        ascentMs = PhaseTimingBaseline(BilateralStatistic(stat(1_500.0, 200.0, 5), RobustStatistic.NOT_MEASURED), stat(1_500.0, 200.0, 5)),
        asymmetry = AsymmetryBaseline.NotMeasurable,
        meanCurveLeft = curve(160.25, 47.75),
        meanCurveRight = unmeasured(),
        curveSpread = BilateralStatistic(stat(2.5, 0.75, 5), RobustStatistic.NOT_MEASURED),
    )

    private val plankBaseline = HoldBaseline(
        calibrationId = "plank",
        estimatorVersion = "p6-1",
        createdAtEpochMs = 1_790_537_900_000.0,
        sourceSessionId = null,
        targetValue = 180.0,
        meanHeldValue = 176.5,
        meanAbsDeviation = 3.5,
        sway = 1.25,
        heldMs = 33_250.0,
        breakCount = 1,
        coverage = 0.96875,
    )

    val baselines: List<CalibrationBaseline> = listOf(curlBaseline, plankBaseline)
}
