package com.kinesis.core.analysis.verdict

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord

/**
 * "Counted", wherever a stored set is shown - DECISIONS.md P3-78. The set summary, history,
 * streaks, the dashboard and trends all ask here, so none of them can disagree with the count the
 * live screen showed. `SetRecord.completedUnits` stays the measurement layer's VALID count, which
 * calibration, the export and the evaluation use.
 */
object CountedRepetitions {

    private val repetitionProfiles: Map<String, RepetitionExercise> =
        ExerciseLibrary.all.filterIsInstance<RepetitionExercise>().associateBy { it.id }

    /**
     * Every record of [set] with its verdict, in order. An exercise the library no longer has is
     * shown as its counter recorded it: VALID counted, anything else not an attempt.
     */
    fun judged(set: RepetitionSetRecord): List<JudgedRep> {
        val profile = repetitionProfiles[set.exerciseId]
            ?: return set.reps.map { JudgedRep(it, if (it.validity == RepValidity.VALID) KEPT else UNJUDGED) }
        val verdicts = RepVerdicts.judgeSet(profile, set.reps)
        return set.reps.mapIndexed { i, rep -> JudgedRep(rep, verdicts[i]) }
    }

    /** The repetitions of [set] that count, in order. */
    fun of(set: RepetitionSetRecord): List<RepRecord> = judged(set).filter { it.verdict.outcome.counts }.map { it.rep }

    /**
     * Attempts the user made: every record but the silent ones - a wobble that never got halfway,
     * or the other side seeing the same movement. What a summary's "counted of N attempts" means.
     */
    fun attempts(set: RepetitionSetRecord): Int = judged(set).count { it.verdict.isAttempt }

    /** Counted repetitions, or completed holds. */
    fun units(set: SetRecord): Int = when (set) {
        is RepetitionSetRecord -> of(set).size
        is HoldSetRecord -> set.completedUnits
    }

    fun units(session: SessionRecord): Int = session.sets.sumOf { units(it) }

    private val KEPT = RepVerdict(RepOutcome.COUNTED, null)
    private val UNJUDGED = RepVerdict(RepOutcome.NOISE, null)
}
