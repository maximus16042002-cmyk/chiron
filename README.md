# Kinesis (the app is called Chiron)

Camera-based personal movement analysis. Android, Kotlin Multiplatform,
MediaPipe Pose.

The system measures movement, personalises its thresholds to the individual, and
applies technique rules drawn from published sources. It is not a medical
diagnostic system.

## Status

As of 2026-10-14 (`docs/DECISIONS.md` P3-84): **about 88% of the build plan**, counted in
`docs/HANDOVER.md` section 7.1. What is left is on the phone. Phases are those of
`docs/ZERO_DATASET_BUILD_PLAN.md`.

| Phase | State |
|---|---|
| P0 Architecture, P1 Shared core, P2 Kinematics, P3 Profiles and rep engine | Done |
| P4 Device reality check | Partly: the app is in regular use on the owner's phone; `docs/P4_TOMORROW.md`'s recorded tests 3-12 are owed |
| P5 Records and export, P6 Calibration, P7 MM-Fit pipeline | Done. Calibration asks for 10-15 valid repetitions a side (P3-83) |
| P8 Form engine | Layer 2's cited technique rules are shown as "Form notes" (Tier A, P3-74). Layer 1's personal-deviation messages are built and not shown: they failed their held-out test (P3-42) |
| P9 ML | Built, studied, not shipped: personal anomaly detection (a negative result, P3-38) and the exercise verifier (too many warnings on correct repetitions for some people, P3-59) |
| P10 Android app | Done and on the phone |
| P11 Windows PWA | Cut (P0-2) |
| P12 Evaluation and report | Written |

- **Tests:** the shared engine, all 15 profiles and 2 variants, and the Android app are tested on
  JVM, JS and Android: **3,423 test executions, none failing** (P3-83).
- **On the owner's phone** (Nothing Phone 2a, Android 16), in use since the demonstration of
  2026-10-07 (P3-68). The build from P3-83 was installed on 2026-10-14 after a verified backup
  (P3-84). Since P3-67 the app has gained:
  - the squat and the lunge counting from the knee nearer the camera (P3-68, P3-71);
  - a camera-view check that advises rather than blocks (P3-68; `CLAUDE.md` section 10 records the
    relaxation);
  - sets and holds that survive the app being killed (P3-66, P3-72);
  - the live angle and a flash on each counted repetition (P3-76);
  - a verdict for every attempt, counted or the reason it was not (P3-78);
  - push-ups counted from the user's own start position once calibrated, which passed on MM-Fit's
    development and held-out people with no set counted above its label (P3-82).
- **Still owed:**
  - the plank, which was not credited in the owner's sessions (P3-70), and the sit-up, which counted
    nothing in its one attempt there, both to be checked on the phone;
  - recalibrating the bicep curl side-on: the phone's only baseline is from front-on curls;
  - `docs/P4_TOMORROW.md`'s recorded tests 3-12.
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
(`jvmAllocationTest`). Android Lint is run separately (`gradlew lint`); it could
not run on this machine's JDK 25 until AGP 8.9.1 — `docs/DECISIONS.md` P1-1, P3-61. The `js()` target is not optional:
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


## Download App

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/maximus16042002-cmyk/chiron/releases/download/v1.0-beta/app-debug.apk)

Or visit the [Latest Release](https://github.com/maximus16042002-cmyk/chiron/releases/latest) page.
