package com.kinesis.core.analysis.history

import com.kinesis.core.analysis.history.HistoryFixtures.reps
import com.kinesis.core.analysis.history.HistoryFixtures.session
import com.kinesis.core.analysis.history.HistoryFixtures.today
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a reminder says - DECISIONS.md P3-49. Nothing if work was already completed today; the
 * streak, by its own rule, if one is running; otherwise a plain line. Whether missing today would
 * end the streak is asked of `Streaks` itself, so a reminder never claims a freeze the rule would
 * not spend, or a loss the rule would not impose.
 */
class RemindersTest {

    @Test
    fun no_reminder_once_work_is_done_today() {
        assertNull(Reminders.message(listOf(session(0, reps("squat", 1))), today))
    }

    @Test
    fun abandoned_work_today_does_not_silence_it() {
        assertTrue(Reminders.message(listOf(session(0, reps("squat", 0, aborted = 3))), today) != null)
    }

    @Test
    fun with_no_streak_running_it_is_a_plain_line() {
        assertEquals("Time to train.", Reminders.message(emptyList(), today))
        assertEquals("Time to train.", Reminders.message(listOf(session(-5, reps("squat", 3))), today))
    }

    /** Four days ending yesterday, no freeze held: missing today ends it, and the text says what keeps it. */
    @Test
    fun a_running_streak_with_no_freeze_says_what_keeps_it() {
        val history = (-4..-1).map { session(it, reps("squat", 2)) }
        assertEquals("Train today to keep your 4-day streak.", Reminders.message(history, today))
    }

    /** Seven days ending yesterday earn a freeze, which would cover today. */
    @Test
    fun a_running_streak_a_freeze_would_cover_says_so() {
        val history = (-7..-1).map { session(it, reps("squat", 2)) }
        assertEquals(
            "Your 7-day streak continues if you train today. If you don't, a freeze covers today.",
            Reminders.message(history, today),
        )
    }

    /**
     * Yesterday was frozen, so the streak runs this morning - but a second missed day ends it
     * whatever is held (P3-49 rule 4), so the reminder must not promise a freeze.
     */
    @Test
    fun after_a_frozen_yesterday_no_freeze_is_promised() {
        val history = (-15..-2).map { session(it, reps("squat", 2)) }
        val text = Reminders.message(history, today)!!
        assertEquals("Train today to keep your 14-day streak.", text)
    }

    @Test
    fun no_guilt_words() {
        val histories = listOf(
            emptyList(),
            (-4..-1).map { session(it, reps("squat", 2)) },
            (-7..-1).map { session(it, reps("squat", 2)) },
        )
        for (history in histories) {
            val text = Reminders.message(history, today)!!.lowercase()
            for (word in listOf("lose", "lost", "fail", "miss", "lazy", "shame", "break", "don't let")) {
                assertTrue(!text.contains(word), "'$word' in: $text")
            }
        }
    }
}
