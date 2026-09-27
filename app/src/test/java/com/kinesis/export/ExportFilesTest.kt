package com.kinesis.export

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files an export is made of. The formats are pinned by the core's
 * `SessionExportTest`; this pins the app's half - names, types, and that CSV
 * keeps repetitions and holds apart.
 */
class ExportFilesTest {

    private val sessions = listOf(
        SessionRecord(
            "s1", 1_000.0, 0,
            listOf(
                RepetitionSetRecord("squat", 1_000.0, listOf(rep())),
                HoldSetRecord("plank", 2_000.0, listOf(hold())),
            ),
        ),
    )

    @Test
    fun json_is_one_file_with_every_set() {
        val files = ExportFiles.build(ExportFormat.JSON, sessions, 5.0, stamp = "42")

        assertEquals(1, files.size)
        assertEquals("kinesis-sessions-42.json", files[0].name)
        assertEquals("application/json", files[0].mimeType)
        assertTrue(files[0].contents.contains("\"exerciseId\":\"squat\""))
        assertTrue(files[0].contents.contains("\"exerciseId\":\"plank\""))
    }

    @Test
    fun csv_is_two_files_that_keep_repetitions_and_holds_apart() {
        val files = ExportFiles.build(ExportFormat.CSV, sessions, 5.0, stamp = "42")

        assertEquals(listOf("kinesis-reps-42.csv", "kinesis-holds-42.csv"), files.map { it.name })
        files.forEach { assertEquals("text/csv", it.mimeType) }

        val (reps, holds) = files
        assertTrue(reps.contents.contains(",squat,"))
        assertFalse(reps.contents.contains(",plank,"))
        assertTrue(holds.contents.contains(",plank,"))
        assertFalse(holds.contents.contains(",squat,"))
    }

    private fun rep() = RepRecord(
        repIndex = 1,
        attributedSide = null,
        startMs = 0.0,
        endMs = 1_000.0,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(90.0, 88.0),
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(80.0, 82.0),
        descentMs = 500.0,
        ascentMs = 500.0,
        resampledLeft = doubleArrayOf(170.0, 80.0),
        resampledRight = doubleArrayOf(170.0, 82.0),
    )

    private fun hold() = HoldRecord(
        startMs = 0.0,
        endMs = 10_000.0,
        validity = HoldValidity.VALID,
        totalHoldMs = 9_500.0,
        longestUnbrokenMs = 9_500.0,
        breakCount = 0,
        meanAbsDeviation = 2.0,
        maxAbsDeviation = 4.0,
        coverage = 1.0,
    )
}
