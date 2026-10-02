package com.kinesis.core.analysis

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.FormMeasures
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The form summary in the facade's record JSON, which is also the export's - DECISIONS.md P3-44.
 * Written as `{id: {left, right}}`, read back the same, and absent from older records.
 */
class FormJsonTest {

    private fun rep(form: FormMeasures) = RepRecord(
        repIndex = 3, attributedSide = null, startMs = 0.0, endMs = 1_500.0, validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(80.0, 81.0), peakValue = BilateralSignal(170.0, 171.0),
        troughValue = BilateralSignal(90.0, 90.0), descentMs = 700.0, ascentMs = 800.0,
        resampledLeft = DoubleArray(4) { 170.0 - it }, resampledRight = DoubleArray(4) { 171.0 - it },
        form = form,
    )

    @Test
    fun a_form_summary_round_trips_through_the_record_json_with_its_unmeasured_side() {
        val form = FormMeasures(mapOf(FormMeasure.TRUNK_MINUS_SHIN_LEAN to BilateralSignal(12.5, Double.NaN)))
        val json = EngineJson.rep(rep(form))
        assertTrue("\"form\":{\"trunk_minus_shin_lean_deg\":{\"left\":12.5,\"right\":null}}" in json, json)
        assertEquals(form, EngineJson.readRep(JsonText.parse(json)).form)
    }

    @Test
    fun a_record_without_a_form_writes_an_empty_one() {
        assertTrue("\"form\":{}" in EngineJson.rep(rep(FormMeasures.NONE)))
    }

    /** A record exported before P3-44 has no `form` key: it reads as none, never as zero. */
    @Test
    fun an_older_record_without_the_key_reads_as_no_form() {
        val older = EngineJson.rep(rep(FormMeasures.NONE)).replace(",\"form\":{}", "")
        assertTrue("form" !in older)
        assertTrue(EngineJson.readRep(JsonText.parse(older)).form.isEmpty)
    }
}
