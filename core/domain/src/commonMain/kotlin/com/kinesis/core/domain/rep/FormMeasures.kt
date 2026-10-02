package com.kinesis.core.domain.rep

import com.kinesis.core.domain.bilateral.BilateralSignal

/**
 * A measure of a joint other than the one that drives the repetition count, taken over one
 * repetition - DECISIONS.md P3-44. Every measure is in degrees and kept per side.
 *
 * A closed set, so every consumer handles every measure, and so a stored measure's [id] always
 * means the same thing. Adding one is a record format change (CONTRACTS.md section 10).
 */
enum class FormMeasure(val id: String) {

    /** The largest angle at the shoulder between hip and elbow: the upper arm against the trunk. */
    UPPER_ARM_TRUNK_MAX("upper_arm_trunk_max_deg"),

    /**
     * The largest bend of the hip below the line from shoulder to ankle: 180 degrees minus the
     * angle at the hip, while the hip is lower in the image than that line. 0 if it never was.
     */
    BODY_LINE_SAG_MAX("body_line_sag_max_deg"),

    /** As [BODY_LINE_SAG_MAX], while the hip is higher in the image than the line. */
    BODY_LINE_HIKE_MAX("body_line_hike_max_deg"),

    /**
     * At the repetition's deepest frame: the trunk's lean from vertical (hip to shoulder) minus
     * the shin's (ankle to knee), each unsigned. Positive when the trunk leans further.
     */
    TRUNK_MINUS_SHIN_LEAN("trunk_minus_shin_lean_deg"),
    ;

    companion object {
        /** The measure with this [id], or `null` for one this version does not know. */
        fun ofId(id: String): FormMeasure? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The form measures one repetition carries - DECISIONS.md P3-44: one [BilateralSignal] for each
 * measure its exercise asked for. A side that was not measured is [Double.NaN], as everywhere
 * (CLAUDE.md section 3); a measure the exercise did not ask for is absent, and reads as not
 * measured on both sides.
 *
 * Measurement, not interpretation: what a technique rule makes of these is an `AnalysisResult`'s
 * business (CLAUDE.md section 15).
 */
class FormMeasures(values: Map<FormMeasure, BilateralSignal>) {

    private val values: Map<FormMeasure, BilateralSignal> = values.toMap()

    /** The measures present, in [FormMeasure] order. */
    val measures: List<FormMeasure> get() = FormMeasure.entries.filter { it in values }

    val isEmpty: Boolean get() = values.isEmpty()

    /** This measure on both sides; not measured on either if the exercise did not ask for it. */
    fun on(measure: FormMeasure): BilateralSignal = values[measure] ?: NOT_MEASURED

    override fun equals(other: Any?): Boolean = other is FormMeasures && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "FormMeasures(" + measures.joinToString { it.id + "=" + on(it) } + ")"

    companion object {
        /** No form measures: an exercise that asks for none, or a record stored before P3-44. */
        val NONE: FormMeasures = FormMeasures(emptyMap())

        private val NOT_MEASURED = BilateralSignal(Double.NaN, Double.NaN)
    }
}
