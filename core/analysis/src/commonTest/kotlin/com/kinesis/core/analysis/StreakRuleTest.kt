package com.kinesis.core.analysis

import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Permanent guard - DECISIONS.md P3-10.
 *
 * The streak rule, and the day arithmetic underneath it. Neither
 * `FINAL_ARCHITECTURE.md` nor `ZERO_DATASET_BUILD_PLAN.md` nor `CONTRACTS.md`
 * mentions streaks at all; DECISIONS.md P3-3 added them and deferred the
 * mechanics - "whether a missed day breaks it or a freeze exists" - to the batch
 * that built them. P3-10 is that decision, and this file is what holds it.
 *
 * ### Why this is correctness work and not cosmetics
 *
 * A streak is the one number in the product a user will notice being wrong. It
 * breaks silently: nobody reports "my streak was computed with the wrong
 * timezone offset", they report "it reset for no reason", weeks later, with no
 * way to reconstruct why. The failure modes worth guarding are all arithmetic -
 * a day boundary off by one, a truncation where a floor was needed, a timezone
 * change rewriting history - so they are all testable, and all tested here.
 *
 * ### The day a session belongs to is fixed when the session happens
 *
 * `LocalDay.of` takes the UTC offset **captured at session time**, not the
 * device's current one. Flying from Auckland to Los Angeles must not retroactively
 * move last week's sessions onto different days. FINAL_ARCHITECTURE section 3
 * lists "No date/time, no locale, no file system" among the properties that make
 * the shared core good multiplatform material, so none of this uses a timezone
 * database: it is integer arithmetic over an offset the platform recorded.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class StreakRuleTest {

    private val day0 = LocalDay(20_000)

    // --- Day arithmetic ------------------------------------------------------------

    @Test
    fun a_session_just_before_midnight_and_one_just_after_land_on_different_days() {
        val offset = 60 // UTC+1
        val midnightUtc = 20_000 * LocalDay.MILLIS_PER_DAY - offset * 60_000.0

        val before = LocalDay.of(midnightUtc - 1_000.0, offset)
        val after = LocalDay.of(midnightUtc + 1_000.0, offset)

        assertEquals(
            1,
            before.daysUntil(after),
            "a second either side of local midnight must be two different days, got " +
                before + " and " + after,
        )
    }

    @Test
    fun the_same_instant_falls_on_different_days_in_different_offsets() {
        // Noon UTC is still the same day in London and 01:00 the next day at
        // UTC+13. Ten hours would not have been enough - 10:00 + 13 is 23:00,
        // the same day - which is the kind of arithmetic this test exists for.
        val instant = 20_000 * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0
        assertEquals(LocalDay(20_000), LocalDay.of(instant, utcOffsetMinutes = 0))
        assertEquals(LocalDay(20_001), LocalDay.of(instant, utcOffsetMinutes = 13 * 60))
    }

    /**
     * The day number must come from a floor, not a truncation. They agree for
     * every positive value and disagree for every negative one, so a truncation
     * survives every test written against modern timestamps and then collapses
     * two days into one for anything before 1970 - or, more realistically, for
     * anyone whose device clock is briefly wrong.
     */
    @Test
    fun day_numbers_come_from_a_floor_and_not_a_truncation() {
        assertEquals(LocalDay(-1), LocalDay.of(-1.0, utcOffsetMinutes = 0))
        assertEquals(LocalDay(-1), LocalDay.of(-LocalDay.MILLIS_PER_DAY, utcOffsetMinutes = 0))
        assertEquals(LocalDay(-2), LocalDay.of(-LocalDay.MILLIS_PER_DAY - 1.0, utcOffsetMinutes = 0))
        assertEquals(LocalDay(0), LocalDay.of(0.0, utcOffsetMinutes = 0))
    }

    /**
     * The point of storing the offset on the session rather than reading the
     * device's current one. A session performed at UTC+13 stays on the day it was
     * performed after the user moves to UTC-7; the alternative silently rewrites
     * history every time someone travels.
     */
    @Test
    fun moving_timezone_does_not_move_the_day_a_past_session_belongs_to() {
        val instant = 20_000 * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0
        val performedInAuckland = session(instant, utcOffsetMinutes = 13 * 60, reps = 5)

        assertEquals(LocalDay(20_001), performedInAuckland.localDay)

        // The user is now in Los Angeles. Recomputing with the *current* offset
        // would put that session a day earlier - which is why nothing recomputes.
        assertEquals(LocalDay(20_000), LocalDay.of(instant, utcOffsetMinutes = -7 * 60))
        assertEquals(
            LocalDay(20_001),
            performedInAuckland.localDay,
            "a past session moved days when the device's timezone changed",
        )
    }

    // --- What makes a day count ---------------------------------------------------------

    /**
     * P3-10's rule: a day counts when at least one repetition or hold **actually
     * completed** on it. Opening the app does not count, and neither does a set
     * that was started and abandoned - `repetitionCount` only advances on
     * `VALID`, and the streak agrees with it.
     */
    @Test
    fun a_day_counts_only_when_a_repetition_or_a_hold_actually_completed() {
        val trained = Streaks.summarise(listOf(session(dayAt(0), reps = 1)), today = day0)
        assertEquals(1, trained.currentDays)

        val abandoned = Streaks.summarise(
            listOf(session(dayAt(0), reps = 0, abortedReps = 8)),
            today = day0,
        )
        assertEquals(
            0,
            abandoned.currentDays,
            "eight abandoned repetitions kept a streak alive",
        )
        assertEquals(0, abandoned.qualifyingDays)
    }

    @Test
    fun a_completed_hold_counts_the_day_just_as_a_repetition_does() {
        val held = Streaks.summarise(listOf(session(dayAt(0), holds = 1)), today = day0)
        assertEquals(1, held.currentDays)

        val brokenHold = Streaks.summarise(
            listOf(session(dayAt(0), holds = 0, tooShortHolds = 3)),
            today = day0,
        )
        assertEquals(0, brokenHold.currentDays, "three too-short holds kept a streak alive")
    }

    @Test
    fun two_sessions_on_the_same_day_count_as_one_day() {
        val summary = Streaks.summarise(
            listOf(
                // dayAt() is already noon, so these are 09:00 and 18:00 the
                // same day rather than noon plus nine and eighteen hours.
                session(dayAt(0) - 3 * 3_600_000.0, reps = 5),
                session(dayAt(0) + 6 * 3_600_000.0, reps = 5),
            ),
            today = day0,
        )
        assertEquals(1, summary.currentDays)
        assertEquals(1, summary.qualifyingDays)
    }

    // --- Accumulating and breaking ---------------------------------------------------------

    @Test
    fun consecutive_days_accumulate_and_a_missed_day_breaks_the_streak() {
        val sessions = listOf(-5, -4, -3, -1, 0).map { session(dayAt(it), reps = 3) }
        val summary = Streaks.summarise(sessions, today = day0)

        assertEquals(2, summary.currentDays, "day -2 was missed, so the current run is -1 and 0")
        assertEquals(3, summary.longestDays, "the earlier run of three is the longest")
        assertEquals(5, summary.qualifyingDays)
        assertEquals(day0, summary.lastQualifyingDay)
    }

    /**
     * P3-10: the streak is intact until the day it would break actually ends.
     * Someone who trained yesterday and has not trained yet this morning has not
     * lost anything, and showing them a zero would be both wrong and the single
     * most demotivating thing the feature could do.
     */
    @Test
    fun not_having_trained_yet_today_does_not_break_yesterdays_streak() {
        val sessions = listOf(-3, -2, -1).map { session(dayAt(it), reps = 3) }
        val summary = Streaks.summarise(sessions, today = day0)

        assertEquals(3, summary.currentDays, "a streak ending yesterday is still running today")
        assertEquals(day0.plusDays(-1), summary.lastQualifyingDay)
    }

    /**
     * Two days of silence is a break, though. This is the other side of the rule
     * above, and the pair of them is what "a missed day breaks it" means
     * precisely - P3-3 left exactly this undecided.
     */
    @Test
    fun a_full_day_of_silence_does_break_the_streak() {
        val sessions = listOf(-4, -3, -2).map { session(dayAt(it), reps = 3) }
        val summary = Streaks.summarise(sessions, today = day0)

        assertEquals(0, summary.currentDays, "the streak should have ended a day ago")
        assertEquals(3, summary.longestDays, "but the longest run is still remembered")
    }

    @Test
    fun the_longest_streak_survives_a_later_break() {
        val sessions = (-20..-14).map { session(dayAt(it), reps = 3) } +
            listOf(-1, 0).map { session(dayAt(it), reps = 3) }
        val summary = Streaks.summarise(sessions, today = day0)

        assertEquals(2, summary.currentDays)
        assertEquals(7, summary.longestDays)
    }

    @Test
    fun an_empty_history_reports_no_streak_rather_than_a_zero_that_looks_measured() {
        val summary = Streaks.summarise(emptyList(), today = day0)
        assertEquals(0, summary.currentDays)
        assertEquals(0, summary.longestDays)
        assertEquals(0, summary.qualifyingDays)
        assertNull(summary.lastQualifyingDay, "an empty history has no last day")
    }

    @Test
    fun sessions_in_any_order_produce_the_same_streak() {
        val days = listOf(-4, -2, -1, 0, -3)
        val shuffled = days.map { session(dayAt(it), reps = 2) }
        val ordered = days.sorted().map { session(dayAt(it), reps = 2) }

        assertEquals(
            Streaks.summarise(ordered, today = day0).currentDays,
            Streaks.summarise(shuffled, today = day0).currentDays,
            "the streak depends on the order the sessions were listed in",
        )
        assertTrue(Streaks.summarise(shuffled, today = day0).currentDays == 5)
    }

    // --- A run of days, for the week strip and the calendar - P3-48 -----------------------------

    @Test
    fun a_run_of_days_lists_every_day_in_order_with_its_completed_work() {
        val history = listOf(
            session(dayAt(-6), reps = 3),
            session(dayAt(-2), holds = 1),
            session(dayAt(-2), reps = 2),
            session(dayAt(0), reps = 1),
        )
        val week = Streaks.activity(history, from = day0.plusDays(-6), to = day0)

        assertEquals((-6..0).map { day0.plusDays(it) }, week.map { it.day }, "seven days, oldest first, none skipped")
        assertEquals(listOf(3, 0, 0, 0, 3, 0, 1), week.map { it.completedUnits }, "two sessions on one day add up")
        assertEquals(listOf(true, false, false, false, true, false, true), week.map { it.counted })
    }

    /**
     * The strip and the streak must not tell different stories: a day marked in one is a day
     * counted by the other.
     */
    @Test
    fun a_day_with_only_abandoned_work_is_not_marked_as_the_streak_would_not_count_it() {
        val history = listOf(
            session(dayAt(-1), abortedReps = 4, tooShortHolds = 2),
            session(dayAt(0), reps = 1),
        )
        val week = Streaks.activity(history, from = day0.plusDays(-1), to = day0)

        assertEquals(listOf(false, true), week.map { it.counted })
        assertEquals(
            Streaks.qualifyingDays(history),
            week.filter { it.counted }.map { it.day }.toSet(),
            "the strip marks exactly the streak's qualifying days",
        )
    }

    @Test
    fun a_session_is_marked_on_the_day_of_the_offset_it_was_recorded_in() {
        // 23:30 UTC, recorded at UTC+1: the next local day, as the streak has it.
        val lateUtc = day0.dayNumber * LocalDay.MILLIS_PER_DAY + 23.5 * 3_600_000.0
        val week = Streaks.activity(
            listOf(session(lateUtc, utcOffsetMinutes = 60, reps = 1)),
            from = day0,
            to = day0.plusDays(1),
        )
        assertEquals(listOf(false, true), week.map { it.counted })
    }

    @Test
    fun an_empty_range_is_empty_and_an_empty_history_is_all_unmarked() {
        assertEquals(emptyList(), Streaks.activity(listOf(session(dayAt(0), reps = 1)), from = day0, to = day0.plusDays(-1)))
        assertTrue(Streaks.activity(emptyList(), from = day0.plusDays(-6), to = day0).none { it.counted })
    }

    // --- Building history --------------------------------------------------------------------

    private fun dayAt(offsetDays: Int): Double =
        (day0.dayNumber + offsetDays) * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0

    private fun session(
        epochMs: Double,
        utcOffsetMinutes: Int = 0,
        reps: Int = 0,
        abortedReps: Int = 0,
        holds: Int = 0,
        tooShortHolds: Int = 0,
    ): SessionRecord {
        val sets = ArrayList<SetRecord>()
        if (reps > 0 || abortedReps > 0) {
            sets += RepetitionSetRecord(
                exerciseId = "squat",
                startedAtEpochMs = epochMs,
                reps = List(reps) { rep(it + 1, RepValidity.VALID) } +
                    List(abortedReps) { rep(reps + it + 1, RepValidity.ABORTED) },
            )
        }
        if (holds > 0 || tooShortHolds > 0) {
            sets += HoldSetRecord(
                exerciseId = "plank",
                startedAtEpochMs = epochMs,
                holds = List(holds) { hold(HoldValidity.VALID) } +
                    List(tooShortHolds) { hold(HoldValidity.TOO_SHORT) },
            )
        }
        return SessionRecord(
            sessionId = "s" + epochMs.toLong(),
            startedAtEpochMs = epochMs,
            utcOffsetMinutes = utcOffsetMinutes,
            sets = sets,
        )
    }

    private fun rep(index: Int, validity: RepValidity): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = 0.0,
        endMs = 2_000.0,
        validity = validity,
        rangeOfMotion = BilateralSignal(90.0, 90.0),
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(80.0, 80.0),
        descentMs = 1_000.0,
        ascentMs = 1_000.0,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )

    private fun hold(validity: HoldValidity): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = 30_000.0,
        validity = validity,
        totalHoldMs = 28_000.0,
        longestUnbrokenMs = 28_000.0,
        breakCount = 0,
        meanAbsDeviation = 3.0,
        maxAbsDeviation = 8.0,
        coverage = 1.0,
        meanHeldValue = Double.NaN,
        heldValueSd = Double.NaN,
    )
}
