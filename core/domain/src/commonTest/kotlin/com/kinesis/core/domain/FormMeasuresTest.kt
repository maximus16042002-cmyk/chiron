package com.kinesis.core.domain

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.FormMeasures
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The per-repetition form summary - DECISIONS.md P3-44. Per side, and absent means unmeasured. */
class FormMeasuresTest {

    @Test
    fun a_measure_not_asked_for_reads_as_unmeasured_on_both_sides_never_zero() {
        val form = FormMeasures(mapOf(FormMeasure.UPPER_ARM_TRUNK_MAX to BilateralSignal(12.0, Double.NaN)))
        val absent = form.on(FormMeasure.BODY_LINE_SAG_MAX)
        assertTrue(absent.left.isNaN() && absent.right.isNaN())
        assertEquals(12.0, form.on(FormMeasure.UPPER_ARM_TRUNK_MAX).left)
        assertTrue(form.on(FormMeasure.UPPER_ARM_TRUNK_MAX).right.isNaN())
    }

    @Test
    fun a_record_made_without_a_form_carries_none() {
        val rep = RepRecord(
            repIndex = 1, attributedSide = null, startMs = 0.0, endMs = 1_000.0, validity = RepValidity.VALID,
            rangeOfMotion = BilateralSignal(80.0, 80.0), peakValue = BilateralSignal(170.0, 170.0),
            troughValue = BilateralSignal(90.0, 90.0), descentMs = 400.0, ascentMs = 500.0,
            resampledLeft = DoubleArray(64), resampledRight = DoubleArray(64),
        )
        assertTrue(rep.form.isEmpty)
        assertEquals(FormMeasures.NONE, rep.form)
    }

    /** Stored ids must keep their meaning: one id per measure, and each found again by its id. */
    @Test
    fun every_measure_has_its_own_stable_id() {
        val ids = FormMeasure.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (measure in FormMeasure.entries) assertEquals(measure, FormMeasure.ofId(measure.id))
        assertNull(FormMeasure.ofId("not_a_measure"))
    }

    @Test
    fun measures_are_listed_in_a_fixed_order_whatever_order_they_were_given_in() {
        val form = FormMeasures(
            linkedMapOf(
                FormMeasure.BODY_LINE_HIKE_MAX to BilateralSignal(1.0, 2.0),
                FormMeasure.BODY_LINE_SAG_MAX to BilateralSignal(3.0, 4.0),
            ),
        )
        assertEquals(listOf(FormMeasure.BODY_LINE_SAG_MAX, FormMeasure.BODY_LINE_HIKE_MAX), form.measures)
    }
}
