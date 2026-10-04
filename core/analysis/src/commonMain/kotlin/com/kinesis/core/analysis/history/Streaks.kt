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

    /**
     * The active days in the streak still running today. Zero if it has ended. A frozen day keeps
     * a streak running but is not counted in it (P3-49).
     */
    val currentDays: Int,

    /** The largest count any streak has reached, whether or not it is the current one. */
    val longestDays: Int,

    /** The most recent day that counted, or `null` if none ever has. */
    val lastQualifyingDay: LocalDay?,

    /** How many days in the whole history counted. Not a streak, a total. */
    val qualifyingDays: Int,

    /** Freezes held as of today, after any spent on yesterday - DECISIONS.md P3-49. */
    val freezesHeld: Int,
)

/**
 * One calendar day, the work completed on it, and whether a freeze was spent on it -
 * DECISIONS.md P3-48 and P3-49.
 *
 * [completedUnits] is the streak's measure (completed repetitions plus completed holds), so a day
 * is [counted] exactly when [Streaks] would count it. A day is never both counted and [frozen]: a
 * freeze is spent only on a day with no work.
 */
data class DayActivity(val day: LocalDay, val completedUnits: Int, val frozen: Boolean = false) {
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
 * ### A missed day breaks it, unless a freeze covers it
 *
 * P3-10 had no freeze, and said that because the streak is **computed from history rather than
 * stored**, adding one later would be "a change to this function and not a migration". P3-49 is
 * that change, the owner's rule from FEATURE_PLAN Batch 3:
 * - **earning:** a run of consecutive active days earns one freeze on its 7th, 14th, 21st, … day,
 *   unless [MAX_FREEZES_HELD] are already held. A frozen day is not an active day, so it ends the
 *   run and earning starts again from zero;
 * - **spending:** a missed day straight after an active day spends one freeze, if one is held, and
 *   is frozen. If the next day is active the streak continues; if it is missed too, the streak
 *   ends. One freeze at most is spent on a gap;
 * - **never refunded:** a freeze spent on a gap that then ends the streak stays spent. A day's
 *   state never changes once the day is over;
 * - **the count is active days.** A frozen day keeps a streak alive and adds nothing to it.
 *
 * The freezes are derived, never stored, so sync and reinstalling cannot corrupt them.
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

    /** A run earns a freeze on every one of its days divisible by this - P3-49. */
    const val FREEZE_EARNED_EVERY: Int = 7

    /** The most freezes held at once - P3-49. */
    const val MAX_FREEZES_HELD: Int = 2

    /**
     * Every day from [from] to [to] inclusive, oldest first, with the work completed on it and
     * whether a freeze was spent on it, as of [today] - the week strip on Home and the calendar
     * (DECISIONS.md P3-48, P3-49). Days without a session are listed with nothing, not left out,
     * so a gap shows as a gap.
     *
     * Each session is bucketed by the offset it was recorded in, as the streak does, and a day is
     * marked by the same rule: [qualifyingDays] and the counted days here are the same set, and the
     * frozen days are the ones [summarise] spent freezes on.
     */
    fun activity(sessions: List<SessionRecord>, today: LocalDay, from: LocalDay, to: LocalDay): List<DayActivity> {
        if (from > to) return emptyList()
        val units = IntArray(from.daysUntil(to) + 1)
        for (session in sessions) {
            val index = from.daysUntil(session.localDay)
            if (index in units.indices) units[index] += session.completedUnits
        }
        val frozen = walk(qualifyingDays(sessions), today).frozen
        return units.indices.map {
            val day = from.plusDays(it)
            DayActivity(day, units[it], frozen = day in frozen)
        }
    }

    /**
     * The streak as of [today].
     *
     * Order-independent: the history is reduced to a set of days before anything
     * is counted, so the same sessions in any order give the same answer.
     */
    fun summarise(sessions: List<SessionRecord>, today: LocalDay): StreakSummary {
        val days = qualifyingDays(sessions)
        val ledger = walk(days, today)
        return StreakSummary(
            currentDays = ledger.currentDays,
            longestDays = ledger.longestDays,
            lastQualifyingDay = days.maxByOrNull { it.dayNumber },
            qualifyingDays = days.size,
            freezesHeld = ledger.freezesHeld,
        )
    }

    private class Ledger(
        val currentDays: Int,
        val longestDays: Int,
        val freezesHeld: Int,
        val frozen: Set<LocalDay>,
    )

    /**
     * One pass over the active days, oldest first, applying P3-49's rule at every gap.
     *
     * The gap after the last active day is judged against [today], and today itself is never a
     * miss: P3-10's "today's silence is not yet a break". So "yesterday missed" is decided this
     * morning, and a freeze spent on it is spent for good.
     */
    private fun walk(days: Set<LocalDay>, today: LocalDay): Ledger {
        val frozen = HashSet<LocalDay>()
        var held = 0
        var run = 0
        var count = 0
        var longest = 0
        var previous: LocalDay? = null

        // A gap of [missed] days after [last]: spend one freeze on its first day if one is held.
        // Returns whether the streak survives it.
        fun gap(last: LocalDay, missed: Int): Boolean {
            run = 0
            val spent = held > 0
            if (spent) {
                held--
                frozen += last.plusDays(1)
            }
            return spent && missed == 1
        }

        for (day in days.sortedBy { it.dayNumber }) {
            val last = previous
            if (last != null) {
                val missed = last.daysUntil(day) - 1
                if (missed >= 1 && !gap(last, missed)) count = 0
            }
            run++
            count++
            if (count > longest) longest = count
            if (run % FREEZE_EARNED_EVERY == 0 && held < MAX_FREEZES_HELD) held++
            previous = day
        }

        val last = previous
        var current = 0
        if (last != null) {
            // Days strictly between the last active day and today; today is not yet missed.
            val missed = last.daysUntil(today) - 1
            current = if (missed < 1 || gap(last, missed)) count else 0
        }
        return Ledger(currentDays = current, longestDays = longest, freezesHeld = held, frozen = frozen)
    }
}
