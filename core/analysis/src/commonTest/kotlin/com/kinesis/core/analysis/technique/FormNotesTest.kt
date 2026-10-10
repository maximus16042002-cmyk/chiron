package com.kinesis.core.analysis.technique

import com.kinesis.core.analysis.deviation.AcsmTempoRule
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.finding.Finding
import com.kinesis.core.domain.finding.TechniqueFault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Form notes" a set summary shows - DECISIONS.md P3-74: Layer 2's six cited rules, once per
 * set, grouped by rule, and nothing else.
 */
class FormNotesTest {

    private val authority = ExerciseLibrary.pushUp.techniqueAuthority

    private fun fault(id: String, text: String = "$id text", source: String = "a source", version: String = TechniqueRules.RULE_VERSION) =
        TechniqueFault.from(authority = authority, faultId = id, displayText = text, ruleVersion = version, sourceRef = source)

    private fun rep(index: Int, vararg findings: Finding) = RepetitionAnalysisResult(
        exerciseId = "pushup",
        repIndex = index,
        analysisVersion = "test",
        baseline = null,
        comparison = null,
        findings = findings.toList(),
    )

    @Test
    fun notes_group_by_rule_with_how_many_repetitions_each_fired_on() {
        val notes = FormNotes.of(
            listOf(
                rep(1, fault(TechniqueRules.PUSHUP_HIPS_SAGGED, "first sag")),
                rep(2),
                rep(3, fault(TechniqueRules.PUSHUP_HIPS_SAGGED, "second sag"), fault(TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED)),
                rep(4, fault(TechniqueRules.PUSHUP_HIPS_SAGGED)),
            ),
        )

        // A fixed order - the rules' own - not the order the repetitions happened in.
        assertEquals(listOf(TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED, TechniqueRules.PUSHUP_HIPS_SAGGED), notes.map { it.faultId })
        val sag = notes.single { it.faultId == TechniqueRules.PUSHUP_HIPS_SAGGED }
        assertEquals(3, sag.repetitionsFlagged)
        assertEquals(4, sag.repetitionsJudged)
        assertEquals("first sag", sag.example, "the first repetition's own sentence")
        assertEquals("a source", sag.sourceRef)
    }

    /** The ACSM tempo message is a TechniqueFault too, and stays off (P3-30, P3-74). */
    @Test
    fun the_acsm_tempo_message_and_every_personal_deviation_are_left_out() {
        val notes = FormNotes.of(
            listOf(
                rep(1, fault(AcsmTempoRule.FAULT_ID, version = AcsmTempoRule.RULE_VERSION)),
                rep(2, Deviation("rom_left", DeviationDirection.BELOW_BASELINE, "smaller than usual", "p8-layer1-1")),
            ),
        )
        assertTrue(notes.isEmpty(), "shown: $notes")
    }

    @Test
    fun a_set_where_no_rule_fired_has_no_notes() {
        assertTrue(FormNotes.of(listOf(rep(1), rep(2))).isEmpty())
        assertTrue(FormNotes.of(emptyList()).isEmpty())
    }

    /** One repetition firing a rule on both sides counts once. */
    @Test
    fun a_repetition_flagged_on_both_sides_counts_once() {
        val notes = FormNotes.of(
            listOf(rep(1, fault(TechniqueRules.PUSHUP_HIPS_SAGGED, "left"), fault(TechniqueRules.PUSHUP_HIPS_SAGGED, "right"))),
        )
        assertEquals(1, notes.single().repetitionsFlagged)
    }
}
