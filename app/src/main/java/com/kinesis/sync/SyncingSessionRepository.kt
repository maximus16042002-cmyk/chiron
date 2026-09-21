package com.kinesis.sync

import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRepository

/**
 * Enqueues a session for upload. Implemented by WorkManager in the app, and by
 * a list in tests.
 */
interface SyncQueue {
    /** Records that [sessionId] still needs pushing. Must not block or throw. */
    fun enqueue(sessionId: String)
}

/**
 * Wraps the local repository so a successful local save schedules an upload -
 * DECISIONS.md P3-14.
 *
 * ### The guarantee this class exists to keep
 *
 * CLAUDE.md section 16, as amended: *"A session MUST save locally with no
 * network available; sync is additive and never a precondition."*
 *
 * So the ordering is not incidental. The local write happens **first**, and the
 * enqueue happens only after it returns. If the enqueue fails for any reason it
 * is swallowed, because by then the session is already durably on disk and
 * losing the upload is a retry, while losing the session is losing the user's
 * training.
 *
 * Nothing here touches the network. `SyncQueue` schedules work; it does not do
 * it. That is what keeps a Firestore outage from ever reaching
 * `SessionRecorder`, which is the property `SyncOutageTest` pins.
 *
 * ### Decorator, not a replacement
 *
 * Room stays the source of truth. Everything downstream - the dashboard, the
 * streak rule, the whole post-rep path - still reads `SessionRepository` and
 * cannot tell whether sync exists.
 */
class SyncingSessionRepository(
    private val delegate: SessionRepository,
    private val queue: SyncQueue,
    private val isSignedIn: () -> Boolean,
) : SessionRepository {

    override suspend fun allSessions(): List<SessionRecord> = delegate.allSessions()

    override suspend fun save(session: SessionRecord) {
        // Local first, always. If this throws, nothing was queued and the caller
        // learns the save failed - which is the truth.
        delegate.save(session)

        // Everything past this point is best-effort. The session is on disk.
        if (!isSignedIn()) return
        try {
            queue.enqueue(session.sessionId)
        } catch (error: Exception) {
            // Deliberately swallowed. A failed *enqueue* must not turn a
            // successful local save into a failure - the next sign-in reconciles
            // through SessionMerge.pushMissing anyway.
        }
    }

    override suspend fun clear() = delegate.clear()
}
