package com.kinesis.export

import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The app's half of P5's export - DECISIONS.md P3-25. The format is the core's
 * and tested there; this checks that the files the share sheet is handed are
 * that format, whole, and that an earlier export does not linger beside them.
 */
class ExportFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val curl = RepRecord(
        repIndex = 1,
        attributedSide = Side.RIGHT,
        startMs = 5_011_060.0,
        endMs = 5_014_220.0,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(Double.NaN, 135.375),
        peakValue = BilateralSignal(Double.NaN, 168.5),
        troughValue = BilateralSignal(Double.NaN, 33.125),
        descentMs = 1_470.0,
        ascentMs = 1_440.0,
        resampledLeft = DoubleArray(64) { Double.NaN },
        resampledRight = DoubleArray(64) { 168.5 - it },
    )

    private val sessions = listOf(
        SessionRecord("s-1", 1_790_537_401_000.0, 330, listOf(RepetitionSetRecord("bicep_curl", 1_790_537_445_250.0, listOf(curl)))),
    )

    @Test
    fun the_four_files_hold_exactly_the_cores_export() {
        val files = ExportFiles.write(File(folder.root, "exports"), sessions, emptyList(), 1_790_600_000_000.0, "2026-09-28")
        val csv = SessionExport.csv(sessions)

        assertEquals(4, files.size)
        assertEquals(SessionExport.json(sessions, emptyList(), 1_790_600_000_000.0), files[0].readText(Charsets.UTF_8))
        assertEquals(csv.sets, files[1].readText(Charsets.UTF_8))
        assertEquals(csv.reps, files[2].readText(Charsets.UTF_8))
        assertEquals(csv.holds, files[3].readText(Charsets.UTF_8))
    }

    @Test
    fun each_file_is_named_for_its_table_and_the_day() {
        val names = ExportFiles.write(File(folder.root, "exports"), sessions, emptyList(), 0.0, "2026-09-28").map { it.name }
        assertEquals(
            listOf("kinesis-2026-09-28.json", "kinesis-2026-09-28-sets.csv", "kinesis-2026-09-28-reps.csv", "kinesis-2026-09-28-holds.csv"),
            names,
        )
    }

    /** The cache holds one export at a time: yesterday's is gone once today's is written. */
    @Test
    fun a_new_export_replaces_the_last_one() {
        val directory = File(folder.root, "exports")
        val old = ExportFiles.write(directory, sessions, emptyList(), 0.0, "2026-09-27")
        val new = ExportFiles.write(directory, sessions, emptyList(), 0.0, "2026-09-28")

        for (file in old) assertFalse("${file.name} survived the next export", file.exists())
        for (file in new) assertTrue("${file.name} was not written", file.isFile)
        assertEquals(new.map { it.name }.toSet(), directory.list()!!.toSet())
    }
}
