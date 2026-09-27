package com.kinesis.core.analysis.export

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P5's JSON and CSV export - DECISIONS.md P3-21.
 *
 * What an export can get silently wrong, each pinned here:
 * - left and right folded into one column, or swapped (CLAUDE.md section 3);
 * - an unmeasured side written as 0, which reads as a measurement;
 * - a hold given a repetition column (CLAUDE.md section 11);
 * - an id with a comma or a quote breaking a row or the document.
 */
class SessionExportTest {

    private fun rep(index: Int, side: Side?, left: Double, right: Double) = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = 1000.0 * index,
        endMs = 1000.0 * index + 900.0,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(left, right),
        peakValue = BilateralSignal(170.0, 171.0),
        troughValue = BilateralSignal(170.0 - left, 171.0 - right),
        descentMs = 500.0,
        ascentMs = 400.0,
        resampledLeft = DoubleArray(4) { left + it },
        resampledRight = DoubleArray(4) { right + it },
    )

    private val hold = HoldRecord(
        startMs = 0.0,
        endMs = 31_000.0,
        validity = HoldValidity.VALID,
        totalHoldMs = 30_000.0,
        longestUnbrokenMs = 20_000.0,
        breakCount = 1,
        meanAbsDeviation = 2.5,
        maxAbsDeviation = 6.0,
        coverage = 0.97,
    )

    private val squats = RepetitionSetRecord(
        "squat", 1_700_000_000_000.0,
        listOf(rep(1, null, 91.0, 87.0), rep(2, null, 93.0, 88.0)),
    )

    /** An alternating set: each repetition measured one side, the other is NaN. */
    private val curls = RepetitionSetRecord(
        "bicep_curl", 1_700_000_100_000.0,
        listOf(rep(1, Side.LEFT, 120.0, Double.NaN), rep(2, Side.RIGHT, Double.NaN, 118.0)),
    )

    private val plank = HoldSetRecord("plank", 1_700_000_200_000.0, listOf(hold))

    private val session = SessionRecord("s-1", 1_700_000_000_000.0, 330, listOf(squats, curls, plank))

    private fun lines(csv: String) = csv.trimEnd('\n').split("\r\n", "\n")

    private fun column(csv: String, name: String): List<String> {
        val rows = lines(csv)
        val at = rows[0].split(',').indexOf(name)
        assertTrue(at >= 0, "no column $name in ${rows[0]}")
        return rows.drop(1).map { it.split(',')[at] }
    }

    @Test
    fun rep_csv_keeps_left_and_right_in_separate_columns_the_right_way_round() {
        val csv = SessionExport.repsCsv(listOf(session))
        assertEquals(listOf(91.0, 93.0, 120.0, null), column(csv, "rom_left").map(::number))
        assertEquals(listOf(87.0, 88.0, null, 118.0), column(csv, "rom_right").map(::number))
    }

    @Test
    fun no_csv_column_combines_the_two_sides() {
        val header = lines(SessionExport.repsCsv(listOf(session)))[0].split(',')
        for (name in header.filter { it.startsWith("rom") || it.startsWith("peak") || it.startsWith("trough") }) {
            assertTrue(name.endsWith("_left") || name.endsWith("_right"), "column $name is not one side")
        }
    }

    @Test
    fun an_unmeasured_side_is_empty_in_csv_and_null_in_json_never_zero() {
        val csv = SessionExport.repsCsv(listOf(session))
        assertEquals("", column(csv, "rom_right")[2])
        val json = SessionExport.json(listOf(session))
        assertTrue("\"rangeOfMotion\":{\"left\":120" in json, json)
        assertTrue("\"right\":null" in json, json)
        assertFalse("NaN" in json || "NaN" in csv)
    }

    @Test
    fun one_row_per_repetition_and_per_hold_with_holds_kept_apart() {
        assertEquals(1 + 4, lines(SessionExport.repsCsv(listOf(session))).size)
        val holds = SessionExport.holdsCsv(listOf(session))
        assertEquals(1 + 1, lines(holds).size)
        val header = lines(holds)[0]
        assertFalse("rep" in header, "a hold row has somewhere to put a repetition: $header")
        assertEquals(listOf("plank"), column(holds, "exercise_id"))
        assertEquals(listOf("1"), column(holds, "break_count"))
    }

    @Test
    fun rows_carry_session_set_and_side_so_they_can_be_regrouped() {
        val csv = SessionExport.repsCsv(listOf(session))
        assertEquals(listOf("s-1", "s-1", "s-1", "s-1"), column(csv, "session_id"))
        assertEquals(listOf("1", "1", "2", "2"), column(csv, "set_index"))
        assertEquals(listOf("", "", "LEFT", "RIGHT"), column(csv, "attributed_side"))
        assertEquals(listOf("1", "2", "1", "2"), column(csv, "rep_index"))
        assertEquals(listOf("3"), column(SessionExport.holdsCsv(listOf(session)), "set_index"))
    }

    @Test
    fun csv_quotes_a_field_holding_a_comma_or_a_quote() {
        val odd = SessionRecord("a,\"b\"", 0.0, 0, listOf(squats))
        val row = lines(SessionExport.repsCsv(listOf(odd)))[1]
        assertTrue(row.startsWith("\"a,\"\"b\"\"\","), row)
    }

    @Test
    fun json_escapes_strings_and_nests_sets_by_mode() {
        val odd = SessionRecord("q\"\\\n", 0.0, -120, listOf(squats, plank))
        val json = SessionExport.json(listOf(odd))
        assertTrue("\"sessionId\":\"q\\\"\\\\\\n\"" in json, json)
        assertTrue("\"utcOffsetMinutes\":-120" in json, json)
        assertTrue("\"mode\":\"repetition\"" in json && "\"mode\":\"hold\"" in json, json)
        assertTrue("\"reps\":[{" in json && "\"holds\":[{" in json, json)
        assertTrue(json.startsWith("{\"format\":\"kinesis-sessions\",\"version\":1,"), json)
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun json_keeps_the_resampled_signal_per_side() {
        val json = SessionExport.json(listOf(session))
        assertTrue("\"resampledLeft\":[91" in json, json)
        assertTrue("\"resampledRight\":[87" in json, json)
    }

    @Test
    fun an_empty_history_still_exports_headers_and_a_valid_document() {
        assertEquals(1, lines(SessionExport.repsCsv(emptyList())).size)
        assertEquals(1, lines(SessionExport.holdsCsv(emptyList())).size)
        assertEquals("{\"format\":\"kinesis-sessions\",\"version\":1,\"sessions\":[]}", SessionExport.json(emptyList()))
    }

    /** JS writes a whole Double without ".0"; the value, not the spelling, is what is pinned. */
    private fun number(cell: String): Double? = if (cell.isEmpty()) null else cell.toDouble()
}
