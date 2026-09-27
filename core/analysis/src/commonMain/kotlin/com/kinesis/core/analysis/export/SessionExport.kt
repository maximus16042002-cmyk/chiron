package com.kinesis.core.analysis.export

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord

/**
 * JSON and CSV export of recorded sessions - CLAUDE.md section 15, P5.
 *
 * Measurement only, exactly as stored: no `AnalysisResult`, no derived tempo
 * ratio, no reducer. The JSON also carries the calibration baselines, which are
 * stored statistics of the user's own reps, not interpretation. The same two rules Room's mapping (P3-12) and the
 * Firestore document (P3-14) keep hold here too.
 *
 * **Left and right never share a field.** Every bilateral value is a
 * `{"left", "right"}` object in JSON and two columns in CSV.
 *
 * **`NaN` stays "not measured".** It is `null` in JSON and an empty cell in
 * CSV, never `0`. The idle arm of an alternating repetition exports as empty,
 * not as a measured zero.
 *
 * ### Why this is in the shared core
 *
 * It is pure string building over domain records, with no I/O, so it costs the
 * core nothing, and it keeps JSON output in one place: repetitions and holds are
 * written by the same [EngineJson] the `KinesisEngine` facade uses. It is also
 * the input CLAUDE.md section 1's fallback names - "a replay-only web client
 * consuming exported JSON" - so its format belongs with the code that would read
 * it. Writing files and the share sheet stay in `:app`.
 *
 * Runs when the user asks for an export, over history already written. It is on
 * no per-frame path.
 */
object SessionExport {

    /** Identifies the file, so a reader can refuse something that is not one. */
    const val FORMAT = "kinesis-session-export"

    /** Bumped when a field is renamed or removed. Adding one does not bump it. */
    const val SCHEMA_VERSION = 1

    // --- JSON --------------------------------------------------------------------------

    /**
     * Every session, complete, including the resampled signals.
     *
     * [baselines] are the user's calibration baselines (P3-22), exported beside
     * the sessions they were built from.
     *
     * [exportedAtEpochMs] is a parameter rather than read from a clock, so the
     * same history always exports to the same text.
     */
    fun toJson(
        sessions: List<SessionRecord>,
        exportedAtEpochMs: Double,
        baselines: List<CalibrationBaseline> = emptyList(),
    ): String = EngineJson.obj {
        field("format", FORMAT)
        field("schema", SCHEMA_VERSION)
        field("exportedAtEpochMs", exportedAtEpochMs)
        raw("sessions", sessions.joinToString(",", "[", "]", transform = ::session))
        // P3-22's baselines, in the form storage and the facade already use, so
        // BaselineJson.read takes each one back unchanged. JSON only: a baseline
        // is nested statistics and a mean curve per side, not a table row.
        raw("baselines", baselines.joinToString(",", "[", "]") { BaselineJson.write(it) })
    }

    private fun session(session: SessionRecord): String = EngineJson.obj {
        field("sessionId", session.sessionId)
        field("startedAtEpochMs", session.startedAtEpochMs)
        field("utcOffsetMinutes", session.utcOffsetMinutes)
        field("localDate", session.localDay.toIsoDate())
        raw("sets", session.sets.joinToString(",", "[", "]", transform = ::set))
    }

    private fun set(set: SetRecord): String = when (set) {
        is RepetitionSetRecord -> EngineJson.obj {
            field("kind", KIND_REPETITION)
            field("exerciseId", set.exerciseId)
            field("startedAtEpochMs", set.startedAtEpochMs)
            raw("reps", set.reps.joinToString(",", "[", "]") { EngineJson.rep(it) })
        }

        is HoldSetRecord -> EngineJson.obj {
            field("kind", KIND_HOLD)
            field("exerciseId", set.exerciseId)
            field("startedAtEpochMs", set.startedAtEpochMs)
            raw("holds", set.holds.joinToString(",", "[", "]") { EngineJson.hold(it) })
        }
    }

    // --- CSV ---------------------------------------------------------------------------

    /**
     * One row per repetition, with its session and set repeated on each row so
     * the file opens in a spreadsheet with nothing to join.
     *
     * The resampled signals are left out: 64 values per side do not belong in a
     * cell. They are in the JSON export.
     */
    fun repsCsv(sessions: List<SessionRecord>): String {
        val out = StringBuilder()
        row(out, REP_COLUMNS)
        for (session in sessions) {
            session.sets.forEachIndexed { setIndex, set ->
                if (set !is RepetitionSetRecord) return@forEachIndexed
                for (rep in set.reps) row(out, context(session, setIndex, set) + repCells(rep))
            }
        }
        return out.toString()
    }

    /**
     * One row per hold. A separate file from [repsCsv], not a shared one with
     * half its columns empty: a hold has no repetition index, and a table with
     * somewhere to put one invites it - CLAUDE.md section 11.
     */
    fun holdsCsv(sessions: List<SessionRecord>): String {
        val out = StringBuilder()
        row(out, HOLD_COLUMNS)
        for (session in sessions) {
            session.sets.forEachIndexed { setIndex, set ->
                if (set !is HoldSetRecord) return@forEachIndexed
                for (hold in set.holds) row(out, context(session, setIndex, set) + holdCells(hold))
            }
        }
        return out.toString()
    }

    private fun context(session: SessionRecord, setIndex: Int, set: SetRecord): List<String> = listOf(
        session.sessionId,
        session.localDay.toIsoDate(),
        number(session.startedAtEpochMs),
        session.utcOffsetMinutes.toString(),
        (setIndex + 1).toString(),
        set.exerciseId,
        number(set.startedAtEpochMs),
    )

    private fun repCells(rep: RepRecord): List<String> = listOf(
        rep.repIndex.toString(),
        rep.attributedSide?.name ?: "",
        number(rep.startMs),
        number(rep.endMs),
        rep.validity.name,
        number(rep.rangeOfMotion.left),
        number(rep.rangeOfMotion.right),
        number(rep.peakValue.left),
        number(rep.peakValue.right),
        number(rep.troughValue.left),
        number(rep.troughValue.right),
        number(rep.descentMs),
        number(rep.ascentMs),
    )

    private fun holdCells(hold: HoldRecord): List<String> = listOf(
        number(hold.startMs),
        number(hold.endMs),
        hold.validity.name,
        number(hold.totalHoldMs),
        number(hold.longestUnbrokenMs),
        hold.breakCount.toString(),
        number(hold.meanAbsDeviation),
        number(hold.maxAbsDeviation),
        number(hold.coverage),
        number(hold.meanHeldValue),
        number(hold.heldValueSd),
    )

    /** Not measured is an empty cell. Never `0`, and never the text `NaN`. */
    private fun number(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "" else value.toString()

    /** RFC 4180: CRLF line endings, and a cell quoted only when it has to be. */
    private fun row(out: StringBuilder, cells: List<String>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) out.append(',')
            if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"')
            } else {
                out.append(cell)
            }
        }
        out.append("\r\n")
    }

    private const val KIND_REPETITION = "repetition"
    private const val KIND_HOLD = "hold"

    private val CONTEXT_COLUMNS = listOf(
        "session_id",
        "local_date",
        "session_started_at_epoch_ms",
        "utc_offset_minutes",
        "set_number",
        "exercise_id",
        "set_started_at_epoch_ms",
    )

    val REP_COLUMNS: List<String> = CONTEXT_COLUMNS + listOf(
        "rep_index",
        "attributed_side",
        "start_ms",
        "end_ms",
        "validity",
        "range_of_motion_left",
        "range_of_motion_right",
        "peak_value_left",
        "peak_value_right",
        "trough_value_left",
        "trough_value_right",
        "descent_ms",
        "ascent_ms",
    )

    val HOLD_COLUMNS: List<String> = CONTEXT_COLUMNS + listOf(
        "start_ms",
        "end_ms",
        "validity",
        "total_hold_ms",
        "longest_unbroken_ms",
        "break_count",
        "mean_abs_deviation",
        "max_abs_deviation",
        "coverage",
        "mean_held_value",
        "held_value_sd",
    )
}
