# Kinesis — Repository Structure

*P0, 2026-09-19.*

```
kinesis/
├── CLAUDE.md                  Working rules. Read first, every session.
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml     Single source of truth for versions
│
├── docs/
│   ├── FINAL_ARCHITECTURE.md       Architecture decision (converted)
│   ├── ZERO_DATASET_BUILD_PLAN.md  Revised scope (converted, supersedes above)
│   ├── DECISIONS.md                Dated decision record — highest authority
│   ├── CONTRACTS.md                Type-level contracts P1 implements
│   ├── REPOSITORY_STRUCTURE.md     This file
│   └── G1_SPIKE_RESULT.md          Multiplatform viability evidence
│
├── app/                       Android application
│
├── core/                      Container only — its build file is empty by design
│   ├── domain/                Domain types.    targets: androidTarget, jvm, js
│   └── analysis/              THE ANALYSIS ENGINE — all analysis lives here
│       └── src/
│           ├── commonMain/kotlin/   Kinematics, FSM, profiles, rules, calibration,
│           │                        and the @JsExport facade (KinesisEngine.kt)
│           ├── commonTest/kotlin/   Guard tests and the JVM/JS parity suite — every target
│           └── jvmTest/kotlin/      JVM-only guards, and the parity golden-file writer
│
├── desktop/                   JVM module; candidate home for the replay client
│
├── tools/
│   ├── g1-spike/              Standalone G1 viability spike (see G1_SPIKE_RESULT.md)
│   └── firestore-rules/       firestore.rules unit tests on the Firebase emulator (P3-19)
│
└── Kinesis — *.docx           Original exports, kept for provenance
```

## The one rule this layout encodes

Everything analytical is in `core/analysis/src/commonMain`. Platform source sets
hold camera acquisition, the pose adapter, mirroring correction, rendering and
storage — nothing that decides anything about movement.

The `@JsExport` facade, `KinesisEngine`, is the only exported surface in the
project. It lives in `core/analysis/src/commonMain`, not `jsMain`, so that the
JVM runs the very facade JavaScript runs and the parity suite can compare them
(`DECISIONS.md` P3-19). `SharedCoreSourceGuardTest` fails if `@JsExport` appears
anywhere else.

All three modules carry `androidTarget`, `jvm` and `js` targets. The `js()`
target is not optional — it carries the cross-platform parity study. See
`DECISIONS.md` P0-2 and P0-9.

`core/` is a Gradle project but a container: its build file is empty on purpose,
because Gradle evaluates intermediate projects and anything declared there would
apply to a project with no sources. `core/src/` is a P0 leftover that nothing
reads and can be deleted.

## What is deliberately absent

| Missing | Why | When |
|---|---|---|
| `web/` — React client | PWA cut; only the JS target survives | If ever, P11 |
| Any Kotlin source in the core modules | That is P1 | P1 |
| Exercise profiles | Three archetypes first, per `CLAUDE.md` §9 | P3 |
| ML, Python tooling, dataset pipeline | Out of P0 scope | P7, P9 |

## Open sequencing question

`app/` exists as a Compose scaffold, but **P4 is the device reality check and
the Zero-Dataset plan puts it in week 3** — so a bare instrumentation harness
is needed early: CameraX, MediaPipe, skeleton draw, frame-rate readout, no
product UI. FINAL_ARCHITECTURE's phase list does not say which phase builds it,
and P10 "Visualization and Android" is far too late.

Recommendation for P1: treat `app/` as that harness first and the product shell
later. Decide at the start of P1 and record it in `DECISIONS.md`.

## Version catalog

All versions live in `gradle/libs.versions.toml`. No module declares a version
inline.

**These versions are unverified.** P0 ran in a sandbox with no access to Maven
Central, so nothing here was resolved against a repository. The first `gradle
build` on your machine is what confirms them — if resolution fails, the fix is a
version bump in the catalog, recorded in `DECISIONS.md`, not a change anywhere
else.

Per `CLAUDE.md` §17, no dependency beyond the Kotlin Multiplatform plugin and
the Kotlin test library is declared at P0. MediaPipe, CameraX, Compose, Room and
everything else arrive in the phase that needs them, each with its licence
checked at that point.
