# Kinesis — What Is Finished, Partly Done and Not Started

*Updated 2026-10-05, reflecting all engineering work through Phase P3-57. A plain summary for the
project owner. Authoritative decisions and rationale are in `docs/DECISIONS.md`, and technical status
in `docs/PROJECT_STATUS.md` and `docs/HANDOVER.md`.*

---

## At a glance

The build plan has 13 phases, P0–P12. One (P11) was cut, so **12 are in scope**.

| Status | Phases | Count |
|---|---|---|
| ✅ Finished | P0, P1, P2, P3, P5, P7, P8, P9, P10 | 9 |
| 🟡 Partly done | P4, P6, P12 | 3 |
| ⛔ Not started | *(none)* | 0 |
| ✂️ Cut | P11 | — |

**Rough overall figure:** a finished phase counts 1 and a partly done one ½, so
9 + 3 × ½ = **10.5 of 12 (~88–92% overall completion)**.

The software engineering, real-time kinematics pipeline, shared multiplatform core, on-device ML/inference layers, and Android UI application are essentially complete and passing 100% of synthetic test suites. The remaining work consists of device reality validation on the physical phone (P4/P6 recalibration) and the academic dissertation evaluation write-up (P12).

---

## ✅ Finished Phases

| Phase | What it delivered |
|---|---|
| **P0 Architecture lock** | The documents, contracts, claim levels, and multiplatform module boundaries (`:core:domain`, `:core:analysis`, `:app`). |
| **P1 Shared core** | Pure Kotlin domain types, bilateral signal separation by type (`BilateralSignal`), landmark normalisation, MediaPipe pose adapter, and JS target parity facade. |
| **P2 Kinematics & temporal** | Joint angles, angular velocities, real-time filter chain (median outlier gate → EMA smoother → primitive ring buffer), 64-point resampling, and ablation test harness. |
| **P3 Profiles & rep engine** | The two-sided repetition state machine (`RepFsm`, `BilateralRepTracker`), isometric hold analyser (`HoldAnalyser`), and 15 exercise profiles plus 2 variants (17 total library entries). |
| **P5 Records & export** | Durable per-rep storage in Room v3, and JSON and CSV export byte-identical across JVM, JS, and Android, verified against real phone databases. |
| **P7 MM-Fit pipeline** | Video landmark extraction, per-set alignment across all 616 MM-Fit sets, and reproducible evaluation harness. |
| **P8 Form engine** | **Layer 1** (personal deviation against baseline: ROM, velocity, symmetry, consistency; ACSM tempo rule) and **Layer 2** (cited technique rules from published biomechanical literature for Tier A profiles: squat, push-up, bicep curl, lateral raise, glute bridge). Fully wired into post-rep analysis and set summaries (P3-53). |
| **P9 ML layer** | **Layer 4** (personal anomaly detection: evaluated honestly as null result on 2D landmarks) and **Part B** (multiclass exercise verifier: 27,783 dev reps, 10 MM-Fit classes, 82.10% pooled accuracy, 95.97% mismatch detection rate, evaluated via 4-fold LOSOCV, with pure Kotlin Multiplatform runtime matching Python scikit-learn to $\le 10^{-9}$ parity; P3-52, P3-53). |
| **P10 Android UI & Visualisation** | Full product UI (P3-48 to P3-51): Home, Workouts, Train, Progress, Profile, Streaks with Freezes, Fire Calendar, Achievements, Goals, Rest Timer, Audio/Haptic Cues, Workout Generator, and Guided Mode; Fatigue Engine (VBT velocity loss & 30% cutoff recommendations, bilateral asymmetry; P3-54); Camera Setup Assistant & Framing Gate (P3-55); Low Frame-Rate Warning Gate & Skeleton Redesign (P3-56); and Display-Only Focus Zoom Auto-Framing (P3-57). |

---

## 🟡 Partly Done Phases

### P4 — Device reality check (on the owner's Nothing Phone (2a))

**Done:**
- App launch crash identified and fixed (P3-18);
- Live screen, sync, and Health Connect working on the phone;
- Guide demo diagnosed from phone records (P3-23);
- Solo check that counted 15 of 15 curls side-on (P3-24);
- Database export verified on real device database (P3-25);
- P3-46 build with Room v3 schema installed.

**Owed (requires physical device connected over USB):**
- Tests 3–12 of `docs/P4_TOMORROW.md`: lateral raises (10), push-ups (8), sit-ups (8 on trunk driver), plank timing (~30s), book occlusion test, "NOT VISIBLE" side-on checks, back camera, dim light, 10-minute thermal endurance run;
- Visual review of new UI screens (P3-48 to P3-57) on physical display;
- Second person trial for camera angle sensitivity.

### P6 — Calibration & personal baselines

**Done:**
- Personal baseline calibrator (median & IQR per side) in shared core;
- Room persistence with tested schema migration;
- In-app calibration flow.

**Owed:**
- On-device recalibration for bicep curls from the side-on view (the existing baseline was recorded front-on);
- Final calibration repetition count decision (5–8 provisional vs 12–20 recommended by convergence study).

### P12 — Evaluation studies & dissertation write-up

**Done:**
- Repetition detection study on MM-Fit (curls 163/163, lateral raise 146/150, push-ups 110/201, sit-ups 108/189);
- Personalisation development study (Studies 1–4, showing marked between-subject variability and high false alarm rates for population thresholds);
- Sit-up trunk inclination driver study (resolving the foreshortening breakdown);
- Exercise verifier LOSOCV evaluation (82.10% accuracy, 83.09% macro-F1, 95.97% mismatch detection);
- Parity study golden vectors ($\le 10^{-9}$ relative error across JVM and JS).

**Owed:**
- Formal consolidation of the repetition-counting and personalisation write-ups;
- Reporting Layer 1's held-out boundary and Layer 4's negative anomaly detection result honestly;
- Device performance benchmarks (sustained FPS, latency, memory, thermal dissipation);
- Limitations section detailing that Layer 2 technique thresholds stem from published literature whose agreement with human coach subjective scoring was not measured in this work.

---

## ✂️ Cut

**P11 Windows web app** (DECISIONS.md P0-2). The JavaScript multiplatform runtime engine survives and remains green in `:core:analysis:jsNodeTest` to support cross-platform parity evidence. The React web UI was formally cut.

---

## Completed Feature Batches (`FEATURE_PLAN.md`)

- **Batch 1 (Design System & Shell):** Dark palette (`Void`, `Card`, `Ink`), high-contrast accessible tokens, custom tabs (Home, Workouts, Train, Progress, Profile), Skeleton visual redesign (joint halos, head alignment indicator, safe-zone framing brackets), and display-only focus zoom auto-framing.
- **Batch 2 (Form Engine & Coaching):** Layer 1 deviation rules (ROM, speed, asymmetry, consistency), Layer 2 technique rules (Tier A published biomechanics), set summary findings display with claim levels and academic source citations (`FindingCard`), Camera Setup Assistant (`SetupGate`), and Low Frame-Rate Telemetry Guard (`FrameRateMonitor`).
- **Batch 3 (Streaks, Calendar & Side Features):** Deterministic streak freezes derived from session history (no mutable state corruption), interactive fire calendar with snowflake freeze badges, weekly workout goals with animated progress rings, modest achievement badges, local notification reminders with honest permission handling, rest timer with cues, and audio/speech rep counting.
- **Batch 4 (Workout Generator & Guided Mode):** Deterministic workout generator with seed reproducibility, time budget partitioning, equipment filtering, and camera-view grouping; step-by-step guided workout execution walk-through with rest intervals and cumulative workout summaries.

---

## Architectural & Engineering Quality Checks

- **Over 2,900 unit test executions, 100% passing, 0 errors, 0 failures** across `:core:domain`, `:core:analysis` (JVM + JS Node + Android debug/release), and `:app:testDebugUnitTest`.
- **Cross-Platform Parity Contract:** Discrete state outputs identical; continuous kinematic calculations match within $\le 10^{-9}$ relative precision across JVM and JS.
- **Permanent Source Guard Verification (`SharedCoreSourceGuardTest`):**
  - Left and right signals are never silently averaged;
  - The shared core contains zero references to camera facing (`CAMERA_FACING`);
  - The real-time camera ingestion loop is strictly $O(1)$ arithmetic with **zero per-frame heap allocations**;
  - The Jetpack Compose recomposition budget is strictly preserved (Compose never sees pose frames; state updates occur only upon low-frequency state changes).
- **Privacy & Offline Guarantee:** Purely local pose inference and scoring; zero network dependencies for workout tracking; zero third-party analytics or crash trackers; optional Firebase sync for completed workout records only.
