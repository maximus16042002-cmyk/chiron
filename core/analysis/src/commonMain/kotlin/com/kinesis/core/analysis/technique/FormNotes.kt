package com.kinesis.core.analysis.technique

import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.finding.TechniqueFault

/**
 * One cited technique rule's note for a whole set - DECISIONS.md P3-74.
 *
 * @property repetitionsFlagged how many of the set's repetitions the rule fired on.
 * @property repetitionsJudged how many repetitions the set had.
 * @property example the first flagged repetition's own sentence, composed by [TechniqueRules].
 */
data class FormNote(
    val faultId: String,
    val repetitionsFlagged: Int,
    val repetitionsJudged: Int,
    val example: String,
    val sourceRef: String,
)

/**
 * The "Form notes" a set summary shows - DECISIONS.md P3-74, the owner's decision to show Layer 2.
 *
 * Only the six cited rules of [TechniqueRules], selected by fault id: the ACSM tempo message is a
 * `TechniqueFault` too and stays off (P3-30), and Layer 1's personal deviations stay off (P3-42).
 * Grouped per rule, in the rules' own order, so the card reads the same whichever repetition
 * happened first. Post-rep: it reads a finished set's results.
 */
object FormNotes {

    /** The rules shown, in the order the card lists them. */
    val SHOWN: List<String> = listOf(
        TechniqueRules.RAISED_ABOVE_SHOULDER_LEVEL,
        TechniqueRules.CURL_STARTED_ELBOW_BENT,
        TechniqueRules.CURL_UPPER_ARM_MOVED_FORWARD,
        TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED,
        TechniqueRules.PUSHUP_HIPS_SAGGED,
        TechniqueRules.PUSHUP_HIPS_RAISED,
        TechniqueRules.SQUAT_TRUNK_AHEAD_OF_SHIN,
    )

    /** The card's footnote, composed here so the app adds no claim of its own (CLAUDE.md section 2). */
    const val FOOTNOTE: String =
        "From published exercise guidelines. How often these notes agree with a coach's judgement has not been measured."

    fun of(results: List<RepetitionAnalysisResult>): List<FormNote> =
        SHOWN.mapNotNull { faultId ->
            val flagged = results.mapNotNull { result ->
                result.findings.firstOrNull { it is TechniqueFault && it.faultId == faultId } as TechniqueFault?
            }
            flagged.firstOrNull()?.let { first ->
                FormNote(
                    faultId = faultId,
                    repetitionsFlagged = flagged.size,
                    repetitionsJudged = results.size,
                    example = first.displayText,
                    sourceRef = first.sourceRef,
                )
            }
        }
}
