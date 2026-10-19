package com.kinesis.core.analysis.verdict

import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.analysis.history.Trends
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What "counted" means wherever a stored set is shown - DECISIONS.md P3-78: the set summary,
 * history, streaks, the dashboard and trends all count the verdict's `COUNTED`, so none of them can
 * disagree with the number the live screen showed.
 */
class CountedRepetitionsTest {

    @Test
    fun a_stored_set_counts_what_the_verdict_counts_not_every_valid_record() {
        val set = RepetitionSetRecord(
            "bicep_curl", 0.0,
            listOf(
                curl(1, durationMs = 2_000.0), // counted
                curl(2, durationMs = 500.0), // too fast
                curl(3, durationMs = 2_000.0, peak = 140.0), // started bent: a form fault, not counted (P3-88)
            ),
        )
        assertEquals(3, set.completedUnits, "the measurement layer still sees three VALID records")
        assertEquals(1, CountedRepetitions.units(set))
        assertEquals(listOf(1), CountedRepetitions.of(set).map { it.repIndex })
        assertEquals(3, CountedRepetitions.attempts(set))
    }

    @Test
    fun a_hold_counts_as_before_and_a_session_sums_its_sets() {
        val hold = HoldSetRecord("plank", 0.0, listOf(HoldRecord(0.0, 30_000.0, HoldValidity.VALID, 30_000.0, 30_000.0, 0, 2.0, 5.0, 1.0, 178.0, 1.0)))
        val curls = RepetitionSetRecord("bicep_curl", 0.0, listOf(curl(1, 2_000.0), curl(2, 500.0)))
        assertEquals(1, CountedRepetitions.units(hold))
        assertEquals(2, CountedRepetitions.units(SessionRecord("s", 0.0, 0, listOf(curls, hold))))
    }

    /**
     * History counts as the screen did: a day of only too-fast curls is no streak day, and the
     * dashboard and trends see the two counted curls, not the three VALID records.
     */
    @Test
    fun streaks_the_dashboard_and_trends_count_the_verdicts_count() {
        val rejectedOnly = SessionRecord("a", DAY_MS, 0, listOf(RepetitionSetRecord("bicep_curl", DAY_MS, listOf(curl(1, 500.0), curl(2, 600.0)))))
        assertEquals(emptySet(), Streaks.qualifyingDays(listOf(rejectedOnly)))

        val mixed = SessionRecord(
            "b", 2 * DAY_MS, 0,
            listOf(RepetitionSetRecord("bicep_curl", 2 * DAY_MS, listOf(curl(1, 2_000.0), curl(2, 500.0), curl(3, 2_000.0, peak = 140.0)))),
        )
        assertEquals(1, Streaks.qualifyingDays(listOf(mixed)).size)
        assertEquals(1, Dashboard.perExercise(listOf(mixed)).single().validRepCount)
        assertEquals(1, Trends.of(listOf(mixed), "bicep_curl", 5).single().countedRepetitions)
    }

    /** An exercise the library no longer knows keeps the measurement layer's count rather than vanishing. */
    @Test
    fun an_unknown_exercise_falls_back_to_valid_records() {
        val set = RepetitionSetRecord("retired_exercise", 0.0, listOf(curl(1, 500.0)))
        assertEquals(1, CountedRepetitions.units(set))
    }

    private fun curl(index: Int, durationMs: Double, peak: Double = 170.0) = RepRecord(
        repIndex = index,
        attributedSide = Side.RIGHT,
        startMs = index * 5_000.0,
        endMs = index * 5_000.0 + durationMs,
        validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(Double.NaN, peak - 45.0),
        peakValue = BilateralSignal(Double.NaN, peak),
        troughValue = BilateralSignal(Double.NaN, 45.0),
        descentMs = durationMs / 2.0,
        ascentMs = durationMs / 2.0,
        resampledLeft = DoubleArray(64) { Double.NaN },
        resampledRight = DoubleArray(64) { Double.NaN },
    )

    private companion object {
        /** Noon UTC on 2026-10-06, in epoch milliseconds: far from any day boundary. */
        const val DAY_MS = 1_791_288_000_000.0
    }
}
