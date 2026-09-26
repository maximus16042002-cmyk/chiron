package com.kinesis.core.analysis

import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Permanent guard - CLAUDE.md sections 3 and 15, DECISIONS.md P3-10.
 *
 * The dashboard aggregation. DECISIONS.md P3-3 specified it as "session history,
 * trends over time (ROM, deviation, and the other Layer 1/2 outputs already
 * produced per rep), built entirely from `RepRecord`/`HoldRecord` history the
 * engine already generates. No new data collection."
 *
 * ### The rule that matters most here
 *
 * Aggregation is where bilateral integrity is easiest to lose. Every summary in
 * this file is a pile of numbers being reduced, and the obvious way to write
 * "average range of motion" is to average everything in sight - which is
 * precisely the prototype's `(left + right) / 2f`, reintroduced one layer up
 * (DECISIONS.md P2-2). CLAUDE.md section 3 applies to the dashboard exactly as
 * it applies to the engine: left and right stay separate, and every summary
 * value stays a [BilateralSignal].
 *
 * ### What is deliberately absent
 *
 * P3-3 names "deviation" among the trends. Deviation is a Level 2 claim measured
 * against a `CalibrationBaseline`, and no such type exists yet - P6 builds it.
 * Nothing here reports deviation, and P3-10 records that as blocked rather than
 * dropped. What this summarises is what `RepRecord` and `HoldRecord` already
 * measure on their own.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class DashboardSummaryTest {

    private val today = LocalDay(20_000)

    // --- Counting -------------------------------------------------------------------

    @Test
    fun only_completed_repetitions_are_counted() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    repSet("squat", valid = 8, aborted = 3, incomplete = 2),
                ),
            ),
            today,
        )

        assertEquals(8, summary.totalValidReps, "abandoned repetitions were counted as work")
        assertEquals(8, summary.perExercise.single().validRepCount)
    }

    @Test
    fun hold_time_comes_from_holds_and_never_from_repetitions() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    repSet("squat", valid = 10),
                    holdSet("plank", heldMs = listOf(30_000.0, 25_000.0)),
                ),
            ),
            today,
        )

        assertEquals(55_000.0, summary.totalHoldMs, absoluteTolerance = 1e-9)

        val squat = summary.perExercise.single { it.exerciseId == "squat" }
        val plank = summary.perExercise.single { it.exerciseId == "plank" }
        assertEquals(0.0, squat.totalHoldMs, "a repetition set contributed hold time")
        assertEquals(0, plank.validRepCount, "a hold contributed a repetition count")
    }

    @Test
    fun a_too_short_hold_contributes_no_hold_time() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    HoldSetRecord(
                        exerciseId = "plank",
                        startedAtEpochMs = dayAt(0),
                        holds = listOf(hold(9_000.0, HoldValidity.TOO_SHORT)),
                    ),
                ),
            ),
            today,
        )
        assertEquals(0.0, summary.totalHoldMs, "an attempt that never became a hold was counted")
    }

    // --- Bilateral integrity, one layer up ----------------------------------------------

    /**
     * The guard this file exists for. Two repetitions with left ranges of 100 and
     * 110 and right ranges of 50 and 60 must summarise as left 105, right 55. An
     * implementation that pooled all four values would report 80 on both sides -
     * a number that is not wrong so much as meaningless, and which would make an
     * asymmetric athlete look perfectly symmetric.
     */
    @Test
    fun range_of_motion_is_averaged_within_each_side_and_never_across_them() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    RepetitionSetRecord(
                        "squat",
                        dayAt(0),
                        listOf(
                            rep(1, RepValidity.VALID, BilateralSignal(100.0, 50.0)),
                            rep(2, RepValidity.VALID, BilateralSignal(110.0, 60.0)),
                        ),
                    ),
                ),
            ),
            today,
        )

        val mean = summary.perExercise.single().meanRangeOfMotion
        assertEquals(105.0, mean.left, absoluteTolerance = 1e-9, message = "left mean")
        assertEquals(55.0, mean.right, absoluteTolerance = 1e-9, message = "right mean")
        assertTrue(
            mean.asymmetry() > 0.0,
            "an asymmetric history summarised as perfectly symmetric, which is what " +
                "pooling the two sides looks like",
        )
    }

    /**
     * An alternating exercise records one side per repetition and `NaN` on the
     * other - that is how the absence stays visible (P2-2). A per-side mean must
     * skip the side that did not contribute rather than propagating its `NaN`,
     * or every bicep-curl summary in the product reads `NaN` on both arms.
     */
    @Test
    fun an_alternating_exercise_keeps_each_arms_average_separate() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    RepetitionSetRecord(
                        "bicep_curl",
                        dayAt(0),
                        listOf(
                            rep(1, RepValidity.VALID, BilateralSignal(90.0, Double.NaN), Side.LEFT),
                            rep(2, RepValidity.VALID, BilateralSignal(Double.NaN, 70.0), Side.RIGHT),
                            rep(3, RepValidity.VALID, BilateralSignal(94.0, Double.NaN), Side.LEFT),
                            rep(4, RepValidity.VALID, BilateralSignal(Double.NaN, 74.0), Side.RIGHT),
                        ),
                    ),
                ),
            ),
            today,
        )

        val mean = summary.perExercise.single().meanRangeOfMotion
        assertEquals(92.0, mean.left, absoluteTolerance = 1e-9, message = "left arm mean")
        assertEquals(72.0, mean.right, absoluteTolerance = 1e-9, message = "right arm mean")
    }

    /**
     * A side that never contributed anywhere stays `NaN`. It is not zero: zero is
     * a measurement, and no measurement was made. The near-side-only row from
     * P3-8 is the case this protects.
     */
    @Test
    fun a_side_that_was_never_measured_summarises_as_absent_and_not_as_zero() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    RepetitionSetRecord(
                        "row",
                        dayAt(0),
                        listOf(
                            rep(1, RepValidity.VALID, BilateralSignal(80.0, Double.NaN)),
                            rep(2, RepValidity.VALID, BilateralSignal(82.0, Double.NaN)),
                        ),
                    ),
                ),
            ),
            today,
        )

        val mean = summary.perExercise.single().meanRangeOfMotion
        assertEquals(81.0, mean.left, absoluteTolerance = 1e-9)
        assertTrue(
            mean.right.isNaN(),
            "an unmeasured side summarised as " + mean.right + " rather than staying absent",
        )
    }

    @Test
    fun the_best_range_of_motion_is_the_largest_seen_on_each_side_separately() {
        val summary = Dashboard.summarise(
            listOf(
                session(
                    dayAt(0),
                    RepetitionSetRecord(
                        "squat",
                        dayAt(0),
                        listOf(
                            rep(1, RepValidity.VALID, BilateralSignal(100.0, 70.0)),
                            rep(2, RepValidity.VALID, BilateralSignal(95.0, 88.0)),
                        ),
                    ),
                ),
            ),
            today,
        )

        val best = summary.perExercise.single().bestRangeOfMotion
        assertEquals(100.0, best.left, absoluteTolerance = 1e-9)
        assertEquals(88.0, best.right, absoluteTolerance = 1e-9)
    }

    // --- Shape of the summary -----------------------------------------------------------

    @Test
    fun history_is_grouped_per_exercise_and_ordered_by_most_recently_performed() {
        val summary = Dashboard.summarise(
            listOf(
                session(dayAt(-5), repSet("squat", valid = 5)),
                session(dayAt(-2), repSet("pushup", valid = 5)),
                session(dayAt(-1), repSet("squat", valid = 5)),
            ),
            today,
        )

        assertEquals(
            listOf("squat", "pushup"),
            summary.perExercise.map { it.exerciseId },
            "the most recently performed exercise should come first",
        )
        assertEquals(today.plusDays(-1), summary.perExercise.first().lastPerformedDay)
        assertEquals(2, summary.perExercise.first().setCount)
    }

    @Test
    fun the_summary_carries_the_streak_so_the_two_cannot_disagree() {
        val sessions = listOf(-2, -1, 0).map { session(dayAt(it), repSet("squat", valid = 4)) }
        val summary = Dashboard.summarise(sessions, today)

        assertEquals(3, summary.streak.currentDays)
        assertEquals(3, summary.sessionCount)
        assertEquals(today.plusDays(-2), summary.firstDay)
        assertEquals(today, summary.lastDay)
    }

    @Test
    fun an_empty_history_summarises_as_absent_rather_than_as_zeroes_that_look_measured() {
        val summary = Dashboard.summarise(emptyList(), today)

        assertEquals(0, summary.sessionCount)
        assertEquals(0, summary.totalValidReps)
        assertTrue(summary.perExercise.isEmpty())
        assertNull(summary.firstDay)
        assertNull(summary.lastDay)
    }

    // --- Building history -----------------------------------------------------------------

    private fun dayAt(offsetDays: Int): Double =
        (today.dayNumber + offsetDays) * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0

    private fun session(epochMs: Double, vararg sets: SetRecord): SessionRecord = SessionRecord(
        sessionId = "s" + epochMs.toLong() + "-" + sets.size,
        startedAtEpochMs = epochMs,
        utcOffsetMinutes = 0,
        sets = sets.toList(),
    )

    private fun repSet(
        exerciseId: String,
        valid: Int = 0,
        aborted: Int = 0,
        incomplete: Int = 0,
    ): RepetitionSetRecord {
        var index = 0
        val reps = ArrayList<RepRecord>()
        repeat(valid) { reps += rep(++index, RepValidity.VALID) }
        repeat(aborted) { reps += rep(++index, RepValidity.ABORTED) }
        repeat(incomplete) { reps += rep(++index, RepValidity.INCOMPLETE) }
        return RepetitionSetRecord(exerciseId, 0.0, reps)
    }

    private fun holdSet(exerciseId: String, heldMs: List<Double>): HoldSetRecord =
        HoldSetRecord(exerciseId, 0.0, heldMs.map { hold(it, HoldValidity.VALID) })

    private fun rep(
        index: Int,
        validity: RepValidity,
        rangeOfMotion: BilateralSignal = BilateralSignal(90.0, 90.0),
        side: Side? = null,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = 0.0,
        endMs = 2_000.0,
        validity = validity,
        rangeOfMotion = rangeOfMotion,
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(80.0, 80.0),
        descentMs = 1_000.0,
        ascentMs = 1_000.0,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )

    private fun hold(totalHoldMs: Double, validity: HoldValidity): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = totalHoldMs + 2_000.0,
        validity = validity,
        totalHoldMs = totalHoldMs,
        longestUnbrokenMs = totalHoldMs,
        breakCount = 0,
        meanAbsDeviation = 3.0,
        maxAbsDeviation = 8.0,
        coverage = 1.0,
        meanHeldValue = Double.NaN,
        heldValueSd = Double.NaN,
    )
}
