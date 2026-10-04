package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.analysis.history.DayActivity
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.math.roundToInt

/**
 * Progress: the history, and every session in detail - DECISIONS.md P3-48.
 *
 * Every number is a stored measurement or the core's summary of them (`Dashboard.summarise`).
 * Left and right are shown side by side and never combined (CLAUDE.md section 3): a single
 * "range of motion" for two sides would be the one number the project exists not to show.
 */
@Composable
fun ProgressScreen(
    summary: DashboardSummary,
    /** Every stored session, newest first. */
    sessions: List<SessionRecord>,
    /** Every day from the first session's month to today - `Streaks.activity`. */
    calendar: List<DayActivity>,
    today: LocalDay,
    onOpenSession: (SessionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDay: LocalDay? by remember { mutableStateOf(null) }
    val shownSessions = remember(sessions, selectedDay) {
        val day = selectedDay
        if (day == null) sessions else sessions.filter { it.localDay == day }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Progress", "Everything you have recorded") }

        item {
            KCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberBlock(summary.sessionCount.toString(), "sessions", Modifier.weight(1f))
                    NumberBlock(summary.totalValidReps.toString(), "repetitions", Modifier.weight(1f))
                    NumberBlock(summary.streak.qualifyingDays.toString(), "days trained", Modifier.weight(1f))
                }
            }
        }

        item { StreakHeader(summary.streak) }
        item { MonthCalendar(calendar, today, selectedDay, onSelect = { selectedDay = it }) }

        if (summary.perExercise.isNotEmpty()) {
            item { SectionLabel("By exercise") }
            items(summary.perExercise, key = { "exercise-" + it.exerciseId }) { exercise ->
                KCard {
                    Text(nameOf(exercise.exerciseId), style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(
                        if (exercise.totalHoldMs > 0.0 && exercise.validRepCount == 0) {
                            seconds(exercise.totalHoldMs) + " held · " + plural(exercise.setCount, "set")
                        } else {
                            plural(exercise.validRepCount, "repetition") + " · " + plural(exercise.setCount, "set")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                    )
                    if (exercise.validRepCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        SidesLine("Range of motion, mean", exercise.meanRangeOfMotion.left, exercise.meanRangeOfMotion.right)
                        SidesLine("Range of motion, largest", exercise.bestRangeOfMotion.left, exercise.bestRangeOfMotion.right)
                    }
                }
            }
        }

        item {
            val day = selectedDay
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel(if (day == null) "Sessions" else "Sessions on " + dayTitle(day)) }
                if (day != null) FilterChip("Show all", selected = false, onClick = { selectedDay = null })
            }
        }
        if (shownSessions.isEmpty()) {
            item {
                KCard {
                    Note(
                        if (selectedDay == null) "No sessions yet. Your first set will appear here." else "No sessions on this day.",
                    )
                }
            }
        }
        items(shownSessions, key = { "session-" + it.sessionId }) { session ->
            SessionCard(session, onClick = { onOpenSession(session) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** One session in a list: when, what, and how much counted. */
@Composable
fun SessionCard(session: SessionRecord, onClick: () -> Unit) {
    KCard(onClick = onClick) {
        Text(whenOf(session), style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        Text(
            session.sets.map { nameOf(it.exerciseId) }.distinct().joinToString(" · ").ifEmpty { "No sets" },
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Text(
            plural(session.sets.size, "set") + " · " + plural(session.completedUnits, "counted"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
        )
    }
}

/** One session, set by set and repetition by repetition. */
@Composable
fun SessionDetailScreen(
    session: SessionRecord,
    /** The page it was opened from, named on the way back. */
    backLabel: String,
    onBack: () -> Unit,
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
        item { Box(Modifier.padding(top = 8.dp)) { BackLink(backLabel, onBack) } }
        item { PageTitle(whenOf(session), plural(session.sets.size, "set") + " · " + plural(session.completedUnits, "counted")) }
        items(session.sets.withIndex().toList(), key = { "set-" + it.index }) { (index, set) ->
            SetCard(index + 1, set)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SetCard(position: Int, set: SetRecord) {
    KCard {
        Text("Set $position", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        Text(nameOf(set.exerciseId), style = MaterialTheme.typography.titleMedium, color = Ink)
        when (set) {
            is RepetitionSetRecord -> {
                Text(
                    plural(set.completedUnits, "repetition") + " counted of " + plural(set.reps.size, "attempt"),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
                if (set.reps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    RepHeader()
                    for (rep in set.reps) RepLine(rep)
                }
            }
            is HoldSetRecord -> {
                Text(plural(set.completedUnits, "hold") + " counted", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                for (hold in set.holds) HoldLine(hold)
            }
        }
    }
}

@Composable
private fun RepHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Cell("#", 0.6f, InkFaint)
        Cell("", 1.6f, InkFaint)
        Cell("L range", 1.2f, InkFaint)
        Cell("R range", 1.2f, InkFaint)
        Cell("down / up", 1.6f, InkFaint)
    }
}

/** One repetition: whether it counted, each side's range apart, and its two phase times. */
@Composable
private fun RepLine(rep: RepRecord) {
    val counted = rep.validity == RepValidity.VALID
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(rep.repIndex.toString(), 0.6f, InkSecondary)
        Cell(validityLabel(rep.validity), 1.6f, if (counted) Ink else Attention)
        Cell(degrees(rep.rangeOfMotion.left), 1.2f, Ink)
        Cell(degrees(rep.rangeOfMotion.right), 1.2f, Ink)
        Cell(secondsShort(rep.descentMs) + " / " + secondsShort(rep.ascentMs), 1.6f, InkSecondary)
    }
}

@Composable
private fun HoldLine(hold: HoldRecord) {
    val counted = hold.validity == HoldValidity.VALID
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Cell(holdValidityLabel(hold.validity), 1.6f, if (counted) Ink else Attention)
        Cell(seconds(hold.totalHoldMs), 1.2f, Ink)
        Cell(plural(hold.breakCount, "break"), 1.2f, InkSecondary)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(weight))
}

@Composable
private fun SidesLine(label: String, left: Double, right: Double) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.weight(1f))
        Text("L " + degrees(left), style = MaterialTheme.typography.bodySmall, color = Ink)
        Spacer(Modifier.width(12.dp))
        Text("R " + degrees(right), style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

// --- Words for fields, not judgements --------------------------------------------------------

/** A record's validity, named: whether it counted, and if not, why. Shape as well as colour. */
internal fun validityLabel(validity: RepValidity): String = when (validity) {
    RepValidity.VALID -> "✓ counted"
    RepValidity.ABORTED -> "✕ turned back"
    RepValidity.INCOMPLETE -> "✕ unfinished"
    RepValidity.INSUFFICIENT_DATA -> "✕ not seen"
}

/** A hold's validity, named, as a repetition's is. */
internal fun holdValidityLabel(validity: HoldValidity): String = when (validity) {
    HoldValidity.VALID -> "✓ held"
    HoldValidity.BROKEN -> "✕ broken"
    HoldValidity.TOO_SHORT -> "✕ too short"
    HoldValidity.INSUFFICIENT_DATA -> "✕ not seen"
}

/** An exercise's name from the library, or its id if the library no longer has it. */
fun nameOf(exerciseId: String): String =
    ExerciseLibrary.all.firstOrNull { it.id == exerciseId }?.displayName ?: exerciseId

/** When a session started, in the time zone it was recorded in. */
fun whenOf(session: SessionRecord): String {
    val format = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())
    format.timeZone = SimpleTimeZone(session.utcOffsetMinutes * 60_000, "recorded")
    return format.format(Date(session.startedAtEpochMs.toLong()))
}

/** Whole degrees, or a dash for a side not measured - never 0. */
private fun degrees(value: Double): String = if (value.isFinite()) value.roundToInt().toString() + "°" else "—"

private fun seconds(ms: Double): String = if (ms.isFinite()) (ms / 1000.0).roundToInt().toString() + " s" else "—"

private fun secondsShort(ms: Double): String =
    if (ms.isFinite()) String.format(Locale.getDefault(), "%.1f", ms / 1000.0) + "s" else "—"

private fun plural(count: Int, noun: String): String = "$count " + if (count == 1 || noun == "counted") noun else noun + "s"
