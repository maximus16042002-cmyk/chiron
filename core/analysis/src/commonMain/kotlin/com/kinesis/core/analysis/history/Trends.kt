package com.kinesis.core.analysis.history

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord

/**
 * One set, as a point on its exercise's trend - DECISIONS.md P3-51.
 *
 * [rangeOfMotion] is each side's own mean over the set's counted repetitions, a [BilateralSignal]
 * so the two can never be read as one number; a side the set did not measure is [Double.NaN].
 */
data class TrendPoint(
    val day: LocalDay,
    val startedAtEpochMs: Double,
    val countedRepetitions: Int,
    val rangeOfMotion: BilateralSignal,
    /** Time held over the set's completed holds; 0 for a repetition set. */
    val heldMs: Double,
)

/**
 * Each exercise's trend, set by set - DECISIONS.md P3-51. Measurements only: no fit, no slope, no
 * "improving". Post-rep path in the CONTRACTS.md section 7 sense: it runs when a page opens, over
 * history already written.
 */
object Trends {

    /** The last [limit] sets of [exerciseId], oldest first. */
    fun of(sessions: List<SessionRecord>, exerciseId: String, limit: Int): List<TrendPoint> {
        require(limit > 0) { "a trend needs at least one point, got " + limit }
        val points = ArrayList<TrendPoint>()
        for (session in sessions.sortedBy { it.startedAtEpochMs }) {
            for (set in session.sets) {
                if (set.exerciseId != exerciseId) continue
                points += when (set) {
                    is RepetitionSetRecord -> {
                        val counted = set.validReps
                        TrendPoint(
                            day = session.localDay,
                            startedAtEpochMs = session.startedAtEpochMs,
                            countedRepetitions = counted.size,
                            rangeOfMotion = BilateralSignal(
                                left = meanOfMeasured(counted.map { it.rangeOfMotion.left }),
                                right = meanOfMeasured(counted.map { it.rangeOfMotion.right }),
                            ),
                            heldMs = 0.0,
                        )
                    }
                    is HoldSetRecord -> TrendPoint(
                        day = session.localDay,
                        startedAtEpochMs = session.startedAtEpochMs,
                        countedRepetitions = 0,
                        rangeOfMotion = BilateralSignal(Double.NaN, Double.NaN),
                        heldMs = set.validHolds.sumOf { it.totalHoldMs },
                    )
                }
            }
        }
        return points.takeLast(limit)
    }

    /** The mean of the measured values; NaN when none was measured - never 0. */
    private fun meanOfMeasured(values: List<Double>): Double {
        val measured = values.filter { !it.isNaN() }
        return if (measured.isEmpty()) Double.NaN else measured.sum() / measured.size
    }
}
