package com.kinesis.core.analysis.export

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord

/**
 * P5's export: session history as JSON and CSV - CLAUDE.md section 15,
 * DECISIONS.md P3-21.
 *
 * Measurement only, exactly as recorded. Nothing here interprets, filters or
 * derives beyond what the records already carry, so an export read back is the
 * same evidence the dashboard was built from.
 *
 * **JSON** is the complete record: every session, every set, every repetition
 * and hold, including the resampled signals. Each repetition and hold is written
 * by the facade's own [EngineJson], so a record exported here and a record
 * returned by `KinesisEngine` are the same object shape.
 *
 * **CSV** is for a spreadsheet, so it is two files, not one: [repsCsv] and
 * [holdsCsv]. A single table would give every hold a repetition column and
 * every repetition a hold time - CLAUDE.md section 11. The resampled signals are
 * left out of CSV; they are 64 numbers a side, and the JSON has them.
 *
 * **Left and right are separate columns**, `_left` and `_right`, never one
 * combined value - CLAUDE.md section 3. A side that was not measured is `NaN`
 * inside Kotlin; it is an empty cell in CSV and `null` in JSON, never 0, which
 * would read as a measurement.
 *
 * `set_index` counts sets within a session from 1, in stored order.
 */
object SessionExport {

    const val FORMAT: String = "kinesis-sessions"
    const val VERSION: Int = 1

    fun json(sessions: List<SessionRecord>): String {
        val out = StringBuilder()
        out.append("{\"format\":").append(quote(FORMAT))
            .append(",\"version\":").append(VERSION)
            .append(",\"sessions\":[")
        sessions.forEachIndexed { i, session ->
            if (i > 0) out.append(',')
            out.append("{\"sessionId\":").append(quote(session.sessionId))
                .append(",\"startedAtEpochMs\":").append(jsonNumber(session.startedAtEpochMs))
                .append(",\"utcOffsetMinutes\":").append(session.utcOffsetMinutes)
                .append(",\"localDay\":").append(session.localDay.dayNumber)
                .append(",\"sets\":[")
            session.sets.forEachIndexed { j, set ->
                if (j > 0) out.append(',')
                out.append("{\"exerciseId\":").append(quote(set.exerciseId))
                    .append(",\"startedAtEpochMs\":").append(jsonNumber(set.startedAtEpochMs))
                when (set) {
                    is RepetitionSetRecord -> {
                        out.append(",\"mode\":\"repetition\",\"reps\":")
                        set.reps.joinTo(out, ",", "[", "]") { EngineJson.rep(it) }
                    }
                    is HoldSetRecord -> {
                        out.append(",\"mode\":\"hold\",\"holds\":")
                        set.holds.joinTo(out, ",", "[", "]") { EngineJson.hold(it) }
                    }
                }
                out.append('}')
            }
            out.append("]}")
        }
        return out.append("]}").toString()
    }

    private val REP_HEADER = listOf(
        "session_id", "set_index", "exercise_id", "rep_index", "attributed_side",
        "start_ms", "end_ms", "duration_ms", "validity",
        "rom_left", "rom_right", "peak_left", "peak_right", "trough_left", "trough_right",
        "descent_ms", "ascent_ms",
    )

    private val HOLD_HEADER = listOf(
        "session_id", "set_index", "exercise_id", "hold_index",
        "start_ms", "end_ms", "validity",
        "total_hold_ms", "longest_unbroken_ms", "break_count",
        "mean_abs_deviation", "max_abs_deviation", "coverage",
    )

    /** One row per repetition, across every repetition set. */
    fun repsCsv(sessions: List<SessionRecord>): String {
        val out = StringBuilder()
        row(out, REP_HEADER)
        for (session in sessions) {
            session.sets.forEachIndexed { j, set ->
                if (set !is RepetitionSetRecord) return@forEachIndexed
                for (rep in set.reps) {
                    row(
                        out,
                        listOf(session.sessionId, (j + 1).toString(), set.exerciseId, rep.repIndex.toString(),
                            rep.attributedSide?.name ?: "",
                            number(rep.startMs), number(rep.endMs), number(rep.durationMs), rep.validity.name) +
                            sides(rep.rangeOfMotion) + sides(rep.peakValue) + sides(rep.troughValue) +
                            listOf(number(rep.descentMs), number(rep.ascentMs)),
                    )
                }
            }
        }
        return out.toString()
    }

    /** One row per hold, across every hold set. Carries no repetition field. */
    fun holdsCsv(sessions: List<SessionRecord>): String {
        val out = StringBuilder()
        row(out, HOLD_HEADER)
        for (session in sessions) {
            session.sets.forEachIndexed { j, set ->
                if (set !is HoldSetRecord) return@forEachIndexed
                set.holds.forEachIndexed { k, hold ->
                    row(
                        out,
                        listOf(session.sessionId, (j + 1).toString(), set.exerciseId, (k + 1).toString(),
                            number(hold.startMs), number(hold.endMs), hold.validity.name,
                            number(hold.totalHoldMs), number(hold.longestUnbrokenMs), hold.breakCount.toString(),
                            number(hold.meanAbsDeviation), number(hold.maxAbsDeviation), number(hold.coverage)),
                    )
                }
            }
        }
        return out.toString()
    }

    /** The two sides as two cells, left then right. Not a reducer: nothing is combined. */
    private fun sides(signal: BilateralSignal): List<String> = listOf(number(signal.left), number(signal.right))

    /** An unmeasured value is empty, never 0. */
    private fun number(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "" else value.toString()

    private fun jsonNumber(value: Double): String = number(value).ifEmpty { "null" }

    /** RFC 4180 rows: CRLF-terminated, a field quoted when it holds a comma, quote or line break. */
    private fun row(out: StringBuilder, fields: List<String>) {
        fields.forEachIndexed { i, field ->
            if (i > 0) out.append(',')
            if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                out.append('"').append(field.replace("\"", "\"\"")).append('"')
            } else {
                out.append(field)
            }
        }
        out.append("\r\n")
    }

    /** A JSON string literal. Ids come from the platform, so they are escaped rather than trusted. */
    private fun quote(value: String): String {
        val out = StringBuilder("\"")
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }
}
