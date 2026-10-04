package com.kinesis.reminders

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * When the next training reminder is due - DECISIONS.md P3-49.
 *
 * Wall-clock time in the phone's zone, platform-side: the shared core has no time zones
 * (FINAL_ARCHITECTURE section 3), and a reminder is about the user's evening, not about a count of
 * milliseconds. Each candidate day is reached by adding calendar days and then setting the hour,
 * so a reminder set for 18:00 stays at 18:00 when daylight saving begins or ends.
 */
object ReminderTiming {

    /**
     * The first reminder strictly after [nowMs], on one of [weekdays] (numbered as
     * `java.util.Calendar` numbers them, 1 = Sunday), at [minuteOfDay] past midnight. `null` if
     * no weekday is chosen. Strictly after, so a reminder computed as it fires is not due again
     * at once.
     */
    fun next(nowMs: Long, zone: TimeZone, weekdays: Set<Int>, minuteOfDay: Int): Long? {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "a time of day is 0 to 1439 minutes, got $minuteOfDay" }
        if (weekdays.isEmpty()) return null
        for (daysAhead in 0..7) {
            val candidate = GregorianCalendar(zone).apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_MONTH, daysAhead)
                set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                set(Calendar.MINUTE, minuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.get(Calendar.DAY_OF_WEEK) in weekdays && candidate.timeInMillis > nowMs) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    const val MINUTES_PER_DAY: Int = 24 * 60
}
