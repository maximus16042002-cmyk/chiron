package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.OnAccent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the reminder card shows - DECISIONS.md P3-49. */
data class ReminderState(
    val on: Boolean,
    /** Minutes after midnight. */
    val minuteOfDay: Int,
    /** Days numbered as `java.util.Calendar` numbers them. */
    val weekdays: Set<Int>,
    /** The phone refused notifications when the user last turned reminders on. */
    val refused: Boolean,
)

/**
 * Training reminders, in Profile - DECISIONS.md P3-49: on or off, a time, and the days. A refusal
 * of the notification permission is said once, with where to change it, and not asked again
 * unprompted.
 */
@Composable
fun ReminderCard(
    state: ReminderState,
    onToggle: (Boolean) -> Unit,
    onMinuteOfDay: (Int) -> Unit,
    onWeekdays: (Set<Int>) -> Unit,
) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Training reminders", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(if (state.on) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            Switch(checked = state.on, onCheckedChange = onToggle)
        }
        if (state.on) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeOfDay(state.minuteOfDay), style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.weight(1f))
                Stepper("−", "Half an hour earlier", enabled = true) {
                    onMinuteOfDay((state.minuteOfDay - STEP + MINUTES_PER_DAY) % MINUTES_PER_DAY)
                }
                Spacer(Modifier.width(8.dp))
                Stepper("+", "Half an hour later", enabled = true) {
                    onMinuteOfDay((state.minuteOfDay + STEP) % MINUTES_PER_DAY)
                }
            }
            Spacer(Modifier.height(12.dp))
            val firstWeekday = remember { Calendar.getInstance().firstDayOfWeek }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val weekday = (firstWeekday - 1 + i) % 7 + 1
                    DayToggle(weekday, weekday in state.weekdays) { chosen ->
                        onWeekdays(if (chosen) state.weekdays + weekday else state.weekdays - weekday)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Note(
                if (state.weekdays.isEmpty()) {
                    "Choose at least one day."
                } else {
                    "A notification at this time on the chosen days, unless you have already trained that day."
                },
            )
        }
        if (state.refused) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Notifications are turned off for Kinesis. Allow them in the phone's settings, then turn reminders on here.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Note("Made on this phone. Nothing is sent anywhere.")
    }
}

/** One day, chosen or not: filled with a letter, or outlined. Shape as well as colour. */
@Composable
private fun DayToggle(weekday: Int, chosen: Boolean, onChange: (Boolean) -> Unit) {
    val name = remember(weekday) { weekdayName(weekday, "EEEE") }
    var mark = Modifier.size(36.dp).clip(CircleShape)
    mark = if (chosen) mark.background(Accent) else mark.border(1.dp, InkFaint, CircleShape)
    Box(
        mark
            .clickable { onChange(!chosen) }
            .semantics {
                contentDescription = name
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            weekdayName(weekday, "EEEEE"),
            style = MaterialTheme.typography.labelLarge,
            color = if (chosen) OnAccent else InkSecondary,
        )
    }
}

/** 18:00, or 6:00 PM, as the phone is set. */
@Composable
private fun timeOfDay(minuteOfDay: Int): String {
    val context = LocalContext.current
    val format = android.text.format.DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(minuteOfDay * 60_000L))
}

/** A weekday's name in the phone's language. 1970-01-04, day 3, was a Sunday. */
private fun weekdayName(weekday: Int, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date((3L + weekday - 1) * 86_400_000L))

private const val STEP = 30
private const val MINUTES_PER_DAY = 24 * 60
