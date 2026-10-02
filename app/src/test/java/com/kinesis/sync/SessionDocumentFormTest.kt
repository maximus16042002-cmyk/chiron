package com.kinesis.sync

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.FormMeasures
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The form summary DECISIONS.md P3-44 added, in a synced document.
 *
 * Written only when the repetition has one, for P3-22's reason: a repetition recorded before
 * P3-44 must upload exactly as an older client uploaded it, or the rules (P3-19) refuse the
 * identical retry forever.
 */
class SessionDocumentFormTest {

    private fun rep(form: FormMeasures) = RepRecord(
        repIndex = 1, attributedSide = Side.LEFT, startMs = 0.0, endMs = 1_200.0, validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(110.0, Double.NaN), peakValue = BilateralSignal(165.0, Double.NaN),
        troughValue = BilateralSignal(55.0, Double.NaN), descentMs = 500.0, ascentMs = 600.0,
        resampledLeft = DoubleArray(4) { 160.0 - it }, resampledRight = DoubleArray(4) { Double.NaN },
        form = form,
    )

    private fun session(rep: RepRecord) = SessionRecord("s-1", 1_000.0, 60, listOf(RepetitionSetRecord("bicep_curl", 1_000.0, listOf(rep))))

    @Suppress("UNCHECKED_CAST")
    private fun repMap(session: SessionRecord): Map<String, Any?> {
        val sets = SessionDocument.toMap(session)["sets"] as List<Map<String, Any?>>
        return (sets.single()["reps"] as List<Map<String, Any?>>).single()
    }

    @Test
    fun a_repetition_without_a_form_writes_no_form_key() {
        assertFalse("form" in repMap(session(rep(FormMeasures.NONE))).keys)
    }

    @Test
    fun a_form_round_trips_with_its_unmeasured_side_still_unmeasured() {
        val form = FormMeasures(mapOf(FormMeasure.UPPER_ARM_TRUNK_MAX to BilateralSignal(27.25, Double.NaN)))
        @Suppress("UNCHECKED_CAST")
        val written = repMap(session(rep(form)))["form"] as Map<String, Map<String, Any?>>
        assertEquals(mapOf("left" to 27.25, "right" to null), written["upper_arm_trunk_max_deg"])

        val back = SessionDocument.fromMap(SessionDocument.toMap(session(rep(form))))!!
        val upper = (back.sets.single() as RepetitionSetRecord).reps.single().form.on(FormMeasure.UPPER_ARM_TRUNK_MAX)
        assertEquals(27.25, upper.left, 0.0)
        assertTrue(upper.right.isNaN())
    }

    /** A document synced before P3-44, read by this client: no form, so not measured, never zero. */
    @Test
    fun an_older_document_reads_as_having_no_form() {
        val back = SessionDocument.fromMap(SessionDocument.toMap(session(rep(FormMeasures.NONE))))!!
        assertTrue((back.sets.single() as RepetitionSetRecord).reps.single().form.isEmpty)
    }
}
