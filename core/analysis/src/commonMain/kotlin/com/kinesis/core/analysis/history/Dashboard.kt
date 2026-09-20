package com.kinesis.core.analysis.history

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord

/**
 * What one exercise's history adds up to.
 *
 * Every measured quantity stays a [BilateralSignal]. CLAUDE.md section 3 applies
 * to a summary exactly as it applies to a frame: aggregation is the easiest
 * place in the codebase to lose the distinction between the two sides, because
 * "average range of motion" reads like one number.
 */
data class ExerciseSummary(

    val exerciseId: String,

    /** Sets performed, whether or not anything completed in them. */
    val setCount: Int,

    /** Repetitions that completed. Aborted and incomplete ones are not work. */
    val validRepCount: Int,

    /** Time actually spent holding, summed over completed holds. */
    val totalHoldMs: Double,

    /**
     * Mean range of motion, **within each side**.
     *
     * A side with no measurement anywhere stays [Double.NaN] rather than
     * becoming zero - zero is a measurement and none was made. For an
     * alternating exercise each arm's mean is taken over the repetitions that
     * arm performed, so a bicep-curl summary reads a real number on both sides
     * rather than `NaN` on both.
     */
    val meanRangeOfMotion: BilateralSignal,

    /** Largest range of motion seen, within each side. */
    val bestRangeOfMotion: BilateralSignal,

    val lastPerformedDay: LocalDay?,
)

/**
 * The whole dashboard, as of a given day.
 *
 * Carries the [streak] rather than leaving the screen to compute its own, so the
 * two cannot disagree about the same history.
 */
data class DashboardSummary(
    val streak: StreakSummary,
    val sessionCount: Int,
    val totalValidReps: Int,
    val totalHoldMs: Double,
    val firstDay: LocalDay?,
    val lastDay: LocalDay?,

    /** One entry per exercise ever performed, most recently performed first. */
    val perExercise: List<ExerciseSummary>,
)

/**
 * Aggregates session history into what the dashboard shows - DECISIONS.md P3-3
 * and P3-10.
 *
 * P3-3 specified: "session history, trends over time (ROM, deviation, and the
 * other Layer 1/2 outputs already produced per rep), built entirely from
 * `RepRecord`/`HoldRecord` history the engine already generates. **No new data
 * collection.**" This is that, and nothing more - every number below is already
 * measured by the engine, and this only adds them up.
 *
 * ### What is missing, and why
 *
 * **Deviation is not here.** It is a Level 2 claim measured against a
 * `CalibrationBaseline`, which does not exist yet - P6 builds it. Reporting
 * "below your normal" without a baseline would be inventing the comparison, so
 * the dashboard reports what the records measure on their own until there is
 * something honest to compare them against. P3-10 records this as blocked rather
 * than dropped.
 *
 * ### Where this runs
 *
 * Post-rep path, in the CONTRACTS.md section 7 sense: once, when a screen opens,
 * over history already written. It walks the whole history and allocates freely,
 * which is what that path is for. Nothing here is reachable from the per-frame
 * path.
 */
object Dashboard {

    fun summarise(sessions: List<SessionRecord>, today: LocalDay): DashboardSummary {
        val streak = Streaks.summarise(sessions, today)
        if (sessions.isEmpty()) {
            return DashboardSummary(
                streak = streak,
                sessionCount = 0,
                totalValidReps = 0,
                totalHoldMs = 0.0,
                firstDay = null,
                lastDay = null,
                perExercise = emptyList(),
            )
        }

        val accumulators = LinkedHashMap<String, ExerciseAccumulator>()
        for (session in sessions) {
            val day = session.localDay
            for (set in session.sets) {
                val accumulator = accumulators.getOrPut(set.exerciseId) {
                    ExerciseAccumulator(set.exerciseId)
                }
                accumulator.add(set, day)
            }
        }

        val days = sessions.map { it.localDay }
        val perExercise = accumulators.values
            .map { it.summarise() }
            .sortedWith(
                compareByDescending<ExerciseSummary> { it.lastPerformedDay?.dayNumber ?: Int.MIN_VALUE }
                    .thenBy { it.exerciseId },
            )

        return DashboardSummary(
            streak = streak,
            sessionCount = sessions.size,
            totalValidReps = perExercise.sumOf { it.validRepCount },
            totalHoldMs = perExercise.sumOf { it.totalHoldMs },
            firstDay = days.minByOrNull { it.dayNumber },
            lastDay = days.maxByOrNull { it.dayNumber },
            perExercise = perExercise,
        )
    }

    /**
     * Running totals for one exercise.
     *
     * The two sides are accumulated in separate fields that never meet. That is
     * deliberate and is what the guard test checks: the obvious implementation
     * of "mean range of motion" pools every value it can see, which is the old
     * prototype's `(left + right) / 2f` reappearing one layer up from where
     * DECISIONS.md P2-2 found it.
     */
    private class ExerciseAccumulator(val exerciseId: String) {

        var setCount: Int = 0
        var validRepCount: Int = 0
        var totalHoldMs: Double = 0.0
        var lastDay: LocalDay? = null

        private var leftSum: Double = 0.0
        private var leftCount: Int = 0
        private var leftBest: Double = Double.NaN

        private var rightSum: Double = 0.0
        private var rightCount: Int = 0
        private var rightBest: Double = Double.NaN

        fun add(set: com.kinesis.core.domain.session.SetRecord, day: LocalDay) {
            setCount++
            val previous = lastDay
            if (previous == null || previous < day) lastDay = day

            when (set) {
                is RepetitionSetRecord -> for (rep in set.validReps) {
                    validRepCount++
                    addLeft(rep.rangeOfMotion.left)
                    addRight(rep.rangeOfMotion.right)
                }

                is HoldSetRecord -> for (hold in set.validHolds) {
                    totalHoldMs += hold.totalHoldMs
                }
            }
        }

        /**
         * A side that was not measured contributes nothing rather than poisoning
         * the mean. An alternating exercise records one side per repetition and
         * `NaN` on the other, so propagating it would make every bicep-curl
         * summary read `NaN` on both arms.
         */
        private fun addLeft(value: Double) {
            if (value.isNaN()) return
            leftSum += value
            leftCount++
            if (leftBest.isNaN() || value > leftBest) leftBest = value
        }

        private fun addRight(value: Double) {
            if (value.isNaN()) return
            rightSum += value
            rightCount++
            if (rightBest.isNaN() || value > rightBest) rightBest = value
        }

        fun summarise(): ExerciseSummary = ExerciseSummary(
            exerciseId = exerciseId,
            setCount = setCount,
            validRepCount = validRepCount,
            totalHoldMs = totalHoldMs,
            meanRangeOfMotion = BilateralSignal(
                left = if (leftCount == 0) Double.NaN else leftSum / leftCount,
                right = if (rightCount == 0) Double.NaN else rightSum / rightCount,
            ),
            bestRangeOfMotion = BilateralSignal(left = leftBest, right = rightBest),
            lastPerformedDay = lastDay,
        )
    }
}
