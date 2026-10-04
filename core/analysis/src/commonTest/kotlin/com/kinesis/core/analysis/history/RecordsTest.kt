package com.kinesis.core.analysis.history

import com.kinesis.core.analysis.history.HistoryFixtures.holds
import com.kinesis.core.analysis.history.HistoryFixtures.reps
import com.kinesis.core.analysis.history.HistoryFixtures.session
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Personal records - DECISIONS.md P3-49.
 *
 * A record is a set's number that is larger than every earlier one for the same exercise: the
 * most repetitions counted in one set, the longest completed hold, the largest range of motion on
 * each side. Only counted repetitions and completed holds count, as for the streak. Range of motion
 * is per side and never averaged (CLAUDE.md section 3), and the words say "largest", not "best": a
 * larger range is a measurement, not a judgement.
 */
class RecordsTest {

    @Test
    fun a_first_set_of_an_exercise_sets_no_record() {
        assertEquals(emptyList(), Records.newIn(emptyList(), session(0, reps("squat", 10))))
    }

    @Test
    fun more_repetitions_in_a_set_than_ever_before_is_a_record() {
        val before = listOf(session(-2, reps("squat", 8)), session(-1, reps("squat", 12)))
        val records = Records.newIn(before, session(0, reps("squat", 13)))

        assertEquals(1, records.size, records.toString())
        val record = records.single()
        assertEquals(RecordKind.MOST_REPETITIONS_IN_A_SET, record.kind)
        assertEquals(13.0, record.value)
        assertEquals(12.0, record.previous)
        assertEquals(null, record.side)
    }

    @Test
    fun equalling_a_record_is_not_a_new_one() {
        val before = listOf(session(-1, reps("squat", 12)))
        assertEquals(emptyList(), Records.newIn(before, session(0, reps("squat", 12))))
    }

    @Test
    fun only_counted_repetitions_count_toward_a_record() {
        val before = listOf(session(-1, reps("squat", 12)))
        assertEquals(emptyList(), Records.newIn(before, session(0, reps("squat", 10, aborted = 5))))
    }

    @Test
    fun a_longer_completed_hold_is_a_record_and_a_broken_one_is_not() {
        val before = listOf(session(-1, holds("plank", listOf(30_000.0))))

        val longer = Records.newIn(before, session(0, holds("plank", listOf(40_000.0))))
        assertEquals(listOf(RecordKind.LONGEST_HOLD), longer.map { it.kind })
        assertEquals(40_000.0, longer.single().value)
        assertEquals(30_000.0, longer.single().previous)

        val broken = Records.newIn(before, session(0, holds("plank", listOf(60_000.0), HoldValidity.BROKEN)))
        assertEquals(emptyList(), broken, "a hold that did not complete set a record")
    }

    /**
     * Left 110 and right 95 against 100 and 100: a record on the left only. The mean of the new
     * set, 102.5, is above the old mean, 100 - a combined "range of motion" would call that a
     * record for both sides, or for neither side in particular.
     */
    @Test
    fun range_of_motion_records_are_per_side_and_never_averaged() {
        val before = listOf(session(-1, reps("squat", 5, left = 100.0, right = 100.0)))
        val records = Records.newIn(before, session(0, reps("squat", 5, left = 110.0, right = 95.0)))

        assertEquals(1, records.size, records.toString())
        val record = records.single()
        assertEquals(RecordKind.LARGEST_RANGE_OF_MOTION, record.kind)
        assertEquals(Side.LEFT, record.side)
        assertEquals(110.0, record.value)
        assertEquals(100.0, record.previous)
    }

    @Test
    fun another_exercise_s_history_does_not_count() {
        val before = listOf(session(-1, reps("bicep_curl", 20)))
        assertEquals(emptyList(), Records.newIn(before, session(0, reps("squat", 5))))
    }

    /** An alternating curl measures one arm per repetition; the other side is not measured, not zero. */
    @Test
    fun a_side_not_measured_sets_no_record() {
        val before = listOf(session(-1, reps("bicep_curl", 4, left = 100.0, right = 100.0)))
        val records = Records.newIn(
            before,
            session(0, reps("bicep_curl", 3, left = Double.NaN, right = 120.0, side = Side.RIGHT)),
        )
        assertEquals(listOf(Side.RIGHT), records.map { it.side })
    }

    @Test
    fun the_words_name_both_numbers_and_make_no_judgement() {
        val before = listOf(
            session(-1, reps("squat", 8, left = 100.0, right = 100.0), holds("plank", listOf(30_000.0))),
        )
        val records = Records.newIn(
            before,
            session(0, reps("squat", 9, left = 104.0, right = 101.0), holds("plank", listOf(45_000.0))),
        )
        assertEquals(4, records.size, records.toString())
        for (record in records) {
            val text = record.displayText
            for (word in listOf("better", "best", "correct", "improv", "perfect", "good", "bad")) {
                assertTrue(!text.lowercase().contains(word), "'$word' in: $text")
            }
        }
        assertTrue(records.any { it.displayText.contains("9") && it.displayText.contains("8") })
        assertTrue(records.any { it.displayText.contains("45 s") && it.displayText.contains("30 s") })
        assertTrue(records.any { it.displayText.contains("left") && it.displayText.contains("104°") })
    }
}
