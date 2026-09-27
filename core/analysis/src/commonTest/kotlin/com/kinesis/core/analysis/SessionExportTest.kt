package com.kinesis.core.analysis

import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P5 export. What can go wrong here is quiet: a side merged into one field, a
 * `NaN` written as `0`, a hold given a repetition column. Each test pins one of
 * those. Runs on JVM and JS, since the format is meant to be read by both.
 */
class SessionExportTest {

    // --- JSON --------------------------------------------------------------------------

    @Test
    fun json_carries_the_format_header_and_every_session() {
        val json = SessionExport.toJson(listOf(squatSession(), plankSession()), exportedAtEpochMs = 5.0)

        assertTrue(json.startsWith("{\"format\":\"kinesis-session-export\",\"schema\":1,"), json)
        assertTrue("\"sessionId\":\"s-squat\"" in json)
        assertTrue("\"sessionId\":\"s-plank\"" in json)
        assertTrue(json.endsWith("}]}"), json)
    }

    @Test
    fun json_keeps_left_and_right_as_separate_fields() {
        val json = SessionExport.toJson(listOf(squatSession()), 0.0)

        assertTrue("\"rangeOfMotion\":{\"left\":91.5,\"right\":88.25}" in json, json)
        assertTrue("\"peakValue\":{\"left\":${number(170.0)},\"right\":${number(171.0)}}" in json, json)
    }

    @Test
    fun json_writes_an_unmeasured_side_as_null_never_zero() {
        val json = SessionExport.toJson(listOf(curlSession()), 0.0)

        assertTrue("\"rangeOfMotion\":{\"left\":null,\"right\":${number(120.0)}}" in json, json)
        assertTrue("\"resampledLeft\":[null,null,null]" in json, json)
        assertFalse("NaN" in json, json)
    }

    @Test
    fun json_gives_holds_no_repetition_fields() {
        val json = SessionExport.toJson(listOf(plankSession()), 0.0)

        assertTrue("\"kind\":\"hold\"" in json)
        assertTrue("\"holds\":[{" in json)
        assertFalse("\"reps\"" in json)
        assertFalse("repIndex" in json)
    }

    @Test
    fun json_is_the_same_text_every_time() {
        val sessions = listOf(squatSession(), curlSession(), plankSession())
        assertEquals(SessionExport.toJson(sessions, 1.0), SessionExport.toJson(sessions, 1.0))
    }

    @Test
    fun json_escapes_strings() {
        val session = SessionRecord("a\"b\\c\nd", 0.0, 0, emptyList())
        val json = SessionExport.toJson(listOf(session), 0.0)

        assertTrue("\"sessionId\":\"a\\\"b\\\\c\\u000ad\"" in json, json)
    }

    @Test
    fun json_of_no_sessions_is_still_a_valid_document() {
        assertEquals(
            "{\"format\":\"kinesis-session-export\",\"schema\":1," +
                "\"exportedAtEpochMs\":" + number(0.0) + ",\"sessions\":[]}",
            SessionExport.toJson(emptyList(), 0.0),
        )
    }

    // --- CSV ---------------------------------------------------------------------------

    @Test
    fun reps_csv_has_one_row_per_repetition_under_its_header() {
        val lines = lines(SessionExport.repsCsv(listOf(squatSession(), curlSession(), plankSession())))

        assertEquals(SessionExport.REP_COLUMNS.joinToString(","), lines[0])
        assertEquals(1 + 2 + 1, lines.size, "two squat reps, one curl rep, and no row for the plank")
        lines.forEach { assertEquals(SessionExport.REP_COLUMNS.size, it.split(',').size, it) }
    }

    @Test
    fun reps_csv_keeps_left_and_right_in_their_own_columns() {
        val row = cells(SessionExport.repsCsv(listOf(squatSession())), row = 1)

        assertEquals(number(91.5), row.getValue("range_of_motion_left"))
        assertEquals(number(88.25), row.getValue("range_of_motion_right"))
        assertEquals("", row.getValue("attributed_side"), "a simultaneous rep belongs to neither side")
        assertEquals("squat", row.getValue("exercise_id"))
        assertEquals("1", row.getValue("set_number"))
    }

    @Test
    fun reps_csv_writes_an_unmeasured_side_as_an_empty_cell() {
        val csv = SessionExport.repsCsv(listOf(curlSession()))
        val row = cells(csv, row = 1)

        assertEquals("", row.getValue("range_of_motion_left"))
        assertEquals(number(120.0), row.getValue("range_of_motion_right"))
        assertEquals("RIGHT", row.getValue("attributed_side"))
        assertEquals("", row.getValue("descent_ms"))
        assertFalse("NaN" in csv)
    }

    @Test
    fun holds_csv_has_one_row_per_hold_and_no_repetition_column() {
        val csv = SessionExport.holdsCsv(listOf(squatSession(), plankSession()))
        val lines = lines(csv)

        assertEquals(2, lines.size)
        assertFalse(SessionExport.HOLD_COLUMNS.any { it.startsWith("rep") })
        val row = cells(csv, row = 1)
        assertEquals("plank", row.getValue("exercise_id"))
        assertEquals("2", row.getValue("break_count"))
        assertEquals(number(30_000.0), row.getValue("total_hold_ms"))
        assertEquals(number(178.5), row.getValue("mean_held_value"))
        assertEquals(number(1.25), row.getValue("held_value_sd"))
    }

    @Test
    fun csv_uses_the_session_local_date_not_the_utc_one() {
        // 2026-09-26T23:30Z, recorded at UTC+10: that is Sunday the 27th locally.
        val row = cells(SessionExport.repsCsv(listOf(squatSession())), row = 1)
        assertEquals("2026-09-27", row.getValue("local_date"))
    }

    @Test
    fun csv_quotes_a_cell_that_needs_it() {
        val session = SessionRecord(
            "id,with \"quote\"", 0.0, 0,
            listOf(RepetitionSetRecord("squat", 0.0, listOf(rep(1, null, BilateralSignal(1.0, 2.0))))),
        )
        val line = lines(SessionExport.repsCsv(listOf(session)))[1]
        assertTrue(line.startsWith("\"id,with \"\"quote\"\"\","), line)
    }

    @Test
    fun csv_uses_crlf_line_endings() {
        val csv = SessionExport.repsCsv(emptyList())
        assertEquals(SessionExport.REP_COLUMNS.joinToString(",") + "\r\n", csv)
    }

    // --- The calendar date ------------------------------------------------------------

    @Test
    fun iso_dates_are_right_across_epoch_leap_years_and_negative_days() {
        assertEquals("1970-01-01", LocalDay(0).toIsoDate())
        assertEquals("1969-12-31", LocalDay(-1).toIsoDate())
        assertEquals("2000-02-29", LocalDay(11_016).toIsoDate())
        assertEquals("2000-03-01", LocalDay(11_017).toIsoDate())
        assertEquals("2024-12-31", LocalDay(20_088).toIsoDate())
        assertEquals("2026-09-27", LocalDay(20_723).toIsoDate())
    }

    // --- Fixtures ----------------------------------------------------------------------

    /** 2026-09-26T23:30:00Z, at UTC+10. */
    private val startedAt = 1_790_465_400_000.0

    private fun squatSession() = SessionRecord(
        "s-squat", startedAt, 600,
        listOf(
            RepetitionSetRecord(
                "squat", startedAt,
                listOf(
                    rep(1, null, BilateralSignal(91.5, 88.25)),
                    rep(2, null, BilateralSignal(90.0, 89.0)),
                ),
            ),
        ),
    )

    /** An alternating curl: the left arm was idle, so it was not measured. */
    private fun curlSession() = SessionRecord(
        "s-curl", startedAt, 600,
        listOf(
            RepetitionSetRecord(
                "bicep_curl", startedAt,
                listOf(
                    RepRecord(
                        repIndex = 1,
                        attributedSide = Side.RIGHT,
                        startMs = 0.0,
                        endMs = 2_000.0,
                        validity = RepValidity.VALID,
                        rangeOfMotion = BilateralSignal(Double.NaN, 120.0),
                        peakValue = BilateralSignal(Double.NaN, 160.0),
                        troughValue = BilateralSignal(Double.NaN, 40.0),
                        descentMs = Double.NaN,
                        ascentMs = 900.0,
                        resampledLeft = DoubleArray(3) { Double.NaN },
                        resampledRight = doubleArrayOf(160.0, 40.0, 160.0),
                    ),
                ),
            ),
        ),
    )

    private fun plankSession() = SessionRecord(
        "s-plank", startedAt, 600,
        listOf(
            HoldSetRecord(
                "plank", startedAt,
                listOf(
                    HoldRecord(
                        startMs = 0.0,
                        endMs = 32_000.0,
                        validity = HoldValidity.VALID,
                        totalHoldMs = 30_000.0,
                        longestUnbrokenMs = 20_000.0,
                        breakCount = 2,
                        meanAbsDeviation = 3.5,
                        maxAbsDeviation = 9.0,
                        coverage = 0.97,
                        meanHeldValue = 178.5,
                        heldValueSd = 1.25,
                    ),
                ),
            ),
        ),
    )

    private fun rep(index: Int, side: Side?, rom: BilateralSignal) = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = index * 1_000.0,
        endMs = index * 1_000.0 + 900.0,
        validity = RepValidity.VALID,
        rangeOfMotion = rom,
        peakValue = BilateralSignal(170.0, 171.0),
        troughValue = BilateralSignal(78.5, 82.75),
        descentMs = 450.0,
        ascentMs = 450.0,
        resampledLeft = doubleArrayOf(170.0, 80.0, 170.0),
        resampledRight = doubleArrayOf(171.0, 82.0, 171.0),
    )

    /** How this platform writes a double - JVM and JS differ on `1.0` versus `1`. */
    private fun number(value: Double): String = value.toString()

    private fun lines(csv: String): List<String> = csv.split("\r\n").dropLast(1)

    private fun cells(csv: String, row: Int): Map<String, String> {
        val lines = lines(csv)
        return lines[0].split(',').zip(lines[row].split(',')).toMap()
    }
}
