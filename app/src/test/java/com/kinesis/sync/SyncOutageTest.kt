package com.kinesis.sync

import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRecorder
import com.kinesis.storage.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - CLAUDE.md section 16 as amended, DECISIONS.md P3-14.
 *
 * > *"A session MUST save locally with no network available; sync is additive
 * > and never a precondition."*
 *
 * ### The failure this prevents
 *
 * Adding cloud sync to an offline-first app fails in one specific way: the
 * upload creeps into the write path, and a user with no signal loses a set they
 * just performed. It does not look like a bug in review - `save()` still gets
 * called, the code still reads sensibly - and it only shows up on a gym wifi
 * that resolves DNS but drops packets.
 *
 * So these tests drive the whole local path with the remote side failing in
 * every way it can, and assert the local result is byte-for-byte what it would
 * have been with sync switched off entirely.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SyncOutageTest {

    private val today = LocalDay(20_000)
    private val startedAt = today.dayNumber * LocalDay.MILLIS_PER_DAY + 10 * 3_600_000.0

    private class FakeRepository : SessionRepository {
        val saved = mutableListOf<SessionRecord>()
        override suspend fun allSessions(): List<SessionRecord> = saved.toList()
        override suspend fun save(session: SessionRecord) { saved += session }
        override suspend fun clear() = saved.clear()
    }

    /** A queue that fails the way a broken scheduler would. */
    private class ExplodingQueue : SyncQueue {
        var attempts = 0
        override fun enqueue(sessionId: String) {
            attempts++
            throw IllegalStateException("scheduler unavailable")
        }
    }

    private class RecordingQueue : SyncQueue {
        val enqueued = mutableListOf<String>()
        override fun enqueue(sessionId: String) { enqueued += sessionId }
    }

    // --- The local write survives everything ------------------------------------------

    /**
     * The central assertion. The queue throws on every call; the session must
     * still be on disk and still reach the dashboard.
     */
    @Test
    fun a_failing_sync_queue_never_prevents_a_local_save() = runBlocking {
        val local = FakeRepository()
        val queue = ExplodingQueue()
        val repository = SyncingSessionRepository(local, queue) { true }

        val recorder = SessionRecorder("s1", startedAt, 0, repository)
        recorder.closeRepetitionSet("squat", startedAt, List(10) { rep(it + 1) })
        val written = recorder.finish()

        assertTrue("the local save was lost because the upload could not be queued", written != null)
        assertEquals(1, local.saved.size)
        assertEquals(1, queue.attempts)

        val summary = Dashboard.summarise(repository.allSessions(), today)
        assertEquals(10, summary.totalValidReps)
        assertEquals(
            "a session that failed to queue did not earn its streak day",
            1,
            summary.streak.currentDays,
        )
    }

    /**
     * And with sync switched off entirely, the local result is identical - which
     * is what "additive" means. If these two ever diverge, sync has become a
     * participant in the write path rather than an observer of it.
     */
    @Test
    fun the_local_result_is_identical_with_sync_broken_and_with_sync_absent() = runBlocking {
        val withBrokenSync = FakeRepository()
        SessionRecorder(
            "s1", startedAt, 0,
            SyncingSessionRepository(withBrokenSync, ExplodingQueue()) { true },
        ).apply {
            closeRepetitionSet("squat", startedAt, List(7) { rep(it + 1) })
            finish()
        }

        val withoutSync = FakeRepository()
        SessionRecorder("s1", startedAt, 0, withoutSync).apply {
            closeRepetitionSet("squat", startedAt, List(7) { rep(it + 1) })
            finish()
        }

        val a = Dashboard.summarise(withBrokenSync.allSessions(), today)
        val b = Dashboard.summarise(withoutSync.allSessions(), today)

        assertEquals(b.sessionCount, a.sessionCount)
        assertEquals(b.totalValidReps, a.totalValidReps)
        assertEquals(b.streak.currentDays, a.streak.currentDays)
        assertEquals(
            b.perExercise.single().meanRangeOfMotion.left,
            a.perExercise.single().meanRangeOfMotion.left,
            1e-12,
        )
        assertEquals(
            b.perExercise.single().meanRangeOfMotion.right,
            a.perExercise.single().meanRangeOfMotion.right,
            1e-12,
        )
    }

    /** Signed out, nothing is queued - and the save still happens. */
    @Test
    fun a_signed_out_user_still_records_sessions_locally() = runBlocking {
        val local = FakeRepository()
        val queue = RecordingQueue()
        val repository = SyncingSessionRepository(local, queue) { false }

        SessionRecorder("s1", startedAt, 0, repository).apply {
            closeRepetitionSet("squat", startedAt, List(5) { rep(it + 1) })
            finish()
        }

        assertEquals(1, local.saved.size)
        assertTrue("a signed-out session was queued for upload", queue.enqueued.isEmpty())
    }

    @Test
    fun a_signed_in_save_queues_exactly_one_upload_after_the_local_write() = runBlocking {
        val local = FakeRepository()
        val queue = RecordingQueue()
        val repository = SyncingSessionRepository(local, queue) { true }

        SessionRecorder("s1", startedAt, 0, repository).apply {
            closeRepetitionSet("squat", startedAt, List(5) { rep(it + 1) })
            finish()
        }

        assertEquals(listOf("s1"), queue.enqueued)
        assertEquals(1, local.saved.size)
    }

    /**
     * Reading history never touches the network either. The dashboard is a local
     * read, so it works on a plane.
     */
    @Test
    fun reading_history_goes_to_local_storage_and_not_to_the_remote() = runBlocking {
        val local = FakeRepository()
        val repository = SyncingSessionRepository(local, RecordingQueue()) { true }

        SessionRecorder("s1", startedAt, 0, repository).apply {
            closeRepetitionSet("squat", startedAt, List(3) { rep(it + 1) })
            finish()
        }

        assertEquals(1, repository.allSessions().size)
        assertEquals(local.saved.map { it.sessionId }, repository.allSessions().map { it.sessionId })
    }

    private fun rep(index: Int): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = 0.0,
        endMs = 2_000.0,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(95.0, 91.0),
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(75.0, 79.0),
        descentMs = 1_000.0,
        ascentMs = 1_000.0,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )
}
