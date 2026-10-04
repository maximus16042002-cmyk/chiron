package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.calibration.CalibrationGuide
import com.kinesis.core.analysis.workout.Equipment
import com.kinesis.core.analysis.workout.Focus
import com.kinesis.core.analysis.workout.GuidedStep
import com.kinesis.core.analysis.workout.Level
import com.kinesis.core.analysis.workout.PlanItem
import com.kinesis.core.analysis.workout.PlanRequest
import com.kinesis.core.analysis.workout.RoomNeed
import com.kinesis.core.analysis.workout.WorkoutCatalogue
import com.kinesis.core.analysis.workout.WorkoutGenerator
import com.kinesis.core.analysis.workout.WorkoutPlan
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkSecondary
import kotlin.math.roundToInt

/*
 * The workout generator's pages and guided mode - DECISIONS.md P3-50. The plan, its targets and
 * its time are the core's (`WorkoutGenerator`); these pages lay them out.
 */

// --- Build ----------------------------------------------------------------------------------

/** Time, focus, level, equipment: four choices, then the plan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutBuilderScreen(initial: PlanRequest?, onMake: (minutes: Int, focus: Focus, level: Level, equipment: Equipment) -> Unit) {
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 20) }
    var focus by remember { mutableStateOf(initial?.focus ?: Focus.FULL_BODY) }
    var level by remember { mutableStateOf(initial?.level ?: Level.BEGINNER) }
    var equipment by remember { mutableStateOf(initial?.equipment ?: Equipment.NONE) }
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Build a workout", "From the exercises Kinesis can follow") }
        item {
            KCard {
                Choice("Time") {
                    for (m in listOf(10, 20, 30)) FilterChip("$m min", minutes == m) { minutes = m }
                }
                Choice("Focus") {
                    for (f in Focus.entries) FilterChip(focusLabel(f), focus == f) { focus = f }
                }
                Choice("Level") {
                    for (l in Level.entries) FilterChip(levelLabel(l), level == l) { level = l }
                }
                Choice("Equipment") {
                    FilterChip("None", equipment == Equipment.NONE) { equipment = Equipment.NONE }
                    FilterChip("Dumbbells", equipment == Equipment.DUMBBELLS) { equipment = Equipment.DUMBBELLS }
                }
            }
        }
        item { PrimaryButton("Make the plan", onClick = { onMake(minutes, focus, level, equipment) }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(label: String, chips: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = InkSecondary)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    Spacer(Modifier.height(14.dp))
}

// --- The plan --------------------------------------------------------------------------------

/**
 * A plan: its exercises in order, each with its target, rest and what the room needs; shuffle,
 * leave an exercise out, save it, start it.
 */
@Composable
fun PlanScreen(
    plan: WorkoutPlan,
    favourite: Boolean,
    onShuffle: () -> Unit,
    onLeaveOut: (String) -> Unit,
    onFavourite: () -> Unit,
    onStart: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                "About " + plan.estimatedMinutes + " min",
                focusLabel(plan.request.focus) + " · " + levelLabel(plan.request.level) + " · " +
                    if (plan.request.equipment == Equipment.DUMBBELLS) "dumbbells" else "no equipment",
            )
        }
        if (plan.items.isEmpty()) {
            item { KCard { Note("Nothing fits these choices. Try more time, another focus, or fewer exercises left out.") } }
        }
        items(plan.items, key = { it.exerciseId }) { item -> PlanItemCard(item, onLeaveOut) }
        item {
            Note(
                "Targets are suggestions, not prescriptions: go at your own pace. The time is an estimate " +
                    "(" + WorkoutGenerator.SECONDS_PER_REPETITION + " s a repetition, with setting up and rest).",
            )
        }
        if (plan.items.isNotEmpty()) item { PrimaryButton("Start workout", onClick = onStart) }
        item {
            Row {
                SecondaryButton("Shuffle", onClick = onShuffle, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SecondaryButton(if (favourite) "★ Saved" else "☆ Save", onClick = onFavourite, modifier = Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlanItemCard(item: PlanItem, onLeaveOut: (String) -> Unit) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nameOf(item.exerciseId), style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(targetText(item), style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(
                    viewLabel(WorkoutCatalogue.viewOf(item.exerciseId)) + " · " + item.restSeconds + " s rest" +
                        when (item.needs) {
                            RoomNeed.CHAIR -> " · needs a chair"
                            RoomNeed.WALL -> " · needs a wall"
                            null -> ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = InkSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onLeaveOut(item.exerciseId) }
                    .semantics { contentDescription = "Leave out " + nameOf(item.exerciseId) }
                    .padding(12.dp),
            )
        }
    }
}

/** "3 sets × 10 each side", "2 sets × 20 s hold". */
fun targetText(item: PlanItem): String {
    val sets = item.sets.toString() + if (item.sets == 1) " set × " else " sets × "
    val hold = item.targetHoldSeconds
    return if (hold != null) sets + hold + " s hold" else sets + item.targetReps + if (item.perSide) " each side" else ""
}

// --- Guided ----------------------------------------------------------------------------------

/** Before a set: what comes next, its target, and how to set up for it. */
@Composable
fun GuidedPrepareScreen(step: GuidedStep, stepNumber: Int, totalSteps: Int, onStart: () -> Unit) {
    val profile = WorkoutCatalogue.profileOf(step.item.exerciseId)
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle(profile.displayName, "Set " + step.setNumber + " of " + step.item.sets + " · step " + stepNumber + " of " + totalSteps) }
        item {
            KCard {
                Text("Target", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                Text(stepTarget(step), style = MaterialTheme.typography.headlineMedium, color = Ink)
                Note("A suggestion. Kinesis counts what you do.")
            }
        }
        item {
            KCard {
                Text("Setting up", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Note(CalibrationGuide.placement(profile))
            }
        }
        item { PrimaryButton("Start set", onClick = onStart) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** "10 each side", "30 s hold". */
fun stepTarget(step: GuidedStep): String {
    val hold = step.item.targetHoldSeconds
    return if (hold != null) "$hold s hold" else step.item.targetReps.toString() + if (step.item.perSide) " each side" else ""
}

/** Between sets: the rest, started for the user, and what comes next. */
@Composable
fun GuidedRestScreen(next: GuidedStep, restSeconds: Int, sameExercise: Boolean, onNext: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Rest", "Set saved") }
        item { RestTimerCard(restSeconds, autoStart = true) }
        item {
            KCard {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                Text(nameOf(next.item.exerciseId), style = MaterialTheme.typography.titleLarge, color = Ink)
                Text("Set " + next.setNumber + " of " + next.item.sets + " · " + stepTarget(next), style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
            }
        }
        item { PrimaryButton(if (sameExercise) "Start set " + next.setNumber else "Set up for " + nameOf(next.item.exerciseId), onClick = onNext) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The end of a workout: the core's totals over its sets, and any records it set. */
@Composable
fun GuidedSummaryScreen(sets: Int, repetitions: Int, heldMs: Double, durationMs: Long, records: List<String>, onDone: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Workout done", "Every set is saved in Progress") }
        item {
            KCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberBlock((durationMs / 60_000.0).roundToInt().toString() + " min", "time", Modifier.weight(1f))
                    NumberBlock(sets.toString(), if (sets == 1) "set" else "sets", Modifier.weight(1f))
                    NumberBlock(repetitions.toString(), "repetitions", Modifier.weight(1f))
                }
                if (heldMs > 0.0) {
                    Spacer(Modifier.height(8.dp))
                    NumberBlock((heldMs / 1000.0).roundToInt().toString() + " s", "held")
                }
            }
        }
        if (records.isNotEmpty()) {
            item {
                KCard {
                    Text("★ New personal records", style = MaterialTheme.typography.titleMedium, color = Accent)
                    for (record in records) {
                        Spacer(Modifier.height(4.dp))
                        Text(record, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
            }
        }
        item { PrimaryButton("Done", onClick = onDone) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- On other pages ---------------------------------------------------------------------------

/** The way into the generator, and the saved workouts, at the top of Workouts. */
@Composable
fun WorkoutPlansCard(favourites: List<PlanRequest>, onBuild: () -> Unit, onOpen: (PlanRequest) -> Unit) {
    KCard {
        Text("Build a workout", style = MaterialTheme.typography.titleMedium, color = Ink)
        Note("Choose the time, focus, level and equipment; Kinesis lays out the exercises.")
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Build a workout", onClick = onBuild)
        if (favourites.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Saved", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
            for (request in favourites) {
                Text(
                    "★ " + requestLabel(request),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.clickable { onOpen(request) }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** "20 min · full body · beginner". */
fun requestLabel(request: PlanRequest): String =
    request.minutes.toString() + " min · " + focusLabel(request.focus).lowercase() + " · " + levelLabel(request.level).lowercase() +
        if (request.equipment == Equipment.DUMBBELLS) " · dumbbells" else ""

private fun focusLabel(focus: Focus): String = when (focus) {
    Focus.FULL_BODY -> "Full body"
    Focus.UPPER -> "Upper body"
    Focus.LOWER -> "Lower body"
    Focus.CORE -> "Core"
}

private fun levelLabel(level: Level): String = when (level) {
    Level.BEGINNER -> "Beginner"
    Level.INTERMEDIATE -> "Intermediate"
    Level.ADVANCED -> "Advanced"
}
