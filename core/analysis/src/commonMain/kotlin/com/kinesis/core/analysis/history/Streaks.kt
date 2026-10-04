package com.kinesis.core.analysis.history

import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord

/**
 * A streak, as of a given day.
 *
 * [currentDays] is what the user is shown; [longestDays] is what they keep after
 * they lose it.
 */
data class StreakSummary(

    /** Consecutive qualifying days ending today or yesterday. Zero if broken. */
    val currentDays: Int,

    /** The longest run ever achieved, whether or not it is the current one. */
    val longestDays: Int,

    /** The most recent day that counted, or `null` if none ever has. */
    val lastQualifyingDay: LocalDay?,

    /** How many days in the whole history counted. Not a streak, a total. */
    val qualifyingDays: Int,
)

/**
 * One calendar day and the work completed on it - DECISIONS.md P3-48.
 *
 * [completedUnits] is the streak's measure (completed repetitions plus completed holds), so a day
 * is [counted] exactly when [Streaks] would count it.
 */
data class DayActivity(val day: LocalDay, val completedUnits: Int) {
    val counted: Boolean get() = completedUnits > 0
}

/**
 * Consecutive-day tracking over session history - DECISIONS.md P3-3 and P3-10.
 *
 * P3-3 specified this as "Duolingo-style consecutive-day tracking. Pure local
 * state: a counter and a calendar view over session timestamps. No new
 * architecture", and explicitly deferred the mechanics - "whether a missed day
 * breaks it or a freeze exists" - to the batch that built them. P3-10 settles
 * them:
 *
 * ### A day counts when work completed on it
 *
 * At least one repetition or one hold must have **completed** - `RepValidity.VALID`
 * or `HoldValidity.VALID`. Opening the app does not count, and neither does a set
 * that was started and abandoned. This agrees with `BilateralRepTracker`, whose
 * repetition count also only advances on `VALID`, so the streak and the rep
 * count can never tell different stories about the same session.
 *
 * No minimum beyond one, and no distinction by exercise or tier: nothing in any
 * document asks for either, and a threshold nobody specified is a threshold
 * invented here.
 *
 * ### A missed day breaks it, and there is no freeze
 *
 * Deliberate, and recorded in P3-10 rather than omitted quietly. A freeze needs
 * state of its own - how many are held, how many are spent, how they are earned -
 * which is exactly the "new architecture" P3-3 said this feature would not add,
 * and it is a motivation mechanic with no correctness content. Because the streak
 * is **computed from history rather than stored**, adding one later is a change
 * to this function and not a migration.
 *
 * ### Today's silence is not yet a break
 *
 * A streak ending yesterday is still running this morning. Someone who has not
 * trained yet today has not lost anything, and showing them a zero would be both
 * wrong and the most demotivating thing the feature could do. It breaks once a
 * full day has passed with no work in it.
 *
 * ### No clock
 *
 * [summarise] takes the current day rather than reading one. The shared core has
 * no date/time (FINAL_ARCHITECTURE section 3), the platform supplies it, and the
 * tests are deterministic as a consequence rather than by arrangement.
 *
 * Post-rep path in the CONTRACTS.md section 7 sense: this runs when a screen
 * opens, over a history that has already been written, and may allocate freely.
 */
object Streaks {

    /** The distinct local days on which any work completed. */
    fun qualifyingDays(sessions: List<SessionRecord>): Set<LocalDay> {
        val days = HashSet<LocalDay>()
        for (session in sessions) {
            if (session.completedUnits > 0) days += session.localDay
        }
        return days
    }

    /**
     * Every day from [from] to [to] inclusive, oldest first, with the work completed on it - the
     * week strip on Home and the calendar (DECISIONS.md P3-48). Days without a session are listed
     * with nothing, not left out, so a gap shows as a gap.
     *
     * Each session is bucketed by the offset it was recorded in, as the streak does, and a day is
     * marked by the same rule: [qualifyingDays] and the counted days here are the same set.
     */
    fun activity(sessions: List<SessionRecord>, from: LocalDay, to: LocalDay): List<DayActivity> {
        if (from > to) return emptyList()
        val units = IntArray(from.daysUntil(to) + 1)
        for (session in sessions) {
            val index = from.daysUntil(session.localDay)
            if (index in units.indices) units[index] += session.completedUnits
        }
        return units.indices.map { DayActivity(from.plusDays(it), units[it]) }
    }

    /**
     * The streak as of [today].
     *
     * Order-independent: the history is reduced to a set of days before anything
     * is counted, so the same sessions in any order give the same answer.
     */
    fun summarise(sessions: List<SessionRecord>, today: LocalDay): StreakSummary {
        val days = qualifyingDays(sessions)
        if (days.isEmpty()) {
            return StreakSummary(
                currentDays = 0,
                longestDays = 0,
                lastQualifyingDay = null,
                qualifyingDays = 0,
            )
        }

        val ordered = days.sortedBy { it.dayNumber }

        var longest = 1
        var run = 1
        for (index in 1 until ordered.size) {
            run = if (ordered[index - 1].daysUntil(ordered[index]) == 1) run + 1 else 1
            if (run > longest) longest = run
        }

        // The run is anchored to today if today counted, and to yesterday
        // otherwise - a streak does not break until a whole day has passed
        // without work.
        val anchor = when {
            today in days -> today
            today.plusDays(-1) in days -> today.plusDays(-1)
            else -> null
        }

        var current = 0
        if (anchor != null) {
            var day: LocalDay = anchor
            while (day in days) {
                current++
                day = day.plusDays(-1)
            }
        }

        return StreakSummary(
            currentDays = current,
            longestDays = longest,
            lastQualifyingDay = ordered.last(),
            qualifyingDays = ordered.size,
        )
    }
}
