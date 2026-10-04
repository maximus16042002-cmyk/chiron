package com.kinesis.export

import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.session.SessionRecord
import java.io.File

/**
 * Writes P5's export to files the share sheet can hand on - DECISIONS.md P3-25.
 *
 * The format is the core's [SessionExport], identical on every platform and
 * tested there; this only names the files and writes them. It runs when the
 * user asks, never during a set.
 *
 * Nothing here sends anything anywhere. The files go to the app's cache, and
 * leave the phone only if the user picks a destination in the share sheet -
 * CLAUDE.md section 16.
 */
object ExportFiles {

    /**
     * Replaces whatever [directory] held with a fresh export, and returns the
     * four files: the JSON document, then the sets, repetitions and holds tables.
     *
     * @param day the local date, `yyyy-MM-dd`, for the file names.
     */
    fun write(
        directory: File,
        sessions: List<SessionRecord>,
        baselines: List<CalibrationBaseline>,
        exportedAtEpochMs: Double,
        day: String,
    ): List<File> {
        // One export at a time in the cache; the last one is not kept.
        directory.deleteRecursively()
        check(directory.mkdirs()) { "could not create $directory" }

        val csv = SessionExport.csv(sessions)
        val contents = listOf(
            "chiron-$day.json" to SessionExport.json(sessions, baselines, exportedAtEpochMs),
            "chiron-$day-sets.csv" to csv.sets,
            "chiron-$day-reps.csv" to csv.reps,
            "chiron-$day-holds.csv" to csv.holds,
        )
        return contents.map { (name, text) ->
            File(directory, name).also { it.writeText(text, Charsets.UTF_8) }
        }
    }
}
