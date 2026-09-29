# Kinesis — Project Status

*Snapshot updated 2026-09-29, from the repository as it stands at the end of
DECISIONS.md P3-30. This file summarises. It is not an authority: where it
disagrees with `docs/DECISIONS.md`, DECISIONS.md is right and this file is out
of date.*

---

## Next session starts here

As of DECISIONS.md P3-30 and its addendum, "Owner decisions before the held-out
run".

**Done:**
- **The split is by subject:** development A, B, C and w19's subject; held-out
  w12, w13, w16, w17, w18, w20, untouched. `tools/mmfit-eval/subjects.py` holds
  it.
- **P8 Layer 1** is built in the core and **not wired into the app**.
- **The personalisation study's S1–S4** have run on the development subjects.
- **Decided in the addendum:**
  - uncited population thresholds never reach the user (6);
  - one message per side for depth (7);
  - the ACSM tempo message held back from the UI until the phone's hand-timed
    check, then shown once per set (2);
  - the sit-up floor stays at 114.4°, with a knee-independent trunk-angle driver
    added to P8's list (4);
  - S5 to run on development subjects A and B, "sessions of unknown spacing" (8).
- **Written and fixed before any run:** Decision 3's selection rule and S5's
  protocol.
- **Checked against the code:** `docs/EXERCISE_GUIDE.md`'s counting rules.
- **Pushed.**

**Open, in this order:**
1. **The phone session:** `docs/P4_TOMORROW.md`. It also records the P3-29 curl
   test and the first real P6 calibration.
2. **The curl-timing trace on the development workouts** (decision 5): the
   one-frame lifts after a bridged start.
3. **The S5 run** (development subjects A and B only).
4. **The Decision 3 evaluation,** by the addendum's fixed selection rule.
   Calibration stays one set; if no candidate qualifies, report and stop.
5. **The frozen tag `p3-30-frozen`:** the engine, the chosen settings, P3-30's cuts
   and floors, and the tools, as the held-out run will use them.
6. **Then a separate approval for the held-out run** (decision 1).

**Also open, not in that sequence:**
- **P8:** Layer 2, the cited technique rules, and a knee-independent trunk-angle
  driver for the sit-up (development subjects only).
- **Squats:** the near-knee question, still untouched.
- **Feature Plan Batch 1** gains the skeleton redesign and a display-only focus
  zoom, both after the evaluation work (CLAUDE.md §21).

---

## 1. What Kinesis is

A camera-based personal movement-analysis app for Android. The phone camera
watches the user exercise, MediaPipe Pose turns each frame into 33 body
landmarks, and a shared Kotlin analysis engine counts repetitions, times holds,
and measures range of motion, tempo and left/right symmetry. Later phases add
personal calibration (the app learns *your* normal) and rule-based technique
feedback drawn from published sources.

It is explainable movement analysis, offline-first for everything that matters.
**It is not a medical diagnostic system.**

The research question the evaluation answers: *do personal baselines
outperform population thresholds for flagging movement deviation?*

---

## 2. Where it stands, in one paragraph

The analysis engine is built and heavily tested: normalisation, validity
gating, bilateral kinematics, filtering, the repetition state machine, the
isometric hold analyser, and all 15 exercise profiles plus 2 variants. Around it
sits a working Android app — sign-in, an exercise library, a history dashboard
with streaks, Health Connect context, local Room storage, Firebase sync, and the
live screen (camera → MediaPipe → pose adapter → shared core, skeleton drawn,
each finished set saved; P3-15, P3-16). Personal calibration (P6) is built into
the core and the app (P3-22), and the P5 export is done (P3-25).

**It has run on a real phone** — the owner's Nothing Phone (2a), Android 16 —
since P3-18: the launch crash found and fixed there (P3-18); the guide demo
diagnosed from the phone's own records, and the gap bridge built from its
recordings (P3-23); a solo check in which one set counted 15 of 15 curls
(P3-24); the export run over the phone's real database, 59 sessions, 134
repetitions and 4 holds matching exactly (P3-25); and the P3-28/P3-29 build
installed for the owner's curl check (P3-29). That is one person, one phone,
mostly the front camera, so the device reality check (P4) is still only partly
done. **P7 is done**: MM-Fit's 616 sets through the phone's own pipeline, with
repetition counts measured per exercise (P3-27 to P3-29). What is left is the
research core — the form engine (P8), the optional ML layer (P9) and the six
evaluation studies (P12), the parts the MSc report actually rests on.

---

## 3. How much is finished

### By phase

The build plan (`docs/ZERO_DATASET_BUILD_PLAN.md`) has 13 phases, P0–P12.

| Phase | What it is | Status |
|---|---|---|
| P0 Architecture Lock | Documents, contracts, module structure | ✅ Done |
| P1 Shared Core | Domain types, bilateral signal, claim levels, landmark model, pose adapter, JS facade | ✅ Done |
| P2 Kinematics & Temporal | Geometry, joint angles, velocity, ring buffers, filter chain, resampling, ablation harness | ✅ Done |
| P3 Profiles & Rep Engine | Rep FSM, bilateral rep tracker, hold analyser, 15 profiles + 2 variants | ✅ Done |
| P4 Device Reality Check | Frame rate, thermal, mirroring, landmark reliability on a real phone | 🟡 Partly done (DECISIONS.md P3-18 onward) — launch crash fixed, live screen and sync work, solo curl check done (P3-24). Still owed: back camera, left arm, tracking-loss test, hold timing, heat run, other lighting, a second person |
| P5 RepRecord & Export | Record types, persistence, JSON/CSV export | ✅ Done (DECISIONS.md P3-25) — records and Room storage; the export in the shared core (CONTRACTS.md §10), golden-tested byte for byte on JVM and JS, and run once on the phone: all 59 sessions, 134 repetitions and 4 holds matched the database exactly |
| P6 Calibration & Personalisation | `CalibrationBaseline`, personal thresholds | 🟡 Built, not yet run on the phone (DECISIONS.md P3-22) — per-side baselines from 5–8 valid reps (provisional), hold baselines, `AnalysisResult` as numbers beside the baseline, Room storage with a tested migration, the calibration flow. Personal *thresholds* are P8's rules. The MM-Fit convergence study (P3-30 S4, development subjects) says 5–8 repetitions do not settle a range-of-motion baseline (about 12–20 would); the count is the owner's decision |
| P7 Dataset pipeline | MM-Fit → MediaPipe → evaluation harness | ✅ Done (DECISIONS.md P3-27 to P3-29). The build plan defines P7 as: *"acquire MM-Fit, run MediaPipe over its RGB video, align set boundaries and rep counts, build the evaluation harness"* (`ZERO_DATASET_BUILD_PLAN.md`, phase table). All four were done in P3-27: the videos on disk, MediaPipe run with the phone's own model file, the labels aligned set by set (five workouts drift, by up to 6.8 s, and were corrected), and the `:mmfit-eval` harness running the live screen's own class. All 616 sets went through, and the rep-count study was measured for 7 exercises. The engine defect it exposed (a fast start is lost) is fixed and judged on the 13 held-out workouts in P3-28: curls 368 of 368, every set exact; push-ups and sit-ups now count from the near side. In P3-29 near-side counting stopped letting a failed attempt hide a repetition, and the lateral raise's start zone moved to 36°: held-out raises 125 → 356 of 369, with no set counted above its label in either change. The push-up and sit-up start zones stay as they are, with a personal start zone proposed (P8). The verifier and personalisation studies, once listed here, belong to P9 and P12 in the plan's own evaluation table, not to P7 |
| P8 Form Engine | Layer 1 deviation rules, Layer 2 cited technique rules | 🟡 Layer 1 built in the shared core (DECISIONS.md P3-30), **not wired into the app**: personal deviations (Level 2) in range of motion, lifting and lowering time, left–right balance and consistency; the cited ACSM tempo rule (Level 3, Tier A); the push-up and sit-up personal start zone. Layer 2, the cited technique rules, not started |
| P9 ML Layer | Exercise verifier, Layer 4 personal anomaly detection | ⛔ Not started (may be trimmed) |
| P10 Visualisation & Android | App UI, live screen, dashboard | 🟡 Partial — shell, dashboard, streaks, Health Connect, sync, theme, the calibration flow and the live camera screen (reps and holds, sets saved) done and run on the owner's phone (P3-18 onward); visualisations not built |
| P11 Windows PWA | React web app | ✂️ Cut (DECISIONS.md P0-2). The JS *target* survives; the web *app* does not |
| P12 Evaluation & Documentation | Six studies, final write-up | 🟡 Started: the repetition-count study measured under P7 (P3-27 to P3-29; on the six unseen people in P3-30), and the personalisation study's development half (P3-30, four subjects). Still to come: its held-out run, the verifier, device and pose-reliability studies, parity as a study, and the write-up |

### Rough overall figure

Counting a done phase as 1 and a partial phase as ½, across the 12 phases still
in scope: **6 done + 5 × ½ = 8.5 of 12, roughly 71%** (2026-09-29, end of
P3-30). The six done are P0, P1, P2, P3, P5 and P7; P4, P6, P8, P10 and P12 are
the five partial phases. Earlier the same day it read 7.5 of 12 (63%); P8 and P12
became partial in P3-30.

**A correction to the previous figure.** It read 5 done + 3 × ½ = 6.5 of 12,
54%, written at P3-25 when P6, P7 and P10 were the partial phases. P4 became
partial afterwards and the figure was not recomputed, so it left P4 out. Counted
the same way it should have read 5 + 4 × ½ = 7 of 12, 58%, before P7 was marked
done.

Treat that as an estimate, not a measurement. Phases are not equal in size, and
the unfinished half contains the heaviest academic work — the form engine, the
ML layer and all six evaluation studies. The engineering foundation is well
past half; the thesis is well short of it.

### Test suite

Last full run (DECISIONS.md P3-30): **2,078 test executions, 0 failures, 0
errors, 0 skipped**, via `gradlew assemble allTests :app:testDebugUnitTest
:app:testReleaseUnitTest` — core tests count once per target (JVM, JS, both
Android variants), so this is executions, not distinct tests. `:app` holds 181
of them in the debug variant and 169 in release; the MM-Fit tools
(`tools/mmfit-eval`) hold 12, and `test_subjects.py` 8 more in Python. Every test is synthetic: none
has seen a camera frame or a model. What has run on the phone is recorded in
DECISIONS.md, entry by entry, and is not a test count.

---

## 4. Features

### What a user can do in the app today

| Feature | Detail |
|---|---|
| **Account sign-in** | Email/password via Firebase Auth. Signed out, the app shows only the sign-in screen. A disclosure above the sign-in button states exactly what leaves the device (session counts, ranges, tempo, hold times, timestamps) and what never does (video, photos, camera frames, analysis). The signed-in email stays visible in the nav bar. |
| **Exercise library** | All 17 entries, read from the engine's `ExerciseLibrary`, each showing its required camera view, posture, whether it is a hold, its tier, and — for variants — its parent exercise. |
| **History dashboard** | Current and longest day streak; total sessions, valid reps and hold seconds; per-exercise rep and set counts; mean range of motion shown **left and right separately**, never averaged. |
| **Streaks** | Consecutive-day tracking over stored session timestamps, timezone-aware (DECISIONS.md P3-10). |
| **Daily health context** | Steps, active calories and mean heart rate from Health Connect, read-only, shown *beside* Kinesis's own numbers. Every absence is named honestly — not installed, needs update, permission denied, device too old, read failed — with an in-app "grant access" button. |
| **Cloud backup/sync** | Completed sessions sync to Firestore under the user's account. Local save always happens first; upload is scheduled afterwards by WorkManager and retried with backoff, so training is never lost to a bad connection. Merge on sign-in pulls history to a new device before the dashboard appears. |
| **Live analysis screen** | Camera preview with the skeleton drawn over it, portrait only, front camera by default with a switch to the back camera (switching starts a new set; the one in progress is saved). Repetition exercises show rep count, movement phase and tracking state; holds (plank, wall sit, side plank) show time held, hold state and tracking state. **[ END SET ]**, or leaving the screen, finishes the set — an unfinished repetition is recorded as incomplete — and saves it locally, then to sync. Missing model, refused camera permission and an unsuitable camera are each shown as themselves. Debug builds add a Phase B readout (frame rate, largest per-frame angle change, landmark visibility, active camera; Logcat tag `KinesisPhaseB`). **Run on the owner's phone since P3-18**; see P4 for what is still owed. |
| **Licences** | Linked from the history screen: the pose model's notice (Apache 2.0, from its model card), the app's direct dependencies with the licence each declares, and the full Apache License 2.0 text. |

### What the engine can do (built and tested; wired to the camera, run on the owner's phone since P3-18)

- **Pose adapter** — converts camera coordinates to anatomical left/right,
  undoing front-camera mirroring in exactly one place, and puts x and y in one
  unit so angles are not distorted by the frame's shape (P3-16). Both guarded by
  permanent regression tests.
- **Normalisation and validity gate** — decides per frame whether the landmarks
  a given exercise needs are reliable, marginal or missing, and blocks analysis
  on bad frames rather than producing confident nonsense.
- **Bilateral kinematics** — joint angles and landmark velocities, left and
  right kept structurally separate by the `BilateralSignal` type.
- **Real-time filter chain** — median outlier gate → EMA smoother → bounded ring
  buffer, O(1) per frame with no per-frame allocation (enforced by
  `RealTimeBudgetTest`). Gap interpolation and fixed-length resampling (N = 64)
  run post-repetition.
- **Repetition state machine** — IDLE → TOP → DESCENDING → BOTTOM → ASCENDING,
  with hysteresis bands, minimum/maximum repetition durations, and explicit
  ABORTED / INCOMPLETE / INSUFFICIENT_DATA outcomes instead of guesses.
- **Bilateral rep tracker** — one state machine per side; per-limb exercises
  (e.g. alternating curls) and simultaneous exercises (e.g. squats) combine
  cycles differently, as each profile declares.
- **Isometric hold analyser** — plank, wall sit and side plank never go through
  the rep counter; the type system makes that impossible.
- **15 exercise profiles + 2 variants** across three tiers (below).
- **Claim-level enforcement** — Tier B and C profiles cannot construct a
  technique (Level 3) finding. This is enforced by types, not UI wording.
- **Skeleton overlay** — a dedicated Canvas view with a four-state,
  colour-blind-aware palette (tracked / degraded / not visible / attention),
  fed directly from the pose callback so Compose never redraws per frame. The
  attention state now pulses (a widening stroke, 1 Hz), as P3-12 specified.
- **Named reducers where two sides become one** — the live readout's phase
  (`BilateralPhase.furthestThroughRepetition`, display-only) and the hold angle
  (`HoldReading.furthestFromTarget`), each pinned by tests.
- **JS facade** (`KinesisEngine`) — the single `@JsExport` surface for the
  cross-platform parity study.

### Exercise library

| Tier | Profiles | May claim |
|---|---|---|
| A | Squat, Push-up, Bicep Curl, Lateral Raise *(seated)*, Glute Bridge | Up to Level 3 (cited technique rules, once P8 exists) |
| B | Shoulder Press *(seated)*, Row *(near side only)*, Lunge, Hip Abduction, Knee Raise, Sit-up, Leg Raise | Up to Level 2 |
| C | Wall Sit, Plank, Side Plank *(holds)* | Up to Level 2 |

Variants inherit their parent's profile and calibration: **Goblet Squat →
Squat**, **Reverse Lunge → Lunge**. Honest count, as the report must state it:
*15 exercise profiles across 3 tiers and 2 analysis modes, presented as 17
library entries including 2 variants.*

---

## 5. What still needs to be finished

In the order it has to happen.

### Done since the last snapshot

- **Camera wired to the live screen** — DECISIONS.md P3-15 and P3-16. Repetitions
  and holds, skeleton overlay, portrait lock, sets saved through
  `SessionRecorder` → `SyncingSessionRepository`. Also fixed along the way: joint
  angles distorted by the frame's aspect ratio (up to ~16° on a 3:4 frame), a
  `NaN` visibility counted as fully seen, a squat unfinished at the end of a set
  recorded as "aborted", the dashboard ignoring daylight saving, and the
  attention pulse never drawn.
- **Firestore security rules published** by the project owner (P3-14
  follow-up). **Not yet tested against a real client**: nothing has shown that a
  user can reach their own sessions and not anyone else's, or that update and
  delete are refused. That check belongs with the first Firebase round trip
  below.
- **P3-17:**
  - A front/back camera switch, with a permanent guard that both cameras give
    the same anatomical left and right.
  - **The pose model's licence read and recorded**: Apache 2.0, card dated
    2021-04-16. The model file and card are now committed, with an in-app
    licences screen.
  - The outlier gate now re-admits a sustained change after three consistent
    frames instead of locking a side out.
  - A debug-only Phase B readout.
  - The model card's limits are recorded in P3-17: trained only on back-camera
    photos; out of scope beyond about 4 m, without the head visible, or with
    more than one person; z not metric.

### Next — the device reality check (P4 / Phase B)

Needs a physical phone: MediaPipe ships no x86_64 library, so a standard
emulator cannot load it. **The order for the next solo session is
`docs/P4_TOMORROW.md`.** The list below is the original P4 plan; parts of it
were done in P3-18 to P3-24, and DECISIONS.md P3-19's camera-session list is the
current, itemised version.

1. First run on a real phone (Nothing Phone 2a): sustained frame rate,
   per-frame latency, memory, 15-minute thermal run. Each frame currently costs
   two bitmap allocations before MediaPipe.
2. Confirm left and right live **with each camera separately** — the one
   failure that produces no visible symptom, only silently swapped numbers.
   Raise the left arm; the left-side values must move. Do it once with the front
   camera and once with the back: they are different code paths, and the back
   path has no mirroring anywhere.
3. Confirm the skeleton lies on the body **with each camera** (this also checks
   that CameraX mirrors the front preview and not the back), and that a known
   angle measures true on a real frame (the aspect correction is only
   synthetic-tested). Check that switching camera mid-set saves the first set
   and starts a new one.
4. Work inside the model card's conditions — one person in shot, head in frame,
   within about 4 m — and compare front and back at the same distance: the model
   was trained and evaluated only on back-camera photos.
5. Real rep counts against real movement; real hold times against a stopwatch;
   landmark reliability per exercise and camera view. Watch for side-on
   profiles (squat, plank) whose far-side landmarks may read "not visible" all
   set while the near side is measured — the debug readout's visibility range
   shows it directly.
6. **Read the debug readout's largest per-frame angle change against 20°.** The
   outlier gate now re-admits a sustained change after three consistent frames
   (P3-17), but a movement that stays faster than 20° per frame is still dropped
   while it lasts. Check real sudden changes are re-admitted, and the readout's
   frame rate against the device.
7. First real Firebase round trip (no Firebase call has ever executed), including
   the security-rules check above.
8. Permission dialog, refusal, backgrounding, leaving mid-set — and that the set
   is saved on each exit path.
9. Tune the provisional constants (`smoothingAlpha`, `VisibilityPolicy`
   thresholds, hold tolerances, `outlierReadmitSamples`) against device data;
   P2-4's ablation showed smoothing lag, not noise, is the binding constraint.

### Then — the research core

10. ~~**P5 export**~~ — done in P3-25 (CONTRACTS.md §10).
11. ~~**P6 calibration**~~ — built in P3-22 (`CalibrationBaseline`,
    `AnalysisResult`, the calibration flow); still owed: a first calibration on
    the phone. Personal *thresholds* are P8's.
12. ~~**P7 dataset pipeline**~~ — done in P3-27 to P3-29, with the aspect-ratio
    correction applied at MM-Fit's own 1280×720.
13. **P8 form engine** — Layer 1 deviation rules (ROM, tempo, asymmetry,
    consistency, drift vs. baseline) and Layer 2 Tier A technique rules, each
    carrying a `sourceRef` citation. The attention colour in the overlay is
    already reserved for these findings.
14. **P9 ML layer** — exercise verifier (supervised, MM-Fit, leave-one-subject-out)
    and Layer 4 personal anomaly detection (one-class model fitted on-device).
    Optional; may be cut.
15. **P12 evaluation — the six studies:**
    - Repetition detection accuracy vs. MM-Fit labels (616 sets, 6,160 reps)
    - Exercise verifier accuracy (if P9 survives)
    - Personalisation: variance decomposition (ICC), population vs. personal
      threshold flag rates, calibration convergence (how many reps are enough)
    - Device performance (from P4)
    - Pose reliability in deployment (from P4)
    - Cross-platform JVM/JS parity
    …and the final write-up with its limitations section.

### Loose ends

- ~~Golden-file JVM/JS parity tests~~ — built in P3-19 §8 (`EngineParityTest`).
- Python ↔ Kotlin numeric parity, wherever ML is used.
- ~~Record a decision for dropping **Front Raise**~~ — recorded in P3-19 §9:
  cut for FINAL_ARCHITECTURE §5's reason; it was never in the code.
- A live session holds exactly one set, because saved sessions are append-only;
  the dashboard's "sessions" count therefore counts live sets (P3-16).
- A set is lost if the app is killed while it is under way, and a save that
  fails after leaving the screen is not reported anywhere (P3-16).
- Normalised coordinates still have no consumer on the real-time path; whether
  normalisation belongs in CONTRACTS.md §7's per-frame order is open (P3-15).
- **The libyuv licence notice is owed before any build is distributed.**
  CameraX's `camera-core` bundles libyuv under a BSD licence, which requires its
  notice in a binary distribution; its text is not yet in the app (P3-17,
  correcting P3-13). **The same holds for Health Connect's repackaged Protocol
  Buffers (BSD-3), added with the P3-19 client upgrade.**
- The `:desktop` module is an empty placeholder (intended home for a replay-only
  client).
- Optional: an `abiFilters` restriction to arm64 would cut roughly 29 MB from
  the ~56 MB release APK.
- UI polish pass — deliberately deferred; lowest priority per CLAUDE.md §21.

---

## 6. What it is built with

### Platform and language

| | |
|---|---|
| Language | Kotlin 2.1.21 |
| Shared engine | Kotlin Multiplatform — Android, JVM and JS targets. All analysis logic lives in `commonMain`; nothing analytical is written twice. |
| Android | compileSdk / targetSdk 35, minSdk 24, Java/JVM target 17 |
| Build | Gradle (wrapper), Android Gradle Plugin 8.7.3, KSP 2.1.21-2.0.1, version catalog in `gradle/libs.versions.toml` |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, Activity Compose 1.9.3, Lifecycle 2.8.7. Skeleton drawn on a plain Android `View`/Canvas, not Compose. |
| Theme | Phosphor green `#39FF14` on near-black `#060A06`, monospace throughout; amber `#FFB000` for attention (never red, for colour-blind users) |

### Libraries

| Library | Version | Used for | Decision |
|---|---|---|---|
| CameraX (core, camera2, lifecycle, view) | 1.4.1 | Camera capture. Apache 2.0; `camera-core` also bundles libyuv under BSD. | P3-13, P3-17 |
| MediaPipe Tasks Vision | 0.10.21 | On-device pose estimation. Google's telemetry backend is excluded so it adds no network permission. | P3-13 |
| BlazePose GHUM 3D Lite model | card 2021-04-16 | `pose_landmarker_lite.task`, shipped unmodified. Apache 2.0. | P3-13, P3-17 |
| Room (+ KSP compiler) | 2.7.1 | Local session storage — the source of truth | P3-12 |
| Health Connect client | 1.0.0-alpha11 | Read-only steps, active calories, heart rate | P3-11 |
| Firebase BoM → Auth, Firestore | 33.7.0 | Account sign-in and session sync (the one deliberate exception to "no cloud") | P3-14 |
| WorkManager | 2.10.0 | Background, retrying sync uploads | P3-14 |
| JUnit | 4.13.2 | Android unit tests; `kotlin.test` for the shared core | — |

Every dependency went through the CLAUDE.md §17 check (purpose, licence, bundle
cost, offline impact, maintenance) and is recorded in DECISIONS.md.

### Data and tooling

- **MM-Fit** public dataset — the only external data. Used for rep-count
  semantics already (P3-1) and for the evaluation studies. No self-collected or
  self-labelled data exists or is planned.
- **G1 spike** (`tools/g1-spike`) — the P0 proof that the shared engine produces
  matching results on JVM and JS.
- Planned for P7/P12: Python for the MediaPipe-over-MM-Fit pipeline and the
  statistics.
- AI-assisted development with Claude, governed by `CLAUDE.md`.

### Module layout

| Module | Contents |
|---|---|
| `:core:domain` | Types: `BilateralSignal`, claim levels and findings, landmark model, exercise profile interface, `RepRecord`, `SessionRecord` |
| `:core:analysis` | The engine: geometry, kinematics, landmark normalisation/validity, temporal filters, rep FSM and tracker, hold analyser, profiles and library, dashboard/streak aggregation; `KinesisEngine` JS facade in `jsMain` |
| `:app` | Android: camera and pose source, pose adapter, Health Connect, Room storage, Firebase sync, Compose screens, skeleton overlay |
| `:desktop` | Empty placeholder |

---

## 7. Rules the project holds itself to

These shape every part of the code above and are why some things look slower
than they might:

- **Left and right are never averaged.** Every reduction is a named function.
- **All analysis lives in the shared core**; the Android app only captures,
  draws and stores.
- **Real-time path is O(1) per frame**; no ML per frame; Compose never redraws
  per frame.
- **Claim levels are enforced by types.** Technique rules must cite a published
  source, and no accuracy figure may be attached to them — there are no fault
  labels, so no such number exists.
- **Measure → test → document → then claim.** No number appears anywhere until
  it has been measured on this system.
- **Offline-first.** All analysis stays on the phone; recording works with no
  network; only finished session summaries sync, and the app says so.
