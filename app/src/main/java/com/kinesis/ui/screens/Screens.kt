package com.kinesis.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kinesis.camera.LiveCamera
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.setup.SetupVerdict
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ContextUnavailable
import com.kinesis.health.DailyMetric
import com.kinesis.licenses.ThirdPartyNotice
import com.kinesis.licenses.ThirdPartyNotices
import com.kinesis.ui.live.LiveHoldUiState
import com.kinesis.ui.live.LiveUiState
import com.kinesis.ui.live.SkeletonOverlayView
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.Card
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.OnAccent
import com.kinesis.ui.theme.Void
import com.kinesis.ui.theme.color

/**
 * The screens - DECISIONS.md P3-12, in P3-48's look.
 *
 * Every string a user reads
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
    /** Shown under the title: the workout builder and saved workouts (P3-50). */
    header: (@Composable () -> Unit)? = null,
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
        if (header != null) item { header() }
        item { SectionLabel("Exercises") }
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
                        append(viewLabel(profile.requiredView))
                        append(" · ")
                        append(postureLabel(profile.posture))
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
            .background(PageBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Licences", "What the app is built from, and the terms each part ships under") }
        item { SectionLabel("Pose model") }
        item { NoticeRow(ThirdPartyNotices.poseModel) }
        item { SectionLabel("Software") }
        items(ThirdPartyNotices.software) { NoticeRow(it) }
        item { SectionLabel("BSD 3-Clause") }
        items(bsdTexts) { (notice, text) ->
            KCard {
                NoticeText(notice)
                Spacer(Modifier.height(8.dp))
                LicenceText(text)
            }
        }
        item { SectionLabel("Apache License, Version 2.0") }
        item { KCard { LicenceText(apacheLicenceText) } }
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
    KCard { NoticeText(notice) }
}

@Composable
private fun NoticeText(notice: ThirdPartyNotice) {
    Text(notice.component, style = MaterialTheme.typography.titleMedium, color = Ink)
    Text(notice.licence, style = MaterialTheme.typography.bodySmall, color = Ink)
    if (notice.detail.isNotEmpty()) {
        Text(notice.detail, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

/**
 * Daily context from Health Connect, shown beside Kinesis's own numbers and
 * never mixed into them - P3-3's "displayed alongside ... rather than replacing".
 *
 * Every absence renders as itself. The reasons come from P3-11's
 * `ContextUnavailable`, so this panel and the read path cannot disagree about
 * why there is nothing to show. It sits inside the caller's card (Profile, P3-48).
 */
@Composable
internal fun HealthContextPanel(
    context: ActivityContextResult?,
    onRequestHealthAccess: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Today", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(6.dp))

        when (context) {
            null -> Note("Reading…")

            is ActivityContextResult.Available -> {
                Metric("Steps", context.context.steps, "")
                Metric("Active calories", context.context.activeCaloriesKcal, " kcal")
                Metric("Mean heart rate", context.context.meanHeartRateBpm, " bpm")
                Spacer(Modifier.height(6.dp))
                Note("Read from Health Connect and shown beside your training. Kinesis does not use it in any measurement.")
            }

            is ActivityContextResult.Unavailable -> {
                Note(describe(context.reason))
                if (context.reason == ContextUnavailable.PERMISSIONS_DENIED &&
                    onRequestHealthAccess != null
                ) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Allow access", onClick = onRequestHealthAccess)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, metric: DailyMetric, unit: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        Text(
            when (metric) {
                is DailyMetric.Measured -> metric.value.toInt().toString() + unit
                DailyMetric.NotPermitted -> "not permitted"
                DailyMetric.NoData -> "no data"
                DailyMetric.ReadFailed -> "could not be read"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (metric is DailyMetric.Measured) Ink else InkFaint,
        )
    }
}

private fun describe(reason: ContextUnavailable): String = when (reason) {
    ContextUnavailable.PLATFORM_TOO_OLD -> "This phone predates Health Connect."
    ContextUnavailable.NOT_INSTALLED -> "Health Connect is not installed."
    ContextUnavailable.PROVIDER_UPDATE_REQUIRED -> "Health Connect needs updating."
    ContextUnavailable.PERMISSIONS_DENIED -> "Kinesis has not been allowed to read steps, active calories or heart rate."
    ContextUnavailable.READ_FAILED -> "Health Connect could not be read."
}

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
        switchModelLabel, onSwitchModel, modeLabel, state.setupVerdict,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.repCount.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = state.overlayState.color,
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(if (state.repCount == 1) "repetition" else "repetitions", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                Text(fieldName(state.phase.name), style = MaterialTheme.typography.titleMedium, color = Ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        TrackingLine(state.overlayState.meaning, state.overlayState.color)
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
        switchModelLabel, onSwitchModel, modeLabel, state.setupVerdict,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (state.heldSeconds / 60).toString() + ":" + (state.heldSeconds % 60).toString().padStart(2, '0'),
                style = MaterialTheme.typography.displayLarge,
                color = state.overlayState.color,
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text("held", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                Text(fieldName(state.holdState.name), style = MaterialTheme.typography.titleMedium, color = Ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        TrackingLine(state.overlayState.meaning, state.overlayState.color)
    }
}

/** The tracking state in words, in the overlay state's own colour, with a mark so colour is not alone. */
@Composable
private fun TrackingLine(meaning: String, color: androidx.compose.ui.graphics.Color) {
    Text("● " + meaning.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = color)
}

/** An enum value as a field name: `ASCENDING` reads "Ascending". A label, not a judgement. */
private fun fieldName(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

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
    setupVerdict: SetupVerdict = SetupVerdict.NoPerson,
    readout: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Void),
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
            Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(nameOf(exerciseId), Card, Ink)
                if (modeLabel != null) Pill(modeLabel, Accent, OnAccent)
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("⟲ $switchCameraLabel", Card, Ink, onClick = onSwitchCamera)
                if (switchModelLabel != null && onSwitchModel != null) {
                    Pill(switchModelLabel, Card, InkSecondary, onClick = onSwitchModel)
                }
            }
            if (setupVerdict !is SetupVerdict.Ready) {
                Surface(
                    color = Card.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Attention.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.bodySmall)
                        Text(
                            setupVerdict.coachingMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = Attention,
                            fontWeight = FontWeight.Medium,
                        )
                    }
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
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Card)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            readout()
            Spacer(Modifier.height(14.dp))
            PrimaryButton("End set", onClick = onEndSet)
        }
    }
}

/** A small rounded label over the camera, tappable when [onClick] is given. */
@Composable
private fun Pill(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
            .background(PageBackground),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = Attention, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                textAlign = TextAlign.Center,
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                PrimaryButton(action, onClick = onAction)
            }
        }

        Text(
            nameOf(exerciseId),
            style = MaterialTheme.typography.labelLarge,
            color = InkSecondary,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}
