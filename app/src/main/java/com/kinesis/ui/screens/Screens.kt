package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ContextUnavailable
import com.kinesis.health.DailyMetric
import com.kinesis.camera.LiveCamera
import com.kinesis.licenses.ThirdPartyNotice
import com.kinesis.licenses.ThirdPartyNotices
import com.kinesis.ui.live.LiveHoldUiState
import com.kinesis.ui.live.LiveUiState
import com.kinesis.ui.live.SkeletonOverlayView
import com.kinesis.ui.theme.Amber
import com.kinesis.ui.theme.PhosphorDim
import com.kinesis.ui.theme.PhosphorFaint
import com.kinesis.ui.theme.PhosphorGreen
import com.kinesis.ui.theme.VoidRaised
import com.kinesis.ui.theme.color

/**
 * The screens - DECISIONS.md P3-12.
 *
 * Phosphor green on near-black, monospace throughout. Every string a user reads
 * about their movement comes from the core: CONTRACTS.md section 2 and
 * FINAL_ARCHITECTURE section 6 both say the UI "renders what it is given and
 * composes no sentences of its own", which is what stops UI copy from making a
 * claim the tier system would have refused. The labels here name fields; they do
 * not judge movement.
 */

// --- Exercise selection -------------------------------------------------------------

/**
 * Every entry in the library, read from [ExerciseLibrary] rather than listed
 * here - all seventeen, including the two variants.
 *
 * The tier and the analysis mode are shown because they are what the entry is
 * entitled to claim, not decoration: a Tier C hold can never produce a technique
 * judgement, and a user who can see that is a user who is not surprised by it.
 */
@Composable
fun ExerciseSelectionScreen(
    onSelect: (ExerciseProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text("KINESIS", style = MaterialTheme.typography.displayLarge)
                Text(
                    ExerciseLibrary.profiles.size.toString() + " profiles · " +
                        ExerciseLibrary.variants.size + " variants · " +
                        ExerciseLibrary.all.size + " entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhosphorFaint,
                )
            }
        }

        items(ExerciseLibrary.all, key = { it.id }) { profile ->
            ExerciseRow(profile, onClick = { onSelect(profile) })
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExerciseRow(profile: ExerciseProfile, onClick: () -> Unit) {
    val isVariant = profile is ExerciseVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isVariant) PhosphorFaint else PhosphorDim)
            .background(VoidRaised)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(profile.requiredView.name.replace('_', ' ').lowercase())
                    append(" · ")
                    append(profile.posture.name.lowercase())
                    if (profile is IsometricExercise) append(" · hold")
                    if (isVariant) append(" · variant of " + (profile as ExerciseVariant).parent.id)
                },
                style = MaterialTheme.typography.bodySmall,
                color = PhosphorFaint,
            )
        }
        Text(
            "TIER " + profile.tier.name,
            style = MaterialTheme.typography.labelSmall,
            color = PhosphorDim,
        )
    }
}

// --- Dashboard ------------------------------------------------------------------------

/**
 * History, read from stored sessions through `Dashboard.summarise`.
 *
 * The aggregation is the core's; this only lays it out. Note what is *not*
 * here: no deviation, because deviation is measured against a
 * `CalibrationBaseline` the app does not store yet (P6's engine side is P3-21), and a dashboard that showed
 * "below your normal" without one would be inventing the comparison.
 */
@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    context: ActivityContextResult?,
    onRequestHealthAccess: (() -> Unit)?,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    summary.streak.currentDays.toString(),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    "DAY STREAK · longest " + summary.streak.longestDays,
                    style = MaterialTheme.typography.bodySmall,
                    color = PhosphorFaint,
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Stat("SESSIONS", summary.sessionCount.toString(), Modifier.weight(1f))
                Stat("REPS", summary.totalValidReps.toString(), Modifier.weight(1f))
                Stat(
                    "HOLD",
                    (summary.totalHoldMs / 1000).toInt().toString() + "s",
                    Modifier.weight(1f),
                )
            }
        }

        if (summary.perExercise.isEmpty()) {
            item {
                Text(
                    "NO SESSIONS RECORDED",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhosphorFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        }

        items(summary.perExercise, key = { it.exerciseId }) { exercise ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, PhosphorDim)
                    .background(VoidRaised)
                    .padding(14.dp),
            ) {
                Text(exercise.exerciseId, style = MaterialTheme.typography.titleMedium)
                Text(
                    exercise.validRepCount.toString() + " reps · " +
                        exercise.setCount + " sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhosphorFaint,
                )
                // Left and right stay separate here too - a single averaged
                // range of motion would be the one number CLAUDE.md section 3
                // exists to prevent.
                Text(
                    "ROM  L " + format(exercise.meanRangeOfMotion.left) +
                        "   R " + format(exercise.meanRangeOfMotion.right),
                    style = MaterialTheme.typography.bodySmall,
                    color = PhosphorDim,
                )
            }
        }

        item { HealthContextPanel(context, onRequestHealthAccess) }
        item {
            Text(
                "[ LICENCES ]",
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorFaint,
                modifier = Modifier
                    .clickable(onClick = onOpenLicences)
                    .padding(vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- Licences --------------------------------------------------------------------------

/**
 * The terms the app's third-party components ship under - DECISIONS.md P3-17.
 *
 * Static text from [ThirdPartyNotices] plus the full Apache License 2.0, read
 * from the APK's assets by the caller. [apacheLicenceText] is `null` only if
 * that asset could not be read, which is said rather than hidden.
 */
@Composable
fun LicencesScreen(apacheLicenceText: String?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "LICENCES",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        item { Text("POSE MODEL", style = MaterialTheme.typography.labelSmall, color = PhosphorFaint) }
        item { NoticeRow(ThirdPartyNotices.poseModel) }
        item { Text("SOFTWARE", style = MaterialTheme.typography.labelSmall, color = PhosphorFaint) }
        items(ThirdPartyNotices.software) { NoticeRow(it) }
        item { Text("APACHE LICENSE, VERSION 2.0", style = MaterialTheme.typography.labelSmall, color = PhosphorFaint) }
        item {
            Text(
                apacheLicenceText ?: "The licence text could not be read from the app's assets.",
                style = MaterialTheme.typography.bodySmall,
                color = if (apacheLicenceText == null) Amber else PhosphorDim,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun NoticeRow(notice: ThirdPartyNotice) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, PhosphorDim)
            .background(VoidRaised)
            .padding(14.dp),
    ) {
        Text(notice.component, style = MaterialTheme.typography.bodyMedium)
        Text(notice.licence, style = MaterialTheme.typography.bodySmall, color = PhosphorGreen)
        if (notice.detail.isNotEmpty()) {
            Text(notice.detail, style = MaterialTheme.typography.bodySmall, color = PhosphorFaint)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .border(1.dp, PhosphorDim)
            .background(VoidRaised)
            .padding(12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = PhosphorFaint)
    }
}

/**
 * Daily context from Health Connect, shown beside Kinesis's own numbers and
 * never mixed into them - P3-3's "displayed alongside ... rather than replacing".
 *
 * Every absence renders as itself. The reasons come from P3-11's
 * `ContextUnavailable`, so this panel and the read path cannot disagree about
 * why there is nothing to show.
 */
@Composable
private fun HealthContextPanel(
    context: ActivityContextResult?,
    onRequestHealthAccess: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, PhosphorFaint)
            .padding(14.dp),
    ) {
        Text("DAILY CONTEXT", style = MaterialTheme.typography.labelSmall, color = PhosphorFaint)
        Spacer(Modifier.height(6.dp))

        when (context) {
            null -> Text("—", style = MaterialTheme.typography.bodySmall, color = PhosphorFaint)

            is ActivityContextResult.Available -> {
                Metric("STEPS", context.context.steps)
                Metric("ACTIVE KCAL", context.context.activeCaloriesKcal)
                Metric("MEAN BPM", context.context.meanHeartRateBpm)
            }

            is ActivityContextResult.Unavailable -> {
                Text(
                    describe(context.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = PhosphorFaint,
                )
                if (context.reason == ContextUnavailable.PERMISSIONS_DENIED &&
                    onRequestHealthAccess != null
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "[ GRANT ACCESS ]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhosphorGreen,
                        modifier = Modifier.clickable(onClick = onRequestHealthAccess),
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, metric: DailyMetric) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PhosphorFaint)
        Text(
            when (metric) {
                is DailyMetric.Measured -> metric.value.toInt().toString()
                DailyMetric.NotPermitted -> "not permitted"
                DailyMetric.NoData -> "no data"
                DailyMetric.ReadFailed -> "could not be read"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (metric is DailyMetric.Measured) PhosphorDim else PhosphorFaint,
        )
    }
}

private fun describe(reason: ContextUnavailable): String = when (reason) {
    ContextUnavailable.PLATFORM_TOO_OLD -> "this device predates Health Connect"
    ContextUnavailable.NOT_INSTALLED -> "Health Connect is not installed"
    ContextUnavailable.PROVIDER_UPDATE_REQUIRED -> "Health Connect needs updating"
    ContextUnavailable.PERMISSIONS_DENIED -> "no permissions granted"
    ContextUnavailable.READ_FAILED -> "could not be read"
}

private fun format(value: Double): String =
    if (value.isNaN()) "—" else value.toInt().toString() + "°"

// --- Live analysis --------------------------------------------------------------------

/**
 * The live screen for a repetition exercise: the camera preview with the
 * skeleton drawn over it, and the readout beneath - DECISIONS.md P3-15.
 *
 * The only thing that recomposes this screen is [state], which
 * `LiveAnalysisBridge` publishes a few times a second at most - CLAUDE.md
 * section 6.
 */
@Composable
fun LiveAnalysisScreen(
    state: LiveUiState,
    preview: PreviewView,
    overlay: SkeletonOverlayView,
    onEndSet: () -> Unit,
    switchCameraLabel: String,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
    diagnostics: String? = null,
) {
    LiveCameraLayout(
        state.exerciseId, preview, overlay, onEndSet, switchCameraLabel, onSwitchCamera, diagnostics, modifier,
    ) {
        Text(
            state.repCount.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = state.overlayState.color,
        )
        Text(
            state.phase.name,
            style = MaterialTheme.typography.titleMedium,
            color = PhosphorDim,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            state.overlayState.meaning.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = state.overlayState.color,
        )
    }
}

/**
 * The live screen for an isometric hold - DECISIONS.md P3-16. Time held, the
 * hold's state and the tracking state; no repetition count, because a hold has
 * none (CLAUDE.md section 11).
 */
@Composable
fun LiveHoldScreen(
    state: LiveHoldUiState,
    preview: PreviewView,
    overlay: SkeletonOverlayView,
    onEndSet: () -> Unit,
    switchCameraLabel: String,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
    diagnostics: String? = null,
) {
    LiveCameraLayout(
        state.exerciseId, preview, overlay, onEndSet, switchCameraLabel, onSwitchCamera, diagnostics, modifier,
    ) {
        Text(
            (state.heldSeconds / 60).toString() + ":" + (state.heldSeconds % 60).toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayLarge,
            color = state.overlayState.color,
        )
        Text(
            state.holdState.name.replace('_', ' '),
            style = MaterialTheme.typography.titleMedium,
            color = PhosphorDim,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            state.overlayState.meaning.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = state.overlayState.color,
        )
    }
}

/**
 * The camera area and the readout beneath it.
 *
 * The preview and the overlay are Android views embedded once, never driven by
 * Compose. They fill the same box, and that box is sized to the analysis
 * frame's exact shape - a portrait 4:3 frame, which `LiveCamera` refuses to run
 * without - with a preview scale type that does not crop. So the preview and
 * the overlay share exact bounds, and the overlay, drawing in frame widths,
 * lands on the body by construction rather than by luck. DECISIONS.md P3-16.
 *
 * The camera switch sits in the camera area's corner. Switching starts a new
 * set - the one in progress is saved as leaving the screen would save it.
 * DECISIONS.md P3-17.
 */
@Composable
private fun LiveCameraLayout(
    exerciseId: String,
    preview: PreviewView,
    overlay: SkeletonOverlayView,
    onEndSet: () -> Unit,
    switchCameraLabel: String,
    onSwitchCamera: () -> Unit,
    /** Debug builds' Phase B readout, about once a second; always `null` in release. */
    diagnostics: String?,
    modifier: Modifier,
    readout: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Clipped: embedded views are not clipped to their own bounds in
            // Compose, and MediaPipe places landmarks outside the frame - on the
            // phone the legs were drawn over the rep counter. DECISIONS.md P3-19.
            Box(Modifier.aspectRatio(LiveCamera.PORTRAIT_FRAME_ASPECT).clipToBounds()) {
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                AndroidView(factory = { overlay }, modifier = Modifier.fillMaxSize())
            }
            Text(
                exerciseId,
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorFaint,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            Text(
                "[ $switchCameraLabel ]",
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorGreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onSwitchCamera)
                    .padding(16.dp),
            )
            if (diagnostics != null) {
                Text(
                    diagnostics,
                    style = MaterialTheme.typography.labelSmall,
                    color = PhosphorFaint,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(VoidRaised)
                        .padding(8.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            readout()
            Spacer(Modifier.height(16.dp))
            Text(
                "[ END SET ]",
                style = MaterialTheme.typography.titleMedium,
                color = PhosphorGreen,
                modifier = Modifier.clickable(onClick = onEndSet),
            )
        }
    }
}

/**
 * The live screen when nothing is being measured, and why.
 *
 * A missing model, a refused permission or a camera that cannot provide the
 * frame the screen needs is shown as that, rather than as a readout of zeroes -
 * a count of 0 would read as "you did nothing" when the truth is "nothing was
 * watching".
 */
@Composable
fun LiveUnavailableScreen(
    exerciseId: String,
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = Amber)
            Spacer(Modifier.height(16.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = PhosphorDim,
                textAlign = TextAlign.Center,
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "[ $action ]",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhosphorGreen,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }

        Text(
            exerciseId,
            style = MaterialTheme.typography.labelSmall,
            color = PhosphorFaint,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}

