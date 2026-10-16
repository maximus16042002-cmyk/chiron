package com.kinesis.ui.screens

import com.kinesis.core.analysis.verdict.RepOutcome
import com.kinesis.core.analysis.verdict.RepVerdict
import com.kinesis.core.analysis.verdict.RepVerdicts
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.Fault
import com.kinesis.ui.theme.Ink
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a stored attempt is named in the set summary and history - DECISIONS.md P3-78: whether it
 * counted, and if not why, by a mark as well as a colour.
 */
class VerdictLabelTest {

    @Test
    fun a_counted_repetition_is_marked_counted() {
        val v = RepVerdict(RepOutcome.COUNTED, null)
        assertEquals("✓ counted", verdictLabel(v))
        assertEquals(Ink, verdictColor(v))
    }

    /** The owner's decision: a form fault counts, and is named in red. */
    @Test
    fun a_counted_form_fault_says_both_in_red() {
        val v = RepVerdict(RepOutcome.FORM_FAULT, "Upper arm moved forward", "curl_upper_arm_moved_forward")
        assertEquals("✓ counted · Upper arm moved forward", verdictLabel(v))
        assertEquals(Fault, verdictColor(v))
    }

    @Test
    fun a_rejected_attempt_says_why_in_red_without_repeating_not_counted() {
        val v = RepVerdict(RepOutcome.TOO_FAST, RepVerdicts.TOO_FAST_MESSAGE)
        assertEquals("✕ Too fast", verdictLabel(v))
        assertEquals(Fault, verdictColor(v))
    }

    /** Lost tracking is the system's limit, not the user's fault: amber, not red. */
    @Test
    fun lost_tracking_is_amber() {
        val v = RepVerdict(RepOutcome.LOST_TRACKING, RepVerdicts.LOST_TRACKING_MESSAGE)
        assertEquals("✕ Lost tracking", verdictLabel(v))
        assertEquals(Attention, verdictColor(v))
    }

    /** DECISIONS.md P3-86: a wrong exercise attempt says wrong exercise, in red. */
    @Test
    fun a_wrong_exercise_attempt_says_wrong_exercise_in_red() {
        val v = RepVerdict(RepOutcome.WRONG_EXERCISE, RepVerdicts.WRONG_EXERCISE_MESSAGE)
        assertEquals("✕ Wrong exercise", verdictLabel(v))
        assertEquals(Fault, verdictColor(v))
    }
}
