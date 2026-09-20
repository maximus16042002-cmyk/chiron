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
resolves the front raise (cut) and the overhead triceps extension (cut). So §5
carried out its instruction for two of the three exercises §4 flagged, and
skipped only the lunge (while also addressing one unflagged exercise, the
Overhead Triceps Extension). That narrows P3-7's finding from "§5 is unreliable"
to "§5 has exactly one omission", which is worth knowing.

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
8. **Deferred Items:** Addressed "Open — bicep curl repetition semantics" by updating `CONTRACTS.md` Section 9 to explicitly link to the closure in `DECISIONS.md` P3-1.

The audit is complete and the project remains structurally sound per its own constraints.
