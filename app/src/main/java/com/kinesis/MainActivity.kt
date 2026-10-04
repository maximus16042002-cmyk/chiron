package com.kinesis

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
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
import com.kinesis.export.ExportFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import com.kinesis.ui.screens.Tab
import com.kinesis.ui.screens.BottomBar
import com.kinesis.ui.screens.HomeScreen
import com.kinesis.ui.screens.ProgressScreen
import com.kinesis.ui.screens.SessionDetailScreen
import com.kinesis.ui.screens.ProfileScreen
import com.kinesis.ui.screens.OnboardingScreen
import com.kinesis.ui.screens.PageTitle
import com.kinesis.ui.screens.KCard
import com.kinesis.ui.screens.Note
import com.kinesis.ui.screens.PrimaryButton
import com.kinesis.ui.theme.Void
import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.analysis.history.DayActivity
import com.kinesis.ui.screens.monthStart
import com.kinesis.ui.screens.BackLink

/**
 * The app shell - DECISIONS.md P3-12, given five pages in P3-48.
 *
 * No navigation library: the pages and the training flow are a few pieces of state, so a
 * dependency for them would be a §17 check spent on a `when`.
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
    // DECISIONS.md P3-48: three short pages on the first launch, and again on request.
    val preferences = LocalContext.current.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    var introduced by remember { mutableStateOf(preferences.getBoolean(INTRODUCED, false)) }
    if (!introduced) {
        OnboardingScreen(onDone = {
            preferences.edit().putBoolean(INTRODUCED, true).apply()
            introduced = true
        })
        return
    }
    val showIntroduction: () -> Unit = {
        preferences.edit().putBoolean(INTRODUCED, false).apply()
        introduced = false
    }

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
            KinesisApp(repository, contextSource, calibration, current.uid, current.email, showIntroduction) {
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

/** The training flow, inside the Train tab - DECISIONS.md P3-22, P3-48. */
private sealed interface Destination {
    data class Exercise(val profile: ExerciseProfile) : Destination
    data class CalibrationIntro(val profile: ExerciseProfile) : Destination
    data class Live(val profile: ExerciseProfile, val calibrating: Boolean = false) : Destination
    data class CalibrationResult(val profile: ExerciseProfile, val session: SessionRecord?) : Destination
    data class SetSummary(val profile: ExerciseProfile, val session: SessionRecord) : Destination
}

/**
 * The app once signed in: five pages behind a bottom bar - DECISIONS.md P3-48. Hand-rolled, as
 * P3-12 chose: a page, the training flow's place, and two detail views are four pieces of state,
 * not a navigation library. The back button walks back through them. The bar is hidden while the
 * camera is live, so the live screen is the camera and nothing else.
 */
@Composable
private fun KinesisApp(
    repository: SessionRepository,
    contextSource: ActivityContextSource,
    calibration: CalibrationCoordinator,
    uid: String,
    email: String?,
    onShowIntroduction: () -> Unit,
    onSignOut: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var training: Destination? by remember { mutableStateOf(null) }
    var openSession: SessionRecord? by remember { mutableStateOf(null) }
    var showLicences by remember { mutableStateOf(false) }

    var sessions: List<SessionRecord> by remember { mutableStateOf(emptyList()) }
    var summary: DashboardSummary? by remember { mutableStateOf(null) }
    var week: List<DayActivity> by remember { mutableStateOf(emptyList()) }
    var calendar: List<DayActivity> by remember { mutableStateOf(emptyList()) }
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

    // The pages read from storage, not from memory. Re-read whenever something could have changed it.
    LaunchedEffect(reloadToken) {
        val all = repository.allSessions()
        sessions = all.sortedByDescending { it.startedAtEpochMs }
        summary = Dashboard.summarise(all, today)
        week = Streaks.activity(all, today, from = today.plusDays(-6), to = today)
        val firstDay = all.minOfOrNull { it.localDay.dayNumber }?.let { LocalDay(it) } ?: today
        calendar = Streaks.activity(all, today, from = monthStart(minOf(firstDay, today)), to = today)
        baselines = calibration.baselines(uid)
        context = contextSource.readDay(today, offsetMinutes)
    }

    val requestPermissions = rememberHealthPermissionLauncher { reloadToken++ }

    // P5's export - DECISIONS.md P3-25. Every local session and this user's
    // baselines, written off the main thread, then handed to the share sheet.
    // One at a time: on the phone an export took about 14 s (P3-25), and a
    // second tap would empty the folder the first is still writing into.
    val exportScope = rememberCoroutineScope()
    val activityContext = LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    val export: () -> Unit = export@{
        if (exporting) return@export
        exporting = true
        exportScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    ExportFiles.write(
                        directory = File(activityContext.cacheDir, EXPORT_DIRECTORY),
                        sessions = repository.allSessions(),
                        baselines = calibration.baselines(uid).values.toList(),
                        exportedAtEpochMs = System.currentTimeMillis().toDouble(),
                        day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()),
                    )
                }
                shareExport(activityContext, files)
            } finally {
                exporting = false
            }
        }
    }

    fun startTraining(profile: ExerciseProfile) {
        training = Destination.Exercise(profile)
        tab = Tab.TRAIN
    }

    // The back button: out of a detail, then out of the training flow, then back to Home. The
    // on-screen back links call the same function, so the two cannot disagree.
    val current = training
    val goBack: () -> Unit = {
        when {
            openSession != null -> openSession = null
            showLicences -> showLicences = false
            tab == Tab.TRAIN && current is Destination.Live -> training = Destination.Exercise(current.profile)
            tab == Tab.TRAIN && current != null && current !is Destination.Exercise ->
                training = Destination.Exercise(profileOf(current))
            tab == Tab.TRAIN && current is Destination.Exercise -> {
                training = null
                tab = Tab.WORKOUTS
            }
            else -> tab = Tab.HOME
        }
    }
    BackHandler(enabled = openSession != null || showLicences || tab != Tab.HOME, onBack = goBack)

    val live = tab == Tab.TRAIN && current is Destination.Live
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            val shown = summary ?: Dashboard.summarise(emptyList(), today)
            when (tab) {
                Tab.HOME -> if (openSession != null) {
                    SessionDetailScreen(openSession!!, backLabel = "Home", onBack = { openSession = null })
                } else {
                    val last = sessions.firstOrNull()
                    HomeScreen(
                        summary = shown,
                        week = week,
                        lastSession = last,
                        quickStart = last?.sets?.lastOrNull()?.exerciseId?.let { id -> ExerciseLibrary.all.firstOrNull { it.id == id } },
                        onQuickStart = { startTraining(it) },
                        onBrowse = { tab = Tab.WORKOUTS },
                        onOpenSession = { openSession = it },
                    )
                }

                Tab.WORKOUTS -> ExerciseSelectionScreen(
                    calibratedIds = baselines.keys,
                    onSelect = { startTraining(it) },
                )

                Tab.TRAIN -> TrainPage(
                    destination = current,
                    repository = repository,
                    calibration = calibration,
                    uid = uid,
                    baselines = baselines,
                    onChoose = { tab = Tab.WORKOUTS },
                    onBack = goBack,
                    onNavigate = { training = it },
                    onChanged = { reloadToken++ },
                )

                Tab.PROGRESS -> if (openSession != null) {
                    SessionDetailScreen(openSession!!, backLabel = "Progress", onBack = { openSession = null })
                } else {
                    ProgressScreen(
                        summary = shown,
                        sessions = sessions,
                        calendar = calendar,
                        today = today,
                        onOpenSession = { openSession = it },
                    )
                }

                Tab.PROFILE -> if (showLicences) {
                    LicencesPage(onBack = goBack)
                } else {
                    ProfileScreen(
                        email = email,
                        onSignOut = onSignOut,
                        baselines = baselines.values.map { displayNameOf(it.calibrationId) to it },
                        context = context,
                        onRequestHealthAccess = requestPermissions?.let { { it(ActivityContextPermissions.ALL) } },
                        onExport = export,
                        exporting = exporting,
                        onOpenLicences = { showLicences = true },
                        onShowIntroduction = onShowIntroduction,
                    )
                }
            }
        }
        if (!live) {
            BottomBar(tab) { selected ->
                if (selected == tab) {
                    // A second tap on the current page returns to its top.
                    openSession = null
                    showLicences = false
                } else {
                    openSession = null
                    showLicences = false
                    tab = selected
                    reloadToken++
                }
            }
        }
    }
}

/** The exercise a training destination belongs to. */
private fun profileOf(destination: Destination): ExerciseProfile = when (destination) {
    is Destination.Exercise -> destination.profile
    is Destination.CalibrationIntro -> destination.profile
    is Destination.Live -> destination.profile
    is Destination.CalibrationResult -> destination.profile
    is Destination.SetSummary -> destination.profile
}

/**
 * The Train tab: the exercise, calibration and live screens, as before (P3-22), or - with no
 * exercise chosen yet - the way to choose one.
 */
@Composable
private fun TrainPage(
    destination: Destination?,
    repository: SessionRepository,
    calibration: CalibrationCoordinator,
    uid: String,
    baselines: Map<String, CalibrationBaseline>,
    onChoose: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onChanged: () -> Unit,
) {
    // The old text bar kept every training screen clear of the status bar; with the bar gone, the
    // page does it. Edge-to-edge is enforced at targetSdk 35, so the background goes under the clock.
    Box(
        Modifier
            .fillMaxSize()
            .background(if (destination is Destination.Live) Void else MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column {
            // A visible way back on every training screen but the live one, whose way out is
            // ending the set.
            if (destination != null && destination !is Destination.Live) {
                BackLink(if (destination is Destination.Exercise) "Workouts" else profileOf(destination).displayName, onBack)
            }
            Box(Modifier.weight(1f)) {
                TrainFlow(destination, repository, calibration, uid, baselines, onChoose, onNavigate, onChanged)
            }
        }
    }
}

@Composable
private fun TrainFlow(
    destination: Destination?,
    repository: SessionRepository,
    calibration: CalibrationCoordinator,
    uid: String,
    baselines: Map<String, CalibrationBaseline>,
    onChoose: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onChanged: () -> Unit,
) {
    when (val current = destination) {
        null -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageTitle("Train", "Choose an exercise to start a set")
            KCard {
                Note("Each exercise shows how to place the phone, and lets you calibrate it to your own movement first.")
                Spacer(Modifier.padding(top = 12.dp))
                PrimaryButton("Choose an exercise", onClick = onChoose)
            }
        }

        is Destination.Exercise -> ExerciseScreen(
            profile = current.profile,
            baseline = baselines[CalibrationKey.of(current.profile)],
            onStartSet = { onNavigate(Destination.Live(current.profile)) },
            // A variant calibrates through its parent - CONTRACTS.md section 3.
            onCalibrate = if (current.profile is ExerciseVariant) {
                null
            } else {
                { onNavigate(Destination.CalibrationIntro(current.profile)) }
            },
        )

        is Destination.CalibrationIntro -> CalibrationIntroScreen(
            profile = current.profile,
            instructions = CalibrationGuide.instructions(current.profile),
            onStart = { onNavigate(Destination.Live(current.profile, calibrating = true)) },
        )

        is Destination.Live -> LiveDestination(
            profile = current.profile,
            repository = repository,
            // A set saved by leaving the screen lands after the pages have
            // already been read, so the save itself asks for a re-read.
            onSessionSaved = onChanged,
            onSetEnded = { session ->
                onChanged()
                onNavigate(
                    when {
                        current.calibrating -> Destination.CalibrationResult(current.profile, session)
                        session != null -> Destination.SetSummary(current.profile, session)
                        else -> Destination.Exercise(current.profile)
                    },
                )
            },
            modeLabel = if (current.calibrating) "Calibration set" else null,
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
                onChanged()
            }
            CalibrationResultScreen(
                profile = current.profile,
                outcome = outcome,
                onDone = { onNavigate(Destination.Exercise(current.profile)) },
                onTryAgain = { onNavigate(Destination.CalibrationIntro(current.profile)) },
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
                onDone = { onNavigate(Destination.Exercise(current.profile)) },
            )
        }
    }
}

/** The licences (P3-17), read from the APK's assets. */
@Composable
private fun LicencesPage(onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val readAsset = { path: String ->
        try {
            appContext.assets.open(path).bufferedReader().use { it.readText() }
        } catch (error: IOException) {
            null
        }
    }
    val licence = remember { readAsset(ThirdPartyNotices.APACHE_LICENSE_ASSET) }
    val bsd = remember { ThirdPartyNotices.bsdNotices.map { it to it.textAsset?.let(readAsset) } }
    Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BackLink("Profile", onBack)
        LicencesScreen(apacheLicenceText = licence, bsdTexts = bsd)
    }
}

/** The app's own small settings: SharedPreferences, no new dependency (P3-48). */
private const val PREFERENCES = "kinesis"

/** Whether the first-launch pages have been seen. */
private const val INTRODUCED = "introduced"

/** The cache folder the export is written to; `res/xml/export_paths.xml` shares only this. */
private const val EXPORT_DIRECTORY = "exports"

/**
 * Hands the export's files to the share sheet - DECISIONS.md P3-25. The user
 * picks where they go, if anywhere; each receiving app gets read access to these
 * files alone, through the FileProvider, for as long as it holds the share.
 */
private fun shareExport(context: Context, files: List<File>) {
    val authority = context.packageName + ".exports"
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        // The grant flag applies to the clip's URIs, so every file is on it.
        clipData = ClipData.newRawUri("Kinesis export", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Export Kinesis data")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** A calibration id's display name, from the library. A revised key (P3-46) names its exercise. */
private fun displayNameOf(calibrationId: String): String {
    val exerciseId = CalibrationKey.exerciseIdOf(calibrationId)
    return ExerciseLibrary.all.firstOrNull { it.id == exerciseId }?.displayName ?: calibrationId
}
