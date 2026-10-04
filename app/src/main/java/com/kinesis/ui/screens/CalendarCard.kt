package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.DayActivity
import com.kinesis.core.analysis.history.StreakSummary
import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.Raised
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/**
 * The streak, its freezes and the month calendar - DECISIONS.md P3-49.
 *
 * Which day counted and which a freeze covered is the core's (`Streaks.activity`); this file turns
 * day numbers into a month grid and draws them. Month and weekday arithmetic is presentation, done
 * with `GregorianCalendar` in UTC: a `LocalDay` is a count of days since 1970-01-01, so read in UTC
 * it is exactly the calendar date it stands for, with no time zone left to apply.
 */

/** The streak, the longest, and the freezes held. */
@Composable
fun StreakHeader(streak: StreakSummary) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", style = MaterialTheme.typography.displayLarge, color = Accent)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(streak.currentDays.toString(), style = MaterialTheme.typography.displayLarge, color = Ink)
                Text(
                    "day streak · longest " + streak.longestDays,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics {
                    contentDescription = streak.freezesHeld.toString() + if (streak.freezesHeld == 1) " freeze held" else " freezes held"
                },
            ) {
                Text("❄ " + streak.freezesHeld, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(if (streak.freezesHeld == 1) "freeze" else "freezes", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Note(
            "Every " + Streaks.FREEZE_EARNED_EVERY + " days in a row earns a freeze, up to " +
                Streaks.MAX_FREEZES_HELD + ". A freeze covers one missed day; two in a row end the streak.",
        )
    }
}

/**
 * Months from the first one with a session to this one, swiped between; this month first.
 *
 * Fire on a day with completed work, a snowflake on a day a freeze covered, today ringed, future
 * days dimmed. The marks differ in shape, not only colour. Tapping a past day selects it; tapping
 * it again clears the selection.
 */
@Composable
fun MonthCalendar(
    /** Every day from the first month's start to today, from `Streaks.activity`. */
    days: List<DayActivity>,
    today: LocalDay,
    selected: LocalDay?,
    onSelect: (LocalDay?) -> Unit,
) {
    val byDay = remember(days) { days.associateBy { it.day } }
    val months = remember(days, today) {
        val first = monthStart(days.firstOrNull()?.day ?: today)
        val last = monthStart(today)
        generateSequence(first) { nextMonth(it) }.takeWhile { it <= last }.toList()
    }
    val pager = rememberPagerState(initialPage = months.lastIndex) { months.size }
    val scope = rememberCoroutineScope()

    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val page = pager.currentPage
            MonthArrow("‹", "Previous month", enabled = page > 0) { scope.launch { pager.animateScrollToPage(page - 1) } }
            Text(
                monthTitle(months[page]),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow("›", "Next month", enabled = page < months.lastIndex) { scope.launch { pager.animateScrollToPage(page + 1) } }
        }
        Spacer(Modifier.height(8.dp))
        val firstWeekday = remember { Calendar.getInstance().firstDayOfWeek }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    weekdayLetter((firstWeekday - 1 + i) % 7 + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
            MonthGrid(months[page], firstWeekday, byDay, today, selected, onSelect)
        }
    }
}

@Composable
private fun MonthArrow(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        style = MaterialTheme.typography.titleLarge,
        color = if (enabled) Ink else Raised,
        modifier = Modifier
            .clip(CircleShape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = label }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun MonthGrid(
    start: LocalDay,
    firstWeekday: Int,
    byDay: Map<LocalDay, DayActivity>,
    today: LocalDay,
    selected: LocalDay?,
    onSelect: (LocalDay?) -> Unit,
) {
    val length = monthLength(start)
    val leading = (weekdayOf(start) - firstWeekday + 7) % 7
    val rows = (leading + length + 6) / 7
    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val index = row * 7 + column - leading
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (index in 0 until length) {
                            val day = start.plusDays(index)
                            DayCell(day, index + 1, byDay[day], today, day == selected, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDay,
    number: Int,
    activity: DayActivity?,
    today: LocalDay,
    isSelected: Boolean,
    onSelect: (LocalDay?) -> Unit,
) {
    val future = day > today
    val counted = activity?.counted == true
    val frozen = activity?.frozen == true
    var cell = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
    if (isSelected) cell = cell.background(Raised)
    if (day == today) cell = cell.border(2.dp, Ink, CircleShape)
    if (!future) cell = cell.clickable { onSelect(if (isSelected) null else day) }
    cell = cell.semantics {
        contentDescription = number.toString() + ", " + when {
            future -> "not yet"
            counted -> "trained"
            frozen -> "covered by a freeze"
            day == today -> "today, not trained yet"
            else -> "no training"
        }
    }
    Column(cell, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                future -> InkFaint
                counted || frozen -> Ink
                else -> InkSecondary
            },
        )
        when {
            counted -> Text("🔥", style = MaterialTheme.typography.labelSmall)
            frozen -> Text("❄", style = MaterialTheme.typography.labelSmall, color = Ink)
        }
    }
}

// --- Day numbers as calendar dates -------------------------------------------------------------

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private fun calendarOf(day: LocalDay): GregorianCalendar =
    GregorianCalendar(UTC).apply { timeInMillis = day.dayNumber * 86_400_000L }

/** The first day of the month [day] falls in. */
internal fun monthStart(day: LocalDay): LocalDay = day.plusDays(1 - calendarOf(day).get(Calendar.DAY_OF_MONTH))

private fun monthLength(start: LocalDay): Int = calendarOf(start).getActualMaximum(Calendar.DAY_OF_MONTH)

private fun nextMonth(start: LocalDay): LocalDay = start.plusDays(monthLength(start))

/** `Calendar.SUNDAY` (1) to `Calendar.SATURDAY` (7). */
private fun weekdayOf(day: LocalDay): Int = calendarOf(day).get(Calendar.DAY_OF_WEEK)

private fun monthTitle(start: LocalDay): String = utcFormat("LLLL yyyy").format(Date(start.dayNumber * 86_400_000L))

/** A weekday's narrow name in the phone's language. 1970-01-04, day 3, was a Sunday. */
private fun weekdayLetter(weekday: Int): String = utcFormat("EEEEE").format(Date((3L + weekday - 1) * 86_400_000L))

/** A day, written out: "Sat 3 Oct". */
internal fun dayTitle(day: LocalDay): String = utcFormat("EEE d MMM").format(Date(day.dayNumber * 86_400_000L))

private fun utcFormat(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = UTC }
