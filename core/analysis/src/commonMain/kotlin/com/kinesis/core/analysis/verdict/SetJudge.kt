package com.kinesis.core.analysis.verdict

import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.rep.RepRecord

/**
 * One live set's verdicts, in the order its records arrive - DECISIONS.md P3-78.
 *
 * **Post-rep path.** [judge] is called once per completed record, a few times a minute, never per
 * frame; the list it keeps grows by one each time. It lives here, outside the real-time path's
 * files, so `SharedCoreSourceGuardTest` keeps guarding those files as they are.
 */
class SetJudge(private val profile: RepetitionExercise) {

    private val judged = ArrayList<JudgedRep>()

    /** Repetitions counted so far. */
    var counted: Int = 0
        private set

    /** [record]'s verdict, given the set so far; see [RepVerdicts.judge]. */
    fun judge(record: RepRecord, otherSideMidRepetition: Boolean): RepVerdict {
        val verdict = RepVerdicts.judge(profile, record, judged, otherSideMidRepetition)
        judged += JudgedRep(record, verdict)
        if (verdict.outcome.counts) counted++
        return verdict
    }
}
