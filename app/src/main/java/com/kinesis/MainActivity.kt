package com.kinesis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.MediaPipeException
import com.kinesis.camera.LiveCamera
import com.kinesis.camera.PoseLandmarkerSource
import com.kinesis.camera.PoseModelAsset
import com.kinesis.camera.PoseModelState
import com.kinesis.camera.hasCameraPermission
import com.kinesis.camera.rememberCameraPermissionLauncher
import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.health.ActivityContextPermissions
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ActivityContextSource
import com.kinesis.health.HealthConnectActivityContextSource
import com.kinesis.health.rememberHealthPermissionLauncher
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.RoomSessionRepository
import com.kinesis.storage.SessionRepository
import com.kinesis.sync.AuthFailure
import com.kinesis.sync.AuthGateway
import com.kinesis.sync.AuthResult
import com.kinesis.sync.AuthState
import com.kinesis.sync.FirebaseAuthGateway
import com.kinesis.sync.FirestoreSessionStore
import com.kinesis.sync.SessionMerge
import com.kinesis.sync.SyncingSessionRepository
import com.kinesis.sync.WorkManagerSyncQueue
import com.kinesis.pose.CameraFacing
import com.kinesis.ui.live.LiveAnalysisBridge
import com.kinesis.ui.live.LiveSessionEngine
import com.kinesis.ui.live.LiveUiState
import com.kinesis.ui.live.SkeletonOverlayView
import com.kinesis.ui.screens.AuthScreen
import com.kinesis.ui.screens.DashboardScreen
import com.kinesis.ui.screens.ExerciseSelectionScreen
import com.kinesis.ui.screens.LiveAnalysisScreen
import com.kinesis.ui.screens.LiveUnavailableScreen
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.PhosphorFaint
import com.kinesis.ui.theme.PhosphorGreen
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * The app shell - DECISIONS.md P3-12.
 *
 * Three destinations, no navigation library: there are three screens and one
 * back path, so a dependency for it would be a §17 check spent on a `when`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val local = RoomSessionRepository(KinesisDatabase.get(applicationContext).sessionDao())
        val auth: AuthGateway = FirebaseAuthGateway()

        // Room stays the source of truth; the decorator only schedules an
        // upload after a local write has already succeeded - P3-14.
        val repository: SessionRepository = SyncingSessionRepository(
            delegate = local,
            queue = WorkManagerSyncQueue(applicationContext) {
                (auth.state as? AuthState.SignedIn)?.uid
            },
            isSignedIn = { auth.state is AuthState.SignedIn },
        )
        val merge = SessionMerge(local, FirestoreSessionStore())
        val contextSource = HealthConnectActivityContextSource(applicationContext)

        setContent {
            KinesisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KinesisRoot(auth, merge, repository, contextSource)
                }
            }
        }
    }
}

/**
 * Signed out, the app shows the account screen and nothing else - P3-14 makes
 * sign-in a gate on recording and viewing history.
 *
 * On a successful sign-in the account's history is merged before the dashboard
 * appears, so a new device does not briefly show an empty history that then
 * fills in. Both halves are wrapped in runCatching: a failed pull is an empty
 * pull, and neither can block the app from working offline.
 */
@Composable
private fun KinesisRoot(
    auth: AuthGateway,
    merge: SessionMerge,
    repository: SessionRepository,
    contextSource: ActivityContextSource,
) {
    var state: AuthState by remember { mutableStateOf(auth.state) }
    var busy by remember { mutableStateOf(false) }
    var failure: AuthFailure? by remember { mutableStateOf(null) }
    var mergedFor: String? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    when (val current = state) {
        is AuthState.SignedIn -> {
            LaunchedEffect(current.uid) {
                if (mergedFor != current.uid) {
                    runCatching { merge.pullMissing(current.uid) }
                    runCatching { merge.pushMissing(current.uid) }
                    mergedFor = current.uid
                }
            }
            KinesisApp(repository, contextSource, current.email) {
                scope.launch {
                    auth.signOut()
                    state = auth.state
                    mergedFor = null
                }
            }
        }

        else -> AuthScreen(
            busy = busy,
            failure = failure,
            onSignIn = { email, password ->
                scope.launch {
                    busy = true
                    failure = null
                    when (val result = auth.signIn(email, password)) {
                        is AuthResult.Success -> state = auth.state
                        is AuthResult.Failed -> failure = result.reason
                    }
                    busy = false
                }
            },
            onSignUp = { email, password ->
                scope.launch {
                    busy = true
                    failure = null
                    when (val result = auth.signUp(email, password)) {
                        is AuthResult.Success -> state = auth.state
                        is AuthResult.Failed -> failure = result.reason
                    }
                    busy = false
                }
            },
        )
    }
}

private sealed interface Destination {
    data object Dashboard : Destination
    data object Select : Destination
    data class Live(val profile: ExerciseProfile) : Destination
}

@Composable
private fun KinesisApp(
    repository: SessionRepository,
    contextSource: ActivityContextSource,
    email: String?,
    onSignOut: () -> Unit,
) {
    var destination: Destination by remember { mutableStateOf(Destination.Dashboard) }
    var summary: DashboardSummary? by remember { mutableStateOf(null) }
    var context: ActivityContextResult? by remember { mutableStateOf(null) }
    var reloadToken by remember { mutableStateOf(0) }

    val offsetMinutes = remember { TimeZone.getDefault().rawOffset / 60_000 }
    val today = remember(reloadToken) {
        LocalDay.of(System.currentTimeMillis().toDouble(), offsetMinutes)
    }

    // The dashboard reads from storage, not from memory - this batch's item 3.
    // Re-read whenever something could have changed it.
    LaunchedEffect(reloadToken) {
        summary = Dashboard.summarise(repository.allSessions(), today)
        context = contextSource.readDay(today, offsetMinutes)
    }

    val requestPermissions = rememberHealthPermissionLauncher { reloadToken++ }

    Column(Modifier.fillMaxSize()) {
        NavBar(
            destination = destination,
            email = email,
            onDashboard = { destination = Destination.Dashboard; reloadToken++ },
            onSelect = { destination = Destination.Select },
            onSignOut = onSignOut,
        )

        when (val current = destination) {
            Destination.Dashboard -> DashboardScreen(
                summary = summary ?: Dashboard.summarise(emptyList(), today),
                context = context,
                onRequestHealthAccess = requestPermissions?.let {
                    { it(ActivityContextPermissions.ALL) }
                },
            )

            Destination.Select -> ExerciseSelectionScreen(
                onSelect = { destination = Destination.Live(it) },
            )

            is Destination.Live -> LiveDestination(current.profile)
        }
    }
}

/**
 * The live screen's preconditions, checked in the order a failure should be
 * reported - DECISIONS.md P3-15. Each unmet one is shown as itself rather than
 * as a readout of zeroes.
 */
@Composable
private fun LiveDestination(profile: ExerciseProfile) {
    if (profile !is RepetitionExercise) {
        // Holds go through HoldAnalyser, which this screen does not run yet.
        // Forcing one through the repetition tracker is what CLAUDE.md section
        // 11 forbids, and the type system already refuses it.
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = profile.mode.name.replace('_', ' ') + " NOT YET WIRED",
            detail = "The live camera runs repetition exercises only. " + profile.displayName +
                " is analysed as a hold, by an analyser this screen does not run yet. " +
                "Nothing is being measured.",
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

        is PoseModelState.Present -> LiveCameraGate(profile, model.assetPath)
    }
}

/** Asks once when the screen opens; after that, only when the user taps. */
@Composable
private fun LiveCameraGate(profile: RepetitionExercise, modelAssetPath: String) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    val request = rememberCameraPermissionLauncher { granted ->
        permitted = granted
        refused = !granted
    }
    LaunchedEffect(Unit) { if (!permitted) request() }

    if (permitted) {
        LiveCameraSession(profile, modelAssetPath)
    } else {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = if (refused) "CAMERA PERMISSION REFUSED" else "CAMERA PERMISSION NEEDED",
            detail = "Live analysis needs the camera. Frames are analysed on this phone; none is " +
                "stored or uploaded." +
                if (refused) " If the system no longer asks, allow it in Settings > Apps > Kinesis." else "",
            action = "ALLOW CAMERA",
            onAction = request,
        )
    }
}

/**
 * The running pipeline: camera -> MediaPipe -> PoseAdapter -> shared-core
 * analysis -> bridge and overlay. Torn down when the screen leaves composition;
 * CameraX's lifecycle binding stops the camera when the app is backgrounded.
 */
@Composable
private fun LiveCameraSession(profile: RepetitionExercise, modelAssetPath: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(context) }

    val preview = remember {
        PreviewView(context).apply {
            // A TextureView, so the overlay above it composites like any other
            // view instead of depending on SurfaceView z-ordering inside Compose.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val overlay = remember { SkeletonOverlayView(context).apply { displayMirrored = true } }

    // The bridge publishes on MediaPipe's thread; Compose state is set on main.
    val published = remember(profile) { mutableStateOf<LiveUiState?>(null) }
    val bridge = remember(profile) {
        LiveAnalysisBridge(profile.id) { state -> main.execute { published.value = state } }
    }
    val engine = remember(profile) { LiveSessionEngine(LiveRepetitionAnalysis(profile), bridge, overlay) }
    var failure: String? by remember(profile) { mutableStateOf(null) }

    DisposableEffect(engine, modelAssetPath, lifecycleOwner) {
        val camera = try {
            val source = PoseLandmarkerSource(
                context, modelAssetPath, CameraFacing.FRONT, engine::onLandmarks, engine::onNoPose,
            )
            LiveCamera(context, source) { reason -> failure = reason }
        } catch (error: MediaPipeException) {
            failure = "the pose model could not be loaded: " + error.message
            null
        } catch (error: UnsatisfiedLinkError) {
            // MediaPipe ships arm64-v8a, armeabi-v7a and x86 only - DECISIONS.md P3-15.
            failure = "MediaPipe has no native library for this device's processor"
            null
        }
        camera?.bind(lifecycleOwner, preview)
        onDispose { camera?.close() }
    }

    val reason = failure
    if (reason != null) {
        LiveUnavailableScreen(
            exerciseId = profile.id,
            headline = "LIVE ANALYSIS UNAVAILABLE",
            detail = reason + ". Nothing is being measured.",
        )
    } else {
        LiveAnalysisScreen(state = published.value ?: bridge.state, preview = preview, overlay = overlay)
    }
}

@Composable
private fun NavBar(
    destination: Destination,
    email: String?,
    onDashboard: () -> Unit,
    onSelect: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // targetSdk 35 means Android 15 enforces edge-to-edge, so the app
            // is laid out behind the status bar and has to inset itself. The
            // background is applied *before* the inset so the bar's colour
            // still fills the area behind the clock, and only the content is
            // pushed clear of it.
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        NavItem("HISTORY", destination is Destination.Dashboard, onDashboard)
        NavItem("TRAIN", destination !is Destination.Dashboard, onSelect)
        Spacer(Modifier.weight(1f))
        // The signed-in address stays visible. A user should never have to
        // wonder which account their training is going to.
        Text(
            text = (email ?: "signed in") + "  [OUT]",
            style = MaterialTheme.typography.labelSmall,
            color = PhosphorFaint,
            modifier = Modifier.clickable(onClick = onSignOut),
        )
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "> $label" else "  $label",
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) PhosphorGreen else PhosphorFaint,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
