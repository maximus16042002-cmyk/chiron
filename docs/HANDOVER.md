# Kinesis — Complete Handover

*Written 2026-10-04, at the end of DECISIONS.md P3-51 (commit `f180bc8` on `master`). For any AI
assistant or developer picking this project up without the history of the sessions that built it.*

**This file is a guide, not an authority.** Where it disagrees with `docs/DECISIONS.md`, the
decision record is right and this file is out of date. Read section 0 before touching anything.

---

## 0. Read this first

### 0.1 What to read, in what order

1. **`CLAUDE.md`** (repository root): the engineering rules. Every rule in it is binding; most are
   restated in section 2 below.
2. **This file:** the whole story and the to-do list.
3. **`docs/DECISIONS.md`:** the dated decision record, P0-1 to P3-51, about 11,500 lines. It is the
   **highest authority**. When you need detail on anything below, search it for the entry number.
4. **`docs/PROJECT_STATUS.md`:** a status summary. Some parts are stale; section 11.3 lists them.
5. **`docs/CONTRACTS.md`:** type-level contracts. **`docs/FEATURE_PLAN.md`:** the app feature
   batches. **`docs/EXERCISE_GUIDE.md`:** how each exercise is counted. **`docs/P4_TOMORROW.md`:**
   the owner's phone test script.
6. Older plans: `docs/ZERO_DATASET_BUILD_PLAN.md` supersedes `docs/FINAL_ARCHITECTURE.md`, and
   FINAL_ARCHITECTURE still contains voided instructions (DECISIONS P0-8 lists them). **Never read
   the two `.docx` files at the root**; they are originals kept for provenance.

### 0.2 Document precedence (from CLAUDE.md)

`DECISIONS.md` > `ZERO_DATASET_BUILD_PLAN.md` > `FINAL_ARCHITECTURE.md` > `CLAUDE.md` (an
operational distillation) > everything else.

### 0.3 How work is done here (the pattern every session followed)

1. **Read** the relevant DECISIONS entries before changing anything.
2. **Write a design entry first** (next number, currently **P3-52**). It states what will be built
   and every choice the source documents leave open. Commit it before any code. For studies, the
   protocol and pass rule are fixed and committed (sometimes tagged, e.g. `p3-42-frozen`) **before**
   any data is looked at.
3. **Write the tests first** (shared-core tests run on JVM and JS).
4. **Implement.** All analysis lives in the shared core.
5. **Mutation-check the new guard tests:** plant the wrong rule, confirm a test fails, restore the
   file byte for byte.
6. **Run the full suite:** `gradlew assemble allTests :app:testDebugUnitTest :app:testReleaseUnitTest`.
7. **Append a "Result" section** to the entry, with the measured numbers and anything not done.
8. **Commit** (one coherent step per commit) and **push**.

The motto (CLAUDE.md §23): **MEASURE → TEST → DOCUMENT → THEN CLAIM.** Never state a number that
was not measured on this system.

---

## 1. The project in brief

| | |
|---|---|
| **What** | Kinesis: a camera-based personal movement-analysis app for Android. The phone camera watches you exercise. MediaPipe Pose turns each frame into 33 body landmarks. A shared Kotlin engine counts repetitions, times holds, and measures range of motion, tempo and left/right symmetry. |
| **Why** | An MSc project. The research question: *do personal baselines outperform population thresholds for flagging movement deviation?* |
| **What it is not** | **Not a medical or diagnostic system.** Never describe or build it as one. Feedback is "form feedback", never "diagnosis", "injury" or "correct posture". |
| **Owner** | Nitish Amritraj B (the project owner, an MSc student). They delegate heavily ("do whatever you want… I just want the app perfect and complete") but keep certain decisions (section 8.2). |
| **Repository** | `E:\kinesis` on the owner's Windows 11 PC. Remote: private GitHub `maximus16042002-cmyk/chiron`, branch `master`. 113 commits as of `f180bc8`. |
| **Phone** | The owner's Nothing Phone (2a), model A142, Android 16, used over USB with `adb`. |
| **Dataset** | MM-Fit (public): RGB video of 10 people doing 10 exercises, with set and repetition labels. Kinesis runs MediaPipe over the videos itself. **No self-collected or self-labelled data exists or may be created.** |
| **Time span** | P0 began 2026-09-19; this handover is 2026-10-04. The owner said not to worry about the due date. |

---

## 2. The rules (non-negotiable; from CLAUDE.md and DECISIONS)

### 2.1 Architecture

- **Platform:** Android (Kotlin, Jetpack Compose, CameraX, MediaPipe Pose).
- **Shared core:** Kotlin Multiplatform `commonMain`, with JVM, JS and Android targets. **All analysis
  logic lives in the shared core.** Platform code only does camera acquisition, the pose adapter,
  mirroring, rendering/UI and storage.
- **The JS target must stay green.** It carries the JVM/JS parity study. The React PWA is **cut**
  (P0-2). Never rewrite the engine in TypeScript.
- **Numbers:** `Double` throughout the core; no `Float`, and no `Long` in analysis paths that JS
  runs. Discrete outputs match exactly across platforms; continuous ones within about 1e-9
  relative.

### 2.2 Left and right

- **Never average left and right silently, anywhere.** Use `BilateralSignal(left, right)`. Any
  reduction to one number is an explicitly named function. Permanent guard:
  `SharedCoreSourceGuardTest` scans every production source for averaging.
- Front-camera mirroring is undone in **one place**, the platform `PoseAdapter`. The core always
  receives anatomical left/right. There are permanent mirroring regression tests.

### 2.3 Real time

- The per-frame path (landmarks → normalisation → validity → kinematics → ring buffers → FSM or
  hold state) is **O(1) per frame**, with no per-frame allocation (enforced by `RealTimeBudgetTest`
  and the source guard's file classification).
- No ML per frame. Heavy work happens only after a completed repetition or hold.
- Compose must not recompose per frame. The skeleton is drawn on a plain Android View/Canvas.

### 2.4 Claim levels

- **Level 1** (measurement), **Level 2** (personal deviation against your own baseline), **Level 3**
  (technique judgement).
- **Tier B and Tier C profiles cannot construct a Level 3 finding.** This is enforced by types: a
  `TechniqueAuthority` capability token that only Tier A profiles hold (P1-3).
- Every Level 3 (Layer 2) technique rule carries `sourceRef` (a cited published source, quoted) and
  `ruleVersion`. **A rule with no source does not ship. No accuracy figure may be attached to any
  technique rule**: there are no fault labels, so no such number exists.

### 2.5 ML scope

- Only: (1) an exercise **verifier** that flags a mismatch with the chosen exercise (never
  auto-selects); (2) **personal anomaly detection** (Layer 4), fitted per user on the device.
- Supervised technique-quality classifiers (Layer 3) are **removed from scope**.
- Prefer deterministic statistics everywhere. Never add ML to look like an AI project.

### 2.6 Privacy and offline (CLAUDE.md §16, amended by P3-14)

- All analysis on the phone. Recording works with no network.
- **The one permitted network use:** Firebase Auth (email/password) and Cloud Firestore, to sync
  finished `SessionRecord`s to the user's account. The UI discloses this.
- No analytics, crash reporting or telemetry. MediaPipe's Google telemetry backend is excluded
  (P3-13).
- No video is stored. Calibration baselines stay on the phone and do not sync.
- `OfflineManifestGuardTest` must keep failing on any network capability other than the declared
  Firebase artifacts.

### 2.7 Dependencies

Before adding any dependency, run the **§17 check** and record it in DECISIONS: why it's needed,
whether something existing covers it, licence (code and model weights separately), platform
compatibility, size, offline impact, maintenance. Recent work avoided new dependencies entirely:
navigation, charts and settings are all hand-rolled.

### 2.8 Evaluation integrity

- MM-Fit is split **by subject**. **Development:** subjects A, B, C and w19's subject, in workouts
  w00–w06 and w19. **Held-out:** w12, w13, w16, w17, w18, w20 (six unseen people).
  `tools/mmfit-eval/subjects.py` holds the split.
- **Held-out data is used only with the owner's explicit approval,** once per pre-registered test,
  with the pass rule frozen and committed first (tags `p3-42-frozen`, `p3-46-frozen`).
- MM-Fit's bundled OpenPose skeletons are never fed to the engine. The pipeline is MM-Fit video →
  MediaPipe → Kinesis.
- Squats are excluded from MM-Fit comparisons: MM-Fit films them front-on, and the profile is
  side-on.

### 2.9 The owner's standing instructions (from sessions; obey them)

- **Screenshots and recordings of the owner never go into git.** They are saved only under
  `local/phase-b/` (gitignored) and described in words in DECISIONS (P3-19).
- **The phone is for automated tests over USB.** **Never run `connectedAndroidTest` on the owner's
  phone:** it uninstalled the app and its data once (P3-23).
- **Before installing a build on the phone:**
  1. back up the database with `adb shell run-as com.kinesis …` (copy every file in `databases/`);
  2. install with `adb install -r` (keeps data);
  3. record and verify the APK's SHA-256;
  4. open the app with `am start`.
- Never force-stop the app, put the phone to sleep, or poll its thermal state without asking. The
  owner rejected that once.
- `google-services.json` is deliberately committed (P3-20; the repository is private).
- When stopping background processes on the PC, stop only processes identified by PID (for
  example the Kotlin compile daemon). Never stop every `java.exe`: Android Studio may be running.

---

## 3. Architecture and code map

### 3.1 Modules

| Module | Contents |
|---|---|
| `:core:domain` | Types only. `bilateral/BilateralSignal`, `finding/ClaimLevel`+`Finding`, `landmark/*`, `profile/ExerciseProfile` (tiers, `CameraView`, `Posture`), `rep/RepRecord`+`FormMeasures`+`MovementPhase`, `session/SessionRecord`+`LocalDay`, `calibration/CalibrationBaseline`, `analysis/AnalysisResult`. |
| `:core:analysis` | The engine (`commonMain`); see 3.2. `KinesisEngine.kt` is the only `@JsExport` surface (the JS facade). It lives in `commonMain` (P3-19), so the JVM runs the same facade the parity tests compare. Tests are in `commonTest` (JVM and JS) and `jvmTest` (source guards, allocation tests). |
| `:app` | The Android app, `com.kinesis` (section 3.3). |
| `:desktop` | Empty placeholder (a possible replay-only client). |
| `tools/mmfit-eval` | The MM-Fit evaluation harness: Kotlin (runs the app's own live pipeline over landmark files) plus Python scripts for alignment and statistics. See its README. |
| `tools/g1-spike` | P0's proof that the engine gives matching results on JVM and JS. |
| `tools/firestore-rules` | Firestore security rules work (P3-14). `firestore.rules` is at the root. |

### 3.2 The shared engine (`core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/`)

| Package | What |
|---|---|
| `landmark/` | `LandmarkNormalizer`, `ValidityGate` (per-frame: reliable / marginal / missing; blocks analysis on bad frames). |
| `geometry/`, `kinematics/` | Joint angles; `BodyPoint`, `DrivingSignal` (a joint angle or a segment inclination, e.g. `SegmentInclination.TRUNK` for the sit-up), landmark velocity. |
| `temporal/` | `RealTimeSignalPipeline`: median outlier gate → EMA smoother (weighted per unit time since P3-33) → ring buffer. Gap interpolation and resampling to 64 points after a repetition. |
| `rep/` | `RepFsm` (IDLE → TOP → DESCENDING → BOTTOM → ASCENDING, with hysteresis, min/max durations, explicit ABORTED / INCOMPLETE / INSUFFICIENT_DATA outcomes), `BilateralRepTracker` (one FSM per side; per-limb vs simultaneous combination; near-side-only rules), `FormTracker` (per-frame ring buffers of other joints, reduced per repetition into `FormMeasures`, P3-44), `BilateralPhase`. |
| `hold/` | `HoldAnalyser`, `HoldReading`: isometric holds, never through the repetition FSM. |
| `live/` | `LiveRepetitionAnalysis`, `LiveHoldAnalysis`: the per-frame entry points the app calls. |
| `profile/` | `ExerciseLibrary` (the 17 entries), `ArchetypeProfiles` (squat, curl, plank), `TierBProfiles`, `TierCProfiles`, `ExerciseVariant` (variants delegate to their parent), `ExerciseKinds` (`RepSignalSpec`, `RepetitionSemantics`, `AnalysedSides`). |
| `calibration/` | P6: `Calibration` (`CalibrationKey`, with a revision for the sit-up's trunk driver), `CalibrationPolicy` (5–8 valid repetitions per side, provisional), `RobustStatistics`, `PostRepAnalysis` (numbers beside the baseline), `CurveDistance`, `CalibrationGuide` (calibration instructions and the "Setting up" placement text), `BaselineJson`. |
| `deviation/` | P8 Layer 1: `DeviationRules`, `DeviationPolicy`, `RepFeatures`, `References`, `AcsmTempoRule`, `PersonalStartZone`. **Built and tested, not wired into the app** (it failed its held-out test; P3-42). |
| `technique/` | P8 Layer 2: `TechniqueRules`, six cited rules (P3-43, P3-44). **Built, not wired**; they wait for the phone's side-on checks. |
| `anomaly/` | P9 Layer 4: `IsolationForest`, `RobustDistance`, `PersonalAnomalyModel`, `AnomalyFeatures`, `SplitMix64`. **Built, does not ship** (P3-38). |
| `export/` | `SessionExport`: JSON and CSV, export `formatVersion` 2, golden-tested byte for byte on JVM and JS. |
| `history/` | `Dashboard` (totals, per-exercise summary, records fields), `Streaks` (streak, freezes, `activity` for the calendar), `Records`, `WeeklyGoal`, `Achievements`, `Reminders` (reminder text), `Trends` (chart points). |
| `workout/` | `WorkoutGenerator`: the catalogue, plan generation, `GuidedWorkout` steps (P3-50). |

### 3.3 The Android app (`app/src/main/java/com/kinesis/`)

| Package / file | What |
|---|---|
| `MainActivity.kt` | The shell (hand-rolled navigation, no library). `KinesisRoot` runs onboarding, then the auth gate. `KinesisApp` holds the five tabs, the `Destination` sealed interface for the Train flow (Exercise, CalibrationIntro, Live, CalibrationResult, SetSummary, Builder, Plan, Guided), `backFrom`/`goBack`, `BackHandler`, `TrainPage`/`TrainFlow`/`GuidedFlow`, `LicencesPage`. |
| `camera/` | `LiveCamera` (CameraX, a portrait 4:3 frame), `PoseLandmarkerSource` (MediaPipe), permission, orientation, model asset. |
| `pose/PoseAdapter.kt` | Camera coordinates to anatomical left/right, mirroring, and aspect correction. |
| `ui/live/` | `LiveHost` (sessions, saving, failures), bridges/engines, `SkeletonOverlayView` (Canvas, four-state colour-blind-aware palette), `SkeletonColouring`/`Smoothing`, `RepFeedback` + `RepCountWatcher` (tone, vibration, spoken count, P3-49). |
| `ui/screens/` | `Components` (`KCard`, buttons, `BottomBar`, `Tab`, `BackLink`, `viewLabel`/`postureLabel`), `HomeScreen`, `Screens` (Workouts list, licences, Health Connect panel, live layouts), `CalibrationScreens` (exercise page, calibration, set summary), `ProgressScreens`, `CalendarCard`, `TrendCharts`, `ProfileScreen`, `ReminderCard`, `RestTimerCard`, `WorkoutScreens` (builder, plan, guided), `OnboardingScreen`, `AuthScreen`. |
| `ui/theme/KinesisTheme.kt` | `KinesisPalette` tokens: canvas `#0E0F12`, card `#1A1C21`, raised `#23262D`, ink `#F2F3F5`, ink secondary `#A0A4AD`, ink faint `#8F939C`, accent `#FF6B35`, attention amber `#FFB000`, chart series left `#3987E5` and right `#199E70`. Sans-serif type with tabular figures. |
| `storage/` | Room: `KinesisDatabase` (**version 3**, `MIGRATION_2_3` adds `reps.form`), entities and mapping, `SessionRecorder`, `SessionRepository`, baseline storage, `utcOffsetMinutesAt`. |
| `sync/` | Firebase: `AuthGateway`, `FirebaseSync`, `SessionDocument` (Firestore mapping), `SessionMerge`, `SessionSyncWorker` (WorkManager, retry), `SyncingSessionRepository` (local first, then sync). |
| `health/` | Health Connect: steps, active calories, mean heart rate, read-only and shown beside training; every absence is named. |
| `calibration/CalibrationCoordinator.kt` | Runs the core's calibrator and stores baselines on the phone. |
| `export/ExportFiles.kt` | Writes the export and hands it to the share sheet. |
| `reminders/` | `ReminderTiming` (next time, DST-safe), `ReminderWork.kt` (`ReminderScheduler`, `ReminderWorker`, `ReminderNotification`). |
| `settings/` | `AppSettings` (SharedPreferences file `kinesis`: onboarding flag, weekly goal, reminders, rest length, repetition cues, last and favourite workouts), `PlanRequestCodec`. |
| `licenses/ThirdPartyNotices.kt` | Licence texts shown in-app: Apache 2.0, plus BSD notices for libyuv and Protocol Buffers. |
| `diagnostics/PhaseBProbe.kt` | Debug-only live readout (Logcat tag `KinesisPhaseB`). |

### 3.4 Data flow

**Live (per frame):** CameraX frame → MediaPipe → `PoseAdapter` (anatomical L/R, aspect) →
`LiveRepetitionAnalysis` / `LiveHoldAnalysis` (validity → angles → filters → FSM and form tracker) →
UI state published a few times a second, plus the skeleton overlay drawn directly from the pose
callback.

**After a set:** `RepRecord`s / `HoldRecord`s → `SessionRecord` → Room (the source of truth) →
WorkManager sync to Firestore if signed in. One live set is one `SessionRecord`.

**Interpretation is kept separate:** `AnalysisResult` (numbers beside the baseline) is computed from
records and the baseline, never merged into the record, so history can be re-analysed when rules
change.

### 3.5 Stored formats

| Format | Version | Notes |
|---|---|---|
| Room database | **3** | Live on the owner's phone since P3-47 (79 sessions migrated intact). |
| Export | `formatVersion` **2** | Adds the per-repetition `form` (P3-44). |
| Firestore | — | Writes `form` only when present. |
| Rule versions | `p8-layer2-1`, `p8-layer2-2` | — |

---

## 4. The exercise library

**Count it honestly:** *15 exercise profiles across 3 tiers and 2 analysis modes, presented as 17
library entries including 2 variants that share a parent profile and calibration.* Never say "17
exercises".

| id | Name | Tier | Camera | Posture | Mode | Counting |
|---|---|---|---|---|---|---|
| `squat` | Squat | A | side-on | standing | reps | both sides together |
| `pushup` | Push-up | A | side-on | prone | reps | near side only (P3-28) |
| `bicep_curl` | Bicep Curl | A | **side-on** (P3-32) | standing | reps | **one arm = one rep** (P3-1), per limb; turn round for the other arm |
| `lateral_raise` | Lateral Raise | A | front-on | **seated** (P0-4) | reps | both together; start zone ≤ 36° (P3-29) |
| `glute_bridge` | Glute Bridge | A | side-on | supine | reps | both together |
| `shoulder_press` | Shoulder Press | B | front-on | **seated** | reps | both together |
| `row` | Row | B | side-on | hinged | reps | near side only |
| `lunge` | Lunge | B | side-on | standing | reps | both together |
| `hip_abduction` | Hip Abduction | B | front-on | standing | reps | per limb |
| `knee_raise` | Knee Raise | B | side-on | standing | reps | per limb |
| `situp` | Sit-up | B | side-on | supine | reps | near side; **driven by trunk inclination** since P3-46 (top 11.9°, bottom 75.0°, hysteresis 8°) |
| `leg_raise` | Leg Raise | B | side-on | supine | reps | both together |
| `wall_sit` | Wall Sit | C | side-on | seated | hold | — |
| `plank` | Plank | C | side-on | prone | hold | — |
| `side_plank` | Side Plank | C | front-on | side-lying | hold | — |
| `goblet_squat` | Goblet Squat | variant of squat | | | | inherits everything |
| `reverse_lunge` | Reverse Lunge | variant of lunge | | | | inherits everything |

- **Front Raise** was cut (FINAL_ARCHITECTURE §5; ratified P3-19 §9).
- **Under schedule pressure,** Side Plank and Hip Abduction may be dropped, giving 15 entries and
  13 profiles. That is a fallback, not the goal.

---

## 5. What happened: the full timeline

Each line is one DECISIONS.md entry. Search for the entry number for its detail.

### Phase 0: architecture lock (2026-09-19)

- **P0-1** The repository was empty, and not a git repo; the `.docx` plans were converted to markdown.
- **P0-2** Keep the Kotlin/JS target (for the parity study); cut the React PWA.
- **P0-3** Glute Bridge stays Tier A.
- **P0-4** Lateral Raise and Shoulder Press are seated, matching MM-Fit.
- **P0-5** The target is 17 entries / 15 profiles / 2 variants.
- **P0-6** Layer 2 is unvalidated (literature thresholds only); P8 module 8.6, "rule validation
  against labels", is deleted.
- **P0-7** Tier A now means "cited technique rules exist", not "validated".
- **P0-8** Lists the superseded instructions still in FINAL_ARCHITECTURE: subject recruitment,
  Layer 3, gate G3. Do not act on them.
- **P0-9** Module structure adopted. An incident: Android Studio overwrote the root Gradle config
  mid-session, and it was rebuilt.

### Phase 1: shared core types

- **P1-1** Android Lint fails on JDK 25 (environmental).
- **P1-2** Where each type lives.
- **P1-3** Claim-level enforcement by a `TechniqueAuthority` capability token.
- **P1-4** Provisional constants (visibility 0.70 / 0.40).
- **P1-5** The front-camera mirroring assumption, stated.

### Phase 2: kinematics and temporal

- **P2-1** P2's scope.
- **P2-2** The old prototype at `E:\kinesis_old` was inspected read-only; it is reference only.
- **P2-3** `app/` is an instrumentation harness first.
- **P2-4** The ablation: smoothing **lag**, not noise, is the binding constraint.

### Phase 3 onward: engine, app, device, evaluation (P3-1 to P3-51)

**Engine and library:**
- **P3-1** MM-Fit counts curls **per arm**.
- **P3-2** The rep engine's design: one FSM per side, never a fused signal.
- **P3-5** Repetition semantics for push-up, lateral raise and glute bridge, measured from MM-Fit.
- **P3-6** The variant mechanism: delegation to the parent.
- **P3-7, P3-8** The Tier B profiles. The camera-view contract was enforced, and "near side only"
  introduced.
- **P3-9** Tier C holds. Their thresholds carry no citation, by design.

**Scope and the owner's early decisions:**
- **P3-3** The owner added a dashboard, streaks and Health Connect.
- **P3-4** The owner had all 15 profiles built before device validation. This consciously reversed
  CLAUDE.md's original order.

**The first app:**
- **P3-10** Streaks: no freeze at the time, later superseded by P3-49.
- **P3-11** The Health Connect §17 check.
- **P3-12** Compose UI, Room, and the phosphor-green terminal theme (the theme was superseded by
  P3-48).
- **P3-13** The CameraX and MediaPipe §17 checks; MediaPipe's telemetry backend excluded.
- **P3-14** **The one deliberate architecture change:** Firebase Auth and Firestore sync of session
  history.
- **P3-15, P3-16** The live screen wired end to end; frame-shape correction; holds; sets saved.
- **P3-17** A front/back camera switch, the model licence recorded (Apache 2.0), an outlier gate
  that re-admits a sustained change, and the debug readout.

**The device (Phase B):**
- **P3-18** The first run on the phone crashed on MediaPipe telemetry, and was fixed.
- **P3-19** Screenshots moved out of git; the overlay clipped to the camera box; JVM/JS golden
  parity tests built.
- **P3-20** The private GitHub remote.
- **P3-21** Owner: the camera session waits, and P6 goes first.
- **P3-22** **P6 calibration:** per-side baselines from 5–8 valid repetitions (provisional), stored on
  the phone only.
- **P3-23** The guide demo counted nothing. It was diagnosed from the phone's data, and the gap
  tolerance was built. (The `connectedAndroidTest` incident happened here.)
- **P3-24** A solo phone check: one set counted 15 of 15 curls.
- **P3-25** **P5 export:** JSON and CSV in the core, golden-tested, matching the phone's database
  exactly.
- **P3-26** Owner: "fast repetition" messages come after P7.

**MM-Fit evaluation (P7):**
- **P3-27** All 616 sets were run through the phone's own pipeline. It exposed a fast-start defect
  (curls lost 54%).
- **P3-28** The fast-start fix: held-out curls 368 of 368, every set exact. Push-ups and sit-ups
  count from the near side.
- **P3-29** The near-side rule refined. The lateral raise's start zone moved 28 → 36°: held-out
  raises 125 → 356 of 369 (70.3% of sets exact, 94.6% within ±1), and no set counted over its label.
- **P3-30** The by-subject split. **P8 Layer 1** built (personal and population comparators, the
  ACSM tempo rule, a personal start zone). The personalisation study S1–S4 run on the development
  subjects.

**Back on the phone, and fixes:**
- **P3-31** The phone session of 2026-09-29. Curls filmed sideways: 16 of 16 and 15 of 15. Facing
  the phone, curls counted false repetitions, so the curl went side-on.
- **P3-32** The curl filmed side-on; an `RepFsm` fix for a movement crossing the whole zone between
  two results.
- **P3-33** The smoother weighted per unit of time. At the phone's 15 results a second: lateral raise
  351 → 388 of 409, rows 235 → 332, push-ups 62 → 88.

**ML (P9):**
- **P3-34** P9 brought into scope; designed before code.
- **P3-35** Layer 4: an Isolation Forest in the core.
- **P3-36** A robust covariance distance added to the comparison.
- **P3-37** The anomaly study: the forest sees only planted stalls, which simple statistics see as
  often.
- **P3-38** **The owner delegated: Layer 4 does not ship** (a negative result). The order of work
  toward a complete app was set.

**Layer 1's fate:**
- **P3-39** Curl timing traced; the engine stays unchanged; behaviour pinned by tests.
- **P3-40** S5: one session's baseline misdescribes the same person's other sessions (range of
  motion 31% vs 15%; left–right 35% vs 4%). Tempo holds.
- **P3-41** Decision 3: no Layer 1 setting qualified.
- **P3-42** A smaller setting, tested once on held-out people, **failed** (false alarms on 3 of 15
  messages). **Layer 1 stays out of the app**, which shows measurements only.

**Layer 2 and the sit-up:**
- **P3-43** **Layer 2 step A:** three cited technique rules.
- **P3-44** Step B: `FormMeasures` per repetition (Room v3, export v2), and three more rules (section
  6.4).
- **P3-45, P3-46** **The sit-up switched to a trunk-angle driver.** Development: 413 of 451 (the hip
  angle got 150). Held-out: 146 of 189 (hip: 108). No set over its label. Adopted; old sit-up
  calibrations retired.
- **P3-47** The P3-46 build installed on the phone after a backup. Room v3 migrated on the owner's
  real data.

**The complete app** (built this session; **none of it yet seen on the phone**):
- **P3-48** FEATURE_PLAN Batch 1:
  - the new look (dark, rounded, orange accent, sans-serif; palette contrast-tested);
  - five tabs (Home, Workouts, Train, Progress, Profile), back-button handling, onboarding;
  - every screen restyled; a "Setting up" card per exercise, from the core.
- **P3-49** FEATURE_PLAN Batch 3:
  - **streak freezes** (the owner's rule: one earned every 7 consecutive active days, at most 2
    held; one missed day spends one; two in a row end the streak; derived from history, never
    stored, never refunded);
  - a month calendar;
  - records (per side), a weekly goal ring, six achievements;
  - local reminders;
  - a rest timer, and tone / vibration / spoken-count cues.
- **P3-50** FEATURE_PLAN Batch 4:
  - **a deterministic workout generator** in the core (time, focus, level, equipment, seed;
    grouped by camera view; pinned identical on JVM and JS);
  - **guided workouts** (prepare → live → rest → next → summary);
  - favourites, and "Repeat last workout".
- **P3-51** P10's charts: each exercise's page with **left and right range of motion as two
  lines**, counted-per-set columns, records, and a table view. The colours were validated for
  colour-vision deficiency.

---

## 6. Results so far (what has been measured)

### 6.1 Repetition counting on MM-Fit (P7, P3-27 to P3-33)

The pipeline: MM-Fit video → MediaPipe (the phone's own model file) → the app's live pipeline in
`tools/mmfit-eval`, compared set by set with MM-Fit's labels. 616 sets in all. Squats are excluded
(wrong view).

- **Bicep curl:** held-out 368 of 368, every set exact, after the P3-28 fast-start fix.
- **Lateral raise:** held-out 356 of 369 at 30 fps after P3-29, 70.3% of sets exact and 94.6% within
  ±1.
- **Shoulder press:** good from the start: 80% of sets exact across all workouts in P3-27, and
  86.1% exact held-out after P3-28.
- **Push-up, sit-up, row:** counted from the near side. They improved a great deal but remain weaker
  (e.g. held-out sit-ups 209 of 399 on the hip driver in P3-29). The sit-up later moved to the trunk
  driver (P3-46: held-out 146 of 189 on its comparison set).
- **The phone's 15 results a second** costs counts on some exercises; P3-33's time-weighted
  smoother recovered much of it.
- **The principle held throughout:** no change was accepted if any set counted **above** its label.

Full tables are in P3-27, P3-28, P3-29, P3-33 and P3-46. **Do not quote these as accuracy on other
cameras, views or people;** CLAUDE.md §22 forbids claiming an external benchmark validates a
different condition.

### 6.2 Personalisation study (P3-30, P3-40), development subjects only

- **S1 (variability):** people differ far more than one person varies. Lateral raise range of
  motion: people differ by about 21–23° (SD); one person's repetitions by about 4°. With four
  subjects, no ICC interval excludes zero.
- **S2 (false alarms):** at an equal operating point, the population comparator's false alarms
  concentrate on whoever sits far from the others (10 of 16 metrics worse for the worst-off
  subject), while the personal comparator's spread evenly. The personal `|z| > 2` rule flags too
  much (14–32%) with a 5–8 repetition baseline.
- **S3 (sensitivity, synthetic):** the personal comparator detects planted range and tempo changes
  far better than the population one (e.g. curl range cut 20%: 94.5% vs 25.2%).
- **S4 (convergence):** 5–8 repetitions do not settle a range-of-motion baseline; about 12–20 would.
  The calibration count is the owner's decision.
- **S5 (P3-40):** a baseline from one session often misdescribes the same person's other sessions,
  for range of motion and left–right balance; tempo holds.

### 6.3 Layer 1 (personal deviation messages)

Built in the core (P3-30). Decision 3 found no qualifying setting (P3-41). A smaller setting failed
its single held-out test (P3-42). **Not in the app.**

### 6.4 Layer 2 (cited technique rules, Tier A)

Six rules in `technique/TechniqueRules.kt`, each with a 20° tolerance (the app's own choice, not the
sources'):

| Rule | Exercise | Source |
|---|---|---|
| `raised_above_shoulder_level` | Lateral raise | S1: ACE Exercise Library, *Lateral Raise* |
| `curl_started_elbow_bent` | Bicep curl | S2: ACE, *Hammer Curl* |
| `pushup_arms_not_extended` | Push-up | S3: ACE, *Push-Ups* |
| `curl_upper_arm_moved_forward` | Bicep curl | S5: Smith A., NSCA Coach 7(4), a biceps curl checklist |
| `pushup_hips_sagged` / `pushup_hips_raised` | Push-up | S3, and S6: Cook, Burton & Hoogenboom 2006, N Am J Sports Phys Ther |
| `squat_trunk_ahead_of_shin` | Squat | S4: ACE, *Bodyweight Squat* |

- **Not wired into the app.** They wait for the side-on checks on the phone.
- The glute bridge has no rule (no fitting source). Squat depth is deliberately not a rule.

### 6.5 Layer 4 (personal anomaly detection)

Built (Isolation Forest plus a robust distance). The study (P3-37) found it adds nothing over simple
statistics, and the on-device rule would flag 7–30% of normal repetitions. **Does not ship**
(P3-38). It stands as a negative result for the report.

---

## 7. Where things stand

### 7.1 Phases (the build plan has P0–P12; P11 is cut)

| Phase | Status |
|---|---|
| P0 Architecture, P1 Shared core, P2 Kinematics, P3 Profiles & rep engine | ✅ Done |
| P4 Device reality check | 🟡 Partly done: tests 1–2 of `P4_TOMORROW.md` run (P3-31); tests 3–12 owed |
| P5 Records & export | ✅ Done |
| P6 Calibration | 🟡 Built. The only on-device baseline is from front-on curls; recalibration is owed |
| P7 MM-Fit pipeline | ✅ Done |
| P8 Form engine | 🟡 Layer 1 built but not shipping; Layer 2's six rules built, not wired (wait for the phone) |
| P9 ML | 🟡 Layer 4 built, not shipping (negative result); **the verifier (Part B) not started** |
| P10 Visualisation & Android app | 🟡 Nearly complete in code (P3-48 to P3-51); **unseen on the phone** |
| P11 Windows PWA | ✂️ Cut |
| P12 Evaluation & write-up | 🟡 Rep-count and personalisation studies partly done; the rest owed |

**A rough figure:** 6 done + 6 × ½ = about 75% of the build plan. The engineering is far ahead of
the thesis: the remaining work is mostly evaluation and the write-up.

### 7.2 Proven on the phone vs built only

- **On the phone, at some point:**
  - the live screen;
  - counting curls (side-on);
  - saving sets;
  - sync;
  - Health Connect;
  - the export;
  - the calibration flow (once);
  - the P3-46 build with Room v3.
- **Built and unit-tested only, never seen on a device:** everything in P3-48 to P3-51 (the new UI,
  calendar, freezes, records, goal, achievements, reminders and notifications, rest timer, cues and
  speech, the workout generator and guided mode, charts), plus the sit-up trunk driver's live
  behaviour.

### 7.3 Tests

- **The last full run:** **2,831 test executions, 0 failed, 0 skipped.**
- **How the count is made:** shared-core tests count once per target: JVM, JS, Android debug and
  release.
- **The command:** `gradlew assemble allTests :app:testDebugUnitTest :app:testReleaseUnitTest`, plus
  `:app:compileDebugAndroidTestKotlin`.
- **Every test is synthetic.** None has seen a camera frame or a model; device evidence is recorded
  in DECISIONS, entry by entry.

---

## 8. What has to be done next

### 8.1 Needs the phone (the owner connects it over USB)

1. **Install the latest build safely** (section 2.9):
   - back up `databases/` via `run-as`;
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`;
   - record the SHA-256;
   - `am start`.
2. **Screenshot every new page** into `local/phase-b/`, and describe each in words in DECISIONS
   under P3-48 to P3-51:
   - onboarding;
   - Home (streak, ring, last 7 days, repeat last workout);
   - Workouts (builder card);
   - Train;
   - plan, guided prepare / rest / summary;
   - Progress (calendar, achievements, exercise trend page);
   - Profile (goal, cues, reminders);
   - the restyled live screen.
3. **Check on the device what tests cannot:**
   - the trend chart renders without label collisions;
   - a reminder notification arrives, and the `POST_NOTIFICATIONS` prompt and refusal behave;
   - tone, vibration and spoken counts work, and speech uses an offline voice;
   - the rest timer's end cue;
   - a full guided workout end to end;
   - back-button paths;
   - onboarding once, then again from Profile.
4. **Resume `docs/P4_TOMORROW.md`:**
   - recalibrate the curl sideways (the existing baseline is from front-on data);
   - test 3, lateral raise (≤ 36° start zone);
   - test 4, push-ups side-on;
   - test 5, sit-ups side-on, now on the trunk driver;
   - test 6, plank against a stopwatch;
   - test 7, the book test (a short loss of tracking);
   - test 8, the "NOT VISIBLE" label;
   - test 9, back camera;
   - test 10, dim light;
   - test 11, a 10-minute heat run (cool the phone first);
   - test 12, squats (optional).
5. **The side-on checks for the six Layer 2 rules,** before any is shown in the app (P3-43, P3-44).
6. **The Firestore security rules,** against a real client: a user reaches only their own sessions,
   and update and delete are refused. PROJECT_STATUS §5 lists this as not yet done; confirm before
   relying on it.
7. **A second person,** for P4's device study.

### 8.2 Decisions only the owner can make (ask; do not decide)

- **The push-up personal start zone** (P3-29, P3-30): adopt or not.
- **The calibration count:** 5–8 now; S4 suggests about 12–20 (P3-30, P3-40, P3-42).
- **A low frame rate:** gate weighted by time, or warn when results fall below about 10 a second
  (P3-33).
- **The six curl features** in P3-30's Decision 3 rule (P3-32). This is probably moot while Layer 1
  is out.
- **Approval to start P9 Part B,** the exercise verifier. It needs the §17 check for scikit-learn (or
  an alternative), and the owner's go-ahead for the final leave-one-subject-out run over all ten
  people (P3-34).
- **Approval for any further use of held-out data.**
- **After the phone checks:** whether to wire the Layer 2 rules into the set summary.

### 8.3 Engineering work that needs no phone (in order)

1. **P9 Part B, the exercise verifier** (after approval). It is supervised, trained on MM-Fit and
   evaluated leave-one-subject-out. Features and model are chosen on the development people only; the
   frozen pipeline then runs once over all ten. It needs Python ↔ Kotlin numeric parity tests (CLAUDE.md
   §18) and consumes completed `RepRecord`s (including `FormMeasures`), never frames.
2. **Wiring Layer 2's rules** into the set summary as Level 3 findings, once the phone checks pass:
   - Tier A only;
   - each finding shows its source;
   - the wording comes from the core.
3. **P12, the evaluation studies** and the final report:
   - repetition detection vs MM-Fit (mostly measured; consolidate);
   - verifier accuracy;
   - personalisation (variance decomposition, population vs personal flag rates, calibration
     convergence);
   - device performance (P4);
   - pose reliability in deployment (P4);
   - JVM/JS parity as a study.

   The report's limitations must say: *these [Layer 2] rules implement thresholds from cited
   sources, and their agreement with expert human judgement was not measured in this work*. It
   must report Layer 1's held-out failure and Layer 4's negative result honestly.
4. **Deferred app items** (lowest priority, CLAUDE.md §21):
   - the skeleton redesign and a display-only focus zoom (FEATURE_PLAN Batch 1 additions);
   - a setup assistant that blocks scoring when framing is clearly wrong (CLAUDE.md §10, a SHOULD;
     the validity gate already blocks bad frames);
   - optionally, `abiFilters` to arm64 (saves about 29 MB of a ~56 MB APK).

### 8.4 Known limitations (recorded, not fixed)

- A live session holds exactly one set, so the "sessions" count counts sets (P3-16).
- A set is lost if the app is killed mid-set, and a save that fails after leaving the screen is
  not reported (P3-16).
- A set ended with the back button during a guided workout is saved, but not counted in that
  workout's summary (P3-50).
- The curl's timing has a known one-frame artefact at some frame rates (P3-30, P3-39). It is pinned
  by tests, not fixed.
- On-device frame rate is about 15 results a second, and the phone has run hot (thermal status 3 for
  half of the P3-31 session).

---

## 9. How to build, test and work with the phone (Windows specifics)

### 9.1 Environment

- **Windows 11.** The shell is Git Bash or PowerShell.
- **JDK:** Android Studio's JBR, `C:\Program Files\Android\Android Studio\jbr`. Set `JAVA_HOME` to
  it.
- **adb:** `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- **No emulator image is installed.** MediaPipe ships no x86_64 library, so an emulator cannot run
  the live screen anyway.
- **Versions:** Kotlin 2.1.21; AGP 8.7.3; Compose BOM 2024.12.01; compileSdk/targetSdk 35; minSdk 24;
  Room 2.7.1; WorkManager 2.10.0; CameraX 1.4.1; MediaPipe Tasks Vision 0.10.21; Firebase BoM
  33.7.0; Health Connect 1.0.0-alpha11.

### 9.2 Commands

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat :app:compileDebugKotlin                       # quick compile check
./gradlew.bat :core:analysis:jvmTest --tests "*Streak*"     # one core test class, JVM
./gradlew.bat :core:analysis:jsNodeTest                     # core tests on JS
./gradlew.bat assemble allTests :app:testDebugUnitTest :app:testReleaseUnitTest :app:compileDebugAndroidTestKotlin   # full suite
./gradlew.bat :app:assembleDebug                            # APK: app/build/outputs/apk/debug/app-debug.apk
```

Test results are XML under `*/build/test-results/<task>/`. Count executions by summing `tests=`
across the files.

### 9.3 Known build problems and their fixes

| Symptom | Fix |
|---|---|
| `FileSystemException … classes.jar … used by another process` | `./gradlew.bat --stop`, then rerun |
| Top-level functions suddenly "unresolved" | Build-cache artefact: rerun the compile with `--rerun --no-build-cache -Pkotlin.incremental=false` |
| Windows "paging file too small" or PowerShell failing to load | The Kotlin compile daemon has eaten memory. `gradlew --stop`, then find the `java.exe` whose command line contains `kotlin-compiler-embeddable` and stop **that PID only** |
| Android Lint fails | Known incompatibility with JDK 25 (P1-1). Lint is not part of the suite |
| Bash heredocs mangle `\n` inside generated Kotlin strings | Write scripts or files with an editor tool rather than heredocs |

### 9.4 Phone procedures

- **Backup first:**
  - `adb exec-out run-as com.kinesis cat databases/<file> > local/backup-<date>/<file>`, for every
    file in `databases/`;
  - check integrity on the copy.
- **Install:** `adb install -r <apk>`. Never uninstall, and never `connectedAndroidTest`.
- **Identify builds by SHA-256,** not install time (P3-31 found an unrecorded reinstall).
- **The debug readout:** `adb logcat -s KinesisPhaseB`.
- **Device files go to `local/`** (gitignored): screenshots in `local/phase-b/`, session folders
  like `local/p4-2026-09-29/`.

---

## 10. Where things are on disk

| Path | What | In git? |
|---|---|---|
| `docs/` | All the documents above; `docs/third_party/` holds the BlazePose model card PDF | yes |
| `docs/Chiron_Project_Report.docx` | The owner's own report file. Untracked; not read or edited by the AI sessions | no |
| `app/src/main/assets/pose_landmarker_lite.task` | The pose model (Apache 2.0) | yes |
| `app/google-services.json` | Firebase config, committed on purpose (P3-20) | yes |
| `data/` | MM-Fit and every study's outputs (`data/mm-fit/`, `data/p3-30-study/`, `data/p3-37-anomaly-study/`, `data/p3-40-s5/`, `data/mm-fit-eval-*`) | no (gitignored) |
| `local/` | Phone backups, screenshots, session notes, mutation logs (`local/p3-49-*.log`, `local/p3-50-*.log`), the full pose model for the debug switch | no (gitignored) |
| `tools/mmfit-eval/` | The evaluation harness. `README.md` explains how to rerun it | yes |
| Git tags | `p3-42-frozen`, `p3-46-frozen`: the frozen states before each held-out test | yes |
| `camera-session.log`, `demo.log` | Old untracked logs at the root | no |

---

## 11. Things a newcomer gets wrong

### 11.1 Traps

- **"17 exercises":** wrong; see section 4.
- **Averaging left and right "just for display":** forbidden. Use two values, or a named reducer
  with a test.
- **Putting a threshold or a sentence about movement in UI code:** forbidden. Words about movement
  come from the core (`CalibrationGuide`, `Records.NewRecord.displayText`, `Reminders.message`).
- **Adding a library for navigation, charts or settings:** not needed; each was built without one.
  Any new dependency needs the §17 check written into DECISIONS.
- **Quoting MM-Fit counting results as "accuracy" for the phone:** a different camera, people and
  frame rate.
- **Using held-out workouts to tune anything:** never, without the owner's approval and a frozen
  protocol.
- **Showing Layer 1, Layer 2 or Layer 4 output in the app:** not without the conditions in section 8.

### 11.2 Glossary

| Term | Meaning |
|---|---|
| **Active day / qualifying day** | A local day on which at least one repetition or hold *completed* (VALID). Drives streaks, the goal, the calendar and reminders. |
| **Calibration / baseline** | One ordinary set; its valid repetitions become per-side medians and spreads, stored on the phone. |
| **Near side only** | Exercises filmed side-on where only the side nearer the camera is reliable (push-up, row, sit-up). |
| **Per limb** | Each arm or leg counts its own repetitions (curl, knee raise, hip abduction). |
| **Phase B** | The device reality check (P4) on the owner's phone. |
| **Development / held-out** | MM-Fit split by subject (section 2.8). |
| **Tier A/B/C** | What an exercise may claim: A up to Level 3 (cited rules), B and C up to Level 2. |
| **Mutation check** | Planting a deliberate bug to prove a test catches it. |

### 11.3 Stale parts of `docs/PROJECT_STATUS.md` (fix when next editing it)

- §6 "Theme" still describes the old phosphor-green monospace theme. The current theme is P3-48's
  (section 3.3).
- §4's feature table still shows "[ END SET ]" bracket buttons and says "mean and best range". The
  UI is restyled, and the label is now "largest".
- §5's loose end on the libyuv and Protocol Buffers licence notices is out of date: both BSD notices
  are in `ThirdPartyNotices.bsdNotices` and shown on the licences screen.
- §7.3's test count (2,078) is old; the latest is 2,831.

---

*End of handover. When you finish a piece of work:*
1. *add its entry to `docs/DECISIONS.md` (next: **P3-52**);*
2. *update `docs/PROJECT_STATUS.md`'s "Next session starts here";*
3. *update section 8 of this file.*
