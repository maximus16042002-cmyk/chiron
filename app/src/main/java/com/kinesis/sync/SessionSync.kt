package com.kinesis.sync

import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.SessionRepository

/**
 * Pushing and pulling sessions - DECISIONS.md P3-14.
 *
 * ### Room is the source of truth; this is additive
 *
 * CLAUDE.md section 16 as amended: *"A session MUST save locally with no network
 * available; sync is additive and never a precondition."* So every method here
 * is something that happens **after** a local write has already succeeded, and
 * nothing here can prevent one.
 */

/** Why a push did not land. */
enum class SyncFailure {
    /** No usable network. The ordinary case, and the one worth retrying. */
    NETWORK_UNAVAILABLE,

    /** Signed out, or the rules rejected the write. */
    NOT_PERMITTED,

    /** Anything else. */
    UNKNOWN,
}

sealed interface SyncResult {
    data object Pushed : SyncResult
    data class Failed(val reason: SyncFailure) : SyncResult
}

/**
 * The remote half, behind an interface so the merge can be tested without
 * Firestore.
 */
interface RemoteSessionStore {

    /** Writes one session under the signed-in user's own document tree. */
    suspend fun push(uid: String, session: SessionRecord): SyncResult

    /** Every session the account holds. */
    suspend fun pull(uid: String): List<SessionRecord>
}

/**
 * Merges remote history into local storage on sign-in - DECISIONS.md P3-14.
 *
 * ### Why a union is sufficient, verified rather than assumed
 *
 * The brief asked for this to be checked rather than taken from the documents,
 * and it was. Three things had to hold, and all three do:
 *
 * - `SessionRecord`, `SetRecord`, `RepRecord` and `HoldRecord` are entirely
 *   `val` and `List`. There is no `var` and no `MutableList` anywhere in them.
 * - The DAO has no `@Update` and no per-session delete - only `@Insert` and a
 *   wholesale `deleteAllSessions`.
 * - `SessionRecorder.finish()` writes once, and nothing re-opens a session
 *   afterwards.
 *
 * So a session is immutable from the moment it is written, two devices can never
 * hold different versions of the same `sessionId`, and the merge is a union
 * keyed by id rather than a reconciliation.
 *
 * ### The one thing that check did turn up
 *
 * `RoomSessionRepository.save` uses a bare `@Insert`, and `SessionEntity`'s
 * primary key is the `sessionId`. Re-inserting a session that already exists
 * locally would therefore **throw a constraint violation**, not be ignored - so
 * pulling a session this device already has would crash the merge.
 *
 * The union is filtered against what is already stored before anything is
 * written. That is a real defect the assumption would have hidden, and it is why
 * the brief asked for the check.
 */
class SessionMerge(
    private val local: SessionRepository,
    private val remote: RemoteSessionStore,
) {

    /**
     * Pulls remote sessions and stores the ones this device does not have.
     *
     * @return how many were new.
     */
    suspend fun pullMissing(uid: String): Int {
        val known = local.allSessions().map { it.sessionId }.toHashSet()
        var added = 0
        for (session in remote.pull(uid)) {
            if (known.add(session.sessionId)) {
                local.save(session)
                added++
            }
        }
        return added
    }

    /**
     * Pushes local sessions the account does not have.
     *
     * Failures are returned rather than thrown; the caller queues them.
     */
    suspend fun pushMissing(uid: String): List<SessionRecord> {
        val remoteIds = remote.pull(uid).map { it.sessionId }.toHashSet()
        val unpushed = local.allSessions().filter { it.sessionId !in remoteIds }
        val failed = mutableListOf<SessionRecord>()
        for (session in unpushed) {
            if (remote.push(uid, session) !is SyncResult.Pushed) failed += session
        }
        return failed
    }
}
