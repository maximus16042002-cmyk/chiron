package com.kinesis.core.analysis.history

import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord
import kotlin.math.floor
import kotlin.math.min

/**
 * One achievement and how far towards it the history is - DECISIONS.md P3-49.
 *
 * [value] is capped at [target], so a finished achievement reads as done rather than as "150 of
 * 100".
 */
data class Achievement(
    val id: String,
    val title: String,
    val value: Double,
    val target: Double,
    val unit: String,
) {
    val achieved: Boolean get() = value >= target

    /** "64 of 100 repetitions". */
    val progressText: String get() = grouped(value) + " of " + grouped(target) + " " + unit

    private fun grouped(number: Double): String {
        val digits = floor(number).toLong().toString()
        val out = StringBuilder()
        for ((index, digit) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(',')
            out.append(digit)
        }
        return out.toString()
    }
}

/**
 * Achievements - DECISIONS.md P3-49. Modest on purpose: six, no points, no levels.
 *
 * Every one is read from the same summary as the Home and Progress pages (`Dashboard.summarise`),
 * so they count what those count: completed repetitions and holds, and the streak's days. A streak
 * achievement uses the **longest** streak, so it is kept after the streak ends.
 */
object Achievements {

    fun of(sessions: List<SessionRecord>, today: LocalDay): List<Achievement> {
        val summary = Dashboard.summarise(sessions, today)
        val days = summary.streak.qualifyingDays.toDouble()
        val longest = summary.streak.longestDays.toDouble()
        val reps = summary.totalValidReps.toDouble()
        val minutesHeld = floor(summary.totalHoldMs / 60_000.0)
        return listOf(
            achievement("first_workout", "First workout", days, 1.0, "workout"),
            achievement("streak_7", "7 days in a row", longest, 7.0, "days"),
            achievement("streak_30", "30 days in a row", longest, 30.0, "days"),
            achievement("reps_100", "100 repetitions", reps, 100.0, "repetitions"),
            achievement("reps_1000", "1,000 repetitions", reps, 1_000.0, "repetitions"),
            achievement("hold_hour", "An hour held", minutesHeld, 60.0, "minutes"),
        )
    }

    private fun achievement(id: String, title: String, value: Double, target: Double, unit: String) =
        Achievement(id = id, title = title, value = min(value, target), target = target, unit = unit)
}
