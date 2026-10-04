package com.kinesis.core.analysis.history

import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord

/**
 * What a training reminder says - DECISIONS.md P3-49.
 *
 * Composed here, not in the notification code (FEATURE_PLAN rule 4), and decided by the streak's
 * own rule: whether missing today would end the streak is asked of [Streaks.summarise] as of
 * tomorrow, so the text never promises a freeze the rule would not spend. No guilt wording: it says
 * what keeps a streak, never what loses one.
 *
 * Post-rep path in the CONTRACTS.md section 7 sense: it runs once, when a reminder is due, over
 * history already written.
 */
object Reminders {

    /** The reminder for [today], or `null` if work was already completed today. */
    fun message(sessions: List<SessionRecord>, today: LocalDay): String? {
        if (today in Streaks.qualifyingDays(sessions)) return null
        val now = Streaks.summarise(sessions, today)
        if (now.currentDays == 0) return "Time to train."
        val coveredIfMissed = Streaks.summarise(sessions, today.plusDays(1)).currentDays > 0
        return if (coveredIfMissed) {
            "Your " + now.currentDays + "-day streak continues if you train today. If you don't, a freeze covers today."
        } else {
            "Train today to keep your " + now.currentDays + "-day streak."
        }
    }
}
