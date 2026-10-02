package com.kinesis.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * ### Versions and migrations - DECISIONS.md P3-22
 *
 * **Never destructive.** A schema change ships with a [Migration] that carries
 * every existing session across, and the builder has no destructive fallback:
 * were a migration ever missing, Room would refuse to open the database rather
 * than empty it. Losing a user's training to a schema change is the one outcome
 * this storage may not have.
 *
 * Every version's schema is exported to `app/schemas/`, and
 * `KinesisDatabaseMigrationTest` builds a real version 1 database from the
 * exported schema, fills it, migrates it with [build] - the configuration the
 * app itself uses - and reads every session back. `DatabaseVersionGuardTest`
 * fails if [VERSION] moves without a migration and an exported schema.
 *
 * | Version | What |
 * |---|---|
 * | 1 | Sessions, sets, repetitions, holds (P3-12) |
 * | 2 | `baselines` (P3-22); two hold columns, `meanHeldValue` and `heldValueSd` |
 * | 3 | one repetition column, `form`: the form summary (P3-44) |
 */
@Database(
    entities = [SessionEntity::class, SetEntity::class, RepEntity::class, HoldEntity::class, BaselineEntity::class],
    version = KinesisDatabase.VERSION,
    exportSchema = true,
)
abstract class KinesisDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun baselineDao(): BaselineDao

    companion object {

        const val VERSION: Int = 3

        const val NAME: String = "kinesis.db"

        /**
         * Adds the baselines table and the two hold columns. Nothing is dropped,
         * rewritten or copied: every existing row stays as it was, and existing
         * holds read `NULL` - not measured - in the new columns. The statements
         * are the ones Room exports for version 2 (`app/schemas/.../2.json`), so
         * the migrated schema is the schema a fresh install gets; Room checks
         * that on every open after migrating.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `baselines` (`ownerUid` TEXT NOT NULL, " +
                        "`calibrationId` TEXT NOT NULL, `kind` TEXT NOT NULL, `estimatorVersion` TEXT NOT NULL, " +
                        "`createdAtEpochMs` REAL NOT NULL, `sourceSessionId` TEXT, `payloadJson` TEXT NOT NULL, " +
                        "PRIMARY KEY(`ownerUid`, `calibrationId`))",
                )
                db.execSQL("ALTER TABLE `holds` ADD COLUMN `meanHeldValue` REAL")
                db.execSQL("ALTER TABLE `holds` ADD COLUMN `heldValueSd` REAL")
            }
        }

        /**
         * Adds the repetitions' form summary column - DECISIONS.md P3-44. Every existing row stays
         * as it was and reads `NULL`: no form summary, not measured, never zero.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reps` ADD COLUMN `form` TEXT")
            }
        }

        /** Every migration, oldest first. A version bump adds one here. */
        val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var instance: KinesisDatabase? = null

        fun get(context: Context): KinesisDatabase = instance ?: synchronized(this) {
            instance ?: build(context, NAME).also { instance = it }
        }

        /**
         * The app's configuration: every migration, and no destructive fallback.
         * The migration test opens its database through this, so what it proves
         * is what the phone runs.
         */
        fun build(context: Context, name: String): KinesisDatabase = Room.databaseBuilder(
            context.applicationContext,
            KinesisDatabase::class.java,
            name,
        ).addMigrations(*MIGRATIONS.toTypedArray()).build()
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
