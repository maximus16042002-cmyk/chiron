package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.analysis.AnalysisResult
import com.kinesis.core.domain.analysis.BesideBaseline
import com.kinesis.core.domain.analysis.HoldAnalysisResult
import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Calibration and the set summary - DECISIONS.md P3-22.
 *
 * Every number here is computed in the shared core: the baseline by the
 * calibrator, the comparison by the post-rep analysis. These screens lay the
 * numbers out and label the fields. Sentences about movement come from the core
 * too - the calibration instructions and the reason a set did not calibrate - and
 * there are no judgements at all: "shallower than your usual" and its rules are
 * P8's. Left and right are shown separately everywhere a value has two sides.
 */

// --- Exercise --------------------------------------------------------------------------

/**
 * One library entry: what it is, its baseline if it has one, and the two ways in -
 * an ordinary set, or a calibration set. A variant has no calibration of its own
 * (CONTRACTS.md section 3), so it shows its parent's baseline and offers no
 * CALIBRATE; the parent's screen does that.
 */
@Composable
fun ExerciseScreen(
    profile: ExerciseProfile,
    baseline: CalibrationBaseline?,
    onStartSet: () -> Unit,
    onCalibrate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "tier " + profile.tier.name + " · " + profile.requiredView.name.replace('_', ' ').lowercase() +
                        " · " + profile.posture.name.replace('_', ' ').lowercase() +
                        if (profile is IsometricExercise) " · hold" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
            }
        }
        item { Action("START SET", onStartSet) }
        if (onCalibrate != null) item { Action(if (baseline == null) "CALIBRATE" else "RECALIBRATE", onCalibrate) }
        if (profile is ExerciseVariant) {
            item {
                Text(
                    "Uses the " + profile.parent.displayName + " baseline: a variant shares its parent's " +
                        "calibration. Calibrate from " + profile.parent.displayName + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
            }
        }
        item {
            if (baseline == null) {
                Card {
                    Label("BASELINE")
                    Text("NOT CALIBRATED", style = MaterialTheme.typography.bodyMedium, color = InkFaint)
                }
            } else {
                BaselineCard(if (profile is ExerciseVariant) profile.parent.displayName else profile.displayName, baseline)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- Calibration -----------------------------------------------------------------------

/** What to do, from the core's own words, before the calibration set starts. */
@Composable
fun CalibrationIntroScreen(
    profile: ExerciseProfile,
    instructions: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("CALIBRATE " + profile.displayName.uppercase(), style = MaterialTheme.typography.titleMedium)
        Text(instructions, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        Text(
            "The baseline is kept on this phone only. It is not uploaded with your sessions, and " +
                "calibrating again replaces it.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint,
        )
        Action("START CALIBRATION SET", onStart)
    }
}

/**
 * The result of a calibration set: the new baseline, per side, or the plain
 * reason there is none. [outcome] is `null` while it is being computed and saved.
 */
@Composable
fun CalibrationResultScreen(
    profile: ExerciseProfile,
    outcome: CalibrationOutcome?,
    onDone: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "CALIBRATION · " + profile.displayName.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        when (outcome) {
            null -> item { Text("Building the baseline…", style = MaterialTheme.typography.bodyMedium, color = InkFaint) }

            is CalibrationOutcome.Calibrated -> {
                item { Text(outcome.displayText, style = MaterialTheme.typography.bodyMedium, color = Ink) }
                item {
                    Text(
                        "Saved on this phone. It replaces any earlier " + profile.displayName + " baseline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint,
                    )
                }
                item { BaselineCard(profile.displayName, outcome.baseline) }
                item { Action("DONE", onDone) }
            }

            else -> {
                item { Text(outcome.displayText, style = MaterialTheme.typography.bodyMedium, color = Attention) }
                item {
                    Text(
                        "Nothing was saved, and any earlier baseline is unchanged. The set itself is in your history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint,
                    )
                }
                item { Action("TRY AGAIN", onTryAgain) }
                item { Action("DONE", onDone) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- The baseline card ---------------------------------------------------------------

/** A baseline's values, each side separately. Median, with the IQR as the rep-to-rep spread. */
@Composable
fun BaselineCard(title: String, baseline: CalibrationBaseline, modifier: Modifier = Modifier) {
    Card(modifier) {
        Label("BASELINE · " + title.uppercase() + " · " + day(baseline.createdAtEpochMs))
        when (baseline) {
            is RepetitionBaseline -> {
                Text(
                    "repetitions used: L " + baseline.repetitionsUsedLeft + "   R " + baseline.repetitionsUsedRight,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
                Spacer(Modifier.height(6.dp))
                SidesRow("RANGE", baseline.rangeOfMotion.left, baseline.rangeOfMotion.right, ::degrees)
                SidesRow("TOP", baseline.peakValue.left, baseline.peakValue.right, ::degrees)
                SidesRow("FAR", baseline.troughValue.left, baseline.troughValue.right, ::degrees)
                TimingRow("DOWN", baseline.descentMs)
                TimingRow("UP", baseline.ascentMs)
                SidesRow("CURVE SPREAD", baseline.curveSpread.left, baseline.curveSpread.right, ::degrees)
                Value(
                    "L MINUS R",
                    when (val asymmetry = baseline.asymmetry) {
                        is AsymmetryBaseline.Paired -> signedDegrees(asymmetry.leftMinusRight.median) +
                            spread(asymmetry.leftMinusRight, ::degrees)
                        is AsymmetryBaseline.BetweenSideMedians ->
                            signedDegrees(asymmetry.leftMinusRight) + " (between the two sides' medians)"
                        AsymmetryBaseline.NotMeasurable -> "not measurable: one side seen"
                    },
                )
            }

            is HoldBaseline -> {
                Value("HELD AT", degrees(baseline.meanHeldValue) + " (target " + degrees(baseline.targetValue) + ")")
                Value("OFFSET", signedDegrees(baseline.postureOffset))
                Value("FROM TARGET", degrees(baseline.meanAbsDeviation) + " mean")
                Value("SWAY", degrees(baseline.sway) + " SD")
                Value("HELD FOR", seconds(baseline.heldMs) + ", " + baseline.breakCount + " breaks")
            }
        }
        Text(
            "Provisional: estimator " + baseline.estimatorVersion + ".",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SidesRow(label: String, left: RobustStatistic, right: RobustStatistic, unit: (Double) -> String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        Text(
            "L " + unit(left.median) + spread(left, unit) + "   R " + unit(right.median) + spread(right, unit),
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
        )
    }
}

/** Timing, attributed as the records attribute it: per side, or once for a repetition both sides made. */
@Composable
private fun TimingRow(label: String, timing: PhaseTimingBaseline) {
    if (timing.perRepetition.isMeasured) {
        Value(label, seconds(timing.perRepetition.median) + spread(timing.perRepetition, ::seconds) + " both sides together")
    } else {
        SidesRow(label, timing.perSide.left, timing.perSide.right, ::seconds)
    }
}

// --- Dashboard ------------------------------------------------------------------------

/** Every calibrated exercise's baseline, per side. [baselines] pairs a display name with each. */
@Composable
fun BaselinesPanel(baselines: List<Pair<String, CalibrationBaseline>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("BASELINES")
        if (baselines.isEmpty()) {
            Text(
                "No exercise calibrated yet. Calibrate one from TRAIN.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
            )
        }
        for ((name, baseline) in baselines) BaselineCard(name, baseline)
    }
}

// --- Set summary ----------------------------------------------------------------------

/**
 * A finished set: every record's measurements, and - when the exercise has a
 * baseline - the baseline's values beside them. Numbers only.
 */
@Composable
fun SetSummaryScreen(
    profile: ExerciseProfile,
    session: SessionRecord,
    baselineTitle: String?,
    results: List<AnalysisResult>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = session.sets.single()
    val reps = (set as? RepetitionSetRecord)?.reps.orEmpty().associateBy { it.repIndex }
    val holds = (set as? HoldSetRecord)?.holds.orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp)) {
                Text("SET · " + profile.displayName.uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (set is RepetitionSetRecord) set.completedUnits.toString() + " counted of " + set.reps.size + " records"
                    else set.completedUnits.toString() + " valid hold",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
                Text(
                    if (baselineTitle != null) "BESIDE THE $baselineTitle BASELINE" else "NO BASELINE: CALIBRATE TO SEE ITS VALUES HERE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (baselineTitle != null) Ink else InkFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(results) { result ->
            when (result) {
                is RepetitionAnalysisResult -> RepetitionRow(result, reps[result.repIndex])
                is HoldAnalysisResult -> HoldRow(result, holds.firstOrNull())
            }
        }
        item { Action("DONE", onDone) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RepetitionRow(result: RepetitionAnalysisResult, rep: RepRecord?) {
    Card {
        Label(
            "#" + result.repIndex + "  " + (rep?.validity?.name ?: "") +
                (rep?.attributedSide?.let { "  " + it.name } ?: ""),
            if (rep?.validity == RepValidity.VALID) Ink else Attention,
        )
        val comparison = result.comparison
        if (comparison == null && rep != null) {
            Value("RANGE", "L " + degrees(rep.rangeOfMotion.left) + "   R " + degrees(rep.rangeOfMotion.right))
            Value("DOWN · UP", seconds(rep.descentMs) + " · " + seconds(rep.ascentMs))
        } else if (comparison != null) {
            Beside("RANGE L", comparison.rangeOfMotionLeft, ::degrees)
            Beside("RANGE R", comparison.rangeOfMotionRight, ::degrees)
            val whose = when (comparison.timingSide) {
                Side.LEFT -> " L"
                Side.RIGHT -> " R"
                null -> ""
            }
            Beside("DOWN$whose", comparison.descentMs, ::seconds)
            Beside("UP$whose", comparison.ascentMs, ::seconds)
        }
    }
}

@Composable
private fun HoldRow(result: HoldAnalysisResult, hold: HoldRecord?) {
    Card {
        Label("HOLD  " + (hold?.validity?.name ?: ""))
        val comparison = result.comparison
        if (comparison == null && hold != null) {
            Value("HELD AT", degrees(hold.meanHeldValue))
            Value("SWAY", degrees(hold.heldValueSd))
            Value("HELD FOR", seconds(hold.totalHoldMs))
        } else if (comparison != null) {
            Beside("HELD AT", comparison.meanHeldValue, ::degrees)
            Beside("SWAY", comparison.sway, ::degrees)
            Beside("HELD FOR", comparison.heldMs, ::seconds)
        }
    }
}

/** A measured value, and the baseline's for the same quantity. No difference, no direction. */
@Composable
private fun Beside(label: String, value: BesideBaseline, unit: (Double) -> String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        Row {
            Text(unit(value.measured), style = MaterialTheme.typography.bodySmall, color = Ink)
            Text("   baseline " + unit(value.baseline), style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        }
    }
}

// --- Pieces ---------------------------------------------------------------------------

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().border(1.dp, InkSecondary).background(Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) { content() }
}

@Composable
private fun Label(text: String, color: Color = InkFaint) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun Value(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        Text(value, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Text(
        "[ $label ]",
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

// Formatting only: a value that was not measured shows as a dash, never as zero.

private fun degrees(value: Double): String =
    if (!value.isFinite()) "—" else String.format(Locale.ROOT, "%.1f°", value)

private fun signedDegrees(value: Double): String =
    if (!value.isFinite()) "—" else String.format(Locale.ROOT, "%+.1f°", value)

private fun seconds(ms: Double): String =
    if (!ms.isFinite()) "—" else String.format(Locale.ROOT, "%.2f s", ms / 1000.0)

private fun spread(statistic: RobustStatistic, unit: (Double) -> String): String =
    if (!statistic.isMeasured) "" else " (IQR " + unit(statistic.interquartileRange) + ")"

private fun day(epochMs: Double): String =
    if (!epochMs.isFinite()) "" else SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(epochMs.toLong()))
