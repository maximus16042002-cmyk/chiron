package com.kinesis.export

import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.session.SessionRecord

/** What the user asked to export. */
enum class ExportFormat { JSON, CSV }

/** One file of an export: its name, MIME type and contents. */
class ExportFile(val name: String, val mimeType: String, val contents: String)

/**
 * Which files an export consists of - P5, CLAUDE.md section 15.
 *
 * Pure, so the naming and the split between files are testable on the JVM; the
 * formats themselves are the shared core's [SessionExport], tested on JVM and
 * JS. JSON is one complete file. CSV is two, repetitions and holds, because a
 * hold has no repetition columns - CLAUDE.md section 11.
 */
object ExportFiles {

    fun build(
        format: ExportFormat,
        sessions: List<SessionRecord>,
        exportedAtEpochMs: Double,
        stamp: String,
    ): List<ExportFile> = when (format) {
        ExportFormat.JSON -> listOf(
            ExportFile(
                "kinesis-sessions-$stamp.json",
                MIME_JSON,
                SessionExport.toJson(sessions, exportedAtEpochMs),
            ),
        )

        ExportFormat.CSV -> listOf(
            ExportFile("kinesis-reps-$stamp.csv", MIME_CSV, SessionExport.repsCsv(sessions)),
            ExportFile("kinesis-holds-$stamp.csv", MIME_CSV, SessionExport.holdsCsv(sessions)),
        )
    }

    const val MIME_JSON = "application/json"
    const val MIME_CSV = "text/csv"
}
