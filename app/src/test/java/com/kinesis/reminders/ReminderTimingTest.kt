package com.kinesis.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * When the next reminder is due - DECISIONS.md P3-49. The first chosen weekday at the chosen
 * wall-clock time strictly after now, in the phone's zone, so 18:00 stays 18:00 across a change of
 * daylight saving.
 */
class ReminderTimingTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val london = TimeZone.getTimeZone("Europe/London")
    private val sixPm = 18 * 60

    @Test
    fun later_today_when_today_is_chosen_and_the_time_is_still_ahead() {
        val mondayMorning = at(utc, 2026, Calendar.OCTOBER, 5, 10, 0)
        assertEquals(
            at(utc, 2026, Calendar.OCTOBER, 5, 18, 0),
            ReminderTiming.next(mondayMorning, utc, setOf(Calendar.MONDAY), sixPm),
        )
    }

    @Test
    fun the_next_chosen_day_once_today_s_time_has_passed() {
        val mondayEvening = at(utc, 2026, Calendar.OCTOBER, 5, 19, 0)
        assertEquals(
            "only Mondays: a week on",
            at(utc, 2026, Calendar.OCTOBER, 12, 18, 0),
            ReminderTiming.next(mondayEvening, utc, setOf(Calendar.MONDAY), sixPm),
        )
        assertEquals(
            "Mondays and Tuesdays: tomorrow",
            at(utc, 2026, Calendar.OCTOBER, 6, 18, 0),
            ReminderTiming.next(mondayEvening, utc, setOf(Calendar.MONDAY, Calendar.TUESDAY), sixPm),
        )
    }

    /** Strictly after: a reminder computed at the moment it fires must not fire again at once. */
    @Test
    fun exactly_at_the_time_the_next_one_is_a_later_day() {
        val sixPmMonday = at(utc, 2026, Calendar.OCTOBER, 5, 18, 0)
        assertEquals(
            at(utc, 2026, Calendar.OCTOBER, 6, 18, 0),
            ReminderTiming.next(sixPmMonday, utc, EVERY_DAY, sixPm),
        )
    }

    @Test
    fun six_pm_stays_six_pm_across_the_clocks_going_forward() {
        // British Summer Time began at 01:00 UTC on Sunday 29 March 2026.
        val saturdayEvening = at(london, 2026, Calendar.MARCH, 28, 19, 0)
        val next = ReminderTiming.next(saturdayEvening, london, EVERY_DAY, sixPm)!!
        assertEquals(at(utc, 2026, Calendar.MARCH, 29, 17, 0), next)
    }

    @Test
    fun no_day_chosen_means_no_reminder() {
        assertNull(ReminderTiming.next(at(utc, 2026, Calendar.OCTOBER, 5, 10, 0), utc, emptySet(), sixPm))
    }

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private companion object {
        val EVERY_DAY = (Calendar.SUNDAY..Calendar.SATURDAY).toSet()
    }
}
