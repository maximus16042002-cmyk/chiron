package com.kinesis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.export.ExportFormat
import com.kinesis.export.SessionExporter
import com.kinesis.health.ActivityContextPermissions
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ActivityContextSource
import com.kinesis.health.HealthConnectActivityContextSource
import com.kinesis.health.rememberHealthPermissionLauncher
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.RoomSessionRepository
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.utcOffsetMinutesAt
import com.kinesis.sync.AuthFailure
import com.kinesis.sync.AuthGateway
import com.kinesis.sync.AuthResult
import com.kinesis.sync.AuthState
import com.kinesis.sync.FirebaseAuthGateway
import com.kinesis.sync.FirestoreSessionStore
import com.kinesis.sync.SessionMerge
import com.kinesis.sync.SyncingSessionRepository
import com.kinesis.sync.WorkManagerSyncQueue
import com.kinesis.licenses.ThirdPartyNotices
import com.kinesis.ui.live.LiveDestination
import com.kinesis.ui.screens.LicencesScreen
import com.kinesis.ui.screens.AuthScreen
import com.kinesis.ui.screens.DashboardScreen
import com.kinesis.ui.screens.ExerciseSelectionScreen
import com.kinesis.ui.screens.CalibrationIntroScreen
import com.kinesis.ui.screens.CalibrationResultScreen
import com.kinesis.ui.screens.ExerciseScreen
import com.kinesis.ui.screens.SetSummaryScreen
import com.kinesis.calibration.CalibrationCoordinator
import com.kinesis.core.analysis.calibration.CalibrationGuide
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.RoomBaselineRepository
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.PhosphorFaint
import com.kinesis.ui.theme.PhosphorGreen
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The app shell - DECISIONS.md P3-12.
 *
 * Three destinations, no navigation library: there are three screens and one
 * back path, so a dependency for it would be a §17 check spent on a `when`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = KinesisDatabase.get(applicationContext)
        val local = RoomSessionRepository(database.sessionDao())
        // Baselines stay on this phone: nothing wraps this repository with sync.
        // DECISIONS.md P3-22.
        val calibration = CalibrationCoordinator(RoomBaselineRepository(database.baselineDao()))
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
                    KinesisRoot(auth, merge, repository, contextSource, calibration)
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
    calibration: CalibrationCoordinator,
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
            KinesisApp(repository, contextSource, calibration, current.uid, current.email) {
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
    data object Licences : Destination

    // DECISIONS.md P3-22: an exercise's own screen, calibration, and the set summary.
    data class Exercise(val profile: ExerciseProfile) : Destination
    data class CalibrationIntro(val profile: ExerciseProfile) : Destination
    data class Live(val profile: ExerciseProfile, val calibrating: Boolean = false) : Destination
    data class CalibrationResult(val profile: ExerciseProfile, val session: SessionRecord?) : Destination
    data class SetSummary(val profile: ExerciseProfile, val session: SessionRecord) : Destination
}

/** The training side of the app: every destination reached from TRAIN. */
private val Destination.isTraining: Boolean
    get() = this !is Destination.Dashboard && this !is Destination.Licences

@Composable
private fun KinesisApp(
    repository: SessionRepository,
    contextSource: ActivityContextSource,
    calibration: CalibrationCoordinator,
    uid: String,
    email: String?,
    onSignOut: () -> Unit,
) {
    var destination: Destination by remember { mutableStateOf(Destination.Dashboard) }
    var summary: DashboardSummary? by remember { mutableStateOf(null) }
    // The signed-in user's baselines, by calibration id. Re-read with the history.
    var baselines: Map<String, CalibrationBaseline> by remember(uid) { mutableStateOf(emptyMap()) }
    var context: ActivityContextResult? by remember { mutableStateOf(null) }
    var reloadToken by remember { mutableStateOf(0) }

    // The same offset the recorder files sessions under, daylight saving
    // included - P3-16. rawOffset ignored it.
    val offsetMinutes = remember { utcOffsetMinutesAt(System.currentTimeMillis()) }
    val today = remember(reloadToken) {
        LocalDay.of(System.currentTimeMillis().toDouble(), offsetMinutes)
    }

    // The dashboard reads from storage, not from memory - this batch's item 3.
    // Re-read whenever something could have changed it.
    LaunchedEffect(reloadToken) {
        summary = Dashboard.summarise(repository.allSessions(), today)
        baselines = calibration.baselines(uid)
        context = contextSource.readDay(today, offsetMinutes)
    }

    val requestPermissions = rememberHealthPermissionLauncher { reloadToken++ }

    // P5 export. Reads the same stored history the dashboard does; the chooser
    // is started from here because only the UI has an activity to start it on.
    val activityContext = LocalContext.current
    val exporter = remember(repository) { SessionExporter(activityContext.applicationContext, repository) }
    val scope = rememberCoroutineScope()
    var exportNote: String? by remember { mutableStateOf(null) }
    val export: (ExportFormat) -> Unit = { format ->
        scope.launch {
            exportNote = try {
                val chooser = exporter.shareIntent(format, System.currentTimeMillis(), baselines.values.toList())
                if (chooser == null) {
                    "No sessions recorded yet, so there is nothing to export."
                } else {
                    activityContext.startActivity(chooser)
                    null
                }
            } catch (error: IOException) {
                "The export could not be written: " + error.message
            }
        }
    }

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
                onOpenLicences = { destination = Destination.Licences },
                onExport = export,
                exportNote = exportNote,
                baselines = baselines.values.map { displayNameOf(it.calibrationId) to it },
            )

            Destination.Licences -> {
                val appContext = LocalContext.current.applicationContext
                val readAsset = { path: String ->
                    try {
                        appContext.assets.open(path).bufferedReader().use { it.readText() }
                    } catch (error: IOException) {
                        null
                    }
                }
                val licence = remember { readAsset(ThirdPartyNotices.APACHE_LICENSE_ASSET) }
                val bsd = remember {
                    ThirdPartyNotices.bsdNotices.map { it to it.textAsset?.let(readAsset) }
                }
                LicencesScreen(apacheLicenceText = licence, bsdTexts = bsd)
            }

            Destination.Select -> ExerciseSelectionScreen(
                calibratedIds = baselines.keys,
                onSelect = { destination = Destination.Exercise(it) },
            )

            is Destination.Exercise -> ExerciseScreen(
                profile = current.profile,
                baseline = baselines[CalibrationKey.of(current.profile)],
                onStartSet = { destination = Destination.Live(current.profile) },
                // A variant calibrates through its parent - CONTRACTS.md section 3.
                onCalibrate = if (current.profile is ExerciseVariant) {
                    null
                } else {
                    { destination = Destination.CalibrationIntro(current.profile) }
                },
            )

            is Destination.CalibrationIntro -> CalibrationIntroScreen(
                profile = current.profile,
                instructions = CalibrationGuide.instructions(current.profile),
                onStart = { destination = Destination.Live(current.profile, calibrating = true) },
            )

            is Destination.Live -> LiveDestination(
                profile = current.profile,
                repository = repository,
                // A set saved by leaving the screen lands after the dashboard
                // has already been read, so the save itself asks for a re-read.
                onSessionSaved = { reloadToken++ },
                onSetEnded = { session ->
                    reloadToken++
                    destination = when {
                        current.calibrating -> Destination.CalibrationResult(current.profile, session)
                        session != null -> Destination.SetSummary(current.profile, session)
                        else -> Destination.Exercise(current.profile)
                    }
                },
                modeLabel = if (current.calibrating) "CALIBRATION SET" else null,
            )

            is Destination.CalibrationResult -> {
                // Computed and saved once per calibration set: keyed on the set's
                // session, so recomposing never calibrates twice.
                var outcome: CalibrationOutcome? by remember(current) { mutableStateOf(null) }
                LaunchedEffect(current) {
                    outcome = if (current.session == null) {
                        CalibrationCoordinator.withNobodySeen(current.profile)
                    } else {
                        calibration.finishCalibration(uid, current.profile, current.session)
                    }
                    reloadToken++
                }
                CalibrationResultScreen(
                    profile = current.profile,
                    outcome = outcome,
                    onDone = { destination = Destination.Exercise(current.profile) },
                    onTryAgain = { destination = Destination.CalibrationIntro(current.profile) },
                )
            }

            is Destination.SetSummary -> {
                val baseline = baselines[CalibrationKey.of(current.profile)]
                val results = remember(current, baseline) {
                    calibration.analyseSet(current.profile, current.session, baseline)
                }
                SetSummaryScreen(
                    profile = current.profile,
                    session = current.session,
                    baselineTitle = baseline?.let { displayNameOf(it.calibrationId).uppercase() },
                    results = results,
                    onDone = { destination = Destination.Exercise(current.profile) },
                )
            }
        }
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
        NavItem("TRAIN", destination.isTraining, onSelect)
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

/** A calibration id's display name, from the library. */
private fun displayNameOf(calibrationId: String): String =
    ExerciseLibrary.all.firstOrNull { it.id == calibrationId }?.displayName ?: calibrationId
