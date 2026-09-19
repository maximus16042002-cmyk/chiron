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

**The wrapper scripts are absent.** `gradlew`, `gradlew.bat` and
`gradle/wrapper/gradle-wrapper.jar` are not in the repository — only
`gradle-wrapper.properties` survived the P0-9 overwrite incident. A fresh clone
cannot build. P1 was run against the cached Gradle 9.3.0 distribution directly.
Regenerating the wrapper (`gradle wrapper --gradle-version 9.3.0`) and
committing it is outstanding work.

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

## OPEN — Bicep curl repetition semantics

*Raised 2026-09-19. Must be closed before P3.*

MM-Fit performs bicep curls **alternating arms**. Kinesis has not defined
whether one repetition means one arm or both.

If the Kinesis profile counts a two-arm curl as one repetition and MM-Fit counts
each arm, the rep-count comparison is wrong by a factor of two — and it will
look like a threshold-tuning problem rather than a definitional one, which is
the expensive way to discover it.

This cannot be resolved from the documents. It requires inspecting MM-Fit's
actual label file once the dataset is downloaded. Whatever is decided must be
pinned by a test, per `CLAUDE.md` §18.

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
