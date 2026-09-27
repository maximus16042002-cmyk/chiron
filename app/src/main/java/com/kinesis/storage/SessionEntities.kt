package com.kinesis.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * The stored shape of a session - DECISIONS.md P3-12.
 *
 * ### Two rules this schema exists to keep
 *
 * **1. Left and right never share a column.** CLAUDE.md section 3 says bilateral
 * signals stay separate "everywhere", and storage is part of everywhere. A
 * schema with one `rangeOfMotion` column would force a reducer at write time and
 * lose the distinction permanently - the prototype's `(left + right) / 2f`
 * (P2-2), made durable. Every bilateral measurement is two nullable columns.
 *
 * **2. `NaN` survives the round trip.** This is the subtle one. `NaN` is
 * Kinesis's "not measured" sentinel throughout - an alternating repetition
 * records one side and `NaN` on the other, and the near-side-only row (P3-8)
 * records `NaN` for the arm the camera cannot see. **SQLite has no way to store
 * a `NaN` in a REAL column**: it arrives back as `0.0` or `NULL` depending on the
 * driver. Stored naively, every bicep curl in the database would reload claiming
 * a real measurement of zero on the idle arm.
 *
 * So every measured column is `Double?`, `NaN` is written as `NULL`, and `NULL`
 * is read back as `NaN`. The mapping is in [SessionMapping] and is guarded by
 * test, because nothing about the schema itself makes it obvious.
 */

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAtEpochMs: Double,
    val utcOffsetMinutes: Int,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val sessionId: String,
    val exerciseId: String,
    val startedAtEpochMs: Double,

    /** `REPETITION` or `HOLD`. The two are never mixed in one set - P3-10. */
    val kind: String,

    /** Position within the session, so sets reload in the order performed. */
    val orderInSession: Int,
)

/** Two columns, never one. See the note at the top of this file. */
data class BilateralColumns(
    @ColumnInfo(name = "left") val left: Double?,
    @ColumnInfo(name = "right") val right: Double?,
)

@Entity(
    tableName = "reps",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["setId"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class RepEntity(
    @PrimaryKey(autoGenerate = true) val repId: Long = 0,
    val setId: Long,
    val repIndex: Int,

    /** `LEFT`, `RIGHT`, or null for a simultaneous repetition - P3-1. */
    val attributedSide: String?,

    val startMs: Double,
    val endMs: Double,
    val validity: String,

    @Embedded(prefix = "rom_") val rangeOfMotion: BilateralColumns,
    @Embedded(prefix = "peak_") val peakValue: BilateralColumns,
    @Embedded(prefix = "trough_") val troughValue: BilateralColumns,

    val descentMs: Double?,
    val ascentMs: Double?,

    /** 64 doubles each, encoded by [SessionMapping]. `NaN` survives. */
    val resampledLeft: ByteArray,
    val resampledRight: ByteArray,
) {
    // Room generates equals/hashCode warnings for ByteArray fields; these are
    // stored blobs compared by content, and nothing relies on entity equality.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = repId.hashCode()
}

@Entity(
    tableName = "holds",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["setId"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class HoldEntity(
    @PrimaryKey(autoGenerate = true) val holdId: Long = 0,
    val setId: Long,
    val startMs: Double,
    val endMs: Double,
    val validity: String,
    val totalHoldMs: Double,
    val longestUnbrokenMs: Double,
    val breakCount: Int,
    val meanAbsDeviation: Double?,
    val maxAbsDeviation: Double?,
    val coverage: Double,

    /**
     * Where the hold was held and how steadily - DECISIONS.md P3-22, added by
     * the version 2 migration. `NULL` for every hold recorded before it, which
     * reloads as `NaN`: not measured, never zero.
     */
    val meanHeldValue: Double?,
    val heldValueSd: Double?,
)

/** A set with whichever children it has. Exactly one list is ever populated. */
data class SetWithChildren(
    @Embedded val set: SetEntity,
    @Relation(parentColumn = "setId", entityColumn = "setId")
    val reps: List<RepEntity>,
    @Relation(parentColumn = "setId", entityColumn = "setId")
    val holds: List<HoldEntity>,
)

/** A session and its sets, which is the only shape anything reads. */
data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(entity = SetEntity::class, parentColumn = "sessionId", entityColumn = "sessionId")
    val sets: List<SetWithChildren>,
)
