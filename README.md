# Kinesis

Camera-based personal movement analysis. Android, Kotlin Multiplatform,
MediaPipe Pose.

The system measures movement, personalises its thresholds to the individual, and
applies technique rules drawn from published sources. It is not a medical
diagnostic system.

## Status

**P0-P3, P5 and P7 are complete; the app is feature-complete in code; the device check (P4) and the
research core (P8-P9 remainder, P12) are not - about 75% of the plan.** As of 2026-10-05
(DECISIONS.md P3-59):

- The shared analysis engine, all 15 profiles and 2 variants, calibration, the export, the MM-Fit
  evaluation pipeline, and the Android app (five pages, calendar and streak freezes, records, weekly
  goal, reminders, workout generator and guided workouts, per-exercise trend charts, lifting speed
  per side, a camera-view setup gate) are built and tested on JVM, JS and Android: **3,069 test
  executions, none failing**.
- The exercise verifier (P9 Part B) is built and evaluated on the development people, and does not
  ship: on new people it warned on too many correct repetitions (P3-59).
- On the owner's phone (Nothing Phone 2a, Android 16) the live screen, curl counting, set saving,
  account sync, Health Connect and the export have run. **Everything built since P3-47 - the whole
  new look and every feature after it - has not yet been seen on a device.**
- Still owed: the rest of the phone session (`docs/P4_TOMORROW.md`), then deciding whether to show
  the cited technique rules, and the evaluation write-up (P12).
- P3-52 to P3-58 were written by another AI agent; P3-59 reviewed and corrected them. Its log is
  `docs/antigravity_works.md`.

**`docs/HANDOVER.md` is the complete account of the project: history, rules, results, what is
done and what must be done next. Start there.** `docs/PROJECT_STATUS.md` is the shorter status
snapshot, and `docs/DECISIONS.md` has the reasons and every measured number.

## Read in this order

1. **`CLAUDE.md`** — working rules. Every session starts here.
2. **`docs/HANDOVER.md`** — the complete account of the project, for a newcomer or another AI.
3. **`docs/DECISIONS.md`** — the dated decision record. Highest authority.
4. **`docs/CONTRACTS.md`** — the type-level contracts.
5. **`docs/ZERO_DATASET_BUILD_PLAN.md`** — current scope and evaluation plan.
6. **`docs/FINAL_ARCHITECTURE.md`** — the architecture review it builds on.
   Contains superseded sections; `DECISIONS.md` P0-8 lists them.

`docs/REPOSITORY_STRUCTURE.md` explains the layout.
`docs/G1_SPIKE_RESULT.md` holds the multiplatform viability evidence.

## Build and test

Gradle needs a JDK. Android Studio's bundled one works:
`JAVA_HOME` = `<Android Studio>/jbr`.

```
gradlew assemble allTests :app:testDebugUnitTest
```

Compiles every target and runs every JVM, JS and Android unit test. That
includes the JVM/JavaScript golden-file parity suite (`EngineParityTest`) and
the per-frame allocation guards, which run in a JVM of their own
(`jvmAllocationTest`). Android Lint is excluded because it cannot run on this
machine's JDK 25 — `docs/DECISIONS.md` P1-1. The `js()` target is not optional:
it carries the cross-platform parity result.

**On a phone** (USB debugging on):

```
gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

These run on the device: the pose model, rotation, clipping, frame conversion
and Health Connect tests. Some open the camera; none records anything. The
flag keeps the app installed, so a sign-in survives the run. Without it the
app is uninstalled afterwards.

- **Frame-rate benchmark**, opt-in: add
  `-Pandroid.testInstrumentationRunnerArguments.class=com.kinesis.camera.FramePipelineBenchmark -Pandroid.testInstrumentationRunnerArguments.kinesisBenchmark=true`.
  For a non-debuggable build, use `:app:connectedProfilingAndroidTest
  -Pkinesis.testBuildType=profiling`. See `DECISIONS.md` P3-19 §5 for the
  optional local inputs.
- **Firestore security rules**, on the Firebase emulator (needs Node and a JDK):
  `cd tools/firestore-rules && npm install && npm test`.
- **Regenerating the parity golden file**, only on purpose after an intended
  engine change: `KINESIS_WRITE_GOLDEN=1 gradlew :core:analysis:jvmTest --tests
  "*EngineParityGoldenWriter*"`, then review the diff.

## Licences

The app shows its third-party notices on its **LICENCES** screen, and ships the
Apache License 2.0 text itself (`app/src/main/assets/licenses/APACHE-2.0.txt`),
as that licence asks. The notices cover:
- **the pose model**, MediaPipe's BlazePose GHUM 3D Lite, bundled unmodified
  under Apache 2.0. Its model card is in `docs/third_party/`.
- every direct dependency, with the licence its POM declares, naming the ones
  that are not plain Apache 2.0: Firebase Authentication (Google's Android SDK
  licence), and the BSD-licensed components: libyuv (in CameraX) and Protocol
  Buffers (through MediaPipe, Firestore and the Health Connect client). Their
  full BSD 3-Clause texts ship in `app/src/main/assets/licenses/`, taken from
  each project's own repository (`DECISIONS.md` P3-21).

## The three things that matter most

- **Bilateral signals are never averaged.** Left and right stay separate.
- **All analysis lives in `core/analysis/src/commonMain`.** Platform code holds camera,
  rendering and storage — no decisions about movement.
- **Measure, then claim.** No number appears anywhere until it has been measured
  on this system.
