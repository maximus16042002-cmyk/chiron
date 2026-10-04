package com.kinesis.core.analysis

import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Permanent guard - DECISIONS.md P3-49, the streak freeze.
 *
 * The owner's rule (FEATURE_PLAN Batch 3): "earn 1 freeze for every 7 consecutive active days,
 * hold at most 2. A single missed day consumes one freeze automatically and the streak continues;
 * two missed days in a row, or none left, resets it." P3-49 makes every choice those words leave
 * open, and each choice is pinned here:
 * - a frozen day ends a run, so earning restarts after it;
 * - one freeze at most is spent on a gap, and it is not refunded if the gap ends the streak anyway;
 * - the count is active days, so a frozen day keeps the streak alive without adding to it;
 * - a day's state never changes once the day is over.
 *
 * Every history here is a list of day offsets from [today], each an active day.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class StreakFreezeRuleTest {

    private val today = LocalDay(20_000)

    // --- Earning ---------------------------------------------------------------------------------

    @Test
    fun the_first_day_earns_nothing() {
        assertEquals(0, Streaks.summarise(active(0), today).freezesHeld)
    }

    @Test
    fun six_consecutive_days_earn_nothing_so_a_miss_after_them_ends_the_streak() {
        val history = active(-7..-2) + active(0)
        val summary = Streaks.summarise(history, today)

        assertEquals(0, summary.freezesHeld)
        assertEquals(1, summary.currentDays, "six days earn no freeze, so the miss on day -1 ended the streak")
        assertEquals(6, summary.longestDays)
        assertFalse(frozen(history, today.plusDays(-1)), "no freeze was held to spend")
    }

    @Test
    fun the_seventh_consecutive_day_earns_a_freeze() {
        assertEquals(1, Streaks.summarise(active(-6..0), today).freezesHeld)
    }

    @Test
    fun a_freeze_earned_on_day_seven_covers_day_eight() {
        val history = active(-8..-2) + active(0)
        val summary = Streaks.summarise(history, today)

        assertTrue(frozen(history, today.plusDays(-1)), "day -1 should be frozen")
        assertEquals(0, summary.freezesHeld, "and the freeze spent on it")
        assertEquals(8, summary.currentDays, "seven days, a frozen one, then today: 8 active days")
    }

    @Test
    fun twenty_one_days_hold_two_freezes_not_three() {
        assertEquals(1, Streaks.summarise(active(-12..0), today).freezesHeld, "13 days: one freeze")
        assertEquals(2, Streaks.summarise(active(-13..0), today).freezesHeld, "14 days: two")
        assertEquals(2, Streaks.summarise(active(-20..0), today).freezesHeld, "21 days: still two, the cap")
    }

    /**
     * The owner's words are "consecutive **active** days", and a frozen day is not active. Ten
     * days, a freeze, then five more: the run after the freeze is five, not fifteen, so the second
     * freeze a fourteen-day run would earn is not earned.
     */
    @Test
    fun a_frozen_day_ends_the_run_and_earning_starts_again_from_zero() {
        val history = active(-15..-6) + active(-4..0)
        val summary = Streaks.summarise(history, today)

        assertTrue(frozen(history, today.plusDays(-5)))
        assertEquals(15, summary.currentDays)
        assertEquals(0, summary.freezesHeld, "the run since the freeze is 5 days; nothing is earned yet")
    }

    // --- Spending --------------------------------------------------------------------------------

    @Test
    fun two_missed_days_in_a_row_end_the_streak_and_the_freeze_stays_spent() {
        val history = active(-10..-4) + active(-1..0)
        val summary = Streaks.summarise(history, today)

        assertTrue(frozen(history, today.plusDays(-3)), "the first missed day took the freeze")
        assertFalse(frozen(history, today.plusDays(-2)), "the second missed day is simply missed")
        assertEquals(2, summary.currentDays, "two misses in a row end the streak")
        assertEquals(0, summary.freezesHeld, "a spent freeze is not refunded")
        assertEquals(7, summary.longestDays)
    }

    /** Two freezes held, two days missed: one is spent, the streak ends, and one is left. */
    @Test
    fun only_one_freeze_is_spent_on_a_gap_however_many_are_held() {
        val history = active(-17..-4) + active(0)
        val summary = Streaks.summarise(history, today)

        assertTrue(frozen(history, today.plusDays(-3)))
        assertFalse(frozen(history, today.plusDays(-2)))
        assertFalse(frozen(history, today.plusDays(-1)))
        assertEquals(1, summary.currentDays)
        assertEquals(1, summary.freezesHeld, "fourteen days earned two; one gap spent one")
    }

    @Test
    fun with_no_freeze_held_one_missed_day_ends_the_streak() {
        val summary = Streaks.summarise(active(-5..-2), today)
        assertEquals(0, summary.currentDays, "yesterday was missed with nothing to cover it")
    }

    @Test
    fun freezes_held_survive_the_streak_ending() {
        // Fourteen days earn two; the two-day gap spends one and ends the streak; one is kept.
        val history = active(-30..-17) + active(-2..0)
        val summary = Streaks.summarise(history, today)

        assertEquals(3, summary.currentDays)
        assertEquals(1, summary.freezesHeld)
        assertEquals(14, summary.longestDays)
    }

    // --- The count ---------------------------------------------------------------------------------

    @Test
    fun a_frozen_day_keeps_the_streak_alive_but_does_not_add_to_it() {
        val summary = Streaks.summarise(active(-8..-2) + active(0), today)
        assertEquals(8, summary.currentDays, "not 9: the frozen day is not an active day")
        assertEquals(8, summary.longestDays)
        assertEquals(8, summary.qualifyingDays)
    }

    // --- Today, and days once they are over ----------------------------------------------------------

    /**
     * This morning, yesterday is decided: a freeze was held, so yesterday is frozen and the streak
     * runs. Tomorrow morning, with today missed too, the streak has ended - and yesterday is still
     * frozen, the freeze still spent. Neither changes after the fact.
     */
    @Test
    fun yesterday_frozen_this_morning_is_still_frozen_tomorrow() {
        val history = active(-8..-2)

        val thisMorning = Streaks.summarise(history, today)
        assertTrue(frozen(history, today.plusDays(-1), asOf = today))
        assertEquals(7, thisMorning.currentDays, "a freeze covers yesterday; the streak runs")
        assertEquals(0, thisMorning.freezesHeld)

        val tomorrow = today.plusDays(1)
        val tomorrowMorning = Streaks.summarise(history, tomorrow)
        assertTrue(frozen(history, today.plusDays(-1), asOf = tomorrow), "yesterday's state changed after the fact")
        assertFalse(frozen(history, today, asOf = tomorrow))
        assertEquals(0, tomorrowMorning.currentDays, "two misses: the streak has ended")
        assertEquals(0, tomorrowMorning.freezesHeld, "and the freeze was not refunded")
    }

    @Test
    fun today_without_work_is_not_frozen_or_missed() {
        val history = active(-6..-1)
        assertFalse(frozen(history, today))
        assertEquals(6, Streaks.summarise(history, today).currentDays)
    }

    // --- What the rule does not depend on ----------------------------------------------------------

    @Test
    fun the_result_does_not_depend_on_session_order() {
        val history = active(-15..-6) + active(-4..0)
        val forwards = Streaks.summarise(history, today)
        val backwards = Streaks.summarise(history.reversed(), today)
        val shuffled = Streaks.summarise(history.sortedBy { (it.startedAtEpochMs * 7919).rem(13.0) }, today)

        assertEquals(forwards, backwards)
        assertEquals(forwards, shuffled)
    }

    /**
     * A set at 23:30 UTC, recorded at UTC+1, belongs to the next local day. Here that day is the
     * seventh of a run, so it earns the freeze that covers day -1. Bucketed by UTC instead, the run
     * is six days and the streak ends.
     */
    @Test
    fun a_day_is_taken_in_the_offset_the_session_was_recorded_in() {
        val lateOnDayMinusThree = (today.dayNumber - 3) * LocalDay.MILLIS_PER_DAY + 23.5 * 3_600_000.0
        val history = active(-8..-3) + session(lateOnDayMinusThree, utcOffsetMinutes = 60) + active(0)
        val summary = Streaks.summarise(history, today)

        assertTrue(frozen(history, today.plusDays(-1)))
        assertEquals(8, summary.currentDays)
    }

    // --- The calendar's view of the same rule ---------------------------------------------------------

    @Test
    fun the_calendar_marks_frozen_days_and_never_marks_one_as_active() {
        val history = active(-8..-2) + active(0)
        val days = Streaks.activity(history, today, from = today.plusDays(-8), to = today.plusDays(2))

        assertEquals(listOf(-1), days.filter { it.frozen }.map { today.daysUntil(it.day) })
        assertTrue(days.none { it.frozen && it.counted }, "a day cannot be both")
        assertEquals(Streaks.qualifyingDays(history), days.filter { it.counted }.map { it.day }.toSet())
        assertTrue(days.filter { it.day > today }.none { it.counted || it.frozen }, "future days are blank")
    }

    // --- Building history ----------------------------------------------------------------------------

    private fun frozen(history: List<SessionRecord>, day: LocalDay, asOf: LocalDay = today): Boolean =
        Streaks.activity(history, asOf, from = day, to = day).single().frozen

    private fun active(offset: Int): List<SessionRecord> = active(offset..offset)

    private fun active(offsets: IntRange): List<SessionRecord> =
        offsets.map { session((today.dayNumber + it) * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0) }

    private fun session(epochMs: Double, utcOffsetMinutes: Int = 0): SessionRecord = SessionRecord(
        sessionId = "s" + epochMs.toLong() + "@" + utcOffsetMinutes,
        startedAtEpochMs = epochMs,
        utcOffsetMinutes = utcOffsetMinutes,
        sets = listOf(
            RepetitionSetRecord(
                exerciseId = "squat",
                startedAtEpochMs = epochMs,
                reps = listOf(
                    RepRecord(
                        repIndex = 1,
                        attributedSide = null,
                        startMs = 0.0,
                        endMs = 2_000.0,
                        validity = RepValidity.VALID,
                        rangeOfMotion = BilateralSignal(90.0, 90.0),
                        peakValue = BilateralSignal(170.0, 170.0),
                        troughValue = BilateralSignal(80.0, 80.0),
                        descentMs = 1_000.0,
                        ascentMs = 1_000.0,
                        resampledLeft = DoubleArray(64),
                        resampledRight = DoubleArray(64),
                    ),
                ),
            ),
        ),
    )
}
