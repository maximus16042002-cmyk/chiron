package com.kinesis.sync

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-14.
 *
 * Merging an account's history into this device on sign-in.
 *
 * ### Why a union is enough, and the defect checking it turned up
 *
 * Sessions are append-only, verified rather than assumed: the record types are
 * entirely `val` and `List`, the DAO has no `@Update` and no per-session delete,
 * and `SessionRecorder.finish()` writes once. Two devices can therefore never
 * hold different versions of one `sessionId`, so there is nothing to reconcile.
 *
 * But `RoomSessionRepository.save` uses a bare `@Insert` against a primary key
 * of `sessionId` - so re-inserting a session this device already has throws a
 * constraint violation rather than being ignored. The union has to filter before
 * it writes, and `a_session_already_held_locally_is_not_written_twice` is what
 * holds that.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SessionMergeTest {

    private class FakeRepository(initial: List<SessionRecord> = emptyList()) : SessionRepository {
        val saved = initial.toMutableList()
        var saveCalls = 0

        override suspend fun allSessions(): List<SessionRecord> = saved.toList()

        override suspend fun save(session: SessionRecord) {
            saveCalls++
            // Room's behaviour: the id is the primary key, and a bare @Insert
            // throws on a duplicate rather than ignoring it.
            require(saved.none { it.sessionId == session.sessionId }) {
                "UNIQUE constraint failed: sessions.sessionId (" + session.sessionId + ")"
            }
            saved += session
        }

        override suspend fun clear() = saved.clear()
    }

    private class FakeRemote(
        private val stored: MutableList<SessionRecord> = mutableListOf(),
        private val pushFails: Set<String> = emptySet(),
    ) : RemoteSessionStore {
        val pushed = mutableListOf<String>()

        override suspend fun push(uid: String, session: SessionRecord): SyncResult {
            if (session.sessionId in pushFails) {
                return SyncResult.Failed(SyncFailure.NETWORK_UNAVAILABLE)
            }
            pushed += session.sessionId
            stored += session
            return SyncResult.Pushed
        }

        override suspend fun pull(uid: String): List<SessionRecord> = stored.toList()
    }

    // --- Pulling ---------------------------------------------------------------------

    @Test
    fun signing_in_on_a_new_device_pulls_the_accounts_whole_history() = runBlocking {
        val local = FakeRepository()
        val remote = FakeRemote(mutableListOf(session("a"), session("b"), session("c")))

        val added = SessionMerge(local, remote).pullMissing("uid-1")

        assertEquals(3, added)
        assertEquals(listOf("a", "b", "c"), local.saved.map { it.sessionId }.sorted())
    }

    /**
     * The defect the append-only check turned up. Without the filter this throws
     * a constraint violation rather than doing nothing.
     */
    @Test
    fun a_session_already_held_locally_is_not_written_twice() = runBlocking {
        val local = FakeRepository(listOf(session("a"), session("b")))
        val remote = FakeRemote(mutableListOf(session("a"), session("b"), session("c")))

        val added = SessionMerge(local, remote).pullMissing("uid-1")

        assertEquals("only the one genuinely new session should be written", 1, added)
        assertEquals(1, local.saveCalls)
        assertEquals(listOf("a", "b", "c"), local.saved.map { it.sessionId }.sorted())
    }

    @Test
    fun local_only_sessions_survive_a_pull_untouched() = runBlocking {
        val local = FakeRepository(listOf(session("local-only")))
        val remote = FakeRemote(mutableListOf(session("remote-only")))

        SessionMerge(local, remote).pullMissing("uid-1")

        assertEquals(
            listOf("local-only", "remote-only"),
            local.saved.map { it.sessionId }.sorted(),
        )
    }

    @Test
    fun pulling_an_empty_account_changes_nothing() = runBlocking {
        val local = FakeRepository(listOf(session("a")))
        val added = SessionMerge(local, FakeRemote()).pullMissing("uid-1")

        assertEquals(0, added)
        assertEquals(0, local.saveCalls)
    }

    /** The union is a set operation, so running it twice is a no-op. */
    @Test
    fun merging_twice_is_the_same_as_merging_once() = runBlocking {
        val local = FakeRepository()
        val remote = FakeRemote(mutableListOf(session("a"), session("b")))
        val merge = SessionMerge(local, remote)

        merge.pullMissing("uid-1")
        val secondPass = merge.pullMissing("uid-1")

        assertEquals(0, secondPass)
        assertEquals(2, local.saved.size)
    }

    // --- Pushing ----------------------------------------------------------------------

    @Test
    fun only_sessions_the_account_lacks_are_pushed() = runBlocking {
        val local = FakeRepository(listOf(session("a"), session("b"), session("c")))
        val remote = FakeRemote(mutableListOf(session("a")))

        val failed = SessionMerge(local, remote).pushMissing("uid-1")

        assertTrue(failed.isEmpty())
        assertEquals(listOf("b", "c"), remote.pushed.sorted())
    }

    /**
     * A push that fails is returned so the caller can queue it - never thrown,
     * and never allowed to lose the local copy.
     */
    @Test
    fun a_failed_push_is_reported_and_the_local_session_is_untouched() = runBlocking {
        val local = FakeRepository(listOf(session("a"), session("b")))
        val remote = FakeRemote(mutableListOf(), pushFails = setOf("b"))

        val failed = SessionMerge(local, remote).pushMissing("uid-1")

        assertEquals(listOf("b"), failed.map { it.sessionId })
        assertEquals(listOf("a"), remote.pushed)
        assertEquals(
            "a failed upload removed or altered the local session",
            listOf("a", "b"),
            local.saved.map { it.sessionId }.sorted(),
        )
    }

    @Test
    fun a_total_outage_pushes_nothing_and_loses_nothing() = runBlocking {
        val local = FakeRepository(listOf(session("a"), session("b")))
        val remote = FakeRemote(mutableListOf(), pushFails = setOf("a", "b"))

        val failed = SessionMerge(local, remote).pushMissing("uid-1")

        assertEquals(2, failed.size)
        assertTrue(remote.pushed.isEmpty())
        assertEquals(2, local.saved.size)
    }

    // --- Records -----------------------------------------------------------------------

    private fun session(id: String): SessionRecord = SessionRecord(
        sessionId = id,
        startedAtEpochMs = 1_700_000_000_000.0,
        utcOffsetMinutes = 0,
        sets = listOf(
            RepetitionSetRecord(
                "squat", 1_700_000_000_000.0,
                listOf(
                    RepRecord(
                        repIndex = 1,
                        attributedSide = null,
                        startMs = 0.0,
                        endMs = 2_000.0,
                        validity = RepValidity.VALID,
                        rangeOfMotion = BilateralSignal(95.0, 92.0),
                        peakValue = BilateralSignal(170.0, 170.0),
                        troughValue = BilateralSignal(75.0, 78.0),
                        descentMs = 1_000.0,
                        ascentMs = 1_000.0,
                        resampledLeft = DoubleArray(64),
                        resampledRight = DoubleArray(64),
                    ),
                ),
            ),
        ),
    )
}
