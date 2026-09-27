package com.kinesis.sync

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord

/**
 * A session as a Firestore document - DECISIONS.md P3-14.
 *
 * Pure functions, separated from the Firestore client so the part that can be
 * wrong is testable on the JVM - the same split P3-12 drew for Room's mapping
 * and P3-11 drew for Health Connect.
 *
 * ### The two rules carried over from P3-12
 *
 * **Left and right never share a key**, for the same reason they never share a
 * column: a single averaged value would be the prototype's `(left + right) / 2f`
 * made durable, this time in someone else's datacentre.
 *
 * **`NaN` survives**, written as `null` and read back as `NaN`. `NaN` is the
 * sentinel for "not measured" - the idle arm of an alternating repetition, the
 * unseen arm of the near-side-only row - and a round trip that turns it into
 * `0.0` writes a false measurement that then syncs to every other device.
 */
object SessionDocument {

    fun toMap(session: SessionRecord): Map<String, Any?> = mapOf(
        "sessionId" to session.sessionId,
        "startedAtEpochMs" to session.startedAtEpochMs,
        "utcOffsetMinutes" to session.utcOffsetMinutes.toLong(),
        "schema" to SCHEMA_VERSION,
        "sets" to session.sets.map(::setToMap),
    )

    @Suppress("UNCHECKED_CAST")
    fun fromMap(data: Map<String, Any?>): SessionRecord? {
        val sessionId = data["sessionId"] as? String ?: return null
        val startedAt = (data["startedAtEpochMs"] as? Number)?.toDouble() ?: return null
        val offset = (data["utcOffsetMinutes"] as? Number)?.toInt() ?: return null
        val sets = (data["sets"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(::setFromMap)
        return SessionRecord(sessionId, startedAt, offset, sets)
    }

    // --- Sets ---------------------------------------------------------------------

    private fun setToMap(set: SetRecord): Map<String, Any?> = when (set) {
        is RepetitionSetRecord -> mapOf(
            "kind" to KIND_REPETITION,
            "exerciseId" to set.exerciseId,
            "startedAtEpochMs" to set.startedAtEpochMs,
            "reps" to set.reps.map(::repToMap),
        )

        is HoldSetRecord -> mapOf(
            "kind" to KIND_HOLD,
            "exerciseId" to set.exerciseId,
            "startedAtEpochMs" to set.startedAtEpochMs,
            "holds" to set.holds.map(::holdToMap),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun setFromMap(data: Map<String, Any?>): SetRecord? {
        val exerciseId = data["exerciseId"] as? String ?: return null
        val startedAt = (data["startedAtEpochMs"] as? Number)?.toDouble() ?: return null
        return if (data["kind"] == KIND_HOLD) {
            HoldSetRecord(
                exerciseId, startedAt,
                (data["holds"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(::holdFromMap),
            )
        } else {
            RepetitionSetRecord(
                exerciseId, startedAt,
                (data["reps"] as? List<Map<String, Any?>>).orEmpty().mapNotNull(::repFromMap),
            )
        }
    }

    // --- Repetitions and holds --------------------------------------------------------

    private fun repToMap(rep: RepRecord): Map<String, Any?> = mapOf(
        "repIndex" to rep.repIndex.toLong(),
        "attributedSide" to rep.attributedSide?.name,
        "startMs" to rep.startMs,
        "endMs" to rep.endMs,
        "validity" to rep.validity.name,
        "romLeft" to nullIfNaN(rep.rangeOfMotion.left),
        "romRight" to nullIfNaN(rep.rangeOfMotion.right),
        "peakLeft" to nullIfNaN(rep.peakValue.left),
        "peakRight" to nullIfNaN(rep.peakValue.right),
        "troughLeft" to nullIfNaN(rep.troughValue.left),
        "troughRight" to nullIfNaN(rep.troughValue.right),
        "descentMs" to nullIfNaN(rep.descentMs),
        "ascentMs" to nullIfNaN(rep.ascentMs),
        "resampledLeft" to rep.resampledLeft.map(::nullIfNaN),
        "resampledRight" to rep.resampledRight.map(::nullIfNaN),
    )

    @Suppress("UNCHECKED_CAST")
    private fun repFromMap(data: Map<String, Any?>): RepRecord? {
        val index = (data["repIndex"] as? Number)?.toInt() ?: return null
        val validity = (data["validity"] as? String)?.let {
            runCatching { RepValidity.valueOf(it) }.getOrNull()
        } ?: return null

        return RepRecord(
            repIndex = index,
            attributedSide = (data["attributedSide"] as? String)?.let {
                runCatching { Side.valueOf(it) }.getOrNull()
            },
            startMs = number(data["startMs"]),
            endMs = number(data["endMs"]),
            validity = validity,
            rangeOfMotion = BilateralSignal(number(data["romLeft"]), number(data["romRight"])),
            peakValue = BilateralSignal(number(data["peakLeft"]), number(data["peakRight"])),
            troughValue = BilateralSignal(number(data["troughLeft"]), number(data["troughRight"])),
            descentMs = number(data["descentMs"]),
            ascentMs = number(data["ascentMs"]),
            resampledLeft = doubles(data["resampledLeft"]),
            resampledRight = doubles(data["resampledRight"]),
        )
    }

    private fun holdToMap(hold: HoldRecord): Map<String, Any?> = mapOf(
        "startMs" to hold.startMs,
        "endMs" to hold.endMs,
        "validity" to hold.validity.name,
        "totalHoldMs" to hold.totalHoldMs,
        "longestUnbrokenMs" to hold.longestUnbrokenMs,
        "breakCount" to hold.breakCount.toLong(),
        "meanAbsDeviation" to nullIfNaN(hold.meanAbsDeviation),
        "maxAbsDeviation" to nullIfNaN(hold.maxAbsDeviation),
        "coverage" to hold.coverage,
    ) + measuredOnly("meanHeldValue", hold.meanHeldValue) + measuredOnly("heldValueSd", hold.heldValueSd)

    private fun holdFromMap(data: Map<String, Any?>): HoldRecord? {
        val validity = (data["validity"] as? String)?.let {
            runCatching { HoldValidity.valueOf(it) }.getOrNull()
        } ?: return null
        return HoldRecord(
            startMs = number(data["startMs"]),
            endMs = number(data["endMs"]),
            validity = validity,
            totalHoldMs = number(data["totalHoldMs"]),
            longestUnbrokenMs = number(data["longestUnbrokenMs"]),
            breakCount = (data["breakCount"] as? Number)?.toInt() ?: 0,
            meanAbsDeviation = number(data["meanAbsDeviation"]),
            maxAbsDeviation = number(data["maxAbsDeviation"]),
            coverage = number(data["coverage"]),
            // Absent from holds recorded before DECISIONS.md P3-22: unmeasured.
            meanHeldValue = number(data["meanHeldValue"]),
            heldValueSd = number(data["heldValueSd"]),
        )
    }

    /**
     * A key written only when its value was measured - DECISIONS.md P3-22.
     *
     * The two hold fields P3-22 added are left out when unmeasured, rather than
     * written as `null` like every older field. A hold recorded before P3-22 has
     * neither, so its document stays byte for byte what an older client
     * uploaded. That matters for one path: the rules accept a re-upload only if
     * it is identical to the stored document (P3-19), and a retry arriving after
     * an upgrade must not differ by two `null` keys and be refused forever.
     */
    private fun measuredOnly(key: String, value: Double): Map<String, Any?> =
        if (value.isNaN()) emptyMap() else mapOf(key to value)

    // --- The conversion that carries the risk -------------------------------------------

    private fun nullIfNaN(value: Double): Double? = if (value.isNaN()) null else value

    /** A missing or null value reloads as unmeasured, never as zero. */
    private fun number(value: Any?): Double = (value as? Number)?.toDouble() ?: Double.NaN

    @Suppress("UNCHECKED_CAST")
    private fun doubles(value: Any?): DoubleArray {
        val list = value as? List<Any?> ?: return DoubleArray(0)
        return DoubleArray(list.size) { number(list[it]) }
    }

    const val KIND_REPETITION: String = "REPETITION"
    const val KIND_HOLD: String = "HOLD"

    /** Bumped if the shape ever changes, so an old client can refuse politely. */
    const val SCHEMA_VERSION: Long = 1
}
