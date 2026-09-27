package com.kinesis.core.analysis.export

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.JsonText
import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P5's export - DECISIONS.md P3-25, CONTRACTS.md section 10.
 *
 * The golden-file test is exact, byte for byte, and runs on every target: JVM,
 * JS and both Android variants. A change that moves a field, renames one, or
 * changes how a number is written fails it until the golden file is regenerated
 * on purpose (`SessionExportGoldenWriter`) and the diff reviewed.
 */
class SessionExportTest {

    private val fixture = SessionExportFixture
    private val json = SessionExport.json(fixture.sessions, fixture.baselines, fixture.EXPORTED_AT)
    private val csv = SessionExport.csv(fixture.sessions)

    // --- The golden file ------------------------------------------------------------

    @Test
    fun the_json_export_matches_the_golden_file_byte_for_byte() =
        assertEquals(SessionExportGolden.json, json)

    @Test
    fun the_csv_export_matches_the_golden_file_byte_for_byte() {
        assertEquals(SessionExportGolden.setsCsv, csv.sets, "sets.csv")
        assertEquals(SessionExportGolden.repsCsv, csv.reps, "reps.csv")
        assertEquals(SessionExportGolden.holdsCsv, csv.holds, "holds.csv")
    }

    /** An empty golden file would make the two tests above pass on an empty export. */
    @Test
    fun the_golden_file_holds_every_kind_of_record() {
        for (expected in listOf("\"mode\":\"repetition\"", "\"mode\":\"hold\"", "INSUFFICIENT_DATA", "TOO_SHORT", "\"baselines\":[{")) {
            assertTrue(expected in SessionExportGolden.json, "the golden JSON lacks $expected")
        }
        assertEquals(6, SessionExportGolden.setsCsv.lines().count { it.isNotEmpty() }, "a header and five sets")
        assertEquals(7, SessionExportGolden.repsCsv.lines().count { it.isNotEmpty() }, "a header and six repetitions")
        assertEquals(3, SessionExportGolden.holdsCsv.lines().count { it.isNotEmpty() }, "a header and two holds")
    }

    // --- The document ---------------------------------------------------------------

    @Test
    fun the_document_names_its_format_and_version() {
        val root = JsonText.obj(JsonText.parse(json), "the export")
        assertEquals(SessionExport.FORMAT, JsonText.string(root, "format"))
        assertEquals(SessionExport.FORMAT_VERSION, JsonText.int(root, "formatVersion"))
        assertEquals(fixture.EXPORTED_AT, JsonText.double(root, "exportedAtEpochMs"))
    }

    /**
     * Every repetition is exported exactly as the facade reports records, so an
     * exported set's `reps` can go straight back into `computeBaseline` - and
     * read back, every field survives, `NaN` included.
     */
    @Test
    fun every_repetition_is_the_facades_record_and_reads_back_whole() {
        var checked = 0
        for ((sessionIndex, session) in fixture.sessions.withIndex()) {
            for ((setIndex, set) in session.sets.withIndex()) {
                if (set !is RepetitionSetRecord) continue
                val exported = setsOf(sessionIndex)[setIndex]
                val reps = exported["reps"] as List<*>
                assertEquals(set.reps.size, reps.size)
                for ((i, original) in set.reps.withIndex()) {
                    assertTrue(EngineJson.rep(original) in json, "rep ${original.repIndex} is not the facade's record text")
                    assertSameRecord(original, EngineJson.readRep(reps[i]))
                    checked++
                }
            }
        }
        assertEquals(6, checked)
    }

    @Test
    fun a_hold_set_carries_its_holds_and_nothing_a_repetition_has() {
        val plank = setsOf(0)[3]
        assertEquals("hold", plank["mode"])
        assertTrue("holds" in plank)
        assertFalse("reps" in plank, "a hold set must not carry a repetition list - CLAUDE.md section 11")
        val hold = EngineJson.readHold((plank["holds"] as List<*>).single())
        val original = (fixture.sessions[0].sets[3] as HoldSetRecord).holds.single()
        assertEquals(original.totalHoldMs, hold.totalHoldMs)
        assertEquals(original.meanHeldValue, hold.meanHeldValue)
    }

    /** A hold recorded before P3-22 exports its two later fields as `null`: unmeasured, not zero. */
    @Test
    fun a_field_that_was_never_measured_is_null_not_zero() {
        val hold = JsonText.obj((setsOf(1)[0]["holds"] as List<*>).single(), "the old hold")
        assertNull(hold["meanHeldValue"])
        assertNull(hold["heldValueSd"])
        assertNull(hold["meanAbsDeviation"])
        val holdRow = rows(csv.holds).last()
        assertEquals("", holdRow[column(csv.holds, "meanHeldValue")])
        assertEquals("", holdRow[column(csv.holds, "heldValueSd")])
    }

    // --- Counts, which P7's comparison is built on ---------------------------------

    @Test
    fun each_set_counts_its_records_and_its_valid_ones() {
        val curls = setsOf(0)[0]
        assertEquals(4, JsonText.int(curls, "recordCount"))
        assertEquals(2, JsonText.int(curls, "validCount"))
        val row = rows(csv.sets)[0]
        assertEquals("4", row[column(csv.sets, "recordCount")])
        assertEquals("2", row[column(csv.sets, "validCount")])
    }

    /** A set that counted nothing is a count of zero, not a missing set. */
    @Test
    fun a_set_with_no_records_is_exported_with_a_count_of_zero() {
        val empty = setsOf(0)[2]
        assertEquals(0, JsonText.int(empty, "recordCount"))
        assertEquals(0, JsonText.int(empty, "validCount"))
        assertEquals(emptyList<Any?>(), empty["reps"])
        val row = rows(csv.sets)[2]
        assertEquals("3", row[column(csv.sets, "setIndex")])
        assertEquals("0", row[column(csv.sets, "validCount")])
    }

    @Test
    fun sets_are_numbered_from_one_in_the_order_performed() {
        assertEquals(listOf(1, 2, 3, 4), setsOf(0).map { JsonText.int(it, "setIndex") })
        assertEquals(listOf("1", "2", "3", "4", "1"), rows(csv.sets).map { it[column(csv.sets, "setIndex")] })
    }

    // --- Left and right - CLAUDE.md section 3 ---------------------------------------

    @Test
    fun left_and_right_stay_in_their_own_json_fields() {
        val leftCurl = JsonText.obj((setsOf(0)[0]["reps"] as List<*>)[0], "the left curl")
        val rom = JsonText.obj(leftCurl["rangeOfMotion"], "rangeOfMotion")
        assertEquals(112.5, rom["left"])
        assertNull(rom["right"], "the idle arm must stay unmeasured, not take the other arm's value")
    }

    /** No column holds a combined value: every bilateral quantity is two columns, never one. */
    @Test
    fun left_and_right_stay_in_their_own_csv_columns() {
        val header = rows(csv.reps, header = true).first()
        for (quantity in listOf("rangeOfMotion", "peakValue", "troughValue")) {
            assertTrue(quantity + "Left" in header && quantity + "Right" in header, "$quantity is not two columns")
            assertFalse(quantity in header, "$quantity has a combined column")
        }
        val squat = rows(csv.reps)[4]
        assertEquals("85.75", squat[column(csv.reps, "rangeOfMotionLeft")])
        assertEquals("101.25", squat[column(csv.reps, "rangeOfMotionRight")])
        val halfSeen = rows(csv.reps)[5]
        assertEquals("24.5", halfSeen[column(csv.reps, "rangeOfMotionLeft")])
        assertEquals("", halfSeen[column(csv.reps, "rangeOfMotionRight")])
    }

    // --- Baselines ----------------------------------------------------------------

    /** One format for a baseline, the one calibration already writes and reads. */
    @Test
    fun baselines_are_exported_as_calibration_writes_them() {
        for (baseline in fixture.baselines) {
            assertTrue(BaselineJson.write(baseline) in json, "${baseline.calibrationId} is not in BaselineJson's form")
        }
        val root = JsonText.obj(JsonText.parse(json), "the export")
        assertEquals(2, (root["baselines"] as List<*>).size)
    }

    // --- CSV mechanics ------------------------------------------------------------

    @Test
    fun every_csv_row_has_a_cell_for_every_column_and_ends_with_crlf() {
        for ((name, table) in listOf("sets" to csv.sets, "reps" to csv.reps, "holds" to csv.holds)) {
            assertTrue(table.endsWith("\r\n"), "$name.csv does not end with CRLF")
            val lines = table.removeSuffix("\r\n").split("\r\n")
            val width = lines.first().split(',').size
            for (line in lines) assertEquals(width, line.split(',').size, "$name.csv: $line")
        }
    }

    /** RFC 4180: a cell with a comma, a quote or a line break is quoted, quotes doubled. */
    @Test
    fun a_cell_that_needs_quoting_is_quoted() {
        val odd = SessionRecord("a,\"b\"", 1_790_000_000_000.0, 0, listOf(RepetitionSetRecord("squat", 1_790_000_000_000.0, emptyList())))
        val row = SessionExport.csv(listOf(odd)).sets.split("\r\n")[1]
        assertTrue(row.startsWith("\"a,\"\"b\"\"\","), row)
    }

    @Test
    fun an_export_of_nothing_is_a_document_and_three_headers() {
        val empty = SessionExport.json(emptyList(), emptyList(), fixture.EXPORTED_AT)
        val root = JsonText.obj(JsonText.parse(empty), "the empty export")
        assertEquals(emptyList<Any?>(), root["sessions"])
        assertEquals(emptyList<Any?>(), root["baselines"])
        val tables = SessionExport.csv(emptyList())
        for (table in listOf(tables.sets, tables.reps, tables.holds)) {
            assertEquals(1, table.split("\r\n").count { it.isNotEmpty() }, "expected the header alone: $table")
        }
    }

    /** Epoch milliseconds in plain digits: no `1.79E12` on one platform and not the other. */
    @Test
    fun epoch_times_are_written_in_plain_digits() {
        assertTrue("\"startedAtEpochMs\":1790537401000," in json)
        assertTrue(",1790537445250," in csv.sets)
        assertFalse(Regex("[0-9]E[0-9-]").containsMatchIn(json), "a number in E notation")
    }

    // --- Helpers ------------------------------------------------------------------

    private fun setsOf(sessionIndex: Int): List<Map<String, Any?>> {
        val root = JsonText.obj(JsonText.parse(json), "the export")
        val session = JsonText.obj((root["sessions"] as List<*>)[sessionIndex], "a session")
        return (session["sets"] as List<*>).map { JsonText.obj(it, "a set") }
    }

    private fun rows(table: String, header: Boolean = false): List<List<String>> {
        val lines = table.removeSuffix("\r\n").split("\r\n").map { it.split(',') }
        return if (header) lines else lines.drop(1)
    }

    private fun column(table: String, name: String): Int {
        val index = table.substringBefore("\r\n").split(',').indexOf(name)
        assertTrue(index >= 0, "no column $name")
        return index
    }

    private fun assertSameRecord(expected: RepRecord, actual: RepRecord) {
        assertEquals(expected.repIndex, actual.repIndex)
        assertEquals(expected.attributedSide, actual.attributedSide)
        assertEquals(expected.validity, actual.validity)
        same(expected.startMs, actual.startMs)
        same(expected.endMs, actual.endMs)
        same(expected.rangeOfMotion, actual.rangeOfMotion)
        same(expected.peakValue, actual.peakValue)
        same(expected.troughValue, actual.troughValue)
        same(expected.descentMs, actual.descentMs)
        same(expected.ascentMs, actual.ascentMs)
        assertEquals(expected.resampledLeft.size, actual.resampledLeft.size)
        for (i in expected.resampledLeft.indices) same(expected.resampledLeft[i], actual.resampledLeft[i])
        for (i in expected.resampledRight.indices) same(expected.resampledRight[i], actual.resampledRight[i])
        assertTrue(expected.validity != RepValidity.VALID || !actual.startMs.isNaN())
    }

    private fun same(expected: BilateralSignal, actual: BilateralSignal) {
        same(expected.left, actual.left)
        same(expected.right, actual.right)
    }

    /** Equal, with `NaN` - not measured - equal to itself. */
    private fun same(expected: Double, actual: Double) {
        if (expected.isNaN()) assertTrue(actual.isNaN(), "expected unmeasured, got $actual") else assertEquals(expected, actual)
    }
}
