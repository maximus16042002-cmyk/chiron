package com.kinesis.core.analysis.history

import com.kinesis.core.analysis.history.HistoryFixtures.holds
import com.kinesis.core.analysis.history.HistoryFixtures.reps
import com.kinesis.core.analysis.history.HistoryFixtures.session
import com.kinesis.core.analysis.history.HistoryFixtures.today
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Achievements - DECISIONS.md P3-49. Modest, derived from history, counted by the same rules as
 * the streak and the totals: only completed repetitions and holds count.
 */
class AchievementsTest {

    private fun achieved(sessions: List<com.kinesis.core.domain.session.SessionRecord>): Set<String> =
        Achievements.of(sessions, today).filter { it.achieved }.map { it.id }.toSet()

    @Test
    fun an_empty_history_has_achieved_nothing_and_shows_every_target() {
        val all = Achievements.of(emptyList(), today)
        assertEquals(
            listOf("first_workout", "streak_7", "streak_30", "reps_100", "reps_1000", "hold_hour"),
            all.map { it.id },
        )
        assertTrue(all.none { it.achieved })
        assertTrue(all.all { it.value == 0.0 && it.target > 0.0 })
    }

    @Test
    fun the_first_workout_needs_completed_work() {
        assertEquals(emptySet(), achieved(listOf(session(0, reps("squat", 0, aborted = 5)))))
        assertEquals(setOf("first_workout"), achieved(listOf(session(0, reps("squat", 1)))))
    }

    @Test
    fun a_streak_achievement_is_kept_after_the_streak_ends() {
        val history = (-20..-14).map { session(it, reps("squat", 1)) }
        assertTrue("streak_7" in achieved(history), "seven days in a row, ended a week ago")
        assertFalse("streak_30" in achieved(history))
    }

    @Test
    fun repetition_totals_count_only_counted_repetitions() {
        val ninetyNine = listOf(session(-1, reps("squat", 60, aborted = 40)), session(0, reps("squat", 39)))
        assertFalse("reps_100" in achieved(ninetyNine))
        assertTrue("reps_100" in achieved(ninetyNine + session(0, reps("squat", 1))))
    }

    @Test
    fun an_hour_held_counts_only_completed_holds() {
        val fiftyNineMinutes = listOf(
            session(-1, holds("plank", List(59) { 60_000.0 })),
            session(0, holds("plank", listOf(600_000.0), HoldValidity.BROKEN)),
        )
        assertFalse("hold_hour" in achieved(fiftyNineMinutes))
        assertTrue("hold_hour" in achieved(fiftyNineMinutes + session(0, holds("wall_sit", listOf(60_000.0)))))
    }

    @Test
    fun progress_is_capped_at_the_target_and_says_how_far() {
        val history = listOf(session(0, reps("squat", 150)))
        val reps100 = Achievements.of(history, today).single { it.id == "reps_100" }
        val reps1000 = Achievements.of(history, today).single { it.id == "reps_1000" }
        assertEquals(100.0, reps100.value, "shown as done, not as 150 of 100")
        assertEquals(150.0, reps1000.value)
        assertTrue(reps1000.progressText.contains("150") && reps1000.progressText.contains("1,000"), reps1000.progressText)
    }
}
