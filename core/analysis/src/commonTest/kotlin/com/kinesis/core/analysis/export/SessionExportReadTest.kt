package com.kinesis.core.analysis.export

import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reading an export back - DECISIONS.md P3-66. The inverse of [SessionExport.json], used for the
 * draft of a set in progress, which is written in the export's own format.
 *
 * The fixture is the export's golden fixture, which holds every kind of record: repetition and
 * hold sets, a one-sided repetition, sides never measured (`null`), form measures, an empty set.
 */
class SessionExportReadTest {

    private val fixture = SessionExportFixture
    private val json = SessionExport.json(fixture.sessions, fixture.baselines, fixture.EXPORTED_AT)

    /** The strongest check available: what is read back exports to the same bytes. */
    @Test
    fun an_export_read_back_and_exported_again_is_byte_identical() {
        val read = SessionExport.readSessions(json)

        assertEquals(fixture.sessions.size, read.size)
        assertEquals(json, SessionExport.json(read, fixture.baselines, fixture.EXPORTED_AT))
    }

    @Test
    fun each_sessions_identity_and_each_sets_kind_survive() {
        val read = SessionExport.readSessions(json)
        for ((expected, actual) in fixture.sessions.zip(read)) {
            assertEquals(expected.sessionId, actual.sessionId)
            assertEquals(expected.startedAtEpochMs, actual.startedAtEpochMs)
            assertEquals(expected.utcOffsetMinutes, actual.utcOffsetMinutes)
            assertEquals(expected.sets.map { it::class }, actual.sets.map { it::class })
            assertEquals(expected.sets.map { it.exerciseId }, actual.sets.map { it.exerciseId })
            assertEquals(expected.sets.map { it.completedUnits }, actual.sets.map { it.completedUnits })
        }
        assertTrue(read.flatMap { it.sets }.any { it is HoldSetRecord })
        assertTrue(read.flatMap { it.sets }.any { it is RepetitionSetRecord })
    }

    /** The draft is one session in a document of its own. */
    @Test
    fun a_document_of_one_session_reads_back_to_that_session() {
        val one = fixture.sessions.first()
        val document = SessionExport.json(listOf(one), emptyList(), 0.0)

        val read = SessionExport.readSessions(document).single()

        assertEquals(document, SessionExport.json(listOf(read), emptyList(), 0.0))
    }

    @Test
    fun a_document_that_is_not_an_export_is_refused() {
        assertFailsWith<IllegalArgumentException> { SessionExport.readSessions("""{"format":"other","formatVersion":2,"sessions":[]}""") }
        assertFailsWith<IllegalArgumentException> { SessionExport.readSessions("not json at all") }
        assertFailsWith<IllegalArgumentException> { SessionExport.readSessions("""[1, 2, 3]""") }
    }

    /** A version this reader does not know might mean anything; it is refused, never guessed at. */
    @Test
    fun a_newer_format_version_is_refused() {
        val newer = json.replace("\"formatVersion\":2", "\"formatVersion\":3")
        assertTrue(newer != json, "the fixture's version field was not where the test expects it")
        assertFailsWith<IllegalArgumentException> { SessionExport.readSessions(newer) }
    }
}
