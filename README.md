# Kinesis

Camera-based personal movement analysis. Android, Kotlin Multiplatform,
MediaPipe Pose.

The system measures movement, personalises its thresholds to the individual, and
applies technique rules drawn from published sources. It is not a medical
diagnostic system.

## Status

**P0–P3 complete; the camera is wired to the live screen; P4 (device testing)
not started.** The shared analysis engine, all 15 profiles and 2 variants, the
Android app shell, local storage and sync are built and tested on JVM and JS.
The live screen runs camera → MediaPipe → shared core for repetitions and holds
and saves finished sets, but **none of it has run on a phone yet** — that is P4.
See `docs/PROJECT_STATUS.md` for the current state and `docs/DECISIONS.md` for
why.

## Read in this order

1. **`CLAUDE.md`** — working rules. Every session starts here.
2. **`docs/DECISIONS.md`** — the dated decision record. Highest authority.
3. **`docs/CONTRACTS.md`** — the type-level contracts P1 implements.
4. **`docs/ZERO_DATASET_BUILD_PLAN.md`** — current scope and evaluation plan.
5. **`docs/FINAL_ARCHITECTURE.md`** — the architecture review it builds on.
   Contains superseded sections; `DECISIONS.md` P0-8 lists them.

`docs/REPOSITORY_STRUCTURE.md` explains the layout.
`docs/G1_SPIKE_RESULT.md` holds the multiplatform viability evidence.

## Build

```
gradlew assemble allTests :app:testDebugUnitTest
```

Compiles every target and runs every test on JVM, JS and both Android variants.
Android Lint is excluded because it cannot run on this machine's JDK 25 —
`docs/DECISIONS.md` P1-1. Both core targets must stay green. The `js()` target
is not optional — it carries the cross-platform parity result.

## The three things that matter most

- **Bilateral signals are never averaged.** Left and right stay separate.
- **All analysis lives in `core/analysis/src/commonMain`.** Platform code holds camera,
  rendering and storage — no decisions about movement.
- **Measure, then claim.** No number appears anywhere until it has been measured
  on this system.
