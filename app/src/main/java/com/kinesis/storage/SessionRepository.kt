package com.kinesis.storage

import com.kinesis.core.domain.session.SessionRecord

/**
 * Local, on-device storage for completed sessions - DECISIONS.md P3-12.
 *
 * FINAL_ARCHITECTURE section 8 specifies "Local storage only - Room on Android,
 * IndexedDB on web, behind one shared repository interface", and P3-10
 * deliberately did not build it: with no screen to read history, the interface
 * would have been an abstraction nothing consumed, which is the failure P3-8 and
 * P3-9 each found once. A dashboard exists now, so it is built now.
 *
 * ### Why this lives in `:app` and not in the shared core
 *
 * FINAL_ARCHITECTURE says "shared", meaning `commonMain`. That was written when
 * the Windows PWA was in scope; P0-2 cut the React application, and the project
 * owner's direction for this batch was explicit - Android-only, do not
 * over-engineer for multiplatform. Putting it here also keeps a promise the core
 * makes about itself: FINAL_ARCHITECTURE section 3 lists "No I/O, no networking,
 * no threading in the core" and "No coroutines needed in the analysis path" among
 * the properties that make Kotlin/JS viable, and a suspending repository
 * interface in `commonMain` would spend the first of those. DECISIONS.md P3-12
 * records the divergence.
 *
 * ### Read everything, aggregate in memory
 *
 * That is genuinely the access pattern, not a simplification.
 * `Dashboard.summarise` takes a `List<SessionRecord>` and walks all of it, so
 * there is no filter, no join and no index this interface needs to expose today.
 * When P6 calibration and P11 charts arrive there will be real queries, and this
 * is the place they get added.
 */
interface SessionRepository {

    /** Every session ever recorded, oldest first. */
    suspend fun allSessions(): List<SessionRecord>

    /**
     * Persists one completed session.
     *
     * Called when a set finishes, not per repetition - CONTRACTS.md section 7's
     * post-rep path. Nothing on the per-frame path touches storage.
     */
    suspend fun save(session: SessionRecord)

    /** Removes everything. Exists for the settings screen and for tests. */
    suspend fun clear()
}
