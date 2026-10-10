# Kinesis — Engineering Rules for AI Coding Sessions

Kinesis is a camera-based personal movement-analysis system: an Android app,
built around MediaPipe Pose, a profile-driven exercise-analysis engine, and
personal calibration. It is explainable movement analysis, offline-first.
**It is not a medical diagnostic system** — never describe or implement it as
one.

## Document precedence — read this before anything else

When documents disagree, the later one wins. Highest authority first:

1. **`docs/DECISIONS.md`** — the dated decision record. Every conflict that has
   been resolved is resolved there, with reasoning. Read it at the start of
   every session.
2. **`docs/ZERO_DATASET_BUILD_PLAN.md`** — supersedes the Final Architecture
   Decision on data collection, ML scope, gates, timeline and verdict.
3. **`docs/FINAL_ARCHITECTURE.md`** — supersedes the Master Development Plan.
4. **This file** — an operational distillation, not an authority. If it
   disagrees with the above, this file is wrong and must be corrected in the
   same session that finds the disagreement.

`docs/CONTRACTS.md` holds the type-level contracts. `docs/REPOSITORY_STRUCTURE.md`
explains the layout. The two `.docx` files at the root are originals kept for
provenance — never read those, read the markdown.

**FINAL_ARCHITECTURE contains superseded instructions** — subject recruitment,
labelled-rep targets, Layer 3 supervised classifiers, gate G3, inter-rater
agreement. `docs/DECISIONS.md` P0-8 lists them all. Do not act on them.

This file states rules as **MUST**, **SHOULD**, or **MUST NOT**. Anything
not stated here is not decided by this file — check the source documents
before assuming.

---

## 1. Platform architecture

- **MUST**: Android — Kotlin, Jetpack Compose, CameraX, MediaPipe Pose.
- **MUST**: Shared analysis core — Kotlin Multiplatform, `commonMain`, with
  `jvm()` and `js()` targets. All analysis logic is shared; nothing
  analytical is reimplemented per platform.
- **MUST**: the `js()` target exists and stays green. It carries the
  cross-platform parity study, which is a surviving evaluation result. The
  `@JsExport` facade is designed in P1, never retrofitted.
- **MUST NOT**: build the React PWA. It was cut — `docs/DECISIONS.md` P0-2.
  The JS *target* survives; the React *application* does not. These were
  previously conflated, and they are separable.
- **MUST NOT**: rewrite the shared engine in TypeScript, or duplicate
  kinematics/FSM/calibration/form logic in a second implementation, without
  an explicit, documented architecture decision.
- If the JS target ever fails to compile, the documented fallback is a
  replay-only web client consuming exported JSON — not a rewrite.

## 2. Where analysis logic lives — the core rule

- **MUST**: all analysis logic lives in the shared core (`commonMain`).
  This includes kinematics, temporal processing, exercise profiles, the rep
  FSM, isometric hold analysis, calibration, deviation rules, technique
  rules, DTW, feature extraction, the ML evaluator, and claim-level
  enforcement.
- **MUST**: platform-specific code is limited to camera acquisition, the
  pose adapter, mirroring/FOV handling, rendering/UI, and platform storage.
- **MUST NOT**: put any analysis decision in Android or web UI code.

## 3. Bilateral signals — non-negotiable

- **MUST**: left and right measurements stay separate everywhere, via an
  explicit type such as `BilateralSignal(left, right)`.
- **MUST NOT**: silently average left/right values, anywhere, ever.
- **MUST**: any reduction to a combined/bilateral output is an explicit,
  named operation at the call site — never implicit.
- **MUST**: treat any future change that could collapse left/right
  information as an architectural change requiring explicit review, not a
  routine edit.
- **MUST**: maintain a permanent automated test asserting bilateral
  integrity (see §21).

## 4. Camera mirroring

- **MUST**: the platform pose adapter converts camera coordinates to
  anatomical left/right before anything reaches the shared core.
- **MUST NOT**: let the shared core know or care whether the camera is
  front/back or mirrored/unmirrored — it always receives anatomical
  left/right landmarks.
- **MUST**: maintain a permanent regression test for front-camera
  mirroring (see §21).

## 5. Numeric rules

- **MUST**: use `Double` throughout the shared analysis core.
- **MUST NOT**: use `Float` for core analysis calculations, or `Long` in
  analysis paths where JS compatibility matters.
- **MUST NOT**: rely on exact cross-platform floating-point equality.
- **MUST**: discrete outputs (rep counts, movement phases, discrete fault
  flags) match exactly across platforms.
- **MUST**: continuous numeric outputs are compared using documented
  tolerances (target: agreement within ~1e-9 relative), not exact equality.

## 6. Real-time performance contract

Real-time path (per frame): landmarks → normalization → validity check →
current-frame kinematics → fixed-size/ring buffers → FSM/hold state.

- **MUST**: the real-time path is O(1) per frame.
- **MUST NOT**: iterate over complete session history per frame.
- **MUST NOT**: reprocess an entire signal history for every new frame.
- **MUST NOT**: run ML per frame — models consume a completed `RepRecord`,
  never individual frames.
- **MUST NOT**: perform heavy post-processing per frame.
- **MUST NOT**: drive Compose recomposition from pose frames; skeleton
  rendering goes to a dedicated Canvas/SurfaceView, separated from
  low-frequency UI state.

## 7. Post-rep path

Post-rep (after a completed repetition or hold): `RepRecord` → feature
extraction → deviation rules → technique rules → DTW/reference comparison
→ ML → explanation → persistence.

- **MUST**: heavy analysis happens only after a completed repetition/hold.
- **MUST**: ML consumes completed `RepRecord`-level information, never raw
  frames.

## 8. Claim levels — structural enforcement

Three claim levels: **Level 1** (measurement — rep count, ROM, tempo,
velocity, symmetry, consistency), **Level 2** (personal deviation — below
baseline, above baseline, unusual for you), **Level 3** (technique
judgement — trunk compensation, incomplete lockout, body-line deviation).

- **MUST**: claim levels are enforced structurally in the type system, not
  by UI wording.
- **MUST**: Tier B and Tier C exercise profiles are structurally unable to
  construct a Level 3 finding — this must be a compile-time or
  construction-time impossibility, not a convention.
- **MUST**: every `Finding` carries its claim level.
- **MUST**: maintain a permanent test proving a Tier B/C profile cannot
  emit a Level 3 finding (see §21).

## 9. Exercise scope and development order

- Target scope: **17 library entries, 15 exercise profiles, 2 variants.**
  Under schedule pressure this cuts to 15 entries / 13 profiles by dropping
  Side Plank and Hip Abduction — that is the fallback position, not the goal.
  Variants inherit their parent profile and calibration; they are not separate
  algorithms.
- Count honestly in the report and the viva: *15 exercise profiles across 3
  tiers and 2 analysis modes, presented as 17 library entries including 2
  variants that share a parent profile and calibration.* Never "17 exercises".
- Tier assignments are fixed in `docs/CONTRACTS.md` §3. Tier A is Squat,
  Push-up, Bicep Curl, Lateral Raise (seated), Glute Bridge. Lateral Raise and
  Shoulder Press are **seated**, matching MM-Fit — `docs/DECISIONS.md` P0-4.
- **MUST NOT**: artificially duplicate algorithms to inflate the exercise
  count.
- **MUST**: if the exercise list changes, record it in `docs/DECISIONS.md` —
  never change it silently.
- The three archetype profiles — Squat (bilateral repetition), Bicep Curl
  (alternating/unilateral repetition), Plank (isometric hold) — were built
  and tested first, per this section's original discipline.
- **Superseded by `docs/DECISIONS.md` P3-4**: the remaining twelve profiles
  are now built before the archetypes' own P4 device validation, at the
  explicit direction of the project owner, accepting the risk P3-4 states.
  Read P3-4 before treating this section's original ordering as current.
- **MUST NOT**: implement any profile, including these twelve, without guard
  tests written first — dropping the validate-before-scaffold order is not
  license to skip tests-first discipline.
- **MUST**: treat configuration-cost and validation-cost as separate for
  every new exercise — cheap to configure does not mean cheap to trust.
  Each new exercise needs explicit consideration of camera view, landmark
  reliability, movement signal, rep semantics, bilateral handling, and
  real-world testing.

## 10. Camera-view contract

- **MUST**: every exercise profile declares its required camera view.
- **MUST NOT**: silently analyse an exercise from an unsuitable view.
- **SHOULD**: the setup assistant validates framing, body visibility,
  approximate distance, view angle, and camera stability before scoring.
- **MUST**: when required setup conditions are clearly invalid, block
  scoring/technique analysis rather than produce confident nonsense.
- **Currently relaxed by `docs/DECISIONS.md` P3-68:** the app reports a wrong
  camera view without blocking, because the view estimator misjudged the owner's
  real setup on the phone. Blocking returns once its thresholds are measured
  there; the blocking path stays built and tested.

## 11. Analysis modes

- Two modes: **repetition** (generic rep FSM) and **isometric hold** (hold
  analyser) — e.g. plank, wall sit.
- **MUST NOT**: force an isometric exercise through the repetition FSM.
- **MUST**: both modes produce a compatible record model for downstream
  storage/analysis.

## 12. Deterministic-first philosophy

- **MUST**: prefer deterministic/statistical methods for kinematics,
  temporal filtering, rep counting, phase detection, ROM, tempo, velocity,
  symmetry, smoothness, calibration, reference-rep selection, DTW, and
  baseline deviation.
- **MUST NOT**: add ML to make Kinesis look more like an AI project. Use
  ML only where it solves a genuine learning problem the deterministic
  approach cannot.

## 12a. The analysis layers, and what each may claim

| Layer | What it does | Claim level | Evidence behind it |
|---|---|---|---|
| 1 — Deviation rules | ROM, tempo, asymmetry, consistency, within-set drift versus the user's own baseline | 1 and 2 | The user's calibration reps. No external data. |
| 2 — Technique rules | Exercise-specific form rules, Tier A only | 3 | **Cited published thresholds only. Not validated in this work.** |
| 3 — Supervised quality classifiers | *Removed from scope* | — | — |
| 4 — Personal anomaly detection | One-class model fitted per user at runtime | 2 | Unsupervised, no labels |

- **MUST**: every Layer 2 technique rule carries a `sourceRef` field, alongside
  `ruleVersion`, citing the published threshold it implements. **A rule with no
  traceable source does not ship.**
- **MUST NOT**: attach an accuracy, precision or recall figure to any Layer 2
  rule. There are no fault labels in this project, so no such number exists.
- The report's limitations section states, in these terms: *these rules
  implement thresholds from cited sources, and their agreement with expert
  human judgement was not measured in this work.*
- **Know what Tier A now means.** With no labels anywhere, Tier A is not
  "validated" — it is "cited technique rules were written for it." The tier
  system is still an honest scope boundary enforced by types, but do not claim
  the stronger version. See `docs/DECISIONS.md` P0-7.
- P8 module **8.6 "Rule validation against labels" is deleted**, not reduced.

## 13. Current ML scope — exactly three uses

1. **Exercise verifier** — multiclass classifier flagging a mismatch with
   the selected exercise. **MUST NOT** be used for automatic exercise
   selection.
2. **Personal anomaly detection** — One-Class SVM or Isolation Forest,
   fitted per-user, on-device, at runtime, to that user's own history.
   **MUST**: phrase output as personal unusualness, never as correctness.
3. **Exercise-specific supervised technique-quality classifiers** —
   **currently removed from scope.** No self-collected or self-labelled
   dataset is being created. **MUST NOT** be reintroduced without an
   explicit architecture revision.

- **MUST NOT**: use end-to-end video models, per-frame ML, unnecessary
  sequence models, or arbitrary third-party model stacks.

## 14. Public dataset rule

- **MUST**: Kinesis requires no self-collected or self-labelled training
  dataset. Public datasets may be used where their licences permit the
  intended use.
- **MUST**: the analysis engine consumes MediaPipe landmarks only.
- **MUST NOT**: feed MM-Fit's bundled OpenPose skeletons directly into the
  Kinesis engine — they are a different joint format (17 joints,
  OpenPose-derived) and are not interchangeable with MediaPipe's 33
  landmarks.
- **MUST**: for MM-Fit evaluation, the pipeline is: MM-Fit RGB video →
  MediaPipe Pose → Kinesis representation → compare against MM-Fit's
  published set-boundary/rep-count labels.

## 15. Data model

- Hierarchy: `SessionRecord` → `SetRecord` → `RepRecord` / `HoldRecord`,
  plus `CalibrationBaseline` and `AnalysisResult`.
- `RepRecord` represents measured movement. `AnalysisResult` represents
  interpretation produced by a specific rule/model version, kept separate
  so historical reps can be re-analyzed when rules or models change.
- **MUST NOT**: merge raw measurement and interpretation into one mutable
  object without a strong, documented reason.
- **MUST**: use fixed-length resampled signals for completed reps.
- Export formats: JSON, CSV. Persistence is local only.

## 16. Privacy and offline operation

**Amended in Batch 10 by explicit decision — `docs/DECISIONS.md` P3-14.** The
prohibition on authentication and a cloud database is lifted *for session-record
sync only*. Everything else in this section stands unchanged, and the carve-out
is deliberately narrow.

- **MUST**: keep all analysis on-device and offline. Landmarks, angles,
  repetitions, holds and every finding are computed on the phone.
- **MUST**: remain offline-first for recording. A session **MUST** save locally
  with no network available; sync is additive and never a precondition.
- **MAY**: use Firebase Authentication (email/password) and Cloud Firestore to
  sync `SessionRecord` history to the signed-in user's account. This is the
  entire permitted use.
- **MUST**: disclose in the UI that session data leaves the device. This project
  does not collect silently.
- **MUST NOT**: use cloud inference, video upload, server-side processing, or
  network dependencies beyond the Firebase artifacts P3-14 names.
- **MUST NOT**: add analytics, crash reporting or telemetry. P3-13 excluded
  Google's datatransport backend when MediaPipe pulled it in unasked; that
  exclusion stands.
- **MUST NOT**: store user video as part of normal product operation.
- **MUST**: keep `OfflineManifestGuardTest` able to fail. Its job is now "no
  network capability from anything other than the declared Firebase artifacts",
  not "no network capability at all" — weakening it further is an architecture
  change and needs its own decision entry.

## 17. Dependencies and licensing

- **MUST**: before adding any new dependency, check why it's needed,
  whether an existing capability already covers it, its licence, platform
  compatibility, bundle/runtime cost, offline compatibility, and
  maintenance status.
- **MUST NOT**: add a dependency without this check, and **MUST NOT**
  copy code from GitHub merely because it looks useful.
- **MUST**: for every reused component, determine code licence, model/
  weights licence, and dataset/training-data restrictions separately.
  Public visibility on GitHub is not permission to copy code or weights.
- **MUST NOT**: use components with incompatible licensing merely because
  they simplify implementation.

## 18. Testing — part of the architecture, not an afterthought

Permanent guard tests that MUST exist and MUST NOT be weakened or removed
to make a build pass:

- Bilateral integrity (no path collapses `BilateralSignal` to a scalar
  without a named reducer).
- Front-camera mirroring regression.
- Claim-level enforcement (Tier B/C cannot construct a Level 3 finding).
- Exercise/rep semantics (e.g. the bicep-curl alternating-arm rep
  definition is pinned, not left to drift).
- Golden-file cross-platform parity, run on both JVM and JS targets.
- Numeric Python/Kotlin parity on held-out data, wherever ML is used.
- Real-time per-frame budget (no allocation or history iteration creeps
  into the real-time path).

Tests exist to catch silent wrongness, not just crashes.

## 19. The existing prototype

- A previous Kinesis prototype exists outside this repository. It is
  **reference material only.**
- **MUST NOT**: copy it into this repository automatically.
- **MUST NOT**: modify the old prototype from this repository.
- **MUST NOT**: discard its validated concepts simply because this
  repository is new — inspect it deliberately, identify reusable
  behaviour, and port/rebuild according to the final architecture.

## 20. Development discipline

For every session working on this codebase: inspect before changing; read
the relevant architecture docs; make the smallest coherent change; run
tests; report what changed; report failed assumptions; never silently
redesign the architecture; do not start later phases prematurely; prefer
measurable behaviour over visual polish; preserve evaluation requirements
when cutting scope.

## 21. Scope priority under time pressure

When time is tight, preserve in this order — never sacrifice evaluation
to preserve optional UI polish:

1. Core correctness
2. Calibration/personalization
3. Rep/hold analysis
4. Evaluation
5. Evidence/reproducibility
6. Android product polish
7. Windows PWA
8. Stretch visualisations/features

## 22. Never do these

- Silently average bilateral signals.
- Put analysis logic in UI/platform code.
- Run ML per frame.
- Reprocess full history per frame.
- Invent or state accuracy numbers that have not been measured on this
  system, anywhere — code, comments, docs, or report drafts.
- Claim technique correctness without evidence.
- Claim that an external benchmark result (e.g. MM-Fit) validates a
  different camera condition or exercise variant than the one it was
  actually measured on.
- Treat exercise variants as separate algorithms.
- Add dependencies without the check in §17.
- Copy unlicensed GitHub code or model weights.
- Rewrite the shared engine in TypeScript without an explicit,
  documented architecture decision.
- Implement all exercise profiles before the three archetypes are
  validated — **superseded by `docs/DECISIONS.md` P3-4**; read that entry
  before citing this line as current.
- Silently change an already-decided architecture choice.

## 23. Final rule

When uncertain, the order of operations is always:

**MEASURE → TEST → DOCUMENT → THEN CLAIM**

never:

ASSUME → IMPLEMENT → CLAIM
