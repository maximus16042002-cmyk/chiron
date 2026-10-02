package com.kinesis.core.analysis.export

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.JsonNumber
import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord

/**
 * P5's export: every session, set, repetition and hold, as one JSON document
 * and as three CSV tables - CLAUDE.md section 15, DECISIONS.md P3-25. The
 * format is CONTRACTS.md section 10.
 *
 * ### What it carries, and what it does not
 *
 * Measurement only. Sessions, sets and records exactly as stored, each set's
 * record and valid counts, and the calibration baselines. No `AnalysisResult`:
 * those are interpretation, recomputed from a record and a baseline rather than
 * stored, and both of those are here. No finding, so no claim level above 1.
 *
 * ### Two rules it keeps
 *
 * **Left and right are never one value.** Every bilateral quantity is a
 * `{"left", "right"}` pair in JSON and two columns in CSV (CLAUDE.md section 3).
 * A side that was not measured is `null` in JSON and an empty cell in CSV,
 * never zero and never the other side's value.
 *
 * **A repetition is the facade's record, verbatim.** Each entry of a set's
 * `reps` is written by the same code that writes `KinesisEngine.pushFrame`'s
 * records, and each baseline by `BaselineJson`, so an exported set can go
 * straight back into `computeBaseline`, and there is one format for each.
 *
 * ### Times
 *
 * `startedAtEpochMs` fields are UTC epoch milliseconds. A record's `startMs` and
 * `endMs` are milliseconds on the clock that stamped the frames - the camera's,
 * on a phone - which the export does not relate to the epoch: only differences
 * between records of one set are meaningful. A harness that controls its frame
 * clock, as P7's will, knows what it is.
 *
 * Not on the real-time path: it runs over stored history, when asked.
 */
object SessionExport {

    const val FORMAT: String = "kinesis-export"

    /**
     * Raised whenever a field is added, removed, renamed or changes meaning. 2: each repetition
     * record carries its form summary - DECISIONS.md P3-44.
     */
    const val FORMAT_VERSION: Int = 2

    /** The whole history as one JSON document. */
    fun json(sessions: List<SessionRecord>, baselines: List<CalibrationBaseline>, exportedAtEpochMs: Double): String =
        EngineJson.obj {
            field("format", FORMAT)
            field("formatVersion", FORMAT_VERSION)
            field("exportedAtEpochMs", exportedAtEpochMs)
            raw("sessions", sessions.joinToString(",", "[", "]") { session(it) })
            raw("baselines", baselines.joinToString(",", "[", "]") { BaselineJson.write(it) })
        }

    /** The same history as three flat tables. The 64-point curves are in the JSON only. */
    fun csv(sessions: List<SessionRecord>): ExportCsv {
        val sets = Table(SET_COLUMNS)
        val reps = Table(REP_COLUMNS)
        val holds = Table(HOLD_COLUMNS)
        for (session in sessions) {
            for ((position, set) in session.sets.withIndex()) {
                val setIndex = position + 1
                sets.row(
                    text(session.sessionId),
                    number(session.startedAtEpochMs),
                    session.utcOffsetMinutes.toString(),
                    setIndex.toString(),
                    text(set.exerciseId),
                    mode(set),
                    number(set.startedAtEpochMs),
                    recordCount(set).toString(),
                    set.completedUnits.toString(),
                )
                when (set) {
                    is RepetitionSetRecord -> for (rep in set.reps) reps.row(repCells(session, setIndex, set, rep))
                    is HoldSetRecord -> for ((i, hold) in set.holds.withIndex()) holds.row(holdCells(session, setIndex, set, i + 1, hold))
                }
            }
        }
        return ExportCsv(sets = sets.text(), reps = reps.text(), holds = holds.text())
    }

    // --- JSON -----------------------------------------------------------------------

    private fun session(session: SessionRecord): String = EngineJson.obj {
        field("sessionId", session.sessionId)
        field("startedAtEpochMs", session.startedAtEpochMs)
        field("utcOffsetMinutes", session.utcOffsetMinutes)
        raw(
            "sets",
            session.sets.withIndex().joinToString(",", "[", "]") { (position, set) -> setJson(position + 1, set) },
        )
    }

    private fun setJson(setIndex: Int, set: SetRecord): String = EngineJson.obj {
        field("setIndex", setIndex)
        field("exerciseId", set.exerciseId)
        field("mode", mode(set))
        field("startedAtEpochMs", set.startedAtEpochMs)
        field("recordCount", recordCount(set))
        // The rep count, or the held count: records that completed as VALID.
        field("validCount", set.completedUnits)
        when (set) {
            is RepetitionSetRecord -> raw("reps", set.reps.joinToString(",", "[", "]") { EngineJson.rep(it) })
            is HoldSetRecord -> raw("holds", set.holds.joinToString(",", "[", "]") { EngineJson.hold(it) })
        }
    }

    private fun mode(set: SetRecord): String = when (set) {
        is RepetitionSetRecord -> "repetition"
        is HoldSetRecord -> "hold"
    }

    private fun recordCount(set: SetRecord): Int = when (set) {
        is RepetitionSetRecord -> set.reps.size
        is HoldSetRecord -> set.holds.size
    }

    // --- CSV ------------------------------------------------------------------------

    private val SET_COLUMNS = listOf(
        "sessionId", "sessionStartedAtEpochMs", "utcOffsetMinutes",
        "setIndex", "exerciseId", "mode", "setStartedAtEpochMs",
        "recordCount", "validCount",
    )

    private val REP_COLUMNS = listOf(
        "sessionId", "setIndex", "exerciseId",
        "repIndex", "attributedSide", "validity", "startMs", "endMs",
        "rangeOfMotionLeft", "rangeOfMotionRight",
        "peakValueLeft", "peakValueRight",
        "troughValueLeft", "troughValueRight",
        "descentMs", "ascentMs",
    )

    private val HOLD_COLUMNS = listOf(
        "sessionId", "setIndex", "exerciseId",
        "holdIndex", "validity", "startMs", "endMs",
        "totalHoldMs", "longestUnbrokenMs", "breakCount",
        "meanAbsDeviation", "maxAbsDeviation", "coverage",
        "meanHeldValue", "heldValueSd",
    )

    private fun repCells(session: SessionRecord, setIndex: Int, set: SetRecord, rep: RepRecord): List<String> = listOf(
        text(session.sessionId),
        setIndex.toString(),
        text(set.exerciseId),
        rep.repIndex.toString(),
        rep.attributedSide?.name ?: "",
        rep.validity.name,
        number(rep.startMs),
        number(rep.endMs),
        // One cell per side, always: CLAUDE.md section 3.
        number(rep.rangeOfMotion.left),
        number(rep.rangeOfMotion.right),
        number(rep.peakValue.left),
        number(rep.peakValue.right),
        number(rep.troughValue.left),
        number(rep.troughValue.right),
        number(rep.descentMs),
        number(rep.ascentMs),
    )

    private fun holdCells(session: SessionRecord, setIndex: Int, set: SetRecord, holdIndex: Int, hold: HoldRecord): List<String> = listOf(
        text(session.sessionId),
        setIndex.toString(),
        text(set.exerciseId),
        holdIndex.toString(),
        hold.validity.name,
        number(hold.startMs),
        number(hold.endMs),
        number(hold.totalHoldMs),
        number(hold.longestUnbrokenMs),
        hold.breakCount.toString(),
        number(hold.meanAbsDeviation),
        number(hold.maxAbsDeviation),
        number(hold.coverage),
        number(hold.meanHeldValue),
        number(hold.heldValueSd),
    )

    /** A measurement as a cell: the JSON text of the number, or empty when not measured. */
    private fun number(value: Double): String = if (value.isNaN() || value.isInfinite()) "" else JsonNumber.format(value)

    /** RFC 4180: quoted when it holds a comma, a quote or a line break, quotes doubled. */
    private fun text(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private class Table(private val columns: List<String>) {
        private val out = StringBuilder().append(columns.joinToString(",")).append(CRLF)

        fun row(vararg cells: String) = row(cells.asList())

        fun row(cells: List<String>) {
            check(cells.size == columns.size) { "${cells.size} cells for ${columns.size} columns" }
            out.append(cells.joinToString(",")).append(CRLF)
        }

        fun text(): String = out.toString()
    }

    private const val CRLF = "\r\n"
}

/** The three CSV tables of an export: one row per set, per repetition, per hold. */
class ExportCsv(val sets: String, val reps: String, val holds: String)
