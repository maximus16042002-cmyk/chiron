package com.kinesis.storage

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Drafts of sets in progress - DECISIONS.md P3-66. Writes run on a direct executor here, so each
 * is on disk when the call returns.
 */
class SetDraftsTest {

    private class Store : SessionRepository {
        val saved = mutableListOf<SessionRecord>()
        override suspend fun allSessions(): List<SessionRecord> = saved.toList()
        override suspend fun save(session: SessionRecord) { saved += session }
        override suspend fun clear() = saved.clear()
    }

    private val root: File = Files.createTempDirectory("set-drafts").toFile()
    private fun drafts(account: String = "kinesis") = SetDrafts(File(root, account), Runnable::run)

    private fun rep(index: Int) = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = index * 2_000.0,
        endMs = index * 2_000.0 + 1_800.0,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(95.0, Double.NaN),
        peakValue = BilateralSignal(170.0, Double.NaN),
        troughValue = BilateralSignal(75.0, Double.NaN),
        descentMs = 900.0,
        ascentMs = 900.0,
        resampledLeft = DoubleArray(64) { 170.0 - it },
        resampledRight = DoubleArray(64) { Double.NaN },
    )

    private fun session(id: String, reps: Int) = SessionRecord(
        sessionId = id,
        startedAtEpochMs = 1_760_000_000_000.0,
        utcOffsetMinutes = 330,
        sets = listOf(RepetitionSetRecord("squat", 1_760_000_000_000.0, (1..reps).map(::rep))),
    )

    private fun draftFiles(account: String = "kinesis") =
        File(root, account).listFiles { f -> f.name.endsWith(".json") }.orEmpty().toList()

    @Test
    fun a_leftover_draft_is_saved_once_and_removed() = runBlocking {
        val store = Store()
        drafts().write(session("a", reps = 3))

        val outcome = drafts().recover(store).single() as DraftRecovery.Recovered

        assertEquals("a", outcome.session.sessionId)
        assertEquals(3, (store.saved.single().sets.single() as RepetitionSetRecord).reps.size)
        assertTrue("the draft was left behind", draftFiles().isEmpty())
        assertTrue("recovered twice", drafts().recover(store).isEmpty())
        assertEquals(1, store.saved.size)
    }

    /** Killed after the save but before the draft was discarded: never saved twice. */
    @Test
    fun a_draft_whose_session_is_already_stored_is_only_removed() = runBlocking {
        val store = Store()
        store.save(session("a", reps = 3))
        drafts().write(session("a", reps = 3))

        assertEquals(DraftRecovery.AlreadySaved("a"), drafts().recover(store).single())
        assertEquals(1, store.saved.size)
        assertTrue(draftFiles().isEmpty())
    }

    @Test
    fun a_discarded_draft_is_not_recovered() = runBlocking {
        val store = Store()
        drafts().write(session("a", reps = 2))
        drafts().discard("a")

        assertTrue(drafts().recover(store).isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    /** Switching camera ends one set and starts another: discarding one never touches the other. */
    @Test
    fun two_sets_drafts_are_kept_and_discarded_apart() = runBlocking {
        val store = Store()
        drafts().write(session("first", reps = 2))
        drafts().write(session("second", reps = 1))
        drafts().discard("first")

        val recovered = drafts().recover(store).map { (it as DraftRecovery.Recovered).session.sessionId }
        assertEquals(listOf("second"), recovered)
    }

    @Test
    fun an_unreadable_draft_is_set_aside_and_never_saved_or_retried() = runBlocking {
        val store = Store()
        File(root, "kinesis").mkdirs()
        File(root, "kinesis/broken.json").writeText("{\"format\":\"kinesis-export\",\"formatVersion\":2,\"sess")

        val outcome = drafts().recover(store).single() as DraftRecovery.Unreadable

        assertTrue("the unreadable draft was not kept", outcome.keptAs.isFile)
        assertTrue(store.saved.isEmpty())
        assertTrue("retried", drafts().recover(store).isEmpty())
    }

    /** P3-60: each account's drafts in its own directory, never another's. */
    @Test
    fun two_accounts_drafts_never_mix() = runBlocking {
        val owner = SetDrafts.directoryName("kinesis.db")
        val friend = SetDrafts.directoryName("kinesis-M3kfriend00000000000000000bb.db")
        assertNotEquals(owner, friend)

        drafts(owner).write(session("owners-set", reps = 2))
        assertTrue(drafts(friend).recover(Store()).isEmpty())
        assertEquals(1, drafts(owner).recover(Store()).size)
    }
}
