package com.kinesis.sync

import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two hold fields DECISIONS.md P3-22 added, in a synced document.
 *
 * They are written only when measured. A hold recorded before P3-22 has neither,
 * so its document must stay exactly what an older client uploaded: the Firestore
 * rules accept a re-upload only if it is identical to the stored document (P3-19),
 * and one that differed by two null keys would be refused, and retried, forever.
 */
class SessionDocumentHoldFieldsTest {

    private fun session(hold: HoldRecord) =
        SessionRecord("s-1", 1_000.0, 60, listOf(HoldSetRecord("plank", 1_000.0, listOf(hold))))

    private fun hold(mean: Double, sd: Double) =
        HoldRecord(0.0, 21_000.0, HoldValidity.VALID, 19_500.0, 12_000.0, 1, 3.5, 9.0, 0.97, mean, sd)

    @Suppress("UNCHECKED_CAST")
    private fun holdMap(session: SessionRecord): Map<String, Any?> {
        val sets = SessionDocument.toMap(session)["sets"] as List<Map<String, Any?>>
        return (sets.single()["holds"] as List<Map<String, Any?>>).single()
    }

    /** The keys a hold's document had before P3-22. */
    private val olderKeys = setOf(
        "startMs", "endMs", "validity", "totalHoldMs", "longestUnbrokenMs", "breakCount",
        "meanAbsDeviation", "maxAbsDeviation", "coverage",
    )

    @Test
    fun an_unmeasured_hold_writes_exactly_the_document_an_older_client_wrote() {
        assertEquals(olderKeys, holdMap(session(hold(Double.NaN, Double.NaN))).keys)
    }

    @Test
    fun a_measured_hold_writes_both_and_they_come_back() {
        val map = holdMap(session(hold(176.5, 2.25)))
        assertEquals(olderKeys + setOf("meanHeldValue", "heldValueSd"), map.keys)
        val back = SessionDocument.fromMap(SessionDocument.toMap(session(hold(176.5, 2.25))))!!
        val held = (back.sets.single() as HoldSetRecord).holds.single()
        assertEquals(176.5, held.meanHeldValue, 0.0)
        assertEquals(2.25, held.heldValueSd, 0.0)
    }

    /** A document from before P3-22, read by this client: unmeasured, never zero. */
    @Test
    fun an_older_document_reads_both_as_unmeasured() {
        val back = SessionDocument.fromMap(SessionDocument.toMap(session(hold(Double.NaN, Double.NaN))))!!
        val held = (back.sets.single() as HoldSetRecord).holds.single()
        assertTrue(held.meanHeldValue.isNaN())
        assertTrue(held.heldValueSd.isNaN())
    }
}
