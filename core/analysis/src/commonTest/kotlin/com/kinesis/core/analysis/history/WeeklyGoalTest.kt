package com.kinesis.core.analysis.history

import com.kinesis.core.analysis.history.HistoryFixtures.reps
import com.kinesis.core.analysis.history.HistoryFixtures.session
import com.kinesis.core.analysis.history.HistoryFixtures.today
import com.kinesis.core.domain.session.LocalDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The weekly goal - DECISIONS.md P3-49: a number of active days a week, an active day being the
 * streak's (P3-10), so two sessions on one day count once and the goal agrees with the streak. The
 * week starts on the day the platform says the locale starts it on, passed in as a number, so the
 * core stays locale-free.
 *
 * Weekdays are numbered as `java.util.Calendar` numbers them, 1 = Sunday to 7 = Saturday, so the
 * platform hands its own value straight over.
 */
class WeeklyGoalTest {

    private val sunday = 1
    private val monday = 2

    @Test
    fun weekdays_follow_the_calendar_from_a_thursday_epoch() {
        assertEquals(5, WeeklyGoal.weekday(LocalDay(0)), "1970-01-01 was a Thursday")
        assertEquals(1, WeeklyGoal.weekday(LocalDay(3)), "1970-01-04 was a Sunday")
        assertEquals(4, WeeklyGoal.weekday(LocalDay(-1)), "1969-12-31 was a Wednesday: a floor, not a remainder")
        assertEquals(7, WeeklyGoal.weekday(LocalDay(-5)), "1969-12-27 was a Saturday")
    }

    @Test
    fun the_week_starts_on_the_locale_s_first_day() {
        // 1970-01-01, a Thursday: the week began on Monday 29 December, or on Sunday the 28th.
        assertEquals(LocalDay(-3), WeeklyGoal.weekStart(LocalDay(0), firstWeekday = monday))
        assertEquals(LocalDay(-4), WeeklyGoal.weekStart(LocalDay(0), firstWeekday = sunday))
        // On the first day itself, the week starts today.
        assertEquals(LocalDay(-3), WeeklyGoal.weekStart(LocalDay(-3), firstWeekday = monday))
    }

    @Test
    fun active_days_this_week_are_counted_once_each() {
        val start = WeeklyGoal.weekStart(today, monday)
        val offset = start.daysUntil(today)
        val history = listOf(
            session(-offset, reps("squat", 3)),
            session(-offset, reps("squat", 3)),
            session(0, reps("plank_free", 1)),
            session(-offset - 1, reps("squat", 3)),
        )
        val progress = WeeklyGoal.progress(history, today, firstWeekday = monday, goalDays = 3)

        assertEquals(start, progress.weekStart)
        val expected = if (offset == 0) 1 else 2
        assertEquals(expected, progress.activeDays, "two sessions on one day count once; last week's not at all")
        assertEquals(3, progress.goalDays)
        assertFalse(progress.met)
    }

    @Test
    fun a_day_of_abandoned_work_does_not_count_toward_the_goal() {
        val progress = WeeklyGoal.progress(listOf(session(0, reps("squat", 0, aborted = 6))), today, monday, goalDays = 1)
        assertEquals(0, progress.activeDays)
        assertFalse(progress.met)
    }

    @Test
    fun the_goal_is_met_on_reaching_it() {
        val progress = WeeklyGoal.progress(listOf(session(0, reps("squat", 1))), today, monday, goalDays = 1)
        assertTrue(progress.met)
    }

    @Test
    fun a_goal_is_one_to_seven_days_and_a_weekday_one_to_seven() {
        assertFailsWith<IllegalArgumentException> { WeeklyGoal.progress(emptyList(), today, monday, goalDays = 0) }
        assertFailsWith<IllegalArgumentException> { WeeklyGoal.progress(emptyList(), today, monday, goalDays = 8) }
        assertFailsWith<IllegalArgumentException> { WeeklyGoal.weekStart(today, firstWeekday = 0) }
        assertFailsWith<IllegalArgumentException> { WeeklyGoal.weekStart(today, firstWeekday = 8) }
    }
}
