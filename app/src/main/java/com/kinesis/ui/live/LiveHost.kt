package com.kinesis.ui.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.MediaPipeException
import com.kinesis.camera.LiveCamera
import com.kinesis.camera.PoseLandmarkerSource
import com.kinesis.camera.PoseModelAsset
import com.kinesis.camera.PoseModelState
import com.kinesis.camera.hasCameraPermission
import com.kinesis.camera.rememberCameraPermissionLauncher
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.pose.CameraFacing
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.utcOffsetMinutesAt
import com.kinesis.ui.screens.LiveAnalysisScreen
import com.kinesis.ui.screens.LiveHoldScreen
import com.kinesis.ui.screens.LiveUnavailableScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

/**
 * Where finished sets are written. Process-lifetime on purpose: leaving the
 * live screen ends the set, and the composition that ended it is gone before the
 * write completes, so the write cannot belong to it. DECISIONS.md P3-16.
 */
private val setWrites = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * The live destination - DECISIONS.md P3-15 and P3-16.
 *
 * Preconditions are checked in the order a failure should be reported, and each
 * unmet one is shown as itself rather than as a readout of zeroes. The screen is
 * locked to portrait while it is shown, because the camera area, the frame shape
 * and the overlay's geometry are all built for portrait and landscape has not
 * been handled deliberately.
 */
@Composable
fun LiveDestination(
    profile: ExerciseProfile,
    repository: SessionRepository,
    onSessionSaved: () -> Unit,
    onSetEnded: () -> Unit,
) {
    LockToPortrait()

    if (profile !is RepetitionExercise && profile !is IsometricExercise) {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "NO LIVE ANALYSER",
            detail = profile.displayName + " is neither a repetition nor a hold profile, so " +
                "nothing here can analyse it. Nothing is being measured.",
        )
        return
    }

    val context = LocalContext.current
    when (val model = remember { PoseModelAsset.locate(context) }) {
        PoseModelState.Absent -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "NO POSE MODEL",
            detail = "No pose_landmarker .task file is in the app's assets, so pose estimation " +
                "cannot run and nothing is being measured. The rest of Kinesis works without it.",
        )

        is PoseModelState.Unreadable -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "POSE MODEL UNREADABLE",
            detail = model.reason + ". Nothing is being measured.",
        )

        is PoseModelState.Present -> LiveCameraGate(
            profile,
            LiveEnvironment(model.assetPath, repository, onSessionSaved, onSetEnded),
        )
    }
}

private class LiveEnvironment(
    val modelAssetPath: String,
    val repository: SessionRepository,
    val onSessionSaved: () -> Unit,
    val onSetEnded: () -> Unit,
)

/** Asks once when the screen opens; after that, only when the user taps. */
@Composable
private fun LiveCameraGate(profile: ExerciseProfile, env: LiveEnvironment) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    val request = rememberCameraPermissionLauncher { granted ->
        permitted = granted
        refused = !granted
    }
    LaunchedEffect(Unit) { if (!permitted) request() }

    if (!permitted) {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = if (refused) "CAMERA PERMISSION REFUSED" else "CAMERA PERMISSION NEEDED",
            detail = "Live analysis needs the camera. Frames are analysed on this phone; none is " +
                "stored or uploaded." +
                if (refused) " If the system no longer asks, allow it in Settings > Apps > Kinesis." else "",
            action = "ALLOW CAMERA",
            onAction = request,
        )
        return
    }

    when (profile) {
        is RepetitionExercise -> LiveRepetitionSession(profile, env)
        is IsometricExercise -> LiveHoldSession(profile, env)
    }
}

@Composable
private fun LiveRepetitionSession(profile: RepetitionExercise, env: LiveEnvironment) {
    val overlay = rememberOverlay()
    val main = rememberMainExecutor()
    // The bridge publishes on MediaPipe's thread; Compose state is set on main.
    val published = remember(profile) { mutableStateOf<LiveUiState?>(null) }
    val bridge = remember(profile) {
        LiveAnalysisBridge(profile.id) { state -> main.execute { published.value = state } }
    }
    val engine = remember(profile) { LiveRepetitionEngine(profile, bridge, overlay) }

    LiveCameraSession(profile, engine, overlay, env) { preview, onEndSet ->
        LiveAnalysisScreen(published.value ?: bridge.state, preview, overlay, onEndSet)
    }
}

/** Plank, Wall Sit, Side Plank - through HoldAnalyser, never the repetition tracker. */
@Composable
private fun LiveHoldSession(profile: IsometricExercise, env: LiveEnvironment) {
    val overlay = rememberOverlay()
    val main = rememberMainExecutor()
    val published = remember(profile) { mutableStateOf<LiveHoldUiState?>(null) }
    val bridge = remember(profile) {
        LiveHoldBridge(profile.id) { state -> main.execute { published.value = state } }
    }
    val engine = remember(profile) { LiveHoldEngine(profile, bridge, overlay) }

    LiveCameraSession(profile, engine, overlay, env) { preview, onEndSet ->
        LiveHoldScreen(published.value ?: bridge.state, preview, overlay, onEndSet)
    }
}

/**
 * The running pipeline: camera -> MediaPipe -> PoseAdapter -> shared-core
 * analysis -> bridge and overlay, whichever mode [engine] is.
 *
 * A set ends by the end-set control or by leaving the screen, whichever comes
 * first; both go through the same [LiveSetSaver], so a set is written once.
 */
@Composable
private fun LiveCameraSession(
    profile: ExerciseProfile,
    engine: LiveEngine,
    overlay: SkeletonOverlayView,
    env: LiveEnvironment,
    screen: @Composable (preview: PreviewView, onEndSet: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
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
        )
    }
    var ending by remember(engine) { mutableStateOf(false) }
    var failure: Pair<String, String>? by remember(engine) { mutableStateOf(null) }

    // Leaving the screen ends the set too. Returns the same write if the
    // end-set control already started it.
    DisposableEffect(saver) { onDispose { saver.endSet() } }

    if (ending) {
        LaunchedEffect(saver) {
            when (val outcome = saver.endSet().await()) {
                SetSaveOutcome.Saved, SetSaveOutcome.NothingToSave -> env.onSetEnded()
                is SetSaveOutcome.Failed -> failure = "SET NOT SAVED" to outcome.reason
            }
        }
    }

    val shown = failure
    when {
        shown != null -> LiveUnavailableScreen(profile.id, shown.first, shown.second)

        ending -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "SAVING SET",
            detail = "Writing this set to the phone. If you are signed in, sync follows later.",
        )

        !portrait -> LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "TURNING TO PORTRAIT",
            detail = "Live analysis runs in portrait only.",
        )

        else -> {
            DisposableEffect(engine, env.modelAssetPath, lifecycleOwner) {
                val camera = try {
                    val source = PoseLandmarkerSource(
                        context, env.modelAssetPath, CameraFacing.FRONT, engine::onLandmarks, engine::onNoPose,
                    )
                    LiveCamera(context, source) { reason ->
                        failure = "LIVE ANALYSIS UNAVAILABLE" to reason + ". Nothing is being measured."
                    }
                } catch (error: MediaPipeException) {
                    failure = "LIVE ANALYSIS UNAVAILABLE" to
                        "the pose model could not be loaded: " + error.message + ". Nothing is being measured."
                    null
                } catch (error: UnsatisfiedLinkError) {
                    // MediaPipe ships arm64-v8a, armeabi-v7a and x86 only - P3-15.
                    failure = "LIVE ANALYSIS UNAVAILABLE" to
                        "MediaPipe has no native library for this device's processor. Nothing is being measured."
                    null
                }
                camera?.bind(lifecycleOwner, preview, view.display?.rotation ?: Surface.ROTATION_0)
                onDispose { camera?.close() }
            }
            screen(preview) {
                // Closed now, so nothing after the tap is part of the set; written
                // on the process scope, awaited above.
                saver.endSet()
                ending = true
            }
        }
    }
}

/**
 * Portrait while the live screen is shown, restored when it leaves.
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
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
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
    return remember { SkeletonOverlayView(context).apply { displayMirrored = true } }
}

@Composable
private fun rememberMainExecutor() = LocalContext.current.let { context ->
    remember { ContextCompat.getMainExecutor(context) }
}
