package com.kinesis.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.BilateralStatistic
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The version 1 to 2 migration, on the phone - DECISIONS.md P3-22.
 *
 * A version 1 database is built from the schema exported in `app/schemas`, which
 * is the schema every existing install has, and filled with a session holding a
 * repetition set and a hold set. It is then migrated by [KinesisDatabase.build],
 * the configuration the app itself runs, and every row is read back through the
 * app's own DAO and mapping. "Never destructive" means exactly that: nothing
 * that went in is missing or changed when it comes out.
 */
@RunWith(AndroidJUnit4::class)
class KinesisDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, KinesisDatabase::class.java)

    private val name = "migration-test.db"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    /** One session as version 1 stored it: a two-repetition squat set and a plank set. */
    private fun createVersion1() {
        helper.createDatabase(name, 1).apply {
            insert("sessions", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("sessionId", "f6ec3996-7046-4343-bcbc-99380e9881a1")
                put("startedAtEpochMs", 1_727_133_262_000.0)
                put("utcOffsetMinutes", 330)
            })
            insert("sets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("setId", 1L)
                put("sessionId", "f6ec3996-7046-4343-bcbc-99380e9881a1")
                put("exerciseId", "squat")
                put("startedAtEpochMs", 1_727_133_262_500.0)
                put("kind", SessionMapping.KIND_REPETITION)
                put("orderInSession", 0)
            })
            insert("sets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("setId", 2L)
                put("sessionId", "f6ec3996-7046-4343-bcbc-99380e9881a1")
                put("exerciseId", "plank")
                put("startedAtEpochMs", 1_727_133_300_000.0)
                put("kind", SessionMapping.KIND_HOLD)
                put("orderInSession", 1)
            })
            insertRep(1, RepValidity.VALID, romLeft = 96.5, romRight = 91.25, side = null)
            // An alternating-style record with its idle side unmeasured: NULL in version 1.
            insertRep(2, RepValidity.INSUFFICIENT_DATA, romLeft = 40.0, romRight = null, side = Side.LEFT)
            insert("holds", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("setId", 2L)
                put("startMs", 0.0)
                put("endMs", 21_000.0)
                put("validity", HoldValidity.VALID.name)
                put("totalHoldMs", 19_500.0)
                put("longestUnbrokenMs", 12_000.0)
                put("breakCount", 1)
                put("meanAbsDeviation", 3.5)
                putNull("maxAbsDeviation")
                put("coverage", 0.97)
            })
            close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertRep(
        index: Int,
        validity: RepValidity,
        romLeft: Double,
        romRight: Double?,
        side: Side?,
    ) {
        val curve = DoubleArray(64) { if (romRight == null) Double.NaN else 160.0 - it }
        insert("reps", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("setId", 1L)
            put("repIndex", index)
            if (side == null) putNull("attributedSide") else put("attributedSide", side.name)
            put("startMs", index * 3_000.0)
            put("endMs", index * 3_000.0 + 1_900.0)
            put("validity", validity.name)
            put("rom_left", romLeft)
            if (romRight == null) putNull("rom_right") else put("rom_right", romRight)
            put("peak_left", 165.0)
            if (romRight == null) putNull("peak_right") else put("peak_right", 164.0)
            put("trough_left", 165.0 - romLeft)
            if (romRight == null) putNull("trough_right") else put("trough_right", 164.0 - romRight)
            put("descentMs", 1_050.0)
            putNull("ascentMs")
            put("resampledLeft", SessionMapping.encode(DoubleArray(64) { 165.0 - it }))
            put("resampledRight", SessionMapping.encode(curve))
        })
    }

    @Test
    fun migrating_to_version_2_keeps_every_session_exactly() = runBlocking {
        createVersion1()
        // Room's own check: the migrated schema is the schema version 2 declares.
        helper.runMigrationsAndValidate(name, 2, true, KinesisDatabase.MIGRATION_1_2).close()

        val database = KinesisDatabase.build(context, name)
        try {
            val sessions = RoomSessionRepository(database.sessionDao()).allSessions()
            assertEquals(1, sessions.size)
            val session = sessions.single()
            assertEquals("f6ec3996-7046-4343-bcbc-99380e9881a1", session.sessionId)
            assertEquals(1_727_133_262_000.0, session.startedAtEpochMs, 0.0)
            assertEquals(330, session.utcOffsetMinutes)
            assertEquals(listOf("squat", "plank"), session.sets.map { it.exerciseId })

            val reps = (session.sets[0] as RepetitionSetRecord).reps
            assertEquals(listOf(1, 2), reps.map { it.repIndex })
            assertEquals(96.5, reps[0].rangeOfMotion.left, 0.0)
            assertEquals(91.25, reps[0].rangeOfMotion.right, 0.0)
            assertTrue("an unmeasured side stays unmeasured", reps[1].rangeOfMotion.right.isNaN())
            assertEquals(Side.LEFT, reps[1].attributedSide)
            assertTrue(reps[0].ascentMs.isNaN())
            assertEquals(165.0 - 10, reps[0].resampledLeft[10], 0.0)
            assertTrue(reps[1].resampledRight.all { it.isNaN() })

            val hold = (session.sets[1] as HoldSetRecord).holds.single()
            assertEquals(19_500.0, hold.totalHoldMs, 0.0)
            assertEquals(1, hold.breakCount)
            assertTrue(hold.maxAbsDeviation.isNaN())
            // The new columns: not measured before version 2, and not invented now.
            assertTrue(hold.meanHeldValue.isNaN())
            assertTrue(hold.heldValueSd.isNaN())
        } finally {
            database.close()
        }
    }

    /** No migration step is skipped: the production builder alone carries version 1 across. */
    @Test
    fun the_apps_own_builder_migrates_a_version_1_database_without_losing_it() = runBlocking {
        createVersion1()
        val database = KinesisDatabase.build(context, name)
        try {
            assertEquals(1, RoomSessionRepository(database.sessionDao()).allSessions().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun after_migrating_a_baseline_is_stored_per_user_and_replaced_on_recalibration() = runBlocking {
        createVersion1()
        val database = KinesisDatabase.build(context, name)
        try {
            val baselines = RoomBaselineRepository(database.baselineDao())
            baselines.save("uid-a", squatBaseline(medianLeft = 100.0, session = "first"))
            baselines.save("uid-a", squatBaseline(medianLeft = 88.0, session = "second"))
            baselines.save("uid-a", HoldBaseline("plank", "p6-1", 1.0, "h", 180.0, 175.0, 5.0, 2.0, 12_000.0, 0, 0.9))
            baselines.save("uid-b", squatBaseline(medianLeft = 70.0, session = "other user"))

            val mine = baselines.baselinesFor("uid-a")
            assertEquals(setOf("squat", "plank"), mine.keys)
            val squat = mine.getValue("squat") as RepetitionBaseline
            assertEquals(88.0, squat.rangeOfMotion.left.median, 0.0)
            assertEquals("second", squat.sourceSessionId)
            assertTrue(squat.rangeOfMotion.right.median.isNaN())
            assertEquals(70.0, (baselines.baselinesFor("uid-b").getValue("squat") as RepetitionBaseline).rangeOfMotion.left.median, 0.0)
            // And the sessions are still there.
            assertEquals(1, RoomSessionRepository(database.sessionDao()).allSessions().size)
        } finally {
            database.close()
        }
    }

    private fun squatBaseline(medianLeft: Double, session: String) = RepetitionBaseline(
        calibrationId = "squat",
        estimatorVersion = "p6-1",
        createdAtEpochMs = 1_727_200_000_000.0,
        sourceSessionId = session,
        rangeOfMotion = BilateralStatistic(RobustStatistic(medianLeft, 3.0, 8), RobustStatistic.NOT_MEASURED),
        peakValue = BilateralStatistic.NOT_MEASURED,
        troughValue = BilateralStatistic.NOT_MEASURED,
        descentMs = PhaseTimingBaseline(BilateralStatistic.NOT_MEASURED, RobustStatistic(1_000.0, 50.0, 8)),
        ascentMs = PhaseTimingBaseline(BilateralStatistic.NOT_MEASURED, RobustStatistic(800.0, 40.0, 8)),
        asymmetry = AsymmetryBaseline.Paired(RobustStatistic(4.0, 1.0, 8)),
        meanCurveLeft = DoubleArray(64) { 165.0 - it },
        meanCurveRight = DoubleArray(64) { Double.NaN },
        curveSpread = BilateralStatistic.NOT_MEASURED,
    )
}
