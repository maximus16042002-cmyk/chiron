# Kinesis — Complete Handover

*Written 2026-10-04 and revised the same day after a full verification pass, at the end of
DECISIONS.md P3-51. For any AI assistant or developer picking this project up without the history
of the sessions that built it.*

**This file is a guide, not an authority.** Where it disagrees with `docs/DECISIONS.md`, the
decision record is right and this file is out of date. Appendix B lists what was checked against
which source, and which statements in the first version of this file were wrong and have been
corrected.

---

## Contents

0. [Read this first](#0-read-this-first) — reading order, how work is done, a ready-made briefing prompt
1. [The project in brief](#1-the-project-in-brief)
2. [The rules](#2-the-rules-non-negotiable)
3. [Architecture and code map](#3-architecture-and-code-map)
4. [The exercise library](#4-the-exercise-library)
5. [What happened: the full timeline](#5-what-happened-the-full-timeline)
6. [Results so far](#6-results-so-far-what-has-been-measured)
7. [Where things stand](#7-where-things-stand)
8. [What has to be done next](#8-what-has-to-be-done-next)
9. [Build, test and phone procedures (Windows)](#9-build-test-and-phone-procedures-windows)
10. [Running the MM-Fit evaluation](#10-running-the-mm-fit-evaluation)
11. [Firebase, security and the repository](#11-firebase-security-and-the-repository)
12. [What is not in git](#12-what-is-not-in-git)
13. [Traps, glossary, FAQ](#13-traps-glossary-faq)
14. [Known limitations](#14-known-limitations)
- [Appendix A: topic → DECISIONS entry index](#appendix-a-topic--decisions-entry-index)
- [Appendix B: what was verified, and corrections to the first version](#appendix-b-what-was-verified-and-corrections-to-the-first-version)

---

## 0. Read this first

### 0.1 What to read, in what order

1. **`CLAUDE.md`** (repository root): the engineering rules. Every rule in it is binding; most are
   restated in section 2 below.
2. **This file.**
3. **`docs/DECISIONS.md`:** the dated decision record, P0-1 to P3-51, about 11,500 lines. It is the
   **highest authority**. Search it for the entry numbers cited here whenever you need the reasoning,
   the evidence tables, or the alternatives that were rejected.
4. **`docs/PROJECT_STATUS.md`** (a shorter status snapshot, refreshed 2026-10-04) and the root
   **`README.md`** (build and test commands, licences).
5. **`docs/CONTRACTS.md`** (type-level contracts: §1 BilateralSignal, §2 ClaimLevel and Finding,
   §3 tier gating, §4 the mirroring contract, §5 the JS facade, §6 numeric contract, §7 the
   real-time / post-rep boundary, §8 the camera-view contract, §10 the export format).
   **`docs/FEATURE_PLAN.md`** (the app feature batches). **`docs/EXERCISE_GUIDE.md`** (how each
   exercise is counted). **`docs/P4_TOMORROW.md`** (the owner's phone test script).
   **`docs/REPOSITORY_STRUCTURE.md`** (the layout).
6. Background plans: `docs/ZERO_DATASET_BUILD_PLAN.md` (current scope and evaluation plan; it
   supersedes `docs/FINAL_ARCHITECTURE.md`). FINAL_ARCHITECTURE still contains voided instructions:
   DECISIONS P0-8 lists them. `docs/PROGRESS_REPORT.md` is a 2026-09-29 snapshot and is out of
   date.
7. **Never read the two `.docx` files at the repository root.** They are originals kept for
   provenance; the markdown copies are in `docs/`.

### 0.2 Document precedence (from CLAUDE.md)

`DECISIONS.md` > `ZERO_DATASET_BUILD_PLAN.md` > `FINAL_ARCHITECTURE.md` > `CLAUDE.md` (an
operational distillation) > everything else. If `CLAUDE.md` disagrees with the ones above it,
`CLAUDE.md` is wrong and must be corrected in the same session that finds the disagreement.

### 0.3 How work is done here (the pattern every session followed)

1. **Read** the relevant DECISIONS entries before changing anything.
2. **Write a design entry first** (next number: **P3-75**). It states what will be built and every
   choice the source documents leave open. Commit it before any code. For studies, the protocol and
   pass rule are fixed and committed (sometimes tagged, e.g. `p3-42-frozen`) **before** any data is
   looked at.
3. **Write the tests first** (shared-core tests run on JVM and JS).
4. **Implement.** All analysis lives in the shared core.
5. **Mutation-check the new guard tests:** plant the wrong rule, confirm a test fails, restore the
   file byte for byte.
6. **Run the full suite** (section 9.2).
7. **Append a "Result" section** to the entry, with measured numbers and anything not done.
8. **Commit** (one coherent step per commit) and **push**.

The motto (CLAUDE.md §23): **MEASURE → TEST → DOCUMENT → THEN CLAIM.** Never state a number that
has not been measured on this system.

### 0.4 A ready-made briefing for another AI

An AI that can only see what you paste into a chat cannot read the code. It needs the repository
(the private GitHub repo `maximus16042002-cmyk/chiron`, or a zip of `E:\kinesis` without `data/`).
Give it access, then paste this:

```text
You are taking over "Kinesis": a camera-based personal movement-analysis Android app (Kotlin
Multiplatform engine + MediaPipe Pose) that is also a BCA final-year research project. It is NOT a medical
or diagnostic system and must never be described or built as one.

Before doing anything:
1. Read CLAUDE.md, then docs/HANDOVER.md in full. docs/DECISIONS.md is the final authority;
   search it for any entry number HANDOVER cites before changing that area.
2. Obey HANDOVER section 2 exactly. The rules that are easiest to break:
   - never average left and right (use BilateralSignal and a named reducer);
   - all analysis logic lives in the shared core (commonMain), never in UI code;
   - no ML per frame, nothing O(history) per frame;
   - Tier B/C exercises can never emit a Level 3 (technique) finding;
   - every technique rule cites a published source; no accuracy number may be attached to one;
   - never state a number that was not measured on this system;
   - never use held-out MM-Fit data without the owner's explicit approval and a frozen protocol.
3. Work in this order for any change: design entry in DECISIONS.md first (next number P3-75),
   tests first, implement in the core, mutation-check new guard tests, run the full suite
   (HANDOVER 9.2), append a Result section, commit, push.
4. Ask the owner before: installing anything on their phone (back up first, HANDOVER 9.4), using
   held-out data, adding a dependency (CLAUDE.md section 17 check), or deciding anything listed in
   HANDOVER 8.2.
5. Everything built since P3-47 has NOT been seen on a real phone. Do not claim it works on a
   device. State what is tested and what is not.

Current state and the to-do list are in HANDOVER sections 7 and 8.
```

---

## 1. The project in brief

| | |
|---|---|
| **What** | Kinesis (shown to users as **Chiron** since P3-65; the code, packages and record keep "Kinesis"): a camera-based personal movement-analysis app for Android. The phone camera watches you exercise. MediaPipe Pose turns each frame into 33 body landmarks. A shared Kotlin engine counts repetitions, times holds, and measures range of motion, tempo and left/right symmetry. |
| **Why** | A BCA final-year project (Bachelor of Computer Applications); the report calls the app **Chiron**. The research question: **do personal baselines outperform population thresholds for flagging movement deviation?** |
| **What it is not** | **Not a medical or diagnostic system.** Feedback is "form feedback", never "diagnosis", "injury" or "correct posture guaranteed". |
| **Owner** | Nitish Amritraj B, a BCA final-year student. The September progress report (`docs/Chiron_Project_Report.docx`, not in git) lists Nitish and Dominic as its authors. They delegate heavily ("do whatever you want… I just want the app perfect and complete") but keep certain decisions (section 8.2). They said the due date is not a worry. |
| **Repository** | `E:\kinesis` on the owner's Windows 11 PC. Remote: private GitHub `maximus16042002-cmyk/chiron`, branch `master`. See `git log` for history; tags `p3-42-frozen` and `p3-46-frozen` mark frozen pre-test states. |
| **Phone** | The owner's Nothing Phone (2a), model A142, Android 16, over USB with `adb`. It holds real data (79 sessions, 79 sets, 258 repetitions, 4 holds and 1 baseline as of P3-47). |
| **Dataset** | **MM-Fit** (public, CC BY 4.0; Zenodo record 7607736, 40.8 GB with video and depth; project site mmfit.github.io; paper at vradu.uk/UbiComp2021.pdf; starter code MIT at github.com/KDMStromback/mm-fit): 10 people, 10 exercises, 616 labelled sets, 6,160 repetitions, set and repetition-count labels, **no form-quality labels**. The pipeline: MM-Fit video → MediaPipe → Kinesis engine → compare with MM-Fit's counts. **No self-collected or self-labelled data exists or may be created.** Two form-labelled datasets (FLEX, M3GYM) exist behind non-commercial licence agreements and are not used. |
| **Time span** | P0 began 2026-09-19; this handover is 2026-10-04. |

### 1.1 The research design (from `ZERO_DATASET_BUILD_PLAN.md`)

The headline experiment needs no fault labels, only many repetitions from several people:

- **Study 1, variance decomposition:** per exercise and metric, split variance into between-person
  and within-person parts and report the intraclass correlation (ICC). High ICC means any single
  population threshold must misfire on somebody. A low ICC is a legitimate result too.
- **Study 2, population vs personal thresholds:** flag rate per person under a pooled population
  threshold, against a personal threshold from that person's first repetitions. MM-Fit repetitions
  are routine workout execution, so a flag is a presumed false positive. **State that assumption
  explicitly.** The claim is about flag rate on routine repetitions, never about precision.
- **Study 3, calibration convergence:** how many calibration repetitions until a personal baseline
  settles, with bootstrap intervals.

The **six studies that survive** (the report's only source of numbers):

| Study | Data | Metrics |
|---|---|---|
| Repetition detection | MM-Fit, 616 sets | Counted vs labelled per set: MAE, share exact, share within ±1 |
| Exercise verifier | MM-Fit, 10 classes, leave-one-subject-out | Accuracy, per-class F1, confusion matrix |
| Personalisation (Studies 1–3) | MM-Fit | ICC with intervals, flag rate per subject, convergence curve |
| Device performance | The owner's phone, live | Sustained fps, latency, memory, 15-minute thermal run |
| Pose reliability in deployment | The owner's phone, live | Landmark visibility per exercise and camera view, failure modes |
| Cross-platform parity | Golden files | JVM vs JS: discrete outputs identical, continuous within 1e-9 relative |

### 1.2 What the report must never claim (from the build plan)

- Any accuracy, precision or recall figure for technique or form correctness (there are no fault
  labels).
- Per-repetition boundary precision (MM-Fit labels set boundaries and counts, not repetition frames).
- That personalisation improves *fault detection* (what is measured is flag rate on routine
  repetitions, a false-positive argument).
- That rules validated on MM-Fit's seated shoulder press or lateral raise apply to the standing
  versions.
- That results generalise beyond MM-Fit's single fixed camera at 30 Hz, unless the live sensitivity
  study says so.
- Any number before it has been measured on this system.

The plan's own summary sentence, to keep the work aligned: *the system measures movement reliably,
personalises its thresholds to the individual with measured justification, and applies technique
rules drawn from published sources whose agreement with expert judgement was not evaluated in this
work.*

---

## 2. The rules (non-negotiable)

### 2.1 Architecture

- **Platform:** Android (Kotlin, Jetpack Compose, CameraX, MediaPipe Pose).
- **Shared core:** Kotlin Multiplatform `commonMain`, with JVM, JS and Android targets. **All
  analysis logic lives in the shared core.** Platform code only does camera acquisition, the pose
  adapter, mirroring, rendering/UI and storage.
- **The JS target must stay green.** It carries the JVM/JS parity study. The React PWA is **cut**
  (P0-2). Never rewrite the engine in TypeScript.
- **Numbers:** `Double` throughout the core; no `Float`, and no `Long` in analysis paths that JS
  runs. Discrete outputs match exactly across platforms; continuous ones within about 1e-9 relative.

### 2.2 Left and right

- **Never average left and right silently, anywhere.** Use `BilateralSignal(left, right)`. Any
  reduction to one number is an explicitly named function. Permanent guard:
  `SharedCoreSourceGuardTest` scans every production source for averaging.
- Front-camera mirroring is undone in **one place**, the platform `PoseAdapter`. The core always
  receives anatomical left/right. There are permanent mirroring regression tests.

### 2.3 Real time

- The per-frame path (landmarks → normalisation → validity → kinematics → ring buffers → FSM or
  hold state) is **O(1) per frame**, with no per-frame allocation (enforced by `RealTimeBudgetTest`
  and the source guard's file classification: every file in the core's temporal, rep, hold, history,
  live, calibration, export and deviation packages must be classified real-time or post-rep).
- No ML per frame. Heavy work happens only after a completed repetition or hold.
- Compose must not recompose per frame. The skeleton is drawn on a plain Android View/Canvas.

### 2.4 Claim levels

- **Level 1** (measurement), **Level 2** (personal deviation against your own baseline), **Level 3**
  (technique judgement).
- **Tier B and Tier C profiles cannot construct a Level 3 finding.** Enforced by types: a
  `TechniqueAuthority` capability token that only Tier A profiles hold (P1-3).
- Every Level 3 (Layer 2) rule carries `sourceRef` (a cited published source, quoted) and
  `ruleVersion`. **A rule with no source does not ship. No accuracy figure may be attached to any
  technique rule.**

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
  (P3-13, P3-18).
- No video is stored. Calibration baselines stay on the phone and do not sync.
- `OfflineManifestGuardTest` must keep failing on any network capability other than the declared
  Firebase artifacts; it reads the manifest merger's blame report and has an allowlist of five
  named artifacts (P3-14).

### 2.7 Dependencies

Before adding any dependency, run the **§17 check** and record it in DECISIONS: why it's needed,
whether something existing covers it, licence (code and model weights separately), platform
compatibility, size, offline impact, maintenance. Recent work avoided new dependencies entirely:
navigation, charts and settings are all hand-rolled.

### 2.8 Evaluation integrity: the MM-Fit split (corrected)

MM-Fit publishes no subject-to-workout mapping. The project's map was judged by eye from contact
sheets and checked against the paper's counts (two people with six workouts, one with two, seven with
one); the evidence is in P3-30. `tools/mmfit-eval/subjects.py` holds it, and `test_subjects.py` and
`SubjectsTest` fail if any person is on both sides.

**The split is by subject (P3-30), and it is the only one to use for new work:**

| | People | Workouts |
|---|---|---|
| **Development** | A, B, C, S19 | **15 workouts:** w00–w11, w14, w15, w19 |
| **Held-out** | S12, S13, S16, S17, S18, S20 (six people, one workout each) | **6 workouts:** w12, w13, w16, w17, w18, w20 |

(A = w01, w03, w06, w08, w10, w14; B = w02, w04, w07, w09, w11, w15; C = w00, w05; S19 = w19.)

**The older split (P3-27) was by workout and leaked:** its development set was w00–w06 and w19, and
its "held-out" set was the other 13 workouts, which include A and B. Figures recorded in P3-27 to
P3-29 under the word "held-out" use that split and are labelled "P3-27 split" wherever they are
reproduced (section 6.1).

**Held-out data is used only with the owner's explicit approval,** once per pre-registered test,
with the pass rule frozen and committed first. It has been read for three things: the P3-30 re-slice
of old runs onto the six people, P3-42 (Layer 1), and P3-46 (the sit-up trunk driver). Do not use
it again without approval.

Also: MM-Fit's bundled OpenPose skeletons are never fed to the engine (a different joint format);
OpenPose is used only as a clock for alignment. Squats are excluded from MM-Fit comparisons because
MM-Fit films them front-on and the profile is side-on.

### 2.9 The owner's standing instructions (obey them)

- **Screenshots and recordings of the owner never go into git.** They are saved only under
  `local/phase-b/` (gitignored) and described in words in DECISIONS (P3-19).
- **The phone holds real data.** Before any on-phone test run, back up its database: P3-23 found
  that a connected instrumented-test run **uninstalled the app and removed its database** (it was
  restored from a backup). The README documents a flag (`leaveApksInstalledAfterRun=true`) that
  keeps the app installed, but it does not replace the backup. After that, P3-23 records updating the app only with `installDebug`
  (which keeps data), and P3-47 notes the on-device test task was not used. Treat it as: **don't,
  unless the owner asks and a verified backup exists.**
- **Before installing a build on the phone:** (1) back up `databases/` with `adb … run-as
  com.kinesis` (section 9.4); (2) install with `adb install -r` (keeps data); (3) record the APK's
  SHA-256 (builds are identified by hash, not install time: P3-31 found an unrecorded reinstall);
  (4) open the app with `am start`. Install only with the owner's approval.
- Never force-stop the app, put the phone to sleep, or poll its thermal state without asking. The
  owner rejected that once.
- `google-services.json` is deliberately committed (P3-20; the repository is private). **Review that
  decision before the repository is ever made public** (section 11.3).
- When freeing memory on the PC, stop only the process you have identified by its PID (for example
  the Kotlin compile daemon: its command line contains `kotlin-compiler-embeddable`). Never stop
  every `java.exe`: Android Studio may be running.

---

## 3. Architecture and code map

### 3.1 Modules

| Module | Contents |
|---|---|
| `:core:domain` | Types only. `bilateral/BilateralSignal`, `finding/ClaimLevel`+`Finding`, `landmark/*`, `profile/ExerciseProfile` (tiers, `CameraView`, `Posture`), `rep/RepRecord`+`FormMeasures`+`MovementContext`+`MovementPhase`, `session/SessionRecord`+`LocalDay`, `calibration/CalibrationBaseline`, `analysis/AnalysisResult`. |
| `:core:analysis` | The engine (`commonMain`); see 3.2. `KinesisEngine.kt` is the only `@JsExport` surface (the JS facade). It lives in `commonMain` (P3-19 §8), so the JVM runs the same facade the parity tests compare. Tests are in `commonTest` (JVM and JS) and `jvmTest` (source guards, allocation tests). |
| `:app` | The Android app, `com.kinesis` (3.3). |
| `:desktop` | Empty placeholder (a possible replay-only client). |
| `tools/mmfit-eval` | The MM-Fit evaluation harness: Kotlin (runs the app's own live pipeline over landmark files) plus Python for alignment and statistics. Section 10. |
| `tools/g1-spike` | P0's proof that the engine gives matching results on JVM and JS. |
| `tools/firestore-rules` | Firestore security-rule tests on the Firebase emulator (Node). Section 11. `firestore.rules` is at the repository root. |

### 3.2 The shared engine (`core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/`)

| Package | What |
|---|---|
| `landmark/` | `LandmarkNormalizer`, `ValidityGate` (per frame: reliable / marginal / missing; blocks analysis on bad frames). `VisibilityPolicy.PROVISIONAL`: reliable ≥ 0.70, marginal ≥ 0.40. |
| `geometry/`, `kinematics/` | Joint angles; `BodyPoint`, `DrivingSignal` (a joint angle or a segment inclination, e.g. `SegmentInclination.TRUNK` for the sit-up), landmark velocity. |
| `temporal/` | `RealTimeSignalPipeline`: median outlier gate (window 5, threshold **35°**, re-admit after 3 consistent samples) → EMA smoother (alpha 0.3, weighted per unit time since P3-33) → ring buffer. Gap bridging up to **500 ms** inside a repetition (`maxBridgedGapMs`). After a repetition: gap interpolation (300 ms) and resampling to **64 points**. All provisional constants live in `TemporalConfig.PROVISIONAL`. |
| `rep/` | `RepFsm` (IDLE → TOP → DESCENDING → BOTTOM → ASCENDING, with hysteresis, min/max durations, explicit ABORTED / INCOMPLETE / INSUFFICIENT_DATA outcomes), `BilateralRepTracker` (one FSM per side; per-limb vs simultaneous combination; near-side-only rules), `FormTracker` (per-frame ring buffers of other joints, reduced per repetition into `FormMeasures`, P3-44), `BilateralPhase`. |
| `hold/` | `HoldAnalyser`, `HoldReading`: isometric holds, never through the repetition FSM. |
| `live/` | `LiveRepetitionAnalysis`, `LiveHoldAnalysis`: the per-frame entry points the app calls. |
| `profile/` | `ExerciseLibrary` (the 17 entries), `ArchetypeProfiles` (squat, curl, plank), `TierBProfiles`, `TierCProfiles`, `ExerciseVariant` (variants delegate to their parent), `ExerciseKinds` (`RepSignalSpec`, `RepetitionSemantics`, `AnalysedSides`). |
| `calibration/` | P6: `Calibration` (`CalibrationKey`, with a revision `situp@trunk`), `CalibrationPolicy.PROVISIONAL` (5–8 valid repetitions per side, 10 s minimum hold, version `p6-1`), `RobustStatistics`, `PostRepAnalysis` (numbers beside the baseline), `CurveDistance`, `CalibrationGuide` (calibration instructions and the "Setting up" placement text), `BaselineJson`. |
| `deviation/` | P8 Layer 1: `DeviationRules`, `DeviationPolicy`, `RepFeatures`, `References`, `AcsmTempoRule`, `PersonalStartZone` (push-up only). **Built and tested, not wired into the app.** |
| `technique/` | P8 Layer 2: `TechniqueRules`, six cited rules. **Built, not wired.** |
| `anomaly/` | P9 Layer 4: `IsolationForest`, `RobustDistance`, `PersonalAnomalyModel`, `AnomalyFeatures`, `SplitMix64`. **Built, does not ship.** |
| `verifier/` | P9 Part B (P3-52, P3-59): `MovementContextTracker` (per frame, inside `BilateralRepTracker`, fills `RepRecord.movementContext`), `ExerciseVerifier`, `PretrainedExerciseVerifierModel` (generated by `tools/mmfit-eval/generate_kotlin_verifier.py`). Covers push-up, sit-up, lateral raise, shoulder press. **Built, does not ship.** |
| `setup/` | P3-55 as corrected by P3-59: `SetupAssistant` (one frame's verdict), `CameraViewEstimator`, `SetupGate` (debounced: blocks scoring on a wrong view or nobody in shot; one message for the screen). Since P3-68 every caller, the app included, uses `SetupEnforcement.REPORT_ONLY`: a wrong view is advised, not blocked, until the estimator is measured on the phone. Blocking stays built and tested. |
| `fps/` | P3-56: `FrameRateMonitor`, a warning below 12 results a second. Never blocks. |
| `speed/` | P3-59: `LiftingSpeed`, each side's lifting speed and slowdown within a set, for the set summary. A Level 1 measurement. |
| `export/` | `SessionExport`: JSON and CSV, export `formatVersion` 2, golden-tested byte for byte on JVM and JS; `JsonNumber` makes every number print identically on every platform (P3-25). |
| `history/` | `Dashboard` (totals, per-exercise summary, record fields), `Streaks` (streak, freezes, `activity` for the calendar), `Records`, `WeeklyGoal`, `Achievements`, `Reminders` (reminder text), `Trends` (chart points). |
| `workout/` | `WorkoutGenerator`: the catalogue, plan generation, `GuidedWorkout` steps (P3-50). |

**`FormMeasures` (P3-44):** one `BilateralSignal` per measure the exercise asks for, in degrees,
reduced over one repetition's window. The four measures: `upper_arm_trunk_max_deg` (curl: largest
angle at the shoulder between hip and elbow), `body_line_sag_max_deg` and `body_line_hike_max_deg`
(push-up: largest bend of the hip below / above the shoulder–ankle line), `trunk_minus_shin_lean_deg`
(squat: trunk lean minus shin lean from vertical at the deepest frame). Stored in `RepRecord.form`;
absent for every other exercise; "not measured" is `NaN`, never zero.

### 3.3 The Android app (`app/src/main/java/com/kinesis/`)

| Package / file | What |
|---|---|
| `MainActivity.kt` | The shell (hand-rolled navigation, no library). `KinesisRoot` runs onboarding, then the auth gate. `KinesisApp` holds the five tabs, the `Destination` sealed interface for the Train flow (Exercise, CalibrationIntro, Live, CalibrationResult, SetSummary, Builder, Plan, Guided), `backFrom`/`goBack`, `BackHandler`, `TrainPage`/`TrainFlow`/`GuidedFlow`, `LicencesPage`. |
| `camera/` | `LiveCamera` (CameraX, a portrait 4:3 frame), `PoseLandmarkerSource` (MediaPipe), permission, orientation (`LiveOrientation` tilt listener: follows the phone upright or upside down, never landscape), model asset. |
| `pose/PoseAdapter.kt` | Camera coordinates to anatomical left/right, mirroring, and the aspect correction (x and y in one unit). |
| `ui/live/` | `LiveHost` (sessions, saving, failures), bridges/engines, `SkeletonOverlayView` (Canvas, four-state colour-blind-aware palette), `SkeletonColouring`/`SkeletonSmoothing` (a display-only One Euro filter), `RepFeedback` + `RepCountWatcher` (tone, vibration, spoken count, P3-49). |
| `ui/screens/` | `Components` (`KCard`, buttons, `BottomBar`, `Tab`, `BackLink`, `viewLabel`/`postureLabel`), `HomeScreen`, `Screens` (Workouts list, licences, Health Connect panel, live layouts), `CalibrationScreens` (exercise page, calibration, set summary), `ProgressScreens`, `CalendarCard`, `TrendCharts`, `ProfileScreen`, `ReminderCard`, `RestTimerCard`, `WorkoutScreens` (builder, plan, guided), `OnboardingScreen`, `AuthScreen`. |
| `ui/theme/` | `KinesisTheme.kt` (`KinesisPalette` tokens: canvas `#0E0F12`, card `#1A1C21`, raised `#23262D`, ink `#F2F3F5`, ink secondary `#A0A4AD`, ink faint `#8F939C`, accent `#FF6B35`, attention amber `#FFB000`, chart series left `#3987E5` and right `#199E70`; sans-serif type with tabular figures) and `SkeletonPalette.kt` (the overlay's four states, unchanged by the redesign). |
| `storage/` | Room: `KinesisDatabase` (**version 3**, `MIGRATION_2_3` adds `reps.form`), entities and mapping, `SessionRecorder`, `SessionRepository`, baseline storage, `utcOffsetMinutesAt`. |
| `sync/` | Firebase: `AuthGateway`, `FirebaseSync`, `SessionDocument` (Firestore mapping), `SessionMerge`, `SessionSyncWorker` (WorkManager, retry), `SyncingSessionRepository` (local first, then sync). |
| `health/` | Health Connect: steps, active calories, mean heart rate, read-only, shown beside training; every absence is named. |
| `calibration/CalibrationCoordinator.kt` | Runs the core's calibrator and stores baselines on the phone. |
| `export/ExportFiles.kt` | Writes the export and hands it to the share sheet. |
| `reminders/` | `ReminderTiming` (next time, DST-safe), `ReminderWork.kt` (`ReminderScheduler`, `ReminderWorker`, `ReminderNotification`). |
| `settings/` | `AppSettings` (SharedPreferences file `kinesis`: onboarding flag, weekly goal, reminders, rest length, repetition cues, last and favourite workouts), `PlanRequestCodec`. |
| `licenses/ThirdPartyNotices.kt` | Licence texts shown in-app: Apache 2.0, plus BSD notices for libyuv and Protocol Buffers. |
| `diagnostics/PhaseBProbe.kt` | Debug-only live readout (Logcat tag `KinesisPhaseB`). A non-debuggable `profiling` build type keeps it for benchmarks. |

### 3.4 Data flow

**Live (per frame):** CameraX frame → MediaPipe (pose_landmarker_lite) → `PoseAdapter` (anatomical
L/R, aspect) → `LiveRepetitionAnalysis` / `LiveHoldAnalysis` (validity → angles → filters → FSM and
form tracker) → UI state published a few times a second, plus the skeleton overlay drawn directly
from the pose callback.

**After a set:** `RepRecord`s / `HoldRecord`s → `SessionRecord` → Room (the source of truth) →
WorkManager sync to Firestore if signed in. One live set is one `SessionRecord`.

**Interpretation is kept separate:** `AnalysisResult` (numbers beside the baseline) is computed from
records and the baseline, never merged into the record, so history can be re-analysed when rules
change.

### 3.5 Stored formats

| Format | Version | Notes |
|---|---|---|
| Room database | **3** | Live on the owner's phone since P3-47. |
| Export | `formatVersion` **2** | Adds the per-repetition `form` (P3-44). Raised whenever a field is added, removed, renamed or changes meaning; the golden file is regenerated with it. |
| Firestore | path `users/{uid}/sessions/{sessionId}` | Append-only; `form` written only when present. |
| Rule versions | `p8-layer2-1`, `p8-layer2-2` | — |
| Calibration policy | `p6-1` | — |

### 3.6 Frame-rate facts that shape everything (P3-18, P3-19 §5, P3-31)

- MediaPipe lite takes about **57–58 ms** from hand-over to result on the phone, which caps live
  analysis at roughly **17 results a second**; the full model takes 66–73 ms. The GPU delegate was
  measured inconsistently (11 ms faster once, 3–4 ms slower once) and **not adopted** (it changes
  the numerics).
- Observed live rates: 4–16 results a second. The phone ran hot (thermal status 3 for half of the
  2026-09-29 session) and rates fell to about 6 a second.
- MM-Fit is 30 fps. The harness `-Pstride=N` option feeds every N-th frame to show the engine at the
  phone's lower rates (2 ≈ 15 a second, 5 ≈ 6).

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
- **Under schedule pressure,** Side Plank and Hip Abduction may be dropped (15 entries, 13
  profiles): a fallback, not the goal.
- **MM-Fit covers** squat, push-up, shoulder press, lunge, row, sit-up, curl and lateral raise (plus
  tricep extension and jumping jacks, which have no Kinesis profile). It does **not** cover plank,
  side plank, wall sit, glute bridge, hip abduction, knee raise or leg raise: the verifier cannot
  verify those.
- **The workout generator's catalogue** (P3-50) assigns body area, equipment and room needs:
  dumbbells for the curl, lateral raise, shoulder press, row and goblet squat; a chair for the two
  seated exercises; a wall for the wall sit.

---

## 5. What happened: the full timeline

Each item is one DECISIONS.md entry. Search for the entry number for its detail.

### Phase 0: architecture lock (2026-09-19)

- **P0-1** The repository was empty and not a git repo; the `.docx` plans were converted to markdown.
- **P0-2** Keep the Kotlin/JS target (for the parity study); cut the React PWA.
- **P0-3** Glute Bridge stays Tier A.
- **P0-4** Lateral Raise and Shoulder Press are seated, matching MM-Fit.
- **P0-5** The target is 17 entries / 15 profiles / 2 variants.
- **P0-6** Layer 2 is unvalidated (literature thresholds only); P8 module 8.6 is deleted.
- **P0-7** Tier A now means "cited technique rules exist", not "validated".
- **P0-8** Lists the superseded instructions in FINAL_ARCHITECTURE: subject recruitment, Layer 3,
  gate G3. Do not act on them.
- **P0-9** Module structure adopted. An incident: Android Studio overwrote the root Gradle config
  mid-session; it was rebuilt.

### Phase 1 and 2: core types, kinematics, temporal

- **P1-1** Android Lint fails on JDK 25 (environmental; lint was excluded from the suite until P3-61's AGP 8.9.1 fixed it).
- **P1-2** Where each type lives. **P1-3** Claim-level enforcement by a capability token.
- **P1-4** Provisional constants. **P1-5** The front-camera mirroring assumption, stated.
- **P2-1** P2's scope. **P2-2** The old prototype at `E:\kinesis_old` was inspected read-only; it is
  reference material only. **P2-3** `app/` is an instrumentation harness first.
- **P2-4** The ablation: smoothing **lag**, not noise, is the binding constraint.

### Phase 3: engine, app, device, evaluation (P3-1 to P3-51)

**Engine and library**
- **P3-1** MM-Fit counts curls **per arm**. **P3-2** The rep engine: one FSM per side, never a fused
  signal. **P3-5** Repetition semantics for push-up, lateral raise and glute bridge, measured from
  MM-Fit. **P3-6** Variants delegate to their parent. **P3-7, P3-8** The Tier B profiles; the
  camera-view contract enforced; "near side only" introduced. **P3-9** Tier C holds; their thresholds
  carry no citation, by design.

**Scope and the owner's early decisions**
- **P3-3** The owner added a dashboard, streaks and Health Connect. **P3-4** The owner had all 15
  profiles built before device validation, consciously reversing CLAUDE.md's original order.

**The first app (P3-10 to P3-14)**
- **P3-10** Streaks (no freeze at the time; superseded by P3-49). **P3-11** The Health Connect §17
  check. **P3-12** Compose UI, Room, and the phosphor-green terminal theme (superseded by P3-48).
- **P3-13** CameraX and MediaPipe §17 checks; MediaPipe's telemetry backend excluded.
- **P3-14** **The one deliberate architecture change:** Firebase Auth and Firestore sync of session
  history, with the offline guard rewritten (not weakened) to allowlist five named artifacts. Two
  Firebase artifacts carry Google's proprietary Android SDK licence, not Apache 2.0.

**The live screen and the first device findings (P3-15 to P3-19)**
- **P3-15** Camera → MediaPipe → adapter → core → readout and overlay wired end to end; mirroring
  made true in capture; defects found by reading the pipeline. Nothing had run on a device.
- **P3-16** The frame-shape correction (joint angles had been distorted by up to ~16° on a 3:4
  frame); holds wired; sets saved; one `NaN`-visibility predicate; the attention pulse drawn. A risk
  found: the outlier gate could lock a side out.
- **P3-17** A front/back camera switch (with a parity guard); the pose model's licence recorded
  (Apache 2.0, model card 2021-04-16); the gate re-admits a sustained change after 3 consistent
  frames; a debug Phase B readout.
- **P3-18** **The first run on the phone crashed.** `TaskRunner.create` always builds a logger that
  links datatransport, so P3-13's exclusion removed code MediaPipe links against unconditionally. The
  fix: inert stand-ins, so MediaPipe links and sends nothing. Verified: no crash, preview on both
  cameras, 4–16 results a second.
- **P3-19** A large batch:
  1. screenshots moved out of git;
  2. the skeleton clipped to the camera box;
  3. an upside-down phone supported by a tilt listener (the owner's phone stands upside down on USB;
     sensor-portrait would not have worked, `mAllowAllRotations=false`);
  4. two repetition bugs fixed: record numbering, and left/right cycles pairing across any gap
     (which could inflate a squat count);
  5. the frame-rate ceiling measured (3.6);
  6. the Firestore rules tested on the emulator: **they refused every upload**; fixed (section 11);
  7. Health Connect: the client was too old for Android 14+, upgraded to 1.1.0-beta01, and a
     permission-rationale activity added;
  8. the JVM/JS parity suite (`EngineParityTest`) built, with the facade moved to `commonMain`.
- **P3-20** The private GitHub remote. No image of the owner is in any commit.
- **P3-21** Owner decisions: the camera session waits; P6 goes first; GPU delegate not adopted;
  a debug-only lite/full pose model switch; the two BSD notice texts (libyuv, Protocol Buffers) now
  ship in the licences screen.

**Calibration, the demo, the export (P3-22 to P3-26)**
- **P3-22** **P6 calibration:** per-side baselines from 5–8 valid repetitions (provisional), stored
  on the phone only (Room version 2). Numbers beside the baseline, no judgement.
- **P3-23** **The guide demo counted nothing** (2026-09-26: 42 sets, none counted). Diagnosed from the
  phone's records: mainly, **the far leg was never tracked side-on** (a squat needs both knees), and
  one unmeasurable frame abandoned a repetition. Changes: a 500 ms gap bridge, outlier threshold
  20° → 35°, an amber flash of 1.5 s, unseen joints drawn grey, a display-only One Euro skeleton
  filter, the curl's at-rest edge 155° → 145° and time limit 10 s → 20 s. A mistake and its repair: a
  connected test run uninstalled the app; the data was restored from a backup (section 2.9).
- **P3-24** A solo check: **15 of 15 curls**; face-on squats counted 4 valid matching the owner's 4
  (but a face-on knee angle is a projection); each amber flash matched a failed record.
- **P3-25** **P5 export:** JSON and CSV in the core, one text for every number on every platform;
  run on the phone's real database, 59 sessions, 134 repetitions and 4 holds matched exactly.
- **P3-26** Owner: two speed messages after P7 ("faster than your usual", Level 2; "faster than the
  ACSM beginner tempo", Level 3 Tier A), with the ACSM source quoted. Both were later held back
  (Layer 1 did not ship).

**MM-Fit evaluation (P7)**
- **P3-27** All 616 sets through the phone's own pipeline. It exposed a fast-start defect (the engine
  lost 54% of curls).
- **P3-28** The fast-start fix; push-ups and sit-ups count from the near side.
- **P3-29** The near-side rule refined (only an accepted VALID cycle blocks an overlapping one); the
  lateral raise's start zone moved 28° → 36°.
- **P3-30** The by-subject split; **P8 Layer 1** built; the personalisation study S1–S4 run on the
  development subjects; the earlier figures re-read on the six unseen people (6.1).

**Back on the phone, and fixes**
- **P3-31** The phone session of 2026-09-29. Curls filmed sideways: right 16 of 16, left 15 of 15.
  Facing the phone, the curl counted arm swings and snaps, so the curl went side-on.
- **P3-32** The curl filmed side-on; a `RepFsm` fix for a movement crossing the whole zone between
  two results.
- **P3-33** The smoother weighted per unit of time. At the phone's 15 results a second (development
  workouts): lateral raise 351 → 388 of 409, rows 235 → 332, push-ups 62 → 88.

**ML (P9)**
- **P3-34** P9 brought into scope; designed before code. **P3-35** Layer 4 in the core.
  **P3-36** A robust covariance distance added to the comparison. **P3-37** The anomaly study.
- **P3-38** **The owner delegated: Layer 4 does not ship** (a negative result), and the order of work
  toward a complete app was set.

**Layer 1's fate**
- **P3-39** Curl timing traced; the engine stays unchanged, behaviour pinned by tests. **P3-40** S5.
  **P3-41** Decision 3: no Layer 1 setting qualified. **P3-42** A smaller setting, tested once on the
  held-out people, **failed**: **Layer 1 stays out of the app.**

**Layer 2 and the sit-up**
- **P3-43** Layer 2 step A: three cited technique rules. **P3-44** Step B: `FormMeasures` per
  repetition (Room v3, export v2) and three more rules.
- **P3-45, P3-46** The sit-up switched to a trunk-angle driver after a pre-registered held-out test.
- **P3-47** The P3-46 build installed on the phone after a backup. Room v3 migrated on the owner's
  real data; all 79 sessions intact.

**The complete app (built in one session; none of it yet seen on the phone)**
- **P3-48** FEATURE_PLAN Batch 1: the new look (dark, rounded, orange accent, sans-serif; palette
  contrast-tested); five tabs; back-button handling; onboarding; every screen restyled; a "Setting
  up" card per exercise, from the core.
- **P3-49** FEATURE_PLAN Batch 3: **streak freezes** (the owner's rule: one earned per 7 consecutive
  active days, at most 2 held; a single missed day spends one; two missed in a row end the streak;
  derived from history, never stored, never refunded); a month calendar; records (per side), a weekly
  goal ring, six achievements; local reminders; a rest timer; tone / vibration / spoken-count cues.
- **P3-50** FEATURE_PLAN Batch 4: a deterministic **workout generator** in the core (time, focus,
  level, equipment, seed; grouped by camera view; pinned identical on JVM and JS) and **guided
  workouts** (prepare → live → rest → next → summary); favourites; "Repeat last workout".
- **P3-51** P10's charts: each exercise's page with **left and right range of motion as two lines**,
  counted-per-set columns, records and a table view; colours validated for colour-vision deficiency.

**Another AI agent's night, and its review (2026-10-04 to 10-05)**
- **P3-52 to P3-58** were built by a different AI agent (Antigravity) from this file, while the owner
  waited for this assistant. Its log is `docs/antigravity_works.md`. It built the exercise verifier
  (P9 Part B), wired findings into the set summary, a "fatigue" card, a camera-view setup gate, a
  frame-rate warning with a skeleton restyle, a display-only zoom, and an x86-free APK.
- **P3-59** reviewed all of it against the code, the data and earlier decisions:
  - **kept:** the verifier's code and parity (built, evaluated, *not shipped*), the frame-rate
    warning, the skeleton restyle, and the ABI filter;
  - **reversed:** findings on the set summary (against P3-42), the zoom (it moved the skeleton but
    not the picture), and the fatigue card's zones, cutoff and advice;
  - **corrected:** the setup gate, which blocked nothing, used the wrong unit for the feet, and
    recomposed the screen every frame. Now it blocks scoring on a wrong camera view only.

**Finishing the app, and the demonstration (2026-10-05 to 10-11)**
- **P3-60** One database file per account: sign-in no longer uploads another account's sessions.
- **P3-61** AGP 8.9.1, compileSdk 36, the stable Health Connect 1.1.0; Android Lint runs again.
- **P3-62** A launcher icon. **P3-63** A failed save is always reported. **P3-64** Lint tidied.
- **P3-65** The app is called **Chiron** everywhere a user reads; Android's cloud backup is off.
- **P3-66** A repetition set survives the app being killed (a draft after every repetition).
- **P3-67** Installed on the phone; the owner demonstrated it to their teacher.
- **P3-68** From the demonstration: the view gate advises instead of blocking, and the squat counts
  its near knee.
- **P3-69, P3-73** Later builds installed after verified backups.
- **P3-70** The owner's real training read (103 sets). 53 were empty, split off by camera switches,
  and are no longer saved. The lunge was losing most repetitions side-on.
- **P3-71** The lunge counts its near knee. The paired path's JVM/JS parity moved to the lateral
  raise.
- **P3-72** A hold survives the app being killed too.
- **P3-74** The owner turned the cited technique rules on: "Form notes" once per set, Tier A only.
- **The report:** `docs/report/REPORT.md` and `docs/report/EVALUATION.md` (drafts; the phone
  studies and the authors' "Use of AI tools" section are owed).

---

## 6. Results so far (what has been measured)

### 6.1 Repetition counting on MM-Fit (P7)

The pipeline: MM-Fit video → MediaPipe (the phone's own model file) → the app's live pipeline in
`tools/mmfit-eval` → compared set by set with MM-Fit's labels. **Always read the split a figure
belongs to** (section 2.8).

**Before any fix (P3-27, all 21 workouts, 616 sets, as shipped):** shoulder press 569 of 598 counted
(80% of sets exact); bicep curl 279 of 599 (the engine lost 54% of curls that were in the signal);
lateral raise 208 of 559; sit-up 101 of 640; push-up 5 of 649. Lunge and row were also measured, with
caveats recorded in P3-27.

**After the fixes, on the six unseen people (the clean figures; P3-30 re-slice):**

| Exercise (the change) | Counted of labelled | Sets exact | Sets over their label |
|---|---|---|---|
| Bicep curl (P3-28 fast-start fix) | **163 of 163** | every set | 0 |
| Lateral raise (P3-29 start zone ≤ 36°) | **146 of 150** | 80.0%; 93.3% within ±1; MAE 0.27 | 0 |
| Push-up (P3-29 near-side rule) | 110 of 201 | 25.0% | 0 |
| Sit-up, hip driver (P3-29 near-side rule) | 108 of 189 | 15.0% | 0 |
| Sit-up, **trunk driver** (P3-46, shipped) | **146 of 189** | — | 0 |

- **Push-up weakness:** S12 counts 0 of 30 and S17 3 of 41. **The near-side rule's whole recorded
  gain (129 → 160 of 411) came from A's and B's workouts;** on the six unseen people it changed
  nothing (110 → 110). The start zone, not the rule, is what still fails.
- **Sit-up weakness (hip driver):** S16 counted 0 of 36 and S20 0 of 30. The trunk driver fixed
  most of it (146 of 189 on the same six, 77.2%).
- **Sit-up development:** 413 of 451 on the trunk driver (91.6%), against 150 on the hip.

**Figures recorded under the old P3-27 split** ("held-out" = 13 workouts, **including A and B**, so
not subject-independent): curl 368 of 368; push-up 160 of 411; sit-up 209 of 399; lateral raise 356
of 369 (70.3% exact, 94.6% within ±1, MAE 0.35); shoulder press 348 of 360 (86.1% exact).

**The principle held throughout:** no change was accepted if any set was counted **above** its label.

**Do not quote any of this as accuracy on other cameras, views, people or frame rates.** CLAUDE.md
§22 forbids claiming an external benchmark validates a different condition. MM-Fit films some
exercises from a different view than the app requires.

### 6.2 Personalisation study (P3-30, P3-40), development subjects only

- **S1 (variability):** people differ far more than one person varies. Lateral raise range of
  motion: people differ by about 21–23° (SD); one person's repetitions by about 4°. With four
  subjects, no ICC interval excludes zero (every interval starts at 0.00).
- **S2 (false alarms):** at an equal operating point, the population comparator's false alarms
  concentrate on whoever sits far from the others (for 10 of 16 metrics the worst-off subject fared
  worse under the population comparator; 3 worse under the personal one), while the personal
  comparator's spread more evenly. The personal `|z| > 2` rule flags too much (14–32% of repetitions)
  with a 5–8 repetition baseline.
- **S3 (sensitivity, synthetic):** the personal comparator detects planted range and tempo changes
  far better than the population one (e.g. curl range cut 20%: 94.5% vs 25.2%).
- **S4 (convergence):** 5–8 repetitions do not settle a range-of-motion baseline; about 12–20 would.
- **S5 (P3-40):** a baseline from one session often misdescribes the same person's other sessions
  (range of motion: 31% of cross-session pairs shifted by more than 2 spreads, against 15% within one
  session; left–right 35% vs 4%). Tempo holds (lifting 6%, lowering 3%).
- **Caveats:** development data only; MM-Fit repetitions are assumed routine, so every rate is an
  upper bound on false alarms; push-up and sit-up sessions mostly could not calibrate then.

### 6.3 Layer 1 (personal deviation messages)

Built in the core (P3-30). Decision 3 found no qualifying setting (P3-41). A smaller setting (threshold
3, derived range floors, 15 messages) failed its single held-out test: false alarms on 3 of 15
messages, because one person's calibration set was shallower than their later sets (P3-42).
**Not in the app.** The app shows measurements only.

### 6.4 Layer 2 (cited technique rules, Tier A)

Six rules in `technique/TechniqueRules.kt`, each using a 20° tolerance (the app's own choice, not the
sources'):

| Rule | Exercise | Source |
|---|---|---|
| `raised_above_shoulder_level` | Lateral raise | S1: ACE Exercise Library, *Lateral Raise* |
| `curl_started_elbow_bent` | Bicep curl | S2: ACE, *Hammer Curl* (ACE's biceps-curl page uses a band; the elbow ends in the same place) |
| `pushup_arms_not_extended` | Push-up | S3: ACE, *Push-Ups* |
| `curl_upper_arm_moved_forward` | Bicep curl | S5: Smith A., *Teaching for Skill Acquisition in Fitness*, NSCA Coach 7(4), Table 4 |
| `pushup_hips_sagged` / `pushup_hips_raised` | Push-up | S3, and S6: Cook, Burton & Hoogenboom 2006, N Am J Sports Phys Ther 1(3) |
| `squat_trunk_ahead_of_shin` | Squat | S4: ACE, *Bodyweight Squat* |

- **Not wired into the app.** Two of them explicitly need the phone's side-on check before being
  shown: the push-up rules (MM-Fit's push-up views are not all side-on, one subject's "sag" of 51° is
  not plausible, and the 160° limit sits inside the spread of top positions) and the curl rule (MM-Fit
  films curls face-on, the rule judges a side-on curl).
- The glute bridge has no rule (no fitting source). Squat depth is deliberately not a rule. The
  squat has no MM-Fit number at all.
- If shown, the ACSM-style message would appear once per set, not once per repetition.

### 6.5 Layer 4 (personal anomaly detection)

Built (an Isolation Forest plus a robust covariance distance). The study (P3-37) found the forest
sees only planted stalls, which simple statistics see as often, and the on-device rule would flag
7–30% of normal repetitions when the history holds 10–40. **Does not ship** (P3-38). It stands as a
negative result for the report.

### 6.6 Cross-platform parity

The parity suite (`EngineParityTest`, P3-19 §8) runs two synthetic scenarios (a squat set of 9
records, a plank) through the facade on every target against a golden file. The export, the
workout plan (seed 2026) and the streak rules are also pinned identically on JVM and JS.

### 6.7 The exercise verifier (P9 Part B), development subjects only

Built by the Antigravity agent (P3-52) and reviewed in P3-59. It is a multinomial logistic
regression over a 145-number summary of each repetition (nine joint signals at 16 points, plus
duration), trained on MM-Fit's development people with one person left out at a time.
- **Accuracy:** 74.2% mean over the four folds (56.6%, 67.2%, 83.8%, 89.2%). The 82.1% quoted in
  P3-52 is pooled over all repetitions, which weights the two largest subjects.
- **Kotlin matches Python** to within 1e-9 on 25 development repetitions.
- **Warnings by selected exercise** (P3-59, `tools/mmfit-eval/verifier_breakdown.py`): pooled
  false-warning rates run from 0.2% (curl) to 43.1% (squat). For individual left-out people they
  reach 75–100% (squat for A and S19), 98% (lateral raise for C), 100% (sit-up for S19) and 86%
  (row for S19).
- **Camera view:** MM-Fit films the curl, squat, lunge and row from a different view than the app.
  P3-59 limits the verifier to push-up, sit-up, lateral raise and shoulder press.
- **Status:** not shipped and wired to nothing. The held-out people have not been used for it.

---

## 7. Where things stand

### 7.1 Phases (the build plan has P0–P12; P11 is cut)

| Phase | Status |
|---|---|
| P0 Architecture, P1 Shared core, P2 Kinematics, P3 Profiles & rep engine | ✅ Done |
| P4 Device reality check | 🟡 Partly done: tests 1–2 of `P4_TOMORROW.md` run (P3-31); tests 3–12 owed |
| P5 Records & export | ✅ Done |
| P6 Calibration | 🟡 Built. The only on-device baseline is from front-on curls; recalibration owed |
| P7 MM-Fit pipeline | ✅ Done |
| P8 Form engine | 🟡 Layer 1 built but not shipping; Layer 2's six rules built, not wired |
| P9 ML | 🟡 Layer 4 studied, negative result, not shipping. Verifier (Part B) built and evaluated on the development people (P3-52): mean accuracy over the four folds 74.2% (56.6–89.2%). Per exercise, some left-out people had 33–100% of their correct repetitions warned (P3-59), so it is **not shipped**; its held-out run is unspent |
| P10 Visualisation & Android app | 🟡 Complete in code: P3-48 to P3-51, plus lifting speed, the setup gate, the frame-rate warning and the skeleton restyle (P3-54 to P3-56 as corrected by P3-59); **unseen on the phone** |
| P11 Windows PWA | ✂️ Cut |
| P12 Evaluation & write-up | 🟡 Rep-count and personalisation studies partly done; the rest owed |

**A rough figure:** 6 done + 6 × ½ = about **75%** of the build plan. Phases are not equal in size.
P3-52 to P3-58 raised this to 79%, 88% and ~90% in different documents by counting P9 and P10 as
done; P3-59 returned it to 75%: the verifier is evaluated but not shipped, and nothing since P3-47
has been seen on the phone.
The engineering is far ahead of the report: the remaining work is mostly evaluation and the write-up.

### 7.2 Proven on the phone vs built only

- **On the phone, at some point:** the live screen on both cameras; counting curls (side-on); saving
  sets; sync to the account; Health Connect; the export; one calibration (front-on curls, to be
  redone); the P3-46 build with Room v3.
- **The build installed on the phone is P3-72's** (SHA-256 `7751df11…6a630`, commit `deaaa76`), installed
  2026-10-11 after a verified backup (P3-73).
- **Built and unit-tested only, never seen on a device:** everything in P3-48 to P3-51 (the new UI,
  calendar, freezes, records, goal, achievements, reminders and notifications, rest timer, cues and
  speech, the workout generator and guided mode, charts), the sit-up trunk driver's live
  behaviour, and P3-54 to P3-59's lifting speed, setup gate, frame-rate warning and skeleton
  restyle. **The setup gate blocks counting on a wrong camera view; if it misjudges the owner's
  real setup, nothing will count.** Check it first on the phone (8.1).

### 7.3 Tests

- **The last full run (P3-74, 2026-10-11):** **3,215 test executions, 0 failed, 0 skipped.** By
  module and target: `core/analysis` 645 JVM (+5 allocation), 634 JS, 634 debug, 634 release;
  `core/domain` 40 on each of the four; `:app` 245 debug and 233 release; `tools/mmfit-eval` 25.
  Python tests (`test_subjects.py`, `test_anomaly_study.py`) are separate.
- **The command:** section 9.2.
- **Every test is synthetic.** None has seen a camera frame or a model; device evidence is recorded
  in DECISIONS, entry by entry.
- **Mutation logs** for recent guards are in `local/` (not in git): `p3-49-freeze-mutations.log`
  (4 of 4 caught), `p3-49-records-mutations.log` (3 of 3), `p3-50-generator-mutations.log` (4 of 4).
  P3-59's mutation checks (lifting speed 4 of 4, the guard 1 of 1, the setup gate 8 of 8) are listed
  in its entry.

---

## 8. What has to be done next

### 8.1 Needs the phone (the owner connects it over USB)

1. **Install the latest build safely** (sections 2.9 and 9.4): back up `databases/` via `run-as`,
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`, record the SHA-256, `am start`.
2. **Screenshot every new page** into `local/phase-b/` and describe each in words in DECISIONS under
   P3-48 to P3-51: onboarding; Home (streak, ring, last 7 days, repeat last workout); Workouts
   (builder card); Train; plan, guided prepare / rest / summary; Progress (calendar, achievements,
   an exercise's trend page); Profile (goal, cues, reminders); the restyled live screen.
3. **Check on the device what tests cannot:**
   - the trend chart renders without label collisions (the data-viz method's last step, "render it and
     look at it", was not done);
   - a reminder notification arrives, and the `POST_NOTIFICATIONS` prompt and refusal behave;
   - tone, vibration and spoken counts work, and speech uses an offline voice;
   - the rest timer's end cue;
   - a full guided workout end to end;
   - the back-button paths;
   - onboarding once, then again from Profile.
4. **Resume `docs/P4_TOMORROW.md`** (record into `local/p4-<date>/`; cool the phone first):
   - **recalibrate the curl sideways** (the existing baseline is from front-on data);
   - test 3 lateral raise (10, against the ≤ 36° start zone); test 4 push-ups side-on (8);
     test 5 sit-ups side-on (8, now on the trunk driver); test 6 plank (~30 s against a stopwatch);
   - test 7 the book test (a short loss of tracking mid-curl); test 8 the "NOT VISIBLE" label on
     side-on curls; test 9 back camera (10 curls); test 10 dim light (10 curls);
   - test 11 a 10-minute heat run (last); test 12 squats (optional).
5. **The side-on checks for the six Layer 2 rules,** before any is shown in the app (section 6.4).
5a. **The near-knee squat and lunge (P3-68, P3-71):** five slow side-on squats and five side-on
   lunges should count five each, with the far knee dim.
5b. **Crash recovery (P3-66, P3-72):** start a set, do a few repetitions, force-stop the app in
   Settings, reopen it: the set should be in history. The same for a plank held for 20 s.
5c. **The view advice (P3-68):** turn the wrong way for a few seconds. The banner should advise
   ("Turn side-on to the camera") and counting should continue. Before the gate ever blocks again,
   record `CameraViewEstimator`'s lateral ratio on the owner's real side-on and front-on setups.
6. **Second real account against the Firestore rules, and the API key's restrictions** (section 11).
7. **A second person,** for P4's device study (the evidence so far is one person, one phone, mostly
   the front camera).

### 8.2 Decisions only the owner can make (ask; do not decide)

| # | Decision | Where it came from |
|---|---|---|
| 1 | **The calibration count.** 5–8 valid repetitions per side now; the convergence study says about 12–20 | P3-30 S4, P3-40, P3-42 |
| 2 | **The push-up personal start zone:** adopt or not (the sit-up's was retired with the trunk driver) | P3-29, P3-30 |
| 3 | ~~Squats side-on~~ **Decided P3-68: the near knee counts.** Originally: **Squats side-on:** count the near knee only (as the row does), use an angled camera, or accept squats as untested. The far knee is often untracked side-on, and a squat counts only when both knees complete | P3-23 "For the owner" item 1; still untouched (P3-29, P3-30) |
| 4 | **A low frame rate:** a gate weighted by time, or a warning | P3-56 built a warning below 12 results a second (the Antigravity agent's choice, not the owner's); a gate is still open |
| 5 | ~~The AGP 8.9.1 and compileSdk 36 batch~~ **Done in P3-61** (stable Health Connect 1.1.0; lint runs again). **Still open: targetSdk 36**, which changes Android 16's run-time behaviour and needs the phone | P3-19, P3-21 ("its own small batch, later") |
| 6 | **Per-side tempo for simultaneous exercises** (squat, push-up…): needs `RepRecord` to keep each side's phase times, a change touching Room, sync and the golden file | P3-22 |
| 7 | ~~Local sessions are not partitioned by account~~ **Fixed in P3-60:** each account has its own database file on the phone; the existing file stays with the first account signed in after the update (the owner). The app's settings are still per phone | P3-22, P3-25, P3-60 |
| 8 | **Whether a calibration hold should be one hold or several** (several would give the sway a spread) | P3-22 |
| 9 | **A setup assistant that enforces each exercise's camera view** (CLAUDE.md §10) | Built in P3-55, corrected in P3-59; since P3-68 it advises only, because it misjudged the owner's real setup. Blocking returns once measured on the phone |
| 10 | **The verifier's held-out run, and whether it should ship at all.** Part B was built without the approval this row asked for (P3-52, under "do whatever you want"). P3-59 recommends fixing a shipping rule (for example, a ceiling on any person's false-warning rate) before any held-out run | P3-34, P3-52, P3-59 |
| 11 | **Approval for any other use of held-out data** | CLAUDE.md, P3-30 |
| 12 | ~~Whether to show Layer 2's rules~~ **Decided P3-74: shown as "Form notes" once per set.** Originally: whether to show Layer 2's rules on the set summary. P3-53 showed them early, with Layer 1's messages; P3-59 took all findings off again | P3-43, P3-44, P3-53, P3-59 |
| 13 | **Before the repository is ever made public:** review `google-services.json`, the API key's restrictions, rotating the key | P3-20 |
| 14 | The six curl features in P3-30's Decision 3 rule (probably moot while Layer 1 is out) | P3-32, P3-33 |
| 15 | ~~The app's name~~ **Decided:** Chiron, everywhere a user reads (P3-65) | P3-62, P3-65 |
| 16 | ~~Cloud backup~~ **Decided:** off, `allowBackup="false"` (P3-65) | P3-62, P3-65 |

### 8.3 Engineering work that needs no phone (in order)

1. **P12, the evaluation studies and the report** (section 1.1). **A full report draft exists: `docs/report/REPORT.md`, with the evaluation
   chapter in `docs/report/EVALUATION.md`** (2026-10-05), every figure traced to its
   DECISIONS entry. It still needs the device studies (4, 5) and the owner's review. Consolidate the repetition-detection
   study; the verifier (P3-52's folds and P3-59's per-exercise breakdown,
   `tools/mmfit-eval/verifier_breakdown.py`); the personalisation write-up (including Layer 1's
   held-out failure and Layer 4's null result, reported honestly); device performance and pose
   reliability (need the phone); parity as a study. The limitations section must say that Layer
   2's rules implement thresholds from cited sources and **their agreement with expert human
   judgement was not measured in this work**, and carry every item of section 1.2.
2. **Showing Layer 2's rules,** once the phone checks pass and the owner decides (8.2 item 12):
   Tier A only; each finding shows its source; the wording comes from the core. The code is ready:
   `TechniqueRules.evaluate` is tested and simply not called from `PostRepAnalysis`.
3. **The verifier, if the owner wants it shipped:** fix the shipping rule first (8.2 item 10), then
   the one held-out run. It covers only push-up, sit-up, lateral raise and shoulder press, the four
   MM-Fit films from the app's view (P3-59).
4. **Display-only focus zoom** (polish; P3-57's attempt was withdrawn): transform the camera preview
   and the overlay together, use the core's y unit (frame widths, P3-16), and check it on the phone.
5. **Housekeeping:** `:desktop` is empty. `docs/PROGRESS_REPORT.md` was rewritten in P3-59.

---

## 9. Build, test and phone procedures (Windows)

### 9.1 Environment

- **Windows 11.** The shell is Git Bash or PowerShell.
- **JDK:** Android Studio's JBR, `C:\Program Files\Android\Android Studio\jbr`. Set `JAVA_HOME` to it.
- **adb:** `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- **No emulator image is installed.** MediaPipe ships no x86_64 library, so an emulator cannot run the
  live screen anyway.
- **Versions (from `gradle/libs.versions.toml` and `app/build.gradle.kts`):** Kotlin 2.1.21; AGP
  8.9.1 (P3-61); KSP 2.1.21-2.0.1; Compose BOM 2024.12.01; Activity Compose 1.9.3; Lifecycle 2.8.7; core-ktx
  1.15.0; Room 2.7.1; WorkManager 2.10.0; CameraX 1.4.1; MediaPipe Tasks Vision 0.10.21; Firebase BoM
  33.7.0 (Auth, Firestore); **Health Connect 1.1.0** (stable, P3-61); compileSdk 36, targetSdk 35; minSdk 24;
  Java/JVM target 17.

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
across the files. Regenerating the parity golden file is only ever done on purpose after an intended
engine change: `KINESIS_WRITE_GOLDEN=1 gradlew :core:analysis:jvmTest --tests "*EngineParityGoldenWriter*"`,
then review the diff.

### 9.3 Known build problems and their fixes

| Symptom | Fix |
|---|---|
| `FileSystemException … classes.jar … used by another process` | `./gradlew.bat --stop`, then rerun |
| Top-level functions suddenly "unresolved" | Build-cache artefact: rerun the compile with `--rerun --no-build-cache -Pkotlin.incremental=false` |
| Windows "paging file too small", a failing shell, or PowerShell failing to load | The Kotlin compile daemon has eaten memory. `gradlew --stop`, then stop **that PID only** |
| Another agent's log or decision entry says "done", "blocks", "validated" | Check the code. P3-59 found a gate that blocked nothing, a zoom that moved only the skeleton, and pooled figures that hid 100% false-warning rates. The tests passed in every case, because each test's fixture shared the code's mistake |
| A background task "completed" but the build failed | The exit code was written to a log, not returned: always read the log's `exit=` line |
| Android Lint | Fixed by AGP 8.9.1 (P3-61): `gradlew lint` runs on all three modules, 0 errors. Not part of the suite command; run it separately |
| Bash heredocs mangle `\n` inside generated Kotlin strings | Write files with an editor tool, not heredocs |

### 9.4 Phone procedures

- **Backup first** (Git Bash, not PowerShell, which rewrites binary output as text):
  ```sh
  S=local/backup-<date>; mkdir -p $S
  for f in $(adb shell run-as com.kinesis ls databases | tr -d '\r'); do
    adb exec-out run-as com.kinesis cat databases/$f > $S/$f
  done
  ```
  Check the copy's integrity and Room version with SQLite.
- **Install:** `adb install -r <apk>` (or `./gradlew :app:installDebug`); both keep data. Never
  uninstall.
- **Identify a build by SHA-256**: `adb pull "$(adb shell pm path com.kinesis | tr -d '\r' | sed
  's/^package://')"` then `sha256sum`.
- **The debug readout:** `adb logcat -v time -s KinesisPhaseB`.
- **Device files go to `local/`** (gitignored): screenshots in `local/phase-b/`, session folders
  like `local/p4-2026-09-29/`.
- **Optional debug-only pose model switch** (lite/full): copy `local/models/pose_landmarker_full.task`
  into `app/src/debug/assets/` (ignored by git; P3-21).

---

## 10. Running the MM-Fit evaluation

Everything is in `tools/mmfit-eval/` (its README has the full file table). It needs the MM-Fit
dataset, which is **not in the repository**.

1. **Get MM-Fit** (section 1; 40.8 GB) and place it at `data/mm-fit/` so that `data/mm-fit/mm-fit/wXX/`
   holds the labels and pose, and `data/mm-fit/video/wXX_rgb.mp4` the video (`w00`–`w20`). Everything
   the tools write goes under `data/`, which is gitignored.
2. **Python environment:** the scripts import only the standard library plus **NumPy, MediaPipe and
   OpenCV (`cv2`)**. Install `numpy`, `mediapipe` and `opencv-python`. (The anomaly study's
   scikit-learn cross-check in P3-34 was never installed; the verifier would be the first use.)
3. **Run it** (about 1.5 hours of CPU for step 1; each worker uses about 300 MB):
   ```sh
   python tools/mmfit-eval/extract_landmarks.py data/mm-fit data/mm-fit-mediapipe --workers 5
   python tools/mmfit-eval/alignment_by_set.py data/mm-fit data/mm-fit-mediapipe w00 w01 ... w20
   python tools/mmfit-eval/extract_landmarks.py data/mm-fit data/mm-fit-mediapipe w16 w17 w18 w19 w20 --workers 5 --shifted
   ./gradlew :mmfit-eval:runHarness
   python tools/mmfit-eval/analyse.py data/mm-fit-eval data/mm-fit-mediapipe --json data/mm-fit-eval/report.json
   ```
4. **Useful switches:** `runHarness -Psplit=development` (development workouts only; the
   personalisation work), `-Pstride=N` (feed every N-th frame; 2 ≈ the phone's 15 a second),
   `-Pgate=D -Pbridge=MS` (outlier gate degrees and longest bridged gap), `-PsitupTrunk=<top>,<bottom>` (the
   sit-up driven by the trunk's inclination, P3-45), `-Pout=<dir>`; `gradlew :mmfit-eval:traceSet -Pworkout=w00 -Pset=22` traces one
   set frame by frame.
5. **The subject split** is `subjects.py` / `subjects.json`; `python tools/mmfit-eval/subjects.py
   --write` regenerates the JSON the Kotlin harness reads; `test_subjects.py` checks it. The other
   tools default to the subject split; `--p3-27-split` reproduces results recorded under the old one.
6. **The studies:** `PersonalisationStudy.kt` + `personalisation_study.py` (S1–S4); `s5_study.py`
   (S5); `decision3.py`, `layer1_test.py` (Layer 1); `AnomalyStudy.kt` + `anomaly_study.py` (Layer 4);
   `trunk_driver_thresholds.py`, `situp_driver_compare.py` (the sit-up; its `--held-out` flag is the
   one-shot gate, to be used only with approval).
7. **Held-out discipline:** `raise_threshold_check.py` reads held-out sets only and refuses to run
   without `--p3-27-split`. Treat any new held-out read as a new pre-registered test (section 0.3).

---

## 11. Firebase, security and the repository

### 11.1 What is in use

- **Firebase project:** `kinesis-5b97b`. `app/google-services.json` is committed on purpose; its
  `package_name` (`com.kinesis`) matches `applicationId`.
- **Auth:** email/password. **Firestore:** one collection shape, `users/{uid}/sessions/{sessionId}`,
  written once per session (append-only; a retry writes the identical document).
- **Licences:** Firebase Auth and Play Services Auth ship under Google's proprietary Android SDK
  licence (not open source); the report must not call the dependency set "all Apache 2.0" (P3-14).
- **Sync design:** the local write always happens first; upload is scheduled afterwards by
  WorkManager under a network constraint, with exponential backoff, returning `retry` (never
  `failure`) so nothing is dropped. `SyncOutageTest` pins that an outage changes nothing locally.
  Sign-in merges the account's history in before the first screen shows (a union keyed by session id).
- **The disclosure** above the sign-in button says what leaves the phone (completed sessions: counts,
  ranges, tempo, hold durations, timestamps) and what never does (video, photos, frames, analysis).

### 11.2 The security rules (`firestore.rules`)

- A signed-in user may **read** and **create** only their own sessions; they may never delete; an
  **update** is allowed only if the new document is identical to the stored one (so a retried upload
  after a lost reply is not refused); a minimal shape check guards the document; everything else is
  denied.
- **Rules are not deployed by the app.** They must be published by hand: Firebase console → Build →
  Firestore Database → Rules → Publish.
- **History:** the first published rules (2026-09-23) had a bug that refused every upload, so the
  app's sessions never reached Firestore until the owner published the fixed file on 2026-09-26
  (P3-19 §6). A real session then reached the account and the owner saw it in the console.
- **Tests:** `cd tools/firestore-rules && npm install && npm test` runs 14 rules tests on the Firebase
  emulator against a `demo-` project that cannot reach the real one (needs Node and a JDK).
  `firebase-tools` is MIT; the emulator binary is downloaded on first run and not redistributed.

### 11.3 What has not been checked (owner's checks)

- **The API key's restrictions** in the Google Cloud console (limited to the `com.kinesis` package and
  its signing certificate, and to the APIs the app calls): never checked by any session (P3-20).
- **The rules against a second real account** (the emulator tests cover it; a real two-account check
  has not been run).
- **Before the repository is ever made public,** review the decision to commit `google-services.json`:
  a public repository puts the key in front of automated scanners. At minimum confirm the key's
  restrictions and the rules' behaviour; consider rotating the key and removing the file from history.
- **Shared phones:** since P3-60 each account has its own database file on the phone, so one account's sessions never upload to another's. The app's settings (rest length, cues, goal, reminders) are still shared.

### 11.4 The Git remote

Private GitHub `maximus16042002-cmyk/chiron`, branch `master`; line endings are LF in the working
tree (git warns it will convert to CRLF). Before the first push, every blob was checked and **no image
of the owner is in any commit** (P3-20). Largest committed file: `pose_landmarker_lite.task`, 5.5 MB.

---

## 12. What is not in git

Another machine, or another AI working from a clone, will be missing these:

| Path | What | Size on the owner's PC | How to recreate |
|---|---|---|---|
| `data/mm-fit/` | The MM-Fit dataset (labels, pose, 21 workout videos) | ~40 GB | Download (section 1) |
| `data/mm-fit-mediapipe/` | MediaPipe landmarks extracted from the videos (the harness input) | ~230 MB | Section 10 step 3 |
| `data/mm-fit-eval-*/` | Dozens of harness runs (one per entry's experiment: `p3-27`, `p3-28-*`, `p3-29-*`, `p3-30-dev`, `p3-32-*`, `p3-33-*`, `p3-39-*`, `p3-42-held-out`, `p3-44-dev`, `p3-45-trunk-dev`, `p3-46-held-out-*`…) | most of the rest of 43 GB in `data/` | Rerun the harness with the entry's switches |
| `data/p3-30-study/`, `p3-37-anomaly-study/`, `p3-40-s5/`, `p3-41-decision3/`, `p3-42-held-out/`, `p3-43-technique/`, `p3-44-technique/` | Study outputs and reports | — | Rerun the study's tool |
| `data/mm-fit-subjects/` | Contact sheets used to group workouts by person (they show MM-Fit's participants) | — | `subject_sheets.py` |
| `local/` | The owner's phone backups, screenshots and recordings, session notes, benchmark inputs, mutation and suite logs, scratch scripts (~200 MB) | ~207 MB | Not recreatable (the owner's own data). **Never commit** |
| `local/models/pose_landmarker_full.task` | The full pose model for the debug lite/full switch | 9.4 MB | Download from MediaPipe's public model storage; recorded SHA-256 begins `4eaa5eb7…` (P3-19 §5) |
| `app/src/debug/assets/*.task` | Where that model is copied for debug builds | — | Copy from `local/models/` |
| `local.properties`, `build/`, `.gradle/`, `.kotlin/`, `kotlin-js-store/`, `node_modules/` | Machine- and build-specific | — | Regenerated |
| `docs/Chiron_Project_Report.docx` | The owner's own report draft (untracked; this handover's author did not read or edit it) | — | The owner's file |
| `camera-session.log`, `demo.log` | Old untracked logs at the root | — | — |
| Outside the repo | Firebase console state (rules, users, API key), GitHub access, the phone itself | — | The owner |

There are no signing keys in the repo (`*.jks`, `*.keystore` are ignored); debug builds use the debug
key. Every study's report that quotes `data/` figures cites its harness command in DECISIONS, so a
figure can be regenerated rather than trusted.

---

## 13. Traps, glossary, FAQ

### 13.1 Traps

- **"17 exercises":** wrong; see section 4.
- **Averaging left and right "just for display":** forbidden. Use two values, or a named reducer with a
  test.
- **Putting a threshold or a sentence about movement in UI code:** forbidden. Words about movement come
  from the core (`CalibrationGuide`, `Records.NewRecord.displayText`, `Reminders.message`).
- **Adding a library for navigation, charts or settings:** not needed; each was built without one.
- **Quoting MM-Fit counting results as "accuracy" for the phone:** a different camera, people and
  frame rate. And **always name the split** (section 6.1): "held-out" in P3-27 to P3-29 means a split
  that leaked two people.
- **Using held-out workouts to tune anything:** never, without the owner's approval and a frozen
  protocol.
- **Showing Layer 1, Layer 2 or Layer 4 output in the app:** not without the conditions in section 8.
- **Treating a green unit-test suite as evidence the app works on a phone:** every test is synthetic.
- **Running instrumented tests on the owner's phone** without a verified backup (section 2.9).
- **Forgetting that WorkManager notifications, speech and sensors need a real device** to verify.

### 13.2 Glossary

| Term | Meaning |
|---|---|
| **Active day / qualifying day** | A local day on which at least one repetition or hold *completed* (VALID). Drives streaks, the goal, the calendar and reminders. |
| **Calibration / baseline** | One ordinary set; its valid repetitions become per-side medians and spreads, stored on the phone. |
| **Near side only** | Exercises filmed side-on where only the side nearer the camera is reliable (push-up, row, sit-up). |
| **Per limb** | Each arm or leg counts its own repetitions (curl, knee raise, hip abduction). |
| **Simultaneous** | Both sides must complete the same repetition for it to count (squat, lunge, shoulder press…). |
| **VALID / ABORTED / INCOMPLETE / INSUFFICIENT_DATA** | A repetition's outcome. Only VALID counts. ABORTED: turned back before the far position; INCOMPLETE: ran out of time or the set ended; INSUFFICIENT_DATA: not enough measured frames. |
| **Phase B** | The device reality check (P4) on the owner's phone. |
| **Development / held-out** | MM-Fit split by subject (section 2.8). |
| **Tier A/B/C** | What an exercise may claim: A up to Level 3 (cited rules), B and C up to Level 2. |
| **Level 1/2/3** | Measurement / personal deviation / technique judgement. |
| **Layer 1/2/3/4** | Deviation rules / technique rules / (removed) supervised classifiers / personal anomaly detection. |
| **ICC** | Intraclass correlation: the share of a metric's variance that is between people. |
| **Mutation check** | Planting a deliberate bug to prove a test catches it. |
| **§17 check** | CLAUDE.md's dependency check (purpose, licence, platform, size, offline impact, maintenance). |
| **Frozen** | A protocol and pass rule committed (and tagged) before any data is looked at. |

### 13.3 FAQ

- **Why is Layer 1 built but not in the app?** It failed its one pre-registered held-out test (P3-42),
  so the app shows measurements only. The code stays for the report.
- **Why is the curl side-on when MM-Fit films it face-on?** Face-on, the engine counted arm swings as
  curls (P3-31); side-on, 16 of 16 and 15 of 15. The cost: MM-Fit's curl numbers do not describe the
  app's curl view.
- **Why can't squats count side-on reliably?** The far knee is often untracked, and a squat counts
  only when both knees complete (P3-23). It is an open owner decision (8.2 item 3).
- **Why is a streak "freeze" never refunded?** A day's state, once the day is over, must never change
  (P3-49 rule 5): yesterday shown frozen this morning must still be frozen tomorrow.
- **Why does the phone count fewer repetitions when hot?** Throttling cuts the result rate to about 6 a
  second (P3-31, P3-33); fewer results a second loses repetitions on some exercises.
- **Where are accuracy numbers for the technique rules?** There are none, by design (section 2.4).

---

## 14. Known limitations

- A live session holds exactly one set, so the "sessions" count counts sets (P3-16).
- A repetition set survives the app being killed: its completed repetitions are drafted and saved on
  the next launch (P3-66), and so does a hold (P3-72). A save that fails is always reported, and
  its draft is retried on the next launch (P3-63, P3-66).
- A set ended with the back button during a guided workout is saved but not counted in that workout's
  summary (P3-50).
- The app's settings are per phone, not per account; sessions and baselines are per account since P3-60.
- The setup gate only advises (P3-68); its thresholds come from MM-Fit and are unmeasured on the phone (P3-55, P3-59).
- The curl's timing has a known one-frame artefact at some frame rates, pinned by tests, not fixed
  (P3-30, P3-39).
- On-device frame rate is about 15 results a second and the phone runs hot (P3-31).
- Per-side tempo is not recorded for simultaneous exercises (P3-22).
- MM-Fit's subject map is judged by eye, checked against the paper's counts (P3-30).
- Normalised coordinates still have no consumer on the real-time path; whether normalisation belongs
  in CONTRACTS.md §7's per-frame order is open (P3-15).
- Android Lint runs again since AGP 8.9.1 (P3-61); it is a separate step, not in the suite command.
- The MM-Fit pose model's training was on back-camera photos only; it is out of scope beyond about 4 m,
  without the head visible, or with more than one person; z is not metric (P3-17).

---

## Appendix A: topic → DECISIONS entry index

| Topic | Entry |
|---|---|
| Document conflicts and corrections | P0-1 to P0-8 |
| Claim-level enforcement | P1-3 |
| Mirroring assumption | P1-5, P3-15 |
| Smoothing ablation | P2-4 |
| Curl semantics (one arm = one rep) | P3-1 |
| Rep engine design | P3-2 |
| Push-up / lateral raise / glute bridge semantics | P3-5 |
| Variants | P3-6 |
| Tier B and C profiles; camera-view contract | P3-7, P3-8, P3-9 |
| Streaks (original), dashboard | P3-3, P3-10; freeze rule P3-49 |
| Health Connect | P3-11, P3-19 §7 |
| UI theme (old) / new look | P3-12 / P3-48 |
| CameraX and MediaPipe, telemetry | P3-13, P3-18 |
| Firebase sync, offline guard | P3-14 |
| Rules emulator tests, publish | P3-19 §6 |
| `google-services.json`, API key, remote | P3-20 |
| Frame shape, holds, saving | P3-16 |
| Either camera, model licence, gate re-admit | P3-17 |
| Orientation (upside-down phone) | P3-19 §3 |
| Frame-rate ceiling | P3-19 §5 |
| JVM/JS parity | P3-19 §8, P3-25 |
| Calibration | P3-22 |
| Guide demo diagnosis, gap bridging | P3-23 |
| Solo curl check | P3-24 |
| Export | P3-25 |
| Speed messages (ACSM) | P3-26, P3-30 addendum |
| MM-Fit pipeline and first results | P3-27 |
| Fast-start fix, near-side counting | P3-28, P3-29 |
| Subject split, Layer 1, S1–S4 | P3-30 |
| Phone session 2026-09-29 | P3-31 |
| Curl side-on, zone-crossing fix | P3-32 |
| Time-weighted smoother | P3-33 |
| P9 design, verifier spec | P3-34 |
| Layer 4 | P3-35, P3-36, P3-37, P3-38 |
| Curl timing trace | P3-39 |
| S5, Decision 3, Layer 1's held-out failure | P3-40, P3-41, P3-42 |
| Layer 2 rules and sources | P3-43, P3-44 |
| Sit-up trunk driver | P3-45, P3-46 |
| Phone install of Room v3 | P3-47 |
| Pages, onboarding, design system | P3-48 |
| Freezes, calendar, records, reminders, cues | P3-49 |
| Workout generator, guided mode | P3-50 |
| Trend charts | P3-51 |

---

## Appendix B: what was verified, and corrections to the first version

### B.1 What was checked, and against what (2026-10-04)

| Claim type | Checked against |
|---|---|
| Versions, SDK levels, JVM target | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| Exercise table (view, posture, semantics, sides) | Each profile's declarations in `core/analysis/.../profile/*.kt` |
| Sit-up thresholds (11.9°, 75.0°, hysteresis 8°) | `TierBProfiles.kt` |
| Engine constants (35° gate, 500 ms bridge, 64-point resampling, 5–8 calibration reps) | `TemporalConfig.PROVISIONAL`, `CalibrationPolicy.PROVISIONAL`, `VisibilityPolicy.PROVISIONAL` |
| MM-Fit split | `tools/mmfit-eval/subjects.py` and `subjects.json` |
| Held-out figures | P3-28, P3-29, P3-30 (re-slice), P3-46 |
| Firestore rules and tests | `firestore.rules`, `tools/firestore-rules/`, P3-14, P3-19 §6, P3-20 |
| Phone state (79 sessions, installed APK hash) | P3-47 |
| Module and file names | Directory listings of the source tree |
| Verifier design | P3-34 Part B |
| Layer 2 rule list and sources | P3-43, P3-44, `TechniqueRules.kt` |
| Research design and "never claim" list | `ZERO_DATASET_BUILD_PLAN.md` |
| Test counts | Summed from the build's result XML files |

### B.2 Not independently verified

- The full text of every DECISIONS entry (the file is about 11,500 lines). Entries cited here for
  their headline results were read at the passages quoted; use the entry for anything finer.
- Anything about how the post-P3-47 screens look or behave on a device (none has been run there).
- The current state of the Firebase console (rules, API key restrictions): outside the repository.

### B.3 Corrections to the first version of this file

1. **The MM-Fit development/held-out split** was given as "w00–w06 and w19" and "w12, w13, w16, w17,
   w18, w20". The first half is the old, leaky P3-27 split. Corrected in 2.8.
2. **The headline "held-out" repetition-counting figures** (curls 368 of 368, push-up 160 of 411,
   sit-up 209 of 399, lateral raise 356 of 369) came from that leaky split. The clean figures on the
   six unseen people are in 6.1 (curl 163 of 163, lateral raise 146 of 150, push-up 110 of 201, sit-up
   108 of 189), with the finding that the push-up near-side gain came entirely from people also in
   development.
3. **Health Connect's version** was given as 1.0.0-alpha11; it is **1.1.0-beta01** (P3-19 §7).
4. **The Firestore rules** were listed as untested against a real client. They were emulator-tested
   (14 of 14), fixed, published by the owner on 2026-09-26, and a real session reached the account
   (section 11). What remains unchecked is narrower (11.3).
5. **The instrumented-test rule** was stated as "never run" without its source. The recorded rule
   (P3-23) is "back up first and expect the app to be removed"; the README documents a flag that
   leaves the app installed. Reconciled in 2.9.
6. **The verifier's input** was described as consuming `RepRecord`s "including `FormMeasures`". The
   verifier's designed input is a richer 16-point multi-signal context that has not been built;
   `FormMeasures` is a smaller summary made for Layer 2 (8.3).
7. **Owner decisions that were missing** and are now listed (8.2): the squat near-knee question,
   per-side tempo, local sessions not partitioned by account, the AGP/compileSdk batch, the
   calibration-hold question, the setup assistant, the API-key review.
8. **Stale documents.** `docs/PROJECT_STATUS.md` (old theme, Health Connect version, test count,
   Firestore note, the libyuv notice that was already shipped) and the root `README.md` (still said
   "P0–P3 complete, P4 under way") were corrected in the same commit as this revision.

---

*When you finish a piece of work:*
1. *add its entry to `docs/DECISIONS.md` (next: **P3-75**);*
2. *update `docs/PROJECT_STATUS.md`'s "Next session starts here";*
3. *update sections 7 and 8 of this file.*
