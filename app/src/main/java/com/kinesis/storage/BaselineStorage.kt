package com.kinesis.storage

import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Personal baselines, on this phone only - DECISIONS.md P3-22.
 *
 * ### Local, and never synced
 *
 * The sign-in disclosure (P3-14) tells the user what leaves the phone: their
 * completed sessions. A baseline is not a session, and nothing uploads one: this
 * repository is not wrapped by `SyncingSessionRepository`, no file in the sync
 * package refers to it, and the Firestore rules refuse any document outside a
 * user's `sessions` (`BaselinesStayOnThePhoneTest` checks all three). So the
 * disclosure stays true without a word changed. The calibration *set* is an
 * ordinary session and syncs like any other; the baseline derived from it stays
 * here. A reinstall or a new phone keeps the history and loses the baselines,
 * and the user calibrates again.
 *
 * ### One per user, per exercise
 *
 * The primary key is the pair. Kinesis's local sessions are not partitioned by
 * account (recorded in P3-22 as found, not changed here), but baselines are:
 * whoever is signed in sees only their own, and calibrating replaces only
 * theirs.
 */
@Entity(tableName = "baselines", primaryKeys = ["ownerUid", "calibrationId"])
data class BaselineEntity(

    /** The signed-in account the baseline belongs to. */
    val ownerUid: String,

    /** A parent profile's id: variants share their parent's baseline. */
    val calibrationId: String,

    /** `REPETITION` or `HOLD`, readable without parsing [payloadJson]. */
    val kind: String,

    val estimatorVersion: String,
    val createdAtEpochMs: Double,
    val sourceSessionId: String?,

    /**
     * The baseline as the core's `BaselineJson` writes it: left and right as
     * separate fields throughout, and "not measured" as `null` rather than a
     * number - the same two rules the session tables keep (P3-12). One column
     * rather than forty, because nothing queries inside a baseline, and the
     * format already round-trips exactly and is tested on every platform.
     */
    val payloadJson: String,
)

@Dao
interface BaselineDao {

    @Query("SELECT * FROM baselines WHERE ownerUid = :ownerUid ORDER BY calibrationId")
    suspend fun baselinesFor(ownerUid: String): List<BaselineEntity>

    /** Recalibrating replaces: the primary key is the user and the exercise. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baseline: BaselineEntity)
}

/** Where the app keeps baselines. Room in the app, a map in tests. */
interface BaselineRepository {

    /** The user's baselines, by calibration id. */
    suspend fun baselinesFor(ownerUid: String): Map<String, CalibrationBaseline>

    /** Stores [baseline] as the user's baseline for its exercise, replacing any earlier one. */
    suspend fun save(ownerUid: String, baseline: CalibrationBaseline)
}

/** The Room-backed [BaselineRepository]. Thin; the mapping is [BaselineMapping]. */
class RoomBaselineRepository(private val dao: BaselineDao) : BaselineRepository {

    override suspend fun baselinesFor(ownerUid: String): Map<String, CalibrationBaseline> =
        withContext(Dispatchers.IO) {
            val baselines = LinkedHashMap<String, CalibrationBaseline>()
            for (row in dao.baselinesFor(ownerUid)) {
                // A row that cannot be read is reported and left out, never shown
                // as a baseline it is not: the exercise reads as not calibrated.
                try {
                    baselines[row.calibrationId] = BaselineMapping.toDomain(row)
                } catch (error: IllegalArgumentException) {
                    Log.e(STORAGE_LOG_TAG, "stored baseline for ${row.calibrationId} could not be read", error)
                }
            }
            baselines
        }

    override suspend fun save(ownerUid: String, baseline: CalibrationBaseline) = withContext(Dispatchers.IO) {
        dao.upsert(BaselineMapping.toEntity(ownerUid, baseline))
    }
}

/** Baselines to rows and back. Pure, so it is tested on the JVM. */
object BaselineMapping {

    const val KIND_REPETITION: String = "REPETITION"
    const val KIND_HOLD: String = "HOLD"

    fun toEntity(ownerUid: String, baseline: CalibrationBaseline): BaselineEntity {
        require(ownerUid.isNotBlank()) { "a baseline belongs to a signed-in user" }
        return BaselineEntity(
            ownerUid = ownerUid,
            calibrationId = baseline.calibrationId,
            kind = when (baseline) {
                is RepetitionBaseline -> KIND_REPETITION
                is HoldBaseline -> KIND_HOLD
            },
            estimatorVersion = baseline.estimatorVersion,
            createdAtEpochMs = baseline.createdAtEpochMs,
            sourceSessionId = baseline.sourceSessionId,
            payloadJson = BaselineJson.write(baseline),
        )
    }

    /** Refuses a row whose columns disagree with its payload, rather than guessing which is right. */
    fun toDomain(entity: BaselineEntity): CalibrationBaseline {
        val baseline = BaselineJson.read(entity.payloadJson)
        require(baseline.calibrationId == entity.calibrationId) {
            "row ${entity.calibrationId} holds a baseline for ${baseline.calibrationId}"
        }
        val kind = if (baseline is RepetitionBaseline) KIND_REPETITION else KIND_HOLD
        require(kind == entity.kind) { "row ${entity.calibrationId} is ${entity.kind} but holds a $kind baseline" }
        return baseline
    }
}

/** Logcat tag for storage problems: `adb logcat -s KinesisStorage`. */
const val STORAGE_LOG_TAG: String = "KinesisStorage"
