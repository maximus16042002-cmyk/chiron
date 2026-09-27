package com.kinesis.ui.live

import android.database.sqlite.SQLiteException
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRecorder
import com.kinesis.storage.SessionRepository
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

    /** Nobody was ever seen, so there was no set to write. */
    data object NothingToSave : SetSaveOutcome

    /** The local database refused the write. */
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
) {

    private var outcome: Deferred<SetSaveOutcome>? = null

    fun endSet(): Deferred<SetSaveOutcome> {
        outcome?.let { return it }
        val recorder = SessionRecorder(sessionId, startedAtEpochMs, utcOffsetMinutes, repository)
        val closed = engine.closeSetInto(recorder, startedAtEpochMs)
        val write = scope.async {
            if (!closed) return@async SetSaveOutcome.NothingToSave
            val session = try {
                recorder.finish()
            } catch (error: SQLiteException) {
                return@async SetSaveOutcome.Failed(error.message ?: "the local database refused the write")
            } ?: return@async SetSaveOutcome.NothingToSave
            onSaved()
            SetSaveOutcome.Saved(session)
        }
        outcome = write
        return write
    }
}
