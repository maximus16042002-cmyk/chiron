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
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.domain.calibration.CalibrationBaseline
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
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.Card
import com.kinesis.ui.theme.color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.statusBarsPadding

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
    /** The user's calibrated exercises' calibration ids. DECISIONS.md P3-22. */
    calibratedIds: Set<String>,
    onSelect: (ExerciseProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    // DECISIONS.md P3-48: one filter, by what the analysis does - counts repetitions or times a hold.
    var filter by remember { mutableStateOf("all") }
    val shown = ExerciseLibrary.all.filter {
        when (filter) {
            "reps" -> it !is IsometricExercise
            "holds" -> it is IsometricExercise
            else -> true
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageTitle(
                "Workouts",
                ExerciseLibrary.profiles.size.toString() + " exercises and " + ExerciseLibrary.variants.size + " variants",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All", filter == "all") { filter = "all" }
                FilterChip("Repetitions", filter == "reps") { filter = "reps" }
                FilterChip("Holds", filter == "holds") { filter = "holds" }
            }
        }
        items(shown, key = { it.id }) { profile ->
            ExerciseRow(profile, CalibrationKey.of(profile) in calibratedIds, onClick = { onSelect(profile) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ExerciseRow(profile: ExerciseProfile, calibrated: Boolean, onClick: () -> Unit) {
    val isVariant = profile is ExerciseVariant
    KCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    buildString {
                        append(profile.requiredView.name.replace('_', ' ').lowercase())
                        append(" · ")
                        append(profile.posture.name.lowercase())
                        append(if (profile is IsometricExercise) " · hold" else " · repetitions")
                        if (isVariant) append(" · a kind of " + (profile as ExerciseVariant).parent.displayName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // The tier is what the entry may claim (CLAUDE.md section 8), so it is shown, not hidden.
                Text("Tier " + profile.tier.name, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                // Whether the user has a baseline for it - a variant's being its parent's (CONTRACTS.md
                // section 3). DECISIONS.md P3-22.
                Text(
                    when {
                        !calibrated -> "not calibrated"
                        profile is ExerciseVariant -> "calibrated via " + profile.parent.displayName
                        else -> "✓ calibrated"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (calibrated) Ink else InkFaint,
                )
            }
        }
    }
}

// --- Licences --------------------------------------------------------------------------

/**
 * The terms the app's third-party components ship under - DECISIONS.md P3-17.
 *
 * Static text from [ThirdPartyNotices] plus the full Apache License 2.0 and the
 * BSD 3-Clause texts (P3-21), read from the APK's assets by the caller. A text
 * is `null` only if its asset could not be read, which is said rather than
 * hidden.
 */
@Composable
fun LicencesScreen(
    apacheLicenceText: String?,
    bsdTexts: List<Pair<ThirdPartyNotice, String?>>,
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
            Text(
                "LICENCES",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        item { Text("POSE MODEL", style = MaterialTheme.typography.labelSmall, color = InkFaint) }
        item { NoticeRow(ThirdPartyNotices.poseModel) }
        item { Text("SOFTWARE", style = MaterialTheme.typography.labelSmall, color = InkFaint) }
        items(ThirdPartyNotices.software) { NoticeRow(it) }
        item { Text("BSD 3-CLAUSE", style = MaterialTheme.typography.labelSmall, color = InkFaint) }
        items(bsdTexts) { (notice, text) ->
            Column {
                NoticeRow(notice)
                Spacer(Modifier.height(6.dp))
                LicenceText(text)
            }
        }
        item { Text("APACHE LICENSE, VERSION 2.0", style = MaterialTheme.typography.labelSmall, color = InkFaint) }
        item { LicenceText(apacheLicenceText) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LicenceText(text: String?) {
    Text(
        text ?: "The licence text could not be read from the app's assets.",
        style = MaterialTheme.typography.bodySmall,
        color = if (text == null) Attention else InkSecondary,
    )
}

@Composable
private fun NoticeRow(notice: ThirdPartyNotice) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, InkSecondary)
            .background(Card)
            .padding(14.dp),
    ) {
        Text(notice.component, style = MaterialTheme.typography.bodyMedium)
        Text(notice.licence, style = MaterialTheme.typography.bodySmall, color = Ink)
        if (notice.detail.isNotEmpty()) {
            Text(notice.detail, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
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
internal fun HealthContextPanel(
    context: ActivityContextResult?,
    onRequestHealthAccess: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, InkFaint)
            .padding(14.dp),
    ) {
        Text("DAILY CONTEXT", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        Spacer(Modifier.height(6.dp))

        when (context) {
            null -> Text("—", style = MaterialTheme.typography.bodySmall, color = InkFaint)

            is ActivityContextResult.Available -> {
                Metric("STEPS", context.context.steps)
                Metric("ACTIVE KCAL", context.context.activeCaloriesKcal)
                Metric("MEAN BPM", context.context.meanHeartRateBpm)
            }

            is ActivityContextResult.Unavailable -> {
                Text(
                    describe(context.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
                if (context.reason == ContextUnavailable.PERMISSIONS_DENIED &&
                    onRequestHealthAccess != null
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "[ GRANT ACCESS ]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        Text(
            when (metric) {
                is DailyMetric.Measured -> metric.value.toInt().toString()
                DailyMetric.NotPermitted -> "not permitted"
                DailyMetric.NoData -> "no data"
                DailyMetric.ReadFailed -> "could not be read"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (metric is DailyMetric.Measured) InkSecondary else InkFaint,
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
    switchModelLabel: String? = null,
    onSwitchModel: (() -> Unit)? = null,
    modeLabel: String? = null,
) {
    LiveCameraLayout(
        state.exerciseId, preview, overlay, onEndSet, switchCameraLabel, onSwitchCamera, diagnostics, modifier,
        switchModelLabel, onSwitchModel, modeLabel,
    ) {
        Text(
            state.repCount.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = state.overlayState.color,
        )
        Text(
            state.phase.name,
            style = MaterialTheme.typography.titleMedium,
            color = InkSecondary,
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
    switchModelLabel: String? = null,
    onSwitchModel: (() -> Unit)? = null,
    modeLabel: String? = null,
) {
    LiveCameraLayout(
        state.exerciseId, preview, overlay, onEndSet, switchCameraLabel, onSwitchCamera, diagnostics, modifier,
        switchModelLabel, onSwitchModel, modeLabel,
    ) {
        Text(
            (state.heldSeconds / 60).toString() + ":" + (state.heldSeconds % 60).toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayLarge,
            color = state.overlayState.color,
        )
        Text(
            state.holdState.name.replace('_', ' '),
            style = MaterialTheme.typography.titleMedium,
            color = InkSecondary,
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
    /** Debug builds' pose model switch (DECISIONS.md P3-21); always `null` in release. */
    switchModelLabel: String?,
    onSwitchModel: (() -> Unit)?,
    /** A calibration set says so on screen (DECISIONS.md P3-22); `null` otherwise. */
    modeLabel: String?,
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
            Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(exerciseId, style = MaterialTheme.typography.labelSmall, color = InkFaint)
                if (modeLabel != null) {
                    Text(modeLabel, style = MaterialTheme.typography.labelSmall, color = Ink)
                }
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                Text(
                    "[ $switchCameraLabel ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink,
                    modifier = Modifier
                        .clickable(onClick = onSwitchCamera)
                        .padding(16.dp),
                )
                if (switchModelLabel != null && onSwitchModel != null) {
                    Text(
                        "[ $switchModelLabel ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                        modifier = Modifier
                            .clickable(onClick = onSwitchModel)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            if (diagnostics != null) {
                Text(
                    diagnostics,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Card)
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
                color = Ink,
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
            Text(headline, style = MaterialTheme.typography.titleMedium, color = Attention)
            Spacer(Modifier.height(16.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                textAlign = TextAlign.Center,
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "[ $action ]",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }

        Text(
            exerciseId,
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}

