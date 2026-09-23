# Kinesis — Project Status

*Snapshot updated 2026-09-23, from the repository as it stands at the end of
DECISIONS.md P3-16. This file summarises. It is not an authority: where it
disagrees with `docs/DECISIONS.md`, DECISIONS.md is right and this file is out
of date.*

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
sits a working Android app shell — sign-in, an exercise library, a history
dashboard with streaks, Health Connect context, local Room storage and Firebase
sync. **The two are now connected** (DECISIONS.md P3-15, P3-16): the live screen
runs camera → MediaPipe → pose adapter → shared core for both repetitions and
holds, draws the skeleton over the preview, and saves each finished set to
storage, where the dashboard, streaks and sync pick it up. **But none of it has
run on a phone.** No real repetition has been counted from a real camera; every
test so far is synthetic, on the JVM and JS. The device reality check (P4) is
the next thing. After that come calibration, the technique-rule engine, the ML
layer and the six evaluation studies — the parts the MSc report actually rests
on.

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
| P4 Device Reality Check | Frame rate, thermal, mirroring, landmark reliability on a real phone | ⛔ Not started — no longer blocked; needs a physical phone (MediaPipe ships no x86_64 library, so standard emulators cannot run it) |
| P5 RepRecord & Export | Record types, persistence, JSON/CSV export | 🟡 Partial — records and Room storage done; JSON/CSV export not built |
| P6 Calibration & Personalisation | `CalibrationBaseline`, personal thresholds | ⛔ Not started |
| P7 Dataset pipeline | MM-Fit → MediaPipe → evaluation harness | 🟡 Partial — MM-Fit downloaded and its labels read (P3-1); MediaPipe-over-video pipeline and harness not built. It must apply the same aspect-ratio correction as the phone, with MM-Fit's own frame size (P3-16) |
| P8 Form Engine | Layer 1 deviation rules, Layer 2 cited technique rules | ⛔ Not started |
| P9 ML Layer | Exercise verifier, Layer 4 personal anomaly detection | ⛔ Not started (may be trimmed) |
| P10 Visualisation & Android | App UI, live screen, dashboard | 🟡 Partial — shell, dashboard, streaks, Health Connect, sync, theme and the live camera screen (reps and holds, sets saved) done but never run on a device; visualisations not built |
| P11 Windows PWA | React web app | ✂️ Cut (DECISIONS.md P0-2). The JS *target* survives; the web *app* does not |
| P12 Evaluation & Documentation | Six studies, final write-up | ⛔ Not started |

### Rough overall figure

Counting a done phase as 1 and a partial phase as ½, across the 12 phases still
in scope: **4 done + 3 × ½ = 5.5 of 12, roughly 45%.** Unchanged by P3-15 and
P3-16: wiring the camera advances P10 within its partial status and unblocks P4,
but completes neither.

Treat that as an estimate, not a measurement. Phases are not equal in size, and
the unfinished half contains the heaviest academic work — the form engine, the
ML layer and all six evaluation studies. The engineering foundation is well
past half; the thesis is well short of it.

### Test suite

Last full run (DECISIONS.md P3-16): **1,078 test executions, 0 failures, 0
errors**, via `gradlew assemble allTests :app:testDebugUnitTest` — core tests
count once per target (JVM, JS, both Android variants), so this is executions,
not distinct tests. `:app` holds 96 of them. Everything is synthetic: no test
has seen a camera frame, a model or a device.

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
| **Live analysis screen** | Front-camera preview with the skeleton drawn over it, portrait only. Repetition exercises show rep count, movement phase and tracking state; holds (plank, wall sit, side plank) show time held, hold state and tracking state. **[ END SET ]**, or leaving the screen, finishes the set — an unfinished repetition is recorded as incomplete — and saves it locally, then to sync. Missing model, refused camera permission and an unsuitable camera are each shown as themselves. **Built and unit-tested; never run on a phone.** |

### What the engine can do (built and tested; wired to the camera, never run on a device)

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

### Next — the device reality check (P4 / Phase B)

Needs a physical phone: MediaPipe ships no x86_64 library, so a standard
emulator cannot load it.

1. First run on a real phone (Nothing Phone 2a): sustained frame rate,
   per-frame latency, memory, 15-minute thermal run. Each frame currently costs
   two bitmap allocations before MediaPipe.
2. Confirm front-camera mirroring live — the one failure that produces no
   visible symptom, only silently swapped left/right numbers. Raise the left
   arm; the left-side values must move.
3. Confirm the skeleton lies on the body, and that a known angle measures true
   on a real frame (the aspect correction is only synthetic-tested).
4. Real rep counts against real movement; real hold times; landmark reliability
   per exercise and camera view. Watch for side-on profiles (squat, plank) whose
   far-side landmarks may read "not visible" all set while the near side is
   measured.
5. **Measure per-frame angle change at the real frame rate.** The outlier gate
   rejects any sustained jump of more than 20° and never recovers from it until
   the set ends (P3-16), so a fast movement at a low frame rate could lock one
   side out.
6. First real Firebase round trip (no Firebase call has ever executed), including
   the security-rules check above.
7. Permission dialog, refusal, backgrounding, leaving mid-set — and that the set
   is saved on each exit path.
8. Tune the provisional constants (`smoothingAlpha`, `VisibilityPolicy`
   thresholds, hold tolerances) against device data; P2-4's ablation showed
   smoothing lag, not noise, is the binding constraint.
9. Read and record the pose model's licence (model card). The
   `pose_landmarker_lite.task` file is in `assets/` but deliberately **not
   committed**, because its terms are not yet recorded in DECISIONS.md. Any APK
   built from this tree packages it.

### Then — the research core

10. **P5 export** — JSON and CSV export of sessions (required by CLAUDE.md §15).
11. **P6 calibration** — build `CalibrationBaseline` and `AnalysisResult`,
    personal baselines from calibration reps, personal thresholds. This also
    unlocks "deviation from your normal" on the dashboard, which is deliberately
    blocked until a baseline exists.
12. **P7 dataset pipeline** — run MediaPipe over MM-Fit's RGB video, align with
    its set boundaries and rep counts, build the evaluation harness. **Apply the
    same aspect-ratio correction the phone applies, using MM-Fit's own frame
    size** (CONTRACTS.md §4), or the evaluation measures a differently distorted
    angle and does not transfer to the phone.
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

- Golden-file JVM/JS parity tests (the facade exists; the parity suite does not).
- Python ↔ Kotlin numeric parity, wherever ML is used.
- Record a decision for dropping **Front Raise** — it was removed from the
  library without a DECISIONS.md entry (flagged by the audit).
- A live session holds exactly one set, because saved sessions are append-only;
  the dashboard's "sessions" count therefore counts live sets (P3-16).
- A set is lost if the app is killed while it is under way, and a save that
  fails after leaving the screen is not reported anywhere (P3-16).
- Normalised coordinates still have no consumer on the real-time path; whether
  normalisation belongs in CONTRACTS.md §7's per-frame order is open (P3-15).
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
| CameraX (core, camera2, lifecycle, view) | 1.4.1 | Camera capture | P3-13 |
| MediaPipe Tasks Vision | 0.10.21 | On-device pose estimation, `pose_landmarker_lite.task` model. Google's telemetry backend is excluded so it adds no network permission. | P3-13 |
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
