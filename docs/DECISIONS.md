# Kinesis — Decision Record

Started 2026-09-19 at P0. Every architectural decision that resolves a conflict
between documents, or closes an open question, is recorded here with its date
and reasoning.

## Document precedence

When two documents disagree, the later one wins. The order, highest first:

1. **This file** — P0 and later decisions, dated.
2. **ZERO_DATASET_BUILD_PLAN.md** — supersedes the Final Architecture Decision
   on data collection, the ML layer scope, gates, timeline and verdict.
3. **FINAL_ARCHITECTURE.md** — supersedes the Master Development Plan.
4. **CLAUDE.md** — an operational distillation, not an authority. If it
   disagrees with the above, CLAUDE.md is wrong and must be corrected.

The two `.docx` files at the repository root are the originals as exported.
They are kept for provenance and are not the working copies. Read the markdown.

---

## P0-1 — Repository state findings

*2026-09-19*

Three things in the P0 brief did not match the disk:

- `docs/FINAL_ARCHITECTURE.md` and `docs/ZERO_DATASET_BUILD_PLAN.md` did not
  exist. The documents were `.docx` files at the repository root, which an AI
  coding agent cannot usefully read. Converted to markdown in `docs/` with
  tables and links preserved. Originals untouched.
- The directory was **not a git repository**. No `.git` was present.
- Apart from `CLAUDE.md` and the two `.docx` files, the directory was empty.

---

## P0-2 — The Kotlin/JS target survives; the React PWA does not

*2026-09-19*

**Conflict.** The Zero-Dataset plan states G1 is gone and the PWA is cut. But
the same document lists P1 as "unchanged" — and the Final Architecture
Decision's P1 change *was* adding the JS facade module — and it keeps
"cross-platform parity: same inputs through JVM and JS targets" as one of its
six surviving evaluation studies. A JVM-vs-JS parity study cannot exist without
a JS target. `CLAUDE.md` §1 meanwhile made the JS target a hard MUST.

**Decision.** Keep `jvm()` and `js()` targets and design the `@JsExport` facade
in P1 as the Final Architecture Decision specifies. Drop the React PWA
application.

**Reasoning.** These are separable and were being treated as one thing. The JS
*target* costs roughly a day and preserves a research claim the Zero-Dataset
plan still counts. The React *application* is the 2.5 weeks that plan correctly
judged to have no academic value. Retrofitting a facade after the core's public
surface exists is expensive; adding a React app later is not.

**Consequence.** G1 is not gone. It is reduced: compile the core to JS and pass
golden files on both targets. No React integration, no `.d.ts` consumed by an
app. The replay-only web client remains the documented fallback if even that
fails.

---

## P0-3 — Glute Bridge stays Tier A

*2026-09-19*

**Conflict.** FINAL_ARCHITECTURE §6 lists the flagship five as Squat, Push-up,
Bicep Curl, Lateral Raise, Glute Bridge. ZERO_DATASET's exercise table demotes
Glute Bridge to Tier B.

**Decision.** Tier A, per FINAL_ARCHITECTURE.

**Reasoning.** The demotion appears to be driven by MM-Fit not covering glute
bridge. But under the Zero-Dataset plan Layer 2 is literature-sourced and
unvalidated for *every* exercise, so benchmark coverage is no longer what
qualifies an exercise for Tier A. FINAL_ARCHITECTURE §12-G already names the
real risk — MediaPipe is weaker on lying poses — and gives the contingency.

**Contingency, unchanged.** If P4 device testing shows Glute Bridge segments
poorly, promote Sit-up to Tier A in its place. Sit-up has MM-Fit coverage and
high landmark reliability.

**This is a type-level decision.** Tier sets `ExerciseProfile.maxClaimLevel`,
which determines whether a profile can construct a Level 3 finding at all.

---

## P0-4 — Lateral Raise and Shoulder Press are seated

*2026-09-19*

**Open question, now closed.** Neither document specified posture. MM-Fit
performs both seated.

**Decision.** Both Kinesis profiles are seated, matching MM-Fit.

**Reasoning.** A rule validated on seated data does not transfer to standing —
seated removes the lower body from the picture and changes the trunk reference.
Matching the benchmark is the only option that lets these two exercises carry a
measured rep-counting claim rather than dropping to deviation-only. It is also
the cheapest of the three options and is what the surviving evaluation plan
assumes.

**Consequence.** The profile definitions must state posture explicitly, and the
camera-view contract for both must expect a seated subject.

---

## P0-5 — Exercise counts corrected

*2026-09-19*

**Conflict.** FINAL_ARCHITECTURE §5–6 specifies 17 library entries / 15
profiles / 2 variants, with Side Plank and Hip Abduction as the documented cut
path down to 15 entries / 13 profiles under schedule pressure. `CLAUDE.md` §9
recorded 15/13 as the *target*.

**Decision.** The target is **17 entries / 15 profiles / 2 variants**. 15/13 is
the cut-down position, not the goal.

**Reasoning.** This was an error introduced when `CLAUDE.md` was written — it
encoded the post-cut number as the objective. `CLAUDE.md` §9 has been corrected.

---

## P0-6 — Layer 2 is unvalidated, and P8 module 8.6 is deleted

*2026-09-19*

**Conflict.** FINAL_ARCHITECTURE §2 defines Layer 2 as "Tier A only, validated
against labels" and gives P8 a module **8.6 Rule validation against labels
(EVAL, needs labels)**. ZERO_DATASET makes Layer 2 thresholds literature-sourced
and explicitly unvalidated.

**Decision.** Layer 2 rules are implemented from cited published thresholds.
Module 8.6 is **deleted**, not reduced — there are no labels to validate
against.

**Consequence.** Every Layer 2 rule carries a `sourceRef` citation in code. The
report's limitations section must state: these rules implement thresholds from
cited sources, and their agreement with expert human judgement was not measured
in this work. No accuracy figure is ever attached to a Layer 2 rule.

---

## P0-7 — The tier system's justification has changed

*2026-09-19*

**Finding, not a decision. Neither document states this.**

Under FINAL_ARCHITECTURE, Tier A meant "technique analysis validated against
labelled data here." Under ZERO_DATASET there are no labels anywhere, so Layer 2
is unvalidated on every exercise.

What now separates Tier A from Tier B is therefore **only** that cited technique
rules were written for it. The tier system still works as an honest scope
boundary — the system makes no technique claim on ten of the fifteen profiles,
enforced by the type system — but its *justification* shifted from "validated"
to "rules exist and are traceable to sources."

This matters in a viva. The answer to "why is this one Tier A?" is now "because
we wrote cited technique rules for it and not for the others," not "because we
measured it." Do not claim the stronger version.

---

## P0-8 — Superseded content still present in FINAL_ARCHITECTURE

*2026-09-19*

FINAL_ARCHITECTURE still contains instructions that ZERO_DATASET voids. They are
left in place for provenance, but a session reading only that document would
follow them wrongly. Void:

| Location | Superseded content |
|---|---|
| §11 MVP, §12-F | 10 subjects, ~250 labelled reps, "start asking people to be subjects in week 5" |
| §2, §7 | Layer 3 — five per-exercise supervised quality classifiers |
| §7, §10 | The rules-vs-ML comparison experiment |
| §13, §12-E, §12-G | Gate G3 label sufficiency |
| §12-I item 1 | Inter-rater agreement / Cohen's kappa on 50 reps — requires labelling |
| §1, §13 | Verdict YELLOW — ZERO_DATASET returns GREEN with one gate |
| §11 | Timeline 11/17/20+ weeks — ZERO_DATASET gives 9/14 |

---

## P0-9 — Module structure adopted, and a P0 incident

*2026-09-19*

**What happened.** During P0 the repository was scaffolded in Android Studio
while the session was mid-task. The session had listed the directory at the
start, found it empty, and then committed roughly forty minutes later without
re-checking or using modification-time guards. Several root configuration files
were overwritten: `settings.gradle.kts`, `gradle/libs.versions.toml`, the root
`build.gradle.kts` and `gradle.properties`. The build stopped resolving.

Module contents — `app`, `core:domain`, `core:analysis`, `desktop`, the
manifest and the Gradle wrapper — were untouched. The root configuration was
subsequently rebuilt.

**The rule this produces.** Every write to a file that was not created in the
same operation carries `expectedMtimeMs`. A directory listing older than a few
minutes is not evidence of current state. This applies to any session, human or
agent, working on a repository someone else may have open.

**Structure adopted.** The scaffolded layout, not the single-`core` layout P0
originally proposed:

| Module | Purpose |
|---|---|
| `:app` | Android application |
| `:core:domain` | Domain types. KMP: `androidTarget`, `jvm`, `js` |
| `:core:analysis` | Analysis engine, depends on `:core:domain`. Same three targets |
| `:desktop` | JVM module depending on both core modules |

`core/` itself is included as a project but is a container: its build file is
deliberately empty because Gradle evaluates intermediate projects. `core/src/`
is a P0 leftover and can be deleted; nothing reads it.

Neither architecture document specifies the `domain`/`analysis` split or the
`desktop` module. Both are adopted here rather than reverted — the split is
reasonable, and `desktop` is a usable home for the replay-only client that
FINAL_ARCHITECTURE §3 names as the web fallback.

**The `js()` target was missing** from both core modules and has been added,
per P0-2. `:core:analysis` also enables TypeScript definition generation, since
the `@JsExport` facade specified in `CONTRACTS.md` §5 lives in its `jsMain`.

**Versions.** The rebuilt catalog pins AGP 8.7.3 and Kotlin 2.1.21. The Gradle
cache on this machine also holds AGP 9.4.0, Kotlin 2.2.10 and Compose BOM
2026.02.01, so a newer coherent set is available if the pinned one conflicts
with the Gradle 9.3.0 wrapper — AGP 8.7 predates Gradle 9. If sync fails on
wrapper compatibility, that is the first thing to check.

---

## P1-1 — Build environment: Android Lint fails on JDK 25, and the Gradle wrapper is missing

*2026-09-19*

**Two findings, both environmental, neither caused by P1 code.**

**Android Lint cannot run.** Every `lint*` task fails with:

```
Execution failed for task ':core:domain:lintAnalyzeDebug'.
> A failure occurred while executing AndroidLintWorkAction
   > 25.0.3
```

AGP 8.7.3's bundled lint cannot read Java 25 class files. The Gradle daemon runs
on Android Studio's JBR 25, pinned by `gradle/gradle-daemon-jvm.properties`
(`toolchainVendor=JETBRAINS`, `toolchainVersion=25`). `lint { abortOnError =
false }` does not help — this is a worker exception, not a lint finding.

**Decision.** Lint is disabled in `:core:domain` and `:core:analysis` via a
`tasks.matching { it.name.startsWith("lint") }` block in each build file, with
the reason stated in a comment beside it. Both are Kotlin Multiplatform library
modules with no Android sources, resources or manifest logic, so lint has
nothing there to find. The task names are matched by prefix rather than by
importing AGP's internal task classes, so the block does not break on an AGP
bump.

**`:app` lint is still failing and was left enabled** — `:app` has real Android
code worth linting. `gradle build` is therefore red on `:app:lintAnalyzeDebug`,
`:app:lintAnalyzeDebugAndroidTest` and `:app:lintAnalyzeDebugUnitTest`. It was
not reached before P1 because the core-module lint failures stopped the build
first.

**The real fix is an AGP bump.** P0-9 notes AGP 9.4.0 is already in this
machine's Gradle cache. Until then, the verification command for this repository
is `gradle assemble allTests :app:testDebugUnitTest`, which compiles every
target and runs every test on JVM, JS and both Android variants, and excludes
only lint.

**The wrapper scripts were absent, then fixed later in P1.** `gradlew`,
`gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` were missing at the point
this entry was first written — only `gradle-wrapper.properties` survived the
P0-9 overwrite incident, and a fresh clone could not build. The wrapper was
regenerated (`gradle wrapper --gradle-version 9.3.0`) and committed later in the
same P1 session as `19d98c9 Add missing Gradle wrapper files`. **This is
resolved, not outstanding** — a later phase reading this entry in isolation
should check `git log -- gradlew` rather than take the paragraph above at face
value.

**Confirmed compatible.** AGP 8.7.3 and Kotlin 2.1.21 configure and compile
cleanly under the Gradle 9.3.0 wrapper. The wrapper-compatibility risk P0-9
flagged did not materialise; only lint is affected.

---

## P1-2 — Where the P1 types live

*2026-09-19*

Neither architecture document allocates the contract types across the
`domain`/`analysis` split that P0-9 adopted, so P1 had to.

| What | Module | Why |
|---|---|---|
| `BilateralSignal`, `ClaimLevel`, `Finding`, `ExerciseProfile`, tier classes, landmark model | `:core:domain` | Types, shared by everything |
| `LandmarkNormalizer`, `ValidityGate` | `:core:analysis` | CONTRACTS §7 names both as steps of the real-time path, and the real-time path is analysis |
| `KinesisEngine` | `:core:analysis/src/jsMain` | CONTRACTS §5; the only `@JsExport` surface, and the only file in `jsMain` |
| `PoseAdapter`, `CameraFacing`, `RawLandmarks` | `:app` | CONTRACTS §4 annotates the adapter *"in platform code only"*, and CLAUDE.md §2 lists the pose adapter and mirroring handling as platform-specific |

**Consequence of the adapter's placement.** `CameraFacing` does not exist in
either core module, which is what CONTRACTS §4 demands, but it also means the
mirroring guard test is a JVM unit test in `:app` rather than a `commonTest`.
The `commonMain` half of that contract — that nothing in the core branches on
camera facing — is enforced instead by a source scan, described in P1-3.

When `:desktop` needs to replay recorded sessions it will need its own adapter
or a shared pure-Kotlin one. Revisit then, not now.

**`:core:analysis` now declares `api(project(":core:domain"))`** rather than
`implementation`. Its public signatures expose domain types, so consumers must
be able to resolve them.

---

## P1-3 — How claim-level enforcement actually works

*2026-09-19*

CLAUDE.md §8 and CONTRACTS.md §3 require that a Tier B or Tier C profile be
*structurally unable* to construct a Level 3 finding — a compile-time or
construction-time impossibility, not a filter applied afterwards. Kotlin has no
dependent types, so this is done with a capability token.

- `TechniqueAuthority` has a private constructor. Its only issue point is an
  `internal` companion function in `:core:domain`, invisible to `:core:analysis`
  and `:app`.
- `TierAProfile` exposes a `techniqueAuthority` property. `TierBProfile` and
  `TierCProfile` do not, and nothing else implements the property.
- `TechniqueFault`'s constructor is private; the only factory, `from`, demands a
  `TechniqueAuthority`.
- `tier` and `maxClaimLevel` are `final override` on all three tier classes, so a
  subclass cannot restate them. `maxClaimLevel` is derived from `Tier` itself, so
  the two cannot disagree.
- `TechniqueFault` is deliberately **not** a `data class`: a generated `copy()`
  would let a caller clone a valid fault into one with a blank `sourceRef`,
  stepping around the check in `from`.

Abstract classes rather than a sealed hierarchy, because sealed types restrict
implementations to the declaring module and P3 defines profiles in
`:core:analysis`.

**Verified, not assumed.** A temporary probe in `:core:analysis` attempted all
six escape routes. Every one is a compile error:

| Route | Compiler says |
|---|---|
| Override `tier` on a Tier B profile | `'tier' in 'TierBProfile' is final and cannot be overridden` |
| Override `maxClaimLevel` | `'maxClaimLevel' in 'TierBProfile' is final and cannot be overridden` |
| `tierBProfile.techniqueAuthority` | `Unresolved reference 'techniqueAuthority'` |
| `TechniqueAuthority("row")` | constructor `is private` |
| `TechniqueAuthority.issueTo(...)` | companion `is internal` |
| `TechniqueFault(...)` directly | constructor `is private` |

The probe was deleted after the check.

### The source-scan guards

Two architectural rules are about code that must not exist anywhere, and no type
signature can express an absence. `SharedCoreSourceGuardTest`, in
`:core:analysis/src/jvmTest`, reads the source tree:

1. No `*Main` source averages `.left` and `.right` outside `BilateralSignal.kt`.
2. No source in either core module mentions `CameraFacing`, `isMirrored`,
   `frontCamera` or `LENS_FACING`.

It strips comments before matching, so a KDoc explaining why the core must not
know about the front camera is not itself a violation — otherwise the rule would
push its own explanation out of the source. It also asserts it scanned a
non-empty file set, so it cannot pass by finding nothing.

**Verified by mutation.** A probe file adding both a `(rom.left + rom.right) /
2.0` collapse and a `CameraFacing` enum to `commonMain` was detected by both
scans, reported with file, line and offending text. Probe deleted.

The mirroring guard was verified the same way: removing the x reflection from
`PoseAdapter.unmirror` while keeping the slot swap failed two of the five
assertions. Notably it did *not* fail the round-trip assertion — a half-correct
mirror is still its own inverse, which is exactly why that test checks the two
halves separately.

---

## P1-4 — Provisional constants, and the normalisation definition

*2026-09-19*

P1 introduces three numbers. **None is a measured result, and none supports any
claim.** They are placeholders so the code can be built and tested before a
device exists to calibrate against, per CLAUDE.md §23 — measure, test, document,
then claim.

| Constant | Value | Status |
|---|---|---|
| `VisibilityPolicy.PROVISIONAL.reliable` | 0.70 | Provisional. P4 device testing sets it. |
| `VisibilityPolicy.PROVISIONAL.marginal` | 0.40 | Provisional. P4 device testing sets it. |
| `LandmarkNormalizer.MIN_TORSO_SCALE` | 1e-6 | Degenerate-input guard, not a quality threshold |

Each is documented as provisional at its declaration. When P4 measures them, the
outcome is recorded here and the constants cite it.

**Normalisation is defined as:** origin at the mid-hip, unit scale equal to the
mid-shoulder-to-mid-hip distance **in the image plane only**. MediaPipe's `z` is
a relative depth estimate rather than a measured one, and folding it into the
scale would make the unit fluctuate with depth noise every frame. Revisit in P2
if the 2D length proves unstable for exercises filmed at an angle.

A frame whose torso length falls below `MIN_TORSO_SCALE` returns `null` rather
than falling back to a default scale. Losing the torso is an ordinary event
during a set — the user turns, steps out of shot, or is briefly occluded — and
substituting a scale would make every downstream measurement quietly wrong
instead of honestly absent.

The mid-hip and mid-shoulder midpoints are geometric centres between two named
landmarks, not reductions of a `BilateralSignal`. No left/right quantity is
averaged away, and both hips stay separately readable in the output.

---

## P1-5 — The front-camera mirroring assumption, stated explicitly

*2026-09-19*

`PoseAdapter` assumes the front camera feed arrives **horizontally mirrored**,
which is what a front-facing preview conventionally does. That has two
consequences and the adapter undoes both: the pose model labels landmarks from
the image it was given, so the subject's left wrist arrives in the `RIGHT_WRIST`
slot; and the x coordinate is reflected about the centre of the frame.

**This assumption is not yet verified against a device.** CameraX can be
configured either way, and the preview and the analysis use case do not have to
agree. If P4 shows the analysis stream is unmirrored, `PoseAdapter.unmirror` is
the single place that changes — nothing else in the codebase knows the
difference exists.

---


---

## P2-1 — What P2 covers, and a discrepancy with the session brief

*2026-09-20*

**Neither revision document enumerates P2's modules.** ZERO_DATASET's phase table
says `P2 Kinematics and Temporal | Unchanged`, deferring to FINAL_ARCHITECTURE,
whose §9 table also says `P2 | Unchanged`. The module content therefore has to be
read off the phase names plus FINAL_ARCHITECTURE §13:

> **Week 3:** Kinematics with bilateral signals as a compile-time shape, filter
> chain, resampling, ablation harness
>
> Then **P3 rep engine**, then P4 on real people by week 6 […] while you are
> still writing the rep engine.

P3 is named **"Profiles and Rep Engine"**. ZERO_DATASET's week-2 line — *"P2 and
P3: kinematics, the repetition FSM, and three archetype profiles"* — groups the
two phases in one week but splits the work along the same line.

**Discrepancy.** The session brief summarised P2 as including the generic
repetition FSM and the isometric hold analyser. The documents place both in P3.

**Decision.** Follow the documents. P2 is:

| Module | Path |
|---|---|
| Geometry primitives | `analysis/geometry` |
| Current-frame kinematics, bilateral by construction | `analysis/kinematics` |
| Fixed-size ring buffer | `analysis/temporal` |
| Filter chain — outlier gate, EMA, gap interpolation | `analysis/temporal` |
| Fixed-length resampling, N = 64 | `analysis/temporal` |
| Ablation harness | `analysis` commonTest |

The repetition FSM, the isometric hold analyser, `RepRecord`/`HoldRecord` and the
three archetype profiles are **P3**, per CLAUDE.md §20 — do not start later
phases prematurely. The ring buffer is the handoff point: P3 reads from it.

**Consequence.** Two of the three guard tests the brief named — rep FSM
phase/count determinism, and isometric hold never forced through the repetition
FSM — guard P3 components and are written in P3, before that code, in the same
tests-first order. The third, the real-time per-frame budget, is written here,
because the kinematics and ring buffer are its subject.

---

## P2-2 — The old prototype, inspected

*2026-09-20*

Access to `E:\kinesis_old` was declined during P0, so the G1 spike used a
structurally-representative stand-in rather than the real code
(`G1_SPIKE_RESULT.md`, and the OPEN item raised at P0). Access was granted at the
start of P2 and the prototype was read, read-only, per CLAUDE.md §19. **That open
item is now closed.**

The prototype is a single-target Android/JVM project under
`core/analysis/src/main/java/com/example/kinesis/core/analysis`. It contains real
kinematics, a real temporal filter chain and a real repetition FSM. It is a
specification, not legacy code, and it was treated as one.

### Ported as ideas, rewritten

| Prototype | What is worth keeping |
|---|---|
| `GeometryUtils.angleDegrees` | Interior angle at the vertex via `acos(dot / (|BA|·|BC|))`, **clamped to [-1, 1]** to absorb floating-point drift, returning `null` on degenerate geometry rather than 0°, 180° or NaN. The rule it states — never return a misleading value as a failure signal — is CLAUDE.md §23 in miniature. |
| `angleDegrees2d` beside the 3D form | The prototype already distrusted MediaPipe's z: *"useful when Z reliability is uncertain"*. This agrees with the P1-4 decision to scale normalisation on the image plane only. Both forms are kept, with the plane chosen explicitly at the call site rather than defaulted. |
| `TemporalPipeline` ordering | Outlier removal → gap interpolation → EMA → resample, **with the reasons recorded**: outliers first so they cannot pollute the smoother or the interpolator, interpolation before EMA so a short gap does not jerk the filter state. The ordering rationale is the valuable part and is carried over verbatim in the KDoc. |
| `EmaSmoother` gap handling | Resets filter state across a gap instead of dragging a stale value across it. Correct, and kept. |
| `VelocityCalculator` timestamp discipline | Rejects non-positive and duplicate Δt, does not assume a fixed frame rate, and documents units as normalised-units per second — explicitly *"NOT physical meters/second"*. |
| `TemporalConfig` | Every magic number in one immutable object with `require` validation. The pattern is kept; the values are re-marked provisional, as P1-4 requires. |
| `RepEngine` FSM shape | Hysteresis band around both thresholds, direction-agnostic via an `isTopGreater` flag, per-rep min/max tracking, per-phase timings, and a genuine validity enum — VALID, ABORTED, INCOMPLETE, INSUFFICIENT_DATA. Good design worth porting **in P3**, and only after it is restructured from batch replay into an incremental `step()`. |
| `LandmarkValidator`'s framing | *"A visually rendered landmark and an analytically usable landmark are NOT the same. Rendering can be permissive; analysis must be conservative."* Already realised in P1 as `VisibilityPolicy`, and the distinction is worth restating in the report. |

### Not ported, and why

| Prototype | Why not |
|---|---|
| **`LiveRepDetector.extractSignalValue`** | Computes `(left + right) / 2f` — precisely the silent bilateral averaging CLAUDE.md §3 and §22 forbid. The `else left ?: right` fallback is worse: when one side drops below confidence the signal silently becomes single-sided, with nothing recording that it did, so a bilateral measurement and a one-armed one are indistinguishable downstream. This is the clearest justification the project has for the `BilateralSignal` type, and it is worth citing in the report as a concrete defect the new architecture prevents by construction. |
| `LiveRepDetector`'s per-frame replay | Its own comment reads *"reprocessing the growing signal array is extremely fast (O(N) across <10,000 items)"*. It breaks CLAUDE.md §6 three ways: `rawSamples` grows without bound over a session, `rawSamples.toList()` copies the entire history every frame, and both the full temporal pipeline and the full FSM are re-run from the beginning on every frame. The new design is incremental, and the per-frame budget guard exists to keep it that way. |
| `Float` throughout — `Vector2`, `Vector3`, `EPSILON = 1e-7f`, all angles | CLAUDE.md §5 and CONTRACTS §6 require `Double` throughout the shared core. |
| `Long` timestamps | §5 bars `Long` in analysis paths where JS compatibility matters. Timestamps are `Double` milliseconds. |
| `Math.toDegrees`, `Float.MAX_VALUE` | JVM-only; would break the `js()` target. Replaced with multiplatform equivalents. |
| Flat `leftElbow()` / `rightElbow()` returning independent scalars | The absence of a bilateral type is what *permitted* the averaging above. Kinematics return `BilateralSignal`. |
| String signal identity — `"knee_angle"`, `primarySignalId`, `when (String)` dispatch | Stringly-typed and unchecked; a typo is a silent null. Replaced by an enum. |
| `LandmarkValidator.getValidLandmark` | `frame.landmarks.firstOrNull { it.type == type }` is a 33-element scan plus a lambda allocation per lookup, three per joint angle, every frame. P1's `Landmarks` is O(1) index arithmetic into a flat `DoubleArray`. |
| `FilterComparisonTest` | Prints a comparison table to the console and asserts nothing. The idea — compare filter configurations against a synthetic signal with known ground truth — is right, and becomes the ablation harness, which asserts a recorded ranking so a regression fails the build. |
| `Vector3.div` returning `Vector3?` | An arithmetic operator that can return null is a trap at the call site. |

**Nothing was copied.** No file, and no fragment, was taken from
`E:\kinesis_old` into this repository; the prototype was read and re-implemented
against the P1 contracts. The prototype was not modified.

---

## P2-3 — `app/` is the instrumentation harness first

*2026-09-20*

**Closes the open question raised in REPOSITORY_STRUCTURE.md** and left open at
the end of P1.

**Decision.** `app/` is built as a bare instrumentation harness before it is a
product shell. Its first form is CameraX capture, MediaPipe Pose, a skeleton
drawn on a dedicated `Canvas`/`SurfaceView`, and a frame-rate readout. No
exercise selection, no navigation, no product screens.

**Reasoning.** P4 is the device reality check and the Zero-Dataset plan puts it in
week 3. It needs sustained frame rate, a thermal run, and front-camera mirroring
verified on a real device — none of which requires a product UI, and all of which
is delayed by building one. FINAL_ARCHITECTURE's phase list does not say which
phase builds the harness, and P10 "Visualization and Android" is far too late to
be the answer.

**Constraint carried from CLAUDE.md §6.** Skeleton rendering goes to a dedicated
`Canvas`/`SurfaceView`. Pose frames must never drive Compose recomposition; the
low-frequency UI state and the per-frame draw path stay separate.

**Not built in P2.** The harness needs CameraX and MediaPipe Pose, which are new
dependencies and require the CLAUDE.md §17 check — purpose, licence, platform
compatibility, bundle and runtime cost, offline compatibility, maintenance
status — recorded before either is added. P2 was instructed to add no
dependencies, so the decision is recorded here and the harness is built in the
phase that performs that check. Nothing in P2 depends on it.

---

## P2-4 — First ablation result: smoothing lag, not noise, is the binding constraint

*2026-09-20*

The filter ablation harness (`FilterAblationTest`) runs the filter chain against
a synthetic signal whose true value is known at every sample, so the question
"did filtering help?" can actually be asked. It separates what smoothing buys
from what it costs.

**What it found.**

| Condition | Result |
|---|---|
| Stationary joint, noisy estimate | Smoothing is pure gain, and monotonic — heavier smoothing leaves less residual noise. The provisional alpha of 0.3 more than halves it. |
| Moving joint, clean estimate | Smoothing is pure cost, and monotonic the other way. Steady-state lag matches the algebra exactly: `slope × (1 − α) / α` per sample. |
| Moving joint, spiked estimate | The outlier gate removes more than 90 % of the error the spikes introduce, over and above the lag floor. |

**The part that matters for P4.** At α = 0.3 the lag is 2.33 samples, about
**78 ms at 30 fps**, and on a two-second repetition cycle that lag error is the
*larger* of the two effects — bigger than the noise the smoothing removes. Two
consequences:

- α cannot be chosen by looking at how tidy the traces are. Lower α always looks
  smoother and always delays the frame on which a repetition boundary can be
  recognised, which shows up later as rep-count latency rather than as
  smoothness.
- The gate and the smoother are not interchangeable. The gate is nearly free and
  removes a large, specific error; the smoother is a trade. If only one survives
  a performance cut, it is the gate.

**What this does not establish.** These are numbers about filter behaviour on a
synthetic signal, not about Kinesis's accuracy on human movement — a different
claim, and CLAUDE.md §22 forbids blurring them. `smoothingAlpha` stays
provisional. P4 measures it on a device, and the harness is the instrument that
will compare the candidates when it does.

---


---

## P3-1 — Bicep curl repetition semantics, closed against the label file

*2026-09-20*

**Closes the OPEN item raised 2026-09-19**, which the decision record named a
hard blocker for P3. The question was whether MM-Fit labels one bicep-curl
repetition as one arm's curl or as both arms.

**Answer: one arm.** MM-Fit performs bicep curls with alternating arms, and each
single-arm curl is one labelled repetition. A labelled set of 10 is five curls
per arm.

### Evidence

MM-Fit was extracted to `data/mm-fit/` and read directly — label CSVs and the
bundled 2D pose. Its label format is
`start_frame, end_frame, repetition_count, exercise_name`.

Two independent measurements, both over all 21 workouts.

**1. Cycles per arm per labelled repetition.** For each labelled set, the
dominant frequency of each wrist's vertical motion was taken from the amplitude
spectrum. Bin *k* of an *N*-point FFT is exactly *k* cycles across the window, so
the peak bin is the cycle count — no threshold, no smoothing parameter, nothing
to tune.

| Exercise | Sets | Cycles per arm, per labelled rep | SD | L/R phase |
|---|---|---|---|---|
| squats | 64 | 1.00 | 0.21 | +1.00 |
| pushups | 65 | 1.00 | 0.32 | +0.08 |
| situps | 65 | 1.00 | 0.24 | +0.95 |
| lateral_shoulder_raises | 56 | 1.00 | 0.03 | +1.00 |
| dumbbell_shoulder_press | 60 | 1.00 | 0.05 | +1.00 |
| tricep_extensions | 64 | 1.00 | 0.04 | +0.99 |
| dumbbell_rows | 64 | 1.00 | 0.02 | +0.99 |
| jumping_jacks | 56 | 1.00 | 0.05 | +0.99 |
| lunges | 62 | 1.00 | 0.16 | +0.93 |
| **bicep_curls** | **59** | **0.50** | **0.01** | **−0.40** |

Nine exercises return exactly one cycle per arm per labelled repetition. Bicep
curls return half — and with the *tightest* spread of all ten, 0.01 across 59
sets, so this is not noise. Every individual set reads 5 cycles on the left wrist
and 5 on the right against a label of 10.

**2. Left/right phase.** Correlation between the two wrists' vertical motion is
positive and near +1 for every other exercise, meaning the arms move together.
For bicep curls it is negative, −0.40, meaning they alternate. Partial rather
than perfect antiphase is what alternating curls actually look like: one arm
begins lowering while the other is still rising.

The nine other exercises act as controls for the method. A measurement technique
that reported 1.00 for nine exercises known to be one cycle per repetition, and
0.50 only for the one exercise the documents flag as alternating, is measuring
what it claims to.

### Decision

**Kinesis counts one bicep-curl repetition per arm**, matching MM-Fit.
`RepetitionSemantics.PER_LIMB`.

**Reasoning.** This is the same argument as P0-4 on seated posture. Matching the
benchmark's definition is what makes the rep-count comparison 1:1 and meaningful;
any other choice requires a conversion factor applied at exactly one place, and a
conversion factor that someone forgets is precisely the factor-of-two error the
open item warned about — one that presents as a threshold-tuning problem rather
than a definitional one.

### Consequences

- The rep FSM runs **per side**. An alternating exercise needs two independent
  state machines, one per arm, and the repetition count is their sum. This falls
  out of the bilateral architecture rather than fighting it: nothing is averaged,
  and each arm's range of motion stays separately attributable.
- Every repetition profile declares its `RepetitionSemantics` explicitly. There
  is no default, because a silent default is how this gets wrong again.
- `BicepCurlSemanticsTest` pins both the declaration and the counting behaviour,
  per CLAUDE.md §18.

**Scope of this entry.** It records what MM-Fit's labels *define*. It is not a
claim about how accurately Kinesis detects repetitions — nothing here has been
measured against MM-Fit yet, and no such figure exists. That comparison is P7 and
P12.

**Not yet verified.** Only MM-Fit's bundled 2D pose was read, to answer a
question about MM-Fit's own labels. Per CLAUDE.md §14 those OpenPose skeletons
are never fed to the Kinesis engine: the evaluation pipeline is MM-Fit RGB video
→ MediaPipe Pose → Kinesis. That pipeline is P7 and nothing here anticipates its
result.


---

## P3-2 — Rep engine design decisions no document specifies

*2026-09-20*

Four choices P3 had to make. None is stated in FINAL_ARCHITECTURE or
ZERO_DATASET, and each is recorded here rather than left in a comment, per
CLAUDE.md §20.

### One state machine per side, never a combined signal

An alternating exercise needs to count each arm separately (P3-1), which means
the FSM cannot be driven by a single fused signal. Rather than special-casing
that, **every** repetition exercise runs two independent `RepFsm`s with two
independent filter pipelines — so no bilateral quantity is ever reduced to a
scalar to drive counting, and an occlusion on one side cannot perturb the other's
filter state.

What differs between exercises is only how the two machines' cycles combine, which
the profile declares as `RepetitionSemantics`:

- `PER_LIMB` — each side's cycle is a repetition, carrying the side that performed
  it. Five curls per arm is ten repetitions.
- `SIMULTANEOUS` — a cycle from each side pairs into one repetition, counted once,
  with both sides' range of motion retained so asymmetry stays measurable.

This falls out of the bilateral architecture rather than fighting it. It is also
the direct structural answer to the prototype's `(left + right) / 2f`.

### A side whose partner never completes is reported, not discarded

Under `SIMULTANEOUS`, if one knee is occluded for a whole repetition its partner
cycle never arrives. The unpaired side is emitted at the end of the set as
`INSUFFICIENT_DATA` with the unmeasured side blank, rather than dropped. The
movement happened and half of it was measured; silently discarding it would
undercount, and silently completing it would invent the missing half.

### The repetition clock re-bases while the subject waits at the top

Standing between repetitions does not accumulate into either the one before or
the one after: while the signal is at the starting position, the cycle buffer and
the repetition clock restart every frame.

Two reasons, both found by test rather than by reasoning ahead.

1. Without it, a pause longer than `maxRepDurationMs` emits a phantom
   `INCOMPLETE` record — someone resting thirteen seconds between sets generated
   a repetition record.
2. Without it, fixed-length resampling stops working as intended. A fixed pause
   is a large fraction of a fast repetition and a small fraction of a slow one, so
   two repetitions of identical shape no longer line up point for point — which is
   the entire purpose of resampling to a fixed length.

### Hysteresis must not reach the range-of-motion measurement

The first correct-looking version of the re-basing above reset the peak value to
the current sample on every top frame. That was wrong, and the test caught it: the
descent passes *through* the hysteresis band while still counted as "at top", so
the recorded peak became the last in-band value rather than the true starting
position. A 100-degree squat measured as 87.

The band is a **detection** device — it exists so a signal resting on a threshold
does not flicker between phases and manufacture repetitions out of estimator
noise. Range of motion is a **Level 1 measurement**. The peak is therefore widened
while at the top, never reset, so the band cannot subtract itself from every
measurement the system reports.

Worth remembering as a general shape: a threshold used for detection should not be
allowed to leak into the quantities being measured.

## P3-3 — Scope expansion: engagement dashboard, streaks, Health Connect

*2026-09-20*

**Neither document specifies this. It is a scope addition, not a document
conflict**, decided directly by the project owner after the P3 review, with the
timeline risk explicitly accepted.

**Decision.** The Android app gains three things beyond the analysis engine
`FINAL_ARCHITECTURE.md` and `ZERO_DATASET_BUILD_PLAN.md` describe:

1. **A dashboard** — session history, trends over time (ROM, deviation, and the
   other Layer 1/2 outputs already produced per rep), built entirely from
   `RepRecord`/`HoldRecord` history the engine already generates. No new data
   collection.
2. **Streaks** — Duolingo-style consecutive-day tracking. Pure local state: a
   counter and a calendar view over session timestamps. No new architecture.
3. **Health Connect integration** — read-only context from the platform's
   on-device health data store (steps, active calories, heart rate — exact set
   to be pinned when this is built), displayed alongside Kinesis's own
   measurements rather than replacing them.

**Reasoning, and the tension worth being honest about.** (1) and (2) cost
almost nothing architecturally — they read data the engine already produces
and hold no new invariants CLAUDE.md needs to protect. (3) is a real
dependency addition and needs the full §17 check when it is built: license,
API level requirements, whether it fits the KMP module split (Health Connect
is Android-only, so this is `:app`-only like CameraX/MediaPipe), and
maintenance status. Health Connect itself is on-device, not a cloud service —
adding it does not violate the "no backend, no auth, no cloud" line in
`FINAL_ARCHITECTURE.md` §6's final scope table, provided it stays read-only
and nothing here introduces an account system or server of Kinesis's own.

**The academic framing this needs, if it is going to help rather than hurt.**
A generic fitness dashboard is not itself a research contribution, and
piling one on top of an already-tight P4–P12 timeline is a real risk to the
things that are — accepted here as a stated tradeoff, not an oversight. The
honest framing, if used in the report: Kinesis measures things a step counter
or generic tracker cannot — bilateral asymmetry, tempo, deviation from a
personal calibrated baseline — and the dashboard contextualises those
technique-specific measurements within the user's broader daily activity
rather than duplicating what Health Connect already reports. That is a
defensible sentence. "The app also has a dashboard and streaks" on its own is
not, and should not be how this is presented in the viva.

**Where this lives.** Primarily **P10 (Visualization and Android)**, which
already owns the app's UI.

**Not yet decided, deferred to detailed design.** The exact Health Connect
data types to request, streak mechanics (whether a missed day breaks it or a
freeze exists), and dashboard layout. Recorded here so the decision to build
these at all is not lost, not to pin the details early.

---

## P3-4 — All fifteen profiles, and this new scope, now precede P4 device validation

*2026-09-20*

**This reverses one of CLAUDE.md's named non-negotiables, not just a schedule
reordering.** §9 required building and validating the three archetypes on a
real device before scaffolding the remaining twelve profiles; §22's "never do"
list names implementing all profiles before the archetypes are validated
explicitly. Decided directly by the project owner, after this tradeoff was
stated plainly, twice, and confirmed both times.

**Decision.** The remaining twelve profiles, the P10 dashboard/streaks/Health
Connect work (P3-3), and the full app UI are built now, before P4's device
testing. `CLAUDE.md` §9 and §22 are corrected to point here, per this file's
own precedence rule — CLAUDE.md is corrected when it disagrees with a recorded
decision, not the other way round.

**What this trades away, stated once for the record.** §9's ordering existed
for a specific reason, recorded in P2-3: P4 needs zero product UI, and is
deliberately scheduled early so a fundamental failure — inadequate frame rate,
thermal throttling, a wrong mirroring assumption, MediaPipe struggling on a
lying pose (the Glute Bridge risk named in P0-3) — is caught before weeks are
spent on twelve more profiles and a full UI built on top of an unverified
foundation. Building all fifteen first means any such finding could require
rework across all twelve non-archetype profiles, not just the three already
device-tested. That risk is accepted here, explicitly, not overlooked.

**What does not change.** Tests-first discipline (§18) and per-exercise
consideration of camera view, landmark reliability, and bilateral handling
(§9's other bullet) still apply to every new profile — building ahead of
device validation is not license to skip guard tests. The three archetypes'
own P4 validation still happens; it simply happens after the other twelve
exist rather than before.

---

## P3-5 — Repetition semantics for push-up, lateral raise and glute bridge

*2026-09-20*

The second batch of profiles needs the same question answered that P3-1 answered
for the bicep curl: does one labelled repetition mean one limb's movement or
both limbs together? Getting it wrong is a factor-of-two error in every rep count
the exercise ever produces, and it presents as a threshold-tuning problem rather
than a definitional one.

Two of the three can be measured. One cannot, and is marked accordingly.

### Method, and one correction to P3-1's

Same threshold-free measurement as P3-1. For each labelled set, the dominant
frequency of each side's motion is read off the amplitude spectrum; bin *k* of an
*N*-point FFT is exactly *k* cycles across the window, so the peak bin is the
cycle count. No deadband, no smoothing window, nothing to tune. Left/right phase
is the correlation between the two sides' signals: near +1 means they move
together, negative means they alternate.

**What P3-1 did not do, and this does: choose the signal per exercise.** P3-1
used wrist height for all ten exercises. That is right for a curl and wrong for a
push-up — the hands are planted on the floor, so wrist height is the one thing
that does *not* move. It shows in P3-1's own table: push-ups have the worst
spread of all ten (SD 0.32, against 0.01–0.05 for exercises whose wrists
genuinely travel). The conclusion there was unaffected, because the question was
about bicep curls and the curl figures are unchanged, but the method is improved
here.

Each exercise is therefore measured on two signals: a raw vertical coordinate of
whichever joint actually travels, and **the joint angle the Kinesis profile will
drive off**, which is the one that has to hold for the decision to mean anything
in the engine.

The MM-Fit array layout was verified against the data rather than assumed:
`pose_2d` is `(2, frames, 19)`, row 0 is *x* and row 1 is *y* (image *y*-down —
nose ≈ 266, ankle ≈ 696), column 0 of the joint axis carries the frame number,
and joints 1–18 are OpenPose COCO-18 shifted by that column.

### Evidence

Cycles per side, per labelled repetition. **1.00** means the label counts a
two-sided movement once; **0.50** means it counts each side separately.

| Exercise | Signal | Sets | Cycles/side/rep | SD | L/R phase |
|---|---|---|---|---|---|
| **pushups** | torso height | 65 | **1.00** | 0.05 | +0.99 |
| **pushups** | elbow angle | 65 | **1.00** | 0.23 | +0.68 |
| **lateral_shoulder_raises** | hand height | 56 | **1.00** | 0.03 | +1.00 |
| **lateral_shoulder_raises** | shoulder abduction angle | 56 | **1.00** | 0.03 | +0.99 |
| squats *(control)* | hip height | 64 | 1.00 | 0.02 | +1.00 |
| squats *(control)* | knee angle | 64 | 1.00 | 0.02 | +0.96 |
| dumbbell_shoulder_press *(control)* | hand height | 60 | 1.00 | 0.05 | +1.00 |
| bicep_curls *(control)* | hand height | 59 | **0.50** | 0.01 | **−0.40** |
| bicep_curls *(control)* | elbow angle | 59 | **0.50** | 0.01 | **−0.40** |

The direct discriminator, counted per set on the joint-angle signal — how often
*both* sides' cycle count equals the label, versus equals half the label:

| Exercise | Sets | Both sides = label | Both sides = label ÷ 2 |
|---|---|---|---|
| pushups | 65 | 47 | **0** |
| lateral_shoulder_raises | 56 | 52 | **0** |
| squats *(control)* | 64 | 61 | **0** |
| dumbbell_shoulder_press *(control)* | 60 | 48 | **0** |
| bicep_curls *(control)* | 59 | **0** | **56** |

**The alternative reading, and why it is rejected.** The plausible alternative is
that MM-Fit performs these the way it performs curls — alternating single arms,
one arm per labelled repetition. That reading predicts 0.50 cycles per side per
repetition and a negative left/right phase, which is exactly what the bicep-curl
control shows. Push-ups and lateral raises show neither, on either signal, in any
set: not one of the 121 sets has both sides at half the label, and the phase is
positive and near +1 throughout. The two readings are cleanly separated by the
measurement rather than by interpretation of it.

The controls are what make that claim about the *method* rather than about the
result. Squats and shoulder press are unambiguously two-sided and return 1.00;
bicep curls are known from P3-1 to be per-limb and return 0.50 under the same
code in the same run.

**One observation worth flagging, not acted on here.** The push-up elbow angle is
a visibly noisier signal than push-up torso height — SD 0.23 against 0.05, phase
+0.68 against +0.99 — and the noise is one-sided: the left elbow's cycle count
disagrees with the label in 18 of 65 sets, the right elbow in 6. That asymmetry
is what an occluded far arm looks like. It is a property of MM-Fit's camera
placement and its OpenPose estimates, not a measurement of MediaPipe, and
nothing here predicts how the same signal behaves in Kinesis. It is recorded
because P4 should look at it: if the elbow angle proves an unreliable driver for
push-ups on a real device, torso height is the alternative this data already
shows to be cleaner.

### Glute bridge — an assumption, not a measurement

**MM-Fit has no glute bridge class.** Its ten are bicep curls, dumbbell rows,
dumbbell shoulder press, jumping jacks, lateral shoulder raises, lunges,
pushups, situps, squats and tricep extensions. There is no label file to check
against, so nothing above applies to it.

The glute bridge is therefore declared `SIMULTANEOUS` **from anatomy**: it is a
bilateral hip extension performed with both feet planted, both hips extend
through the same movement at the same time, and there is no common variant in
which the two sides alternate. A single-leg glute bridge exists, but it is a
different exercise with its own loading and would be its own profile, not this
one performed differently.

**This is an assumption that has never been validated against real motion data,
and it is a weaker basis than the entries above.** It is recorded as a distinct
claim for exactly that reason: the push-up and lateral-raise decisions are
measurements with 121 sets behind them, and this one is reasoning. If it is
wrong, it is wrong in the same factor-of-two way, and the test that pins it
(`TierAProfileTest`) is what will make the correction cheap.

This compounds a risk already on the record. P0-3 keeps the glute bridge in Tier
A while noting that MediaPipe is weaker on lying poses, with the contingency that
P4 may promote the sit-up in its place. The glute bridge is now the one Tier A
exercise with neither benchmark coverage nor device validation behind any of its
declarations.

### Decisions

| Profile | Semantics | Basis |
|---|---|---|
| Push-up | `SIMULTANEOUS` | Measured, 65 MM-Fit sets, two signals |
| Lateral Raise | `SIMULTANEOUS` | Measured, 56 MM-Fit sets, two signals |
| Glute Bridge | `SIMULTANEOUS` | **Assumed from anatomy. No data.** |

With these three, Tier A is complete as CONTRACTS.md §3 assigns it: Squat,
Push-up, Bicep Curl, Lateral Raise (seated), Glute Bridge.

### Consequences

- The camera view and posture of each is declared beside it and pinned by test.
  Lateral Raise is **seated**, per P0-4 — a rule derived from seated data does
  not transfer to standing, and MM-Fit performs it seated.
- `ArchetypeProfiles.all` is no longer the set of all profiles. A new
  `ExerciseLibrary` registry is what the guard sweeps iterate, so a profile added
  outside it is a test failure rather than a silent gap in coverage. The
  archetypes keep their own object because DECISIONS.md and CLAUDE.md §9 refer to
  them as a group.
- `ArchetypeProfileTest.exactly_the_three_archetypes_exist_and_no_more` asserted
  that no further profiles existed, which P3-4 has now authorised. It is replaced
  by a pin on the library's full contents, so growth is still deliberate rather
  than silent.

**Scope of this entry.** It records what MM-Fit's labels *define* for two
exercises and what anatomy implies for a third. It is not a claim about how
accurately Kinesis detects any of them — nothing here has been measured against
MM-Fit, and no such figure exists. That comparison is P7 and P12.

**Not fed to the engine.** Only MM-Fit's bundled OpenPose 2D pose was read, to
answer a question about MM-Fit's own labels. Per CLAUDE.md §14 those skeletons
never reach the Kinesis engine: the evaluation pipeline is MM-Fit RGB video →
MediaPipe Pose → Kinesis.

**Every threshold in the three new profiles is provisional** and marked so in
source, on the same footing as the archetypes' — starting points that let the
engine be exercised, not measured quantities. P6 personalises them from the
user's own calibration repetitions; P4 sanity-checks them on a device first.

---

## P3-6 — Exercise variants: the documented list, and the mechanism no document specifies

*2026-09-20*

CONTRACTS.md §3 requires that variants inherit their parent's profile,
calibration and claim ceiling, and CLAUDE.md §22 lists "treat exercise variants
as separate algorithms" among the things never to do. No document says how that
is enforced in code. This entry records the variant list as the documents
actually state it, and the mechanism, which is a gap of the same kind P3-2
resolved for the rep engine.

### The list, quoted

Both governing documents and CONTRACTS.md were searched for every mention of a
variant, of inheritance, and of the two named examples. **Exactly two variants
are named anywhere.** There is no third.

`docs/CONTRACTS.md` §3, lines 106–107:

> Variants inherit their parent's profile, calibration and claim ceiling: Goblet
> Squat → Squat, Reverse Lunge → Lunge.

`docs/FINAL_ARCHITECTURE.md` §5, "The final list", rows 2 and 10:

> | 2   | **Goblet Squat**                 | *Squat variant* | A     | Side        | Rep  |
>
> | 10  | **Reverse Lunge**                | *Lunge variant* | B     | Side        | Rep  |

`docs/FINAL_ARCHITECTURE.md` §5, "Counting honestly", line 300 — the only
statement anywhere of what a variant may differ in:

> Not "17 exercises". The variants share everything — profile, thresholds,
> calibration baseline, rules — and differ only in a label and a coaching note.
> An examiner who asks "is a goblet squat really a different algorithm?" should
> find you have already said no.

`docs/FINAL_ARCHITECTURE.md` §6, final scope table, line 320:

> | Variants sharing a parent | 2 — Goblet Squat, Reverse Lunge |

`docs/ZERO_DATASET_BUILD_PLAN.md`, revised exercise scope table, line 102:

> | Goblet Squat, Reverse Lunge | Variants | Via parent | Inherit the parent
> profile's claim ceiling |

`CLAUDE.md` §9, lines 146–147:

> Variants inherit their parent profile and calibration; they are not separate
> algorithms.

### What is built here, and what is blocked

| Variant | Parent | Parent's tier | Status |
|---|---|---|---|
| Goblet Squat | Squat | A | **Built in this batch** |
| Reverse Lunge | Lunge | B | **Blocked.** Lunge is not in the library |

Lunge is Tier B and has not been built. Per the batch instruction, its parent is
not built here to unblock it; the variant is deferred to whichever batch adds the
Tier B profiles. `TierAProfileTest` carries a test asserting that neither `lunge`
nor `reverse_lunge` is in the library, so that batch fails until Reverse Lunge is
added alongside its parent rather than being quietly forgotten.

**A third variant was considered by the documents and closed.**
`ZERO_DATASET_BUILD_PLAN.md` line 108, on shoulder press and lateral raise:

> Pick one: make the Kinesis profile seated so the benchmark applies, keep it
> standing and accept these two as deviation-only, or ship both as variants.

DECISIONS.md **P0-4** took the first option — both profiles are seated. There is
therefore no standing variant of either, and this is recorded so the option is
not re-raised as an oversight.

**A stale count in the source document.** `FINAL_ARCHITECTURE.md` §5's heading
reads "The final list — 15 entries, 13 profiles, 2 variants" above a table of 17
rows. **P0-5** already resolved this: the target is 17 entries / 15 profiles / 2
variants, and 15/13 is the cut-down position. Noted here only because the heading
is the first thing a reader of that section sees.

### What the documents do not say, stated rather than guessed

Three silences. Each is flagged in the same way P3-5 flagged the glute bridge's
semantics, because a decision made in the absence of a source should not later
read like one taken from it.

**1. What a coaching note is, and where it lives.** Line 300 is the only mention
of one in any document. Nothing defines its content, its length, or whether it is
analysis or presentation. **Decision:** it is a display-only string carried on
the variant and on nothing else. It is the one field a variant has of its own,
and giving it to the variant type rather than to `ExerciseProfile` means the
other six profiles do not gain a field nothing populates. It is never parsed and
never reaches an analysis decision, on the same footing as `displayName`.

**2. Whether a variant may differ from its parent in camera view or posture.**
The FINAL_ARCHITECTURE table gives every entry its own view column, which could
be read as permitting a difference — but for the only variant in scope the value
is identical to its parent's ("Side" for both Squat and Goblet Squat), so the
document never exercises the question. **Decision:** it may not. The mechanism
below makes every field except the label and the note come from the parent, so a
difference is not expressible without editing the variant class itself, and the
guard test is what notices if someone does. If a future variant genuinely needs a
different view, that is a new decision entry, not an override.

**3. Whether sharing a calibration baseline is the right model.** The documents
are unambiguous that variants share one — "profile, thresholds, calibration
baseline, rules" — and this batch follows them. **The consequence is worth
stating plainly, because it is an assumption about human movement and not about
software:** a goblet squat is performed holding a weight at the chest, which
plausibly changes trunk angle and achievable depth relative to an unloaded squat.
Under a shared baseline, a Level 2 finding of the form "shallower than your
normal" for a goblet squat is measured against the user's *air squat*
repetitions. That may well be the wrong comparison. It has not been measured, by
this project or against any dataset, and nothing here claims otherwise. It is
recorded so that if P6 calibration or P4 testing shows the two distributions
differ, this entry is the one to revisit rather than the behaviour being
rediscovered as a bug.

### The mechanism

No document specifies it, so it is designed here.

**Delegation, not subclassing.** A variant is a small class that delegates the
whole `ExerciseProfile` surface to its parent instance and re-declares only its
own identity:

```kotlin
class RepetitionVariant(
    override val parent: RepetitionExercise,
    override val id: String,
    override val displayName: String,
    override val coachingNote: String,
) : RepetitionExercise by parent, ExerciseVariant
```

`tier`, `maxClaimLevel`, `requiredView`, `posture`, `mode`, `requiredLandmarks`,
`repetitionSemantics` and `repSignal` are not written anywhere in the variant.
They are forwarded to the parent object at runtime, so there is no second copy to
drift and nothing to keep in step. A variant of an isometric profile is the same
shape against `IsometricExercise`, forwarding `holdSignal` instead.

**Why not subclassing.** `class GobletSquatProfile : SquatProfile()` would
require every profile class and every one of its properties to be `open`, which
is the opposite of the intent: it would make the parent's configuration
overridable by default and leave the invariant to reviewer attention. Delegation
inverts that. The default is inheritance, and a deviation requires deliberately
writing an `override` into the variant class, which is exactly the edit the guard
test is aimed at.

**What a variant may override: its `id`, its `displayName`, its `coachingNote`,
and nothing else.** That matches line 300's "differ only in a label and a
coaching note" as closely as a type can.

**A variant carries no technique authority of its own.** `TechniqueAuthority` is
reachable only through `TierAProfile.techniqueAuthority` (P1-3), and a variant is
not a `TierAProfile` — it is a delegating class, whichever tier its parent holds.
Its `maxClaimLevel` is its parent's, so the **ceiling** is inherited exactly as
CONTRACTS.md §3 requires, but the **route to constructing** a Level 3 finding runs
through `variant.parent`. This is deliberate and is stronger than the contract
asks for: a variant cannot author technique rules, it can only be analysed by its
parent's. That is what "not a separate algorithm" means, expressed in types. It
also stays safe without a special case — a Tier B parent has no authority to
lend, so a Tier B variant cannot reach one either.

**`ExerciseVariant` is a `sealed interface` extending `ExerciseProfile`,
carrying `parent` and `coachingNote`.** Sealed, so no third kind of variant can
be invented outside this file.

*This was written the other way round first.* The design note said the interface
should stay out of `ExerciseProfile`'s hierarchy, to avoid an inheritance diamond
with the delegated interface. Writing the guard tests against it showed that to
be wrong on both counts: a variant that is not an `ExerciseProfile` cannot be
asked for its own tier or camera view, so every comparison against the parent
needed a cast, and the diamond is not real — delegation generates an override for
every inherited member, so there is never more than one implementation to choose
between. Corrected here rather than quietly, because the surrounding argument for
delegation stands and this part of it did not.

**Entries and profiles are counted separately.** `ExerciseLibrary.all` is the
library's entries; `ExerciseLibrary.profiles` excludes variants and
`ExerciseLibrary.variants` is the complement. This is CLAUDE.md §9's honest-count
rule — "15 exercise profiles ... presented as 17 library entries" — made
structural instead of editorial, and it is why the Tier A sweep counts profiles
rather than entries. A variant inherits its parent's tier; it does not add one to
it.

### Consequences

- `TierAProfileTest.tier_a_is_exactly_the_five_exercises_contracts_assigns_it`
  now sweeps `profiles` rather than `all`. Left on `all`, the goblet squat would
  have read as a sixth Tier A exercise, which is the exact miscount CLAUDE.md §9
  forbids.
- `VariantInheritanceTest` compares every variant against its parent field by
  field, and drives the same synthetic repetitions through a tracker built on the
  variant and on the parent to show the counts and ranges of motion are
  identical. The declaration pin says they agree; that one shows they behave the
  same way.
- Nothing on the real-time path changed. `RepFsm`, `BilateralRepTracker` and
  `HoldAnalyser` see a `RepetitionExercise`/`IsometricExercise` as before and
  cannot tell a variant from its parent, which is the point.

**Scope of this entry.** It records what the documents name and how inheritance
is implemented. It makes no claim about how a goblet squat is detected, and none
about whether sharing a squat's calibration baseline describes a loaded squat
well — that is the open assumption stated above.

---

## P3-7 — Tier B lower-body and trunk profiles, and an unactioned instruction about the lunge

*2026-09-20*

Builds five of the seven Tier B profiles CONTRACTS.md §3 assigns — Lunge, Hip
Abduction, Knee Raise, Sit-up, Leg Raise — and the Reverse Lunge variant their
arrival unblocks. Shoulder Press and Row remain, and are upper-body.

### What the documents already decided, quoted

`docs/CONTRACTS.md` §3, line 103:

> | B | Shoulder Press *(seated)*, Row *(near side only)*, Lunge, Hip Abduction,
> Knee Raise, Sit-up, Leg Raise |

`docs/FINAL_ARCHITECTURE.md` §5, "The final list", rows 9–14 — the camera view
and mode for each:

> | 9   | Lunge                  | Lunge         | B | Side        | Rep |
>
> | 10  | **Reverse Lunge**      | *Lunge variant* | B | Side      | Rep |
>
> | 11  | Standing Hip Abduction | Hip Abduction | B | Front       | Rep |
>
> | 12  | Standing Knee Raise    | Knee Raise    | B | Side        | Rep |
>
> | 13  | Sit-up                 | Sit-up        | B | Side        | Rep |
>
> | 14  | Lying Leg Raise        | Leg Raise     | B | Side, floor | Rep |

`docs/FINAL_ARCHITECTURE.md` §4, the configuration-versus-validation table:

> | Sit-up                 | 0.25 d | 1 d   | 0 | High   | Low    | High   | Good     |
>
> | Standing Knee Raise    | 0.25 d | 1 d   | 0 | High   | Low    | High   | Good     |
>
> | Lying Leg Raise        | 0.25 d | 1 d   | 0 | High   | Low    | High   | Good     |
>
> | Standing Hip Abduction | 0.5 d  | 1.5 d | 0 | Medium | Medium | Medium | Adequate |
>
> | **Lunge** | 1 d | **4 d** | 0 | **Low-medium** | **High — rear leg occluded**
> | Low for rear leg | **Weak** |

`docs/FINAL_ARCHITECTURE.md` §5, on the replacement that brought the leg raise in:

> **Lying Leg Raise** — hip flexion, side view, large clean ROM, present in
> InfiniteRep, very reliable segmentation.

`docs/ZERO_DATASET_BUILD_PLAN.md`, revised exercise scope, lines 95 and 100:

> | Lunge | B | Direct | Rep counting measured on benchmark; deviation |
>
> | Lying Leg Raise | B | None | Deviation only, validated by live testing |

`docs/ZERO_DATASET_BUILD_PLAN.md`, "Two changes worth making", line 114:

> Add **sit-up** to the library. It is in MM-Fit with full labels, it is a
> side-view exercise whose primary signal is trunk flexion and therefore clearly
> visible, and it costs nothing in data.

### The lunge: an instruction the documents issue and never carry out

`FINAL_ARCHITECTURE.md` §4 puts the lunge in the worst category it defines and
tells the reader to go and fix it:

> | **Cheap to configure, expensive to validate** | **Lunge, bent-over row,
> front raise** | **Fix the view or cut** — section 5 |

§5 then opens:

> Re-examining the list against what a single camera can actually see, three
> entries have problems I did not flag.

**and the three it examines are Front Raise, Bent-over Row and Overhead Triceps
Extension.** The lunge is not among them. Front Raise and Overhead Triceps are
cut, Bent-over Row is restricted to the near side — and the lunge arrives in the
final list at row 9, filmed "Side", with no restriction and no further mention,
carrying §4's own verdict of the highest testing cost in the table (4 days), view
difficulty "High — rear leg occluded", landmark reliability "Low for rear leg",
and a viva rating of "Weak".

**Decision: build it as the final list specifies, and record the gap rather than
close it here.** The final list is the later and more specific statement, and
inventing a restriction for the lunge — by analogy with the row's near-side-only
treatment, say — would be exactly the silent redesign CLAUDE.md §20 forbids.

**The concrete consequence, stated so P4 can look for it.** Filmed side-on, the
rear leg is behind the front one for most of the movement. The lunge is
`SIMULTANEOUS` (measured below), and under that semantics `BilateralRepTracker`
pairs one cycle from each side into one repetition; a side whose partner never
completes is emitted as `INSUFFICIENT_DATA` rather than counted (P3-2). So if the
rear knee tracks as badly as §4 predicts, the failure mode is **under-counting
with a trail of `INSUFFICIENT_DATA` records**, not a wrong angle. That is a
prediction from the design, not a measurement; nothing here says how often it
happens, and P4 is where it is found out. If it does happen, the options are the
row's near-side-only treatment, a 45-degree view, or the cut §4 already
authorises — and that will be its own entry.

### Sit-up: this batch, not the upper-body one

CONTRACTS.md lists Sit-up under Tier B without saying which body region it
belongs to, and it is trunk flexion rather than lower-body movement, so the call
is genuinely open. **Decision: it is built here.**

**Reasoning.** The batch boundary that matters is not anatomical labelling but
which joint drives the signal and which landmarks have to be visible, because
that is what the profiles share and what device testing will exercise together.
Sit-up drives off the shoulder-hip-knee angle, as do Knee Raise and Leg Raise in
this batch, and requires the same six landmarks; the two remaining Tier B
profiles drive off the shoulder and the elbow and require the arms. Grouped by
that, the sit-up belongs with the trunk-and-leg set and fits the upper-body batch
worse than it fits this one. It also has MM-Fit labels, so it can be measured in
the same pass as the lunge rather than in a pass of its own.

`FINAL_ARCHITECTURE.md` §4 groups it the same way, for what that is worth:

> | **Cheap to configure, cheap to validate** | Sit-up, knee raise, lying leg
> raise, wall sit, plank | Add freely |

**A contingency this keeps cheap.** P0-3 and `FINAL_ARCHITECTURE.md` §12 both say
that if P4 shows the glute bridge segments poorly, the sit-up is promoted to Tier
A in its place. Building it now means that promotion is a change of base class
and of one pinned assertion, rather than a new profile written under time
pressure.

### Repetition semantics

Two of the five can be measured against MM-Fit's labels. Three cannot, and are
marked as assumptions in the manner P3-5 established for the glute bridge.

**MM-Fit's ten classes**, read from the label files: `bicep_curls`,
`dumbbell_rows`, `dumbbell_shoulder_press`, `jumping_jacks`,
`lateral_shoulder_raises`, `lunges`, `pushups`, `situps`, `squats`,
`tricep_extensions`. There is no hip abduction, no knee raise and no leg raise.
`jumping_jacks` was considered as a proxy for hip abduction and rejected: a
jumping jack abducts both legs at once off the floor, which is the opposite of
the thing in question for a standing single-leg abduction.

#### Measured — Lunge and Sit-up

Same threshold-free method as P3-1 and P3-5. **The lunge needed a signal the
earlier passes did not use.** Both knees flex on every repetition of a lunge
whichever leg is forward, so a knee-angle measurement reads one cycle per side
either way and cannot distinguish an alternating lunge from a same-leg one. A
third signal was added for it — each ankle's distance from its hip, which is
large for the stepping leg and small for the trailing one, so if the legs swap
roles between repetitions it runs at half the labelled rate on each side.

| Exercise | Signal | Sets | Cycles/side/rep | SD | L/R phase | Both = label | Both = label ÷ 2 |
|---|---|---|---|---|---|---|---|
| **lunges** | knee angle | 62 | **1.00** | 0.07 | +0.82 | 59 | 1 |
| **lunges** | hip height | 62 | **1.00** | 0.01 | +0.99 | 61 | 0 |
| **lunges** | stance (ankle from hip) | 62 | **1.00** | 0.15 | +0.48 | 37 | 3 |
| **situps** | hip angle (trunk) | 65 | **1.00** | 0.07 | +0.99 | 52 | 0 |
| **situps** | shoulder height | 65 | **1.00** | 0.06 | +0.99 | 52 | 0 |
| squats *(control)* | knee angle | 64 | 1.00 | 0.02 | +0.96 | 61 | 0 |
| squats *(control)* | stance | 64 | 1.00 | 0.02 | +1.00 | 61 | 0 |
| bicep_curls *(control)* | elbow angle | 59 | **0.50** | 0.01 | **−0.40** | 0 | **56** |

Both are `SIMULTANEOUS`. The stance signal is the one that carries the argument
for the lunge: it was constructed specifically to read 0.50 if the legs
alternate, and it reads 1.00 in 37 of 62 sets against 3 at half.

**One honest detail about the lunge, which the phase column shows.** Its stance
phase is +0.48, well below the +0.99 its own hip height gives and the +1.00 the
squat gives on the same signal. That is what it looks like when the two legs do
*different things* within a repetition — one steps out while the other stays —
at the *same rate*. Kinesis counts a lunge as one repetition either way, but it
does not model which leg was forward, so a set of alternating lunges and a set of
same-leg lunges are indistinguishable to it. That is a stated limitation, not a
defect being hidden: nothing in the profile claims otherwise, and no document
asks for the distinction.

#### Assumed from anatomy — Hip Abduction, Knee Raise, Leg Raise

No dataset constrains any of these. Each is flagged the way P3-5 flagged the
glute bridge, and the three are not equally strong.

**Standing Hip Abduction — `PER_LIMB`.** One leg lifts out to the side while the
other bears the body's weight. Both legs cannot abduct at once from standing, so
this is close to physical necessity rather than a judgement about how the
exercise is usually performed.

**Standing Knee Raise — `PER_LIMB`.** The same argument: one knee lifts while the
other leg stands.

**Lying Leg Raise — `SIMULTANEOUS`, and this is the weakest of the three.** The
exercise named in FINAL_ARCHITECTURE is the double-leg version, in which both
legs rise together from supine, and its "large clean ROM" description fits that
version. But a single-leg lying leg raise is a common exercise in its own right,
and nothing in any document says which is meant beyond the name. If the intent
was the single-leg version this is a factor-of-two error of exactly the kind P3-1
was raised to prevent. It is recorded here as the one to check first if leg-raise
counts ever look halved, and `TierBProfileTest` pins it so the correction is a
one-line change with a failing test attached.

### Reverse Lunge, and a Batch 2 guard test that had to be replaced

The lunge's arrival unblocks the Reverse Lunge variant P3-6 deferred, which is
built here through the same `RepetitionVariant` delegation.

**The deferral guard could not be made to pass as written, and was replaced by a
stronger one.** `VariantInheritanceTest.the_reverse_lunge_variant_is_deferred_until_its_parent_profile_exists`
asserted that *neither* `lunge` nor `reverse_lunge` was in the library. It was
meant to fire when the parent arrived without its variant; it actually fires
whenever the parent arrives, variant or no variant. That is a flaw in the guard,
found by its first real use.

It is replaced by `every_documented_variant_whose_parent_exists_is_built`, which
holds the two variant-to-parent pairs the documents name — Goblet Squat → Squat,
Reverse Lunge → Lunge — and asserts for each that if the parent is in the
library then so is the variant, attached to that parent. This is **stronger, not
weaker**: the old test would have had to be deleted after this batch, while this
one keeps guarding both pairs permanently and will guard any pair added to the
list. Recorded explicitly because replacing a guard test is exactly the move
CLAUDE.md §18 warns about, and the justification belongs on the record rather
than in a commit message.

### Provisional thresholds, as always

Every threshold in the five new profiles is provisional and marked so in source,
on the same footing as every other in the library. They are starting points that
let the profiles be exercised, not measured quantities; P6 personalises them from
the user's own calibration repetitions and P4 sanity-checks them on a device
first. Nothing here has been validated against a human being.

**Scope of this entry.** It records what MM-Fit's labels define for two
exercises, what anatomy implies for three more, and one instruction the source
documents issue and never carry out. It is not a claim about how accurately
Kinesis detects any of them — no Kinesis output has been compared against MM-Fit,
and no such figure exists. That comparison is P7 and P12. Only MM-Fit's bundled
OpenPose 2D pose was read, to answer questions about MM-Fit's own labels; per
CLAUDE.md §14 those skeletons never reach the Kinesis engine.

---

## P3-8 — Tier B upper body, and a camera-view contract that nothing was enforcing

*2026-09-20*

Completes Tier B with Shoulder Press and Row. Both repetition semantics were
measured against MM-Fit rather than assumed. The row forced a question no earlier
batch had to answer — what it means for a profile to be analysable on one side
only — and answering it exposed that `requiredView` and `posture` had been
declared by thirteen profiles and read by nothing.

### What the documents say, quoted

`docs/CONTRACTS.md` §3, line 103 — the tier and both restrictions, in one line:

> | B | Shoulder Press *(seated)*, Row *(near side only)*, Lunge, Hip Abduction,
> Knee Raise, Sit-up, Leg Raise |

`docs/FINAL_ARCHITECTURE.md` §5, "The final list", rows 7 and 8 — views:

> | 7   | Shoulder Press                   | Shoulder Press | B | Front | Rep |
>
> | 8   | Bent-over Row *(near side only)* | Row            | B | Side  | Rep |

`docs/FINAL_ARCHITECTURE.md` §4, the configuration-versus-validation table:

> | Shoulder Press    | 0.5 d | 1.5 d     | 0 | Medium         | Medium — wrists
> exit frame  | Medium           | Adequate |
>
> | **Bent-over Row** | 0.5 d | **2.5 d** | 0 | **Low-medium** | **High — far
> arm occluded** | Low for far side | **Weak** |

`docs/FINAL_ARCHITECTURE.md` §5, the row's restriction in full — the only place
any document says what "near side only" means:

> **Bent-over Row — the far arm is invisible.** With the torso roughly
> horizontal and the camera to one side, the near arm is clean and the far arm is
> behind the body for most of the movement. Trunk angle also varies enormously
> between people. **Keep it, but near-side only, and say so explicitly** — no
> per-side comparison, no symmetry claim. That is an honest restriction and cheap
> to enforce in the profile.

`docs/FINAL_ARCHITECTURE.md` §12, limitations:

> | Per-side analysis of Bent-over Row | Far arm occluded; near side only |

`docs/ZERO_DATASET_BUILD_PLAN.md`, revised exercise scope, lines 96 and 98:

> | Shoulder Press | A or B | Seated in MM-Fit | Measured only if your profile is
> seated too; otherwise deviation only |
>
> | Bent-over Row | B | Standing row, near-side only | Deviation only; posture
> mismatch too large to claim measurement |

`docs/ZERO_DATASET_BUILD_PLAN.md` line 53, MM-Fit's own exercise list:

> 10: squats, lunges, bicep curls, sit-ups, push-ups, seated overhead triceps
> extensions, **standing dumbbell rows**, jumping jacks, **seated dumbbell
> shoulder press**, seated dumbbell lateral raises

### Checking the two assumptions, as instructed

**"Seated" on Shoulder Press: confirmed, and it is a decision rather than a
description.** CONTRACTS.md writes the tier row as "Shoulder Press *(seated)*",
MM-Fit performs it seated, and ZERO_DATASET is explicit that the Kinesis profile
is "measured only if your profile is seated too; otherwise deviation only".
P0-4 already took that choice for this exercise and the lateral raise. Nothing is
looser or stricter than assumed. Declared as `Posture.SEATED` and pinned.

**"Near side only" on Row: looser than assumed in one respect, and stricter in
another.**

*Looser* — no document names a camera side. §5 says only "the camera to one
side", and the near arm is whichever is nearer. Nothing in the profile fixes left
or right, and the shared core still never learns which is which, per CLAUDE.md
§4. An earlier reading of "near side only" as a required camera side would have
been wrong.

*Stricter* — the restriction is not merely an acknowledgement that the far arm is
noisy. §5 says it is **invisible**, "behind the body for most of the movement",
and forbids two specific outputs: "no per-side comparison, no symmetry claim".
That is a constraint on what the profile may emit, not a caveat about data
quality, and it is the reason a comment would not have been enough.

**§5 on these two exercises — silent on one, not silent on the other, and the
difference matters.** P3-7 recorded that §4 sent the lunge to §5 under "Fix the
view or cut" and that §5 never returned to it. Re-checked here: §5 **does**
address the row, at length, and resolves it — keep it, near-side only. It also
resolves the front raise (cut) and the overhead triceps extension (cut). 

**Correction to original P3-8 claim:** This entry originally claimed that §4 flagged four exercises, and that §5 addressed three of them. A direct grep of `docs/FINAL_ARCHITECTURE.md` for "Fix the view or cut" yields exactly this row from §4:
`| **Cheap to configure, expensive to validate** | **Lunge, bent-over row, front raise** | **Fix the view or cut** — section 5 |`

The correct count of flagged exercises is **three**, not four. §5 addressed two of those three (row and front raise) while also spontaneously addressing one unflagged exercise (overhead triceps extension), leaving only the lunge skipped. That narrows P3-7's finding from "§5 is unreliable" to "§5 has exactly one omission", which is worth knowing.

§5 **is** silent on Shoulder Press, and that silence is explained rather than
suspicious: §4 put it in "Cheap to configure, moderate to validate — Add, budget
the testing", not in the category §5 was written to resolve. But §4 does flag one
view problem for it — "Medium — wrists exit frame" — that no later section picks
up, and which is acted on below.

### Repetition semantics, both measured

Same threshold-free method as P3-1, P3-5 and P3-7, with signals chosen per
exercise rather than reused. Three signals each.

| Exercise | Signal | Sets | Cycles/side/rep | SD | L/R phase | Both = label | Both = label ÷ 2 |
|---|---|---|---|---|---|---|---|
| **dumbbell_shoulder_press** | hand height | 60 | **1.00** | 0.05 | +1.00 | 47 | **0** |
| **dumbbell_shoulder_press** | shoulder abduction | 60 | **1.00** | 0.05 | +0.99 | 48 | **0** |
| **dumbbell_shoulder_press** | elbow angle | 60 | **1.00** | 0.05 | +0.99 | 49 | **0** |
| **dumbbell_rows** | hand height | 64 | **1.00** | 0.02 | +0.99 | 60 | **0** |
| **dumbbell_rows** | elbow angle | 64 | **1.00** | 0.07 | +0.90 | 56 | **0** |
| **dumbbell_rows** | wrist-to-shoulder | 64 | **1.00** | 0.02 | +0.99 | 60 | **0** |
| squats *(control, re-checked)* | knee angle | 64 | 1.00 | 0.02 | +0.96 | 61 | 0 |
| lateral_shoulder_raises *(control, re-checked)* | shoulder abduction | 56 | 1.00 | 0.03 | +0.99 | 52 | 0 |
| bicep_curls *(control, re-checked)* | elbow angle | 59 | **0.50** | 0.01 | **−0.40** | 0 | **56** |

Both are `SIMULTANEOUS`. Not one of the 124 sets across the two exercises reads
half the label on any signal, while the bicep-curl control reads half in 56 of 59
under the same code in the same run.

**Shoulder press re-derived rather than cited.** P3-5 used it only as a control
for the push-up work, on hand height alone, and reported 1.00 at SD 0.05 with
phase +1.00. Re-derived here as the primary finding on three signals, it gives
1.00 at SD 0.05 with phase +1.00 — the same numbers. The earlier control result
stands.

**What the row's measurement does and does not transfer.** MM-Fit's rows are
**standing** dumbbell rows; the Kinesis profile is a **bent-over** row, and
ZERO_DATASET says that posture mismatch is "too large to claim measurement".
What carries across is the **definition of a repetition** — both arms pull
together, so one pull is one repetition — which is a property of the movement
pattern rather than of the trunk angle it is performed at. What does **not**
carry across is any claim about how well Kinesis detects one. CLAUDE.md §22
forbids transferring a benchmark result across a posture change, and this entry
does not.

### The row's occlusion, resolved

**The question.** "Near side only" is structurally unlike either case the engine
already handled. It is not `PER_LIMB`, where limbs alternate and each is
eventually visible. It is not the intermittent occlusion P3-2 covers, where a
two-sided movement loses a side for a while and the unpaired cycle is reported as
`INSUFFICIENT_DATA`. It is a two-sided movement one of whose sides is *never*
there — so waiting for it means waiting forever.

**What the existing machinery actually did, measured rather than assumed.** The
guard test was written first and run against the unmodified tracker, with the row
declared `SIMULTANEOUS` and the far arm below the visibility floor for the whole
set. Ten repetitions on the visible arm produced:

```
expected:<10> but was:<0>
```

**No counted repetitions at all.** Each completed near-side cycle was parked in
`pendingLeft` waiting for a partner and overwritten by the next, and `finish()`
flushed a single `INSUFFICIENT_DATA` record. Nine of the ten left no trace of any
kind. The prediction before running it was that one repetition would survive; the
observed answer was worse. The existing types do **not** cover this case, and the
answer to "does it already degrade correctly" is no.

**The representation chosen: a second axis, not a third semantics value.**

```kotlin
enum class AnalysedSides { BOTH, NEAR_SIDE_ONLY }
```

declared on every `RepetitionExercise` with no default, exactly as
`RepetitionSemantics` is and for the same reason.

The two could have been collapsed into one enum — a `NEAR_SIDE_ONLY` case
alongside `PER_LIMB` and `SIMULTANEOUS` — and that was rejected. They answer
different questions. Semantics is a fact about the **movement**: do the limbs
work together or in turn. `AnalysedSides` is a fact about the **view**: can the
camera see both of them. A bent-over row is a genuinely two-armed movement that a
side-on camera can only ever see one arm of, and one enum cannot say both things
at once. Keeping them separate is also what lets a guard test check them against
each other, which a single enum would make vacuous.

**What the tracker does with it.** Under `SIMULTANEOUS` + `NEAR_SIDE_ONLY` the
pairing requirement is dropped: each completed cycle is one repetition. Two
details matter.

- The repetition is **not attributed to a side**. Both arms of a row pull
  together, so the repetition belongs to no single limb — only half of it was
  measured. `attributedSide` is therefore `null`, unlike `PER_LIMB`, where the
  side is the point.
- The unmeasured side stays `Double.NaN`, and every reducer on `BilateralSignal`
  propagates `NaN`. So `asymmetry()` on a row repetition is `NaN`, and there is
  no finite number for anything downstream to report as a symmetry finding.
  §5's "no per-side comparison, no symmetry claim" is therefore enforced by the
  arithmetic rather than requested in prose.

If the far arm does become briefly measurable and completes a cycle overlapping
one already reported, that is the same repetition seen twice and is suppressed.

**The per-frame cost is one boolean test.** The pairing decision is computed once
at construction from two values fixed at construction, not per frame. The P2
budget guards, the per-frame allocation test and the source scan were re-run and
all pass, so CLAUDE.md §6 still holds.

### The gap this uncovered: a view contract nothing was enforcing

Every profile has declared `requiredView` and `posture` since P1. **Nothing read
either of them** — not the tracker, not the gate, not any sweep. They were pinned
only by per-profile equality assertions, so a profile could declare a view from
which its own driving joint is unmeasurable and no test would notice. P3-7
recorded one instance of exactly that kind of omission going unactioned upstream,
in the source documents; this was the same shape of omission inside the codebase.

`ViewAndSideContractTest` is the check. It does **not** try to decide whether a
view is sensible for an exercise — that is a per-exercise judgement the documents
make, and a general rule here would contradict them, since the push-up and the
bicep curl drive off the same joint from opposite views each for a stated reason.
What it checks is that the declarations do not contradict **each other**:

- alternating limbs plus a view that sees only one is a contradiction with no
  honest resolution, and is forbidden;
- a near-side-only profile must not require landmarks on the side it cannot see;
- a both-sided profile must require its driving joint on both sides;
- a near-side-only profile must produce one measured side and no finite
  asymmetry, verified by running one;
- a both-sided profile must *not* report a full count when half the body is
  missing, which is what stops the near-side handling from being a no-op applied
  everywhere.

**One earlier test was replaced, and the replacement is strictly larger.**
`TierAProfileTest.every_profile_requires_the_landmarks_its_driving_joint_is_built_from`
asserted that every profile requires its driving joint on both sides. Applied to
the row that would have forced it to require a far elbow and wrist it can never
see — and `ValidityGate.evaluate` returns `INVALID` if **any** required landmark
falls below the floor, so the exercise would have been blocked on every frame.
The replacement keeps that assertion for both-sided profiles and adds the
opposite assertion for near-side-only ones. It is more than the test it replaces,
not an exemption from it, and it is recorded here for the same reason P3-7
recorded the reverse-lunge replacement.

### Two smaller decisions

**Shoulder press drives off the shoulder, not the elbow.** §4 rates its view
difficulty "Medium — wrists exit frame": at lockout the hands are the highest
thing in the picture and the first to leave it, and the elbow angle needs a wrist
while shoulder abduction needs only hip, shoulder and elbow. The measurement
above shows the two signals are indistinguishable in quality — 1.00 at SD 0.05
either way — so choosing the one that does not depend on a landmark the documents
say leaves the frame costs nothing. Its `requiredLandmarks` contain no wrists.

**`Posture.HINGED` was added for the row.** The enum had STANDING, SEATED, PRONE,
SUPINE and SIDE_LYING, none of which describes a trunk hinged toward horizontal.
Declaring the row STANDING would have erased the very difference that makes
MM-Fit's benchmark inapplicable to it — MM-Fit's row *is* standing, and
ZERO_DATASET calls that mismatch "too large to claim measurement". P0-4's reason
for posture existing at all is that it changes the trunk reference, and a
bent-over row changes it more than any other entry in the library. Nothing
consumes `Posture` exhaustively, so the addition is purely additive.

**The row's required landmarks are the torso, not the arms.** With the far arm
invisible by construction, requiring both elbows and both wrists would invalidate
every frame. The arm requirement is carried by `analysedSides`; what the gate
checks is the hinged torso — which is also the thing §5 warns "varies enormously
between people".

### Library counts

| | Before this batch | After |
|---|---|---|
| Entries | 13 | **15** |
| Profiles | 11 | **13** |
| Variants | 2 | **2** |

Tier A is complete at five and Tier B is now complete at seven. The two
outstanding entries against the 17 / 15 / 2 target of P0-5 are the Tier C
isometrics that are not yet built: **Wall Sit** and **Side Plank**, alongside the
existing Plank.

**Scope of this entry.** It records what MM-Fit's labels define for two of its own
exercises, how the row's documented view restriction is represented, and a gap in
enforcement that existed before this batch. It is not a claim about how accurately
Kinesis detects anything — no Kinesis output has been compared against MM-Fit, and
no such figure exists. Only MM-Fit's bundled OpenPose 2D pose was read, to answer
questions about MM-Fit's own labels; per CLAUDE.md §14 those skeletons never reach
the Kinesis engine. Every threshold in the two new profiles is provisional.

---

## P3-9 — Wall Sit and Side Plank, and why neither threshold carries a citation

*2026-09-20*

Completes Tier C, and with it the library. Two questions had to be answered that
no earlier batch faced: what a published threshold means for an exercise that
cannot make a technique claim, and whether a one-sided *hold* needs the
`AnalysedSides` concept P3-8 introduced for repetitions.

### What the documents say, quoted

`docs/CONTRACTS.md` §3, line 104:

> | C | Wall Sit, Plank, Side Plank |

`docs/FINAL_ARCHITECTURE.md` §5, "The final list", rows 15–17:

> | 15  | Wall Sit   | Wall Sit   | **C** | Side  | Hold |
>
> | 16  | Plank      | Plank      | **C** | Side  | Hold |
>
> | 17  | Side Plank | Side Plank | **C** | Front | Hold |

`docs/FINAL_ARCHITECTURE.md` §4, the configuration-versus-validation table —
note that the side plank has **no row**, because §5 added it after that table was
written:

> | Wall Sit | 0.5 d | 1 d | 0 | High | Low    | High | Good |
>
> | Plank    | 0.5 d | 1 d | 0 | High | Medium — floor framing | High | Good |

`docs/FINAL_ARCHITECTURE.md` §5, on the side plank's arrival as a replacement:

> **Side Plank** — second isometric, pairs naturally with plank and wall sit,
> tests the hold analyser on a different geometry, trivially reliable.

`docs/FINAL_ARCHITECTURE.md` §3, on why a body-line rule cannot be shared:

> | Body-line deviation | **No** — push-up sag, plank sag, bridge extension and
> wall-sit posture are different geometries sharing a word | Partly | **Yes** |
> **Exercise-specific rule** |

`docs/ZERO_DATASET_BUILD_PLAN.md`, revised exercise scope, line 101 — the only
statement of what a Tier C profile may claim:

> | Wall Sit, Plank, Side Plank | C | None | Hold duration and stability; no
> repetition claim applies |

`CLAUDE.md` §11:

> Two modes: **repetition** (generic rep FSM) and **isometric hold** (hold
> analyser) — e.g. plank, wall sit.

**MM-Fit coverage: none, confirmed.** ZERO_DATASET line 101 states it directly
for all three, and MM-Fit's ten classes — read from its label files in P3-5 —
contain no hold of any kind. No document says otherwise.

### Thresholds: two corrections before anything else

**The brief for this batch asked for thresholds cited "the same way the original
Plank profile's hold threshold is sourced". The plank's threshold is not
sourced.** It reads, in full:

```kotlin
// A straight shoulder-hip-knee line. Provisional.
targetValue = 180.0,
toleranceBand = 20.0,
```

There is no citation, and the word "Provisional" is doing the work. So there was
no precedent to follow, and establishing one is part of this entry rather than
something to copy.

**The second correction is a category one, and it decides the question.**
CLAUDE.md §12a requires that *"every Layer 2 technique rule carries a `sourceRef`
… A rule with no traceable source does not ship"*, and scopes Layer 2 to
*"Exercise-specific form rules, **Tier A only**"*. In code, `sourceRef` is a
constructor parameter of `TechniqueFault`, which demands a `TechniqueAuthority`,
which is reachable only through `TierAProfile.techniqueAuthority` (P1-3).

Wall Sit and Side Plank are **Tier C**. They cannot construct a `TechniqueFault`
— the call does not compile — so there is no Layer 2 rule for a citation to
justify and no field for one to live in. `HoldSignalSpec.targetValue` is a
**detection** parameter: it decides whether the subject is in the position at
all, on exactly the same footing as every `RepSignalSpec.topThreshold` in the
library. P3-2 recorded that a detection threshold must not leak into the
quantities being measured; the same boundary read the other way is that it must
not be dressed as a validated one. Attaching a citation to
`HoldSignalSpec` would imply these profiles carry cited technique rules, which
is precisely the overclaim §12a and P0-7 exist to prevent.

**The literature was searched rather than assumed absent.** Two searches, and
neither yields a threshold of the kind §12a means:

- **Wall sit.** The work that exists characterises what happens *at* a knee
  angle — quadriceps activation reaching 80–90% of maximum EMG under isometric
  contraction, patellofemoral compressive force peaking between 60° and 90° of
  flexion — rather than publishing a pass/fail criterion for whether a wall sit
  is being held correctly. "Knees at ninety" is an exercise-prescription
  convention, not a measured threshold.
- **Side plank.** The standard protocol is McGill's side bridge, and it is a
  **duration** test. Its termination criteria are qualitative — the hips drop,
  the trunk rotates, the shoulder position fails — and the search surfaced no
  published angle in degrees for hip drop.

**Both thresholds are therefore declared provisional**, exactly like every other
in the library, and the claim level is marked by the tier itself: Tier C caps at
`L2_DEVIATION`, structurally, with `TierCProfileTest` pinning it.

**What the literature does support is the shape of the output.** A duration test
with qualitative failure criteria maps directly onto what `HoldAnalyser` already
reports — total hold, longest unbroken hold, break count — and onto what
ZERO_DATASET already permits Tier C to claim: *"Hold duration and stability; no
repetition claim applies."* That agreement is worth noting precisely because it
is not a threshold citation and should not be presented as one.

**Where a citation will genuinely be needed** is P8's Tier A technique rules —
push-up trunk sag, squat depth, and the rest — which do not exist yet. That is
the work §12a was written for, and nothing in this batch discharges or
anticipates it.

### The side plank's one-sidedness: no `AnalysedSides` for holds

A side plank is performed on one side of the body. That is a third kind of
one-sidedness, distinct from both cases already handled: it is not alternating
limbs (`PER_LIMB`), and it is not a limb the camera cannot see (`NEAR_SIDE_ONLY`,
P3-8). P3-8 cost a full guard-test cycle because that case had been left
implicit, so this one is settled here rather than left to surface later.

**Decision: `AnalysedSides` is not extended to `IsometricExercise`.** Two
reasons, both verified rather than asserted.

**1. The hold signal is a property of the body line, not of a limb.** The side
plank holds on the shoulder-hip-knee angle, and both sides' triples describe the
same line. `a_body_line_angle_reads_the_same_on_both_sides_because_it_is_one_line`
measures it: with both sides visible the two angles agree to within a degree.
There are not two limbs to choose between, so "which side is analysed" is not a
question the exercise poses. That is what makes a near-side-only declaration
*meaningless* here rather than merely unnecessary.

**2. There is nothing that could consume it.** `HoldAnalyser.step` takes a
`Double`, not a `BilateralSignal`, and there is no landmark-driven hold tracker
anywhere in the codebase — no isometric counterpart to `BilateralRepTracker`. The
pairing decision `AnalysedSides` feeds in P3-8 has no analogue on the hold side,
so a declaration added now would be a field read by nothing. That is exactly the
failure P3-8 uncovered for `requiredView` and `posture`, and repeating it in a
new corner to look thorough would be worse than leaving the concept out.

**The degradation is already correct, and was checked.**
`a_side_plank_with_the_down_side_occluded_yields_one_measured_side_and_a_nan`
drives `JointAngles.compute` on a side-plank frame whose down-side landmarks sit
below the visibility floor: one side comes back measured, the other `NaN`, and
`asymmetry()` is `NaN`. Nothing special was needed, because this is what
`JointAngles` has done since P2. Mutating it to substitute the other side —
reintroducing the prototype's `left ?: right`, which DECISIONS.md P2-2 records —
fails that test alongside the P2 guard and the P3-8 contrast test, three guards
from three phases catching one anti-pattern.

**What remains open, stated so it is not rediscovered.** When a landmark-driven
hold tracker is eventually built, it must reduce the `BilateralSignal` to the
scalar `HoldAnalyser` wants through an explicit named reducer, per CLAUDE.md §3 —
never an average, and never a silent fallback. That is the point at which the
question "which side" becomes real for holds, and it is the point at which this
decision should be revisited. It is not real now.

### Two smaller calls

**Wall Sit is `Posture.SEATED`.** The enum has no better value and none is added:
a wall sit's body configuration *is* sitting — hips and knees near ninety, trunk
upright — with the wall in place of a chair. It deliberately does **not** carry
the implication P0-4 attached to the seated lateral raise and shoulder press,
that seating removes the lower body from the picture, because for a wall sit the
lower body is the entire measurement. Recorded because `SEATED` now means two
different things in two places, and that is worth someone knowing.

**The side plank is filmed front-on, and this is not the intuitive guess.** §5
row 17 assigns Front. In a side plank the body's frontal plane faces sideways, so
a camera facing the subject sees the whole body line and the hip drop that breaks
it; a camera at the subject's side looks *along* that line and sees nothing. The
profile's hold spec is also deliberately its own instance rather than a reuse of
the plank's, because §3 lists body-line deviation among the things that cannot be
shared — "different geometries sharing a word" — and
`the_plank_and_the_side_plank_are_separate_specifications` fails if someone
reuses one for the other.

### The library is complete: 17 / 15 / 2, confirmed

| | Before | After |
|---|---|---|
| Entries | 15 | **17** |
| Profiles | 13 | **15** |
| Variants | 2 | **2** |

That is P0-5's target exactly — *"17 entries / 15 profiles / 2 variants"* — and
CLAUDE.md §9's honest count: **15 exercise profiles across 3 tiers and 2 analysis
modes, presented as 17 library entries including 2 variants that share a parent
profile and calibration.** Tier A holds five, Tier B seven, Tier C three.

The arithmetic was checked entry by entry against FINAL_ARCHITECTURE §5's
seventeen-row final list rather than by counting to seventeen. Every row maps to
exactly one library entry, and every library entry to exactly one row.

**That audit turned up one divergence, and it had never been recorded.**
FINAL_ARCHITECTURE row 4 assigns the Bicep Curl a view of **"Side / 45°"**. The
profile built in P3 declares `FRONT_ON`, with the reasoning in its KDoc —
alternating arms must both be visible, and side-on occludes the far arm for the
whole set and halves the count. That reasoning is sound and the divergence is
kept, but it was never written down anywhere outside the source file.

It is worth more than a footnote, because the guard added in P3-8 now rejects the
document's version independently: the bicep curl is `PER_LIMB`, and
`no_profile_combines_alternating_limbs_with_a_view_that_sees_only_one` forbids
exactly that pairing. The document's assignment is unsound for the same reason
§5 cut the front raise — "from the **side**, the two arms travel in the same
plane and occlude each other" — and §5 simply did not apply that reasoning to the
curl. This is the second such omission found in §5, after the lunge in P3-7, and
both were found by building rather than by reading.

**Scope of this entry.** It records what the documents assign, why no threshold
here carries a citation, and how a one-sided hold is represented. It makes no
claim about how accurately Kinesis detects any hold — no Kinesis output has been
compared against anything, and no such figure exists. Every threshold in both new
profiles is provisional; P6 personalises them, P4 sanity-checks them first, and
nothing here has been validated against a human being.

---

## P3-10 — Streaks and the dashboard: the mechanics P3-3 deferred

*2026-09-20*

P3-3 added this scope and explicitly deferred its detail: *"Not yet decided,
deferred to detailed design … streak mechanics (whether a missed day breaks it
or a freeze exists), and dashboard layout."* This entry is that design.

### The governing documents say nothing at all

Searched for **streak**, **dashboard**, **engagement**, **Duolingo** and
**Health Connect** across `CONTRACTS.md`, `FINAL_ARCHITECTURE.md`,
`ZERO_DATASET_BUILD_PLAN.md`, `CLAUDE.md` and `REPOSITORY_STRUCTURE.md`.

**Not one match, for any of the five terms, in any of the five documents.**

The only hits for **persistence** are two lines of `CLAUDE.md`:

> § 7, the post-rep path: `RepRecord` → feature extraction → deviation rules →
> technique rules → DTW/reference comparison → ML → explanation → **persistence**.

> § 15: Export formats: JSON, CSV. **Persistence is local only.**

So unlike every prior batch, there is no document text to build from.
**DECISIONS.md P3-3 is the entire specification**, and it is quoted here in the
form the project owner agreed to:

> 1. **A dashboard** — session history, trends over time (ROM, deviation, and
>    the other Layer 1/2 outputs already produced per rep), built entirely from
>    `RepRecord`/`HoldRecord` history the engine already generates. No new data
>    collection.
> 2. **Streaks** — Duolingo-style consecutive-day tracking. Pure local state: a
>    counter and a calendar view over session timestamps. No new architecture.

Everything below is derived from those two paragraphs. Where they are silent the
smallest reasonable rule was chosen and recorded, rather than importing what a
fitness app usually has.

### What persistence existed before this batch: none

Searched the whole repository for `Repository`, `Dao`, `@Entity`, `Room`,
`SQLDelight`, `sqlite` and `.db`. **Zero matches outside build output.** There is
no storage layer, no repository interface, and no dependency on any database.

Worse for this batch's purposes, **most of the data model did not exist
either**. `CLAUDE.md` §15 and `FINAL_ARCHITECTURE.md` §8 both specify:

> Hierarchy: `SessionRecord` → `SetRecord` → `RepRecord` / `HoldRecord`, plus
> `CalibrationBaseline` and `AnalysisResult`.

Of those six types, **only `RepRecord` and `HoldRecord` had been built** — in
P3, as outputs of the rep tracker and hold analyser. `SessionRecord`,
`SetRecord`, `CalibrationBaseline` and `AnalysisResult` were named in two
documents across five batches of profile work and never written. A dashboard
"over session timestamps" cannot be built without a session, so that gap is this
batch's to close, and it is closed.

**The storage technology is already decided, so nothing was chosen here.**
`FINAL_ARCHITECTURE.md` §8:

> Local storage only — **Room on Android, IndexedDB on web, behind one shared
> repository interface.** JSON and CSV export. **No backend, no auth, no cloud,
> no sync.**

and `REPOSITORY_STRUCTURE.md` confirms the dependency is deliberately deferred:

> MediaPipe, CameraX, Compose, **Room** and everything else arrive in the phase
> that needs them, each with its licence checked at that point.

So there is no gap in the stack decision and no §17 check owed *by this batch* —
Room owes one when it is written, which is `:app` work.

### What was built, and what deliberately was not

**Built, in `:core:domain`:** `SessionRecord`, the sealed `SetRecord` with
`RepetitionSetRecord` and `HoldSetRecord`, and `LocalDay`. The set type is sealed
per analysis mode for the same reason `HoldRecord` carries no repetition count
(P3): a set that *could* hold both repetitions and holds is a set something will
eventually put both in.

**Built, in `:core:analysis/history`:** `Streaks` and `Dashboard`, both pure
functions over a supplied history. Classified post-rep in the source guard, so a
new file in that package fails the build until someone says which path it is on.

**Not built: the shared repository interface.** This is a judgement call and is
recorded as one. `FINAL_ARCHITECTURE` specifies it, and it would be fifteen
lines — but with no implementation and no caller it would be an abstraction that
nothing reads, which is precisely the failure this project has now found twice:
`requiredView` and `posture` declared since P1 and read by nothing until P3-8,
and the near-miss of adding `AnalysedSides` to isometric profiles in P3-9 where
nothing could have consumed it. The interface belongs in the change that
implements it against Room, where it can be guard-tested. **Storage therefore
remains unbuilt, and this entry says so plainly rather than implying the
dashboard has something to read from.**

### The day model, and why there is no date library

`FINAL_ARCHITECTURE.md` §3 lists what makes this core good multiplatform
material:

> | No date/time, no locale, no file system | Removes the second largest |
>
> If your core needed HTTP, a database driver, or structured concurrency, I
> would tell you not to do this. It needs none of them.

A timezone database is that kind of dependency, and none is needed. The platform
records the UTC offset in force when a session starts; `LocalDay.of` is integer
arithmetic over it. `Streaks.summarise` takes the current day as a parameter
rather than reading a clock, which follows from the same constraint and makes
every test deterministic as a by-product.

**The offset is captured, not looked up, and this is a correctness decision.**
The day a session belongs to is fixed when the session happens. Bucketing a past
session with the device's *current* offset means a user who flies from Auckland
to Los Angeles silently rewrites their own history — a Tuesday evening session
becomes Monday. `moving_timezone_does_not_move_the_day_a_past_session_belongs_to`
pins it.

**The day number is a floor, not a truncation.** The two agree for every
timestamp after 1970 and disagree for every one before it, so a truncation
passes every test written against realistic data and then folds two days into one
the first time a device clock is wrong. Pinned separately, because it is the kind
of bug that cannot be found by inspection.

### The four decisions P3-3 left open

**1. What counts as a streak day.** At least one repetition or one hold that
**actually completed** — `RepValidity.VALID` or `HoldValidity.VALID` — started
on that local day. Opening the app does not count; a set started and abandoned
does not count.

*Reasoning.* This agrees with `BilateralRepTracker`, whose repetition count also
only advances on `VALID`, so the streak and the rep count can never tell
different stories about the same session. No minimum beyond one, and no
distinction by exercise or tier: nothing in any document asks for either, and a
threshold nobody specified is a threshold invented here.

**2. The day boundary.** The device-local calendar day, from the offset captured
at session start. Two sessions on one day count once; a second either side of
local midnight is two days. Both pinned.

**3. Grace and freezes: not in scope, and deliberately so.** P3-3 named this as
undecided and "Duolingo-style" implies it, so the omission is recorded rather
than left to look like an oversight.

*Reasoning.* A freeze needs state of its own — how many are held, how many spent,
how they are earned — which is exactly the "new architecture" P3-3 said this
feature would not add. It is a motivation mechanic with no correctness content,
and adding an unrequested feature is what CLAUDE.md §20 forbids. Because the
streak is **computed from history rather than stored**, adding one later is a
change to one function and not a migration. That is the main reason the streak is
derived rather than kept as a counter.

**4. Today's silence is not yet a break.** A streak ending yesterday is still
running this morning; it breaks once a full day has passed with no work in it.

*Reasoning.* Someone who has not trained yet today has not lost anything, and
showing them a zero would be both wrong and the most demotivating thing the
feature could do. This is the precise content of "a missed day breaks it", and
both halves are pinned —
`not_having_trained_yet_today_does_not_break_yesterdays_streak` and
`a_full_day_of_silence_does_break_the_streak`.

### What the dashboard shows

Derived from P3-3's "session history, trends over time … built entirely from
`RepRecord`/`HoldRecord` history":

- **Per exercise** — sets performed, repetitions completed, total hold time,
  mean and best range of motion, and the day it was last performed. Ordered most
  recently performed first.
- **Overall** — session count, total completed repetitions, total hold time,
  first and last day, and the streak.

The summary carries the streak rather than leaving the screen to recompute it,
so the two cannot disagree about one history.

**Every measured quantity stays a `BilateralSignal`.** Aggregation is the easiest
place in the codebase to lose the two sides, because "average range of motion"
reads like one number — and the obvious implementation is the prototype's
`(left + right) / 2f` (P2-2) reappearing one layer up from where it was found.
Left and right are accumulated in fields that never meet. A side that was never
measured stays `NaN` rather than becoming zero, because zero is a measurement and
none was made; and an alternating exercise's idle side is skipped rather than
propagated, or every bicep-curl summary would read `NaN` on both arms.

**Deviation is absent, and that is blocked rather than dropped.** P3-3 names it
among the trends, but deviation is a Level 2 claim measured against a
`CalibrationBaseline` — one of the four types §15 specifies and nothing has
built. P6 builds it. Reporting "below your normal" without a baseline would be
inventing the comparison, so the dashboard reports what the records measure on
their own until there is something honest to compare them against.

**Health Connect is untouched**, per the batch scope and P3-3's own note that it
"is a real dependency addition and needs the full §17 check when it is built".

### Three test expectations that were wrong, and what that says

The first run failed three tests, and in all three cases **the test was wrong and
the implementation was right**:

- two "same day" sessions were built at noon **plus** nine and eighteen hours,
  which is 21:00 and 06:00 the next morning;
- 10:00 UTC was asserted to be the next day at UTC+13, which is 23:00 — the same
  day.

That is the day arithmetic being subtle enough to catch the person writing the
tests for it, on the first attempt, in the most obvious direction. It is recorded
because it is the best available evidence that this batch needed tests at all: a
streak is exactly the kind of feature that looks correct in a demo and is wrong
by one day for someone in a timezone the author never tried.

### Mutation results

Every new guard was verified able to fail. Eight probes, each reverted:

| Probe | Caught by |
|---|---|
| `LocalDay` truncates instead of flooring | the floor test, alone |
| `LocalDay` ignores the captured offset | 3 tests, incl. both midnight cases |
| Aborted repetitions count as work | the streak rule and the dashboard count |
| Streak anchors only on today | `not_having_trained_yet_today…` |
| Qualifying days counted per session | `two_sessions_on_the_same_day…` |
| Range of motion pooled across sides | 3 tests, reporting 80.0 where 105.0 was due |
| An unmeasured side reported as zero | `a_side_that_was_never_measured…` |
| An idle side's `NaN` folded into the mean | `an_alternating_exercise_keeps_each_arms_average_separate` |

**Scope of this entry.** It records a product design, not a measurement. Nothing
here was validated against a dataset or a person, because there is nothing in it
to validate against one: a streak rule is a definition, and the only thing that
can be wrong with it is its arithmetic, which is what the tests cover. Nothing on
the real-time path changed.

---

## P3-11 — Health Connect: the §17 check, and what it found

*2026-09-20*

P3-3 added Health Connect to scope and set the bar for building it:

> (3) is a real dependency addition and needs the full §17 check when it is
> built: license, API level requirements, whether it fits the KMP module split
> (Health Connect is Android-only, so this is `:app`-only like
> CameraX/MediaPipe), and maintenance status.

That check is below, and **it found two blocking constraints the project did not
know it had.** Both are measured against the resolved artifacts rather than
taken from documentation, per CLAUDE.md §23.

### The §17 check

CLAUDE.md §17 requires checking "why it's needed, whether an existing capability
already covers it, its licence, platform compatibility, bundle/runtime cost,
offline compatibility, and maintenance status", and separately that "for every
reused component, determine code licence, model/weights licence, and
dataset/training-data restrictions separately".

**Purpose.** Kinesis measures bilateral asymmetry, tempo, range of motion and
deviation from a personal baseline. It measures nothing about the day those
repetitions happened in. Health Connect supplies that: whether the set was
performed on a sedentary day or an active one, and whether the user's heart rate
was elevated. P3-3's framing is that this "contextualises those
technique-specific measurements within the user's broader daily activity rather
than duplicating what Health Connect already reports", and the sharpened version
is the test for what to read at all: **if Health Connect can report it and
Kinesis already measures it, Kinesis's own number wins and the Health Connect one
is not read.** That is what excludes exercise sessions, below.

**No existing capability covers it.** Nothing in the codebase reads platform
health data, and nothing could — the shared core has no Android APIs by design.

**Licence — checked per artifact, which is what §17 asks for, and it mattered.**

| Artifact | Licence | Source |
|---|---|---|
| `androidx.health.connect:connect-client:1.0.0-alpha11` | Apache 2.0 | its POM |
| `com.google.guava:guava:31.1-android` | Apache 2.0 | `guava-parent` POM |
| `com.google.guava:failureaccess`, `listenablefuture` | Apache 2.0 | as Guava |
| `org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3` | Apache 2.0 | its POM |
| *(1.1.0 only)* `connect-client-external-protobuf` | **BSD-3-Clause** | its POM |

All permissive and compatible. The last row is the reason §17 says to check each
component separately: the stable line pulls a repackaged protobuf under
**BSD-3-Clause**, not Apache 2.0. It is compatible and needs only attribution and
the no-endorsement clause, but it would have been missed by checking the headline
artifact alone. Noted now because it becomes live if the version ceiling below is
lifted.

No models, no weights, no training data — this is an API client, so those two
limbs of §17 are empty rather than unexamined.

**Platform.** Android-only, `:app`-only, exactly as P3-3 predicted. `:core:domain`
and `:core:analysis` have no reference to it and could not compile against one —
they are Kotlin Multiplatform modules with JS and JVM targets. The one core type
this code borrows is `LocalDay`, so a day means the same here as it does to the
streak rule (P3-10).

**Bundle — measured, not estimated.** Release APK built immediately before and
after the dependency was declared, same toolchain, same sources otherwise:

| | Before | After | Delta |
|---|---|---|---|
| `app-release-unsigned.apk` | 6,528,418 B | 7,774,206 B | **+1,245,788 B (+19.1%)** |
| `app-debug.apk` | 8,821,716 B | 10,420,190 B | +1,598,474 B |

Most of that is **Guava 31.1-android**, which the pinned version drags in; the
`connect-client` AAR itself is 1,656,115 B. This is a real cost for a
supplementary panel and is recorded as such rather than waved through.

**Offline — verified, not assumed.** FINAL_ARCHITECTURE §6's "No backend, no
auth, no cloud" is a hard line, and P3-3 asked for this to be *shown* not
assumed. The merged release manifest after adding the dependency declares
exactly four permissions:

```
android.permission.health.READ_ACTIVE_CALORIES_BURNED
android.permission.health.READ_HEART_RATE
android.permission.health.READ_STEPS
com.kinesis.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION   (AndroidX internal)
```

**No `INTERNET`. No `ACCESS_NETWORK_STATE`. No `ACCESS_WIFI_STATE`.** Adding
Health Connect introduced zero network capability to Kinesis, which is as direct
a demonstration as this line admits. Health Connect's own store is on-device and
encrypted, and the provider does not request internet access either. Nothing
here adds an account, a server or a sync path.

**Maintenance.** `androidx.health.connect` is maintained by Google as part of
AndroidX and is actively released — a stable 1.1.0 exists. **But Kinesis cannot
use it**, for the reason immediately below, and the version it can use is an
alpha from the 1.0.0 line. That is the weakest point in this check and it is
stated plainly rather than buried.

### Finding 1: the stable version is blocked by this project's toolchain

Declaring `connect-client:1.1.0` fails the build, with the reason measured
rather than predicted:

> Dependency 'androidx.health.connect:connect-client:1.1.0' requires libraries
> and applications that depend on it to compile against version 36 or later of
> the Android APIs … Dependency 'androidx.health.connect:connect-client:1.1.0'
> requires Android Gradle plugin **8.9.1 or higher**.

Kinesis is on AGP **8.7.3** and `compileSdk` **35**. Lifting that means an AGP
upgrade plus an SDK platform download — the installed platforms are `android-35`
and `android-37`, with **no `android-36`** — and AGP 8.7.3 is already entangled
with the JDK 25 lint failure recorded in P1-1. That is a build-infrastructure
change with its own risk surface, not a feature-batch change.

**Decision: pin `1.0.0-alpha11`**, the newest version that resolves against this
toolchain (`1.0.0` final does not exist; the line goes alpha11 → 1.1.0).

**This is the weakest dependency decision in the project and is recorded as
one.** Mitigating it: the dependency is read-only, supplementary, reached only
through an interface, and every failure mode already degrades to "no context",
which is a tested path. If the alpha misbehaves, the app shows an empty panel —
it cannot take a measurement or a screen down with it. Upgrading to stable is
also *cheaper* on every axis once the toolchain allows it: the 1.1.0 AAR is
smaller (1,315,867 B against 1,656,115 B) and drops the Guava dependency
entirely. **Recommendation: do the AGP and `compileSdk` upgrade as its own
task, then move to 1.1.0.**

### Finding 2: the library needs minSdk 26 and Kinesis supports 24

Measured from the manifest merger:

> `uses-sdk:minSdkVersion 24 cannot be smaller than version 26 declared in
> library [androidx.health.connect:connect-client:1.0.0-alpha11]`

Three options were offered; the choice is deliberate.

- *Raise the app's minSdk to 26.* **Rejected.** That drops Android 7.0 and 7.1
  devices from the whole product to gain an optional panel — raising the floor
  for every user to add supplementary context is backwards, and directly against
  P3-3's framing.
- *Find a library with minSdk 24.* None exists.
- **`tools:overrideLibrary="androidx.health.connect.client"`.** Chosen.

The merger warns this "may lead to runtime failures", and that warning is
answered rather than ignored: every entry point in
`HealthConnectActivityContextSource` checks `Build.VERSION.SDK_INT` before
touching the library, the methods that do touch it are `@RequiresApi(26)`, and
below API 26 the read path returns `PLATFORM_TOO_OLD` without loading a single
Health Connect class. The app installs and behaves identically on API 24 and 25;
it simply has no context to show. **This is exactly absence case 3 from the
brief, and it is a real case rather than a hypothetical one.**

### The pinned data types

P3-3 named "steps, active calories, heart rate — exact set to be pinned when
this is built". Those three are the set. Each is justified against the
contextualises-rather-than-duplicates test.

| Metric | Aggregate | Why it earns a place |
|---|---|---|
| **Steps** | `StepsRecord.COUNT_TOTAL` | Whole-day ambulatory volume. Kinesis measures nothing remotely like it, and it is the plainest answer to "was this a sedentary day". |
| **Active calories** | `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL` | Whole-day energy expenditure, covering activity Kinesis never sees — cycling, walking, sport. Partly overlaps steps, which is why it is the weakest of the three. |
| **Mean heart rate** | `HeartRateRecord.BPM_AVG` | The strongest contextualiser. An elevated resting heart rate is the most plausible external explanation for a session below a personal baseline, and it is a fact about the body that no camera can see. |

**What is deliberately excluded, and the exclusion that matters most.**

- **Exercise sessions.** Health Connect exposes `ExerciseSessionRecord`, and
  reading it would be the single most tempting addition — and it is exactly what
  P3-3 forbids. Another app's record of "strength training, 40 minutes" *duplicates*
  what Kinesis measures, and worse, it would put a second, unverified account of
  the user's training next to Kinesis's own. If they disagreed there would be no
  principled way to resolve it. **Kinesis's own measurements are the only account
  of Kinesis's own domain.**
- **Sleep, weight, distance, VO2 max, blood oxygen.** Each is plausibly
  interesting and none was asked for. CLAUDE.md §20 is smallest coherent change,
  and the brief for this batch put it directly: do not default to grabbing
  everything Health Connect exposes because it is available. Every extra type is
  another permission prompt, and a permission the user refuses is worse than one
  never requested.
- **Any write permission.** None is declared in the manifest and none is
  requested. P3-3's "read-only" is therefore true by construction.

### Absence is the first-class case

Three ways this can be missing, per the brief, and all of them are values rather
than exceptions:

```kotlin
sealed interface ActivityContextResult {
    data class Available(val context: DailyActivityContext)
    data class Unavailable(val reason: ContextUnavailable)
}
enum class ContextUnavailable {
    PLATFORM_TOO_OLD, NOT_INSTALLED, PROVIDER_UPDATE_REQUIRED,
    PERMISSIONS_DENIED, READ_FAILED,
}
```

Five reasons rather than three, because the brief's three do not cover a provider
that is installed but too old to serve reads, or a read that fails for a reason
the provider's documentation does not list. `ActivityContextResult` is
deliberately not `DailyActivityContext?`: a null would say "nothing here" and lose
the reason, and the reason is the whole difference between "install Health
Connect to see this", "you denied this permission" and "your phone is too old for
this feature".

**Permissions are granted per metric, so absence is modelled per metric too.**
`DailyMetric` is `Measured` / `NotPermitted` / `NoData`. A permission the user
withheld and a day with nothing recorded are different sentences, and collapsing
both to null would make them one.

**Nothing can fail loudly.** A supplementary feature that can throw is not
supplementary. Every expected condition is a result, and the implementation
catches `Throwable` around each provider call because a health provider on an
arbitrary device fails in ways its documentation does not enumerate.

### What "tested" means for this batch

The pattern from every prior batch — resolve a question against MM-Fit, then
mutate the guard to prove it fails — does not transfer. There is no dataset and
no threshold here. The question is not "what is the right number" but "does the
app survive every way this can be missing", so that is what is tested.

**Tested, on the JVM, with no device or emulator** (`:app:testDebugUnitTest`,
10 tests against a fake source):

- each of the five absence reasons is a value the caller must handle;
- a partial permission grant yields the granted metric and marks the rest
  `NotPermitted`;
- a permitted metric over an empty day is `NoData`, distinct from refused;
- **Kinesis's own dashboard summary is identical across every absence case
  including the fully-available one** — the property that makes "supplementary"
  true rather than aspirational;
- computing a Kinesis summary never reads the context source at all
  (`readCount == 0`).

That last pair matters more than the rest. It is a structural fact today, since
`:core:analysis` cannot reference `:app`; the tests are there so it fails loudly
if a future batch wires the two together.

**Not tested here, and flagged for P4 device work rather than faked.** These
need a real device and a real provider, and a mock would only assert this code's
own assumptions back at it:

- that `sdkStatus` reports what this code expects on Android 13 with the APK
  provider and Android 14+ with the framework module;
- that the permission dialog appears and a partial grant produces the permission
  set assumed here;
- that a real `aggregate` returns the units this code converts
  (`Energy.inKilocalories`, `Long` BPM);
- **that API 24 and 25 devices install and run with the minSdk override** —
  the one place Finding 2's decision could bite.

`:app` already had unit-test infrastructure, contrary to what this batch
expected to find: JUnit 4, one test (`MirroringRegressionTest`, from P1). It now
has 15.

### The boundary with Batch 8

**Nothing is wired into the dashboard UI, and there is no UI.** This batch built
the read path only: the types, the interface, the Health Connect implementation,
and a fake. That is the same boundary Batch 6 drew when it built the dashboard's
aggregation and deliberately left persistence unbuilt — build the part that can
be designed and tested now, and leave the part that needs a consumer to the batch
that has one.

Specifically **not** built here, and belonging to Batch 8:

- any Compose screen, panel or view model;
- the permission request flow — `ActivityContextPermissions.ALL` names what to
  request, but nothing launches a request contract, because the thing that should
  launch it is a screen;
- any pairing of `DashboardSummary` with `ActivityContextResult` in production
  code. The test composes both to prove independence; no production type does,
  because a combiner nothing renders would be an abstraction read by nothing —
  the failure P3-8 and P3-9 each found once.

**Scope of this entry.** It records a dependency check and a read path. It makes
no claim about Health Connect's accuracy or about any user's data, and nothing in
it has run against a real provider. Nothing on the real-time path changed, and
no analysis decision anywhere depends on anything read here.

---

## P3-12 — The app UI, local storage, and a theme that must not hide a fault

*2026-09-22*

The first batch to touch anything visual, and the batch that gives the dashboard
something real to read. It also ran the §17 check on a storage library, which
found a second toolchain constraint of the kind P3-11 found for Health Connect.

### Scope, and what was split out

The batch as briefed contained six pieces. Two of them - the live camera feed and
the skeleton overlay drawn over it - are gated on **CameraX and MediaPipe Pose,
neither of which is declared anywhere in this project**. CLAUDE.md §1 mandates
both, P2-3 deferred them pending §17 checks, and those checks have never been
run; MediaPipe additionally ships a model asset whose **weights licence §17
requires checking separately** from its code licence. That work is also the one
part of this batch that cannot be verified without a device, and P4 - the device
reality check - still has not happened.

Raised with the project owner rather than guessed at, per the batch instruction
to err on the side of asking. **Decision: the camera work moves to its own
batch**, alongside the two dependency checks and P4. What is built here is
everything that can be built and tested now, including the live screen's
readout and the boundary that will feed it.

### The visual theme, and the risk it carries

**No document in the project says anything about colour, typography or theme.**
Searching `CONTRACTS.md`, `FINAL_ARCHITECTURE.md`, `ZERO_DATASET_BUILD_PLAN.md`,
`CLAUDE.md` and `REPOSITORY_STRUCTURE.md` for colour, theme, palette or
typography returns nothing. The green-on-black requirement is a new direction
from the project owner and is recorded here as one.

Phosphor green `#39FF14` on near-black `#060A06`, monospace throughout - the
screen is mostly numbers that change while the user watches them, and a
proportional face makes a rep count shift sideways going from 9 to 10.

**The requirement carries a specific risk, which the owner named: if everything
is the same green, the user can no longer tell what needs their attention.** The
convention that answers it:

| State | Colour | Stroke | Motion | What measures it |
|---|---|---|---|---|
| Tracked | `#39FF14` phosphor | 4dp | — | visibility ≥ `reliable` |
| Degraded | `#208F0B` dim | 3dp | — | `FrameValidity.DEGRADED` |
| Not visible | `#5C6B5C` grey-green | 2dp | — | below `marginal`, angle is `NaN` |
| **Attention** | `#FFB000` amber | **6dp** | **pulse** | `RepValidity.ABORTED`/`INCOMPLETE` |

**Amber, not red.** Red on green is the worst pairing available: roughly eight
percent of men have a red-green deficiency, and for them a red joint on a green
skeleton is a green joint on a green skeleton.

**Three channels, because colour alone measurably is not enough.** Phosphor green
and amber are both bright - their luminance contrast is about **1.35:1**, far
below anything WCAG would call a distinction. In greyscale, on a dim screen at
arm's length, or through the motion blur of a live repetition, hue is the only
thing separating them and hue is the first thing to go. So the attention state
also carries a **fifty percent wider stroke** and **the only pulse in the
overlay**. The stroke width is load-bearing, not decoration, and
`SkeletonPaletteLegibilityTest` fails if it is ever flattened "for consistency".

**Only one state pulses**, deliberately: motion is the loudest cue available and
stops meaning anything if several things use it.

**There is no "correct form" state, because nothing can produce one.** Layer 2
technique rules are P8 and do not exist; `TechniqueFault` is a type nothing
constructs. Every state above maps to something the engine measures **today**.
Inventing a red "fault" joint driven by a stub would be a confident indicator
with no measurement behind it, which is what CLAUDE.md §22 and §23 exist to
prevent. `ATTENTION` is the slot P8's findings occupy when they arrive; until
then it is reached from repetition validity, which is real.

**The guard caught a fault in the first palette written for it.** The degraded
green came in at 3.20:1 against the backdrop - close to invisible on a dim
screen. The colour was changed, not the threshold.

One state is held to a lower contrast bar on purpose. "Not visible" must show
*that* the camera lost a joint while staying quiet enough that a half-occluded
skeleton does not read as a working one, so it is bounded on both sides: at
least 3:1 against the backdrop, and less than half the luminance of a tracked
joint. That is a different requirement for a different role, not a relaxed one.

### §17 check: the storage library

FINAL_ARCHITECTURE §8 already decided this - *"Local storage only - Room on
Android, IndexedDB on web, behind one shared repository interface"* - so no
library was chosen here. The check was run anyway, as instructed, and it was not
a formality.

**Purpose.** Sessions must survive the process. Nothing in the project persists
anything: P3-10 built the dashboard's aggregation and deliberately left storage
unbuilt because no screen consumed it.

**Existing capability.** None. No repository, no DAO, no file store.

**Licence, per artifact.** `room-runtime`, `room-common`, `sqlite` and
`sqlite-framework` are all **Apache 2.0**, read from their POMs. No models, no
weights, no dataset restrictions.

**Platform.** Android-only and `:app`-only. Nothing in `:core:domain` or
`:core:analysis` references it.

**Bundle — measured.** Release APK **7,774,206 → 8,028,443 bytes, +254,237
(+3.3%)**, built before and after on the same toolchain.

**Offline — verified, and this line was first written wrong.** It originally
claimed the merged release manifest was "byte-identical to Batch 7's". That was
an overclaim: only the `uses-permission` lines had been compared, not the
manifest. Checked properly afterwards by building the Batch 7 commit in a
worktree and diffing the two merged manifests, **they are not identical** — Room
contributes one node:

```xml
<service
    android:name="androidx.room.MultiInstanceInvalidationService"
    android:directBootAware="true"
    android:exported="false" />
```

What the line was actually asserting does hold, and is what matters here: the
permission set is unchanged — the three Health Connect reads and one AndroidX
internal receiver permission — so **Room adds no permission at all and no
`INTERNET`**. The service it adds is `exported="false"`, reachable only in-process,
and exists to invalidate query caches across processes. It adds no network
capability and no external surface.

Corrected rather than quietly edited, because "byte-identical" is the kind of
claim that reads as having been checked when it had not been. CLAUDE.md §23 is
measure, then document, then claim; this was documented from a partial
measurement.

**Maintenance.** AndroidX, actively released.

**And the finding: Room 2.6.1 does not build on this toolchain.** Declaring it
fails in KSP with

> `e: [ksp] java.lang.IllegalStateException: unexpected jvm signature V`

Room 2.6.1 predates Kotlin 2.1 and its processor does not survive KSP2. **Room
2.7.1 builds cleanly** with KSP `2.1.21-2.0.1`, and is what is pinned. Unlike
P3-11's Health Connect constraint this one has no cost - 2.7.1 is the current
line and needed no AGP or `compileSdk` change - but it is recorded because
"Room 2.6.1, the version every tutorial uses" would have looked like the safe
choice and does not compile here.

### Where the repository interface lives, and why not where the document says

FINAL_ARCHITECTURE says "one **shared** repository interface", meaning
`commonMain`. It is in `:app` instead.

That was written when the Windows PWA was in scope; P0-2 cut the React
application, and the owner's direction for this batch was explicit - Android
only, do not over-engineer for multiplatform. It also keeps a promise the core
makes about itself: FINAL_ARCHITECTURE §3 lists *"No I/O, no networking, no
threading in the core"* and *"No coroutines needed in the analysis path"* among
the properties that make Kotlin/JS viable, and a suspending repository interface
in `commonMain` would spend the first of those for a platform that no longer
exists. Recorded as a divergence rather than left to be noticed.

### Two rules the schema exists to keep

**Left and right never share a column.** CLAUDE.md §3 says bilateral signals stay
separate "everywhere", and storage is part of everywhere. A single
`rangeOfMotion` column would force a reducer at write time - the prototype's
`(left + right) / 2f` (P2-2) made *durable*, with nothing left to recover from.
Every bilateral measurement is two nullable columns.

**`NaN` survives the round trip, and this one is not obvious.** `NaN` is the
sentinel that means "not measured" throughout Kinesis: the idle arm of an
alternating repetition, the invisible arm of the near-side-only row (P3-8).
**SQLite has no way to store a `NaN` in a REAL column.** Stored naively, every
bicep curl in the database would reload claiming a real measurement of **zero**
on the idle arm - not a display bug but a false measurement written to disk,
poisoning every average computed afterwards. So `NaN` is written as `NULL` and
read back as `NaN`, and the resampled arrays go through a blob rather than REAL
columns precisely because IEEE-754 survives a byte copy.

### The per-frame boundary

CLAUDE.md §6: *"MUST NOT: drive Compose recomposition from pose frames."*
FINAL_ARCHITECTURE §8 puts a number on it: *"Compose state updates only on
rep/phase change - a few times per second."*

`LiveAnalysisBridge` is that boundary. It takes every frame and publishes a
`LiveUiState` only when something a human could perceive has changed. The saving
is not the call - it is that no object is allocated and no Compose tree is
diffed for a frame that changed nothing.

Measured by test: **900 unchanged frames produce 0 recompositions**; a ten-
repetition set is **610 frames and fewer than 60**. The mutation that publishes
unconditionally gives 611 for 610, and fails four tests.

This breaks quietly if it breaks - publishing per frame does not crash, it just
starves the analysis path while Compose diffs thirty times a second, and the
symptom is a dropped frame rate that looks like a pose-estimation problem and is
not one.

### What was built, and what the screens may say

- **Exercise selection** reads `ExerciseLibrary.all` - all 17 entries, both
  variants included - rather than any hardcoded list.
- **Dashboard** reads stored sessions through `Dashboard.summarise`. No fake
  data remains.
- **Live screen** renders the real `LiveUiState`; the camera and overlay arrive
  next batch beneath it, so what changes is what produces the state.
- **Health Connect permission flow**, which P3-11 left unbuilt because the thing
  that should launch a request is a screen. Denial surfaces through P3-11's
  existing `ContextAvailability` / `DailyMetric` types - **not a parallel path** -
  so a partial grant reads as `NotPermitted` per metric, which is already
  distinct from `NoData`. Nothing retries or nags: a refusal is an answer.

**No screen composes a sentence about movement.** CONTRACTS.md §2 and
FINAL_ARCHITECTURE §6 both require that findings carry their own text from
`commonMain`, *"the UI renders what it is given and composes no sentences of its
own"*. The labels here name fields; they do not judge movement. **Deviation is
absent from the dashboard**, because it is measured against a
`CalibrationBaseline` that P6 has not built, and showing "below your normal"
without one would invent the comparison.

### What "tested" means here, and what it cannot

47 JVM tests in `:app`, no device: the palette's legibility arithmetic, the
recomposition budget, the storage round trip, and the recorder-to-dashboard
loop. Six mutations, every new guard verified able to fail.

**Not tested, and flagged rather than faked.** Room's own SQL - the mapping
either side of it is covered, the driver is not - needs instrumentation tests;
the permission dialog and a real partial grant need a device; and nothing here
has been looked at on a screen, so the palette is verified arithmetically and
not perceptually. A contrast ratio is a good proxy for legibility and is not the
same thing as having looked.

### Boundary with the next batch

Not built here: CameraX, MediaPipe, the camera preview, the skeleton overlay
drawn over it, and P4's device validation. `OverlayState` already defines how
that overlay must colour itself, and `LiveAnalysisBridge` already defines what
it may tell Compose, so the next batch adds a `SurfaceView` and a pose callback
beneath an interface that exists.

**Scope of this entry.** It records a product and storage design. Nothing here
measures movement or makes a claim about it; the real-time path is unchanged and
no analysis decision depends on anything added.

---

## P3-13 — CameraX and MediaPipe: two §17 checks, and a hard line nearly crossed

*2026-09-22*

Phase A of the batch that finally adds the two dependencies CLAUDE.md §1 has
mandated since P1 and that P2-3 deferred pending these checks. One of them found
a dependency quietly crossing FINAL_ARCHITECTURE §6's "no cloud" line.

### §17 check: CameraX

**Purpose.** Frames. Nothing in the project captures any; the analysis engine has
been fed synthetic landmarks for nine batches.

**Existing capability.** None. `android.hardware.camera2` directly is the
alternative, and it is substantially more code for lifecycle, rotation and
back-pressure handling that CameraX already solves.

**Licence, per artifact.** `camera-core`, `camera-camera2`, `camera-lifecycle`
and `camera-view` are all **Apache 2.0**, read from their POMs.

**Platform.** Android-only, `:app`-only. `minSdk 21`, below Kinesis's 24, so no
repeat of P3-11's Health Connect conflict.

**Bundle.** Small relative to what follows - the CameraX contribution is the
`libimage_processing_util_jni.so` entries, tens of kilobytes per ABI.

**Offline.** Declares no network permission of its own.

**Maintenance.** AndroidX, actively released. 1.4.1 pinned.

### §17 check: MediaPipe Pose — code and model checked separately

Batch 8's research flagged that the model asset's terms are a distinct question
from the library's. They are, and the reason is sharper than expected.

**Purpose.** On-device pose estimation. CLAUDE.md §1 names MediaPipe Pose
specifically, and the 33-landmark model is what `LandmarkId` has encoded since
P1.

**Licence — code.** `com.google.mediapipe:tasks-vision:0.10.21` is **Apache
2.0**, read from its POM, as is its `tasks-core` transitive.
`com.google.protobuf:protobuf-javalite` comes with it under protobuf's own
BSD-style terms - permissive, attribution only.

**Licence — model, and this is the finding.** **The Maven artifact ships no
model.** Inspecting the AAR finds no `.task` and no `.tflite`: the library is
the runtime only. The pose landmark model is a separate file obtained from
Google's model page and placed in `assets/` by hand.

So the model's terms are not covered by the POM licence that was just verified,
and **they could not be verified from a primary source in this environment**.
The framework documentation describes MediaPipe as Apache 2.0, and that is a
statement about the framework, not a model card for the specific `.task` file.

**What this means in practice, and why it is not blocking.** No model is
committed to this repository and none is distributed by it. The app builds,
installs and runs without one - `PoseModelState` makes its absence a named
result rather than a crash, the same discipline P3-11 applied to Health Connect.
So nothing is currently shipped under unverified terms. **Before the model is
distributed with a build, its model card must be read and its terms recorded
here.** That is an action for the project owner and it is listed in Phase B.

**Closed, 2026-09-23 — read and recorded in P3-17.** The owner supplied the
model card (`docs/third_party/Model_Card_BlazePose_GHUM_3D.pdf`, dated
2021-04-16). It was read in full: the model is licensed under the **Apache
License, Version 2.0**. What that permits and requires, and the card's own
limits, are in P3-17. The model file and the card are now committed, and the
app carries an in-app licences screen with the licence text.

**Bundle — measured, and it is large.** Release APK **8,028,443 → 53,419,223
bytes, +45,390,780 (+565%)**. Essentially all of it is one native library
shipped per ABI:

| Entry | Compressed |
|---|---|
| `lib/x86/libmediapipe_tasks_vision_jni.so` | 21,282,336 |
| `lib/arm64-v8a/libmediapipe_tasks_vision_jni.so` | 13,038,992 |
| `lib/armeabi-v7a/libmediapipe_tasks_vision_jni.so` | 7,984,040 |

**Not acted on, and flagged.** An `abiFilters` restriction to `arm64-v8a` would
drop roughly 29 MB, and the Nothing Phone 2a is arm64. It is not applied here
because nothing in the project states a size budget and silently dropping
architectures breaks x86 emulator testing - a change that surprises someone
later. Recorded as available and cheap if the owner wants it.

**Maintenance.** Google, actively released.

### The finding that matters: MediaPipe added INTERNET

After declaring the dependencies, the merged release manifest contained:

```
android.permission.CAMERA                      (ours, deliberate)
android.permission.health.READ_*               (three, from P3-11)
android.permission.ACCESS_NETWORK_STATE        <- new
android.permission.INTERNET                    <- new
```

FINAL_ARCHITECTURE §6 is *"No backend, no auth, no cloud, no sync."* CLAUDE.md
§16 is *"MUST: remain offline-first"* and *"MUST NOT: use cloud inference, video
upload ... or unnecessary network dependencies."* P3-11 and P3-12 both verified
that Kinesis granted no network capability at all. This crossed that line.

**The manifest-merger blame report names the source exactly:**

> `uses-permission#android.permission.INTERNET`
> `ADDED from [com.google.android.datatransport:transport-backend-cct:3.1.0]`

Not MediaPipe itself - **a telemetry upload backend three levels down its
dependency tree**. Nothing in Kinesis asked for it, nothing in the source
mentions it, and it appears in no diff a reviewer would read.

**Resolution: the dependency is excluded, not the permission.**

```kotlin
implementation(libs.mediapipe.tasks.vision) {
    exclude(group = "com.google.android.datatransport")
    exclude(group = "com.google.firebase")
}
```

Stripping the permission with `tools:node="remove"` would have produced the same
manifest while leaving the uploader in the APK. Excluding the group removes the
code. Verified afterwards: the merged manifest is back to the three health
reads, `CAMERA`, and the AndroidX internal receiver permission - **no `INTERNET`,
no `ACCESS_NETWORK_STATE`**. Pose estimation is entirely on-device and needs
none of it.

**`OfflineManifestGuardTest` is the permanent guard**, added because this class
of regression is invisible: it reads the merged manifest and fails on any
network permission, naming the blame report as the way to find the source. Its
mutation - removing the exclusion - reproduces the original failure exactly.

### What was built

- **`PoseLandmarkerSource`** runs MediaPipe and hands results to the P1
  `PoseAdapter`. **Nothing about mirroring or landmark layout changed** - that
  was settled in P1, is guarded by `MirroringRegressionTest`, and
  FINAL_ARCHITECTURE §12-D names front-camera mirroring as the risk that fails
  most quietly. This batch feeds that adapter; it does not re-derive it.
- **`SkeletonOverlayView`** is a plain `View` drawing on a `Canvas`, fed straight
  from the pose callback. CLAUDE.md §6: Compose never sees a frame. Nothing is
  allocated per frame - the frame is held by reference, the paints are built
  once, and `onDraw` walks a fixed connection table.
- **`SkeletonColouring`** holds the state-derivation logic as pure functions so
  it can be tested on the JVM. It reads the same `VisibilityPolicy` the validity
  gate reads, at the same inclusive boundaries, so the overlay and the engine
  cannot disagree about whether a joint was measured.
- **`PoseModelAsset`** makes a missing model a named state.

**The existing four states were enough.** The brief asked for any uncovered case
to be raised rather than quietly absorbed by a fifth state; none appeared. A
landmark MediaPipe cannot see arrives as low visibility, which is already
`UNTRACKED`, and a missing model is a screen-level condition rather than a
per-joint one.

Face landmarks are deliberately not drawn. No profile in the library uses them,
and a skeleton with a face on it invites the user to believe the system is
looking at more than it is.

### What Phase A could not test

The pose pipeline has never run. There is no model in the repository, no camera
in a JVM test, and no device attached. What is tested is the colouring logic and
the offline guarantee; what is not tested is everything that needs a camera, a
model and a person in front of it. **That is Phase B**, and it is the P4 device
reality check this project has deferred since P2-3 - deliberately, and now for
the last time.

**Scope of this entry.** It records two dependency checks and a pipeline that
has not yet run. It makes no claim about frame rate, pose accuracy or rep
counting; those are measurements Phase B produces and nothing here anticipates
them.

---

## P3-14 — Firebase Auth and Firestore sync: the first architecture change

*2026-09-22*

This entry records the only deliberate reversal of a founding constraint in this
project's history. Everything before it either implemented the documents or
corrected them where they contradicted themselves. This contradicts them on
purpose.

### What was decided, and by whom

Account-based cloud sync of session history, via Firebase Authentication
(email/password) and Cloud Firestore. Requested by the project owner and
**confirmed twice** — first as account creation, then clarified as full data
sync rather than an identity-only gate. It is a decision, not scope discovered
mid-batch, and it is recorded here before any of it was built.

### The contradiction, stated before it was resolved

Three things in the project forbade this:

- `FINAL_ARCHITECTURE.md` §6: *"**None.** No auth, no cloud, no server
  inference, no video upload."*
- `CLAUDE.md` §16: *"**MUST NOT**: use cloud inference, video upload,
  **authentication, a cloud database**, server-side processing, or unnecessary
  network dependencies."*
- `OfflineManifestGuardTest`, added in P3-13, failing the build on any network
  permission at all.

None was overridden silently.

**The two documents were amended first**, before a line of Firebase code was
written. `FINAL_ARCHITECTURE.md` §6's backend row now names Firebase and carries
an amendment block; `CLAUDE.md` §16 was rewritten as a narrow carve-out. Both
say the same thing: sync of `SessionRecord` history is in scope, and **no
cloud inference, no video upload, no analysis off-device, no analytics, and no
telemetry** remain hard lines. Room stays the source of truth.

### The offline guard was rewritten, not weakened

This is the part that mattered most, and the brief was right to single it out.

P3-13 added the guard because MediaPipe silently introduced `INTERNET` through
`com.google.android.datatransport`, a telemetry backend three levels down its
tree — invisible in any diff a reviewer would read. Deleting the guard to make
this batch pass would have thrown away the only thing that catches that.

**Its job changed from "no network permission" to "no network permission from
anything other than the artifacts this entry names."** It reads the manifest
merger's blame report rather than the manifest, because the manifest says *that*
a permission exists and the blame report says *who asked* — and who asked is the
whole question once some askers are legitimate.

The allowlist is five entries, each here for a stated reason:

| Artifact | Why |
|---|---|
| `com.google.firebase:firebase-auth` | sanctioned by this entry |
| `com.google.firebase:firebase-firestore` | sanctioned by this entry |
| `com.google.android.recaptcha:recaptcha` | **unavoidable transitive of firebase-auth**, bot protection on auth endpoints |
| `io.grpc:grpc-android` | Firestore's transport |
| `androidx.work:work-runtime` | the retry queue's network constraint |

reCAPTCHA is the one passenger nobody asked for. It is named explicitly rather
than tolerated silently, because a list somebody had to write is the only thing
that makes it visible.

**Proved by mutation.** Re-adding `transport-backend-cct` — the exact P3-13
villain — fails the guard, naming it:

```
Network capability arrived from an artifact no decision entry declares:
    android.permission.INTERNET  <-  com.google.android.datatransport:transport-backend-cct:3.1.0
```

The protection survives the batch. A second test asserts Kinesis's own manifest
still declares no network permission of its own, so every byte of network
capability stays attributable to a named third party.

**One correction along the way.** The guard's first parser matched the whole
report with a regex and over-captured, attributing `ACCESS_NETWORK_STATE` to
Room among others. Checking that against Room's own manifest showed it was
wrong, and the parser is line-based now because the report is line-structured.
Worth recording because it nearly produced a second false finding about Room
one batch after P3-12's was corrected.

### §17 check: Firebase

**Purpose.** Session history that survives losing the phone.

**Existing capability.** None. Room is local-only by design.

**Licence — and this is the finding.** Not uniformly Apache 2.0:

| Artifact | Licence |
|---|---|
| `firebase-firestore`, `firebase-common` | Apache 2.0 |
| `io.grpc:grpc-okhttp` | Apache 2.0 |
| `androidx.work:work-runtime-ktx` | Apache 2.0 |
| **`firebase-auth`** | **Android Software Development Kit License** |
| **`play-services-auth`** | **Android Software Development Kit License** |

Firebase Auth and Play Services Auth ship under **Google's proprietary Android
SDK licence, not an open-source one**. It permits use in developing Android
applications, which covers this, but it is a different legal instrument from
everything else in the project and §17 asks for exactly this to be determined
rather than assumed. Recorded because an academic project that has been careful
about licensing should not describe its dependency set as "all Apache 2.0" when
two of them are not.

**Uninvited passengers — checked, and the answer was the opposite of expected.**
The brief warned that Firebase BoMs commonly drag in Analytics, Crashlytics and
Performance Monitoring. Checked against the resolved tree:

```
absent : firebase-analytics      absent : firebase-crashlytics
absent : firebase-perf           absent : play-services-measurement
absent : datatransport           absent : firebase-messaging
absent : firebase-installations
```

**None of them appeared.** Declaring `firebase-auth-ktx` and
`firebase-firestore-ktx` against the BoM pulls only auth, Firestore and their
genuine transports. No exclusion was added for them, because adding one against
a dependency that is not present would be theatre — the guard above is what
catches them if a version bump ever introduces one. P3-13's `datatransport`
exclusion on MediaPipe remains and still holds.

**Bundle — measured.** Release APK **53,419,223 → 56,480,938 bytes, +3,061,715
(+5.7%)**. Modest next to MediaPipe's 45 MB.

**Platform.** Android-only, `:app`-only. Nothing in `:core:domain` or
`:core:analysis` references Firebase, and the analysis path cannot tell sync
exists.

**Maintenance.** Google, actively released. BoM 33.7.0.

**`google-services.json` — verified, not assumed.** `package_name` reads
`com.kinesis`; `applicationId` in `app/build.gradle.kts` reads `com.kinesis`.
They match. Project `kinesis-5b97b`.

### Offline-first survives where it matters

`SyncingSessionRepository` decorates the local repository. The ordering is the
guarantee: **the local write happens first**, and the enqueue happens only after
it returns. A failed enqueue is swallowed, because by then the session is on
disk and losing an upload is a retry while losing a session is losing the user's
training.

Nothing in the write path touches the network. `SyncQueue` *schedules*;
`SessionSyncWorker` does the work later, under WorkManager's network constraint
and exponential backoff, and returns `retry` rather than `failure` so transient
problems never drop the work.

`SyncOutageTest` pins it with the queue throwing on every call: the session
still reaches the dashboard, still earns its streak day, and the local result is
**identical to running with sync absent entirely**. Two mutations confirm it —
moving the enqueue ahead of the save, and letting an enqueue failure propagate —
and both lose the session.

### The append-only assumption, checked rather than assumed

The brief asked for this to be verified rather than taken from the documents. It
was, and three things hold:

- `SessionRecord`, `SetRecord`, `RepRecord` and `HoldRecord` are entirely `val`
  and `List` — no `var`, no `MutableList` anywhere.
- The DAO has no `@Update` and no per-session delete; only `@Insert` and a
  wholesale `deleteAllSessions`.
- `SessionRecorder.finish()` writes once and nothing reopens a session.

So two devices can never hold different versions of one `sessionId`, and the
merge is a union keyed by id rather than a reconciliation.

**But the check found a real defect the assumption would have hidden.**
`RoomSessionRepository.save` uses a bare `@Insert` against a primary key of
`sessionId` — so pulling a session this device already holds would **throw a
constraint violation**, not be ignored. The union filters against local ids
before writing anything. `a_session_already_held_locally_is_not_written_twice`
holds it, and the mutation that removes the filter fails with
`UNIQUE constraint failed: sessions.sessionId`.

That is the entire justification for the brief's instruction to check rather
than assume, and it paid for itself.

### Disclosure

`SyncDisclosure` sits on the auth screen **above** the sign-in button, at body
text size, before the user commits. It says what leaves — completed sessions,
counts, ranges of motion, tempo, hold durations, timestamps — and what does not:
no video, no photographs, no camera frames, no analysis off-device. The
signed-in address stays visible in the navigation bar, so a user never has to
wonder which account their training is going to.

This project has been explicit about data everywhere else — Health Connect asks
per metric and reports refusals honestly, and the offline guarantees were
verified rather than asserted. This is the first place data genuinely leaves,
and it is not the place to go quiet.

Auth failures are named states, not exceptions — the same discipline P3-11
applied to Health Connect and P3-13 to the pose model. `NETWORK_UNAVAILABLE` is
distinct from every other failure, because it is the one the user can do nothing
about, and its message says that recording still works without a network.

### What is NOT verified, and needs the project owner

**The Firestore security rules are not deployed and are not enforced.**

`firestore.rules` is written and is a deliverable of this batch. It locks every
document to `users/{uid}/sessions/{sessionId}`, allows read and create only when
`request.auth.uid == userId`, and **denies update and delete outright** — which
enforces the append-only property server-side, so even a modified client cannot
rewrite recorded history.

**They must be pasted into the Firebase console by hand:**
*Firebase console → Build → Firestore Database → Rules → Publish.*

Until that happens the project remains in **test mode, which allows open read
and write to anyone holding the project config — and `google-services.json`
ships inside the APK, so that is not hypothetical.** No code in this app can
enforce the boundary; security rules are the only mechanism. Nothing in this
batch was able to test the rules, and nothing here claims they work.

**Follow-up, 2026-09-23 — published, not yet tested.** The project owner reports
that `firestore.rules` has been published in the Firebase console, so the
owner's action above is done and the project should no longer be in test mode.
That is recorded on the owner's report; no session here saw the console. **The
rules have not been exercised by any client.** Nothing has yet shown that a
signed-in user can create and read their own sessions, that another account or
an unauthenticated client is refused, or that update and delete are denied.
That check needs a real client and belongs with Phase B's first Firebase round
trip. Until it runs, the rules are published, not verified.

Also unverified, and honestly so: no Firebase call in this batch has ever run.
There is no device attached and no emulator; `FirebaseAuthGateway` and
`FirestoreSessionStore` compile and are exercised only through their interfaces
by fakes. What is tested is the merge, the offline guarantee and the document
mapping. What is not tested is Firebase itself, and that needs the device work
Phase B of Batch 9 already owes.

**Scope of this entry.** It records an architecture change, two document
amendments, a guard rewrite and a dependency check. It makes no claim that sync
works end to end, because nothing here has seen it do so.

---

## P3-15 — The live screen wired: camera to readout, and what reading the pipeline found

*2026-09-23*

The batch P3-12 and P3-13 deferred. The live screen ran on a hardcoded
`LiveUiState` behind a "CAMERA PENDING" placeholder; it now runs camera →
MediaPipe → `PoseAdapter` → shared-core analysis → readout and overlay. **None of
it has run on a device.** This entry records what was connected, one rule that
had to be designed, and what reading the pipeline end to end found. One of those
findings needs a decision before Phase B's rep counts mean anything.

**No new dependency.** P3-13 already declared `camera-core`, `camera-camera2`,
`camera-lifecycle` and `camera-view`, which cover everything here, so no §17 check
was due. `OfflineManifestGuardTest` passes unchanged.

### What was built

- **`LiveCamera`** (`:app`) — CameraX `Preview` plus `ImageAnalysis` on the front
  camera, bound to the activity's lifecycle so backgrounding stops the camera.
  Keep-only-latest backpressure, RGBA output, and a 4:3 request on both use cases
  so the preview and the analysis frame share an aspect ratio.
- **`LiveRepetitionAnalysis`** (`commonMain`, new `live` package) — CONTRACTS.md
  §7's real-time path for one repetition exercise: normalise, validity,
  `BilateralRepTracker`, and four values out — display phase, count, frame
  validity, last repetition validity. In the core because deciding what a
  frame's validity is and which repetition was "last" is analysis (CLAUDE.md §2).
- **`BilateralPhase.furthestThroughRepetition`** and `leftPhase` / `rightPhase`
  on the tracker — below.
- **`LiveSessionEngine`** (`:app`) — glue on MediaPipe's result thread: frame to
  the core, the four values to `LiveAnalysisBridge`, the frame itself straight to
  the overlay. Compose state is set on the main thread from what the bridge
  publishes. The engine decides nothing.
- **`LiveAnalysisScreen`** now embeds a `PreviewView` and `SkeletonOverlayView`
  through `AndroidView`, with the readout unchanged beneath them.
  **`LiveUnavailableScreen`** shows each reason nothing is being measured.
- **`MainActivity`**'s live destination checks, in this order: analysis mode,
  pose model (`PoseModelState.Absent` / `Unreadable`), camera permission (asked
  once on opening, then only on a tap), pose runtime and camera binding. Each
  failure is shown as itself — **a count of 0 would read as "you did nothing"
  when the truth is "nothing was watching".** Teardown closes the landmarker.

**Not built, deliberately.** The live screen counts and shows repetitions; it
does not record a session. `SessionRecorder` is not connected to it, so nothing
from a live set reaches storage, the dashboard or sync.

### The phase-reduction rule

The readout has room for one phase and the tracker runs two machines. CLAUDE.md §3
requires that reduction to be named at the call site, so the tracker exposes
`leftPhase` and `rightPhase` and no combined `phase`, and the reduction is
`BilateralPhase.furthestThroughRepetition`.

**The rule:** rank `IDLE < TOP < DESCENDING < BOTTOM < ASCENDING` and show the
higher-ranked side.

- **Idle only when both sides are idle.** An occluded side reads `IDLE`, so it
  can never hide a side that is being measured.
- **Mid-repetition beats at rest.** The resting arm of an alternating curl sits
  at `TOP`; the working arm is what is shown.
- **Both mid-repetition: the one closer to completing.** The readout describes
  the repetition that will be counted next.

For `SIMULTANEOUS` profiles this has a consequence worth having: the tracker
counts a paired repetition only when the second side returns to the top, and
this rule shows `TOP` only when both have, so **the phase and the count change on
the same frame**. The alternative, least-advanced-wins, shows the repetition
finished a few frames before it is counted.

**What it loses, stated.** Under `PER_LIMB`, when both arms are mid-repetition at
once, one arm's phase is not shown. That is acceptable only because the result is
**display-only**: nothing counts, measures, stores or judges from it, and both
per-side phases remain on the tracker. The ranking is written out as a `when`
rather than read from `ordinal`, so reordering the enum cannot change it.

**Pinned by `BilateralPhaseTest`:** all 25 pairs as an explicit table, symmetry,
idle-only-when-both-idle, and two checks against the real tracker — a squat with
the right knee four frames behind the left shows `TOP` on exactly the frame each
repetition is counted, and an alternating curl shows the working arm.

### Validity is reported, not used to gate the tracker

Every frame reaches the tracker, `INVALID` ones included. The tracker already
refuses to measure a side whose landmarks fall below `marginal`, and does it **per
side**. Withholding the whole frame would let an occluded right wrist abandon a
left-arm curl that was being measured perfectly well — the coupling P3-2 built
two independent machines to prevent. For every archetype the required landmarks
are the joint chain itself, so an `INVALID` frame and a `NaN` side coincide.
`FrameValidity` drives the readout and the overlay, and is what P8's technique
rules will be blocked on (CONTRACTS.md §8: measurement may continue if honest,
technique judgement may not).

Pinned: `an_occluded_right_arm_does_not_stop_the_left_arm_being_counted`. Its
mutation — skip the tracker on `INVALID` — counts zero curls instead of three.

### Normalisation has no real-time consumer

CONTRACTS.md §7 puts normalise first, and it runs first. But **nothing on the
real-time path reads normalised coordinates**: joint angles are invariant to
translation and uniform scale and are computed from `Landmarks` directly, and
`NormalizedLandmarks` has no production consumer anywhere. What the step
contributes today is its degenerate-torso check — a collapsed estimate can report
every landmark clearly visible while placing them all on one point, and that
frame is `INVALID` (pinned by
`a_collapsed_torso_is_invalid_however_visible_its_landmarks_claim_to_be`).

It allocates its output every frame to do that. The addition to
`PerFrameAllocationTest` confirms the cost is constant rather than growing; it is
not zero. **Open:** either normalised coordinates get a consumer (P5 features are
the obvious one) or the step's position in §7 should be revisited. Recorded, not
decided.

### Mirroring is made true in capture, not assumed

P1-5: `PoseAdapter` assumes front-camera frames reach the model **mirrored**.
CameraX's analysis stream is the raw sensor image, which is not mirrored — only
the preview is. So `LiveCamera` mirrors every frame before MediaPipe sees it, and
P1-5's premise now holds by construction rather than by a CameraX setting.
`PoseAdapter` is unchanged and `MirroringRegressionTest` still guards it.

The claim that CameraX analysis frames arrive unmirrored comes from CameraX's
documented behaviour and from MediaPipe's own Android sample, which flips
front-camera frames for the same reason. **It has not been verified on this
device.** If it is wrong, the line that changes is the flip in `LiveCamera`, not
the adapter.

The overlay then draws x reflected (`displayMirrored`), because landmarks leave
the adapter in unmirrored image coordinates and the preview is shown mirrored.
Drawing only; no landmark and no measurement changes.

### Defects found by reading the pipeline

Three fixed, two flagged.

1. **Fixed — results carried the wrong timestamp.** `PoseLandmarkerSource`
   stamped each result with the last frame *submitted*. `LIVE_STREAM` drops frames
   while busy and delivers results after later frames have gone in, so that value
   belonged to a different frame — and repetition tempo is measured from these
   timestamps. It now uses `PoseLandmarkerResult.timestampMs()`, the frame's own,
   confirmed present in 0.10.21 with `javap`. Timestamps are the sensor's exposure
   time, so tempo does not absorb processing jitter.
2. **Fixed — a frame with nobody in it produced nothing.** The source returned
   silently, so when the user stepped out the overlay kept the last skeleton **in
   the colour that means "tracked"**, and a repetition in progress hung open. A
   required `onNoPose` callback now clears the overlay and feeds the tracker a
   frame in which nothing was seen, so the repetition ends `INSUFFICIENT_DATA`
   exactly as an occlusion does.
3. **Fixed — the readout claimed tracking before any frame.**
   `LiveAnalysisBridge` started at `FrameValidity.VALID`, so the screen said
   "TRACKED RELIABLY" in phosphor green before the camera had delivered anything —
   the confident indicator with nothing behind it that P3-12 warns against. It
   starts at `INVALID` now; pinned by
   `before_any_frame_the_readout_does_not_claim_tracking`.
4. **Flagged — the attention state does not pulse.** P3-12 gives `ATTENTION`
   three channels: hue, a wider stroke, and "the only pulse in the overlay".
   `OverlayState.pulses` is declared and `SkeletonPaletteLegibilityTest` checks
   it, but **`SkeletonOverlayView` never reads it**. The overlay draws two
   channels. Not fixed here: it is rendering work outside this batch's scope, and
   is recorded so the guard is not mistaken for the behaviour.
5. **Flagged — `NaN` visibility passes the validity gate.** `ValidityGate` and
   `JointAngles` test `visibility < marginal`, which is false for `NaN`, so a
   `NaN` visibility counts as fully visible — while `SkeletonColouring` draws it
   as untracked. The two disagree, contrary to P3-13's "cannot disagree".
   Unreachable from this pipeline, which substitutes 0 for a missing visibility,
   so not changed here; a core behaviour change deserves its own test first.

### Two things that need the project owner

**1. Joint angles are distorted by the frame's aspect ratio. This should be
decided before Phase B interprets any repetition count.**

MediaPipe reports x as a fraction of the image **width** and y as a fraction of
its **height**. Every profile in the library measures its angle in `IMAGE_2D`,
from those two numbers as if they shared a unit. On a portrait 4:3 analysis frame
(480 × 640) they do not: vertical distances are compressed by 0.75 relative to
horizontal ones. Nothing in the repository corrects for this — a search of code,
docs and scripts for aspect, width or height handling, run before this batch
added anything, found nothing.

Computed, not measured on a device:

| True angle | Worst-case error over limb orientations |
|---|---|
| 60° | +15.2° |
| 90° | +16.3° |
| 120° | −15.2° |
| 160° | −6.5° |
| 180° | 0° |

A concrete side-on case: a true 60° knee reads 66.6°. For scale, the squat's
bottom threshold is 90° with a 5° band and the curl's is 60° with 8°.

**Why it cannot be left to calibration.** The error depends on the limbs'
orientation in the image, which changes through the repetition, so it is not a
constant offset a personal baseline absorbs. Thresholds tuned in P4 against
distorted angles would inherit an orientation-dependent error that differs by
camera placement.

**Not fixed here, because every fix changes a convention.** Rescaling to one unit
at the platform edge changes what a `Landmarks` coordinate means and how the
overlay maps it; letterboxing to a square before MediaPipe changes the overlay's
mapping instead. Either way, the MM-Fit pipeline in P5 must apply the same
correction or its results will not transfer. **Recommendation:** correct in
`PoseLandmarkerSource`, the only place that knows the frame's pixel size, by
rescaling y into width units; give the overlay the frame's aspect ratio; and
record the unit in CONTRACTS.md. That is an architecture decision, so it is
recorded here rather than made.

**2. The pose model is in the working tree and will ship in any build.**
`app/src/main/assets/pose_landmarker_lite.task` is present — placed by hand,
untracked, and not added by this batch. Any APK built from this tree packages it.
P3-13: *"Before the model is distributed with a build, its model card must be
read and its terms recorded here."* Still not recorded. A local debug build is
not distribution; handing an APK to anyone else would be.

### Holds, and one device constraint

**Isometric profiles are not wired.** Plank, Wall Sit and Side Plank go through
`HoldAnalyser`, which this screen does not run. Choosing one shows "ISOMETRIC HOLD
NOT YET WIRED" and starts no camera. `BilateralRepTracker` accepts only a
`RepetitionExercise`, so forcing a hold through it would not compile anyway.

**MediaPipe ships no x86_64 library.** Read from the 0.10.21 AAR: `arm64-v8a`,
`armeabi-v7a`, `x86`. Current emulator images are mostly x86_64, where the
landmarker fails to load with `UnsatisfiedLinkError`. The live screen reports
that instead of crashing. Phase B therefore needs a physical device, which it
needed anyway.

### What "tested" means here

`gradlew assemble allTests :app:testDebugUnitTest`: **BUILD SUCCESSFUL, 984 test
executions, 0 failures, 0 errors**, no compiler warnings, against a measured
baseline of 931 immediately before this batch. The additions are
`BilateralPhaseTest` (5) and `LiveRepetitionAnalysisTest` (7) on JVM, JS and both
Android unit-test variants, one test in `PerFrameAllocationTest` (JVM),
`LiveSessionEngineTest` (3), and one in `LiveAnalysisBridgeTest`.

**Eight mutations, each caught by the test written for it:** the reducer showing
the least-advanced side; the torso check removed from validity; `INVALID` frames
withheld from the tracker; a no-pose frame not fed to the tracker; the overlay
coloured independently of the readout; a no-pose frame leaving the skeleton up;
the bridge starting at `VALID`; and `LiveRepetitionAnalysis.kt` dropped from the
source guard's real-time list — the guard now covers the new `live` package.

### What is NOT verified — Phase B

Nothing in this entry has run. Specifically unverified:

- that frames arrive at all, and the sustained frame rate — each frame costs two
  bitmap allocations (`toBitmap`, then rotate and mirror) before MediaPipe;
- the mirroring premise above, which needs the raise-your-left-arm check;
- that the overlay lines up with the body — it assumes portrait, a 4:3 frame on
  both use cases, and `FIT_CENTER`; a device that falls back to another ratio
  would misalign, and landscape is not handled;
- the permission dialog, refusal, and "don't ask again";
- teardown on a real device: leaving the screen, backgrounding, rotation;
- that `PoseModelAsset`'s one-byte read and MediaPipe's own loader agree about a
  truncated file.

**Scope of this entry.** It records a pipeline connected and compiled, one
display rule, three defects fixed and two flagged, and two findings for the owner.
It makes no claim about frame rate, pose accuracy, mirroring correctness or rep
counting on a device, and the aspect-ratio finding means any rep count Phase B
produces before that decision is made measures a distorted angle.

---

## P3-16 — Frame shape corrected, holds wired, sets saved

*2026-09-23*

The batch after P3-15, at the project owner's direction and in their order. The
owner accepted P3-15's recommendation on frame shape. **Nothing in this entry has
run on a device either**; everything below is measured on synthetic frames on
the JVM and JS, and the device work stays Phase B.

### Housekeeping

The eight `.kt` copies at the repository root were deleted, and the P3-15 work
committed as a checkpoint (`7d61424`). **`app/src/main/assets/pose_landmarker_lite.task`
was not committed and is still untracked**: its model-card terms are not yet
recorded, which P3-13 requires before a build carrying it is distributed.

### Frame shape: corrected in the adapter

`PoseAdapter.toAnatomical` now takes the frame's width and height and rescales y
into **frame widths**: x runs 0..1 across the frame, y runs 0..height/width down
it, and a pixel is the same length in both directions. Frame shape is camera
geometry, so it is corrected there, in platform code (CLAUDE.md §2), and the core
never learns a frame had one. The size comes from the `MPImage` MediaPipe hands
back with each result — the exact image that result was computed from.

- **Why widths and not heights:** x and the mirror axis stay exactly as they
  were, and MediaPipe gives z "on roughly the same scale as x", so the unit both
  axes now share is the one z already had.
- **`AspectCorrectionRegressionTest`** (new, beside `MirroringRegressionTest`)
  builds its scene in pixels, where angles are true, poses a 60° and a 120° knee
  at the orientations where the distortion is worst, and requires both to come
  out within 10⁻⁹ on a portrait 480 × 640 frame and on a landscape one, through
  both camera paths. It pins the unit (the bottom edge of a portrait frame is
  640/480 widths down), and shows the same scene reading more than 10° wrong
  uncorrected, so the guard can fail.
- **`MirroringRegressionTest` is unchanged in substance.** Its calls now pass a
  named square frame, on which the aspect step is the identity, so every one of
  its assertions is as it was and it still tests mirroring alone. The
  combination is covered by the new test.
- The overlay draws both axes scaled by the view's width. `LandmarkVelocity`'s
  units are now frame widths per second, and `CONTRACTS.md` §4 records the unit.

**The P7 MM-Fit pipeline must apply the same correction, using MM-Fit's own
frame size.** Landmarks from MM-Fit's video without it carry a different
orientation-dependent angle error from the phone's, so a rep-count evaluation
run on them tests the thresholds against a different measurement and would not
transfer to the phone. `CONTRACTS.md` §4 now says every producer of
`Landmarks` must do this.

**A correction to P3-15:** it placed that pipeline in P5. It is P7, per
`ZERO_DATASET_BUILD_PLAN.md`'s phase table.

### `NaN` visibility: one predicate, used everywhere

`VisibilityPolicy` gains `isMeasurable` and `isReliable`, written as `>=` so a
`NaN` visibility is neither. `ValidityGate`, `JointAngles`, `LandmarkVelocity`
and `SkeletonColouring` all use them.

The brief named `ValidityGate` only; the kinematics were changed too, on
purpose. P3-15 lets `INVALID` frames reach the tracker because "an `INVALID`
frame and a `NaN` side coincide". That holds only if the gate and the kinematics
agree about a landmark, and fixing the gate alone would have made them disagree
the other way round. Pinned on JVM, JS and both Android variants.

### The attention pulse, drawn

P3-12 gave `ATTENTION` three channels and the overlay drew two. It now pulses,
decided by `SkeletonColouring.strokeScale`, a pure function tested on the JVM.
Only a state whose palette entry says it pulses ever varies.

**The pulse widens the stroke, from its own width to half again, rather than
fading it.** Fading would drop amber's contrast below the floor
`SkeletonPaletteLegibilityTest` guarantees for part of every cycle, and the
width channel P3-12 made load-bearing would be the one giving way. It never gets
thinner than 6 dp. **1 Hz**, under WCAG 2.3.1's three flashes a second. The view
keeps redrawing while the figure pulses, so the pulse does not freeze if pose
results stall. Not yet seen on a screen.

### The torso check no longer allocates

`LandmarkNormalizer.normalize` built two 99-element arrays and a wrapper each
frame to answer yes or no. `hasMeasurableTorso` shares the one torso computation
with `normalize`, so the two cannot disagree (pinned case by case), and allocates
nothing — `PerFrameAllocationTest` asserts zero bytes over 50,000 calls. The live
path no longer builds a normalised frame at all. P3-15's open question about
normalisation's place in CONTRACTS.md §7 stands.

### Overlay alignment, true by construction

- Both use cases request **4:3 with no fallback**. After binding, both bound
  resolutions are checked, and any other shape is refused with a named state
  rather than shown with a skeleton drifting off the body.
- The camera area is sized to `LiveCamera.PORTRAIT_FRAME_ASPECT`, the preview
  uses a scale type that never crops, and the preview and the overlay fill the
  same box, so they share exact bounds. The overlay draws in frame widths.
- The target rotation is passed explicitly from the display.
- **The live screen is locked to portrait** while it is shown and restored when
  it leaves, and the camera starts only once the configuration is portrait.
  `MainActivity` now declares that it handles orientation changes itself. Without
  that the lock would recreate the activity, and the navigation state — held in
  Compose and not saved — would drop the user on the dashboard mid-set. Other
  screens now re-lay themselves out on rotation instead of being recreated.

**What "by construction" does not cover:** that CameraX's preview and analysis
take the same crop of the sensor at the same aspect ratio. Expected, since it is
one sensor and one ratio, but not verified. Phase B looks.

### Holds wired

Plank, Wall Sit and Side Plank now run live through `LiveHoldAnalysis`
(`commonMain`): torso check → validity → a filter pipeline per side →
`HoldReading` → `HoldAnalyser`. It accepts only an `IsometricExercise`, so a hold
cannot reach the repetition tracker; the type refuses it. The app side,
`LiveHoldEngine` and `LiveHoldBridge`, is glue in the same shape as the
repetition path. `HoldAnalyser` gains a read-only `heldMs`.

**The reduction P3-9 left open, now decided.** P3-9: *"When a landmark-driven
hold tracker is eventually built, it must reduce the `BilateralSignal` ...
through an explicit named reducer — never an average, and never a silent
fallback."* `HoldReading.furthestFromTarget`:

- **Both sides measured:** the side further from the target is read, so a hold
  is credited only while every side that was seen agrees the body is within
  tolerance. One side sagging out of it is a break, not something the other side
  can outvote.
- **One side measured:** that side is read, as the body line. P3-9 established
  that the hold signal belongs to the line, not to a limb, and measured the two
  sides agreeing within a degree. It is also the normal case: a side-on plank
  sees its near side only.
- **Neither measured:** `NaN`, which `HoldAnalyser` treats as unseen rather than
  broken.
- **Equally far on opposite sides of the target:** the lower value, so the
  answer never depends on which side is called left. The first version returned
  the left side on a tie; the symmetry test caught it.

The result is always exactly one side's value. Pinned by `HoldReadingTest` and,
end to end, by `LiveHoldAnalysisTest`.

**The readout** is time held (m:ss), the hold's state and the tracking state.
`LiveHoldBridge` publishes on whole seconds: a 30-second plank is 900 frames and
31 publications. A hold reached and then lost takes the attention state while it
lasts. That is the hold's counterpart of an abandoned repetition — a measured
event, the body line outside its band — and so the same footing P3-12 gave
`ATTENTION`.

**Plank is ready for Phase B in one sense only:** it runs the same pipeline as
the repetition exercises. One thing to watch there: plank and squat are side-on
profiles that require **both** sides' landmarks. If the far side reads below
marginal on a real device, the readout will say "not visible" for the whole set
while the near side is measured and the hold still counts
(`a_plank_seen_from_one_side_only_is_still_measured`). Whether that wording is
right is a Phase B finding, not a design decided here.

### Sets saved

A set ends by **[ END SET ]** or by leaving the screen, whichever comes first.
Both go through one `LiveSetSaver`, which is idempotent, so a set is written
once. The set is closed synchronously at the tap, and frames arriving after it
are ignored by the engine. The tracker or analyser is finished first, so a
repetition still under way is recorded as `INCOMPLETE`: not counted, not
dropped.

**A defect found on the way:** `BilateralRepTracker` paired two `INCOMPLETE`
halves of a simultaneous repetition as **`ABORTED`** — "turned back before
reaching depth", a claim about the movement the system never observed. It fell
through a branch that knew only VALID, INSUFFICIENT_DATA and ABORTED. The
precedence is now unseen > unfinished > turned back. The test the brief asked
for found it, and `ArchetypeProfileTest` pins it. This also changes one existing
behaviour: a simultaneous repetition timed out by `maxRepDurationMs`, which the
FSM reports as `INCOMPLETE` per side, now pairs as `INCOMPLETE` rather than
`ABORTED`.

**The write path is the existing one, unchanged.** `SessionRecorder` →
`SyncingSessionRepository` → Room: local write first, upload scheduled after
(P3-14). `LiveSetSaverTest` drives it with a sync queue that throws on every
call, which is what no network looks like from here. The set still lands
locally, and the upload attempt comes only after the local write.

- **One set, one session.** The repository is append-only, and the Firestore
  rules deny update, so a saved session can never gain another set. Each set is
  therefore written as its own session the moment it ends, rather than held open
  for more sets a crash could lose. Consequence: the dashboard's session count
  counts live sets.
- **A set in which nobody was ever seen is not saved.** `SessionRecorder` records
  an empty set because "the user pointed a camera at themselves and nothing
  completed". That still holds when someone was seen and nothing completed. It
  does not hold when no pose ever arrived.
- **Writes run on a process-lifetime scope.** Leaving the screen disposes the
  composition that ended the set before the write completes.
- **Not handled, stated:** a save that fails after the user has left the screen
  is reported nowhere; the end-set path shows "SET NOT SAVED". And a set under
  way when the app is killed is lost, because nothing is persisted until it ends.
- **Daylight saving.** The dashboard computed "today" with
  `TimeZone.rawOffset`, which ignores daylight saving. Sessions are now filed,
  and today computed, with the offset actually in force (`utcOffsetMinutesAt`),
  so the two cannot put one session on different days. Pinned by
  `UtcOffsetTest`.

### A risk found, not fixed: the outlier gate can lock a side out

Writing `LiveHoldAnalysisTest`, a one-frame 28° sag was never seen at all.
`MedianOutlierGate` does not remember samples it rejects. That is deliberate
(P2), "or a sustained excursion would talk the gate into accepting it". But it
means **any sustained jump of more than 20° from the window's median is
rejected on every later frame, and that side reads `NaN` until the pipeline
resets.** A real sag develops over many frames and passes, and the test now
models one.

On a device the same property could bite in either direction. A limb moving
faster than about 20° per frame — a brisk curl at a low frame rate — or a
landmark that jumps and stays could lock that side out for the rest of the set.
It is not changed here, because it is P2's real-time design, with its own
ablation (P2-4), and the right threshold depends on frame rates only Phase B can
measure. **Phase B must measure per-frame angle change at the real frame rate.**

### What "tested" means here

`gradlew assemble allTests :app:testDebugUnitTest`: **BUILD SUCCESSFUL, 1,078
test executions, 0 failures, 0 errors**, and 0 compiler warnings on a forced
recompile of every Kotlin source set. The P3-15 checkpoint stood at 984, so this
batch adds 94. `:core:analysis` rose from 191 to 209 per shared target (JS and
both Android variants) and from 204 to 223 on the JVM, which alone runs the
allocation guards. `:app` rose from 75 to 96. `:core:domain` is unchanged at 33
per target.

**Twenty-one mutations, each caught by the test written for it,** with every
file restored and verified byte for byte afterwards:

| Mutation | Caught by |
|---|---|
| Aspect correction removed | `AspectCorrectionRegressionTest` — all three frame tests |
| Axes put in frame heights, not widths (angles still right) | the unit pin alone |
| Policy predicate written as `!(v < marginal)` | three `NaN` tests |
| Gate reverted to `<` | `a_nan_visibility_makes_the_frame_invalid` |
| Kinematics reverted to `<` | `a_nan_visibility_side_is_not_measured_by_the_kinematics_either` |
| Torso check builds a normalised frame | `the_torso_check_allocates_nothing_at_all` |
| Torso check always passes | `the_torso_check_agrees_with_normalisation_on_every_case` |
| Every state pulses | `a_state_that_does_not_pulse_never_changes_width` |
| Pulse fades instead of widening | `attention_pulses_between_its_own_width_and_half_again` |
| Any frame shape accepted | `any_other_shape_is_refused` |
| Hold reading averages the sides | `HoldReadingTest` — two tests |
| Hold reading takes the side nearest the target | `a_hold_is_credited_only_while_every_measured_side_is_within_tolerance` |
| Unfinished pair falls back to `ABORTED` | `a_simultaneous_repetition_unfinished_when_the_set_ends_is_incomplete_not_aborted` |
| Set closed without finishing the tracker | `ending_a_set_mid_repetition_saves_it_locally_with_no_network` |
| Ending twice writes twice | `ending_the_same_set_twice_writes_it_once` |
| Frames after the end still processed | `frames_after_the_set_ended_are_ignored` |
| A set nobody was seen in is saved | `a_set_in_which_nobody_was_seen_is_not_saved` |
| Offset ignores daylight saving | `the_offset_includes_daylight_saving_when_it_is_in_force` |
| Hold bridge publishes every frame | `a_thirty_second_hold_recomposes_about_once_a_second_and_not_per_frame` |
| A broken hold not shown as attention | `a_broken_hold_turns_the_skeleton_to_the_attention_state_while_it_lasts` |
| `LiveHoldAnalysis.kt` dropped from the source guard | `every_file_in_the_real_time_package_is_covered_by_the_scan` |

**Tests-first, honestly stated.** The core pieces were written test-first, and
three of those tests failed first for real reasons: the `ABORTED` defect, the
reducer's tie, and the gate lockout. The app-side engines were written before
their tests, which followed immediately. One of those tests, as first written,
could not fail: it checked the saved set after ending, which is copied at the
end anyway. It was rewritten to assert that frames after the end reach neither
the readout nor the skeleton, and its mutation is among those above.

**A build-environment note.** On this machine the Kotlin compile daemon kept a
module jar open between builds, failing the next one with a Windows file lock.
Builds here ran with `-Pkotlin.compiler.execution.strategy=in-process` after
stopping the daemons. It changes where the compiler runs, not what it compiles.

### What is NOT verified — Phase B

Everything in P3-15's list still stands. Added by this entry:

- the aspect correction on a real frame, and whether the bound frames really are
  4:3;
- that the skeleton lies on the body, including the preview/analysis crop
  question above;
- the pulse, seen rather than computed;
- the portrait lock and rotation handling on a device;
- live hold times against a stopwatch, and the side-on far-side question;
- per-frame angle change against the outlier gate's 20°;
- a set saved on every exit path — END SET, the nav bar, back, backgrounding;
- the Firestore rules against a real client (P3-14 follow-up).

**Scope of this entry.** It records a coordinate convention, two named
reductions, a structural alignment, a save path and four defects fixed —
aspect, `NaN` visibility, the unfinished-pair validity and daylight saving —
plus one risk flagged. It makes no claim about anything a camera, a model or a
person would show.

---

## P3-17 — Either camera, the model's licence, a gate that re-admits, and a Phase B readout

*2026-09-23*

Four items at the owner's direction, in their order. P3-16's work is untouched;
before starting, the tree was checked against `1e5431e` and only this batch's
files differed. **Nothing here has run on a device.**

### Front or back camera

The live screen has a camera switch in the camera area's corner. **Front stays
the default.**

- **One source of truth.** `LiveCamera` reads the facing from the
  `PoseLandmarkerSource` it is given, never from a second parameter. The camera
  it opens, whether it mirrors the frame, and what `PoseAdapter` is told are
  therefore one decision and cannot disagree.
- **Back camera: no mirroring anywhere.** The frame is not flipped before
  MediaPipe, the adapter is told `BACK` and undoes nothing, and the overlay is
  drawn unmirrored. The front path is unchanged from P3-15: flipped before the
  model so P1-5's premise holds, unmirrored by the adapter, and drawn mirrored
  over a mirrored preview.
- **The overlay follows the preview.** `LiveCamera.previewIsMirrored` states
  CameraX's behaviour — it mirrors the front preview and not the back — as a
  claim to check on the device, not a choice made here.
  `LiveCamera.mirrorsAnalysisFrame` is our choice. They are two different facts
  and are kept as two named functions, even though today they give the same
  answer.
- **Switching mid-set ends the set.** The engine is keyed on the facing, so
  switching replaces it. The set in progress ends through the existing
  `LiveSetSaver`, exactly as leaving the screen ends it: saved if anyone was
  seen, with an unfinished repetition as `INCOMPLETE`. A new set starts. Two
  reasons. A set seen from two viewpoints with a gap between is not one
  measurement. And Camera2 does not promise that two camera devices' frame
  timestamps share a clock, so one set's timing cannot safely span both.
- A camera that fails to bind offers the other one. Nothing else offers it.

**`CameraFacingParityTest`** is a new permanent guard beside
`MirroringRegressionTest`. It stages one physical pose — left arm raised, so a
swap shows as a wrong height and not only a wrong x. It runs that pose through
each facing's real capture decision, a simulated model that labels from the
image it is given (P1-5's premise, stated), and the real adapter. Both cameras
give the same anatomical left and right, and both match the pose. It also shows
the skeleton landing where the preview shows each body part for both cameras,
and that a capture decision disagreeing with the adapter is detected.

**Not unit-tested:** that switching actually replaces the set. That is Compose
keying and is left for Phase B.

### The pose model's licence, read and recorded

The owner supplied `docs/third_party/Model_Card_BlazePose_GHUM_3D.pdf`. It was
read in full. It covers the MediaPipe BlazePose GHUM 3D models, by Valentin
Bazarevsky, Ivan Grishchenko and Eduard Gabriel Bazavan of Google. The card is
dated **16 April 2021** and the models are **licensed under the Apache License,
Version 2.0**. P3-13's item is closed.

**What Apache 2.0 permits:** use, reproduction, modification and distribution,
in source or object form, including commercially, with the right to sublicense.
It also gives a patent licence from each contributor, which ends for anyone who
sues claiming the work infringes a patent (§§2–3).

**What it requires of a redistributor (§4):** give recipients a copy of the
licence; keep the copyright, patent, trademark and attribution notices; mark any
modified files; and, if the work includes a NOTICE file, reproduce its
attributions. It grants no trademark rights (§6), and the work comes as is, with
no warranty or liability (§§7–8).

**What Kinesis does about it:**

- It ships `pose_landmarker_lite.task` **unmodified**.
- The file holds two `.tflite` models and no NOTICE, and MediaPipe's AARs carry
  none either, so §4(d) does not apply.
- A new **in-app licences screen**, linked from the history dashboard, shows the
  model's notice: name, authors, card date, licence, "distributed unmodified",
  and the card's own statement that it is not intended for life-critical
  decisions.
- The same screen shows the **full Apache 2.0 text**, shipped as an asset. It was
  copied byte for byte from Android Studio's canonical `LICENSE.txt` rather than
  retyped.
- No new dependency. `ThirdPartyNoticesTest` fails if a model in `assets/` is not
  named in the notice, or if the shipped licence text is incomplete.

**The card's own limits, recorded because they bear on this project:**

- **Trained only on back-camera photos.** All training images were captured on
  back-facing smartphone cameras, through an AR app, in real-world conditions.
  The card reports 30K consented images, most of its 85K training images being
  fitness poses. Its evaluation also used only back-camera photos. **The back
  camera path is therefore the one closest to the model's documented
  conditions.** The front path feeds a mirrored front-camera image, which the
  card does not evaluate at all.
- **Out of scope, in the card's words:** more than one person in the image;
  people further than about 4 m (14 feet); the head not visible; applications
  that need metric depth; and any surveillance or identity recognition. For
  Phase B: one person in shot, head in frame, within 4 m.
- **z is not metric.** It is "up to scale", obtained by fitting a synthetic
  body model (GHUM) to 2D points. Every Kinesis profile measures in `IMAGE_2D`,
  so z enters no measurement. Only `SPATIAL_3D` would use it, and no profile
  does.
- **Accuracy, as Google measured it.** PDJ, the percentage of correctly detected
  keypoints (PCK@0.2 of torso diameter), averaged over 14 regions and 1,400
  back-camera photos drawn from the same source as the training data:
  **Lite 87.0%, Full 91.8%, Heavy 94.2%.** Kinesis ships Lite. **These are
  Google's figures, on Google's data, under Google's conditions. They are not
  Kinesis measurements, and they must never be quoted as Kinesis's accuracy** —
  CLAUDE.md §22.
- The card contradicts itself in one place. Its gender text gives Lite an
  average of 83.7%, while its table gives 87.6%. Only the geographic averages,
  where text and table agree, are quoted above.
- The card states the model is "not intended for human life-critical
  decisions", which is consistent with CLAUDE.md's "not a medical diagnostic
  system".

**The licences screen also lists the app's direct dependencies,** each with the
licence read from its own POM. All are Apache 2.0 except Firebase
Authentication, which is under the Android Software Development Kit License, as
P3-14 recorded.

**A correction to P3-13, found by reading the POMs.** `camera-core`'s POM
declares two licences: Apache 2.0, and a **BSD licence for libyuv**, which it
bundles. P3-13 recorded CameraX as Apache 2.0 only. A BSD licence requires its
copyright notice and licence text to be reproduced in a binary distribution. The
AAR bundles only the Apache text, and libyuv's text was not obtained here. It was
deliberately not reproduced from memory or from a summarising fetch. **The
libyuv notice is owed before a build is distributed.** The licences screen names
the BSD licence but does not yet carry its text.

The model file (5,777,746 bytes) and the card are now committed, as the owner
directed.

### The outlier gate re-admits a sustained change

P3-16 flagged it: `MedianOutlierGate` never remembered a rejected sample, so any
sustained change of more than 20° was rejected on every later frame, for the
rest of the set.

**The rule now:**

- Rejected samples are held aside. Each rejected sample within the threshold of
  the one before extends the run. A sample that disagrees, an accepted sample,
  or an unmeasured frame ends it.
- When the run reaches `outlierReadmitSamples`, the window is re-seeded with the
  run and the completing sample is accepted.
- That number is **3**, provisional: about 100 ms at 30 fps. It must be at least
  2, so a one-frame spike can never re-admit itself, and it must fit in the
  window.
- Still allocation-free, with a fixed cost per sample.

**The price:** the first two frames of a real sudden change are withheld, about
67 ms at 30 fps.

**The residual, stated.** A movement that stays faster than 20° per frame means
each sample disagrees with the one before by more than the threshold, so no run
forms. Such a movement is still dropped while it lasts, and a repetition under
way ends as `INSUFFICIENT_DATA`. It is re-admitted within three frames of
slowing down, where before it was never re-admitted. Whether real movements
reach that speed at real frame rates is what the Phase B readout measures.

**Tests first.** The new tests were written before the rule. Five failed first,
against the old gate:

- the four re-admission tests in `TemporalPipelineTest`;
- a new hold-level test, `a_sudden_sag_that_stays_is_seen_as_a_break_after_a_short_run`
  — the case P3-16 found, a 28° one-frame sag that stays.

The spike, burst and disagreeing-values tests passed before and after. They guard
against re-admitting too eagerly.

**A P2 test was rewritten, on the owner's direction, and is recorded rather than
quietly changed.** `a_rejected_sample_does_not_become_evidence_for_the_next_one`
asserted that a sustained excursion stayed rejected for ten frames. It pinned the
lock-out itself. It is now
`a_rejected_sample_is_not_evidence_until_a_run_of_them_agrees`. It keeps what
the original protected — one wild sample never moves the gate — and changes only
the sustained case.

**`FilterAblationTest` was re-run:** 6 of 6 pass on JVM and JS. Its spikes are
single frames, one every 37 frames, so a run can never form against them.
`the_gate_rejects_the_spikes_and_leaves_the_movement_alone` still requires the
gate to reject exactly the injected spikes, no more and no fewer.

### A flaky allocation guard, made reliable without loosening it

The allocation-growth test added in P3-15 failed in full-suite runs. It read
3.08 MB, and on a later run 2.74 MB, early, against 3.55 MB late, per 50,000
frames. It passed three times out of three when run alone. Two causes:

- **A flaw in the test as I wrote it in P3-15.** The harness runs an untimed pass
  and then a timed pass, and both replayed the same frame numbers. The analysis
  saw time jump back about 1,650 s at the start of every measured window.
- **The JIT.** Running after other test classes, the JIT stopped eliminating
  about 16 bytes of allocation per frame between the early and late windows.
  That is a change in compiled code, not growth over the session.

The fix:

- the frame counter now only moves forward;
- each point is the smallest of three windows;
- the warm-up is long enough that both points see the settled JIT;
- **the tolerance is unchanged.**

Mutation A1 below, a history that grows and is copied as the session runs, is
still caught.

### The Phase B readout, debug builds only

It shows four things:

- the **frame rate sustained** over the last second, counting every result
  MediaPipe delivered;
- the **largest per-frame change** of either side's unfiltered angle over the
  last second — the quantity the gate judges against its 20°. A frame with
  nobody in it breaks the chain;
- the **least and most visible** of the profile's required landmarks on the
  latest frame;
- the **active camera**.

It appears in the camera area's corner and is logged once a second under the
Logcat tag **`KinesisPhaseB`** (`adb logcat -s KinesisPhaseB`).

- **Release builds do not contain it.** The interface is in `main`, the
  implementation is in `src/debug`, and `src/release` has a twin that returns
  `null`. Checked in the built artefacts: the release APK's dex contains no
  `PhaseBStats`, `DebugPhaseBProbe` or `KinesisPhaseB`, and the debug APK
  contains all three. `ReleaseHasNoPhaseBProbeTest` pins it in
  `testReleaseUnitTest`.
- **Zero allocation per frame.** 50,000 frames measure under 1 KB in total,
  where one 16-byte object per frame would be 800,000 bytes. The JVM's counter is
  reached by reflection, because `java.lang.management` is not on an Android
  unit test's compile classpath. The reflective reads cost 40–128 bytes and vary
  slightly, which is why the bound is a total rather than exactly zero. The
  string is built once a second, never per frame.
- **Nothing stored.**
- The core now exposes each side's unfiltered angle on the last frame
  (`rawAngleLeft`, `rawAngleRight`). They are two read-only values, not a
  reduction, and are marked diagnostic; nothing counts or judges from them.

### What "tested" means here

`gradlew assemble allTests :app:testDebugUnitTest`: **BUILD SUCCESSFUL, 1,128
test executions, 0 failures, 0 errors**, and 0 compiler warnings on a forced
recompile of every Kotlin source set, the `debug` and `release` sets included.
P3-16 stood at 1,078, so this batch adds 50:

| Module and target | Before | After |
|---|---|---|
| `:core:analysis`, per shared target (JS and both Android variants) | 209 | 218 |
| `:core:analysis`, JVM | 223 | 232 |
| `:app`, debug unit tests | 96 | 110 |
| `:core:domain`, per target | 33 | 33 |

Separately, `:app:testReleaseUnitTest` — not part of the command above — runs
105 tests with 0 failures. Its count differs from the debug run's because the
probe's tests exist only for debug, and the release-only test only for release.

**Eighteen mutations, each caught by the test written for it,** with every file
restored and verified byte for byte afterwards:

| Mutation | Caught by |
|---|---|
| Back-camera frame mirrored too | `CameraFacingParityTest` — three tests |
| Front-camera frame not mirrored | `CameraFacingParityTest` — three tests |
| Preview assumed never mirrored | `the_front_camera_is_mirrored_twice_and_the_back_not_at_all` |
| Overlay ignores the mirror flag | `the_overlay_lands_on_the_preview_for_both_cameras` |
| Adapter unmirrors the back camera | `CameraFacingParityTest` — three tests |
| A sustained change never re-admitted | `TemporalPipelineTest` — three re-admission tests |
| Re-admission ignores whether rejections agree | `rejections_that_disagree_with_each_other_never_add_up_to_a_readmission` |
| An accepted sample does not end the run | the one-frame spike and short-burst tests |
| A gap does not end the run | `a_gap_restarts_the_run` |
| Re-admitted without re-seeding the window | `after_readmission_the_new_level_is_the_evidence` and one other |
| The same, seen at hold level | `a_sudden_sag_that_stays_is_seen_as_a_break_after_a_short_run` |
| Live analysis grows with the session | `live_repetition_analysis_allocation_does_not_grow_as_the_session_runs` |
| Notice names a model that does not ship | `every_pose_model_shipped_in_assets_is_named_in_the_model_notice` |
| Shipped licence text truncated | `the_apache_licence_shipped_is_the_complete_text` |
| Probe window never expires | `the_largest_angle_change_is_the_worst_side_over_the_last_second` |
| A gap does not break the probe's change chain | `a_frame_with_nobody_in_it_breaks_the_change_chain` |
| Probe allocates per frame | `a_frame_allocates_nothing` |
| Probe publishes every frame | `the_readout_is_published_about_once_a_second_and_names_the_camera` |

**Tests-first, stated as it happened.** The gate's tests were written first and
five failed first; the licence test was written before the notices; the probe's
tests were written before its implementation. `CameraFacingParityTest` was
written before the camera changes. The probe's zero-allocation test did not
compile at first, because `java.lang.management` is not on an Android unit
test's classpath. It was rewritten to reach the counter by reflection, and its
first bound, exact equality with an empty-body baseline, was wrong: the
reflective reads' own cost varies by tens of bytes. It was replaced by the
under-1-KB bound above.

### What is NOT verified — Phase B

Everything in P3-15's and P3-16's lists still stands. Added:

- **Both cameras, separately.** Raise the left arm and the left-side values must
  move, once with the front camera and once with the back. They are different
  code paths: the back path has no mirroring anywhere.
- The skeleton on the body with each camera — which also tests the
  `previewIsMirrored` claim about CameraX.
- Switching mid-set: the first set is saved and a new one starts.
- The model card's conditions: head in frame, within 4 m, one person. Compare the
  front and back cameras at the same distance, since only the back matches the
  model's training.
- Real sudden changes are re-admitted, and the readout's largest per-frame
  change against 20° shows whether fast movements are being dropped.
- The readout itself: its frame rate against the device, and its Logcat output.

**Scope of this entry.** It records a camera choice, a licence read and
recorded, a correction to P3-13, one filter rule changed and one P2 test
rewritten at the owner's direction, a test flaw fixed, and a debug-only
instrument. It makes no accuracy claim of any kind. The PDJ figures above are
Google's.

---

## P3-18 — The first Phase B finding: the live screen crashed on MediaPipe's telemetry

*2026-09-25*

The first run on a real device — the owner's Nothing Phone (2a), model A142,
Android 16 — crashed the moment camera permission was granted. This entry is the
crash as the phone logged it, its cause, the fix, and what a session on the phone
then showed. It is the first entry in this record written from a device.

**Provenance.** The stand-ins, the failure boundaries and the on-device test
below were already in the working tree, uncommitted and with no decision entry,
when this session began. They named this cause. They were not accepted on that
basis: the cause was read from the phone first, and the inherited work was then
checked, corrected in four places (listed below) and mutation-tested.

### The crash, from the phone

`adb logcat -b crash` held two identical `com.kinesis` crashes, six seconds
apart (13:54:30 and 13:54:36 on 2026-09-24), both on the main thread:

```
FATAL EXCEPTION: main   Process: com.kinesis
java.lang.NoClassDefFoundError: Failed resolution of:
    Lcom/google/android/datatransport/runtime/TransportRuntime;
  at com.google.mediapipe.tasks.core.logging.RemoteLoggingClient.<init>(RemoteLoggingClient.java:34)
  at com.google.mediapipe.tasks.core.logging.TasksStatsProtoLogger.<init>(TasksStatsProtoLogger.java:126)
  at com.google.mediapipe.tasks.core.logging.TasksStatsProtoLogger.create(TasksStatsProtoLogger.java:121)
  at com.google.mediapipe.tasks.core.TaskRunner.create(TaskRunner.java:59)
  at com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.createFromOptions(PoseLandmarker.java:200)
  at com.kinesis.camera.PoseLandmarkerSource.<init>(PoseLandmarkerSource.kt:66)
  at com.kinesis.ui.live.LiveHostKt.LiveCameraSession...(LiveHost.kt:320)
  at androidx.compose.runtime.DisposableEffectImpl.onRemembered(Effects.kt:83)
  ...
Caused by: java.lang.ClassNotFoundException: Didn't find class
    "com.google.android.datatransport.runtime.TransportRuntime" on path: DexPathList[base.apk ...]
```

The full log had no `Fatal signal` (no native crash) and no other MediaPipe
error: the landmarker never got as far as a frame. Two other crashes in the same
buffer belong to `com.harman.smartlink`, a different app.

Of the suspects the owner's brief listed, only the missing telemetry classes
fits the trace. A view touched off the main thread does not: the overlay already
invalidates with `postInvalidateOnAnimation()`, which is safe from any thread.
Timestamps, the model file, CameraX binding, `ImageProxy` misuse and a native
crash would all have needed the landmarker to exist.

### Why: P3-13's exclusion removed code MediaPipe links against unconditionally

P3-13 excluded `com.google.android.datatransport` from MediaPipe to keep its
telemetry uploader, and that uploader's `INTERNET` permission, out of the app.
It was checked against the manifest and the build. Nothing ran on a device, and
this failure only exists at run time.

Disassembling `tasks-core-0.10.21` shows why. Creating **any** MediaPipe task
runs `TaskRunner.create`, which always builds `TasksStatsProtoLogger`, which
always builds `RemoteLoggingClient`. That client's constructor links against
datatransport at nine points: `TransportRuntime.initialize`, `getInstance` and
`newFactory`; `CCTDestination.INSTANCE`; `Encoding.of`; a lambda converted to
`Transformer`; `TransportFactory.getTransport`; `Event.ofData`; and
`Transport.send`. It sends to Google's Clearcut service under the log source
`COREML_ON_DEVICE_SOLUTIONS`. There is no flag that turns it off. Nothing else
in `tasks-core` or `tasks-vision` touches datatransport, and neither jar contains
any `java.net`, `javax.net`, OkHttp or `HttpURLConnection` reference. This
logger is MediaPipe's only route off the phone.

### The fix: inert stand-ins, so MediaPipe links and sends nothing

Three ways to make the landmarker construct were weighed.

| Option | Outcome |
|---|---|
| Restore the real library | MediaPipe would upload usage statistics through `transport-backend-cct`, bringing back `INTERNET` and the Clearcut uploader. **Rejected.** It would falsify the privacy notice's "nothing is sent while you are training". |
| Restore only `transport-api` and `transport-runtime` | Does not link: `CCTDestination` lives in the backend. It would also bring an on-disk event store and an upload scheduler. **Rejected.** |
| Inert stand-ins for exactly the linked members | **Chosen.** |

`app/src/main/java/com/google/android/datatransport/` holds Kinesis's own
declarations of the nine members MediaPipe links against, with their exact
descriptors, verified with `javap` against MediaPipe's call sites. Every
factory returns one transport whose `send` does nothing, and `Event.ofData`
returns one shared event, so no payload is kept past the call that would log it.
MediaPipe still builds its usage report in memory (package name and version,
task name, running mode, latency and drop counts), and the report goes nowhere.
The APK contains no uploader, event store, scheduler or network code for it.

**§17.** This is not a dependency and copies no code. The names and signatures
are read from MediaPipe's bytecode, and Google's datatransport is Apache 2.0 in
any case (read from the `transport-api` 3.0.0 and `transport-runtime` and
`transport-backend-cct` 3.1.0 POMs). **Privacy notice: unchanged.** Its statement that nothing is sent
while training was true of the build P3-13 intended, and is true now.
`OfflineManifestGuardTest` is untouched.

**One inherited claim was false, measured.** The stand-in file said that if the
real library ever came back, "the build fails on duplicate classes". With the
P3-13 exclusion removed, the real `transport-api`, `transport-runtime` and
`transport-backend-cct` all return through `tasks-core`, and **`assembleDebug`
still succeeds**. A debug build dexes app and library code separately, so the
duplicate is never detected, and which `TransportRuntime` a phone loads is
undefined. What does catch it, measured with that mutation:
`googles_telemetry_uploader_and_event_store_are_absent` fails,
`OfflineManifestGuardTest` fails, and a release build's dex merge fails
(`Type com.google.android.datatransport.Event is defined multiple times`).
The comment now says so.

### The live screen fails safe

No failure in camera setup, pose model creation, the frame analyser or the
result listener may crash the app. Each is a boundary now:

| Boundary | Thread | What happens |
|---|---|---|
| `PoseLandmarkerSource.open` | main | Returns `Opened` or `Failed(reason)`. Catches exceptions **and `LinkageError`**: this crash was a `NoClassDefFoundError`, and a missing native library is an `UnsatisfiedLinkError`. Neither is an `Exception`. |
| `LiveCamera.bind` listener | main | Camera service, 4:3 binding and any other failure is reported through the existing `onUnavailable`. |
| `LiveCamera.analyse` | CameraX analysis | Anything thrown stops analysis through `source.fail`. |
| Result listener (`deliver`) | MediaPipe | Same. |
| MediaPipe error listener | MediaPipe | Same. **Previously it discarded errors silently.** |

Every failure is logged under `KinesisLive` (`adb logcat -s KinesisLive`) and
reported once. The host then shows the existing `LiveUnavailableScreen` ("LIVE
ANALYSIS STOPPED" or "UNAVAILABLE"), which takes the camera session out of
composition and so unbinds the camera. No zero count is left on screen as if
all were well.

**A behaviour change, stated.** A `MediaPipeException` in the analyser used to
drop one frame silently. It now stops analysis for the set. That trades a
recoverable glitch for a visible stop, which is the owner's instruction: never
swallow an error. If device use shows transient errors, recovering from them
is a separate decision.

**Real-time path.** The happy path gains `AtomicBoolean` reads and no
allocation. Only a failure allocates, once, for its message. The two bitmap
allocations per frame that P3-15 recorded are unchanged.

**Timestamps (suspect c).** They are unchanged and safe by construction.
MediaPipe's monotonic-timestamp rule applies per graph, and a camera switch
builds a new landmarker (P3-17). The analyser also drops any frame whose
timestamp does not advance.

### Tests

**JVM: `MediaPipeTelemetryStandInTest`, five tests.** As inherited, it checked
only that datatransport *class names* resolve, and only in the jar holding the
logger. A MediaPipe upgrade calling a new method on an existing class would
have passed it and crashed a phone. It now reads the constant pool of every
class in `tasks-core` and `tasks-vision` (JVMS §4.4), which is what the device's
linker resolves, and checks each field, method, interface method and lambda
target, by name and exact descriptor. It also pins the surface to the nine
members above, so an upgrade that needs more is reviewed ("does the new call
send anything?") rather than absorbed.

**On-device: `PoseLandmarkerOnDeviceTest`, four tests**, run by
`gradlew connectedDebugAndroidTest`. They cover: the bundled model is in the
APK; the landmarker opens from it and MediaPipe answers a 480×640 frame stamped
with that frame's timestamp; a missing model is reported, not thrown; and the
overlay accepts frames from a background thread while attached to a real window.

**Mutations.** Every file was restored and verified byte for byte afterwards:
backups compared with `cmp` and `git diff --no-index`, and tracked files
restored with `git checkout`.

| Mutation | Result |
|---|---|
| `Event.ofData` loses `@JvmStatic`; `newFactory` takes `CCTDestination` | JVM member test fails, naming both descriptors. The inherited class-name test would have passed. |
| `TransportRuntime` stand-in removed | On the phone, `the_bundled_model_opens_and_runs_a_frame` fails with the phone's own error, reported as a message, not a crash. |
| The same, plus `open`'s `LinkageError` catch removed | The `NoClassDefFoundError` escapes `open` with the crash log's frames (`RemoteLoggingClient.<init>:34` … `createFromOptions:200`). The boundary is what prevents the crash. |
| `requestLayout()` added to the overlay's `submit` | Background-thread test fails with `CalledFromWrongThreadException`. |
| `invalidate()` in place of `postInvalidateOnAnimation()` | **Not caught.** On a hardware-accelerated window Android does not check the thread for `invalidate()`. The test's comment now says so. |
| P3-13 exclusion removed | Debug build succeeds. Caught by the absence test, `OfflineManifestGuardTest` and the release dex merge, as above. |

**The four corrections to the inherited work:**

1. The JVM guard was raised from class names to members, across both jars.
2. The missing-model test also passed with the P3-18 fault back, because
   MediaPipe builds its logger before loading the model. It now asserts the
   failure is about the model.
3. The background-thread test's comment claimed more than the test catches. It
   now says what the test catches and what it cannot.
4. The stand-ins' duplicate-class claim is corrected, as above.

### What "tested" means here

`gradlew assemble allTests :app:testDebugUnitTest`: **BUILD SUCCESSFUL, 1,133
test executions, 0 failures, 0 errors.** P3-17 stood at 1,128. The five new
tests are the JVM stand-in guard.

| Module and target | Before | After |
|---|---|---|
| `:core:analysis`, per shared target (JS and both Android variants) | 218 | 218 |
| `:core:analysis`, JVM | 232 | 232 |
| `:app`, debug unit tests | 110 | 115 |
| `:core:domain`, per target | 33 | 33 |

The core modules' test tasks were up to date, because no core source changed.
`:app`'s tests ran, and the release variant compiled and dex-merged fresh with
the stand-ins in place.

`gradlew connectedDebugAndroidTest` on the A142: **4 tests, 0 failures.**

Compiler warnings were not checked in this entry.

### Verified on the phone

`com.kinesis`, debug, with no ID suffix. It was installed with `installDebug`,
camera granted with `pm grant`, and driven by `input tap` and `screencap`.
The screenshots show the owner, so they are kept out of git, in the ignored
`local/phase-b/`, and are described here in words.

- **The crash is gone.** The live screen (Squat) ran about seven minutes on the
  front camera (00:04–00:11) and about five on the back. The back run ended when
  the test reinstall stopped the app, not by any fault. Throughout: no
  `com.kinesis` entry in the crash buffer, no `FATAL EXCEPTION`, no `Fatal
  signal`, no ANR, nothing under `KinesisLive`, and one process ID.
- **The preview shows on both cameras.** The switch works mid-set, and the
  readout names the camera.
- **The Phase B readout updates.** The probe logs under `KinesisPhaseB` about
  once a second. This closes P3-17's "Logcat output" item.
- **The skeleton lies on the body with both cameras**, including frames where
  the person is off-centre (a full-body frame on each camera, visibility
  0.83–1.00). This agrees with `previewIsMirrored` for both cameras.
  It is not the left/right check (see below).
- **Frames arrive, and the rate is measured, not explained:** front 7.8–16.4
  fps, back 3.9–15.0 fps, from screenshots and the probe's log. That is well
  below 30. Light, the device and the per-frame bitmap work are all candidates;
  none was measured.
- **Switching camera saves the first set.** The app's Room database, read with
  `run-as`, holds one session and one squat set started at 00:04:22 (the front
  run), with 12 candidate repetitions: 11 `INSUFFICIENT_DATA` and 1 `ABORTED`.
  None is valid, matching the 0 shown. The back camera's set is absent because
  the reinstall killed the process with the screen open. A set is written on
  leaving the screen, so that is expected.

### Found on the phone, not fixed

- **The skeleton is drawn outside the camera area**, over the readout and the
  END SET control (the first live frame, upper body in shot, hips and legs
  drawn below the camera area), when MediaPipe places landmarks outside
  the frame. The preview and overlay `AndroidView`s sit in a 3:4 `Box` with no
  `clipToBounds()`, and Compose does not clip an `AndroidView`'s drawing to its
  bounds. `Modifier.clipToBounds()` on that box is the likely fix. It is neither
  applied nor verified.
- **The phone was upside down for the whole run.** With a USB cable in the
  bottom port, that is how it stands. Android's orientation listener proposed
  `ROTATION_180` while the live screen's portrait lock held the display at
  `ROTATION_0`. So both the preview and the frames MediaPipe analysed showed the
  person inverted. The image evidence agrees: lettering on a shirt was flipped
  vertically on the mirrored front camera and rotated 180° on the back.
  MediaPipe tracked the inverted body, but its accuracy on inverted people is
  not established. Any measurement that relies on the image's vertical axis
  would be inverted; which ones do has not been audited here. For the owner:
  the portrait lock could become `sensorPortrait`, or the setup assistant (§10)
  could refuse an inverted phone.
- **The saved set is no evidence about rep counting.** The squats were done
  front-on, the Squat profile requires side-on, and the frames were inverted.

### What is NOT verified — Phase B

Everything in P3-15's, P3-16's and P3-17's lists still stands, except the items
marked verified above. Added:

- **The back camera with the phone upright.** It could not be propped upright
  with the cable in.
- **The left/right check** (raise the left arm, once per camera). Not done.
  Skeleton placement does not test it.
- **The failure screens on the device.** No failure occurred, so neither "LIVE
  ANALYSIS STOPPED" nor "UNAVAILABLE" has been seen on the phone. On the device,
  only `open` has been tested, by the instrumented test. The analyser,
  result-listener, error-listener and binding boundaries have no test that
  triggers them.
- Rep counting under the profile's required view, with the phone upright.
- Sessions longer than a few minutes, and the effect of heat on frame rate.

**Scope of this entry.** It records a crash read from a device, its cause, a fix
that keeps MediaPipe silent, failure boundaries, a JVM and an on-device guard,
one false claim corrected, and one device session with its findings. It makes
no accuracy claim of any kind.


---

## P3-19 — Phase B without the camera: screenshots out of git, the overlay clipped

*2026-09-25*

The P4 work that does not need the owner exercising in front of the camera, at
the owner's direction, in their order. The phone stays connected by USB for
automated tests only. Each part below was committed as it landed. Camera and
exercise checks are listed as pending at the end.

### 1. Screenshots of the owner stay out of git

P3-18's commit put seven screenshots in `docs/phase-b/`. They show the owner's
face, room and account email. The commit was unpushed, so it was amended: the
images moved to `local/phase-b/`, `local/` is in `.gitignore`, and P3-18
describes each screenshot in words. `git log --all` lists no image file in any
commit. **Standing rule from here:** device screenshots are saved only under
`local/`, and this record describes them. The same goes for any image a test
captures from the camera.

### 2. The skeleton is clipped to the camera area

P3-18 found landmarks below the frame drawn over the rep counter and END SET.
**Cause, read from Compose UI 1.7.6's bytecode:** `AndroidViewsHandler` and
`ViewFactoryHolder`, the holders every `AndroidView` sits in, both call
`setClipChildren(false)`. An embedded view is therefore never clipped to its own
bounds, and the overlay draws wherever a landmark falls. **Fix:** `clipToBounds()`
on the 3:4 box in `LiveCameraLayout`, which both the repetition and the hold
screen use.

**Guard: `LiveScreenClippingTest`, on the phone, two tests, one per screen.** Each
renders the real screen, hosted as `MainActivity` hosts it (theme, surface, the
shell's column, the debug readout on), locked to portrait. It gives the overlay a
figure whose shoulders are in the frame and whose legs are below it, and compares
the window's pixels before and after, captured with `PixelCopy` from the real
hardware-rendered surface. It requires changed pixels inside the box, so it can
see the skeleton, and none outside.

- **Without the clip:** 8,477 pixels (repetition) and 6,931 (hold) outside the
  box `Rect(0, 320 – 1084, 1765)`. **With it: 0.** File restored byte for byte.
- **Written first and failing first, but not at the first attempt.** The first
  version passed without the fix, because its "legs below the frame" were at
  y = 1.3. The overlay measures y in frame *widths* (P3-16), so a portrait 4:3
  frame ends at y = 4/3, and 1.3 is still inside. Two wrong leads were tested
  and ruled out before a saved capture showed the legs on the box's bottom edge:
  hosting the screen as the app does, and binding a live camera to the preview.
  Neither changed the result. The camera binding was removed from the test, and
  its captures, which showed the room, were deleted from the phone and the PC.
- One run went to landscape because the phone was lying on its side. It still
  failed without the clip, but the test now locks portrait so it matches the
  live screen.

**Pending, for the camera session:** see the clipped skeleton on the live screen
with a person partly out of frame.

### 3. An upside-down phone is supported (owner's decision)

**Decision.** The live screen follows the phone either way up, and never goes
landscape. The owner's phone stands upside down whenever its USB cable is in,
and P3-18 found that the portrait lock then showed the person inverted, and
analysed them inverted.

**The owner asked for `SCREEN_ORIENTATION_SENSOR_PORTRAIT`. It would not have
worked on their phone, measured.** `dumpsys window` reports
`mAllowAllRotations=false`. Android's rotation policy then refuses the sensor's
180° reading for every sensor-driven orientation except `FULL_SENSOR` and
`FULL_USER`, and those also allow landscape. The owner chose between three
options: an explicit tilt listener, `FULL_SENSOR`, and sensor portrait anyway.
**They chose the tilt listener.**

- **`LiveOrientation.requestFor`** turns `OrientationEventListener`'s tilt into
  fixed `PORTRAIT` within 60° of upright, fixed `REVERSE_PORTRAIT` within 60° of
  upside down, and "keep the current one" in the two landscape bands and when
  the phone lies flat. The bands never meet, so the screen cannot flap. Fixed
  orientations are honoured regardless of `mAllowAllRotations`, which is measured
  below. Like sensor portrait, this ignores the user's rotation lock while the
  live screen is open.
- **No camera restart, so the set does not end.** 0° ↔ 180° is not a
  configuration change, and nothing recomposes. `LiveCamera` registers a
  `DisplayListener` and gives each supported display rotation to both use cases
  with `setTargetRotation`, without rebinding. `PreviewView` already redraws
  itself on the same display change: it has its own `DisplayRotationListener`,
  read from camera-view 1.4.1's bytecode. The listener is removed on `close()`.
- **Frame rotation and mirroring.** The rotate-then-mirror step moved unchanged
  from `LiveCamera` into `FrameUpright`, so it can be tested and, in part 5,
  benchmarked. The mirror is left to right in the upright frame, so it is the
  same selfie mirror either way up. **How mirroring is done is unchanged**, and
  `CameraFacingParityTest` is untouched and green.
- **The overlay** draws in upright-frame coordinates, which follow the display,
  so it needs no change.

**Measured on the phone.** With the target rotation turned over and no rebind,
the back camera went from `rotationDegrees` 90 to 270, and the front from 270 to
90, exactly half a turn each. An explicit reverse-portrait request turned the
display to `ROTATION_180`, and both use cases followed it there and back.

**Tests.**
- `LiveOrientationTest` (JVM, 9 tests) pins the tilt bands, the separation
  between them, the supported rotations, and the transform's geometry: quarter
  turns, the upside-down relation, and left-to-right mirroring.
- `LiveRotationOnDeviceTest` (phone, 3 tests) pins:
  - the production bitmap transform against that specification at all 8
    rotation and mirror combinations;
  - both real cameras' 180° shift;
  - the whole path, with a real display turned upside down and back under a
    bound `LiveCamera`.

**Mutations, all caught, with files restored byte for byte:**
- The analysis update dropped from `followDisplay`. Caught at the first check,
  because the phone lay on its side, the bind ran while the display was still
  landscape, and only the listener corrects that.
- Mirroring before rotating: 90° mirrored landed at x = 0.75 against 0.25.
- The display listener never registered.
- Bands widened until they meet: 2 JVM tests fail.

**Pending, for the camera session:** turn the phone over with the live screen
open, on each camera. The screen, preview and skeleton must turn with it, the
person must stay upright in the preview, and the set must carry on.

### 4. The P3-18 squat set: two bugs found and fixed, one behaviour for the owner

The set was pulled from the phone again with `run-as`: one set, 12 records.
**Every `repIndex` was 0. Durations ran from 0.1 s to 93.5 s**, with 21.8 s and
6.3 s among them. 11 were `INSUFFICIENT_DATA` and 1 `ABORTED`, and
`attributedSide`, descent time and ascent time were empty in all of them. Each
suspect was read in the code, then reproduced in a JVM test before any fix.

**Bug 1: `repIndex` numbering.** `RepRecord` documents `repIndex` as "position in
the set, counting from 1". `BilateralRepTracker` set it to the count of *valid*
repetitions so far. So every record before the first valid one was 0, and a
rejected record repeated the number of the valid one before it. A set of
aborted, valid, valid, incomplete was numbered `[0, 1, 2, 2]`. **Fixed:** a
separate record counter numbers every record, and `repetitionCount` still counts
only valid ones. This matters beyond display: `SessionMapping` sorts a stored
set's repetitions by `repIndex`, and that sort now means chronological order.
Records saved before this fix, including the phone's 12, keep their old numbers.

**Bug 2: left and right cycles paired across any gap.** Under `SIMULTANEOUS`
semantics, one side's finished cycle waited for the other side's next cycle
however late it came, and the pair became one record from the earlier start to
the later end. That produced the 93.5 s, 21.8 s and 6.3 s records. **Worse,
reproduced:** if each knee is seen for a different repetition, two valid
one-sided halves a minute apart became **one VALID, counted squat** spanning
63.6 s. On the phone the halves happened to be invalid, so nothing was counted,
but the count could be inflated. **Fixed:** two halves pair only if their time
spans overlap. Otherwise the older half is reported alone as
`INSUFFICIENT_DATA`, the existing treatment for a half-seen repetition, and the
newer waits for its own partner. Normal pairing is untouched; a test with the
right knee four frames behind the left still gives one valid repetition.

**The max-duration rule has no bug.** `RepFsm` abandons one side's cycle as
`INCOMPLETE` once it runs past `maxRepDurationMs`, 15 s for the squat. No phone
record was `INCOMPLETE`, so no side overran: the long records were pairing
artefacts, and now every record stays inside the limit. The empty descent and
ascent times are expected. A cycle abandoned before it reaches the bottom has no
ascent, and one abandoned mid-descent has no descent time.

**Guard: `RepRecordIntegrityTest`**, 4 tests in the shared core, run on JVM, JS
and Android. Before the fix three failed:
- numbering came out `[0, 1, 2, 2]`, not `[1, 2, 3, 4]`;
- a one-sided record then a valid one came out `[0, 1]`;
- the minute-apart halves made one counted VALID squat 63,591 ms long.

The fourth, ordinary overlapping pairing, passed before and after. After the fix
all 236 `:core:analysis` JVM tests pass. These failures are the mutation check:
they are the pre-fix code.

**What a single bad frame does: it abandons the whole repetition.** Read from
the code, not changed:
- A frame in which a required landmark falls below the visibility threshold, a
  frame in which MediaPipe found nobody (`onNothingSeen`), and **a single sample
  the outlier gate rejects** all reach the tracker as `NaN` for that side.
- `EmaSmoother` resets on `NaN`, and `RepFsm.onUnmeasurableFrame` ends the cycle
  in progress as `INSUFFICIENT_DATA`, then returns to `IDLE`.
- `IDLE` begins a new repetition only at the starting position, so the rest of
  the movement is lost as well.
- Under simultaneous pairing, one side's lost half makes the whole repetition
  `INSUFFICIENT_DATA`.
- A frame the camera never delivers does no harm; it is only a longer time step.

With the phone inverted, the subject front-on and 8–16 fps, this is the likely
main cause of 11 of 12 records being `INSUFFICIENT_DATA`. That is a hypothesis;
the frame-level data was not kept.

**Proposal, not implemented. It waits for measured dropout lengths.**
1. In `RepFsm`, an unmeasurable frame mid-repetition starts a gap instead of
   ending the cycle. If measurement resumes within a tolerance `maxGapMs`, the
   cycle continues.
2. Past the tolerance, the cycle is abandoned as today, but with `endMs` at the
   last measured frame, not at the frame that exceeded the tolerance.
3. The smoother keeps its state across a bridged gap instead of resetting.
4. `SignalResampler` already bridges internal gaps within a cycle, so the
   resampled record needs no change.
5. A rejected outlier counts as a gap sample, so one spike no longer costs a
   repetition.
6. Optionally, `RepRecord` records how many frames were bridged, so a bridged
   repetition is distinguishable. That is a schema change and needs its own
   decision.

`maxGapMs` must come from the camera session's measured distribution of
unmeasurable runs, so part 5's readout now logs each side's run lengths. The
decision belongs to the owner, and it touches Level 1 measurements, so it gets
its own entry when it is made.

### 5. The frame rate: MediaPipe's inference is the ceiling

Live analysis ran at 4 to 16 results a second against a target of about 30.
Part 5 builds what one camera session needs to explain that. It measures what
can be measured without the camera, and fixes the one clear waste on our side.

**A non-debuggable build that keeps the readout.** Build type `profiling` is
debug-signed and not debuggable. It shares the probe from `src/debug`, and its
library modules fall back to their release variant. It is never shipped.
`-Pkinesis.testBuildType=profiling` points the connected tests at it. `dumpsys
package` confirmed the installed build had no `DEBUGGABLE` flag.

**Benchmark: `FramePipelineBenchmark`, on the phone, opt-in with `-e
kinesisBenchmark true`.**
- **Conversion** is timed on a synthetic 640×480 RGBA frame. `toBitmap` is
  CameraX's own default method, so its real native copy runs.
- **MediaPipe** is timed with one frame in flight at a time and the production
  options (`LIVE_STREAM`, one pose, the default CPU delegate). It records how
  long `detectAsync` holds the analysis thread, and the hand-over-to-result
  latency, which bounds results per second.
- **Inputs.** A blank 480×640 frame, and a person: a crop of the owner's P3-18
  front-camera screenshot, turned upright and scaled to 480×640. That image
  stays in `local/bench/` and on the phone, and is never committed.
- **Models.** The full model, `pose_landmarker_full.task` (9,398,198 bytes,
  SHA-256 `4eaa5eb7…c4ad`), was downloaded from Google's model storage into
  `local/models/`. It is not committed and the app is not switched to it.
- **GPU.** The GPU delegate was measured for information only. It changes the
  numerics, so it is the owner's decision, and the app is unchanged.

**Medians, 150 frames for MediaPipe and 200 for conversion.** Both runs came
before the thermal check below, so their thermal state is unknown:

| Stage | Debug build | Profiling build |
|---|---|---|
| `toBitmap` | 0.47 ms | 0.43 ms |
| `toBitmap` + upright, back camera | 3.96 ms | 3.77 ms |
| `toBitmap` + upright, front camera (mirrored) | 3.88 ms | 3.76 ms |
| `detectAsync` hand-over, lite, person | 2.48 ms | 1.45 ms |
| **MediaPipe lite, person: hand-over to result** | **58.46 ms** | **56.94 ms** |
| MediaPipe lite, empty frame | 59.21 ms | 56.41 ms |
| MediaPipe full, person | 73.53 ms | 66.29 ms |
| MediaPipe full, empty frame | 61.37 ms | 59.19 ms |
| MediaPipe lite, person, GPU delegate (not adopted) | 51.85 ms | 45.33 ms |

**Reading.**
- At about 57 ms a frame, MediaPipe's CPU inference caps this phone at about
  **17 results a second**. That is consistent with the 12–16 seen live.
- The analysis thread's share, about 3.8 ms to convert and 1.5–2.5 ms to hand
  over, fits well inside a 33 ms frame.
- Debuggability is not the cause: the non-debuggable build is only a few
  percent faster.
- The full model is slower. The GPU delegate would reach about 22 a second.
  Neither reaches 30 on this phone.
- Unexplained: an empty frame, where only the detector runs, costs as much as
  tracking a person.

**The waste fixed, without changing what MediaPipe sees.** Each frame allocated
two fresh 1.2 MB bitmaps, CameraX's `toBitmap` and the rotated copy.
`UprightFrames` keeps one of each. It copies CameraX's tightly packed RGBA plane into the kept bitmap, and
draws the same rotate-and-mirror matrix into a kept output with an unfiltered
`SRC` paint, which is what `Bitmap.createBitmap(…, filter = false)` does. Any
other frame layout falls back to CameraX's `toBitmap`. `PoseLandmarkerSource`
reuses the `MPImage` wrapper while the bitmap is the same one. Two phone tests
pin the change:
- **Pixel-identical** to the allocating path at all 8 rotation and mirror
  combinations, including the frame after, once the buffers hold the previous
  frame.
- **MediaPipe copies the pixels inside `detectAsync`.** That was read first from
  its bytecode: a bitmap goes to the native `nativeCreateRgbaImage`. Then it was
  measured: a person frame blacked out the instant `detectAsync` returned was
  still found in **40 of 40** results, while a frame submitted already black
  found nobody. That test needs the local person image and is skipped without
  it.

**The kept buffers, measured, and one claim corrected.** Later separate runs
drifted by more than a millisecond between identical cases. The phone then read
thermal status 2 ("moderate", skin 43 °C, CPU 63.5 °C) while charging over USB,
and it was in active use, with another app at 158% CPU. So the benchmark now
interleaves the old and new converter frame by frame, so that both see the same
conditions. It counts ART's bytes allocated and garbage collections for each,
records the thermal status, and writes its results to a file on the phone,
because a first run lost lines to this phone's fast-rolling Logcat.

It was run once more with the owner leaving the phone alone. It was still at
**thermal status 2** (skin 41 °C), with a background app at 120% CPU, so the
absolute numbers read pessimistic. The interleaved comparison is fair: 300
frames each, medians.

| Converter | Debug, back camera | Debug, front | Profiling, back | Profiling, front |
|---|---|---|---|---|
| Allocating (`toBitmap` + upright) | 4.23 ms | 4.24 ms | 4.08 ms | 4.37 ms |
| Kept buffers | 4.08 ms | 4.13 ms | 4.03 ms | 4.21 ms |
| **Garbage collections per 300 frames**, allocating / kept | **6 / 0** | 6 / 0 | 6 / 0 | 6 / 0 |
| Java-heap bytes per frame, allocating / kept | 3 KB / 0 | 3 KB / 0 | 3 KB / 0 | 3 KB / 0 |

- **No meaningful speed gain**: 0.05 to 0.16 ms a frame.
- **The gain is collections.** The allocating path forced a garbage collection
  about every 50 frames, roughly every 1.7 s at 30 frames a second. The kept
  buffers forced none.
- **A claim of this part's first version was wrong and is corrected here.** It
  said the old path made "about 72 MB a second of garbage". A bitmap's pixels
  live in native memory on this Android, not the Java heap, so the Java heap saw
  only 3 KB a frame. The 1.2 MB buffers were native allocations and frees. Their
  cost showed up as the collections above, which native-allocation pressure
  triggers.

**MediaPipe in the same run** (thermal status 2, background load), medians of 150:

| | Debug | Profiling |
|---|---|---|
| Lite, person | 57.15 ms | 54.99 ms |
| Lite, empty frame | 60.73 ms | 58.86 ms |
| Full, person | 75.38 ms | 69.96 ms |
| Full, empty frame | 59.25 ms | 49.85 ms |
| Lite, person, GPU delegate | 62.37 ms | 59.40 ms |

The lite model's figure repeats: about 55–57 ms, a ceiling near 17–18 results a
second. **The GPU delegate was slower here than the CPU**, where the first runs
had it 11 ms faster. Its benefit on this phone is therefore not established,
which is one more reason it is not adopted.

**The readout now explains the frame rate in one session.** It shows three lines
about once a second, also logged under `KinesisPhaseB`:
- frames the camera delivered, frames handed to MediaPipe, and results back;
- conversion time (mean and max), hand-over time, and MediaPipe's
  hand-over-to-result latency (mean and max);
- the angle change and visibility as before, plus **each side's runs of
  unmeasurable frames**: how many, and the longest in frames and milliseconds.
  Those runs are part 4's gap-tolerance input.

The capture numbers are counted on the analysis thread and published through
volatile fields, so the two threads share nothing mutable. Latency comes from a
16-slot, lock-free, allocation-free ring of hand-over times keyed by frame
timestamp, `HandOverTimes`. Release builds have no probe, and the timing hooks
are no-ops there.

**Tests.**
- `PhaseBStatsTest`, 11: the new windows, gap runs and readout fields, and zero
  allocation per frame.
- `HandOverTimesTest`, 3.
- `LiveRotationOnDeviceTest`'s live-camera test now also requires, on the real
  camera, that frames reached the probe, were sent, and got a timed answer.

**Not changed, per the owner:** the camera's auto-exposure frame-rate range.
That waits for the camera session.

### 6. Firestore rules, tested on the emulator: they refused every upload

`firestore.rules` had never been run. P3-14 wrote it, and the owner was to paste
it into the console by hand. It is now tested by `tools/firestore-rules/`: 14
rules unit tests on the Firebase Local Emulator, against a `demo-` project that
cannot reach the real `kinesis-5b97b`. Run them with
`cd tools/firestore-rules && npm install && npm test`.

**§17 check. Development tools only; nothing enters the APK.**
- `firebase-tools` 14.27.0 is MIT. It downloads Google's Firestore emulator on
  first run; that is a local development binary under Firebase's terms, and it
  is not redistributed.
- `@firebase/rules-unit-testing` 4.0.1 and the `firebase` JS SDK 11.10.0 are
  Apache 2.0.
- The test runner is Node's own `node:test`, so no test framework was added.
- `node_modules` and the emulator's log are ignored. `package-lock.json` is
  committed, so the versions are pinned.

**Bug found: a signed-in user could not upload anything.** The emulator's
evaluation log gave `Null value error` at line 61, column 27, which is
`d.sessionId == sessionId` inside `isWellFormedSession()`. That function is
declared outside the `match` block, so it cannot see the path variable
`sessionId`, which reads as null and fails the create. **Had these rules been
published, every upload would have been refused**, and `SessionSyncWorker`
would have retried each one forever. Its only answer to a failed push is
`Result.retry()`. **Fixed:** the path's `sessionId` is passed in as a parameter.

**Gap found: a retried upload would be refused.** The app uploads with `set()`
on a fixed document id and retries every failed push. If the server commits an
upload but the reply is lost, the retry is a second, identical `set()`. The rules
evaluate that as an *update*, and updates were refused, so the retry failed
forever too. **Changed:** an update is allowed only when the new document equals
the stored one, `request.resource.data == resource.data`. That edits nothing.
Any difference is still refused, and delete is still refused. **This is a policy
change to the security boundary.** The owner is told of it when asked to
publish, and can refuse it.

**What the 14 tests hold:**
- **Signed out:** no read, no list, no add.
- **Signed in:**
  - can add, read and list their own sessions;
  - can neither read nor list another user's;
  - cannot add to another user's account;
  - cannot edit their own session, by field update or by a changed `set()`;
  - cannot delete their own session;
  - cannot add a malformed session: `sessionId` differing from the path, no
    `sets`, a string offset, a non-numeric start, or an empty id;
  - cannot touch anything outside their sessions.
- **A retried upload:** an identical one succeeds, one differing in any field is
  refused, and an identical one into another user's account is refused.

**Tests first.** Before the fixes, "adds a session of their own" and the
identical retry both failed. **Mutations**, with the file restored byte for
byte: an update rule of `true` and a delete rule of `true` together failed three
tests, "cannot edit", "cannot delete" and "differs in any way".

**Checked with the owner and on the phone:**
- **The console held the broken P3-14 rules.** They had been published since
  2026-09-23 (the P3-14 follow-up), so every upload from the app had been
  refused. The P3-18 session was not in Firestore. **The owner published the
  fixed file** on 2026-09-26, accepting the identical-retry allowance.
- **The P3-18 session then reached the owner's account.**
  - Before the fix, the phone's Firestore cache held no documents and no
    pending writes, and WorkManager held no sync job.
  - After the fix, the cache holds the server's copy of
    `users/<uid>/sessions/f6ec3996-7046-4343-bcbc-99380e9881a1`, the P3-18
    session's id, with no pending write. The app logged `KinesisSync: pulled 1
    sessions from the account`. **The owner confirmed the document in the
    console.**
  - The upload itself was not observed. It most likely happened when the owner
    opened the app to grant Health Connect, minutes after publishing, through
    the sign-in merge's `pushMissing`, on a build that did not yet log.
- **Sync outcomes are now logged under `KinesisSync`**: a push succeeded, a push
  failed with Firestore's code and message, a pull succeeded with its count, a
  pull failed. Session ids and counts only, never contents. Until now,
  `SessionMerge`'s caller discarded the result, and a refused upload left no
  trace in the app. That is how every upload could fail for three days unseen.

**Still owed:** a session recorded now, with the fixed rules, uploading on its
own through `SessionSyncWorker`. That comes with the camera session's first
new set.

### 7. Health Connect on the phone: the client was out of date, and a failure looked like an empty day

**Found.** On the owner's Android 16 phone the History screen said "Health
Connect needs updating". Nothing on the phone needed updating.
- **The client library, 1.0.0-alpha11, predates Android 14**, where Health
  Connect became part of the platform. Measured on the phone:
  `HealthConnectActivityContextSource.availability()` returned
  `Unavailable(PROVIDER_UPDATE_REQUIRED)`. The phone has both the old
  `com.google.android.apps.healthdata` package and the platform module.
- **The permission dialog could not have appeared anyway.** The manifest had no
  permission rationale, which Health Connect requires before it shows its
  dialog: the `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS` activity-alias on
  Android 14 and later, and `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
  before.

**The client upgrade, to 1.1.0-beta01, with a §17 check.** The stable 1.1.0,
and every release from beta02, requires compileSdk 36 and Android Gradle Plugin
8.9.1. That was read from each release's AAR metadata; the project is on
compileSdk 35 and AGP 8.7.3. **beta01 is the newest release that fits the
current toolchain.** Moving to 1.1.0 waits for an AGP upgrade, which is the
owner's decision; P1-1's lint and JDK 25 note is tied to the same AGP version.
- **Licence:** Apache 2.0.
- **Manifest:** the library adds only its own bound service, as before. It adds
  no permission and no network capability; `OfflineManifestGuardTest` passes.
- **Minimum SDK:** 26, covered by P3-11's `overrideLibrary`.
- **New transitive artifacts.** `connect-client-proto` is Apache 2.0.
  `connect-client-external-protobuf` is **BSD-3-Clause**, Google's Protocol
  Buffers repackaged. It is permissive. It is *named* in the in-app licences
  list, but its BSD notice text is not shipped. Like libyuv's (P3-17), that
  text is owed before any build is distributed; part 9 corrects this entry's
  first wording.
- **API.** `sdkStatus` became `getSdkStatus`. The JVM test fake dropped two
  interface methods that no longer exist.

**The rationale.** `HealthPermissionsRationaleActivity` is declared for both
routes. Every sentence of its text was checked against the code:
- it reads three metrics, for one day;
- it shows them on the History screen, and the analysis never uses them;
- it never writes to Health Connect;
- it never stores or uploads the values: no storage or sync code refers to
  them.

**A failed read looked like an empty day.** `read()` turned any exception from
Health Connect into `DailyMetric.NoData`, so "no data" on screen could hide a
refusal or a failure. That contradicts the owner's requirement that the panel
name the real reason. `DailyMetric.ReadFailed` now exists. The panel shows it
as "could not be read", and it is logged under `KinesisHealth`. Internal bugs
(`NullPointerException` and the other three) still propagate, which
`HealthConnectExceptionTest` pins. `read()` became the top-level `readMetric()`
so the JVM can test it. Tests first: `HealthMetricReadTest` returned `NoData`
where `ReadFailed` was required, before the fix.

**On the phone, after the fix.**
- `HealthConnectOnDeviceTest`, three tests:
  - availability is `Available` on Android 14 and later;
  - both rationale routes resolve;
  - today reads without failure, with the app in the foreground, since Health
    Connect refuses background reads.
  The first two failed before the fix, measured.
- **The owner granted all three permissions** through the app's own
  `[ GRANT ACCESS ]`. `dumpsys package` shows `READ_STEPS`,
  `READ_ACTIVE_CALORIES_BURNED` and `READ_HEART_RATE` granted.
- **The panel shows STEPS, ACTIVE KCAL and MEAN BPM, each "no data"** (01:31,
  the local day 90 minutes old). The reads succeeded and returned no records;
  none failed.
- **Why, measured with a one-off probe that was not kept.** Over the last 30
  days Health Connect holds **135,728 steps**, from its own phone step source
  and Google Fit. It holds **no active-calorie and no heart-rate records at
  all**.
  - Today's steps had simply not been recorded yet.
  - Calories and heart rate will read "no data" on this phone until something
    writes them, such as a wearable or an app that records them. That is the
    true reason, and the panel states the state truthfully.

**Pending, for the owner:**
- a steps figure later in a day with walking;
- opening the rationale screen from Health Connect's own settings;
- the stable client, after an AGP upgrade.

### 8. The JVM/JavaScript parity suite, and what it found on its first run

CLAUDE.md §18 has always required golden-file parity on the JVM and JS targets.
It did not exist until now: the facade was a P1 stub in `jsMain` that threw on
every call, and the JVM could not run it at all.

**The facade, finished as far as the engine allows.**
- **It moved to `commonMain`.** `@JsExport` is allowed in common code and
  ignored off JS, so the JVM, both Android variants and JS all run the same
  facade. The generated `.d.ts` is unchanged in kind: strings, numbers and
  `Float64Array` only.
- **`configure({"id": …})` runs the app's own live analysers.** It uses
  `LiveRepetitionAnalysis` for a repetition exercise and `LiveHoldAnalysis` for
  a hold, and adds nothing analytical of its own.
- **Two methods were added, `stateJson` and `finishSet`.** A hold only reports
  when the set ends, and per-frame phases are what the parity contract names.
  `stateJson` reports each side's phase separately (`LiveRepetitionAnalysis`
  gained two getters for it), and the display phase as a named reduction beside
  them.
- `analyzeRep` and `computeBaseline` still throw; they wait for P6.
- The JSON is written by hand, because the core takes no JSON library for one
  field in and a few flat records out. A `NaN` value becomes `null`.
- **Contract amended.** CONTRACTS.md §5 said `pushFrame` returns "a JSON
  `RepRecord`". It returns a JSON **array**, because one frame can complete two
  records: part 4's half-seen record, then a pair. The golden squat run does
  exactly that at frame 408.

**The suite is `EngineParityTest`**, in `commonTest`, so it runs on every target.
- **Two scenarios of synthetic full-body frames** go through the facade.
  - A squat set of 9 records: an aborted repetition, three valid ones, a
    one-sided one, a one-frame dropout, a lagging right knee, a marginally
    visible repetition (`DEGRADED` frames), and one under way when the set ends.
  - A plank that reaches position, holds, sags, recovers, leaves the frame
    briefly and holds again.
- **Seeded jitter.** Kotlin's `Random(seed)` is the same generator on every
  platform, so the outlier gate, smoother and resampler do real floating-point
  work.
- **The golden file.** `EngineParityGoldenWriter`, JVM-only, wrote
  `EngineParityGolden.kt` once. It runs only with `KINESIS_WRITE_GOLDEN=1`, so
  no run can quietly re-baseline it.
- **What must match.** Every target is compared line by line against the golden
  file:
  - counts and indices exactly;
  - every string exactly: phases, validities, hold states;
  - every other number within 1e-9 relative, as CONTRACTS.md §6 sets it, with a
    1e-12 absolute floor for values that should be zero.
- **The comparison can fail.** A third test feeds it a changed index and a
  1e-8 relative nudge to one angle, and requires both to be reported.

**It found a real cross-platform bug on its first run.** On JS both scenarios
failed. `configure`'s regex ended in a bare `}`. The JVM accepts that, but
Kotlin/JS compiles regexes with JavaScript's `u` flag, under which a lone `}` is
a syntax error. So on JS, `configure` threw before the first frame. The brace is
escaped now. **After the fix: JS 225/225, JVM, Android debug and Android release
all green**, both scenarios within tolerance on every target.

**A guard turned order-dependent; it now runs in its own JVM.** With the parity
suite in the same JVM, `live_repetition_analysis_allocation_does_not_grow_as_the_session_runs`
failed in 2 of 2 full-suite runs, reading 1,952,000 bytes early and about
3,350,000 late. It passed alone, and passed in the full suite without the parity
test. A diagnostic run that sampled six more windows over 1.8 million further
frames read **1,952,000 bytes in every window**. So there is no growth; the
failures were a one-off JIT recompilation that other tests' activity made land
between the guard's two windows. The allocation guards now run in a separate
`jvmAllocationTest` task, in their own JVM, with the same classes and tolerances.
`jvmTest` is finalised by it, so `allTests` always runs them. It was green in
two forced reruns. **Nothing in the guards was loosened.**

**A blind spot in the source guards, closed.** `SharedCoreSourceGuardTest` lists
`app` among the modules it scans, but its walker only opened source sets whose
names end in `Main`. The app's is `main`, so **no guard had ever read a line of
the app.** It now reads every non-test source set, the app's build-type sets
included. The widened scan found nothing. Mutation: a bilateral average and a
stray `@JsExport` added to an app source file, then reverted, were both caught.
`the_js_source_set_contains_only_the_facade` was replaced by the rule CONTRACTS
states, `the_facade_is_the_only_js_export_in_the_project`: exactly one file in
the whole project may contain `@JsExport`.

### 9. Housekeeping

**Front Raise: the reason was on record; only the entry was missing.** The P0
audit's open item said Front Raise "was dropped implicitly by following the §5
table" with no decision recorded. The reason is `FINAL_ARCHITECTURE.md` §5,
present since the P0 commit `977994d`:

> **Front Raise — no good view exists.** From the side, the two arms travel in
> the same plane and occlude each other almost completely; you cannot separate
> left from right, which kills the per-side analysis. From the front, the arm
> moves toward the camera, so shoulder flexion is maximally foreshortened — the
> angle you want to measure is the one 2D projection destroys. A 45° view is a
> compromise that degrades both. **Cut it.**

§5's cut list applies one criterion to Front Raise, Calf Raise and Overhead
Triceps Extension alike. An exercise is supportable only if some single camera
view can see its primary signal unambiguously through the whole range of
motion. Checked in the history: **Front Raise was never in the code.** No
profile, class or id ever existed in `core`, so PROJECT_STATUS's "removed from
the library" was inaccurate. It was never added. **Decision, ratified: Front
Raise stays out, for §5's reason.** The audit's open item and PROJECT_STATUS's
loose end are closed by this entry. Bringing it back would need a view that
sees shoulder flexion per side, which is a new decision.

**README.md** said "P4 not started". It now gives:
- the P4 status, as far as it has been verified on the phone;
- how to build, how to test on the JVM and on a phone, how to run the
  benchmark, the Firestore rules tests and the golden-file regeneration;
- that the app shows its third-party notices on its LICENCES screen and ships
  the Apache 2.0 text. The pose model is Apache 2.0 and bundled unmodified, and
  its model card is in `docs/third_party/`.

The README states that the BSD components are named, not that their texts ship.

**Files.**
- `docs/FEATURE_PLAN.md`, the owner's post-Phase-B plan, is committed as
  written. It contained no credentials or personal data.
- `docs/Kinesis_Project_Report.docx`, the old report, was deleted. It was never
  tracked, so it is gone from this disk and was never in git.
- `docs/Chiron_Project_Report.docx` stays untracked, as asked.

**Owed before any build is distributed.** Two BSD notice texts: CameraX's
libyuv (P3-17) and Health Connect's repackaged Protocol Buffers (part 7). Both
are named in the app; neither text is shipped.

### What "tested" means here

`gradlew assemble allTests :app:testDebugUnitTest`: **BUILD SUCCESSFUL, 1,181
test executions, 0 failures, 0 errors.** P3-18 stood at 1,133.

| Module and target | P3-18 | Now | Added |
|---|---|---|---|
| `:core:analysis`, per shared target (JS, Android debug, Android release) | 218 | 225 | `RepRecordIntegrityTest` 4, `EngineParityTest` 3 |
| `:core:analysis`, JVM | 232 | 235 + 5 | the same 7, and the golden writer (a no-op unless asked); the 5 allocation guards now run in `jvmAllocationTest` |
| `:core:domain`, per target | 33 | 33 | — |
| `:app`, debug unit tests | 115 | 134 | `LiveOrientationTest` 9, `HandOverTimesTest` 3, `PhaseBStatsTest` +5, `HealthMetricReadTest` 2 |

Test tasks whose inputs had not changed since a passing run were up to date or
restored from Gradle's cache.

`gradlew connectedDebugAndroidTest` on the A142 (Android 16): **14 passed, 0
failed.** The 6 `FramePipelineBenchmark` cases skip without their opt-in flag,
by design. The device suite now covers:

| Test class | Tests |
|---|---|
| `PoseLandmarkerOnDeviceTest` | 4 |
| `LiveScreenClippingTest` | 2 |
| `LiveRotationOnDeviceTest` | 3 |
| `UprightFramesOnDeviceTest` | 2 |
| `HealthConnectOnDeviceTest` | 3 |

`tools/firestore-rules`: 14 of 14 on the emulator.

**Mutations, all caught, with every file restored byte for byte:**
- the clip removed (8,477 and 6,931 pixels outside the box);
- four in rotation: the analysis update, mirror order, the listener, the bands;
- the tracker's numbering and pairing (their tests failed first);
- the rules' update and delete;
- the widened source scan reading the app (a bilateral average and a stray
  `@JsExport`);
- a failed Health Connect read (`NoData` before the fix).

The parity suite's first JS run found a real bug. It is kept as that suite's
evidence.

**Before pushing,** every blob reachable in history was checked, by path and by
content signature for PNG, JPEG, GIF, WebP, BMP, MP4 and MOV: **none**. No file
added since `origin/master` is larger than 376 KB.

**Another session worked in parallel.** While this batch was under way, a
separate session added the private remote, pushed `master` up to part 4 with the
owner's direction, and recorded that as P3-20. That is why P3-20 sits after
this entry although it was written during it. The owner had asked this batch not
to push before part 10. The earlier push was that session's, and it checked
first that no owner image was in history. This batch pushes parts 5 to 10.

### Decisions for the owner

1. **Gap tolerance (part 4).** Whether to bridge short runs of unmeasurable
   frames inside a repetition, and the `maxGapMs` to use. It waits for the
   camera session's measured run lengths. It touches Level 1 measurements.
2. **AGP 8.9.1 and compileSdk 36.** They are needed for the stable Health
   Connect client 1.1.0, and are probably the way out of P1-1's lint and JDK 25
   exclusion too.
3. **The GPU delegate.** One run measured it 11 ms faster than the CPU and one
   3–4 ms slower. It changes the numerics. Not adopted.
4. **A debug-only way to run the full model live**, if lite and full are to be
   compared on a person in the camera session. The app has none today, and the
   full model is not switched in.
5. **The two BSD notice texts** (libyuv, Protocol Buffers), owed before any
   build is distributed.

### What is NOT verified — the camera session

Everything below needs the owner in front of the phone. Everything in P3-15's to
P3-18's lists stands, except what this entry and P3-18 closed.

1. **Clipping (part 2).** With a person partly out of frame, the skeleton stops
   at the camera box, on both screens.
2. **Upside down (part 3).** Turn the phone over with the live screen open, on
   each camera:
   - the screen, preview and skeleton turn with it;
   - the person stays upright in the preview;
   - the set carries on.

   The tilt listener's bands are also only tested in code, not by a hand
   turning the phone.
3. **Left and right, on each camera.** Curl with the left arm only. The saved
   records, read with `run-as`, must be attributed `LEFT`. Then the right arm,
   `RIGHT`. The front and back camera paths differ.
4. **Rep counting.** Squat side-on, 10 repetitions, on each camera. The count
   must match, `repIndex` must run 1 to N, and durations must be plausible.
5. **Plank timing** against a stopwatch, for a clean hold and one with a sag.
6. **Lite against full model tracking** on a person. This needs decision 4
   first.
7. **A 15-minute heat run.** Read the readout's rates and MediaPipe latency, and
   the phone's thermal status, each minute.
8. **Auto-exposure and frame rate in bright light.** Is the camera's delivered
   rate, now on the readout, the limit in dim light? The auto-exposure range is
   unchanged until this is measured.
9. **Gap tolerance.** Collect the readout's per-side runs of unmeasurable frames
   over real sets. That is decision 1's input.
10. **Tuning.** `smoothingAlpha`, the `VisibilityPolicy` thresholds, the hold
    tolerances and `outlierReadmitSamples`, against device data.
11. **Kept buffers on real frames.** The readout's conversion time on the live
    camera, and whether CameraX's rows arrive tightly packed. If not, the
    fallback allocates.
12. **A new set uploading on its own** through `SessionSyncWorker`, under the
    fixed rules, with `KinesisSync` logging the push.
13. **The calibration flow, on the phone** *(added by P3-22)*. Calibrate Squat
    side-on with 8–10 ordinary squats; the card must show per-side values, the
    library must mark Squat calibrated, and the dashboard must list the
    baseline. Then an ordinary squat set: the set summary must show each
    repetition's range of motion and tempo beside the baseline. Recalibrate and
    check the card's date and values replace the old ones. Calibrate with too
    few valid repetitions (end after 3) and check the plain "No baseline yet"
    reason, and that the earlier baseline is untouched.
14. **An alternating and a one-sided calibration** *(P3-22)*. Bicep Curl
    front-on, at least 5 curls per arm: two sides, each from its own arm, tempo
    per arm. Row side-on: one side measured, the other shown as a dash, and
    "L minus R" not measurable.
15. **A hold calibration** *(P3-22)*. Plank side-on for 15 s or more: held-at
    angle, offset from 180°, sway and time. A second plank's set summary shows
    them beside the baseline. A plank under 10 s must not calibrate.
16. **Whether 5–8 repetitions and 10 s are reachable** *(P3-22)* at the
    phone's real frame rate and validity, since only VALID repetitions count:
    how many squats it takes to get 5 valid ones is itself a camera-session
    finding.
17. **The book test for short interruptions** *(added by P3-24)*. Curl
    side-on and, mid-curl, sweep a book or towel between the camera and the
    curling arm, some quickly and some slowly. A hand was not big enough to hide
    the arm. The readout gives each gap's length: at 500 ms or less the
    repetition must still count, and longer must end it as INSUFFICIENT_DATA.
18. **"NOT VISIBLE" during side-on curls** *(P3-24)*. The line under the counter
    reads NOT VISIBLE while the near arm is tracked and counted, because the
    hidden far arm makes every frame `INVALID`. Decide with the curl's view what
    it should say, then check it on the phone.
19. **Squats face-on against side-on** *(P3-24)*. Face-on tracked both knees
    and counted the owner's 4 correct squats, but its knee angles are
    projections (one read 40.9°); side-on loses the far knee (P3-23). The
    profile's view is the owner's decision, and it decides whether MM-Fit's
    face-on squats stand for it. Then 10 squats in the chosen view, counted by
    hand.

Not needing the camera but still owed: the Health Connect rationale opened from
Health Connect's own settings, and a steps figure later in a day with walking.
And, needing only the phone attached *(P3-22)*: `KinesisDatabaseMigrationTest`
under `connectedDebugAndroidTest`, and the first install of this build over
the existing one, which migrates the owner's real version 1 database.

**Scope of this entry.** It records the following, and makes no accuracy claim
of any kind:
- device work without the camera: a clipping fix, orientation support by the
  owner's decision, two tracker bugs, and a frame-rate investigation with one
  corrected claim;
- a broken security rule set found and replaced;
- Health Connect made to work on Android 16;
- the parity suite the architecture required, and the cross-platform bug it
  found;
- guard-test repairs, a ratified cut, and housekeeping.

---

## P3-20 — The repository gets a remote: private GitHub, `google-services.json` included

*2026-09-25*

Until now the repository existed only on the owner's disk. At the owner's
direction, `master` is pushed to a private GitHub repository,
`maximus16042002-cmyk/chiron`, as `origin`.

### Checked before the first push

- **No image of the owner in any commit.** Every blob reachable from any ref was
  checked by path and by content signature (PNG, JPEG, GIF, WebP, BMP, MP4/MOV).
  None matched. The only file containing pictures is the third-party BlazePose
  model card PDF in `docs/third_party/`, whose images are Google's. P3-19 §1
  holds.
- **No file over 50 MB.** The largest is `pose_landmarker_lite.task`, 5.5 MB.
- **No signing key anywhere**, on disk or in history. `*.jks` and `*.keystore`
  are now in `.gitignore` so none can be committed later.
- **`data/` and `local/` are anchored to the root, as `/data/` and `/local/`.**
  Unanchored, they would also have hidden any nested folder with the same
  name, such as a future Kotlin package `com.kinesis.data`. `git status
  --ignored` listed the same files before and after. `data/mm-fit/` and
  `local/phase-b/` stay ignored.

### `google-services.json` is committed on purpose

`app/google-services.json` has been tracked since Batch 10 (P3-14). It holds
the Firebase project ID and the Android API key. **The owner decided to keep it
in the pushed history**, for these reasons:

- **The repository is private.**
- **It is not a secret.** Every copy of the APK carries the same values, so
  anyone holding the app already has them. Hiding it from git would protect
  nothing.
- **The protection is elsewhere:** the Firestore security rules, and
  restrictions on the API key in the Google Cloud console, limiting it to the
  `com.kinesis` package and its signing certificate and to the APIs the app
  calls.

**What this entry does not claim.** The Firestore rules are published but have
never been exercised by a client (P3-14 follow-up, 2026-09-23). Whether the
API key carries any restriction has not been checked by any session here; that
lives in the console, not the repository. Both remain owner checks.

**Before the repository is ever made public, this decision must be reviewed.**
A public repository puts the key in front of automated scanners that never see
an APK. At that point, at minimum, confirm the key's restrictions and the
rules' behaviour. Also consider rotating the key and dropping the file from
history.

---

## P3-21 — Owner decisions: the camera session waits, P6 goes first, and P3-19's list

*2026-09-26*

Decided by the project owner at the start of this session, and recorded here
before any work on them, as they asked.

### The camera session is deferred, and P6 goes ahead of P5 and P7

- **The Phase B camera tests wait.** The owner and their teammate will do the
  camera session together later. P3-19's list "What is NOT verified — the camera
  session" still stands in full, and nothing below closes any of it.
- **Work continues before it.** FEATURE_PLAN.md said its batches start "after
  Phase B", once its findings were fixed. That no longer holds, and its order
  line now says so.
- **P6, calibration and personal baselines, goes first, ahead of P5 and P7,**
  so there is something to demonstrate to the project's guide. P5 and P7 follow
  at the owner's direction.
- **Anything tuned to real movement stays provisional until the camera
  session.** That covers every threshold already marked provisional, and every
  number P6 introduces. P6's calibration counts have a second, later authority
  as well: the MM-Fit convergence study (ZERO_DATASET_BUILD_PLAN.md, Study 3).

This is the same trade P3-4 accepted: work built before the device check may
need rework if the camera session finds something fundamental. The owner
accepts it again here.

### P3-19's five decisions for the owner

| P3-19 item | Decision |
|---|---|
| 1. Gap tolerance (`maxGapMs`) | **Waits for the camera session.** It needs the measured lengths of the runs of unmeasurable frames, which only the camera session can collect. |
| 2. AGP 8.9.1 and compileSdk 36 | **Its own small batch, later.** Not part of P6. The stable Health Connect client 1.1.0 and P1-1's lint exclusion wait with it. |
| 3. The GPU delegate | **Not adopted.** It changes the numerics, and its measured benefit on this phone was inconsistent: 11 ms faster in one run, 3–4 ms slower in another. The CPU delegate stays. |
| 4. The full pose model live | **A debug-only switch is added.** Release builds keep the lite model only. |
| 5. The two BSD notice texts | **Added now** to the licences screen: libyuv (CameraX) and Protocol Buffers (Health Connect, MediaPipe, Firestore). |

Items 4 and 5 are built in this session. How is recorded below as each lands.

### Item 5, built: the two BSD texts ship

The licences screen now carries the full BSD 3-Clause text of both components,
under their own heading, above the Apache text.

- **Neither artifact carries a usable copy.** CameraX's AAR has no libyuv text
  at all (P3-17). Health Connect's `connect-client-external-protobuf` jar has a
  `LICENSE.txt`, but without the copyright line, and a BSD licence's first two
  conditions are to reproduce "the above copyright notice".
- **So both came from the projects' own repositories, fetched raw and copied
  byte for byte**, not typed and not summarised:
  - libyuv: `LICENSE` from chromium.googlesource.com/libyuv/libyuv, `main`.
    camera-core's POM names that repository's `README.chromium`, which names
    `LICENSE` as the licence file. "Copyright 2011 The LibYuv Project Authors."
    The repository also has a `PATENTS` file, an additional patent grant to
    the recipient that asks nothing of a redistributor; it is not shipped.
  - Protocol Buffers: `LICENSE` from github.com/protocolbuffers/protobuf at
    tag `v26.1`, the release of `protobuf-javalite` 4.26.1, which is the
    version the release APK resolves (through MediaPipe and Firestore). The
    same terms cover Health Connect's repackaged copy. "Copyright 2008 Google
    Inc."
- `ThirdPartyNoticesTest` checks each text for its copyright line, grant, both
  conditions, endorsement clause and disclaimer. Its first version failed on a
  correct file: libyuv breaks one clause across a line. The clauses are now
  compared with whitespace collapsed.

**P3-17's and P3-19's "owed before any build is distributed" is closed** for
these two texts.

### Item 4, built: a debug-only lite/full switch

- **Debug builds** show `[ USE FULL MODEL ]` under the camera switch when the
  build carries the full model, and `[ USE LITE MODEL ]` to go back.
  Switching is a new set, like switching camera: the set in progress is saved
  and a new one starts, because one set measured by two models is not one
  measurement. The Phase B readout names the model on every line
  (`lite model` / `full model`), so the two can be told apart in Logcat.
- **Release builds cannot switch.** The switch function lives in `src/debug`;
  its `src/release` twin returns nothing to switch to, and release assets hold
  the lite model only. Checked in the built APKs: the debug APK carries
  `pose_landmarker_full.task` (9,398,198 bytes), the release APK only
  `pose_landmarker_lite.task`.
- **The full model is not committed.** It sits in `app/src/debug/assets/`,
  which `.gitignore` excludes; its SHA-256 matches P3-19's download
  (`4eaa5eb7…c4ad`). A build from a fresh clone has no full model and offers no
  switch. To enable it, copy `local/models/pose_landmarker_full.task` there.
  The profiling build shares the debug assets, so lite and full can be timed
  live without a debugger attached.
- The licences screen's model notice now says debug builds may carry the Full
  model under the same card and licence; `ThirdPartyNoticesTest` checks both
  asset folders.

**Five mutations, all caught, files restored byte for byte:** the full model
in `src/main/assets`; the release twin offering the switch; the readout
dropping the model name; the protobuf text without its copyright line; the
libyuv text cut short.

---

## P3-22 — P6: calibration and personal baselines

*2026-09-27*

P6, at the owner's direction and ahead of P5 and P7 (P3-21). A user calibrates
an exercise with an ordinary set; the shared core turns its valid repetitions
into a personal baseline, per side; the app stores it on the phone only; and
every later set of that exercise is shown beside it, as numbers. **No
judgement is made anywhere**: "shallower than your usual" and the rules behind
it are P8's. Nothing here has run on the phone, which was not attached at any
point in this session. **Every number in this entry is provisional**, per
P3-21, until the camera session and the MM-Fit convergence study.

### What was built

| Part | Where | What |
|---|---|---|
| Baseline | `:core:domain` `calibration/` | `CalibrationBaseline`: `RepetitionBaseline` and `HoldBaseline` |
| Calibrator | `:core:analysis` `calibration/` | `Calibration`, `CalibrationPolicy`, `RobustStatistics`, `CalibrationKey`, `CalibrationGuide` |
| Analysis | `:core:domain` `analysis/`, `:core:analysis` | `AnalysisResult`, computed by `PostRepAnalysis` |
| Serialised form | `:core:analysis` | `BaselineJson`, and a small `JsonText` reader |
| Facade | `KinesisEngine` | `computeBaseline` and `analyzeRep`, the two methods CONTRACTS.md §5 said wait for P6 |
| Hold steadiness | `HoldAnalyser`, `HoldRecord` | `meanHeldValue` and `heldValueSd` over the frames that held |
| Storage | `:app` | Room version 2: a `baselines` table, two hold columns, one migration |
| Flow | `:app` | exercise screen, CALIBRATE, calibration result, set summary, dashboard baselines |

### The provisional numbers, and why each

All three live in `CalibrationPolicy.PROVISIONAL` (version `p6-1`, stored on
every baseline) and are pinned by a test, so changing one is a visible change.

| Number | Value | Why this value |
|---|---|---|
| Fewest valid repetitions per side | **5** | The smallest sample in which the quartiles (below) fall exactly on the second-smallest and second-largest values, so one extreme repetition at either end moves neither the median nor the IQR. With 4 it moves the IQR. `RobustStatisticsTest` shows both. It is also the low end of ZERO_DATASET's "5-10 reps". |
| Most valid repetitions used per side | **8** | ZERO_DATASET Study 2 builds each subject's personal baseline from "their first 8 repetitions". Using the same number makes the app's baseline and the study's the same estimator. Taking the first 8 also keeps the tired end of a long set out. |
| Shortest calibration hold | **10 s** | At the 12–16 results a second measured on the phone (P3-18, P3-19), about 120–160 held frames behind the sway figure, and short enough to ask of someone new to a plank. MM-Fit has no holds, so only the camera session can revisit it. |

"Per side" means: a squat's 5–8 repetitions measure both knees at once; a bicep
curl needs 5–8 curls with each arm (10–16 in all); a row needs them on the side
nearer the camera only.

**The quartile definition is fixed too**: NumPy's default (linear, Hyndman and
Fan type 7). The convergence study is Python; if it used a different quartile
it would be answering a question about a different estimator. A mutation to
nearest-rank quartiles fails 12 tests, the parity suite among them.

**Who sets them for real.** The repetition counts: the MM-Fit convergence study
(Study 3: baselines from the first N for N from 3 to 20, with bootstrap
intervals). The hold duration: the camera session. Whether the counts are even
reachable, given that only VALID repetitions count and P3-18's phone set was 11
of 12 `INSUFFICIENT_DATA`: the camera session (checklist item 16).

### What a baseline holds

**Repetition exercises**, each per side as a median and interquartile range, the
IQR being the rep-to-rep variability:

- range of motion; the angle at the starting position (peak) and at the far
  position (trough);
- descent and ascent time — see *Tempo* below;
- the habitual left-minus-right range of motion — see *Asymmetry* below;
- the pointwise mean of the used repetitions' 64-point curves, and each
  repetition's root-mean-square distance from it (the curve's spread).

**One rule decides which repetitions a side is built from, with no branch on the
exercise's shape**: a repetition counts towards a side exactly when it is VALID
and measured that side. A squat's repetitions measure both knees, an
alternating curl's one arm each, a near-side-only row's the side facing the
camera. The same rule gives each of them the right answer. Pinned for all three.

**Tempo, as the records attribute it — a limit stated, not hidden.** A
`RepRecord` carries one descent and one ascent time. For a one-sided repetition
(an alternating curl, a row) that time is that side's, and the baseline files it
per side. For a repetition both sides made together, the tracker takes the time
from the side it measured for longer (P3-2): it describes the repetition, not
either side. The baseline files it once, "both sides together", rather than
under both sides, which would claim two per-side measurements that were never
made (a mutation doing exactly that is caught). **Per-side tempo for squats,
push-ups and the other simultaneous exercises would need `RepRecord` to keep
each side's phase times** — a change to the measurement record, Room, the sync
document and the parity golden file. That is a decision for the owner, and was
not made here.

**Asymmetry comes in three kinds, because how the sides were seen decides what
can be known:** *paired* (both sides in the same repetitions: a median and IQR
of each repetition's left minus right); *between side medians* (alternating
limbs: the difference of the two sides' medians, with no spread of its own);
*not measurable* (the near-side-only row — FINAL_ARCHITECTURE §5's "no symmetry
claim"). This is the "habitual offset" FINAL_ARCHITECTURE §2's asymmetry rule
subtracts; the rule itself is P8.

**The mean curve is a mean, as the brief asked, and so it is not robust.** One
odd repetition among eight moves it. P8's reference repetition — "min median
DTW", FINAL_ARCHITECTURE §7 — is the robust curve, and nothing here anticipates
it.

**Holds get a hold baseline** from one valid calibration hold of at least 10 s:
where the body line was held (and its signed offset from the profile's target),
the mean distance from the target, **the sway (steadiness)**, time held and
breaks. Not per side, on purpose: a hold is analysed on the body line, which
both sides' landmarks describe together (P3-9), reduced to one signal by the
named `HoldReading` rule before the analyser sees it (P3-16). Nothing measured
per side reaches the hold record, so nothing per side can reach its baseline.

**Steadiness had to be measured first.** `HoldRecord` held a mean absolute
deviation from the target, which says how far but not which way, and nothing
about sway. `HoldAnalyser` now keeps a running mean and sample standard
deviation of the signal over the frames that held — Welford's method, constant
space, allocation-free, and accurate for a sway of a millionth of a degree
around 179° (tested). `HoldRecord` gains `meanHeldValue` and `heldValueSd`; a
hold recorded before this reloads both as `NaN`, not measured. A hold without
them cannot calibrate.

**Too few, and the app says so plainly.** No baseline is ever built from fewer
than the minimum. The core composes the reason, in numbers — for example "No
baseline yet. Calibration needs at least 5 valid repetitions on each side; this
set had 4 on the left and 3 on the right. Only repetitions counted as valid are
used." — and the app shows it as given. A set that does not calibrate leaves the
earlier baseline untouched.

**Variants share their parent's baseline** (CONTRACTS §3). CALIBRATE is offered
on parents only; a Goblet Squat's screen shows the Squat baseline and says so.
P3-6's open assumption — a loaded squat measured against unloaded ones — stands,
unmeasured.

### `AnalysisResult`: separate, computed, and making no claim

- **Separate from `RepRecord`** (CLAUDE.md §15). The record is what the camera
  saw; an `AnalysisResult` is what one version of the analysis made of it
  against one baseline, and it names both (`analysisVersion`
  `p6-comparison-1`, and a `BaselineReference`).
- **Computed, not stored.** Every set summary recomputes it from the stored
  records and the current baseline, so re-analysing old repetitions when rules
  change is simply computing it again — the property §15 asks for, by
  construction. Whether P8 should also keep what was shown at the time is P8's
  decision.
- **Numbers only.** A comparison is `BesideBaseline(measured, baseline,
  baselineSpread)`: no difference, no percentage, no direction.
- **`findings` is always empty in P6**, and
  `p6_makes_no_claim_beyond_numbers_for_any_exercise` pins it across the whole
  library. P8 replaces that test deliberately when its rules — each with a
  `ruleVersion`, and for Level 3 a `sourceRef` — exist.

### The facade

`computeBaseline(repsJson)` takes the records exactly as `pushFrame` and
`finishSet` return them and returns an outcome: `calibrated` with the baseline,
or `tooFewRepetitions` / `holdNotUsable` with the counts and the reason. Not
having a baseline is an answer, not an exception. `analyzeRep(repJson,
baselineJson)` returns the `AnalysisResult` as JSON. The facade has no clock or
session, so its baselines carry `null` for both; a platform stamps its own.
CONTRACTS.md §5 is updated.

**Parity.** Three new golden scenarios through the facade — a squat calibration,
alternating curls, and a plank (with a first plank too short to calibrate) —
each fitting a baseline and analysing every record against it. The golden file
was regenerated deliberately and its diff reviewed: every existing line is
unchanged except the plank's hold record, which gains exactly its two new
fields. JVM, JS and both Android variants match within CONTRACTS §6's 1e-9;
counts are compared exactly.

### Storage: Room version 2, never destructive

- A `baselines` table keyed by **(user, exercise)**: one baseline per user per
  exercise, and recalibrating replaces it. The baseline itself is one column,
  the core's `BaselineJson` — left and right as separate fields throughout, and
  "not measured" as `null` — because nothing queries inside a baseline and that
  format already round-trips exactly on every platform. A row that cannot be
  read is logged under `KinesisStorage` and treated as not calibrated.
- Two nullable hold columns for the new measurements.
- **The migration only adds**: one `CREATE TABLE`, two `ALTER TABLE … ADD
  COLUMN`. Nothing is dropped, rewritten or copied. The builder has no
  destructive fallback, so a missing migration would stop the app opening its
  database rather than empty it; the app and the migration test use the same
  builder.
- **Schemas are exported and committed.** `1.json` could not be produced by this
  build once the entities changed, so it was generated from a throwaway git
  worktree at `HEAD` with only schema export switched on; the entity file has
  not changed since P3-12, so it is the schema on the owner's phone. `2.json` is
  this build's. The migration's `CREATE TABLE` is Room's own for version 2,
  character for character.
- **Tests.** `KinesisDatabaseMigrationTest` (on the phone, three tests) builds a
  filled version 1 database from `1.json` — a squat set with a half-seen
  repetition and a plank — migrates it, and reads every row back through the
  app's own DAO and mapping; then stores, replaces and separates baselines by
  user. **It has not run: no phone was attached.** It compiles into the test
  APK. On the JVM, `DatabaseVersionGuardTest` fails if the version moves without
  a gapless migration chain and an exported schema, or if a destructive
  fallback appears.

**§17 check: `androidx.room:room-testing` 2.7.1.** *Why:* a migration test from
the real old schema, which Room itself provides for. *Existing capability:* Room
validates a migrated schema on open, but cannot build an old database from its
exported schema. *Licence:* Apache 2.0, read from its POM; its transitives
(`room-testing-android`, `room-migration`, `room-common`, AndroidX arch-core,
okio) are Apache 2.0. *Platform:* `androidTestImplementation` only — **not in
the APK**; the release runtime classpath has no `room-testing`. *Offline,
bundle:* unaffected. *Maintenance:* AndroidX, the Room version already pinned.

### Baselines stay on the phone, and the disclosure stays true

The sign-in disclosure (P3-14) says completed sessions leave the phone. A
baseline is not a session, and nothing uploads one. `BaselinesStayOnThePhoneTest`
checks three independent things: nothing in the sync package refers to a
baseline type; sync wraps the session repository and nothing else; and the
Firestore rules accept writes under a user's `sessions` only, so an upload that
somehow got written would be refused. The disclosure needs no change and gets
none. Two consequences, stated to the user on the calibration screen:

- **The calibration set itself is an ordinary session.** It is saved, counts
  towards the streak, and syncs like any other — the disclosure already covers
  it. What stays on the phone is the baseline derived from it.
- **A reinstall or a new phone keeps the history and loses the baselines.** The
  user calibrates again.

**Found, not changed: local sessions are not partitioned by account.** The Room
session tables have no user column, and `SessionMerge.pushMissing` pushes every
local session to whoever signs in. On a phone shared by two accounts, one
account's sessions would be uploaded to the other's. Baselines are keyed by user
and do not have this problem; sessions predate this batch and it is not changed
here. **For the owner.**

**One sync detail.** A synced hold writes the two new fields only when measured.
A hold recorded before this batch therefore uploads byte for byte what an older
client wrote — which matters because the rules accept a re-upload only if it is
identical to the stored document (P3-19), and a retry differing by two `null`
keys would be refused forever. `SessionDocumentHoldFieldsTest` pins it.

### The flow in the app

TRAIN lists every entry with **CALIBRATED** / **NOT CALIBRATED** (a variant:
"CALIBRATED VIA SQUAT"). An entry opens its own screen — START SET, CALIBRATE
(RECALIBRATE once calibrated), and its baseline card. CALIBRATE shows the core's
instructions (usual repetitions, whole body in frame, the required view and
posture, the provisional counts) and a line that the baseline stays on this
phone; START CALIBRATION SET runs an ordinary live set labelled CALIBRATION SET.
END SET saves the set as usual, then the core builds the baseline and the app
saves it, replacing any earlier one, and shows the card: per side, median with
its IQR, and the estimator version marked provisional. After an ordinary set, a
**set summary** lists every record — validity, and range of motion per side,
descent and ascent — and, with a baseline, each value beside the baseline's.
HISTORY now lists every calibrated exercise's baseline card.

Calibration completes only through END SET. Leaving a calibration set by the nav
bar saves the set, like any set, and builds no baseline.

### A change to this folder that was not this session's

On resuming, `core/analysis/.../rep/RepFsm.kt` was modified in this working
tree, and this session had not touched it: `isAtTop` and `isAtBottom` gave the
full hysteresis band only while already at the top or bottom — a change to how
repetitions are detected, with no decision entry. Its timestamp (2026-09-26
11:09) matches another Claude session that announced gap-tolerance work in a
separate worktree (`E:\kinesis-repcount`, branch `repcount-gap-tolerance`,
proposing P3-23); that worktree was clean. The owner had said only this session
works in this folder. **It was not committed and not discarded:** an exact copy
and a patch are in `local/foreign-edits/` (gitignored), and the committed
`RepFsm.kt` was restored so this batch's tests measure the committed engine. The
parity golden file was regenerated before that edit was made, so it reflects
the committed FSM. The other session was no longer running and could not be
told. **P3-21 records that gap tolerance waits for the camera session**; whether
that work proceeds is the owner's call.

### What "tested" means here

`gradlew clean`, then `gradlew assemble allTests :app:testDebugUnitTest`:
**BUILD SUCCESSFUL, 1,492 test executions, 0 failures, 0 errors**, and no
compiler warnings from the project's sources. P3-19 stood at 1,181, so this
batch adds 311. `:app:testReleaseUnitTest` runs 153 more, all passing. The test
APK for the phone (`assembleDebugAndroidTest`) builds.

| Module and target | P3-19 | Now | Added |
|---|---|---|---|
| `:core:analysis`, per shared target (JS, Android debug, Android release) | 225 | 295 | calibration 54, steadiness 6, facade 7, parity 3 (the 3 new scenarios) |
| `:core:analysis`, JVM | 235 + 5 | 305 + 5 | the same 70 |
| `:core:domain`, per target | 33 | 33 | — |
| `:app`, debug unit tests | 134 | 165 | coordinator 8, baseline mapping 6, stays-on-phone 4, version guard 3, hold fields 3 + 1, model switch 3, release assets 1, notices 2 |

`connectedDebugAndroidTest`: **not run** — no phone was attached at any point
in this session. It now includes `KinesisDatabaseMigrationTest` (3 tests).

**Tests first, as it happened.** The core was written as a skeleton of
signatures whose bodies threw, and the 60 core tests were run against it: **58
failed**, and the 2 that passed were already right (the policy pin, and a hold
that never held). All 60 then passed on JVM and JS. The facade tests were
written before the facade methods but not run against the stub separately. The
app's storage and coordinator tests were written alongside their code, and are
backed by mutation instead. One test was wrong at first and one of mine had a
regex bug, both caught before they could pass for the wrong reason: the libyuv
clause test (above), and a rules-path pattern that would have stopped at the
first `{`.

**Mutations: 24, all caught, every file restored byte for byte** (plus the five
under P3-21).

| Mutation | Caught by |
|---|---|
| Baseline built from the two sides averaged | 11 tests, incl. `each_side_keeps_its_own_median_range_of_motion` |
| Invalid repetitions counted | `only_valid_repetitions_count` and the too-few test |
| One side enough for a both-sided profile | `an_alternating_exercise_needs_five_on_each_arm` |
| No cap of 8 per side | `only_the_first_eight_valid_repetitions_on_a_side_are_used` |
| A shared repetition's timing filed under both sides | 2 tests |
| Mean curve takes unmeasured points | `the_mean_curve_is_pointwise_and_skips_unmeasured_points` |
| Quartiles by nearest rank | 12 tests, incl. two parity scenarios |
| Sway as a population SD | 2 steadiness tests |
| A finding produced before P8 | `p6_makes_no_claim_beyond_numbers_for_any_exercise` |
| Another exercise's baseline accepted | 2 tests |
| Not measured reloads as zero | 3 JSON and facade tests |
| A variant given its own baseline | `every_variant_uses_its_parents_baseline_and_every_profile_its_own` |
| An unclassified file in the calibration package | the source guard's coverage test |
| Timing never attributed to a side | the alternating-curl calibration test |
| A field dropped from the baseline JSON | 8 tests, incl. two parity scenarios |
| Sync code refers to a baseline | `nothing_in_the_sync_package_touches_a_baseline` |
| The rules open a baselines collection | `the_firestore_rules_accept_writes_only_under_a_users_sessions` |
| Destructive fallback in place of the migration | `the_builder_never_falls_back_to_a_destructive_migration` |
| The migration left out of the list | the gapless-chain test |
| A row whose payload is another exercise's accepted | `a_row_whose_columns_disagree_with_its_payload_is_refused` |
| Baselines shared across users | 7 coordinator tests |
| Room drops the sway | the hold-fields mapping test |
| Sync writes unmeasured hold fields as `null` keys | `an_unmeasured_hold_writes_exactly_the_document_an_older_client_wrote` |
| A failed calibration replaces the baseline anyway | `a_set_that_does_not_calibrate_leaves_the_earlier_baseline_alone` |

One mutation first did not run: its pattern ended in a line feed and that file
has CRLF endings. The harness left the source untouched (checked against `HEAD`)
and it was rerun with a line-ending-free pattern.

### What is NOT verified

- **Anything on the phone.** No device was attached at any point. Not run:
  `connectedDebugAndroidTest`, including `KinesisDatabaseMigrationTest`; the
  migration of the owner's real database on first install of this build; and
  every screen of the flow. The camera-session checklist in P3-19 gains items
  13–16, and the no-camera list the migration run.
- **Every provisional number** — 5, 8, 10 s — against the MM-Fit convergence
  study and against real frame rates and validity.
- **Whether a squat-shaped baseline is a good baseline at all** for any real
  person: nothing here has seen one.

### Decisions for the owner

1. **Per-side tempo for simultaneous exercises** needs `RepRecord` to keep each
   side's phase times: a measurement-record change touching Room, sync and the
   golden file.
2. **Local sessions are not partitioned by account** (above).
3. **The RepFsm edit found in this folder** and the gap-tolerance work it
   belongs to, against P3-21.
4. **Whether a calibration hold should be one hold or several.** One is what a
   hold set is today; several would give the sway a spread of its own.

**Scope of this entry.** It records a calibrator, a data model, a migration and
an app flow, built and tested off the phone. It makes no accuracy claim of any
kind, and every number in it is a provisional setting, not a measured result.

---

## P3-23 — The guide demo, diagnosed from the phone, and what it changed

*2026-09-27 and 2026-09-28*

The owner ran Kinesis in front of the project guide and it counted nothing.
They asked for the failure to be diagnosed as P3-18 was: from saved data, not
from theory. This entry records that diagnosis, an evening of rehearsals on the
phone recorded live, one serious mistake of mine and its repair, and the changes
the owner then directed. **The gap tolerance P3-21 deferred to the camera session
is built here**, at the owner's explicit direction, using the rehearsal
recordings as its measured input; that reverses P3-21 on this one point.

### Where the data was, and was not

- **No per-frame data is stored, by design.** Room keeps one record per
  repetition: validity, the angle at the top and far position, range per side,
  timing, and a 64-point curve per side. The per-second Phase B readout goes to
  Logcat only, and the phone's buffer had rolled over.
- The phone's usage statistics and process-exit log showed Kinesis last ran on
  2026-09-26. The owner confirmed the demo ran on this phone, so the attempt on
  record is the one of **2026-09-26, 10:51 to 11:21: 42 sets** (32 squat, 3
  push-up, 3 curl, 2 plank, 2 wall sit), **none counted**, and 26 of the 38
  repetition sets with no record at all.
- Everything read is kept in `local/guide-demo-2026-09-27/` (gitignored): the
  database copies, the Logcat and rehearsal recordings, the exit reasons and the
  usage statistics.

### The 2026-09-26 attempt: mostly (a), partly (b), not (c)

The owner asked which happened: (a) the pose not detected reliably, (b) the
thresholds never crossed, (c) a crash or silent stop.

- **(a), and it is the main cause: one leg was never tracked.** Standing, the
  knees mostly read 175–180°, so the pose was found. In the three repetitions where the
  right knee went past the 95° a squat needs (to 40°, 74° and 77°), the left knee
  was unmeasured in two and never below 131° in the third. Across all 21 squat records the
  left knee never went below 118.6°, and was unmeasured throughout 7 of them. A
  person's knees bend together; a 50–100° disagreement means the far leg,
  hidden in the side-on view, was not really tracked. A squat is `SIMULTANEOUS`
  and counts only when both knees complete, so it could not count.
- 17 of the 21 squat records ended within 3 s, part-way down: one unmeasurable frame
  abandoned the repetition, the behaviour P3-19 part 4 found. The two planks and
  two wall sits had coverage 0.00.
- **(b), for some repetitions.** The two ABORTED squats bottomed at 116° and
  105° on the right knee, short of 95°. Push-ups reached only 131–154° at the
  elbow.
- **(c), no.** No crash or ANR was recorded. The process exits were the user
  removing the task, a force-stop at 11:12 and low-memory kills in the
  background.

### The rehearsals, recorded live

The owner rehearsed bicep curls with the phone attached and the Phase B readout
recorded. Four findings, each read from the recordings and the saved sets:

1. **The phone was overloaded.** Thermal status 2, then 3 ("severe") after a
   restart; CPU 63–65 °C; 72–189 MB of 7.6 GB free, the kernel swap daemon at
   80–97% of a core; background apps (Myntra, Play Store updates, and after the
   restart a wallet app, Prime Video, Faasos, Duolingo, Duo Mobile) and, after
   the restart, `dex2oat` at 450%. In the first readout MediaPipe took up to
   427 ms a frame, results dipped to 2 a second, and for 41 of 85 seconds nobody
   was found. After the `dex2oat` job finished and the apps were stopped:
   thermal status 2, MediaPipe at most 246 ms, the person seen for 240 of 267
   seconds. The median rate was about 14 results a second in every readout; the
   load showed as the dips and the lost seconds, not as a lower median.
2. **Front-on, a curl cannot be measured in 2D.** Every front-on curl was lost
   at 124–146°, while the arm was seen at visibility 0.9 and the phone kept up,
   with single-frame angle jumps of 70–134°. Facing the camera, a curl swings the
   forearm towards the lens and the projected elbow angle is undefined through
   that part of the movement — the reason FINAL_ARCHITECTURE §5 cut the front
   raise, and why its final list gave the curl "Side / 45°". The profile declares
   `FRONT_ON` (P3-9 kept it so both arms are visible); no setup gate enforces
   the view, so side-on was used. **Side-on, the near arm's curls reached 66°,
   33° and 69°; the 33° one, at 22:23, was the first repetition this phone ever
   counted.**
3. **The glitch filter was stricter than I told the owner.** I said a frame is
   dropped if the angle moves more than 20° from the previous frame. The gate
   compares each sample with the **median of the last five**, which trails a
   moving joint by about three frames; at 15 results a second a curl faster than
   about 100° a second was dropped, and one dropped frame lost the repetition.
4. **The display misled.** Amber stayed until a repetition counted, so almost
   always; and in amber every joint was drawn amber, including MediaPipe's guess
   at the hidden arm, which then looked like a real arm lifting. The counting
   never used that arm: its visibility was 0.05–0.15, below the 0.40 floor, and
   it is unmeasured in every side-on record.

### A mistake of mine, and its repair

To prove the Room migration on the phone before installing, I ran
`connectedDebugAndroidTest` for `KinesisDatabaseMigrationTest`. Its three tests
passed on the A142 — **but the run uninstalled the app afterwards**, removing
its local database. I had not checked whether it would. Why P3-19's connected
runs left the app installed is not established.

- A full copy of the database, taken at 22:07, held all 53 sessions including
  the last set (22:05), and passed SQLite's integrity check. Every set had also
  been uploaded to the account.
- The app was reinstalled with the new build and the three database files put
  back **before it was opened**, byte for byte (SHA-256 matched), with Android's
  original permissions restored. On opening, Room migrated them to version 2 —
  the migration's first real run on the owner's data — and all 53 sessions were
  there. Sign-in, camera and Health Connect permissions had to be granted again.
- From then on the app was updated only with `installDebug`, which keeps data.
  **Rule:** before any on-phone test run against a phone holding real data,
  back up its database, and expect the app to be removed.

### What was changed, at the owner's direction

All values are provisional, set from the recordings of 2026-09-27, and belong to
the camera session to confirm.

| Change | Where | Value, and why |
|---|---|---|
| The drawn skeleton is smoothed | `SkeletonSmoothing`, the overlay | A One Euro filter (Casiez, Roussel and Vogel, CHI 2012), display only: the analysis still receives MediaPipe's frame untouched. 0.3 Hz / 12 / 1 Hz, chosen by simulation: still jitter at most 47% of raw, a moving wrist within 0.012 frame widths, a sudden move caught in 4 frames |
| A repetition crosses short gaps | `RepFsm`, `TemporalConfig.maxBridgedGapMs` | **500 ms.** It bridges 45% of the 106 gaps under 3 s in the readouts recorded before the change (300 ms would bridge 27%). Past it, INSUFFICIENT_DATA as before, now ending at the last measured frame. The phase changes only on measured values, so a gap never counts anything |
| The glitch filter is looser | `TemporalConfig.outlierThreshold` | **20° → 35°.** Smooth movement read 2–20° a frame, glitches 50–174°; the injected 150° spikes of the ablation are still rejected |
| Amber is a flash | `LiveAnalysisBridge` | **1.5 s** after a repetition that did not count, then the figure shows tracking again; each failure flashes, and a counted one clears it at once |
| Unseen joints stay grey | `SkeletonColouring` | A joint below the visibility floor, and a segment to one, is drawn as not seen even when the figure is amber |
| The curl's at-rest edge | `BicepCurlProfile.topThreshold` | **155° → 145°** (at rest from 137°). Side-on the resting arm read 148–170°, so the old edge of 147° made a junk record, and an amber flash, on every wobble — about twenty in one set |
| The curl's time limit | `BicepCurlProfile.maxRepDurationMs` | **10 s → 20 s.** Nine deep curls timed out at 10 s with the arm still 50–121° bent on the way down |

**Tests changed on purpose, recorded rather than quietly edited:**
- `stepping_out_of_shot_mid_repetition_ends_it_as_insufficient_data` stepped out
  for five frames, which the bridge now crosses; it now stays out for longer
  than the limit, which is what leaving the shot means.
- `attention_overrides_every_joint_however_well_it_is_tracked` and
  `attention_wins_over_a_worse_tracking_state_on_a_segment` pinned the old
  colouring; they now pin amber over every joint that was seen and never over
  one that was not.
- The parity golden file was regenerated twice and each diff reviewed. The
  bridge: the squat scenario's one-frame-dropout repetition goes from
  INSUFFICIENT_DATA to VALID, and the plank holds gain 99 ms (the 26° recovery
  from the sag is accepted three frames sooner). The curl settings: 49 lines of
  the curl calibration's timings and curves, with no validity changed anywhere.

**Tests first.** The smoothing's jitter test failed against a pass-through stub.
The gap, amber and colouring tests failed first — the gap and amber ones to a
compile error, as their parameters did not exist yet. Both curl-settings tests
failed against the old values. **Mutations, all caught:** the smoothing
restarting a lost joint; no gap bridged; an abandoned repetition ending at the
unmeasured frame; amber that never expires; a second identical failure not
flashing; unseen joints drawn amber.

### What happened after the changes

With every change except the curl settings installed, one side-on set of the
owner's: **5 curls counted of about 15 real ones**, against 1 in every earlier
attempt put together. The others were the nine timeouts and one lost to
tracking, plus about twenty junk records from the resting arm. The curl
settings answer both, and **have not yet been tried on the phone**.

**Tests.** 1,703 pass, none fail and none are skipped: every unit test of
`:core:domain`, `:core:analysis` (JVM, JS, allocation, and Android debug and
release) and `:app` (debug and release), run on 2026-09-28 before this entry
was committed. On the phone, `KinesisDatabaseMigrationTest` passed 3 of 3, in
the run described above.

### For the owner

1. **Squats cannot count side-on** while the far knee is untracked: they need
   both knees. Options — count the near knee only, as the row does; an angled
   camera; or accept squats as untested. Each is a decision.
2. **The curl's declared view.** The profile says `FRONT_ON`, and front-on
   cannot measure the elbow in 2D; side-on counts the near arm only. No setup
   gate enforces either. A view decision, and the setup assistant (§10) is where
   it would be enforced.
3. **A demo phone needs preparing:** restarted, left to finish its background
   optimisation, cooled, other apps stopped, airplane mode on once signed in.
4. The earlier items stand: P3-22's four, and the RepFsm edit from another
   session in `local/foreign-edits/`. That session had announced P3-23 for gap
   tolerance; it committed nothing, and the owner directed the work here, so
   this entry takes the number.

### What is NOT verified

- The curl settings, and the skeleton smoothing's look, on the phone.
- Whether any of this holds on the back camera, in other light, or for another
  person; everything above is one person, one phone, one evening.
- Everything in P3-19's and P3-22's camera-session lists that this entry does
  not name. Added to it: **curls side-on with the new settings, 10 per arm**,
  counted by hand against the app.

**Scope of this entry.** It records a diagnosis read from saved data and live
recordings, a mistake and its repair, and provisional settings changed at the
owner's direction. It makes no accuracy claim: "5 of about 15" is one set, not
a rate.

---

## P3-24 — A solo check on the phone: curls, face-on squats, amber, and a pause test that did not happen

*2026-09-28*

Before the next camera session, the owner checked P3-23's changes alone: (1)
15–20 curls with the new settings, (2) squats facing the camera, (3) the amber
flash and short interruptions. Everything read is in
`local/solo-check-2026-09-28/` (gitignored):
- database copies from before (01:05) and after;
- the Phase B readout, recorded throughout;
- a three-minute screen recording of part 3, and frames taken from it.

The recording and frames show the owner and stay local, as P3-19's screenshots
do. The recording was deleted from the phone once the local copy was checked.

### Conditions

- **The build** on the phone was the one built for 3efcd86: the installed APK and
  the local one have the same SHA-256.
- **The phone, before the first set:**
  - thermal status 2, CPU 65 °C, skin 41 °C;
  - 207–271 MB free, load average 17–18;
  - a system AI widget using a third of a core.
  - It was not restarted.
- **Camera and rates.** Front camera, lite model. Results a second, as median
  (lowest–highest) per set: first curl set 15.5 (4.6–25.7), squats 17.1
  (10.3–21.4), second curl set 14.4 (7.1–16.6). MediaPipe's slowest single
  result per set: 485, 295 and 342 ms.
- **One clock for records and readout.** The camera stamps frames with the
  phone's elapsed-since-boot clock (`timestampSource` REALTIME). Boot time,
  from `date` minus `/proc/uptime`, read twice 35 minutes apart, agreed to 0.01
  s, so repetition records and readout lines sit on one clock.

### 1. Curls: 15 of 15

| | P3-23's set (2026-09-27) | Set 57 (tonight) |
|---|---|---|
| Curls, by the owner's count | about 15 | 15 |
| VALID | 5 | **15** |
| INCOMPLETE (time limit) | 9 | 0 |
| ABORTED records from the resting arm | 20 | **0** |
| INSUFFICIENT_DATA | 1 | 0 |

- **What each curl measured.** Each took 4.4–8.0 s. At rest the elbow read
  153–169°, at the far end 27–57°. Bending took 1.1–2.2 s and straightening
  1.4–2.9 s. The far arm was unmeasured in every record, as in P3-23.
- **What it does not show:**
  - The longest curl was 8.0 s, so the new 20 s limit was never needed.
  - The records cannot separate whether the lower at-rest edge (137°) is why
    no junk records appeared, or the owner's steadier pace.
  - It is one set and one arm. P3-23's "10 per arm" item is done for the right
    arm only.

### 2. Squats facing the camera

The owner did 4 squats they judged correct and some they judged wrong; their
legs were tired. Set 58 has 13 records:

- **#1–#4:** 0.1–0.4 s each, INSUFFICIENT_DATA, left knee only, made at
  01:34:07–09 while the owner got into position. The readout shows a 2.5 s
  left-knee gap and a 5.7 s right-knee gap closing then.
- **From 01:34:17,** 8 of the 9 records measured both knees throughout, and the
  readout shows no gap on either knee during any of them.
- **4 VALID, the first four.** Lowest readings: left 93.7°, 86.8°, 83.1°,
  40.9°; right 78.1°, 80.6°, 85.8°, 60.7°.
- **4 ABORTED, the next four.** The left knee's lowest reading was 95.6°,
  100.0°, 96.5° and 115.2°, against the 95° needed; the right knee reached
  81–94° each time.
- **#9:** 0.2 s, INSUFFICIENT_DATA, the right knee only, overlapping #10. The
  readout shows no gap then; **its cause is not established.**
- **Against the owner's count.** The 4 counted match the owner's 4 correct
  squats in number and in order. Which squats the owner judged wrong was not
  noted, so pairing them with the four ABORTED is only consistent, not shown.

**Face-on, the knee angle is a projection, not the knee's bend.** One reading
was 40.9°, and within a single squat left and right differed by up to 20° (#8:
40.9° against 60.7°). Counting worked from this view; range, tempo baselines
and asymmetry measured from it are not knee flexion. The profile declares
`SIDE_ON`, and nothing enforces either view.

**This now bears on P7.** Three frames of MM-Fit's `w01_rgb.mp4` show the squat
filmed face-on and the push-up side-on; the curls were not checked. If the
squat profile stays `SIDE_ON`, an MM-Fit squat result does not stand for it
(CLAUDE.md §22). The view is the owner's decision, and it is now due before P7
reports squats.

### 3. Amber: a flash that clears, and nothing stuck

**The recording.** The three-minute recording (180.1 s, 6,526 frames, about 36
a second) covers the second curl set, set 59, from 01:37:08 to 01:40:09. The
set ran 01:37:27–01:40:23; its last record, VALID, fell outside the recording.

**The method.** Each frame was scored for amber and for green: pixels of
`#FFB000`'s hue, and of `#39FF14`'s, at high saturation and brightness, in a
quarter-size frame.

**The results:**
- **Three flashes, lasting 1.68, 1.61 and 1.54 s.**
- **Each matches a record that did not count:**
  - #5 and #6, ABORTED: the half curls, whose far end was 73.7° and 79.9°
    against the 68° needed;
  - #13, INCOMPLETE: 20.0 s, the limit.
- **Lining the video up.** The first flash fixed the video's start, 0.61 s after
  the `date` read taken before `screenrecord` began. The other two flashes then
  start 0.00 s and 0.05 s after their records' ends.
- **Green comes back.** Within 0.5 s after each flash there is no amber, and
  green is back at 1,396 pixels or more, against 1,412–1,484 just before it.
- **No amber in any other frame,** across 11 counted curls.
- **The overrun.** A flash lasts 1.5 s plus 0.04–0.18 s: the timer runs 1.5 s of
  camera time from the failed record, and the colour changes on the next result
  after that.

### 3b. The short-interruption test did not happen

**The bridge was not exercised by a tracking loss.** Across both curl sets, the
readout shows no run of unmeasurable frames on the curling arm during any
repetition. The arm's only gaps fell between repetitions:
- 7.4 s between #12 and #13, when the owner walked up to the phone (the frames
  show them close to the lens);
- short ones before the first repetition;
- after the last repetition.

By the owner's account, the "interrupted" curls #7–#12 were curls done fast on
purpose. A hand is too small to hide the arm; the checklist item below uses a
book.

**One route the readout cannot see.** It counts gaps on the unfiltered angle,
so a frame the outlier gate rejects does not show as a gap, yet it reaches the
repetition machine as unmeasurable and the bridge carries it. Whether the
bridge carried any of the fast curls across gate rejections is not recorded.

### Lifting and lowering fast

**Tonight's times.** The owner lowered fast on purpose in #4, #7–#10 and #14,
and did #9 and #12 fast throughout. **All counted.**
- **Straightening:** 0.07 s in #7–#10, then 0.10, 0.23 and 0.33 s. 0.07 s is one
  result interval at about 14 a second: the arm was bent in one result and
  straight in the next. A movement faster than that is measured as one
  interval.
- **Bending:** 0.20–1.17 s in the fast curls.
- **Part 1, for comparison:** bending 1.1–2.2 s, straightening 1.4–2.9 s.

**The counter does not judge speed, by design.** It checks range, and a 0.5 s
minimum, as MM-Fit's rep counts do. Tempo is a Level 1 measurement stored with
every repetition. For a curl, `descentMs` is the elbow angle falling, which is
bending the arm, and `ascentMs` is straightening it.

**A "too fast" signal would be one of two things, and neither exists:**
- **Level 2:** faster than the owner's own calibration, a Layer 1 deviation rule;
- **Level 3:** a cited technique rule, P8.

Which, and whether amber should carry it, is the owner's decision. Amber today
means only "this repetition did not count".

### "NOT VISIBLE" during side-on curls

**Why it shows.** The line under the counter shows the overlay state's meaning.
The overall state is `UNTRACKED` whenever frame validity is `INVALID`, and
`ValidityGate` makes a frame `INVALID` if any one of the profile's required
landmarks is unmeasurable. The curl requires both arms. Side-on, the far arm is
hidden, so the line reads NOT VISIBLE throughout a set in which the near arm is
tracked, drawn green and counted.

**What it is.** Not a counting fault, but a display that contradicts itself.
It is not changed here: what the line should say depends on the curl's view
decision (P3-23, item 2).

### Two first readings of mine, corrected

- In conversation, from three frames seeked out of order, I said the free hand
  had taken over the wrist landmark and produced false counts. Frames decoded in
  order, and the owner's account, show real fast curls. Retracted.
- I first reported both knees measured in "all 9" face-on squat records. It is 8
  of 9: #9 carries one knee.

### Added to the camera-session list

Items 17–19 in P3-19's list.

### What is NOT verified

- The gap bridge, on a real tracking loss.
- The 20 s limit and the 137° at-rest edge, each on its own.
- The left arm with the new curl settings.
- The back camera, other light, other people: this is one person, one phone,
  one night.

**Scope of this entry.** It records a solo check, read from saved records, the
readout and a screen recording, and it makes no accuracy claim. "15 of 15" is
one set; "4 of 4" is the owner's own count against one set.

---

## P3-25 — P5: the export, one format on every platform

*2026-09-28*

The owner asked for P5 after P3-24. The brief:
- read P5's specification fresh;
- export every session, set and repetition from what Room holds, as JSON and/or
  CSV;
- make sure it carries what P7's rep-count and timing comparison needs;
- keep it in the shared core if it is pure transformation, with left and right
  never merged;
- pin it with a golden file, and add it to the JVM/JS parity suite;
- say plainly whether P7 can consume it.

### What P5 says, and where

**There is no written P5 specification in the repository.**
ZERO_DATASET_BUILD_PLAN's phase table says P5 is "unchanged" from
FINAL_ARCHITECTURE. FINAL_ARCHITECTURE's table says "unchanged" from the Master
Development Plan, which is not in the repository in any form: the two `.docx`
originals at the root are the other two documents.

What the markdown does say:
- **CLAUDE.md §15:** the hierarchy, fixed-length resampled signals, export as
  JSON and CSV, persistence local only.
- **FINAL_ARCHITECTURE:** "JSON and CSV export". Its fallback web client
  "ingests exported session JSON".
- **FEATURE_PLAN:** "JSON and CSV export of sessions from Profile, via the
  Android share sheet; nothing uploaded".
- **PROJECT_STATUS:** records and Room storage done, export not built.

So the specification used here is **both formats, of the whole hierarchy, out
through the share sheet**. It is recorded as assembled, not read. There is no
Profile screen yet, so the entry sits on HISTORY beside the licences.

### What was built

**Shared core (`commonMain`):**
- **`SessionExport`** (package `export`):
  - `json(sessions, baselines, exportedAtEpochMs)` writes one document;
  - `csv(sessions)` writes three tables: sets, reps and holds.
  - The format is CONTRACTS.md §10, new in this entry.
- **`JsonNumber`**: one text for each number on every target (decision 2).
  The facade's JSON writer now uses it too.

**App:**
- **`ExportFiles`** writes the four files to the cache's `exports/` folder,
  replacing the last export.
- **`[ EXPORT DATA ]`** on HISTORY, with the line "They leave the phone only if
  you choose where to send them" (CLAUDE.md §16).
- **`MainActivity`** writes the files off the main thread and opens the share
  sheet through a `FileProvider`:
  - `exports/` in the cache only (`res/xml/export_paths.xml`);
  - not exported;
  - one export at a time.
- **No new dependency (the §17 check).** `FileProvider` is in `androidx.core`,
  already a direct dependency. It needs no network and no permission.

### Decisions the owner should know about

1. **The specification was assembled**, as described above.
2. **Every number the core writes now has one text on every target.**
   - **The problem.** The facade wrote numbers with `Double.toString()`, which
     the JVM and JavaScript write differently: `100.0` against `100`, and
     `1.7905376974852E12` against `1790537697485.2`. The parity suite compared
     values, so both passed; an exact golden file cannot.
   - **The fix.** `JsonNumber` takes the platform's digits and lays them out by
     ECMAScript's Number::toString rules. On JavaScript that changes nothing.
     On a JVM from JDK 19 on, whose digits are JavaScript's (the build uses JDK
     25), the text is now identical.
   - **Checked.** The parity golden file was regenerated. 71 of its 212 existing
     lines changed text, and parsed back, **none changed a value**: every line
     was compared, number by number.
   - **Baselines already stored** on a phone keep their old text and read the
     same.
3. **An exported repetition is the facade's own record, and a baseline is
   `BaselineJson`'s.** No second shape for either: an exported set's `reps` can
   go straight into `computeBaseline`.
4. **What the export does not carry:**
   - `AnalysisResult`, which is not stored and is recomputed from a record and
     a baseline, both of which are exported;
   - when a set ended, which nothing records;
   - which account a session belongs to: local sessions are not partitioned by
     account (P3-22), so an export is every session on the phone plus the
     signed-in user's baselines;
   - the 64-point curves in CSV: they are in the JSON only.
5. **Times.**
   - `...EpochMs` fields are UTC epoch milliseconds.
   - A record's `startMs` and `endMs` are on the clock that stamped the frames,
     and the export does not relate that clock to the epoch, so they are
     compared only within one set.
   - On this phone that clock is elapsed-since-boot (P3-24). The format does
     not depend on it.
6. **`formatVersion` 1.** It is raised whenever a field is added, removed,
   renamed or changes meaning, and the golden file is regenerated with it.

### Tests

**`JsonNumberTest` (8).** JavaScript's layout for:
- whole numbers, fractions and epoch milliseconds;
- where it switches to an exponent, for small and large numbers;
- negative zero, and `null` for `NaN` and the infinities;
- 2,004 values reading back exactly.

Against a stub that used `toString()`, it passed 8 of 8 on JavaScript and failed
5 of 8 on the JVM: the difference it exists to remove.

**`SessionExportTest` (17).** It runs against `SessionExportFixture`:
- two sessions and five sets;
- an alternating set whose idle arm is unmeasured;
- a half-seen simultaneous repetition;
- a set that recorded nothing;
- both hold kinds, and a hold from before P3-22;
- a negative UTC offset;
- one baseline of each kind.

What it checks:
- **The golden file, byte for byte** (the JSON and all three CSVs), on the JVM,
  JavaScript and both Android variants.
- **Counts and sets:**
  - record and valid counts;
  - a set with no records exported with a count of 0;
  - set numbering.
- **Values:**
  - left and right in their own JSON fields and CSV columns;
  - "not measured" as `null` and an empty cell, never 0.
- **Records:**
  - each repetition is the facade's record, and reads back whole;
  - a hold set carries no repetition list;
  - baselines are in `BaselineJson`'s form.
- **CSV mechanics:** a cell for every column, CRLF endings, RFC 4180 quoting.
- **Edges:** an export of nothing, and epoch times in plain digits.

Against the stub, 16 of 17 failed. The one that passed compared an empty golden
file with an empty export, which is why one test checks that the golden file
holds every kind of record.

**Parity: `sessionExport`.** A squat set (three valid, one turned back, one
with a knee unseen) and a plank hold, produced by the facade on each target,
exported, and compared with the golden lines: counts exactly, continuous values
within 1e-9 relative (CONTRACTS.md §6). It adds 12 lines.

**App tests:**
- **`ExportFilesTest` (3):**
  - the four files hold exactly the core's export;
  - the names;
  - a new export replaces the last.
- **`ExportProviderGuardTest` (2):**
  - the provider is the app's only one, not exported, and grants per share;
  - its paths reach the cache's `exports/` and nothing else.

**`SharedCoreSourceGuardTest`:** the `export` package is now classified, with
`SessionExport.kt` on the post-rep side and the reason given.

**Mutations, all caught, every file restored byte for byte (11):**
- CSV left and right swapped;
- `validCount` counting every record;
- "not measured" written as 0;
- an empty set dropped from `sets.csv`;
- CSV quoting skipped;
- the exponent switch one place early;
- whole numbers keeping `.0`;
- the last export left in the cache;
- the reps table written into the sets file;
- the provider exported;
- the provider sharing the whole cache.

**Two harness runs that didn't test anything.** The two provider mutations first
came back "not caught", because Gradle skipped the unit-test task as up to date:
the manifest and paths file are read from disk and are not task inputs. With
`--rerun`, the first was caught. The second was then applied by hand: it failed
at the paths assertion, and the file was restored with a clean diff.

**Totals: 1,818 test executions, none failed, none skipped** (`assemble
allTests :app:testDebugUnitTest :app:testReleaseUnitTest`):

| Module | JVM | JS | Android debug | Android release | Allocation |
|---|---|---|---|---|---|
| `:core:analysis` | 341 | 330 | 330 | 330 | 5 |
| `:core:domain` | 33 | 33 | 33 | 33 | — |
| `:app` | — | — | 181 | 169 | — |

P3-23 had 1,703. The "one export at a time" guard came after this run. Both
`:app` test tasks and `assembleDebug` were rerun after it and passed, with the
counts unchanged.

### On the phone

- **The install.** The database was backed up first: 59 sessions, 59 sets, 134
  repetitions, 4 holds, no baselines, integrity check "ok". The build was
  installed with `installDebug`; the first-install time was unchanged and the
  data kept.
- **The first export.** At 02:33:10.8 a tap on `[ EXPORT DATA ]`. At 02:33:24.6
  the four files were written, and the share sheet opened carrying four
  `content://` links.
- **About 14 s from tap to share sheet, with nothing on screen meanwhile.**
  - Measured once: on a warm, loaded phone, on a debug build just installed.
    Not investigated.
  - A second tap would have emptied the folder the first export was writing,
    so an export now shows `[ EXPORTING... ]` and ignores taps until it
    finishes.
  - That guard was built after the phone run and is not yet on the phone.
- **The share, end to end, by the owner's own choice.** The owner, holding the
  phone, picked WhatsApp in the share sheet and a chat; a CSV from WhatsApp
  was then opened in two spreadsheet apps.
  - I first read the spreadsheet on screen as my own doing, and said so. The
    phone's activity log shows the owner's taps, and I corrected it.
- **The files, pulled from the app's cache and checked against the database
  backup, field by field:**
  - 59 of 59 sessions and 59 of 59 sets;
  - all 134 repetitions, and their 17,152 curve points;
  - 4 of 4 holds;
  - **no difference.**
  - 36 valid repetitions, as the dashboard shows.
  - No number in E notation: epoch times, which Java's `toString` writes in E
    notation, came out as plain digits on Android's runtime too.
  - Sizes: JSON 284,194 bytes; sets.csv 5,689; reps.csv 21,436; holds.csv 571.

### Is it ready for P7?

**Yes for the rep-count study, with two things P7 must supply itself.**

**Ready.**
- Every set's record and valid counts, including sets that counted nothing, so
  "counted against labelled, per set" can be computed directly.
- Each repetition's:
  - validity, start and end;
  - side;
  - per-side range, peak and trough;
  - descent and ascent times;
  - 64-point curves.

  Those are the inputs of Studies 1–3.
- One format, exact across platforms, that P7's harness can call directly on
  the JVM: records from `KinesisEngine`, read by `EngineJson.readRep` into
  `SessionRecord`s, then `SessionExport.json`.

**P7 must bring its own set identity and window.**
- MM-Fit's labels give each set's start and end frame. The export has no field
  for an analysed window or for a source's set id.
- Version 1 carries both through `sessionId` (the workout) and `setIndex` (the
  label's order), with the harness stamping frames in video time so that
  `startMs` and `endMs` are video milliseconds.
- Whether the window belongs in the file is `formatVersion` 2, decided in P7
  and not guessed here.

**Two of Study 1's metrics are not in a record.**
- **Trunk angle range** needs a second signal the engine does not record.
- **A left–right symmetry index** is a named reducer over two exported columns,
  computed in the analysis and never stored as one value (CLAUDE.md §3).

Rep-to-rep consistency comes from the curves.

**And the squat's view is due before P7 reports squats:** MM-Fit films them
face-on (P3-24).

### What is NOT verified

- The `[ EXPORTING... ]` guard, on the phone.
- An export carrying baselines, on the phone: this phone has none. The fixture
  and golden file cover both kinds.
- The export's speed: one measurement, not investigated.
- Android's `Double.toString` digit for digit against the JVM's: the phone's
  export read back value for value, but its text was not compared with a JVM
  export of the same data.
- Any reader other than Python's `json` and `csv` modules and the two
  spreadsheet apps the owner opened.

**Scope of this entry.** It builds P5's export and runs it once on the phone.
It makes no accuracy claim: "59 of 59" is a fidelity check of one export against
one database.

---

## P3-26 — Owner decisions: telling the user a repetition was fast, after P7

*2026-09-28*

The owner: "when a user is doing an exercise fast or incorrect we have to tell
them... at least using a trusted source". Asked when, what and how, the owner
decided the three points below.

### 1. After P7

The plan's order stands: P7, the MM-Fit evaluation, comes first. Then P8, the
form engine, where this feedback lives.

### 2. Two messages, both about speed

- **"Faster than your usual."**
  - Claim Level 2, against the user's own calibration baseline: P6's per-side
    descent and ascent timings.
  - Every exercise, once it is calibrated.
  - No outside source is needed: the user's own repetitions are the reference.
- **"Faster than the beginner tempo ACSM recommends."**
  - Claim Level 3, Tier A only. Tier B and C profiles cannot construct a
    Level 3 finding at all (CLAUDE.md §8).
  - The source was read in full and is quoted below.
- **Not chosen now: cited form rules** ("incorrect form"). None has a verified
  source yet, and each needs one quoted before it ships (CLAUDE.md §12a).
  Candidates noted but not checked:
  - FitnessGram's 90° push-up criteria, seen only second-hand;
  - the NSCA technique guides;
  - Schoenfeld (2010) on squat depth, which classifies depth rather than naming
    a fault.

### 3. Amber carries it too

Amber has meant only "this repetition did not count" (P3-23, P3-24). It will
also mean "look at this", with the message saying which of the two it is.

The overlay's `ATTENTION` state already anticipated this: "When P8's technique
rules exist, a Level 3 finding lands here too" (`SkeletonPalette.kt`).

### The source, read in full

**ACSM position stand, "Progression Models in Resistance Training for Healthy
Adults"** (Ratamess et al.), *Medicine & Science in Sports & Exercise*
41(3):687–708, 2009.

The quotes below were read from the full text, a copy of the PDF whose text was
extracted, not from a summary:
- From the summary: "...performed at a moderate contraction velocity (1–2 s
  CON; 1–2 s ECC)."
- "Evidence category A. For untrained individuals, it is recommended that slow
  and moderate velocities be used."
- "Evidence category B. For intermediate training, it is recommended that
  moderate velocity be used for RT."
- And, against a stronger claim: "Over 6 wk of RT in untrained individuals, it
  was shown that training at a faster velocity (1:1) led to [approximately]
  11% greater strength increases than training at a slower velocity (3:3)."
  The bracketed word stands for a symbol the text extraction could not read.

**So the message may say a repetition was faster than the recommended beginner
tempo. It may not say the repetition was wrong, unsafe or ineffective**, and
the source's own evidence is why.

### What P8 must solve before either message ships

- **The timer measures less than the phase.**
  - `descentMs` and `ascentMs` run between the repetition machine's thresholds,
    not over the whole movement: for the curl, from 137° to 68°. They therefore
    read shorter than the phase ACSM means.
  - A plain "under 1 s" rule would flag controlled repetitions.
  - The threshold has to allow for this, and be checked on the phone against
    repetitions timed by hand.
- **Direction differs by exercise.**
  - For a curl, `descentMs` (the elbow angle falling) is the lift; for a squat
    it is the lowering.
  - So ACSM's CON and ECC map onto the two fields differently for each
    exercise.
- **One frame is the resolution:** about 70 ms, at 14 results a second (P3-24).
- **Level 2 needs a baseline.** Without a calibration, only the Tier A cited
  message can speak.
- **Wording.** Every Level 3 message names its source. The report states the
  limitation in CLAUDE.md §12a's words.

**Scope of this entry.** It records decisions and one verified source. It
builds nothing.

---

## CLOSED — Bicep curl repetition semantics

*Raised 2026-09-19. Closed 2026-09-20 by P3-1.*

MM-Fit performs bicep curls **alternating arms**. Kinesis has not defined
whether one repetition means one arm or both.

If the Kinesis profile counts a two-arm curl as one repetition and MM-Fit counts
each arm, the rep-count comparison is wrong by a factor of two — and it will
look like a threshold-tuning problem rather than a definitional one, which is
the expensive way to discover it.

This cannot be resolved from the documents. It requires inspecting MM-Fit's
actual label file once the dataset is downloaded. Whatever is decided must be
pinned by a test, per `CLAUDE.md` §18.

**Closed.** The dataset was extracted to `data/mm-fit/` and its label files read
directly. MM-Fit counts **one repetition per arm**. The evidence, the decision and
its consequences are in **P3-1**.

---

## CLOSED — The old prototype has not been inspected

*Raised 2026-09-19. Closed 2026-09-20 by P2-2.*

`E:\kinesis_old` contains the previous prototype (modules: `app`, `core`,
`desktop`, `docs`). Access was not granted during P0, so the real angle and FSM
implementations were not read.

FINAL_ARCHITECTURE §3 specifies that the G1 spike should port *the prototype's
actual* angle and FSM code rather than a stand-in. The P0 spike used a
structurally representative implementation instead — see `G1_SPIKE_RESULT.md`.

**Closed.** Access was granted at the start of P2 and the prototype was read
read-only. The findings — what is worth porting, what is not, and why for each —
are recorded in **P2-2**. The FSM itself is inspected but not ported here: it is
P3 work, per P2-1.

---

## CLOSED — Is `app/` the instrumentation harness or the product shell?

*Raised in REPOSITORY_STRUCTURE.md. Closed 2026-09-20 by P2-3.*

REPOSITORY_STRUCTURE.md recommends deciding at the start of P1 whether `app/` is
first a bare instrumentation harness (CameraX, MediaPipe, skeleton draw,
frame-rate readout, no product UI) or the product shell, given that P4 is the
device reality check in week 3.

P1 did not decide it, because P1 was scoped to shared core types and touched
`app/` only to add the pose adapter and its regression test.

**Closed.** Harness first — see **P2-3** for the decision, its reasoning, and the
CameraX/MediaPipe dependency check it still requires.

---

## AUDIT SUMMARY

*Performed 2026-09-20.*

A full-codebase audit was performed to ensure all implementations comply with the documented project rules. The following checks were executed and resolved:

1. **Clean Build:** `gradlew assemble allTests :app:testDebugUnitTest` passed with zero errors or warnings (once JDK 17 environment variables were configured).
2. **Non-Negotiables:** Evaluated left/right bilateral signal reductions, analysis logic placements (must be in shared core, not app/), numeric types (Double, not Float or Long for analysis), and rule source references. **All clean.**
3. **Registry Completeness:** Verified 17 exercise entries mapped to 15 profiles and 2 variants. Addressed discrepancy in P3-8 documentation regarding exactly how many exercises were flagged vs resolved in §4 and §5. Tests fully sweep all 17. **Clean.**
4. **File-Integrity Spot Check:** No signs of file corruption or incomplete git pulls. **Clean.**
5. **Dependency / License Consistency:** Discovered that Jetpack Compose dependencies were added to `app/build.gradle.kts` despite no formal documentation in `DECISIONS.md`. This is flagged for the next UI integration phase, but as a pure data/logic audit, no dependencies violated existing rules.
6. **Exception-Handling Audit:** Fixed a silent failure in `HealthConnectActivityContextSource.kt`. General `catch (error: Throwable)` blocks were catching and swallowing internal exceptions like `NullPointerException` and `IllegalArgumentException`, misclassifying them as external Health Connect unavailabilities. 
    - **Fix:** Switched catch blocks to `Exception` and explicitly rethrew `CancellationException`, `NullPointerException`, `IllegalArgumentException`, and `ClassCastException`.
    - **Verification:** Added `HealthConnectExceptionTest.kt` to prove that internal bugs (`NullPointerException`) propagate out of the method instead of returning `DailyMetric.NoData`.
7. **Dead Code:** Cleaned up unused imports (e.g. `import kotlin.math.abs` in `FilterAblationTest.kt`). No stray `TODO`, `FIXME`, or commented-out code blocks were found. **Clean.**
8. **Dependency / License Consistency Follow-up:** Removed the undisclosed `kotlin("reflect")` dependency from `app/build.gradle.kts`. Rewrote `HealthConnectExceptionTest.kt` to use a manual `FakeHealthConnectClient` instead of `java.lang.reflect.Proxy`.
9. **Deferred Items:** Addressed "Open — bicep curl repetition semantics" by updating `CONTRACTS.md` Section 9 to explicitly link to the closure in `DECISIONS.md` P3-1.
10. **BilateralRepTracker Edge Case:** Addressed the flagged-but-untested single-pending-slot edge case in `BilateralRepTracker.kt`. When `SIMULTANEOUS` and `BOTH` were active, if one side emitted multiple rep cycles while the other side emitted none, the `pendingLeft` or `pendingRight` slots were silently overwritten, losing the cycles instead of flushing them as unpaired `INSUFFICIENT_DATA` repetitions.
    - **Fix:** Updated `BilateralRepTracker.onFrame` and `finish` to flush any existing orphaned cycle using `flushUnpairedSimultaneous()` before overwriting the slot with a new cycle.
    - **Verification:** Added `a_simultaneous_repetition_flushes_pending_slots_when_overwritten` test in `ArchetypeProfileTest.kt` which generates two left cycles without any right cycles, proving that the first cycle is flushed correctly.

The audit is complete and the project remains structurally sound per its own constraints.

### Audit Close-out: Final Checks

1. **Front Raise Resolution:** `FINAL_ARCHITECTURE.md` §4 flagged "front raise" and §5 explicitly cut it ("resolves the front raise (cut)"). However, reviewing `DECISIONS.md` (specifically around P0-5, which restored the 17-entry target list), there is **no recorded decision** formally acknowledging or ratifying the removal of Front Raise from the project scope. It was simply dropped implicitly by following the §5 table. 
   - **Open Item:** The omission of Front Raise from the library was executed without a recorded decision in `DECISIONS.md`. This is an open gap.
   - **Closed by P3-19 §9:** the reason is `FINAL_ARCHITECTURE.md` §5 (no single camera view sees the primary signal), now ratified; Front Raise was never in the code.
2. **Non-negotiables Sweep (Verified from Codebase):**
   - **Bilateral averaging:** Clean. No `BilateralSignal` is collapsed to a scalar without a named reducer (like `asymmetry()`, `meanExplicit()`). `BilateralRepTracker` keeps left and right independent.
   - **Analysis logic placement:** Clean. No scoring, rep counting, or analysis logic lives in the Android `app/` module.
   - **Real-time path allocations/loops:** Clean. The `BilateralRepTracker.onFrame` and `RepFsm.step` pipelines are `O(1)` with no per-frame allocations or history re-iteration.
   - **Numeric types:** Clean. `Double` is used exclusively for analysis throughout `core/domain` and `core/analysis`. No `Float` or `Long` are used in analysis paths.
   - **Claim-level enforcement:** Clean. `TechniqueAuthority` prevents lower-tier profiles from emitting top-tier findings.
   - **Mirroring correction:** Clean. Handled strictly in the platform adapter layer.
   - **Source references:** Clean. Every `TechniqueFault` construction requires and provides a non-empty `sourceRef`.
   - **Landmark format:** Clean.
   - **Predicted accuracy:** Clean. No estimated or predicted accuracy figures exist in the codebase or docs.
3. **Exercise-registry Completeness:** Clean. `ExerciseLibrary.kt` contains exactly 17 entries (15 profiles + 2 variants). Test suites like `TierAProfileTest`, `TierBProfileTest`, and `TierCProfileTest` sweep the full library. 
4. **File-Integrity & Final Clean Build:**
   - Git diff of `DECISIONS.md` and `CLAUDE.md` shows no lost decisions or unrecorded reversions across project history.
   - Executed `gradlew clean` followed by `gradlew assemble allTests :app:testDebugUnitTest`. 
   - **Result:** The exact total test count remains **888 tests** across all modules with **0 failures, 0 errors, 0 warnings**.

This closes the audit. All rules and constraints have been rigorously enforced.
