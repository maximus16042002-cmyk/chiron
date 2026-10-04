package com.kinesis.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.analysis.history.DayActivity
import com.kinesis.core.analysis.history.WeekProgress
import com.kinesis.core.analysis.workout.PlanRequest
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.OnAccent
import com.kinesis.ui.theme.Raised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * This week's active days as a ring against the goal - DECISIONS.md P3-49. The count is the core's;
 * the ring is its picture, with the numbers beside it in words.
 */
@Composable
private fun GoalRing(progress: WeekProgress) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Raised, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                val fraction = (progress.activeDays.toFloat() / progress.goalDays).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    drawArc(
                        Accent, -90f, 360f * fraction, false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                progress.activeDays.toString() + "/" + progress.goalDays,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                progress.activeDays.toString() + " of " + progress.goalDays + if (progress.goalDays == 1) " day" else " days",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
            )
            val left = progress.goalDays - progress.activeDays
            Text(
                when {
                    progress.met -> "✓ Goal reached"
                    left == 1 -> "1 more day to your goal"
                    else -> "$left more days to your goal"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (progress.met) Accent else InkSecondary,
            )
        }
    }
}

/**
 * Seven days in a row, one mark each: filled where work was completed, a snowflake where a freeze
 * covered the day (P3-49), empty where neither, and today ringed. Shape as well as colour - a filled dot against an outlined one - so the strip
 * reads without colour vision.
 */
@Composable
private fun WeekStrip(week: List<DayActivity>) {
    val today = week.last().day
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (entry in week) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weekdayLetter(entry.day), style = MaterialTheme.typography.labelSmall, color = InkFaint)
                Spacer(Modifier.height(6.dp))
                val mark = Modifier.size(28.dp).clip(CircleShape)
                Box(
                    when {
                        entry.counted -> mark.background(Accent)
                        entry.day == today -> mark.border(2.dp, Ink, CircleShape)
                        entry.frozen -> mark.border(1.dp, Ink, CircleShape)
                        else -> mark.border(1.dp, InkFaint, CircleShape)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        entry.counted -> Text("✓", style = MaterialTheme.typography.labelSmall, color = OnAccent)
                        entry.frozen -> Text("❄", style = MaterialTheme.typography.labelSmall, color = Ink)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val active = week.count { it.counted }
    Note(if (active == 1) "Trained on 1 day of the last 7" else "Trained on $active days of the last 7")
}

/** A day's weekday, as one letter in the phone's language. Day 0 is 1970-01-01, in UTC by definition. */
private fun weekdayLetter(day: LocalDay): String {
    val format = SimpleDateFormat("EEEEE", Locale.getDefault())
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(day.dayNumber * 86_400_000L))
}

/**
 * Home: today at a glance - DECISIONS.md P3-48. The streak and the totals are the core's
 * (`Dashboard.summarise`, P3-10); this page lays them out and offers the way back into training.
 */
@Composable
fun HomeScreen(
    summary: DashboardSummary,
    /** The last seven days, oldest first, ending today - `Streaks.activity`. */
    week: List<DayActivity>,
    /** This week's active days against the goal - `WeeklyGoal.progress`; `null` while loading. */
    weekGoal: WeekProgress?,
    /** The most recent session, or `null` before the first. */
    lastSession: SessionRecord?,
    /** The exercise of the last set recorded, offered as "continue". */
    quickStart: ExerciseProfile?,
    onQuickStart: (ExerciseProfile) -> Unit,
    onBrowse: () -> Unit,
    onOpenSession: (SessionRecord) -> Unit,
    /** The last workout started, to repeat (P3-50); `null` before the first. */
    lastWorkout: PlanRequest?,
    onRepeatWorkout: (PlanRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Chiron", "Your training at a glance") }

        item { StreakHeader(summary.streak) }

        item {
            KCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberBlock(summary.sessionCount.toString(), "sessions", Modifier.weight(1f))
                    NumberBlock(summary.totalValidReps.toString(), "repetitions", Modifier.weight(1f))
                    NumberBlock(((summary.totalHoldMs / 1000).toInt()).toString() + " s", "held", Modifier.weight(1f))
                }
            }
        }

        item {
            KCard {
                if (quickStart != null) {
                    Text("Continue", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                    Text(quickStart.displayName, style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Start " + quickStart.displayName, onClick = { onQuickStart(quickStart) })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Choose another exercise", onClick = onBrowse)
                } else {
                    Text("Start your first set", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Note("Pick an exercise, place the phone so your whole body is in view, and start.")
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Choose an exercise", onClick = onBrowse)
                }
            }
        }

        if (lastWorkout != null) {
            item {
                KCard(onClick = { onRepeatWorkout(lastWorkout) }) {
                    Text("Repeat last workout", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(requestLabel(lastWorkout), style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
        }

        if (lastSession != null) {
            item { SectionLabel("Last session") }
            item { SessionCard(lastSession, onClick = { onOpenSession(lastSession) }) }
        }

        if (weekGoal != null) {
            item { KCard { GoalRing(weekGoal) } }
        }

        if (week.isNotEmpty()) {
            item { SectionLabel("Last 7 days") }
            item { KCard { WeekStrip(week) } }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
