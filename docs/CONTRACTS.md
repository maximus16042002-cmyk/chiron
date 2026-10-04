# Kinesis — Core Contracts

*P0, 2026-09-19. Specification only — none of this is implemented yet.*

These are the type-level decisions that P1 implements. They are written down
before any code because each one is expensive to retrofit and each one guards a
failure that is silent when it happens.

Signatures below are specification, not final source. P1 may change names; it
may not change the semantics without a new entry in `DECISIONS.md`.

---

## 1. BilateralSignal

Guards: bilateral signals being silently averaged (`CLAUDE.md` §3).

```kotlin
data class BilateralSignal(val left: Double, val right: Double)
```

**Required semantics**

- No implicit conversion to `Double` exists. No `toDouble()`, no operator
  overload, no property that reads as "the value."
- Every reduction is a named function stating what it means: `asymmetry()`,
  `meanExplicit()`, `dominant()`, `weaker()`. A caller reading the code must be
  able to see which side won and why.
- Anything derived from a bilateral quantity is itself bilateral until an
  explicit reducer is applied. Angles, ranges, velocities and tempos are all
  bilateral by default.

**Permanent test.** No code path collapses a `BilateralSignal` to a scalar
without passing through a named reducer.

---

## 2. ClaimLevel and Finding

Guards: a profile making a claim it is not entitled to make (`CLAUDE.md` §8).

```kotlin
enum class ClaimLevel { L1_MEASUREMENT, L2_DEVIATION, L3_TECHNIQUE }

sealed interface Finding {
    val level: ClaimLevel
    val displayText: String     // composed in commonMain, never in the UI
    val ruleVersion: String
}

data class Measurement(...)    : Finding  // level is always L1
data class Deviation(...)      : Finding  // level is always L2
data class TechniqueFault(...) : Finding  // level is always L3
```

`TechniqueFault` additionally carries:

```kotlin
val sourceRef: String   // citation for the published threshold it applies
```

**Required semantics**

- `level` is fixed per type. It is not a constructor parameter a caller can set.
- `displayText` is generated in `commonMain` from measured values. The UI renders
  what it is given and composes no sentences of its own. This is what stops UI
  copy from bypassing the tier.
- A `TechniqueFault` with no `sourceRef` does not compile or does not construct.
  Per `DECISIONS.md` P0-6, every Layer 2 rule is traceable to a published
  threshold.

---

## 3. ExerciseProfile tier gating

```kotlin
enum class Tier { A, B, C }

interface ExerciseProfile {
    val tier: Tier
    val maxClaimLevel: ClaimLevel   // A -> L3_TECHNIQUE; B and C -> L2_DEVIATION
    val requiredView: CameraView
    val mode: AnalysisMode          // REPETITION or ISOMETRIC_HOLD
}
```

**Required semantics**

Construction is gated, not filtered. The form engine must have **no code path**
that produces a `TechniqueFault` for a profile whose `maxClaimLevel` is
`L2_DEVIATION`. A Tier B profile cannot emit a Level 3 finding even if a rule
tries to. Filtering results afterwards is not acceptable — it leaves the unsafe
path in the codebase.

**Permanent test.** Across the entire rule set, no Tier B or Tier C profile
produces an L3 finding. Runs in `commonTest`, on both targets, forever.

**Tier assignments as of P0** (`DECISIONS.md` P0-3):

| Tier | Profiles |
|---|---|
| A | Squat, Push-up, Bicep Curl, Lateral Raise *(seated)*, Glute Bridge |
| B | Shoulder Press *(seated)*, Row *(near side only)*, Lunge, Hip Abduction, Knee Raise, Sit-up, Leg Raise |
| C | Wall Sit, Plank, Side Plank |

Variants inherit their parent's profile, calibration and claim ceiling: Goblet
Squat → Squat, Reverse Lunge → Lunge.

---

## 4. The mirroring contract

Guards: front-camera input silently inverting anatomical left and right
(`CLAUDE.md` §4). Named in FINAL_ARCHITECTURE §12-D as one of the three highest
technical risks, and the one that fails most quietly.

**The contract.** The platform pose adapter converts camera coordinates into
anatomical left and right before anything reaches the shared core.

```kotlin
// In platform code only:
fun PoseAdapter.toAnatomical(raw: RawLandmarks, facing: CameraFacing): Landmarks
```

- The shared core has no knowledge of `CameraFacing`. There is no parameter, no
  flag, and no branch anywhere in `commonMain` that depends on which camera
  produced the frame.
- `Landmarks` entering the core is always anatomical. A landmark named `LEFT_KNEE`
  is the subject's left knee, regardless of which side of the image it appears on.

**Permanent test.** A known landmark set, passed through the adapter with
`FRONT` and with `BACK` facing, produces the same anatomical assignment. This
test exists from P1 and is never deleted.

**Units** (added by `DECISIONS.md` P3-16). `x` and `y` in a `Landmarks` share
one unit: the width of the image the pose model was given. `x` runs 0..1 across
it and `y` runs 0..height/width down it, so a pixel is the same length in both
directions and an angle computed from them is the angle in the image. `z` is as
MediaPipe reports it, on roughly `x`'s scale. The platform adapter makes this so,
from the frame's pixel size; the core never learns a frame had a shape. **Every
producer of `Landmarks` must apply the same correction with its own frame size**
— the P7 MM-Fit pipeline included — or its angles are not the phone's angles.
Pinned by `AspectCorrectionRegressionTest`.

---

## 5. The JS facade

Guards: rich Kotlin types crossing the JS boundary (FINAL_ARCHITECTURE §3).
Designed in P1, not retrofitted in P11 — see `DECISIONS.md` P0-2.

```kotlin
@JsExport
class KinesisEngine {
    fun configure(profileJson: String)             // {"id": "squat"}
    fun pushFrame(timestampMs: Double, landmarks: DoubleArray, visibility: DoubleArray): String?
    fun stateJson(): String                        // P3-19
    fun finishSet(): String                        // P3-19
    fun computeBaseline(repsJson: String): String                   // P3-22
    fun analyzeRep(repJson: String, baselineJson: String): String   // P3-22
}
```

**Required semantics**

- The entire exported surface is primitives, `DoubleArray`, and JSON strings.
  No Kotlin collections, no sealed classes, no data classes, no nullable rich
  types cross the boundary. A value that is `NaN` inside Kotlin - not measured -
  is JSON `null`.
- `pushFrame` returns `null` on the overwhelming majority of calls, and on a
  frame that completes repetition records, a **JSON array** of them. Usually one,
  but one frame can complete two - a half-seen repetition reported alone, then a
  paired one (P3-19 amended this from "a JSON `RepRecord`"). A hold reports only
  at `finishSet`.
- `stateJson` is the discrete state after the last frame: rep count and each
  side's phase, or the hold's state and time held.
- `finishSet` ends the set: a JSON array of what that completes, possibly
  empty; for a hold, its one `HoldRecord`.
- `computeBaseline` takes a JSON array of the configured exercise's records,
  exactly as `pushFrame` and `finishSet` return them, and returns a JSON
  outcome: `calibrated` with the `baseline`, or `tooFewRepetitions` /
  `holdNotUsable` with the counts and a plain reason. No baseline is an answer,
  not an exception. The facade has no clock or session, so the baseline's
  `createdAtEpochMs` and `sourceSessionId` are `null` (DECISIONS.md P3-22).
- `analyzeRep` takes one record and a baseline (or `null`) and returns a JSON
  `AnalysisResult`: the record's values beside the baseline's, and a `findings`
  array that stays empty until P8's rules exist.
- Everything rich stays inside Kotlin. The `.d.ts` is small and hand-checkable.
- The facade is the *only* `@JsExport` surface in the project. It lives in
  `commonMain` (P3-19), so the JVM runs the same facade JavaScript does.

This shape also makes the parity suite trivial: identical JSON in, identical
JSON out, on both targets.

---

## 6. Numeric contract

Measured at P0 — see `G1_SPIKE_RESULT.md` for the evidence behind these numbers.

| Output class | Guarantee |
|---|---|
| Rep counts, phase sequences, discrete fault flags | **Exact** across targets |
| Angles, ROM, velocities, DTW distances | Agree within **1 × 10⁻⁹ relative** |

- `Double` throughout the shared core. Not `Float`, not `Long`.
- Never assert exact equality on a continuous metric in a cross-platform test.
- Never rely on `Float` precision in a threshold comparison.

Measured worst-case divergence at P0 was 2.8 × 10⁻¹⁴ relative, and 4.3 × 10⁻¹⁴
degrees on a computed joint angle. The 10⁻⁹ tolerance is deliberately
conservative and should not be tightened on that evidence, because it was
measured on one engine and one CPU architecture.

---

## 7. The real-time / post-rep boundary

Guards: silent frame-rate regressions (`CLAUDE.md` §6, §7).

**Real-time path**, roughly 30×/second, O(1) per frame:

```
landmarks -> normalise -> validity -> current-frame kinematics
          -> fixed-size ring buffer -> FSM step -> phase + count
```

**Post-rep path**, roughly 0.3×/second, may touch the whole repetition:

```
RepRecord -> resample(N=64) -> features -> rules -> DTW -> ML -> explanation -> persist
```

**Structural enforcement.** Models accept a `RepRecord`. A `RepRecord` only
exists once a repetition has completed. There is therefore no API through which
a model can be run per frame — the prohibition is a consequence of the type
signatures rather than a rule anyone has to remember.

**`RepRecord.movementContext`** (`DECISIONS.md` P3-52, recorded here by P3-59): an
optional `MovementContext`, the exercise verifier's input. Nine signals (elbow
flexion, upper arm against trunk, hip and knee, each left and right, and the
trunk's lean) resampled to 16 points over the repetition, plus its duration: 145
numbers. It is filled on the live path from fixed-size ring buffers and is `null`
otherwise. It is **not** stored in Room, synced or exported; a repetition read
back from storage has none. The verifier that reads it is built and not shipped.

---

## 8. The camera-view contract

Every profile declares `requiredView`. Before scoring begins, the setup gate
validates framing, body visibility, approximate distance, view angle and camera
stability.

When required conditions are clearly invalid, scoring and technique analysis are
**blocked** rather than producing confident nonsense. Measurement may continue if
it is honest to do so; technique judgement may not.

**As built (`DECISIONS.md` P3-55, corrected by P3-59).** In the app, a wrong camera
view (or nobody in shot) held for half a second blocks scoring: the tracker and the
hold analyser see nothing until the view has been right for a third of a second.
Distance, a cropped head and unseen feet only advise. Occluded landmarks are not a
setup verdict, because the tracker already refuses an unseen side on its own. The
view is not judged for a front-on lying profile (the side plank), which the
estimator cannot see. Camera stability is not checked.

---

## 9. Open — bicep curl repetition semantics

**(Closed by `DECISIONS.md` P3-1)**

**Blocks P3.** MM-Fit performs bicep curls alternating arms. Kinesis has not
defined whether one repetition is one arm or both.

Get this wrong and the rep-count comparison against MM-Fit is out by a factor of
two, presenting as a tuning problem rather than a definitional one.

Resolve by inspecting MM-Fit's label file once downloaded, record the decision in
`DECISIONS.md`, and pin it with a test.

---

## 10. The export format

*Added by `DECISIONS.md` P3-25 (P5).*

Guards: a file format that P7's harness and any other reader rely on, changing
silently.

`SessionExport`, in `commonMain`, turns a list of `SessionRecord`s into one JSON
document and three CSV tables. The app writes them to files and hands them to
the share sheet; P7's harness can call it directly on the JVM.

**JSON:**

```
{ "format": "kinesis-export", "formatVersion": 2, "exportedAtEpochMs": <number>,
  "sessions": [ { "sessionId", "startedAtEpochMs", "utcOffsetMinutes",
                  "sets": [ { "setIndex", "exerciseId", "mode", "startedAtEpochMs",
                              "recordCount", "validCount",
                              "reps": [ <record> ] | "holds": [ <hold> ] } ] } ],
  "baselines": [ <baseline> ] }
```

- **Sets.** `mode` is `repetition` or `hold`. `setIndex` counts from 1, in the
  order performed.
- **Counts.** `recordCount` counts every record, `validCount` only the `VALID`
  ones: the set's rep count, or its held count. A set with no records is still
  exported, with both counts 0.
- **Records.** Each `<record>` and `<hold>` is exactly what the facade returns
  (section 5). Each `<baseline>` is exactly `computeBaseline`'s `baseline`. An
  exported set's `reps` can go straight back into `computeBaseline`.
- **Section 11 of CLAUDE.md.** A repetition set carries `reps` and never
  `holds`, and a hold set the reverse.

**CSV:** three tables, RFC 4180, with CRLF line ends and a header row even when
there are no rows.

| Table | One row per | Columns |
|---|---|---|
| sets | set, including sets with no records | `sessionId, sessionStartedAtEpochMs, utcOffsetMinutes, setIndex, exerciseId, mode, setStartedAtEpochMs, recordCount, validCount` |
| reps | repetition | `sessionId, setIndex, exerciseId, repIndex, attributedSide, validity, startMs, endMs, rangeOfMotionLeft, rangeOfMotionRight, peakValueLeft, peakValueRight, troughValueLeft, troughValueRight, descentMs, ascentMs` |
| holds | hold | `sessionId, setIndex, exerciseId, holdIndex, validity, startMs, endMs, totalHoldMs, longestUnbrokenMs, breakCount, meanAbsDeviation, maxAbsDeviation, coverage, meanHeldValue, heldValueSd` |

The 64-point resampled curves and the form summary are in the JSON only.

**The form summary** (`formatVersion` 2, `DECISIONS.md` P3-44). Each repetition record carries
`"form": { <measure id>: {"left", "right"} }`, one entry for each `FormMeasure` its exercise asks
for, and `{}` for an exercise that asks for none. A record exported at version 1 has no `form` and
reads as having none, so it is not measured, never zero.

**Rules:**

- **Left and right** are a `{"left", "right"}` pair in JSON and two columns in
  CSV. They are never one value.
- **Not measured** is JSON `null` and an empty CSV cell, never `0`.
- **Numbers** are laid out by ECMAScript's Number::toString rules on every
  target (`JsonNumber`), so JVM and JS write the same bytes. On Android's
  runtime the digits may be longer, but they read back to the same double.
- **Times.**
  - `...EpochMs` fields are UTC epoch milliseconds.
  - A record's `startMs` and `endMs` are milliseconds on the clock that stamped
    the frames: the camera's on a phone, the harness's own in P7. The export
    does not relate that clock to the epoch, so compare these times only within
    one set.
- **Versioning.** `formatVersion` rises whenever a field is added, removed,
  renamed or changes meaning, and the golden file (`SessionExportGolden`,
  exact on every target) is regenerated with it.

**Not carried:**

- **`AnalysisResult`.** It is not stored; it is recomputed from a record and a
  baseline, and both are exported.
- **When a set ended.** Nothing records that today.
- **Which account a session belongs to.** Not a field: since `DECISIONS.md` P3-60
  each account has its own database file on the phone, so an export holds the
  signed-in account's sessions and baselines only. (Before P3-60 it held every
  session on the phone.)
