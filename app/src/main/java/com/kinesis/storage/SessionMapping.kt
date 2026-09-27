package com.kinesis.storage

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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts between domain records and stored rows - DECISIONS.md P3-12.
 *
 * Pure functions, deliberately separated from Room so the part that can actually
 * be wrong is testable on the JVM without a device, an emulator or a database.
 * That is the same split Batch 7 drew for Health Connect: test the mapping,
 * flag the driver for device work.
 *
 * ### The two things that can be wrong here
 *
 * **Losing a side.** Every bilateral value is written as two columns and read
 * back as two. Nothing in this file calls a reducer, and
 * `SessionMappingTest` fails if anything starts to.
 *
 * **Losing a `NaN`.** SQLite cannot store one in a REAL column. `NaN` is the
 * sentinel that says "this was not measured" - the idle arm of an alternating
 * repetition, the invisible arm of a near-side-only row (P3-8) - so a round trip
 * that turns it into `0.0` converts "we did not see this" into "we measured
 * zero", which is a false measurement in the database rather than a display bug.
 * `NaN` is written as `NULL` and read back as `NaN`, on every path, including
 * inside the resampled arrays.
 */
object SessionMapping {

    // --- Domain -> rows ------------------------------------------------------------

    fun toEntities(session: SessionRecord): StoredSession {
        val sets = ArrayList<StoredSet>(session.sets.size)
        session.sets.forEachIndexed { index, set ->
            sets += when (set) {
                is RepetitionSetRecord -> StoredSet(
                    set = SetEntity(
                        sessionId = session.sessionId,
                        exerciseId = set.exerciseId,
                        startedAtEpochMs = set.startedAtEpochMs,
                        kind = KIND_REPETITION,
                        orderInSession = index,
                    ),
                    reps = set.reps.map(::toEntity),
                    holds = emptyList(),
                )

                is HoldSetRecord -> StoredSet(
                    set = SetEntity(
                        sessionId = session.sessionId,
                        exerciseId = set.exerciseId,
                        startedAtEpochMs = set.startedAtEpochMs,
                        kind = KIND_HOLD,
                        orderInSession = index,
                    ),
                    reps = emptyList(),
                    holds = set.holds.map(::toEntity),
                )
            }
        }
        return StoredSession(
            session = SessionEntity(
                sessionId = session.sessionId,
                startedAtEpochMs = session.startedAtEpochMs,
                utcOffsetMinutes = session.utcOffsetMinutes,
            ),
            sets = sets,
        )
    }

    private fun toEntity(rep: RepRecord): RepEntity = RepEntity(
        setId = 0,
        repIndex = rep.repIndex,
        attributedSide = rep.attributedSide?.name,
        startMs = rep.startMs,
        endMs = rep.endMs,
        validity = rep.validity.name,
        rangeOfMotion = columns(rep.rangeOfMotion),
        peakValue = columns(rep.peakValue),
        troughValue = columns(rep.troughValue),
        descentMs = nullIfNaN(rep.descentMs),
        ascentMs = nullIfNaN(rep.ascentMs),
        resampledLeft = encode(rep.resampledLeft),
        resampledRight = encode(rep.resampledRight),
    )

    private fun toEntity(hold: HoldRecord): HoldEntity = HoldEntity(
        setId = 0,
        startMs = hold.startMs,
        endMs = hold.endMs,
        validity = hold.validity.name,
        totalHoldMs = hold.totalHoldMs,
        longestUnbrokenMs = hold.longestUnbrokenMs,
        breakCount = hold.breakCount,
        meanAbsDeviation = nullIfNaN(hold.meanAbsDeviation),
        maxAbsDeviation = nullIfNaN(hold.maxAbsDeviation),
        coverage = hold.coverage,
        meanHeldValue = nullIfNaN(hold.meanHeldValue),
        heldValueSd = nullIfNaN(hold.heldValueSd),
    )

    // --- Rows -> domain ------------------------------------------------------------

    fun toDomain(stored: SessionWithSets): SessionRecord = SessionRecord(
        sessionId = stored.session.sessionId,
        startedAtEpochMs = stored.session.startedAtEpochMs,
        utcOffsetMinutes = stored.session.utcOffsetMinutes,
        sets = stored.sets
            .sortedBy { it.set.orderInSession }
            .map(::toDomain),
    )

    private fun toDomain(stored: SetWithChildren): SetRecord = when (stored.set.kind) {
        KIND_HOLD -> HoldSetRecord(
            exerciseId = stored.set.exerciseId,
            startedAtEpochMs = stored.set.startedAtEpochMs,
            holds = stored.holds.map(::toDomain),
        )

        else -> RepetitionSetRecord(
            exerciseId = stored.set.exerciseId,
            startedAtEpochMs = stored.set.startedAtEpochMs,
            reps = stored.reps.sortedBy { it.repIndex }.map(::toDomain),
        )
    }

    private fun toDomain(entity: RepEntity): RepRecord = RepRecord(
        repIndex = entity.repIndex,
        attributedSide = entity.attributedSide?.let { Side.valueOf(it) },
        startMs = entity.startMs,
        endMs = entity.endMs,
        validity = RepValidity.valueOf(entity.validity),
        rangeOfMotion = signal(entity.rangeOfMotion),
        peakValue = signal(entity.peakValue),
        troughValue = signal(entity.troughValue),
        descentMs = nanIfNull(entity.descentMs),
        ascentMs = nanIfNull(entity.ascentMs),
        resampledLeft = decode(entity.resampledLeft),
        resampledRight = decode(entity.resampledRight),
    )

    private fun toDomain(entity: HoldEntity): HoldRecord = HoldRecord(
        startMs = entity.startMs,
        endMs = entity.endMs,
        validity = HoldValidity.valueOf(entity.validity),
        totalHoldMs = entity.totalHoldMs,
        longestUnbrokenMs = entity.longestUnbrokenMs,
        breakCount = entity.breakCount,
        meanAbsDeviation = nanIfNull(entity.meanAbsDeviation),
        maxAbsDeviation = nanIfNull(entity.maxAbsDeviation),
        coverage = entity.coverage,
        meanHeldValue = nanIfNull(entity.meanHeldValue),
        heldValueSd = nanIfNull(entity.heldValueSd),
    )

    // --- The two conversions that carry the risk ---------------------------------------

    /**
     * A bilateral value becomes two columns. There is no reducer here and there
     * must never be one - a single column would force the two sides together at
     * write time and lose the distinction permanently.
     */
    private fun columns(signal: BilateralSignal): BilateralColumns = BilateralColumns(
        left = nullIfNaN(signal.left),
        right = nullIfNaN(signal.right),
    )

    private fun signal(columns: BilateralColumns): BilateralSignal = BilateralSignal(
        left = nanIfNull(columns.left),
        right = nanIfNull(columns.right),
    )

    /** `NaN` has no REAL representation in SQLite, so it becomes `NULL`. */
    private fun nullIfNaN(value: Double): Double? = if (value.isNaN()) null else value

    /** And back. A missing measurement reloads as missing, never as zero. */
    private fun nanIfNull(value: Double?): Double = value ?: Double.NaN

    /**
     * The resampled signal, as a blob.
     *
     * A fixed-width little-endian encoding rather than a string, because these
     * are 64 doubles per side per repetition and the text form would be several
     * times the size for no benefit. IEEE-754 carries `NaN` through a blob
     * unchanged, which is the one thing SQLite's REAL columns cannot do - so the
     * arrays need no null-mapping and keep their unmeasured points exactly.
     */
    internal fun encode(values: DoubleArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Double.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) buffer.putDouble(value)
        return buffer.array()
    }

    internal fun decode(bytes: ByteArray): DoubleArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return DoubleArray(bytes.size / Double.SIZE_BYTES) { buffer.getDouble() }
    }

    const val KIND_REPETITION: String = "REPETITION"
    const val KIND_HOLD: String = "HOLD"
}

/** A session's rows, before Room has assigned any identifiers. */
data class StoredSession(val session: SessionEntity, val sets: List<StoredSet>)

data class StoredSet(
    val set: SetEntity,
    val reps: List<RepEntity>,
    val holds: List<HoldEntity>,
)
