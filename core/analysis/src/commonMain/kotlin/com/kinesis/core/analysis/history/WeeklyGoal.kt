package com.kinesis.core.analysis.history

import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord

/** This week's active days against the goal - DECISIONS.md P3-49. */
data class WeekProgress(val weekStart: LocalDay, val activeDays: Int, val goalDays: Int) {
    val met: Boolean get() = activeDays >= goalDays
}

/**
 * The weekly goal - DECISIONS.md P3-49: a number of **active days** a week.
 *
 * An active day is the streak's qualifying day (P3-10), so two sessions on one day count once,
 * a day of abandoned sets not at all, and the goal and the streak agree about every day.
 *
 * The week starts where the phone's locale starts it. The platform passes that in as a number,
 * numbered as `java.util.Calendar` numbers weekdays (1 = Sunday to 7 = Saturday), so the core
 * stays locale-free (FINAL_ARCHITECTURE section 3) and the platform hands its own value over.
 */
object WeeklyGoal {

    /** The goal before the user sets one. */
    const val DEFAULT_GOAL_DAYS: Int = 3

    /** 1 = Sunday to 7 = Saturday. Day 0, 1970-01-01, was a Thursday. A floor, so it holds before 1970. */
    fun weekday(day: LocalDay): Int = ((day.dayNumber + 4) % 7 + 7) % 7 + 1

    /** The first day of the week [today] is in, for a week starting on [firstWeekday]. */
    fun weekStart(today: LocalDay, firstWeekday: Int): LocalDay {
        require(firstWeekday in 1..7) { "a weekday is 1 (Sunday) to 7 (Saturday), got " + firstWeekday }
        return today.plusDays(-((weekday(today) - firstWeekday + 7) % 7))
    }

    fun progress(sessions: List<SessionRecord>, today: LocalDay, firstWeekday: Int, goalDays: Int): WeekProgress {
        require(goalDays in 1..7) { "a weekly goal is 1 to 7 days, got " + goalDays }
        val start = weekStart(today, firstWeekday)
        val active = Streaks.qualifyingDays(sessions).count { it >= start && it <= today }
        return WeekProgress(weekStart = start, activeDays = active, goalDays = goalDays)
    }
}
