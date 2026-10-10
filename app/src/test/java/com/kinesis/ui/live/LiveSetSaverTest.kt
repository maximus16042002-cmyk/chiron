package com.kinesis.ui.live

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.storage.DraftRecovery
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.SetDrafts
import com.kinesis.sync.SyncQueue
import com.kinesis.sync.SyncingSessionRepository
import com.kinesis.ui.theme.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A live set reaching storage - DECISIONS.md P3-16.
 *
 * Driven end to end on the JVM: synthetic frames through the real engine and
 * core analysis, then the real SessionRecorder and SyncingSessionRepository in
 * front of a local store. The sync queue throws on every call, which is what no
 * network looks like from here - the set must land locally regardless
 * (CLAUDE.md section 16).
 */
class LiveSetSaverTest {

    private class LocalStore : SessionRepository {
        val saved = mutableListOf<SessionRecord>()
        override suspend fun allSessions(): List<SessionRecord> = saved.toList()
        override suspend fun save(session: SessionRecord) { saved += session }
        override suspend fun clear() = saved.clear()
    }

    private class NoNetwork : SyncQueue {
        var attempts = 0
        override fun enqueue(sessionId: String) {
            attempts++
            throw IllegalStateException("no network")
        }
    }

    /** A local store whose write fails, as a full disk or a closed database would. */
    private class FailingStore : SessionRepository {
        override suspend fun allSessions(): List<SessionRecord> = emptyList()
        override suspend fun save(session: SessionRecord) = throw IllegalStateException("disk full")
        override suspend fun clear() = Unit
    }

    private object NoSkeleton : SkeletonSink {
        override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) = Unit
        override fun clear() = Unit
    }

    private val local = LocalStore()
    private val queue = NoNetwork()
    private val repository = SyncingSessionRepository(local, queue) { true }
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var clockMs = 0L
    private var savedCallbacks = 0
    private val unreported = mutableListOf<String>()

    private val draftDirectory: File = Files.createTempDirectory("live-drafts").toFile()
    private val drafts = SetDrafts(draftDirectory, Runnable::run)
    private fun draftFiles() = draftDirectory.listFiles { f -> f.name.endsWith(".json") }.orEmpty().toList()

    private fun saver(engine: LiveEngine, store: SessionRepository = repository) = LiveSetSaver(
        engine = engine,
        repository = store,
        scope = scope,
        sessionId = "set-1",
        startedAtEpochMs = 1_760_000_000_000.0,
        utcOffsetMinutes = 330,
        onSaved = { savedCallbacks++ },
        onUnreportedFailure = { unreported += it },
        drafts = drafts,
    )

    private fun squatEngine() = LiveRepetitionEngine(ArchetypeProfiles.squat, LiveAnalysisBridge("squat") { }, NoSkeleton)

    private fun LiveEngine.feed(knees: Double, frames: Int = 1) = repeat(frames) {
        onLandmarks(body(knees), clockMs)
        clockMs += 33
    }

    private fun LiveEngine.squat(reps: Int) {
        feed(170.0, frames = 10)
        repeat(reps) {
            for (step in 1..30) feed(170.0 - 100.0 * step / 30.0)
            for (step in 1..30) feed(70.0 + 100.0 * step / 30.0)
            feed(170.0, frames = 10)
        }
    }

    @Test
    fun ending_a_set_mid_repetition_saves_it_locally_with_no_network() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 2)
        for (step in 1..15) engine.feed(170.0 - 100.0 * step / 30.0)

        val outcome = saver(engine).endSet().await()

        // The saved session is handed back, for the set summary or the calibration
        // that follows END SET - DECISIONS.md P3-22.
        assertSame(local.saved.singleOrNull(), (outcome as SetSaveOutcome.Saved).session)
        assertEquals("the set never reached local storage", 1, local.saved.size)
        val set = local.saved.single().sets.single() as RepetitionSetRecord
        assertEquals("squat", set.exerciseId)
        // The near-side squat (DECISIONS.md P3-68): the unfinished squat is reported once per knee
        // still under way, and counted by neither.
        assertEquals(
            listOf(RepValidity.VALID, RepValidity.VALID, RepValidity.INCOMPLETE, RepValidity.INCOMPLETE),
            set.reps.map { it.validity },
        )
        assertEquals(2, set.completedUnits)
        assertEquals("the upload was not even attempted after the local write", 1, queue.attempts)
        assertEquals(1, savedCallbacks)
    }

    /** The end-set control and then leaving the screen: one set, written once. */
    @Test
    fun ending_the_same_set_twice_writes_it_once() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 1)
        val saver = saver(engine)

        val first = saver.endSet()
        val second = saver.endSet()

        assertSame(first, second)
        first.await()
        assertEquals(1, local.saved.size)
    }

    /**
     * DECISIONS.md P3-63: leaving the screen ends a set with nobody watching the write. If it
     * fails, the user must still be told, through the callback the app shows as a toast.
     */
    @Test
    fun a_failed_write_nobody_watches_is_reported_once_with_its_reason() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 1)

        val outcome = saver(engine, FailingStore()).endSet().await()

        assertTrue(outcome is SetSaveOutcome.Failed)
        assertEquals(listOf("disk full"), unreported)
        assertEquals(0, savedCallbacks)
    }

    /** End set watches the write and shows the failure itself, so it is not reported twice. */
    @Test
    fun a_failed_write_the_screen_watches_is_not_reported_again() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 1)
        val saver = saver(engine, FailingStore())

        val outcome = saver.endSet(watching = true).await()
        saver.endSet() // then the screen is left, as it is after End set

        assertTrue(outcome is SetSaveOutcome.Failed)
        assertTrue("reported twice: $unreported", unreported.isEmpty())
    }

    /** Before P3-63 only SQLiteException became "not saved"; anything else escaped. */
    @Test
    fun a_failure_of_any_type_is_a_failed_outcome_not_an_escaped_exception() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 1)

        val outcome = saver(engine, FailingStore()).endSet(watching = true).await()

        assertEquals("disk full", (outcome as SetSaveOutcome.Failed).reason)
    }

    @Test
    fun a_successful_write_reports_nothing() = runBlocking {
        val engine = squatEngine()
        engine.squat(reps = 1)

        saver(engine).endSet().await()

        assertTrue(unreported.isEmpty())
        assertEquals(1, savedCallbacks)
    }

    /** DECISIONS.md P3-66: while the set runs its draft holds every completed record; a normal end removes it. */
    @Test
    fun the_draft_follows_the_set_and_is_removed_when_the_set_is_saved() = runBlocking {
        val engine = squatEngine()
        val saver = saver(engine)
        engine.squat(reps = 2)

        val draft = SessionExport.readSessions(draftFiles().single().readText()).single()
        assertEquals("set-1", draft.sessionId)
        assertEquals(
            listOf(RepValidity.VALID, RepValidity.VALID),
            (draft.sets.single() as RepetitionSetRecord).reps.map { it.validity },
        )

        saver.endSet().await()
        assertTrue("the draft outlived the saved set", draftFiles().isEmpty())
    }

    /** A failed save keeps the draft, and the next launch's recovery saves it. */
    @Test
    fun a_failed_save_keeps_the_draft_and_recovery_saves_it() = runBlocking {
        val engine = squatEngine()
        val saver = saver(engine, FailingStore())
        engine.squat(reps = 2)

        assertTrue(saver.endSet().await() is SetSaveOutcome.Failed)
        assertEquals(1, draftFiles().size)

        val recovered = drafts.recover(local).single() as DraftRecovery.Recovered
        assertEquals("set-1", recovered.session.sessionId)
        assertEquals(2, (local.saved.single().sets.single() as RepetitionSetRecord).completedUnits)
    }

    /** The set ends at the tap: later frames reach neither the readout nor the skeleton. */
    @Test
    fun frames_after_the_set_ended_are_ignored() = runBlocking {
        var drawn = 0
        val skeleton = object : SkeletonSink {
            override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) { drawn++ }
            override fun clear() { drawn++ }
        }
        val bridge = LiveAnalysisBridge("squat") { }
        val engine = LiveRepetitionEngine(ArchetypeProfiles.squat, bridge, skeleton)
        engine.squat(reps = 1)
        saver(engine).endSet().await()
        val drawnAtEnd = drawn
        val framesAtEnd = bridge.frameCount

        engine.squat(reps = 3)
        engine.onNoPose(clockMs)

        assertEquals("frames after the end were drawn", drawnAtEnd, drawn)
        assertEquals("frames after the end reached the readout", framesAtEnd, bridge.frameCount)
        val set = local.saved.single().sets.single() as RepetitionSetRecord
        assertEquals(1, set.completedUnits)
    }

    /** Opening the camera and leaving without anyone in shot is not a set. */
    @Test
    fun a_set_in_which_nobody_was_seen_is_not_saved() = runBlocking {
        val engine = squatEngine()
        repeat(60) { engine.onNoPose(clockMs); clockMs += 33 }

        val outcome = saver(engine).endSet().await()

        assertSame(SetSaveOutcome.NothingToSave, outcome)
        assertTrue(local.saved.isEmpty())
        assertEquals(0, savedCallbacks)
    }

    /** DECISIONS.md P3-70: someone was seen, nothing was recorded - nothing to save. */
    @Test
    fun a_repetition_set_with_no_record_at_all_is_not_saved() = runBlocking {
        val engine = squatEngine()
        engine.feed(170.0, frames = 60) // standing, seen, never moving

        val outcome = saver(engine).endSet().await()

        assertSame(SetSaveOutcome.NothingToSave, outcome)
        assertTrue(local.saved.isEmpty())
        assertTrue("an empty set left a draft", draftFiles().isEmpty())
    }

    /** A turned-back repetition is recorded movement, so its set is still saved. */
    @Test
    fun a_set_holding_only_a_turned_back_repetition_is_still_saved() = runBlocking {
        val engine = squatEngine()
        engine.feed(170.0, frames = 10)
        for (step in 1..15) engine.feed(170.0 - 40.0 * step / 15.0)
        for (step in 1..15) engine.feed(130.0 + 40.0 * step / 15.0)
        engine.feed(170.0, frames = 10)

        val outcome = saver(engine).endSet().await()

        val set = (outcome as SetSaveOutcome.Saved).session.sets.single() as RepetitionSetRecord
        assertTrue(set.reps.isNotEmpty())
        assertTrue(set.reps.all { it.validity == RepValidity.ABORTED })
        assertEquals(0, set.completedUnits)
    }

    /** DECISIONS.md P3-70: a hold whose analyser measured no frame is not saved. */
    @Test
    fun a_hold_never_measured_is_not_saved() = runBlocking {
        val engine = LiveHoldEngine(ArchetypeProfiles.plank, LiveHoldBridge("plank") { }, NoSkeleton)
        val unseen = Landmarks.fromPacked(DoubleArray(Landmarks.PACKED_SIZE)) // a pose, every landmark invisible
        repeat(60) {
            engine.onLandmarks(unseen, clockMs)
            clockMs += 33
        }

        assertSame(SetSaveOutcome.NothingToSave, saver(engine).endSet().await())
        assertTrue(local.saved.isEmpty())
    }

    @Test
    fun a_hold_is_saved_as_a_hold_and_never_as_repetitions() = runBlocking {
        val engine = LiveHoldEngine(ArchetypeProfiles.plank, LiveHoldBridge("plank") { }, NoSkeleton)
        repeat(150) {
            engine.onLandmarks(plank(), clockMs)
            clockMs += 33
        }

        val outcome = saver(engine).endSet().await()
        assertSame(local.saved.single(), (outcome as SetSaveOutcome.Saved).session)
        val set = local.saved.single().sets.single() as HoldSetRecord
        assertEquals("plank", set.exerciseId)
        assertEquals(HoldValidity.VALID, set.holds.single().validity)
        assertTrue(set.holds.single().totalHoldMs > 4_000.0)
    }

    // --- Synthetic frames ---------------------------------------------------------------

    private fun body(knees: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        val r = knees * PI / 180.0
        put(LandmarkId.LEFT_SHOULDER, 0.45, 0.25)
        put(LandmarkId.RIGHT_SHOULDER, 0.55, 0.25)
        put(LandmarkId.LEFT_HIP, 0.45, 0.55)
        put(LandmarkId.RIGHT_HIP, 0.55, 0.55)
        put(LandmarkId.LEFT_KNEE, 0.45, 0.72)
        put(LandmarkId.RIGHT_KNEE, 0.55, 0.72)
        put(LandmarkId.LEFT_ANKLE, 0.45 + 0.15 * sin(r), 0.72 - 0.15 * cos(r))
        put(LandmarkId.RIGHT_ANKLE, 0.55 + 0.15 * sin(r), 0.72 - 0.15 * cos(r))
        return Landmarks.fromPacked(packed)
    }

    /** A straight side-on plank: shoulder, hip, knee and ankle in a line. */
    private fun plank(): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        for ((shoulder, hip, knee, ankle) in listOf(
            listOf(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE),
            listOf(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE),
        )) {
            put(shoulder, 0.20, 0.60)
            put(hip, 0.50, 0.61)
            put(knee, 0.75, 0.62)
            put(ankle, 0.95, 0.63)
        }
        return Landmarks.fromPacked(packed)
    }
}
