package com.kinesis

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kinesis.calibration.CalibrationCoordinator
import com.kinesis.core.analysis.deviation.PersonalStartZone
import com.kinesis.core.analysis.calibration.CalibrationGuide
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.verifier.VerifierSetCheck
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.history.Achievement
import com.kinesis.core.analysis.history.Achievements
import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.analysis.history.DayActivity
import com.kinesis.core.analysis.history.Records
import com.kinesis.core.analysis.history.Streaks
import com.kinesis.core.analysis.history.Trends
import com.kinesis.core.analysis.history.WeekProgress
import com.kinesis.core.analysis.history.WeeklyGoal
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.workout.GuidedWorkout
import com.kinesis.core.analysis.workout.PlanRequest
import com.kinesis.core.analysis.workout.WorkoutCatalogue
import com.kinesis.core.analysis.workout.WorkoutGenerator
import com.kinesis.core.analysis.workout.WorkoutPlan
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.export.ExportFiles
import com.kinesis.health.ActivityContextPermissions
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ActivityContextSource
import com.kinesis.health.HealthConnectActivityContextSource
import com.kinesis.health.rememberHealthPermissionLauncher
import com.kinesis.licenses.ThirdPartyNotices
import com.kinesis.reminders.ReminderNotification
import com.kinesis.reminders.ReminderScheduler
import com.kinesis.settings.AppSettings
import com.kinesis.storage.AccountDatabaseNames
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.PreferencesAccountRegistry
import com.kinesis.storage.RoomBaselineRepository
import com.kinesis.storage.RoomSessionRepository
import com.kinesis.storage.SessionRepository
import com.kinesis.storage.SetDrafts
import com.kinesis.storage.utcOffsetMinutesAt
import com.kinesis.sync.AuthFailure
import com.kinesis.sync.AuthGateway
import com.kinesis.sync.AuthResult
import com.kinesis.sync.AuthState
import com.kinesis.sync.FirebaseAuthGateway
import com.kinesis.sync.PasswordReset
import com.kinesis.sync.FirestoreSessionStore
import com.kinesis.sync.SessionMerge
import com.kinesis.sync.SyncingSessionRepository
import com.kinesis.sync.WorkManagerSyncQueue
import com.kinesis.ui.live.LiveDestination
import com.kinesis.ui.live.LocalSetDrafts
import com.kinesis.ui.screens.AuthScreen
import com.kinesis.ui.screens.BackLink
import com.kinesis.ui.screens.BottomBar
import com.kinesis.ui.screens.CalibrationIntroScreen
import com.kinesis.ui.screens.CalibrationResultScreen
import com.kinesis.ui.screens.DuringSetState
import com.kinesis.ui.screens.ExerciseScreen
import com.kinesis.ui.screens.ExerciseSelectionScreen
import com.kinesis.ui.screens.ExerciseTrendScreen
import com.kinesis.ui.screens.GuidedPrepareScreen
import com.kinesis.ui.screens.GuidedRestScreen
import com.kinesis.ui.screens.GuidedSummaryScreen
import com.kinesis.ui.screens.HomeScreen
import com.kinesis.ui.screens.KCard
import com.kinesis.ui.screens.LicencesScreen
import com.kinesis.ui.screens.Note
import com.kinesis.ui.screens.OnboardingScreen
import com.kinesis.ui.screens.PageTitle
import com.kinesis.ui.screens.PlanScreen
import com.kinesis.ui.screens.PrimaryButton
import com.kinesis.ui.screens.ProfileScreen
import com.kinesis.ui.screens.ProgressScreen
import com.kinesis.ui.screens.ReminderState
import com.kinesis.ui.screens.SecondaryButton
import com.kinesis.ui.screens.SessionDetailScreen
import com.kinesis.ui.screens.SetSummaryScreen
import com.kinesis.ui.screens.Tab
import com.kinesis.ui.screens.WorkoutBuilderScreen
import com.kinesis.ui.screens.WorkoutPlansCard
import com.kinesis.ui.screens.monthStart
import com.kinesis.ui.screens.stepTarget
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.Void
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app shell - DECISIONS.md P3-12, given five pages in P3-48.
 *
 * No navigation library: the pages and the training flow are a few pieces of state, so a
 * dependency for them would be a §17 check spent on a `when`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A reminder the user turned on is waiting, whatever happened to the app since - P3-49.
        ReminderScheduler.ensureScheduled(applicationContext)
        val auth: AuthGateway = FirebaseAuthGateway()
        val contextSource = HealthConnectActivityContextSource(applicationContext)
        val openAccount: (String) -> AccountStorage = { uid -> AccountStorage.open(applicationContext, auth, uid) }

        setContent {
            KinesisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KinesisRoot(auth, openAccount, contextSource)
                }
            }
        }
    }
}

/**
 * One account's storage: its own database file, so its sessions and baselines are its own -
 * DECISIONS.md P3-60. Until then one database served every account, and sign-in uploaded every
 * session on the phone to whoever had signed in.
 */
private class AccountStorage(
    val repository: SessionRepository,
    val merge: SessionMerge,
    val calibration: CalibrationCoordinator,
    /** Drafts of sets in progress, so a set survives the app being killed (P3-66). */
    val drafts: SetDrafts,
) {
    companion object {
        fun open(context: Context, auth: AuthGateway, uid: String): AccountStorage {
            val registry = PreferencesAccountRegistry(context)
            registry.write(AccountDatabaseNames.CURRENT_ACCOUNT, uid)
            val database = KinesisDatabase.forAccount(context, uid)
            val local = RoomSessionRepository(database.sessionDao())
            // Room stays the source of truth; the decorator only schedules an upload after a
            // local write has already succeeded - P3-14. The upload goes to this repository's own
            // account, and only while that account is the one signed in (P3-60).
            val repository = SyncingSessionRepository(
                delegate = local,
                queue = WorkManagerSyncQueue(context) { uid },
                isSignedIn = { (auth.state as? AuthState.SignedIn)?.uid == uid },
            )
            return AccountStorage(
                repository = repository,
                merge = SessionMerge(local, FirestoreSessionStore()),
                // Baselines stay on this phone: nothing wraps this repository with sync. P3-22.
                calibration = CalibrationCoordinator(RoomBaselineRepository(database.baselineDao())),
                drafts = SetDrafts.forAccount(context, uid),
            )
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
    openAccount: (String) -> AccountStorage,
    contextSource: ActivityContextSource,
) {
    // DECISIONS.md P3-48: three short pages on the first launch, and again on request.
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { AppSettings(appContext) }
    var introduced by remember { mutableStateOf(settings.introduced) }
    if (!introduced) {
        OnboardingScreen(onDone = {
            settings.introduced = true
            introduced = true
        })
        return
    }
    val showIntroduction: () -> Unit = {
        settings.introduced = false
        introduced = false
    }

    var state: AuthState by remember { mutableStateOf(auth.state) }
    var busy by remember { mutableStateOf(false) }
    var failure: AuthFailure? by remember { mutableStateOf(null) }
    var resetNotice: String? by remember { mutableStateOf(null) }
    var mergedFor: String? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    when (val current = state) {
        is AuthState.SignedIn -> {
            // This account's own database (P3-60), opened once per account.
            val storage = remember(current.uid) { openAccount(current.uid) }
            LaunchedEffect(current.uid) {
                if (mergedFor != current.uid) {
                    // A set the app was killed during, or whose save failed, is saved first, so the
                    // merge below uploads it like any other (P3-66).
                    runCatching { storage.drafts.recover(storage.repository) }
                    runCatching { storage.merge.pullMissing(current.uid) }
                    runCatching { storage.merge.pushMissing(current.uid) }
                    mergedFor = current.uid
                }
            }
            CompositionLocalProvider(LocalSetDrafts provides storage.drafts) {
                KinesisApp(storage.repository, contextSource, storage.calibration, current.uid, current.email, showIntroduction) {
                    scope.launch {
                        auth.signOut()
                        state = auth.state
                        mergedFor = null
                    }
                }
            }
        }

        else -> AuthScreen(
            busy = busy,
            failure = failure,
            resetNotice = resetNotice,
            // DECISIONS.md P3-80: a reset link by email, through Firebase.
            onForgotPassword = { email ->
                scope.launch {
                    busy = true
                    failure = null
                    resetNotice = PasswordReset.messageFor(auth.sendPasswordReset(email))
                    busy = false
                }
            },
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

    // DECISIONS.md P3-50: the workout generator and guided mode.
    data class Builder(val initial: PlanRequest?) : Destination
    data class Plan(val request: PlanRequest) : Destination
    data class Guided(
        val plan: WorkoutPlan,
        /** Index into `GuidedWorkout.steps(plan)`: the set being prepared, done, or rested before. */
        val step: Int,
        val phase: GuidedPhase,
        /** The sets saved so far in this workout. */
        val done: List<SessionRecord>,
        val startedAtMs: Long,
    ) : Destination
}

/** Where a guided workout is within a step - P3-50. */
private enum class GuidedPhase { PREPARE, LIVE, REST, SUMMARY }

/** Whether the camera is live, which hides the bottom bar and the back link. */
private fun isLive(destination: Destination?): Boolean =
    destination is Destination.Live || (destination is Destination.Guided && destination.phase == GuidedPhase.LIVE)

/**
 * One step back through the training flow, or `null` to leave it: out of the camera to where the
 * set was started, out of a guided workout to its plan (every set already saved stays saved), out
 * of a plan to its choices.
 */
private fun backFrom(destination: Destination): Destination? = when (destination) {
    is Destination.Exercise, is Destination.Builder -> null
    is Destination.Live -> Destination.Exercise(destination.profile)
    is Destination.CalibrationIntro -> Destination.Exercise(destination.profile)
    is Destination.CalibrationResult -> Destination.Exercise(destination.profile)
    is Destination.SetSummary -> Destination.Exercise(destination.profile)
    is Destination.Plan -> Destination.Builder(destination.request)
    is Destination.Guided -> when (destination.phase) {
        GuidedPhase.LIVE -> destination.copy(phase = GuidedPhase.PREPARE)
        GuidedPhase.SUMMARY -> null
        else -> Destination.Plan(destination.plan.request)
    }
}

/** What the back link names. */
private fun backLabel(destination: Destination): String = when (destination) {
    is Destination.Exercise, is Destination.Builder -> "Workouts"
    is Destination.Plan -> "Build a workout"
    is Destination.Guided -> "Plan"
    is Destination.Live -> destination.profile.displayName
    is Destination.CalibrationIntro -> destination.profile.displayName
    is Destination.CalibrationResult -> destination.profile.displayName
    is Destination.SetSummary -> destination.profile.displayName
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
    // An exercise's trend page, opened from Progress (P3-51).
    var openExercise: String? by remember { mutableStateOf(null) }
    var showLicences by remember { mutableStateOf(false) }

    var sessions: List<SessionRecord> by remember { mutableStateOf(emptyList()) }
    var summary: DashboardSummary? by remember { mutableStateOf(null) }
    var week: List<DayActivity> by remember { mutableStateOf(emptyList()) }
    var calendar: List<DayActivity> by remember { mutableStateOf(emptyList()) }
    var weekGoal: WeekProgress? by remember { mutableStateOf(null) }
    var achievements: List<Achievement> by remember { mutableStateOf(emptyList()) }
    val settings = LocalContext.current.let { remember(it) { AppSettings(it) } }
    var goalDays by remember { mutableIntStateOf(settings.weeklyGoalDays) }
    var experimentalVerifier by remember { mutableStateOf(settings.experimentalVerifier) }
    var duringSet by remember {
        mutableStateOf(DuringSetState(settings.repTone, settings.repVibrate, settings.spokenCounts, settings.restSeconds))
    }
    val firstWeekday = remember { Calendar.getInstance().firstDayOfWeek }
    // The signed-in user's baselines, by calibration id. Re-read with the history.
    var baselines: Map<String, CalibrationBaseline> by remember(uid) { mutableStateOf(emptyMap()) }
    var context: ActivityContextResult? by remember { mutableStateOf(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

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
        weekGoal = WeeklyGoal.progress(all, today, firstWeekday, goalDays)
        achievements = Achievements.of(all, today)
        baselines = calibration.baselines(uid)
        context = contextSource.readDay(today, offsetMinutes)
    }

    val requestPermissions = rememberHealthPermissionLauncher { reloadToken++ }

    // Reminders - DECISIONS.md P3-49. Stored in AppSettings, scheduled on every change. Turning
    // them on asks for the notification permission on Android 13+; a refusal leaves them off.
    val reminderContext = LocalContext.current.applicationContext
    var reminder by remember {
        val permitted = ReminderNotification.permitted(reminderContext)
        mutableStateOf(
            ReminderState(
                on = settings.remindersOn && permitted,
                minuteOfDay = settings.reminderMinuteOfDay,
                weekdays = settings.reminderWeekdays,
                refused = settings.remindersOn && !permitted,
            ),
        )
    }
    val applyReminder: (ReminderState) -> Unit = { state ->
        settings.remindersOn = state.on
        settings.reminderMinuteOfDay = state.minuteOfDay
        settings.reminderWeekdays = state.weekdays
        reminder = state
        ReminderScheduler.reschedule(reminderContext)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        applyReminder(reminder.copy(on = granted, refused = !granted))
    }
    val toggleReminders: (Boolean) -> Unit = { on ->
        if (on && !ReminderNotification.permitted(reminderContext) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applyReminder(reminder.copy(on = on, refused = false))
        }
    }

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

    fun openTraining(destination: Destination) {
        training = destination
        tab = Tab.TRAIN
    }

    // Saved workouts and the last one started, as request and seed (P3-50).
    var favourites by remember { mutableStateOf(settings.favouriteWorkouts) }
    var lastWorkout by remember { mutableStateOf(settings.lastWorkout) }

    // The back button: out of a detail, then out of the training flow, then back to Home. The
    // on-screen back links call the same function, so the two cannot disagree.
    val current = training
    val goBack: () -> Unit = {
        when {
            openSession != null -> openSession = null
            openExercise != null -> openExercise = null
            showLicences -> showLicences = false
            tab == Tab.TRAIN && current != null -> {
                val back = backFrom(current)
                training = back
                // A finished guided workout goes home; anything else leaves to Workouts.
                if (back == null) tab = if (current is Destination.Guided) Tab.HOME else Tab.WORKOUTS
            }
            else -> tab = Tab.HOME
        }
    }
    BackHandler(enabled = openSession != null || openExercise != null || showLicences || tab != Tab.HOME, onBack = goBack)

    val live = tab == Tab.TRAIN && isLive(current)
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
                        weekGoal = weekGoal,
                        lastSession = last,
                        quickStart = last?.sets?.lastOrNull()?.exerciseId?.let { id -> ExerciseLibrary.all.firstOrNull { it.id == id } },
                        onQuickStart = { startTraining(it) },
                        onBrowse = { tab = Tab.WORKOUTS },
                        onOpenSession = { openSession = it },
                        lastWorkout = lastWorkout,
                        onRepeatWorkout = { openTraining(Destination.Plan(it)) },
                    )
                }

                Tab.WORKOUTS -> ExerciseSelectionScreen(
                    calibratedIds = baselines.keys,
                    onSelect = { startTraining(it) },
                    header = {
                        WorkoutPlansCard(
                            favourites = favourites,
                            onBuild = { openTraining(Destination.Builder(lastWorkout)) },
                            onOpen = { openTraining(Destination.Plan(it)) },
                        )
                    },
                )

                Tab.TRAIN -> TrainPage(
                    destination = current,
                    history = sessions,
                    restSeconds = duringSet.restSeconds,
                    workouts = WorkoutCallbacks(
                        favourites = favourites,
                        lastWorkout = lastWorkout,
                        onFavourite = { request ->
                            settings.favouriteWorkouts =
                                if (request in favourites) favourites - request else listOf(request) + favourites
                            favourites = settings.favouriteWorkouts
                        },
                        onStarted = { request ->
                            settings.lastWorkout = request
                            lastWorkout = request
                        },
                        onFinished = {
                            training = null
                            tab = Tab.HOME
                        },
                    ),
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
                } else if (openExercise != null) {
                    val id = openExercise!!
                    val points = remember(sessions, id) { Trends.of(sessions, id, limit = 12) }
                    ExerciseTrendScreen(
                        exerciseId = id,
                        points = points,
                        summary = shown.perExercise.firstOrNull { it.exerciseId == id },
                        onBack = { openExercise = null },
                    )
                } else {
                    ProgressScreen(
                        summary = shown,
                        sessions = sessions,
                        calendar = calendar,
                        today = today,
                        achievements = achievements,
                        onOpenSession = { openSession = it },
                        onOpenExercise = { openExercise = it },
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
                        reminder = reminder,
                        onToggleReminders = toggleReminders,
                        onReminderMinute = { applyReminder(reminder.copy(minuteOfDay = it)) },
                        onReminderWeekdays = { applyReminder(reminder.copy(weekdays = it)) },
                        duringSet = duringSet,
                        onDuringSet = { state ->
                            settings.repTone = state.tone
                            settings.repVibrate = state.vibrate
                            settings.spokenCounts = state.speak
                            settings.restSeconds = state.restSeconds
                            duringSet = state.copy(restSeconds = settings.restSeconds)
                        },
                        goalDays = goalDays,
                        onGoalDays = {
                            settings.weeklyGoalDays = it
                            goalDays = settings.weeklyGoalDays
                            reloadToken++
                        },
                        experimentalVerifier = experimentalVerifier,
                        onExperimentalVerifier = {
                            settings.experimentalVerifier = it
                            experimentalVerifier = it
                        },
                    )
                }
            }
        }
        if (!live) {
            BottomBar(tab) { selected ->
                if (selected == tab) {
                    // A second tap on the current page returns to its top.
                    openSession = null
                    openExercise = null
                    showLicences = false
                } else {
                    openSession = null
                    openExercise = null
                    showLicences = false
                    tab = selected
                    reloadToken++
                }
            }
        }
    }
}

/** What the Train flow needs from the shell for workouts - P3-50. */
private class WorkoutCallbacks(
    val favourites: List<PlanRequest>,
    val lastWorkout: PlanRequest?,
    val onFavourite: (PlanRequest) -> Unit,
    val onStarted: (PlanRequest) -> Unit,
    val onFinished: () -> Unit,
)

/**
 * A guided workout - DECISIONS.md P3-50: prepare, the live set (the same pipeline and saving as
 * any set), rest, the next; a summary at the end. The sequence is the core's
 * (`GuidedWorkout.steps`), and so are the summary's totals and records.
 */
@Composable
private fun GuidedFlow(
    current: Destination.Guided,
    repository: SessionRepository,
    onNavigate: (Destination) -> Unit,
    onChanged: () -> Unit,
    history: List<SessionRecord>,
    onFinished: () -> Unit,
    baselines: Map<String, CalibrationBaseline>,
) {
    val steps = remember(current.plan) { GuidedWorkout.steps(current.plan) }
    val step = steps[current.step]
    when (current.phase) {
        GuidedPhase.PREPARE -> GuidedPrepareScreen(step, current.step + 1, steps.size) {
            onNavigate(current.copy(phase = GuidedPhase.LIVE))
        }

        GuidedPhase.LIVE -> LiveDestination(
            profile = WorkoutCatalogue.profileOf(step.item.exerciseId).let {
                PersonalStartZone.countingProfile(it, baselines[CalibrationKey.of(it)], calibrating = false)
            },
            repository = repository,
            onSessionSaved = onChanged,
            onSetEnded = { session ->
                onChanged()
                val done = if (session != null) current.done + session else current.done
                onNavigate(
                    if (step.isLastStep) {
                        current.copy(phase = GuidedPhase.SUMMARY, done = done)
                    } else {
                        current.copy(step = current.step + 1, phase = GuidedPhase.REST, done = done)
                    },
                )
            },
            modeLabel = "Set " + step.setNumber + " of " + step.item.sets + " · " + stepTarget(step),
        )

        GuidedPhase.REST -> {
            val previous = steps[current.step - 1]
            val sameExercise = previous.item.exerciseId == step.item.exerciseId
            GuidedRestScreen(step, previous.item.restSeconds, sameExercise) {
                onNavigate(current.copy(phase = if (sameExercise) GuidedPhase.LIVE else GuidedPhase.PREPARE))
            }
        }

        GuidedPhase.SUMMARY -> {
            val endedAt = remember(current) { System.currentTimeMillis() }
            val summary = remember(current) {
                Dashboard.summarise(current.done, LocalDay.of(endedAt.toDouble(), utcOffsetMinutesAt(endedAt)))
            }
            val records = remember(current, history) {
                val ids = current.done.map { it.sessionId }.toSet()
                Records.newIn(history.filter { it.sessionId !in ids }, current.done).map { it.displayText }
            }
            GuidedSummaryScreen(
                sets = current.done.sumOf { it.sets.size },
                repetitions = summary.totalValidReps,
                heldMs = summary.totalHoldMs,
                durationMs = endedAt - current.startedAtMs,
                records = records,
                onDone = onFinished,
            )
        }
    }
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
    history: List<SessionRecord>,
    restSeconds: Int,
    workouts: WorkoutCallbacks,
) {
    // The old text bar kept every training screen clear of the status bar; with the bar gone, the
    // page does it. Edge-to-edge is enforced at targetSdk 35, so the background goes under the clock.
    Box(
        Modifier
            .fillMaxSize()
            .background(if (isLive(destination)) Void else MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column {
            // A visible way back on every training screen but the live one, whose way out is
            // ending the set.
            val finished = destination is Destination.Guided && destination.phase == GuidedPhase.SUMMARY
            if (destination != null && !isLive(destination) && !finished) {
                BackLink(backLabel(destination), onBack)
            }
            Box(Modifier.weight(1f)) {
                TrainFlow(destination, repository, calibration, uid, baselines, onChoose, onNavigate, onChanged, history, restSeconds, workouts)
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
    /** Every stored session, for the records a finished set sets (P3-49). */
    history: List<SessionRecord>,
    restSeconds: Int,
    workouts: WorkoutCallbacks,
) {
    when (val current = destination) {
        null -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageTitle("Train", "A single exercise, or a whole workout")
            KCard {
                Note("Each exercise shows how to place the phone, and lets you calibrate it to your own movement first.")
                Spacer(Modifier.padding(top = 12.dp))
                PrimaryButton("Choose an exercise", onClick = onChoose)
                Spacer(Modifier.padding(top = 8.dp))
                SecondaryButton("Build a workout", onClick = { onNavigate(Destination.Builder(workouts.lastWorkout)) })
            }
        }

        is Destination.Builder -> WorkoutBuilderScreen(current.initial) { minutes, focus, level, equipment ->
            onNavigate(Destination.Plan(PlanRequest(minutes, focus, level, equipment, seed = Random.nextInt())))
        }

        is Destination.Plan -> {
            val plan = remember(current.request) { WorkoutGenerator.generate(current.request) }
            PlanScreen(
                plan = plan,
                favourite = current.request in workouts.favourites,
                onShuffle = { onNavigate(Destination.Plan(current.request.copy(seed = Random.nextInt()))) },
                onLeaveOut = { id -> onNavigate(Destination.Plan(current.request.copy(avoid = current.request.avoid + id))) },
                onFavourite = { workouts.onFavourite(current.request) },
                onStart = {
                    workouts.onStarted(current.request)
                    onNavigate(Destination.Guided(plan, 0, GuidedPhase.PREPARE, emptyList(), System.currentTimeMillis()))
                },
            )
        }

        is Destination.Guided -> GuidedFlow(current, repository, onNavigate, onChanged, history, workouts.onFinished, baselines)

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
            // DECISIONS.md P3-82: a calibrated push-up counts from the user's own start position.
            profile = PersonalStartZone.countingProfile(current.profile, baselines[CalibrationKey.of(current.profile)], current.calibrating),
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
            // Against everything before this set: the set itself is excluded by id, whether or
            // not the history has been re-read since it was saved.
            val records = remember(current, history) {
                Records.newIn(history.filter { it.sessionId != current.session.sessionId }, current.session)
                    .map { it.displayText }
            }
            // The experimental exercise check, only when turned on in Profile - DECISIONS.md P3-85.
            val appSettings = LocalContext.current.let { remember(it) { AppSettings(it) } }
            val verifierNote = remember(current) {
                if (!appSettings.experimentalVerifier) {
                    null
                } else {
                    (current.session.sets.singleOrNull() as? RepetitionSetRecord)
                        ?.let { VerifierSetCheck.summarise(it.exerciseId, it.reps) }
                }
            }
            SetSummaryScreen(
                profile = current.profile,
                session = current.session,
                baselineTitle = baseline?.let { displayNameOf(it.calibrationId).uppercase() },
                results = results,
                records = records,
                restSeconds = restSeconds,
                onDone = { onNavigate(Destination.Exercise(current.profile)) },
                verifierNote = verifierNote,
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
        clipData = ClipData.newRawUri("Chiron export", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Export Chiron data")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** A calibration id's display name, from the library. A revised key (P3-46) names its exercise. */
private fun displayNameOf(calibrationId: String): String {
    val exerciseId = CalibrationKey.exerciseIdOf(calibrationId)
    return ExerciseLibrary.all.firstOrNull { it.id == exerciseId }?.displayName ?: calibrationId
}
