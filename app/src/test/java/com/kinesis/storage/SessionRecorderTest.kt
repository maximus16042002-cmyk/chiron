package com.kinesis.storage

import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-12.
 *
 * The join between a live session and the dashboard. Batch 6 built the
 * aggregation and had nothing to aggregate; this is what closes that loop, and
 * these tests assert the loop actually closes: what a set produced is what the
 * dashboard counts, through storage, not through memory.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SessionRecorderTest {

    private val today = LocalDay(20_000)
    private val startedAt = today.dayNumber * LocalDay.MILLIS_PER_DAY + 10 * 3_600_000.0

    /** An in-memory stand-in. Room's SQL is device work - P3-12. */
    private class FakeRepository : SessionRepository {
        val saved = mutableListOf<SessionRecord>()
        override suspend fun allSessions(): List<SessionRecord> = saved.toList()
        override suspend fun save(session: SessionRecord) { saved += session }
        override suspend fun clear() = saved.clear()
    }

    private fun recorder(repository: SessionRepository) = SessionRecorder(
        sessionId = "s1",
        startedAtEpochMs = startedAt,
        utcOffsetMinutes = 0,
        repository = repository,
    )

    // --- The loop closes -------------------------------------------------------------

    /**
     * The whole point of this batch's item 3: a set performed now shows up in
     * the dashboard read from storage afterwards, with the same numbers.
     */
    @Test
    fun a_recorded_set_reaches_the_dashboard_through_storage() = runBlocking {
        val repository = FakeRepository()
        val recorder = recorder(repository)

        recorder.closeRepetitionSet("squat", startedAt, List(8) { rep(it + 1) })
        recorder.finish()

        val summary = Dashboard.summarise(repository.allSessions(), today)

        assertEquals(1, summary.sessionCount)
        assertEquals(8, summary.totalValidReps)
        assertEquals("squat", summary.perExercise.single().exerciseId)
        assertEquals(
            "the session did not earn the day it was performed on",
            1,
            summary.streak.currentDays,
        )
    }

    @Test
    fun a_hold_reaches_the_dashboard_as_hold_time_and_not_as_repetitions() = runBlocking {
        val repository = FakeRepository()
        val recorder = recorder(repository)

        recorder.closeHoldSet("plank", startedAt, listOf(hold(42_000.0)))
        recorder.finish()

        val summary = Dashboard.summarise(repository.allSessions(), today)
        assertEquals(0, summary.totalValidReps)
        assertEquals(42_000.0, summary.totalHoldMs, 1e-9)
    }

    @Test
    fun several_sets_in_one_session_are_written_once_and_read_back_whole() = runBlocking {
        val repository = FakeRepository()
        val recorder = recorder(repository)

        recorder.closeRepetitionSet("squat", startedAt, List(5) { rep(it + 1) })
        recorder.closeRepetitionSet("pushup", startedAt + 60_000, List(3) { rep(it + 1) })
        recorder.closeHoldSet("plank", startedAt + 120_000, listOf(hold(30_000.0)))
        recorder.finish()

        assertEquals("a session was written more than once", 1, repository.saved.size)

        val summary = Dashboard.summarise(repository.allSessions(), today)
        assertEquals(1, summary.sessionCount)
        assertEquals(8, summary.totalValidReps)
        assertEquals(
            listOf("plank", "pushup", "squat"),
            summary.perExercise.map { it.exerciseId }.sorted(),
        )
    }

    // --- What is deliberately not written -----------------------------------------------

    /**
     * Opening the app and leaving is not a session. Writing one would put a row
     * in the database that the dashboard counts, inflating the session count with
     * something the user never did.
     */
    @Test
    fun a_session_with_no_sets_is_not_written_at_all() = runBlocking {
        val repository = FakeRepository()

        assertNull(recorder(repository).finish())
        assertTrue(repository.saved.isEmpty())
        assertEquals(0, Dashboard.summarise(repository.allSessions(), today).sessionCount)
    }

    /**
     * An empty set *is* written - the user performed a set and nothing completed,
     * which is a fact - but it must not earn a streak day. The rule that stops it
     * lives in `Streaks` (P3-10) and this checks the two agree.
     */
    @Test
    fun a_set_in_which_nothing_completed_is_recorded_but_earns_no_day() = runBlocking {
        val repository = FakeRepository()
        val recorder = recorder(repository)

        recorder.closeRepetitionSet(
            "squat",
            startedAt,
            listOf(rep(1, RepValidity.ABORTED), rep(2, RepValidity.INCOMPLETE)),
        )
        recorder.finish()

        val summary = Dashboard.summarise(repository.allSessions(), today)
        assertEquals("the session itself was not recorded", 1, summary.sessionCount)
        assertEquals(0, summary.totalValidReps)
        assertEquals(
            "two abandoned repetitions earned a streak day",
            0,
            summary.streak.currentDays,
        )
    }

    /**
     * Bilateral integrity survives the whole round trip - tracker to recorder to
     * storage to dashboard. Each step preserves it individually; this is the one
     * test that checks the chain.
     */
    @Test
    fun an_asymmetric_set_is_still_asymmetric_in_the_dashboard() = runBlocking {
        val repository = FakeRepository()
        val recorder = recorder(repository)

        recorder.closeRepetitionSet(
            "squat",
            startedAt,
            listOf(
                rep(1, rangeOfMotion = BilateralSignal(100.0, 70.0)),
                rep(2, rangeOfMotion = BilateralSignal(110.0, 80.0)),
            ),
        )
        recorder.finish()

        val rom = Dashboard.summarise(repository.allSessions(), today)
            .perExercise.single().meanRangeOfMotion

        assertEquals(105.0, rom.left, 1e-9)
        assertEquals(75.0, rom.right, 1e-9)
        assertTrue("the two sides were pooled somewhere in the chain", rom.asymmetry() > 0.0)
    }

    // --- Records -----------------------------------------------------------------------

    private fun rep(
        index: Int,
        validity: RepValidity = RepValidity.VALID,
        rangeOfMotion: BilateralSignal = BilateralSignal(95.0, 93.0),
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = 0.0,
        endMs = 2_000.0,
        validity = validity,
        rangeOfMotion = rangeOfMotion,
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(75.0, 77.0),
        descentMs = 1_000.0,
        ascentMs = 1_000.0,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )

    private fun hold(totalHoldMs: Double): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = totalHoldMs,
        validity = HoldValidity.VALID,
        totalHoldMs = totalHoldMs,
        longestUnbrokenMs = totalHoldMs,
        breakCount = 0,
        meanAbsDeviation = 5.0,
        maxAbsDeviation = 12.0,
        coverage = 1.0,
    )
}
