package com.kinesis.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The on-device store - DECISIONS.md P3-12.
 *
 * Local only. No backend, no auth, no cloud, no sync, per FINAL_ARCHITECTURE
 * section 6, and Room adds no network capability - verified in P3-12 the same
 * way P3-11 verified it for Health Connect.
 */
@Dao
interface SessionDao {

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs ASC")
    suspend fun allSessions(): List<SessionWithSets>

    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Insert
    suspend fun insertReps(reps: List<RepEntity>)

    @Insert
    suspend fun insertHolds(holds: List<HoldEntity>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    /**
     * One session and all its children, in one transaction.
     *
     * A half-written session is worse than no session: the dashboard would count
     * a set that has no repetitions, and the streak would count a day on which
     * nothing completed.
     */
    @Transaction
    suspend fun insertWholeSession(stored: StoredSession) {
        insertSession(stored.session)
        for (storedSet in stored.sets) {
            val setId = insertSet(storedSet.set.copy(sessionId = stored.session.sessionId))
            if (storedSet.reps.isNotEmpty()) {
                insertReps(storedSet.reps.map { it.copy(setId = setId) })
            }
            if (storedSet.holds.isNotEmpty()) {
                insertHolds(storedSet.holds.map { it.copy(setId = setId) })
            }
        }
    }
}

@Database(
    entities = [SessionEntity::class, SetEntity::class, RepEntity::class, HoldEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KinesisDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {

        @Volatile
        private var instance: KinesisDatabase? = null

        fun get(context: Context): KinesisDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KinesisDatabase::class.java,
                "kinesis.db",
            ).build().also { instance = it }
        }
    }
}

/**
 * The Room-backed [SessionRepository].
 *
 * Thin on purpose. Everything that can be wrong - losing a side, losing a `NaN` -
 * lives in [SessionMapping], which is pure and unit-tested; this class only moves
 * rows. P3-12 records what that split leaves for device testing.
 */
class RoomSessionRepository(
    private val dao: SessionDao,
) : SessionRepository {

    override suspend fun allSessions(): List<SessionRecord> = withContext(Dispatchers.IO) {
        dao.allSessions().map(SessionMapping::toDomain)
    }

    override suspend fun save(session: SessionRecord) = withContext(Dispatchers.IO) {
        dao.insertWholeSession(SessionMapping.toEntities(session))
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dao.deleteAllSessions()
    }
}
