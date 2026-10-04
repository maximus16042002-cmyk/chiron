package com.kinesis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.calibration.CalibrationGuide
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.fatigue.FatigueEngine
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
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
import com.kinesis.core.domain.fatigue.FatigueZone
import com.kinesis.core.domain.fatigue.RepetitionFatigue
import com.kinesis.core.domain.fatigue.SetFatigueSummary
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.Raised
import com.kinesis.ui.theme.SeriesLeft
import com.kinesis.ui.theme.SeriesRight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Calibration and the set summary - DECISIONS.md P3-22, in P3-48's look.
 *
 * Every number here is computed in the shared core: the baseline by the
 * calibrator, the comparison by the post-rep analysis. These screens lay the
 * numbers out and label the fields. Sentences about movement come from the core
 * too - the setup and calibration instructions and the reason a set did not
 * calibrate - and there are no judgements at all: "shallower than your usual" and
 * its rules are P8's. Left and right are shown separately everywhere a value has
 * two sides.
 */

// --- Exercise --------------------------------------------------------------------------

/**
 * One library entry: what it is, how to set the phone up for it, its baseline if it has
 * one, and the two ways in - an ordinary set, or a calibration set. A variant has no
 * calibration of its own (CONTRACTS.md section 3), so it shows its parent's baseline and
 * offers no calibration; the parent's page does that.
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
        modifier = modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                profile.displayName,
                "Tier " + profile.tier.name + " · " + viewLabel(profile.requiredView) + " · " +
                    postureLabel(profile.posture) + if (profile is IsometricExercise) " · hold" else " · repetitions",
            )
        }
        if (profile is ExerciseVariant) {
            item {
                KCard {
                    Text("A kind of " + profile.parent.displayName, style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Note(profile.coachingNote)
                    Spacer(Modifier.height(4.dp))
                    Note(
                        "It uses the " + profile.parent.displayName + " calibration: a variant shares its " +
                            "parent's. Calibrate from " + profile.parent.displayName + ".",
                    )
                }
            }
        }
        item {
            KCard {
                Text("Setting up", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Note(CalibrationGuide.placement(profile))
            }
        }
        item { PrimaryButton("Start set", onClick = onStartSet) }
        if (onCalibrate != null) {
            item { SecondaryButton(if (baseline == null) "Calibrate" else "Recalibrate", onClick = onCalibrate) }
        }
        item {
            if (baseline == null) {
                KCard {
                    Text("Not calibrated", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Note(
                        "A calibration set records how you usually move, so each set afterwards is shown " +
                            "beside your own numbers.",
                    )
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
    LazyColumn(
        modifier = modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Calibrate", profile.displayName) }
        item {
            KCard {
                Text(instructions, style = MaterialTheme.typography.bodyLarge, color = Ink)
            }
        }
        item {
            Note(
                "The calibration is kept on this phone only. It is not uploaded with your sessions, and " +
                    "calibrating again replaces it.",
            )
        }
        item { PrimaryButton("Start calibration set", onClick = onStart) }
        item { Spacer(Modifier.height(24.dp)) }
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
        modifier = modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Calibration", profile.displayName) }
        when (outcome) {
            null -> item { KCard { Note("Building the baseline…") } }

            is CalibrationOutcome.Calibrated -> {
                item {
                    KCard {
                        Text("✓ " + outcome.displayText, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Note("Saved on this phone. It replaces any earlier " + profile.displayName + " calibration.")
                    }
                }
                item { BaselineCard(profile.displayName, outcome.baseline) }
                item { PrimaryButton("Done", onClick = onDone) }
            }

            else -> {
                item {
                    KCard {
                        Text(outcome.displayText, style = MaterialTheme.typography.bodyLarge, color = Attention)
                        Spacer(Modifier.height(4.dp))
                        Note("Nothing was saved, and any earlier calibration is unchanged. The set itself is in your history.")
                    }
                }
                item { PrimaryButton("Try again", onClick = onTryAgain) }
                item { SecondaryButton("Done", onClick = onDone) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- The baseline card ---------------------------------------------------------------

/** A baseline's values, each side separately. Median, with the IQR as the rep-to-rep spread. */
@Composable
fun BaselineCard(title: String, baseline: CalibrationBaseline, modifier: Modifier = Modifier) {
    KCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text("Calibrated " + day(baseline.createdAtEpochMs), style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        Spacer(Modifier.height(8.dp))
        when (baseline) {
            is RepetitionBaseline -> {
                SidesHeader()
                Sides("Repetitions used", baseline.repetitionsUsedLeft.toString(), baseline.repetitionsUsedRight.toString())
                SidesRow("Range", baseline.rangeOfMotion.left, baseline.rangeOfMotion.right, ::degrees)
                SidesRow("Top", baseline.peakValue.left, baseline.peakValue.right, ::degrees)
                SidesRow("Far end", baseline.troughValue.left, baseline.troughValue.right, ::degrees)
                TimingRow("Down", baseline.descentMs)
                TimingRow("Up", baseline.ascentMs)
                SidesRow("Curve spread", baseline.curveSpread.left, baseline.curveSpread.right, ::degrees)
                Value(
                    "Left minus right",
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
                Value("Held at", degrees(baseline.meanHeldValue) + " (target " + degrees(baseline.targetValue) + ")")
                Value("Offset", signedDegrees(baseline.postureOffset))
                Value("From target", degrees(baseline.meanAbsDeviation) + " mean")
                Value("Sway", degrees(baseline.sway) + " SD")
                Value("Held for", seconds(baseline.heldMs) + ", " + baseline.breakCount + " breaks")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Median of your repetitions, with the spread between them in brackets. Provisional: estimator " +
                baseline.estimatorVersion + ".",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
        )
    }
}

/** The two columns' heads, so every row below reads left and right apart. */
@Composable
private fun SidesHeader() {
    Sides("", "Left", "Right", InkFaint)
}

@Composable
private fun Sides(label: String, left: String, right: String, color: Color = Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.weight(1.2f))
        Text(left, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(right, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SidesRow(label: String, left: RobustStatistic, right: RobustStatistic, unit: (Double) -> String) {
    Sides(label, unit(left.median) + spread(left, unit), unit(right.median) + spread(right, unit))
}

/** Timing, attributed as the records attribute it: per side, or once for a repetition both sides made. */
@Composable
private fun TimingRow(label: String, timing: PhaseTimingBaseline) {
    if (timing.perRepetition.isMeasured) {
        Value(label, seconds(timing.perRepetition.median) + spread(timing.perRepetition, ::seconds) + ", both sides together")
    } else {
        SidesRow(label, timing.perSide.left, timing.perSide.right, ::seconds)
    }
}

// --- Profile ------------------------------------------------------------------------

/** Every calibrated exercise's baseline, per side. [baselines] pairs a display name with each. */
@Composable
fun BaselinesPanel(baselines: List<Pair<String, CalibrationBaseline>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (baselines.isEmpty()) {
            KCard { Note("No exercise calibrated yet. Calibrate one from an exercise's page in Workouts.") }
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
    /** The records this set set, in the core's words (`Records.newIn`, P3-49); empty if none. */
    records: List<String>,
    /** The rest's length, from Profile (P3-49). */
    restSeconds: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = session.sets.single()
    val reps = (set as? RepetitionSetRecord)?.reps.orEmpty().associateBy { it.repIndex }
    val holds = (set as? HoldSetRecord)?.holds.orEmpty()
    val fatigueSummary = remember(profile, set) {
        if (set is RepetitionSetRecord && profile is RepetitionExercise) {
            FatigueEngine.analyzeSet(profile, set.reps)
        } else null
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(PageBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                profile.displayName,
                if (set is RepetitionSetRecord) {
                    set.completedUnits.toString() + " counted of " + set.reps.size + if (set.reps.size == 1) " attempt" else " attempts"
                } else {
                    set.completedUnits.toString() + if (set.completedUnits == 1) " hold completed" else " holds completed"
                },
            )
        }
        item {
            KCard {
                if (set is RepetitionSetRecord) {
                    NumberBlock(set.completedUnits.toString(), if (set.completedUnits == 1) "repetition" else "repetitions")
                } else {
                    NumberBlock(seconds((set as HoldSetRecord).holds.sumOf { it.totalHoldMs }), "held")
                }
                Spacer(Modifier.height(8.dp))
                Note(
                    if (baselineTitle != null) {
                        "Each record below is shown beside your " + baselineTitle + " calibration."
                    } else {
                        "No calibration yet: calibrate this exercise to see your usual numbers beside these."
                    },
                )
            }
        }
        if (records.isNotEmpty()) {
            item {
                KCard {
                    Text("★ New personal record", style = MaterialTheme.typography.titleMedium, color = Accent)
                    for (record in records) {
                        Spacer(Modifier.height(4.dp))
                        Text(record, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
            }
        }
        if (fatigueSummary != null && fatigueSummary.repetitions.isNotEmpty()) {
            item {
                FatigueCard(fatigueSummary)
            }
        }
        items(results) { result ->
            when (result) {
                is RepetitionAnalysisResult -> {
                    val repFatigue = fatigueSummary?.repetitions?.firstOrNull { it.repIndex == result.repIndex }
                    RepetitionRow(result, reps[result.repIndex], repFatigue)
                }
                is HoldAnalysisResult -> HoldRow(result, holds.firstOrNull())
            }
        }
        item { RestTimerCard(restSeconds) }
        item { PrimaryButton("Done", onClick = onDone) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RepetitionRow(
    result: RepetitionAnalysisResult,
    rep: RepRecord?,
    fatigue: RepetitionFatigue? = null,
) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Repetition " + result.repIndex + (rep?.attributedSide?.let { " · " + sideWord(it) } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            if (rep != null) {
                Text(
                    validityLabel(rep.validity),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rep.validity == RepValidity.VALID) Ink else Attention,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val comparison = result.comparison
        if (comparison == null && rep != null) {
            SidesHeader()
            Sides("Range", degrees(rep.rangeOfMotion.left), degrees(rep.rangeOfMotion.right))
            Value("Down · up", seconds(rep.descentMs) + " · " + seconds(rep.ascentMs))
        } else if (comparison != null) {
            Beside("Range, left", comparison.rangeOfMotionLeft, ::degrees)
            Beside("Range, right", comparison.rangeOfMotionRight, ::degrees)
            val whose = when (comparison.timingSide) {
                Side.LEFT -> ", left"
                Side.RIGHT -> ", right"
                null -> ""
            }
            Beside("Down$whose", comparison.descentMs, ::seconds)
            Beside("Up$whose", comparison.ascentMs, ::seconds)
        }
        if (fatigue != null && (fatigue.concentricVelocity.left.isFinite() || fatigue.concentricVelocity.right.isFinite())) {
            Spacer(Modifier.height(4.dp))
            Sides(
                "Lifting speed",
                formatVelocityWithLoss(fatigue.concentricVelocity.left, fatigue.velocityLossPercent.left),
                formatVelocityWithLoss(fatigue.concentricVelocity.right, fatigue.velocityLossPercent.right),
            )
        }
    }
}

@Composable
private fun HoldRow(result: HoldAnalysisResult, hold: HoldRecord?) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hold", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            if (hold != null) {
                Text(
                    holdValidityLabel(hold.validity),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hold.validity == HoldValidity.VALID) Ink else Attention,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val comparison = result.comparison
        if (comparison == null && hold != null) {
            Value("Held at", degrees(hold.meanHeldValue))
            Value("Sway", degrees(hold.heldValueSd))
            Value("Held for", seconds(hold.totalHoldMs))
        } else if (comparison != null) {
            Beside("Held at", comparison.meanHeldValue, ::degrees)
            Beside("Sway", comparison.sway, ::degrees)
            Beside("Held for", comparison.heldMs, ::seconds)
        }
    }
}

@Composable
private fun FatigueCard(summary: SetFatigueSummary) {
    KCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Velocity & Fatigue", style = MaterialTheme.typography.titleMedium, color = Ink)
            Surface(
                color = when (summary.endingZone) {
                    FatigueZone.FRESH -> SeriesRight.copy(alpha = 0.2f)
                    FatigueZone.OPTIMAL_STIMULUS -> SeriesLeft.copy(alpha = 0.2f)
                    FatigueZone.HIGH_FATIGUE -> Attention.copy(alpha = 0.2f)
                    FatigueZone.EXHAUSTION -> Attention.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    summary.endingZone.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when (summary.endingZone) {
                        FatigueZone.FRESH -> SeriesRight
                        FatigueZone.OPTIMAL_STIMULUS -> SeriesLeft
                        FatigueZone.HIGH_FATIGUE -> Attention
                        FatigueZone.EXHAUSTION -> Attention
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SidesHeader()
        Sides("Peak speed", formatVelocity(summary.setPeakVelocity.left), formatVelocity(summary.setPeakVelocity.right))
        Sides("Final speed", formatVelocity(summary.finalVelocity.left), formatVelocity(summary.finalVelocity.right))
        Sides("Max velocity loss", formatLoss(summary.maxVelocityLossPercent.left), formatLoss(summary.maxVelocityLossPercent.right))

        val cutoffRep = summary.recommendedCutoffRepIndex
        if (cutoffRep != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Raised,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Attention.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚡", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Optimal set cutoff reached at Rep ${cutoffRep + 1} (>30% velocity loss). Further reps increase fatigue without proportional strength gains.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Attention,
                    )
                }
            }
        }

        if (summary.hasFatigueAsymmetry) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Raised,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Attention.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Bilateral fatigue asymmetry detected (≥15% difference in velocity loss between sides). Monitor limb fatigue balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Attention,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Velocity-Based Training (VBT) tracking concentric angular speed loss relative to set peak. Grounded in Pareja-Blanco et al. (2017).",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
        )
    }
}

/** A measured value, and the baseline's for the same quantity. No difference, no direction. */
@Composable
private fun Beside(label: String, value: BesideBaseline, unit: (Double) -> String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary, modifier = Modifier.weight(1f))
        Text(unit(value.measured), style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text("   usual " + unit(value.baseline), style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

// --- Pieces ---------------------------------------------------------------------------

@Composable
private fun Value(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

private fun sideWord(side: Side): String = if (side == Side.LEFT) "left" else "right"

// Formatting only: a value that was not measured shows as a dash, never as zero.

private fun degrees(value: Double): String =
    if (!value.isFinite()) "—" else String.format(Locale.ROOT, "%.1f°", value)

private fun signedDegrees(value: Double): String =
    if (!value.isFinite()) "—" else String.format(Locale.ROOT, "%+.1f°", value)

private fun seconds(ms: Double): String =
    if (!ms.isFinite()) "—" else String.format(Locale.ROOT, "%.2f s", ms / 1000.0)

private fun spread(statistic: RobustStatistic, unit: (Double) -> String): String =
    if (!statistic.isMeasured) "" else " (" + unit(statistic.interquartileRange) + ")"

private fun day(epochMs: Double): String =
    if (!epochMs.isFinite()) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs.toLong()))

private fun formatVelocity(velocity: Double): String =
    if (!velocity.isFinite()) "—" else String.format(Locale.ROOT, "%.0f°/s", velocity)

private fun formatLoss(lossPercent: Double): String =
    if (!lossPercent.isFinite()) "—" else String.format(Locale.ROOT, "%.0f%%", lossPercent)

private fun formatVelocityWithLoss(velocity: Double, lossPercent: Double): String =
    if (!velocity.isFinite()) "—"
    else if (!lossPercent.isFinite() || lossPercent <= 0.0) String.format(Locale.ROOT, "%.0f°/s", velocity)
    else String.format(Locale.ROOT, "%.0f°/s (-%.0f%%)", velocity, lossPercent)
