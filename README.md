# Kinesis

Camera-based personal movement analysis. Android, Kotlin Multiplatform,
MediaPipe Pose.

The system measures movement, personalises its thresholds to the individual, and
applies technique rules drawn from published sources. It is not a medical
diagnostic system.

## Status

**P0 complete.** Architecture locked, contracts specified, repository structure
defined. No analysis code written yet — that is P1.

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
gradle :core:jvmTest :core:jsNodeTest
```

Both targets must stay green. The `js()` target is not optional — it carries the
cross-platform parity result.

Versions are unverified against a repository; see `docs/REPOSITORY_STRUCTURE.md`.

## The three things that matter most

- **Bilateral signals are never averaged.** Left and right stay separate.
- **All analysis lives in `core/src/commonMain`.** Platform code holds camera,
  rendering and storage — no decisions about movement.
- **Measure, then claim.** No number appears anywhere until it has been measured
  on this system.
