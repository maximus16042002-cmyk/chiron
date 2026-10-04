package com.kinesis.ui.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kinesis.camera.LiveCamera
import com.kinesis.camera.LiveOrientation
import com.kinesis.camera.PoseLandmarkerSource
import com.kinesis.camera.PoseModelAsset
import com.kinesis.camera.PoseModelState
import com.kinesis.camera.PoseSourceOpening
import com.kinesis.camera.hasCameraPermission
import com.kinesis.camera.rememberCameraPermissionLauncher
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.diagnostics.phaseBProbeOrNull
import com.kinesis.diagnostics.switchablePoseModels
import com.kinesis.pose.CameraFacing
import com.kinesis.settings.AppSettings
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.SetDrafts
import com.kinesis.storage.utcOffsetMinutesAt
import com.kinesis.ui.screens.LiveAnalysisScreen
import com.kinesis.ui.screens.LiveHoldScreen
import com.kinesis.ui.screens.LiveUnavailableScreen
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Where finished sets are written. Process-lifetime on purpose: leaving the
 * live screen ends the set, and the composition that ended it is gone before the
 * write completes, so the write cannot belong to it. DECISIONS.md P3-16.
 */
private val setWrites = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * The signed-in account's draft of a set in progress - DECISIONS.md P3-66. Provided where the
 * account's storage is opened; `null` (no draft kept) anywhere it is not.
 */
val LocalSetDrafts = staticCompositionLocalOf<SetDrafts?> { null }

/**
 * The live destination - DECISIONS.md P3-15 and P3-16.
 *
 * Preconditions are checked in the order a failure should be reported, and each
 * unmet one is shown as itself rather than as a readout of zeroes. The screen is
 * held in portrait while it is shown - either way up, following the phone
 * (P3-19) - because the camera area, the frame shape and the overlay's geometry
 * are all built for portrait and landscape has not been handled deliberately.
 */
@Composable
fun LiveDestination(
    profile: ExerciseProfile,
    repository: SessionRepository,
    onSessionSaved: () -> Unit,
    /**
     * END SET has saved the set: the session written, or `null` if nobody was
     * ever seen. Leaving the screen any other way saves the set without calling
     * this. DECISIONS.md P3-22.
     */
    onSetEnded: (SessionRecord?) -> Unit,
    /** Shown under the exercise id, e.g. `CALIBRATION SET`; `null` for an ordinary set. */
    modeLabel: String? = null,
) {
    LockToPortrait()

    if (profile !is RepetitionExercise && profile !is IsometricExercise) {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "No live analyser",
            detail = profile.displayName + " is neither a repetition nor a hold profile, so " +
                "nothing here can analyse it. Nothing is being measured.",
        )
        return
    }

    val context = LocalContext.current
    when (val model = remember { PoseModelAsset.locate(context) }) {
        PoseModelState.Absent -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "No pose model",
            detail = "No pose_landmarker .task file is in the app's assets, so pose estimation " +
                "cannot run and nothing is being measured. The rest of Chiron works without it.",
        )

        is PoseModelState.Unreadable -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "Pose model unreadable",
            detail = model.reason + ". Nothing is being measured.",
        )

        is PoseModelState.Present -> {
            // Debug builds: lite and, if this build carries it, full. Release
            // builds: nothing to switch to. DECISIONS.md P3-21.
            val switchable = remember {
                try {
                    switchablePoseModels(PoseModelAsset.presentAssets(context))
                } catch (error: IOException) {
                    emptyList()
                }
            }
            LiveCameraGate(
                profile,
                LiveEnvironment(model.assetPath, switchable, repository, onSessionSaved, onSetEnded, modeLabel),
            )
        }
    }
}

private class LiveEnvironment(
    /** The model a set starts on: lite, in every build. */
    val modelAssetPath: String,
    /** The models the debug-only switch cycles through; empty in release. */
    val switchableModels: List<String>,
    val repository: SessionRepository,
    val onSessionSaved: () -> Unit,
    val onSetEnded: (SessionRecord?) -> Unit,
    val modeLabel: String?,
)

/**
 * What the running pipeline is keyed on. Changing either is a new set: the camera
 * for the reasons [LiveCameraSession] gives, and the model because a set measured
 * partly by one model and partly by another is not one measurement.
 */
private data class LiveSource(val facing: CameraFacing, val modelAssetPath: String)

/** The controls in the camera area's corner. The model switch exists in debug builds only. */
private class LiveSwitches(
    val cameraLabel: String,
    val onSwitchCamera: () -> Unit,
    val modelLabel: String?,
    val onSwitchModel: (() -> Unit)?,
)

/** Asks once when the screen opens; after that, only when the user taps. */
@Composable
private fun LiveCameraGate(profile: ExerciseProfile, env: LiveEnvironment) {
    val context = LocalContext.current
    // Front by default, so the user can see themselves; the back camera is one
    // tap away. DECISIONS.md P3-17.
    var facing by remember { mutableStateOf(CameraFacing.FRONT) }
    val switchCamera = {
        facing = if (facing == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT
    }
    var permitted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    val request = rememberCameraPermissionLauncher { granted ->
        permitted = granted
        refused = !granted
    }
    LaunchedEffect(Unit) { if (!permitted) request() }

    // The debug-only model switch - DECISIONS.md P3-21. Offered only when there
    // is another model to switch to, so never in a release build.
    var modelAssetPath by remember { mutableStateOf(env.modelAssetPath) }
    val models = env.switchableModels
    val switchModel: (() -> Unit)? = if (models.size < 2) {
        null
    } else {
        { modelAssetPath = models[(models.indexOf(modelAssetPath) + 1) % models.size] }
    }
    val source = LiveSource(facing, modelAssetPath)
    val switches = LiveSwitches(
        cameraLabel = switchCameraLabel(facing),
        onSwitchCamera = switchCamera,
        modelLabel = switchModel?.let { switchModelLabel(models[(models.indexOf(modelAssetPath) + 1) % models.size]) },
        onSwitchModel = switchModel,
    )

    if (!permitted) {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = if (refused) "Camera permission refused" else "Camera permission needed",
            detail = "Live analysis needs the camera. Frames are analysed on this phone; none is " +
                "stored or uploaded." +
                if (refused) " If the system no longer asks, allow it in Settings > Apps > Chiron." else "",
            action = "Allow camera",
            onAction = request,
        )
        return
    }

    when (profile) {
        is RepetitionExercise -> LiveRepetitionSession(profile, env, source, switches)
        is IsometricExercise -> LiveHoldSession(profile, env, source, switches)
    }
}

/*
 * Both sessions key their engine on the source - the camera and the model - as
 * well as the profile, so switching either is a new set. See LiveCameraSession.
 */

@Composable
private fun LiveRepetitionSession(
    profile: RepetitionExercise,
    env: LiveEnvironment,
    source: LiveSource,
    switches: LiveSwitches,
) {
    val overlay = rememberOverlay()
    val main = rememberMainExecutor()
    // The bridge publishes on MediaPipe's thread; Compose state is set on main.
    val published = remember(profile, source) { mutableStateOf<LiveUiState?>(null) }
    val bridge = remember(profile, source) {
        LiveAnalysisBridge(profile.id) { state -> main.execute { published.value = state } }
    }
    val diagnostics = remember(profile, source) { mutableStateOf<String?>(null) }
    // Debug and profiling builds' readout; null in release. Handed to the engine
    // for per-result numbers and to the camera for capture timings. P3-19.
    val probe = remember(profile, source) {
        phaseBProbeOrNull(profile.requiredLandmarks, source.facing, source.modelAssetPath) { line ->
            main.execute { diagnostics.value = line }
        }
    }
    val engine = remember(profile, source) { LiveRepetitionEngine(profile, bridge, overlay, probe = probe) }

    // DECISIONS.md P3-49: a tone, a vibration or the count spoken, as set in Profile, once per
    // counted repetition - keyed on the count, never on a frame (CLAUDE.md section 6).
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember(profile, source) {
        val settings = AppSettings(context)
        RepFeedback(context, settings.repTone, settings.repVibrate, settings.spokenCounts)
    }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    val watcher = remember(profile, source) { RepCountWatcher() }
    // Read in a snapshot flow, not in composition: reading the count here would recompose the
    // whole session at the publication rate, where now only the readout does.
    LaunchedEffect(feedback, watcher) {
        snapshotFlow { (published.value ?: bridge.state).repCount }.collect { count ->
            if (watcher.counted(count)) feedback.onRepetition(count, view)
        }
    }

    LiveCameraSession(profile, engine, probe, overlay, env, source, switches.onSwitchCamera) { preview, onEndSet ->
        LiveAnalysisScreen(
            published.value ?: bridge.state, preview, overlay, onEndSet, switches.cameraLabel, switches.onSwitchCamera,
            diagnostics = diagnostics.value,
            switchModelLabel = switches.modelLabel,
            onSwitchModel = switches.onSwitchModel,
            modeLabel = env.modeLabel,
        )
    }
}

/** Plank, Wall Sit, Side Plank - through HoldAnalyser, never the repetition tracker. */
@Composable
private fun LiveHoldSession(
    profile: IsometricExercise,
    env: LiveEnvironment,
    source: LiveSource,
    switches: LiveSwitches,
) {
    val overlay = rememberOverlay()
    val main = rememberMainExecutor()
    val published = remember(profile, source) { mutableStateOf<LiveHoldUiState?>(null) }
    val bridge = remember(profile, source) {
        LiveHoldBridge(profile.id) { state -> main.execute { published.value = state } }
    }
    val diagnostics = remember(profile, source) { mutableStateOf<String?>(null) }
    // Debug and profiling builds' readout; null in release. Handed to the engine
    // for per-result numbers and to the camera for capture timings. P3-19.
    val probe = remember(profile, source) {
        phaseBProbeOrNull(profile.requiredLandmarks, source.facing, source.modelAssetPath) { line ->
            main.execute { diagnostics.value = line }
        }
    }
    val engine = remember(profile, source) { LiveHoldEngine(profile, bridge, overlay, probe = probe) }

    LiveCameraSession(profile, engine, probe, overlay, env, source, switches.onSwitchCamera) { preview, onEndSet ->
        LiveHoldScreen(
            published.value ?: bridge.state, preview, overlay, onEndSet, switches.cameraLabel, switches.onSwitchCamera,
            diagnostics = diagnostics.value,
            switchModelLabel = switches.modelLabel,
            onSwitchModel = switches.onSwitchModel,
            modeLabel = env.modeLabel,
        )
    }
}

/** The action, not the state: what tapping it switches to. */
private fun switchCameraLabel(facing: CameraFacing): String = when (facing) {
    CameraFacing.FRONT -> "Back camera"
    CameraFacing.BACK -> "Front camera"
}

/** Likewise the action: the model tapping it switches to. */
private fun switchModelLabel(next: String): String =
    "USE " + next.removePrefix("pose_landmarker_").removeSuffix(".task").uppercase() + " MODEL"

private class LiveFailure(val headline: String, val detail: String, val offersCameraSwitch: Boolean)

/**
 * The running pipeline: camera -> MediaPipe -> PoseAdapter -> shared-core
 * analysis -> bridge and overlay, whichever mode [engine] is.
 *
 * A set ends by the end-set control or by leaving the screen, whichever comes
 * first; both go through the same [LiveSetSaver], so a set is written once.
 *
 * ### Switching camera is a new set - DECISIONS.md P3-17
 *
 * [engine] is keyed on [source], so switching replaces it: the set in progress
 * ends exactly as it does on leaving the screen - saved if anyone was seen, an
 * unfinished repetition recorded as incomplete - and a new one starts. Two
 * reasons. A set seen from two viewpoints with a gap between is not one
 * measurement. And Camera2 does not promise that two camera devices' frame
 * timestamps share a clock (where it reports the timestamp source as unknown,
 * they cannot be compared), so one set's timing cannot safely span both.
 *
 * Switching the pose model, which only debug builds offer, is a new set for the
 * same first reason: one set measured by two models is not one measurement.
 * DECISIONS.md P3-21.
 */
@Composable
private fun LiveCameraSession(
    profile: ExerciseProfile,
    engine: LiveEngine,
    probe: PhaseBProbe?,
    overlay: SkeletonOverlayView,
    env: LiveEnvironment,
    source: LiveSource,
    onSwitchCamera: () -> Unit,
    screen: @Composable (preview: PreviewView, onEndSet: () -> Unit) -> Unit,
) {
    val facing = source.facing
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val main = rememberMainExecutor()
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    val preview = remember {
        PreviewView(context).apply {
            // A TextureView, so the overlay above it composites like any other
            // view instead of depending on SurfaceView z-ordering inside Compose.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Never crops: with the camera area sized to the frame's shape, the
            // preview fills it exactly and shares its bounds with the overlay.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val drafts = LocalSetDrafts.current
    val saver = remember(engine) {
        val now = System.currentTimeMillis()
        LiveSetSaver(
            engine = engine,
            repository = env.repository,
            scope = setWrites,
            sessionId = UUID.randomUUID().toString(),
            startedAtEpochMs = now.toDouble(),
            utcOffsetMinutes = utcOffsetMinutesAt(now),
            onSaved = env.onSessionSaved,
            // The screen may be gone by now, so the toast, which outlives it (P3-63).
            drafts = drafts,
            onUnreportedFailure = { reason ->
                Toast.makeText(context.applicationContext, "Set not saved: $reason", Toast.LENGTH_LONG).show()
            },
        )
    }
    var ending by remember(engine) { mutableStateOf(false) }
    var failure: LiveFailure? by remember(engine) { mutableStateOf(null) }

    // Leaving the screen - or switching camera, which replaces the engine - ends
    // the set too. Returns the same write if the end-set control already started it.
    DisposableEffect(saver) { onDispose { saver.endSet() } }

    if (ending) {
        LaunchedEffect(saver) {
            when (val outcome = saver.endSet(watching = true).await()) {
                is SetSaveOutcome.Saved -> env.onSetEnded(outcome.session)
                SetSaveOutcome.NothingToSave -> env.onSetEnded(null)
                is SetSaveOutcome.Failed -> failure = LiveFailure("Set not saved", outcome.reason, false)
            }
        }
    }

    val shown = failure
    when {
        // A camera that cannot be used offers the other one; nothing else does.
        shown != null -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = shown.headline,
            detail = shown.detail,
            action = if (shown.offersCameraSwitch) switchCameraLabel(facing) else null,
            onAction = if (shown.offersCameraSwitch) onSwitchCamera else null,
        )

        ending -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "Saving the set",
            detail = "Writing this set to the phone. If you are signed in, sync follows later.",
        )

        !portrait -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "Turning to portrait",
            detail = "Live analysis runs in portrait only.",
        )

        else -> {
            DisposableEffect(engine, facing, source.modelAssetPath, lifecycleOwner) {
                // The skeleton follows the preview: mirrored for the front camera,
                // as-is for the back. The last camera's figure is cleared.
                overlay.displayMirrored = LiveCamera.previewIsMirrored(facing)
                overlay.clear()
                // The facing is given once, here; LiveCamera reads it back from the
                // source, so the camera, the mirroring and the adapter agree.
                // Nothing below may crash the app (P3-18): creation failures come
                // back as a value, and analysis failures - on MediaPipe's or
                // CameraX's thread - stop analysis and arrive here, once.
                val opening = PoseLandmarkerSource.open(
                    context, source.modelAssetPath, facing, engine::onLandmarks, engine::onNoPose,
                    probe = probe,
                    onFailure = { reason ->
                        main.execute {
                            failure = LiveFailure(
                                "Live analysis stopped", reason + ". Nothing is being measured.", offersCameraSwitch = false,
                            )
                        }
                    },
                )
                val camera = when (opening) {
                    is PoseSourceOpening.Failed -> {
                        failure = LiveFailure(
                            "Live analysis unavailable", opening.reason + ". Nothing is being measured.",
                            offersCameraSwitch = false,
                        )
                        null
                    }

                    is PoseSourceOpening.Opened -> LiveCamera(context, opening.source, probe) { reason ->
                        failure = LiveFailure(
                            "Live analysis unavailable", reason + ". Nothing is being measured.", offersCameraSwitch = true,
                        )
                    }
                }
                camera?.bind(lifecycleOwner, preview, view.display?.rotation ?: Surface.ROTATION_0)
                onDispose { camera?.close() }
            }
            screen(preview) {
                // Closed now, so nothing after the tap is part of the set; written
                // on the process scope, awaited above.
                saver.endSet(watching = true)
                ending = true
            }
        }
    }
}

/**
 * Portrait while the live screen is shown - either way up, following the phone
 * (DECISIONS.md P3-19) - and the previous orientation restored when it leaves.
 *
 * The activity declares that it handles orientation changes itself, so this
 * does not recreate it - which would lose the navigation state and drop the
 * user back on the dashboard mid-set.
 */
@Composable
private fun LockToPortrait() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = LiveOrientation.INITIAL
        // The phone's tilt, read here rather than left to the system, which will
        // not turn the owner's phone upside down for a sensor orientation - see
        // LiveOrientation. Called on the main thread.
        val tilt = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(tiltDegrees: Int) {
                val wanted = LiveOrientation.requestFor(tiltDegrees) ?: return
                if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
            }
        }
        if (tilt.canDetectOrientation()) tilt.enable()
        onDispose {
            tilt.disable()
            activity.requestedOrientation = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun rememberOverlay(): SkeletonOverlayView {
    val context = LocalContext.current
    // Mirroring is set per camera when the camera starts - LiveCameraSession.
    return remember { SkeletonOverlayView(context) }
}

@Composable
private fun rememberMainExecutor() = LocalContext.current.let { context ->
    remember { ContextCompat.getMainExecutor(context) }
}
