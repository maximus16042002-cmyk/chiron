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
