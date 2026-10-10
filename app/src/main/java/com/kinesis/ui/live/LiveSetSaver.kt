package com.kinesis.ui.live

import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRecorder
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.SetDrafts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** What became of a set when it ended. */
sealed interface SetSaveOutcome {

    /**
     * Written locally. Sync, if signed in, was scheduled after. Carries the
     * session as written, for the set summary or the calibration that follows
     * END SET - DECISIONS.md P3-22.
     */
    data class Saved(val session: SessionRecord) : SetSaveOutcome

    /** Nobody was ever seen, or nothing was recorded (P3-70), so there was no set to write. */
    data object NothingToSave : SetSaveOutcome

    /** The local write failed, for whatever reason (DECISIONS.md P3-63). */
    data class Failed(val reason: String) : SetSaveOutcome
}

/**
 * Ends a live set and writes it - DECISIONS.md P3-16.
 *
 * Goes through the existing path unchanged: [SessionRecorder], then whatever
 * [SessionRepository] the app was built with - `SyncingSessionRepository` in
 * front of Room - which writes locally first and only then schedules an upload
 * (P3-14). No network is involved in the write.
 *
 * ### One set, one session
 *
 * The repository is append-only: a saved session is never updated, locally or
 * in Firestore's rules. So a set is written as its own session the moment it
 * ends, rather than held open waiting for more sets that a crash could lose.
 *
 * ### Ended once
 *
 * The end-set control and leaving the screen both call [endSet]; the second
 * call returns the first call's result, so a set is never written twice. The
 * set is closed synchronously, at the moment of the call, so frames that arrive
 * while the write is under way are not part of it. The write itself runs on
 * [scope], which the app gives a lifetime longer than the screen's - leaving the
 * screen must not cancel the save of the set it just ended.
 *
 * ### A failure is always reported - DECISIONS.md P3-63
 *
 * End set waits for the write and shows a failure on the screen: it ends the set
 * *watching*. Leaving the screen ends it with nobody watching, so a failure then
 * goes to [onUnreportedFailure], which the app shows as a toast. A watch, once
 * taken, is kept, so a failure is never reported twice.
 *
 * ### A draft while the set runs - DECISIONS.md P3-66
 *
 * With [drafts], every completed repetition record replaces the draft with the
 * session ending the set then would save. A normal end discards it; a failed
 * save keeps it, for [SetDrafts.recover] to retry when the account next opens.
 *
 * Call from the main thread.
 */
class LiveSetSaver(
    private val engine: LiveEngine,
    private val repository: SessionRepository,
    private val scope: CoroutineScope,
    private val sessionId: String,
    private val startedAtEpochMs: Double,
    private val utcOffsetMinutes: Int,
    private val onSaved: () -> Unit,
    /** A failed write no screen was watching, with its reason. Called on [scope]. */
    private val onUnreportedFailure: (String) -> Unit = {},
    /** This account's draft of the set in progress (P3-66); `null` keeps none. */
    private val drafts: SetDrafts? = null,
) {

    init {
        if (drafts != null) {
            engine.observeRepetitions { exerciseId, records ->
                drafts.write(
                    SessionRecord(
                        sessionId = sessionId,
                        startedAtEpochMs = startedAtEpochMs,
                        utcOffsetMinutes = utcOffsetMinutes,
                        sets = listOf(RepetitionSetRecord(exerciseId, startedAtEpochMs, records)),
                    ),
                )
            }
        }
    }

    private var outcome: Deferred<SetSaveOutcome>? = null

    @Volatile
    private var watched = false

    /** @param watching true when the caller will show the outcome itself (End set). */
    fun endSet(watching: Boolean = false): Deferred<SetSaveOutcome> {
        if (watching) watched = true
        outcome?.let { return it }
        val recorder = SessionRecorder(sessionId, startedAtEpochMs, utcOffsetMinutes, repository)
        val closed = engine.closeSetInto(recorder, startedAtEpochMs)
        val write = scope.async {
            if (!closed) {
                drafts?.discard(sessionId)
                return@async SetSaveOutcome.NothingToSave
            }
            val session = try {
                recorder.finish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Any failure, not only SQLiteException: before P3-63 anything else escaped.
                val reason = error.message ?: "the local database refused the write"
                if (!watched) onUnreportedFailure(reason)
                return@async SetSaveOutcome.Failed(reason)
            } ?: run {
                drafts?.discard(sessionId)
                return@async SetSaveOutcome.NothingToSave
            }
            // Saved: the draft is no longer needed. A failure above returned first and kept it.
            drafts?.discard(sessionId)
            onSaved()
            SetSaveOutcome.Saved(session)
        }
        outcome = write
        return write
    }
}
