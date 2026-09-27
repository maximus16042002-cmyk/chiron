package com.kinesis.core.analysis.calibration

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.JsonText
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.BilateralStatistic
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic

/**
 * A baseline as JSON, and back - DECISIONS.md P3-22.
 *
 * The one serialised form of a baseline: the facade's `computeBaseline` returns
 * it, and the app stores it in Room. The two rules storage has kept since P3-12
 * hold here too:
 *
 * - **Left and right are separate fields**, all the way down. No field holds a
 *   value from both sides.
 * - **Not measured survives.** `NaN` is written as `null` and read back as
 *   `NaN`, so the row's far arm reloads as unmeasured, never as a measurement
 *   of zero.
 *
 * Numbers are written with Kotlin's `Double.toString`, which round-trips
 * exactly on every target, so a stored baseline reloads bit for bit.
 *
 * Post-rep path: once per calibration, and once per read of stored baselines.
 */
object BaselineJson {

    private const val REPETITION = "repetition"
    private const val HOLD = "hold"

    fun write(baseline: CalibrationBaseline): String = when (baseline) {
        is RepetitionBaseline -> EngineJson.obj {
            identity(REPETITION, baseline)
            raw("rangeOfMotion", bilateral(baseline.rangeOfMotion))
            raw("peakValue", bilateral(baseline.peakValue))
            raw("troughValue", bilateral(baseline.troughValue))
            raw("descentMs", timing(baseline.descentMs))
            raw("ascentMs", timing(baseline.ascentMs))
            raw("asymmetry", asymmetry(baseline.asymmetry))
            array("meanCurveLeft", baseline.meanCurveLeft)
            array("meanCurveRight", baseline.meanCurveRight)
            raw("curveSpread", bilateral(baseline.curveSpread))
        }

        is HoldBaseline -> EngineJson.obj {
            identity(HOLD, baseline)
            field("targetValue", baseline.targetValue)
            field("meanHeldValue", baseline.meanHeldValue)
            field("meanAbsDeviation", baseline.meanAbsDeviation)
            field("sway", baseline.sway)
            field("heldMs", baseline.heldMs)
            field("breakCount", baseline.breakCount)
            field("coverage", baseline.coverage)
        }
    }

    /**
     * Reads what [write] wrote. Anything else - malformed, a missing field, an
     * unknown kind - is an [IllegalArgumentException], never a baseline filled
     * in with defaults.
     */
    fun read(json: String): CalibrationBaseline {
        val root = JsonText.obj(JsonText.parse(json), "a baseline")
        return when (val kind = JsonText.string(root, "kind")) {
            REPETITION -> RepetitionBaseline(
                calibrationId = JsonText.string(root, "calibrationId"),
                estimatorVersion = JsonText.string(root, "estimatorVersion"),
                createdAtEpochMs = JsonText.double(root, "createdAtEpochMs"),
                sourceSessionId = JsonText.stringOrNull(root, "sourceSessionId"),
                rangeOfMotion = readBilateral(root, "rangeOfMotion"),
                peakValue = readBilateral(root, "peakValue"),
                troughValue = readBilateral(root, "troughValue"),
                descentMs = readTiming(root, "descentMs"),
                ascentMs = readTiming(root, "ascentMs"),
                asymmetry = readAsymmetry(JsonText.obj(JsonText.field(root, "asymmetry"), "asymmetry")),
                meanCurveLeft = JsonText.doubles(root, "meanCurveLeft"),
                meanCurveRight = JsonText.doubles(root, "meanCurveRight"),
                curveSpread = readBilateral(root, "curveSpread"),
            )

            HOLD -> HoldBaseline(
                calibrationId = JsonText.string(root, "calibrationId"),
                estimatorVersion = JsonText.string(root, "estimatorVersion"),
                createdAtEpochMs = JsonText.double(root, "createdAtEpochMs"),
                sourceSessionId = JsonText.stringOrNull(root, "sourceSessionId"),
                targetValue = JsonText.double(root, "targetValue"),
                meanHeldValue = JsonText.double(root, "meanHeldValue"),
                meanAbsDeviation = JsonText.double(root, "meanAbsDeviation"),
                sway = JsonText.double(root, "sway"),
                heldMs = JsonText.double(root, "heldMs"),
                breakCount = JsonText.int(root, "breakCount"),
                coverage = JsonText.double(root, "coverage"),
            )

            else -> throw IllegalArgumentException("unknown baseline kind \"$kind\"")
        }
    }

    // --- Writing ----------------------------------------------------------------------

    private fun EngineJson.Writer.identity(kind: String, baseline: CalibrationBaseline) {
        field("kind", kind)
        field("calibrationId", baseline.calibrationId)
        field("estimatorVersion", baseline.estimatorVersion)
        field("createdAtEpochMs", baseline.createdAtEpochMs)
        field("sourceSessionId", baseline.sourceSessionId)
    }

    internal fun writeStatistic(value: RobustStatistic): String = EngineJson.obj {
        field("median", value.median)
        field("iqr", value.interquartileRange)
        field("count", value.count)
    }

    private fun bilateral(value: BilateralStatistic): String = EngineJson.obj {
        raw("left", writeStatistic(value.left))
        raw("right", writeStatistic(value.right))
    }

    private fun timing(timing: PhaseTimingBaseline): String = EngineJson.obj {
        raw("perSide", bilateral(timing.perSide))
        raw("perRepetition", writeStatistic(timing.perRepetition))
    }

    private fun asymmetry(asymmetry: AsymmetryBaseline): String = when (asymmetry) {
        is AsymmetryBaseline.Paired -> EngineJson.obj {
            field("kind", "paired")
            raw("leftMinusRight", writeStatistic(asymmetry.leftMinusRight))
        }
        is AsymmetryBaseline.BetweenSideMedians -> EngineJson.obj {
            field("kind", "betweenSideMedians")
            field("leftMinusRight", asymmetry.leftMinusRight)
        }
        AsymmetryBaseline.NotMeasurable -> EngineJson.obj { field("kind", "notMeasurable") }
    }

    // --- Reading ----------------------------------------------------------------------

    private fun readStatistic(value: Any?, what: String): RobustStatistic {
        val map = JsonText.obj(value, what)
        return RobustStatistic(
            median = JsonText.double(map, "median"),
            interquartileRange = JsonText.double(map, "iqr"),
            count = JsonText.int(map, "count"),
        )
    }

    private fun readBilateral(parent: Map<String, Any?>, key: String): BilateralStatistic {
        val map = JsonText.obj(JsonText.field(parent, key), key)
        return BilateralStatistic(
            left = readStatistic(JsonText.field(map, "left"), "$key.left"),
            right = readStatistic(JsonText.field(map, "right"), "$key.right"),
        )
    }

    private fun readTiming(parent: Map<String, Any?>, key: String): PhaseTimingBaseline {
        val map = JsonText.obj(JsonText.field(parent, key), key)
        return PhaseTimingBaseline(
            perSide = readBilateral(map, "perSide"),
            perRepetition = readStatistic(JsonText.field(map, "perRepetition"), "$key.perRepetition"),
        )
    }

    private fun readAsymmetry(map: Map<String, Any?>): AsymmetryBaseline = when (val kind = JsonText.string(map, "kind")) {
        "paired" -> AsymmetryBaseline.Paired(readStatistic(JsonText.field(map, "leftMinusRight"), "asymmetry"))
        "betweenSideMedians" -> AsymmetryBaseline.BetweenSideMedians(JsonText.double(map, "leftMinusRight"))
        "notMeasurable" -> AsymmetryBaseline.NotMeasurable
        else -> throw IllegalArgumentException("unknown asymmetry kind \"$kind\"")
    }
}
