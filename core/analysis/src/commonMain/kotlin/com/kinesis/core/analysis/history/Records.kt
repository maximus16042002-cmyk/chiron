package com.kinesis.core.analysis.history

import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.session.SessionRecord
import kotlin.math.roundToInt

/** What a record is a record of - DECISIONS.md P3-49. */
enum class RecordKind {
    MOST_REPETITIONS_IN_A_SET,
    LONGEST_HOLD,
    LARGEST_RANGE_OF_MOTION,
}

/**
 * A number a set has just set, larger than every earlier one for the same exercise.
 *
 * [side] is the side for range of motion, which is recorded per side and never averaged
 * (CLAUDE.md section 3), and `null` for the others.
 */
data class NewRecord(
    val exerciseId: String,
    val kind: RecordKind,
    val side: Side?,
    val value: Double,
    val previous: Double,
) {

    /**
     * One sentence, composed here rather than in the UI (FEATURE_PLAN rule 4). It names both
     * numbers and nothing else: "largest" and "longest" are measurements, and a larger range is
     * not called better - for some movements it is not.
     */
    val displayText: String
        get() = when (kind) {
            RecordKind.MOST_REPETITIONS_IN_A_SET ->
                "Most repetitions counted in one set: " + value.roundToInt() + ". Your most before was " +
                    previous.roundToInt() + "."
            RecordKind.LONGEST_HOLD ->
                "Longest hold: " + seconds(value) + ". Your longest before was " + seconds(previous) + "."
            RecordKind.LARGEST_RANGE_OF_MOTION ->
                "Largest range of motion on your " + (if (side == Side.LEFT) "left" else "right") + " side: " +
                    degrees(value) + ". Your largest before was " + degrees(previous) + "."
        }

    private fun seconds(ms: Double): String = (ms / 1000.0).roundToInt().toString() + " s"

    private fun degrees(value: Double): String = value.roundToInt().toString() + "°"
}

/**
 * Personal records - DECISIONS.md P3-49.
 *
 * Read from the same per-exercise summary the Progress page shows (`Dashboard.perExercise`), so a
 * record and the page cannot disagree: only counted repetitions and completed holds count, and a
 * side that was not measured is not a zero.
 *
 * Post-rep path in the CONTRACTS.md section 7 sense: it runs once, after a set has been saved,
 * over history already written.
 */
object Records {

    /**
     * The records [session] sets against [before], the history before it.
     *
     * The first time an exercise is done sets no record: there is nothing it is larger than, and
     * "a new record" on a first set would be noise. Equalling a record is not a new one.
     */
    fun newIn(before: List<SessionRecord>, session: SessionRecord): List<NewRecord> = newIn(before, listOf(session))

    /**
     * The records [sessions] set together against [before] - a guided workout's sets (P3-50). Each
     * exercise's largest value across them is compared once, so a workout names one record per
     * kind, not one per set.
     */
    fun newIn(before: List<SessionRecord>, sessions: List<SessionRecord>): List<NewRecord> {
        val prior = Dashboard.perExercise(before).associateBy { it.exerciseId }
        val now = Dashboard.perExercise(sessions)
        val records = ArrayList<NewRecord>()
        for (summary in now.sortedBy { it.exerciseId }) {
            val earlier = prior[summary.exerciseId] ?: continue
            val id = summary.exerciseId
            if (earlier.mostRepsInASet > 0 && summary.mostRepsInASet > earlier.mostRepsInASet) {
                records += NewRecord(
                    id, RecordKind.MOST_REPETITIONS_IN_A_SET, null,
                    summary.mostRepsInASet.toDouble(), earlier.mostRepsInASet.toDouble(),
                )
            }
            if (larger(summary.longestHoldMs, earlier.longestHoldMs)) {
                records += NewRecord(id, RecordKind.LONGEST_HOLD, null, summary.longestHoldMs, earlier.longestHoldMs)
            }
            val nowLeft = summary.bestRangeOfMotion.left
            val wasLeft = earlier.bestRangeOfMotion.left
            if (larger(nowLeft, wasLeft)) {
                records += NewRecord(id, RecordKind.LARGEST_RANGE_OF_MOTION, Side.LEFT, nowLeft, wasLeft)
            }
            val nowRight = summary.bestRangeOfMotion.right
            val wasRight = earlier.bestRangeOfMotion.right
            if (larger(nowRight, wasRight)) {
                records += NewRecord(id, RecordKind.LARGEST_RANGE_OF_MOTION, Side.RIGHT, nowRight, wasRight)
            }
        }
        return records
    }

    /** Both measured, and the new one strictly larger. */
    private fun larger(now: Double, before: Double): Boolean = now.isFinite() && before.isFinite() && now > before
}
