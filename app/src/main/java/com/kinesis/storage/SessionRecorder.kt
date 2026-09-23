package com.kinesis.storage

import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord
import java.util.TimeZone

/**
 * Collects a session as it happens and writes it once - DECISIONS.md P3-12.
 *
 * This is the path that makes the dashboard real: a set finishes, the records it
 * produced land here, and the next visit to the dashboard reads them back from
 * storage rather than from memory.
 *
 * ### Nothing here is on the per-frame path
 *
 * Records arrive already complete. `BilateralRepTracker` emits a `RepRecord`
 * when a repetition finishes and `HoldAnalyser` emits a `HoldRecord` when a hold
 * ends - a few times a minute, not thirty times a second. CONTRACTS.md section 7
 * calls this the post-rep path, and storage belongs to it.
 *
 * ### One write per session, not per repetition
 *
 * [finish] is the only thing that touches the database. A repetition-by-
 * repetition write would put disk I/O inside a set for no benefit: the dashboard
 * cannot show a session that is still being performed, and a half-written
 * session is worse than an absent one - the streak would count a day on which
 * nothing completed.
 *
 * ### The clock comes from the caller
 *
 * Same reason as `Streaks.summarise` (P3-10): the offset is captured when the
 * session starts, so the day it belongs to is fixed then and does not move if
 * the user travels. A recorder that read the clock itself would be untestable
 * and would re-introduce exactly the bug P3-10 guards against.
 */
class SessionRecorder(
    private val sessionId: String,
    private val startedAtEpochMs: Double,
    private val utcOffsetMinutes: Int,
    private val repository: SessionRepository,
) {

    private val sets = mutableListOf<SetRecord>()

    /** Sets closed so far. The screen shows it; nothing analytical reads it. */
    val setCount: Int get() = sets.size

    /**
     * Closes a repetition set.
     *
     * An empty set is still recorded. The user pointed a camera at themselves and
     * nothing completed, and that is a fact about the session rather than an
     * absence of one - the streak rule already knows that a set with no valid
     * repetitions does not earn a day (P3-10), so nothing is inflated by keeping
     * it.
     */
    fun closeRepetitionSet(exerciseId: String, startedAtEpochMs: Double, reps: List<RepRecord>) {
        sets += RepetitionSetRecord(exerciseId, startedAtEpochMs, reps.toList())
    }

    /** Closes a hold set. */
    fun closeHoldSet(exerciseId: String, startedAtEpochMs: Double, holds: List<HoldRecord>) {
        sets += HoldSetRecord(exerciseId, startedAtEpochMs, holds.toList())
    }

    /**
     * Writes the session.
     *
     * A session with no sets at all is **not** written: opening the app and
     * leaving is not a session, and recording one would put a row in the database
     * that the dashboard would count among its sessions.
     *
     * @return the session that was written, or `null` if there was nothing to write.
     */
    suspend fun finish(): SessionRecord? {
        if (sets.isEmpty()) return null
        val session = SessionRecord(
            sessionId = sessionId,
            startedAtEpochMs = startedAtEpochMs,
            utcOffsetMinutes = utcOffsetMinutes,
            sets = sets.toList(),
        )
        repository.save(session)
        return session
    }
}

/**
 * Minutes east of UTC in force at [epochMs], daylight saving included -
 * DECISIONS.md P3-16. `TimeZone.rawOffset`, which the dashboard used to read,
 * ignores daylight saving; the recorder and the dashboard must agree on which
 * day a session belongs to, so both read this.
 */
fun utcOffsetMinutesAt(epochMs: Long): Int = TimeZone.getDefault().getOffset(epochMs) / 60_000
