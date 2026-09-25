# Kinesis

Camera-based personal movement analysis. Android, Kotlin Multiplatform,
MediaPipe Pose.

The system measures movement, personalises its thresholds to the individual, and
applies technique rules drawn from published sources. It is not a medical
diagnostic system.

## Status

**P0–P3 complete. P4 — device testing on the owner's phone — is under way.**
The shared analysis engine, all 15 profiles and 2 variants, the Android app,
local storage and account sync are built and tested on JVM, JS and Android.

On a real phone (Nothing Phone 2a, Android 16) the live screen now runs on both
cameras, turns with the phone either way up, and saves sets. Sessions sync to
the signed-in account, and Health Connect's daily context reads. Verified so far:
the P3-18 launch crash fixed, the live pipeline running, and sync end to end.
The benchmarks put MediaPipe's pose model at about 55–57 ms a frame on that
phone. Still owed: the camera session with a person exercising — left and right
per camera, rep counting, hold timing, heat, frame rate in good light, and
tuning.

`docs/PROJECT_STATUS.md` has the current state, and `docs/DECISIONS.md` —
P3-18 onward for P4 — has the reasons and every measured number.

## Read in this order

1. **`CLAUDE.md`** — working rules. Every session starts here.
2. **`docs/DECISIONS.md`** — the dated decision record. Highest authority.
3. **`docs/CONTRACTS.md`** — the type-level contracts.
4. **`docs/ZERO_DATASET_BUILD_PLAN.md`** — current scope and evaluation plan.
5. **`docs/FINAL_ARCHITECTURE.md`** — the architecture review it builds on.
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
  licence), and the BSD-licensed components that CameraX and the Health Connect
  client bundle. **Those BSD notice texts are not yet shipped**, and they must
  be before any build is distributed (`DECISIONS.md` P3-19 §9).

## The three things that matter most

- **Bilateral signals are never averaged.** Left and right stay separate.
- **All analysis lives in `core/analysis/src/commonMain`.** Platform code holds camera,
  rendering and storage — no decisions about movement.
- **Measure, then claim.** No number appears anywhere until it has been measured
  on this system.
