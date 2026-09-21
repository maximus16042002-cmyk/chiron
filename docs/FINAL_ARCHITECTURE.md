# Kinesis — Final Architecture Decision

Last review before development. Supersedes the Master Development Plan where the two disagree.

Sep 18, 2026 · @Someone

## 1. Verdict: YELLOW

**The architecture is sound and you should build it — but four things in the Master Development Plan are wrong, and one of them is wrong in a way that would have shaped months of work.**

Not RED: the core — shared engine, profile-driven exercises, personal calibration, three claim levels, deterministic-first — survives scrutiny and is the right design.

Not GREEN: two decisions are unvalidated gates, one ML abstraction does not hold up, and two exercises have view problems I missed.

### What I got wrong

| \#  | Error                                                                                                                                                                                                                                                                                                | Severity                                                         |
|-----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| 1   | **The shared fault detectors are mostly not ML problems at all.** Four of the six are single-feature thresholds — i.e. rules. Of the two that aren't, pooling across exercises would inject label noise, because the same fault name describes different biomechanics in different movements.        | **High** — this changes the ML plan                              |
| 2   | **I predicted your accuracy.** "Expect 0.80–0.87" came from a rehab benchmark on Kinect skeletons with clinical populations performing rehabilitation gestures. Different sensor, different task, different people. Presenting it as your target was unsupported, and you were right to call it out. | **Medium** — removed, and I have deleted it from the Master Plan |
| 3   | **I promised "bit-identical" cross-platform output.** kotlin.math on JS maps to the JavaScript engine's Math, which is not guaranteed bit-identical to the JVM's. Rep counts and phase sequences will match; floating-point metrics will agree to a tolerance, not exactly.                          | **Medium** — the parity claim needs restating                    |
| 4   | **Front raise and bent-over row have view conflicts I did not catch.** From a side view the two arms occlude each other in a front raise; in a bent-over row the far arm is hidden behind the torso. Neither supports per-side analysis from the view I assigned.                                    | **Medium** — changes the exercise list                           |

### What survives unchanged

- Profile-driven engine with one analysis core — **this remains the best decision in the project**

- Three claim levels, enforced structurally rather than by UI copy

- Bilateral-by-default signal shape, never silently averaged

- The camera-view contract and setup gate

- Personal calibration as the scientific position

- Deterministic-first, ML only where measurable

- RepRecord + AnalysisResult + local storage + export. No backend, no auth, no cloud.

- Two-tier real-time / post-rep split

- Android first, evaluation before PWA

- Data collection starting as early as export exists

### The three gates before you commit

| Gate                             | Question                                                                  | If it fails                                            |
|----------------------------------|---------------------------------------------------------------------------|--------------------------------------------------------|
| **G1** Multiplatform spike       | Does the real core compile and interop usably?                            | Web becomes a **replay-only client**, not a rewrite    |
| **G2** Device reality check      | Does the pipeline hold up on real people and mid-range hardware?          | Cut exercises, not evaluation                          |
| **G3** Per-exercise label volume | After labelling, is there enough per flagship exercise for supervised ML? | Ship rules; report the comparison as a negative result |

G3 is the one nobody plans for. ~500 labelled reps across 5 flagship exercises is ~100 per exercise, and once split by fault type and held out by subject, some fault classes will have too few positives to learn from. **That is a finding, not a failure** — but you should know it may happen before you build the expectation into your report.

### The one-line summary

Build it. Keep the architecture. Replace the shared fault detectors with rules plus a much narrower supervised experiment, treat the KMP spike as a genuine go/no-go, drop two exercises, and stop predicting numbers you have not measured.

## 2. Correction 1 — the shared fault detectors

You asked me to check whether this abstraction is scientifically defensible. **Mostly it is not.** Here is the audit.

| Proposed detector     | Same meaning across exercises?                                                                                 | Comparably normalisable?              | Needs exercise ID? | Verdict                        |
|-----------------------|----------------------------------------------------------------------------------------------------------------|---------------------------------------|--------------------|--------------------------------|
| Incomplete ROM        | Yes as a *measurement*; no as a *fault* — a shallow squat may be mobility, a short curl is usually effort      | Yes, it is a ratio                    | No                 | **Rule, not ML** — one feature |
| Tempo irregularity    | Yes as a measurement                                                                                           | Yes, a ratio                          | No                 | **Rule** — 2–3 features        |
| Asymmetry             | Yes                                                                                                            | Yes — difference from habitual offset | No                 | **Rule** — one feature         |
| Inconsistency         | Yes                                                                                                            | Yes — distance to own reference       | No                 | **Rule** — one feature         |
| Body-line deviation   | **No** — push-up sag, plank sag, bridge extension and wall-sit posture are different geometries sharing a word | Partly                                | **Yes**            | **Exercise-specific rule**     |
| Momentum compensation | **No** — curl trunk-swing is a sagittal lean; lateral-raise momentum is a knee-dip and whole-body bounce       | **No** — different signals entirely   | **Yes**            | **Exercise-specific**          |

### The two errors

**I proposed ML for what are threshold problems.** A Random Forest over a single monotonic ratio is not a model, it is a threshold with extra steps and worse explainability. Four of the six take one feature and compare it to a personal baseline. That is a rule: no training data, works on all 15 exercises immediately, trivially explainable.

**I assumed a shared name implies a shared label.** "Momentum compensation" in a bicep curl means trunk extension in the sagittal plane. In a lateral raise it usually means a knee dip driving the arms up. Pooling reps labelled "momentum" from both would train a model on two different phenomena wearing one label — which is not data efficiency, it is **label noise dressed as data efficiency**. The pooling argument was backwards.

### The corrected design

**Layer 1 — Deviation rules (deterministic, all 15 exercises, no training data)**

| Rule             | Computation                                                        | Output                  |
|------------------|--------------------------------------------------------------------|-------------------------|
| ROM deviation    | rep range ÷ calibrated median range                                | Signed %, Level 2       |
| Tempo deviation  | concentric/eccentric ratio vs baseline; total duration vs baseline | Signed %, Level 2       |
| Asymmetry        | (L − R) − habitual offset                                          | Signed degrees, Level 2 |
| Consistency      | normalised distance to personal reference rep                      | Scalar, Level 2         |
| Within-set drift | trend of each of the above across the set                          | Slope, Level 2          |

These are **Level 2 measurements, not technique judgements**. They ship on every exercise from day one, they need no labels, and they are what makes 15 exercises genuinely cheap. Crucially they make no claim about correctness — they report deviation, and the user interprets it.

**Layer 2 — Exercise-specific technique rules (Tier A only, validated against labels)**

Each flagship exercise gets its own small rule set over its own signals, with its own thresholds relative to the user's baseline. No sharing, no pooling, no pretence that a squat and a curl have the same faults.

| Exercise      | Technique rules                                                                                   |
|---------------|---------------------------------------------------------------------------------------------------|
| Squat         | Trunk lean at bottom; hip-vs-shoulder ascent velocity divergence; knee-angle asymmetry under load |
| Push-up       | Body-line angle deviation from own start-of-set value; partial lockout                            |
| Bicep Curl    | Trunk-angle range within rep; shoulder (elbow) drift; incomplete extension                        |
| Lateral Raise | Arm-height difference; shoulder shrug; elbow-angle change through set                             |
| Glute Bridge  | Knee-angle change during the lift; hip-extension shortfall vs baseline                            |

**Layer 3 — Supervised classification (a scoped experiment, not a shipped promise)**

One model per flagship exercise, over that exercise's own feature vector, predicting that exercise's own fault classes. The purpose is the **comparison against Layer 2**, not the capability.

**Layer 4 — Personal anomaly detection (unsupervised, all 15)**

Unchanged and still the best idea in the ML plan. Fits on the user's own history at runtime, needs no labels, works everywhere, and phrases its output as *"unusual for you"* — a Level 2 statement, not a technique judgement.

### What this costs and buys

|                                              | Before                                               | After                                |
|----------------------------------------------|------------------------------------------------------|--------------------------------------|
| Models to train                              | ~8                                                   | **~6** (5 per-exercise + 1 verifier) |
| Labels needed before anything works          | ~500                                                 | **0** for Layers 1, 2 and 4          |
| Exercises with useful analysis pre-labelling | 0                                                    | **All 15**                           |
| Defensibility                                | "shared detectors" is a claim I'd have had to defend | Each layer's scope is obvious        |

The corrected design is **less ambitious and more honest**, and it decouples the product from the dataset — Layers 1, 2 and 4 ship whether or not labelling finishes on time. Given that data collection is your biggest schedule risk, that decoupling is worth more than the abstraction was.

### The experiment that replaces it

The interesting question is no longer "can one detector span exercises?" but: **does a supervised classifier beat a hand-written rule on the same exercise, same labels, same subject-independent split?**

That is a cleaner experiment, it is answerable with the data you will have, and either outcome is publishable in your report. If rules win, you have a genuine finding about explainable methods on small data. Say so plainly rather than treating it as a disappointment.

## 3. Correction 2 — the KMP decision, judged specifically

**Verdict: KMP with Kotlin/JS is worth it for *your* codebase — with one design change and a real gate.** Not because sharing is generally good, but because of what your core specifically contains.

### Why your core is close to the best case

| Property of your commonMain                           | Effect on Kotlin/JS                                    |
|-------------------------------------------------------|--------------------------------------------------------|
| Pure numeric math — angles, filters, FSM, statistics  | **Ideal.** No platform APIs to abstract.               |
| No I/O, no networking, no threading in the core       | **Ideal.** The hard parts of multiplatform are absent. |
| Data is FloatArray/DoubleArray and small data classes | Good, with a caveat below                              |
| No coroutines needed in the analysis path             | Removes the largest single source of JS interop pain   |
| No date/time, no locale, no file system               | Removes the second largest                             |

If your core needed HTTP, a database driver, or structured concurrency, I would tell you not to do this. It needs none of them.

### The design change: expose a facade, not the core

This is the part the Master Plan got wrong by omission. **Do not publish your Kotlin classes to TypeScript.** Kotlin collections, nullables, sealed classes and data classes all cross the boundary awkwardly, and a React developer consuming kotlin.collections.List is a bad time.

Instead, define **one thin facade** in commonMain, exposed with @JsExport, whose entire surface is primitives, DoubleArray, and plain JSON strings:

KinesisEngine  
configure(profileJson: String)  
pushFrame(timestampMs: Double, landmarks: DoubleArray, visibility: DoubleArray): String?  
// returns null, or a JSON RepRecord when a rep completes  
analyzeRep(repJson: String, baselineJson: String): String // JSON AnalysisResult  
computeBaseline(repsJson: String): String

Five functions. Everything rich stays inside Kotlin; everything crossing the boundary is a number array or a JSON string. TypeScript sees a tiny, hand-written .d.ts you control. Serialisation cost is negligible — a rep is a few KB, once every 2–4 seconds, and pushFrame returns null on the vast majority of calls.

This also makes the parity suite trivial: same JSON in, same JSON out, on both platforms.

### The numeric-parity claim, corrected

I said "bit-identical". That was wrong. kotlin.math on JS delegates to the JavaScript engine's Math, which is **not** guaranteed to match the JVM bit for bit — sin, cos, atan2, sqrt and pow may differ in the last ulp.

What that means in practice:

| Output                  | Parity claim                                                    |
|-------------------------|-----------------------------------------------------------------|
| Rep counts              | **Exact.** Differences of 1e-15 cannot cross a hysteresis band. |
| Phase sequences         | **Exact**, same reason                                          |
| Fault rule outcomes     | **Exact** in all but pathological boundary cases                |
| Angles, ROM, velocities | **Agree to ~1e-9 relative**; assert a tolerance, not equality   |
| DTW distances           | Agree to tolerance                                              |

So the honest claim for your report is: *"identical discrete outputs — rep counts, phases, fault flags — and continuous metrics agreeing within 1e-9 relative tolerance across platforms."* That is still a strong, verifiable engineering claim, and it is one you can actually defend.

Two rules that make it hold: **use Double throughout the core, never Long** (emulated on JS, slow and awkward), and **never rely on Float precision** in a threshold comparison.

### Bundle size and debugging

Bundle: the Kotlin/JS runtime plus a pure-math core compiles to roughly a few hundred KB minified. Irrelevant next to MediaPipe's ~12.9 MB landmark model, which dominates your PWA payload regardless.

Debugging: source maps work but are imperfect. This matters less than it sounds because **the core is tested in commonTest and runs identically on the JVM** — debug analysis bugs on Android where tooling is good, and use the JS side only for integration issues.

### Why not Wasm, and why not a TypeScript rewrite

**Kotlin/Wasm:** designed for sharing UI as well as logic. You are not sharing UI. Interop with JS for non-primitive types is more awkward than Kotlin/JS, and the ecosystem is younger. No benefit here.

**TypeScript rewrite:** safer to build, catastrophic to maintain. Two implementations of a threshold, a filter and an FSM will drift, and the drift is silent. Worse, it destroys the one genuinely valuable thing the second platform gives you — the parity result. A rewrite turns a research claim into duplicated work.

### The gate, and the fallback that is not a rewrite

**G1 is a real go/no-go**, and the Master Plan's "half-day spike" was too vague. Make it concrete:

> Take the **existing prototype's angle calculation and FSM**, not a hello-world. Compile to JS. Call it from a minimal React page with a hard-coded landmark sequence. Assert the rep count matches the JVM result. Measure the build time and look at the generated .d.ts.

Allow one day, not half. If it works, proceed. If it does not:

**The fallback is a replay-only web client**, not a TypeScript rewrite. The web app ingests exported session JSON and renders the analysis, charts, replay and fingerprints — no live camera, no analysis in the browser. That needs almost none of the core, is perhaps four days of React work, still gives you a Windows deliverable, and preserves single-implementation correctness.

That degradation path is what makes the KMP bet safe to take: the downside is a narrower web app, not a rewritten engine.

## 4. Correction 3 — configuration cheap ≠ validation cheap

You are right to press on this. "110 lines of configuration" is true and misleading. Here are the two costs separated.

**Config** = writing the profile. **Test** = getting it reliable on real people. **Data** = labelled reps needed for its tier. **Reliability** = expected real-world segmentation quality. **Landmarks** = how trustworthy the required landmarks are. **Viva** = how comfortable you would be defending it under questioning.

| Exercise               | Config | Test      | Data     | Reliability    | View difficulty                        | Landmarks                       | Viva     |
|------------------------|--------|-----------|----------|----------------|----------------------------------------|---------------------------------|----------|
| **Squat**              | 0.5 d  | **3 d**   | 100 reps | High           | Low                                    | High                            | Strong   |
| **Push-up**            | 0.5 d  | **3 d**   | 100 reps | High           | Medium — floor-level framing           | High                            | Strong   |
| **Bicep Curl**         | 0.5 d  | **3 d**   | 100 reps | High           | Low                                    | High                            | Strong   |
| **Lateral Raise**      | 0.5 d  | **2.5 d** | 100 reps | High           | Low                                    | High                            | Strong   |
| **Glute Bridge**       | 0.5 d  | **2.5 d** | 100 reps | Medium-high    | Medium — floor pose                    | Medium — lying poses are weaker | Good     |
| Shoulder Press         | 0.5 d  | 1.5 d     | 0        | Medium         | Medium — wrists exit frame             | Medium                          | Adequate |
| Sit-up                 | 0.25 d | 1 d       | 0        | High           | Low                                    | High                            | Good     |
| Standing Knee Raise    | 0.25 d | 1 d       | 0        | High           | Low                                    | High                            | Good     |
| Lying Leg Raise        | 0.25 d | 1 d       | 0        | High           | Low                                    | High                            | Good     |
| Standing Hip Abduction | 0.5 d  | 1.5 d     | 0        | Medium         | Medium                                 | Medium                          | Adequate |
| **Bent-over Row**      | 0.5 d  | **2.5 d** | 0        | **Low-medium** | **High — far arm occluded**            | Low for far side                | **Weak** |
| **Front Raise**        | 0.5 d  | **2.5 d** | 0        | **Low-medium** | **High — arms occlude or foreshorten** | Medium                          | **Weak** |
| **Lunge**              | 1 d    | **4 d**   | 0        | **Low-medium** | **High — rear leg occluded**           | Low for rear leg                | **Weak** |
| Wall Sit               | 0.5 d  | 1 d       | 0        | High           | Low                                    | High                            | Good     |
| Plank                  | 0.5 d  | 1 d       | 0        | High           | Medium — floor framing                 | High                            | Good     |

### What the table shows

**Config is 0.25–1 day for everything. Test is 1–4 days and varies fourfold.** Total: roughly **6 days of configuration and 30 days of testing.** The configuration was never the cost.

Three exercises — lunge, bent-over row, front raise — are **configuration-cheap and validation-expensive**, and all three are the ones where a monocular side view cannot see what matters. That correlation is not a coincidence: view difficulty *is* validation cost.

### The four honest categories

| Category                                      | Exercises                                            | Treatment                           |
|-----------------------------------------------|------------------------------------------------------|-------------------------------------|
| **Cheap to configure, cheap to validate**     | Sit-up, knee raise, lying leg raise, wall sit, plank | Add freely                          |
| **Cheap to configure, moderate to validate**  | Shoulder press, hip abduction, glute bridge          | Add, budget the testing             |
| **Cheap to configure, expensive to validate** | **Lunge, bent-over row, front raise**                | **Fix the view or cut** — section 5 |
| **Expensive both ways**                       | Calf raise                                           | Already cut                         |

### The rule this gives you

**An exercise costs a day to add and three days to trust.** Budget testing at four to five times configuration, and treat any exercise whose required view cannot see its primary signal cleanly as expensive regardless of how simple its profile looks.

That rule is what stops "the architecture makes exercises cheap" from turning into fifteen exercises that each work on you, in your room, in good light.

## 5. Correction 4 — the exercise list, re-cut

Re-examining the list against what a single camera can actually see, three entries have problems I did not flag.

### The three problems

**Front Raise — no good view exists.** From the **side**, the two arms travel in the same plane and occlude each other almost completely; you cannot separate left from right, which kills the per-side analysis I assigned it. From the **front**, the arm moves *toward* the camera, so shoulder flexion is maximally foreshortened — the angle you want to measure is the one 2D projection destroys. A 45° view is a compromise that degrades both. **Cut it.**

**Bent-over Row — the far arm is invisible.** With the torso roughly horizontal and the camera to one side, the near arm is clean and the far arm is behind the body for most of the movement. Trunk angle also varies enormously between people. **Keep it, but near-side only, and say so explicitly** — no per-side comparison, no symmetry claim. That is an honest restriction and cheap to enforce in the profile.

**Overhead Triceps Extension — the wrist hides behind the head.** At the bottom of the movement the forearm is behind the skull from a side view, and MediaPipe's wrist landmark becomes unreliable exactly where the ROM minimum is. Since elbow angle *is* the primary signal, this is a failure at the point that matters most. **Cut it.**

### The replacements

Two slots freed. Both replacements are side-view, large-ROM, floor or standing, with high landmark reliability:

- **Lying Leg Raise** — hip flexion, side view, large clean ROM, present in InfiniteRep, very reliable segmentation.

- **Side Plank** — second isometric, pairs naturally with plank and wall sit, tests the hold analyser on a different geometry, trivially reliable.

### The final list — 15 entries, 13 profiles, 2 variants

| \#  | Entry                            | Profile         | Tier  | View        | Mode |
|-----|----------------------------------|-----------------|-------|-------------|------|
| 1   | **Squat**                        | Squat           | **A** | Side        | Rep  |
| 2   | **Goblet Squat**                 | *Squat variant* | A     | Side        | Rep  |
| 3   | **Push-up**                      | Push-up         | **A** | Low side    | Rep  |
| 4   | **Bicep Curl**                   | Bicep Curl      | **A** | Side / 45°  | Rep  |
| 5   | **Lateral Raise**                | Lateral Raise   | **A** | Front       | Rep  |
| 6   | **Glute Bridge**                 | Glute Bridge    | **A** | Side, floor | Rep  |
| 7   | Shoulder Press                   | Shoulder Press  | B     | Front       | Rep  |
| 8   | Bent-over Row *(near side only)* | Row             | B     | Side        | Rep  |
| 9   | Lunge                            | Lunge           | B     | Side        | Rep  |
| 10  | **Reverse Lunge**                | *Lunge variant* | B     | Side        | Rep  |
| 11  | Standing Hip Abduction           | Hip Abduction   | B     | Front       | Rep  |
| 12  | Standing Knee Raise              | Knee Raise      | B     | Side        | Rep  |
| 13  | Sit-up                           | Sit-up          | B     | Side        | Rep  |
| 14  | Lying Leg Raise                  | Leg Raise       | B     | Side, floor | Rep  |
| 15  | Wall Sit                         | Wall Sit        | **C** | Side        | Hold |
| 16  | Plank                            | Plank           | **C** | Side        | Hold |
| 17  | Side Plank                       | Side Plank      | **C** | Front       | Hold |

That is **17 entries and 15 profiles** — slightly over target, which gives you two to drop under pressure (side plank and hip abduction) and still land on 15 entries / 13 profiles.

### Counting honestly

The number you should put in your report and say in your viva:

> **15 exercise profiles across 3 analysis tiers and 2 analysis modes, presented as 17 library entries including 2 variants that share a parent profile and calibration.**

Not "17 exercises". The variants share everything — profile, thresholds, calibration baseline, rules — and differ only in a label and a coaching note. An examiner who asks "is a goblet squat really a different algorithm?" should find you have already said no.

### Cut list, final

| Exercise                       | Reason                                                                      |
|--------------------------------|-----------------------------------------------------------------------------|
| **Calf Raise**                 | Movement amplitude at or below the landmark noise floor                     |
| **Front Raise**                | No camera view shows the primary signal — side occludes, front foreshortens |
| **Overhead Triceps Extension** | Wrist occluded behind the head at the ROM extreme                           |

All three are cut for the same underlying reason, and it is worth stating that way in your report: **an exercise is only supportable if some single camera view can see its primary signal unambiguously through the whole range of motion.** That is a clean, defensible selection criterion, and it is a better answer than a list.

## 6. Final scope and claim enforcement

### Final scope

|                           |                                                                    |
|---------------------------|--------------------------------------------------------------------|
| Library entries           | **17** (15 under pressure)                                         |
| Exercise profiles         | **15** (13 under pressure)                                         |
| Variants sharing a parent | 2 — Goblet Squat, Reverse Lunge                                    |
| Analysis modes            | **2** — repetition, isometric hold                                 |
| Tiers                     | **3** — A validated, B measurement, C hold                         |
| Flagship (Tier A)         | **5** — Squat, Push-up, Bicep Curl, Lateral Raise, Glute Bridge    |
| Platforms                 | Android native (primary), Windows PWA (secondary, cuttable)        |
| Backend                   | **Firebase Auth + Firestore, for account-based sync of session records only** — added by deliberate decision, DECISIONS.md P3-14. Still no server inference, no video upload, and no analysis off-device. |

### Amendment: the backend line, and exactly what it now permits

This section read **"No backend, no auth, no cloud, no server inference, no
video upload"** from P0 until Batch 10. It was amended by explicit decision of
the project owner, recorded in DECISIONS.md **P3-14**, and the amendment is
narrow:

**Now in scope.** Firebase Authentication (email and password) and Cloud
Firestore, used to sync **`SessionRecord` history** to an account so a user's
history survives losing the device. Nothing else.

**Still out of scope, and still hard lines.**

- **No cloud inference.** Every landmark, angle, repetition and hold is computed
  on the device. Nothing about the analysis path changed, and no frame, no
  landmark and no image ever leaves the phone.
- **No video upload.** CLAUDE.md §16's prohibition is untouched. What syncs is
  the numeric record of a completed session, never the footage it came from.
- **Room remains the source of truth.** Firestore is additive. A local save must
  succeed with the network down, and a failed push is retried rather than
  blocking anything.
- **No analytics, no crash reporting, no telemetry.** P3-13 excluded Google's
  datatransport backend when MediaPipe pulled it in silently; that exclusion
  stands, and `OfflineManifestGuardTest` still fails the build on any network
  capability arriving from anything other than the Firebase artifacts this
  amendment names.

The honest framing for the report: this is a deliberate product decision that
trades one of the project's original guarantees for account-based durability,
taken with the tradeoff stated rather than discovered. It is the first
architecture change in this project's history, and it is the reason the document
you are reading carries an amendment rather than a rewrite.

### The three claim levels, enforced in code

This is the most important structural decision in the project, so it needs to be a type, not a convention.

| Level                       | Example                                                  | Evidence required                   | Available on    |
|-----------------------------|----------------------------------------------------------|-------------------------------------|-----------------|
| **1 — Measurement**         | "Rep 7: ROM 84°, 2.1 s down, 1.3 s up"                   | Rep-count accuracy only             | All 15 profiles |
| **2 — Personal deviation**  | "18% below your calibrated baseline" · "unusual for you" | Calibration stability               | All 15 profiles |
| **3 — Technique judgement** | "Trunk compensation detected"                            | **Labelled data for that exercise** | **Tier A only** |

### How it is enforced

Three mechanisms, in order of strength:

**1. The type system.** Findings carry their level:

sealed interface Finding { val level: ClaimLevel }  
Measurement : Finding // level = L1, always  
Deviation : Finding // level = L2, always  
TechniqueFault: Finding // level = L3, always

**2. The profile gates construction.** ExerciseProfile.maxClaimLevel is L2 for Tier B and C, L3 only for Tier A. The form engine refuses to construct a TechniqueFault for a profile whose maximum is L2 — not by filtering it afterwards, but by having no code path that produces one. A Tier B exercise cannot emit a Level 3 finding even if a rule tries.

**3. Wording lives in the core, not the UI.** Every finding carries its own display string, generated in commonMain from the measured values. The UI renders what it is given and composes no sentences of its own. This is what stops UI copy from bypassing the tier — there is no place in the UI where a human writes a judgement.

A test asserts that no Tier B or C profile can produce an L3 finding across the entire rule set. It runs in commonTest, on both platforms, forever.

### Why this is worth the effort

It converts your biggest academic vulnerability into a design feature. The question *"how do you know your form feedback is right for all fifteen exercises?"* has a clean answer: **for ten of them the system never claims form feedback at all, and that is enforced by the type system rather than by discipline.**

That answer is stronger than any accuracy number you could quote, and it costs about a day to implement.

### What the user sees

| Tier  | Library badge        | After a set                                                                        |
|-------|----------------------|------------------------------------------------------------------------------------|
| **A** | "Technique analysis" | Measurements + deviations + **technique findings with explanations**               |
| **B** | "Measurement only"   | Measurements + deviations. Anomalies phrased "unusual for you", never "incorrect". |
| **C** | "Hold analysis"      | Duration, stability, drift                                                         |

Be upfront about the tiers in the UI rather than hiding them. Users understand "we measure everything, we judge technique on five"; examiners respect it.

## 7. Final ML strategy

After the correction, ML does much less than the Master Plan implied — and the project is better for it.

### What is deterministic

| Stage                                      | Method                         | Why not ML                                     |
|--------------------------------------------|--------------------------------|------------------------------------------------|
| Pose → landmarks                           | **Pretrained MediaPipe**       | The only pretrained model you ship             |
| Kinematics                                 | Deterministic                  | It is trigonometry                             |
| Temporal processing                        | Deterministic                  | Signal processing, with a measured ablation    |
| Rep segmentation                           | **Deterministic FSM**          | Causal, cheap, explainable, already works      |
| Phase detection                            | Deterministic                  | Falls out of the FSM free                      |
| Hold analysis                              | Deterministic                  | Duration and variance                          |
| ROM, tempo, velocity, smoothness, symmetry | Deterministic                  | These are definitions, not predictions         |
| Calibration baselines                      | **Statistical** (median/IQR)   | Robust estimation, not learning                |
| Reference-rep selection                    | Deterministic (min median DTW) | An argmin                                      |
| DTW comparison                             | Deterministic algorithm        | A published algorithm, applied                 |
| **Layer 1 deviation rules**                | **Deterministic**              | Single-feature thresholds on baseline ratios   |
| **Layer 2 technique rules**                | **Deterministic**              | Multi-feature but hand-derived and explainable |

### What ML actually does — three things

**1. Exercise verification** — one multiclass model over the 15 profiles. Genuinely a learning problem, and largely trainable on **MM-Fit**, so it costs almost no collection effort. Ships as a mismatch warning, not as exercise selection.

**2. Personal anomaly detection** — One-Class SVM or Isolation Forest fitted on the individual user's own recent reps, at runtime, on-device. **No labels, all 15 profiles, works from the first session.** Output is a Level 2 statement: *unusual for you.*

**3. Per-exercise rep-quality classification** — **five models, one per Tier A exercise**, each over that exercise's own features and its own fault classes. This is the supervised experiment, not a shipped capability promise.

That is it. No shared fault detectors, no sequence models, no transfer learning, no per-frame inference.

### The experiment, stated properly

> For each flagship exercise, does a supervised classifier over engineered rep features outperform a hand-written rule set on the same labels, under leave-one-subject-out evaluation?

Report per exercise, with the rule baseline in the same table and n stated. **Either outcome is a result.** If rules win on 500 reps, that is a finding about explainable methods on small data and you should present it as one — not bury it.

**I am not predicting what you will get.** The earlier "0.80–0.87" figure came from rehabilitation gestures captured on Kinect with a clinical population; it has no bearing on phone-camera gym movements. You will measure your own numbers and report them with their protocol. That is the correct posture and I should have held it the first time.

### Models and deployment

**Use:** Logistic Regression as the interpretable baseline, then Random Forest or Gradient Boosting. One-Class SVM or Isolation Forest for anomalies. **Skip:** RBF SVM, any sequence model (LSTM, TCN, GCN), anything end-to-end on video, transfer learning from NTU-trained checkpoints (licence-encumbered as well as ill-suited).

**Deployment:** train in Python with scikit-learn → export trees to JSON → a small evaluator inside the shared Kotlin core. No TFLite, no ONNX, no inference runtime on either platform, and the same evaluator serves Android and web automatically. Inference is microseconds, once per rep.

### Fallback

| Condition                                    | Behaviour                                                             |
|----------------------------------------------|-----------------------------------------------------------------------|
| Landmark validity below threshold            | Rep counted, flagged, **not scored**                                  |
| No calibration yet                           | Level 1 measurements only; deviations unavailable, clearly stated     |
| Model missing or fails to load               | Rules-only, logged, no user-visible error                             |
| Tier B or C profile                          | ML runs for verification and anomaly only; L3 structurally impossible |
| Insufficient labels for an exercise (**G3**) | That exercise ships with rules only; the model is simply not trained  |

That last row matters. **Layers 1, 2 and 4 ship regardless of how the labelling goes**, which decouples your product from your biggest schedule risk. Only Layer 3 depends on the dataset, and Layer 3 is an experiment rather than a feature.

## 8. Final data model and performance contract

### Data model — unchanged, and deliberately small

SessionRecord → SetRecord → RepRecord / HoldRecord, plus CalibrationBaseline and AnalysisResult. Local storage only — Room on Android, IndexedDB on web, behind one shared repository interface. JSON and CSV export. **No backend, no auth, no cloud, no sync.**

Two design points worth restating because they do real work:

**Fixed-length resampling at write time (N=64).** Makes DTW, ML features, envelope comparison and ghost overlay all trivial, and makes reps from different sessions directly comparable.

**AnalysisResult separate from RepRecord.** The rep is measurement; the analysis is an interpretation by a particular model or rule version. Keeping them apart lets you **re-run analysis over historical reps when rules change** — which is exactly what the ablation studies need, and it costs nothing now.

One addition after the corrections: **AnalysisResult carries claimLevel and ruleVersion per finding.** Without those you cannot reconstruct, three weeks later, why a historical rep was flagged.

### The performance contract

Make this a written rule in the architecture doc, because every violation is a silent frame-rate regression.

**Real-time path — runs ~30×/second, must be O(1) per frame:**

landmarks → normalise → validity check → angles (current frame only)  
→ ring-buffer update → FSM step → phase + count

Every step touches the current frame and a small fixed-size buffer. **Nothing in this path may iterate over session history, allocate per frame, or run a model.**

**Post-rep path — runs ~0.3×/second, may use the whole rep:**

RepRecord → resample → feature extraction → rules → DTW → ML → explanation → persist

### The four prohibitions, and how each is prevented

| Prohibition                           | Prevention                                                                                                                                                             |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Reprocessing signal history per frame | Fixed-size ring buffers in the real-time path; the full signal exists only inside a completed RepRecord                                                                |
| Compose recomposition per pose frame  | Skeleton and live envelope draw on a dedicated Canvas/SurfaceView fed from the pose callback. Compose state updates only on rep/phase change — a few times per second. |
| ML per frame                          | Structural: models take a RepRecord, which only exists when a rep completes. There is no API to run them per frame.                                                    |
| Duplicated pipelines                  | One core, one FSM, one record format. The parity suite fails if the platforms diverge.                                                                                 |

### Instrument it from P4, not at the end

FPS, end-to-end latency, dropped frames, per-rep analysis time and allocation rate should be measurable from the device reality check onward. A performance regression found in week 6 is a bug; the same regression found in week 18 is a redesign.

One specific measurement people skip: **sustained thermal behaviour.** Measure FPS at minute 1 and minute 15 of a continuous session. Mid-range phones throttle, and "it runs at 28 fps" is not a claim you can make from a 30-second test.

## 9. Final phase and module structure

**Thirteen phases, P0–P12, unchanged.** The phase structure survived review. Six module-level changes follow from the corrections.

| Phase   | Change                                                                                                                                                                                                                                                        |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **P0**  | G1 spike upgraded: **port the real FSM and angle code**, not a hello-world. Allow one day. Add the go/no-go decision and the replay-only fallback in writing.                                                                                                 |
| **P1**  | New module **1.6 JS facade** — the narrow @JsExport surface (primitives, DoubleArray, JSON strings). Designed now, not retrofitted in P11.                                                                                                                    |
| **P2**  | Unchanged                                                                                                                                                                                                                                                     |
| **P3**  | Unchanged. Still three archetype profiles only.                                                                                                                                                                                                               |
| **P4**  | Unchanged — still the most important early phase                                                                                                                                                                                                              |
| **P5**  | Unchanged                                                                                                                                                                                                                                                     |
| **P6**  | Unchanged                                                                                                                                                                                                                                                     |
| **P7**  | Protocol narrows: labelled data is needed for **5 flagship exercises only**. Target ~500 reps, but with an explicit **G3 check** once labelled: does each exercise have enough positives per fault class?                                                     |
| **P8**  | **Restructured.** Now the main analysis phase, in two layers: **8.1–8.3 Layer 1 deviation rules** (all 15 profiles, no data needed) and **8.4–8.6 Layer 2 technique rules** (Tier A only). Plus **8.7 claim-level type enforcement** with its permanent test. |
| **P9**  | **Shrunk.** Three components only: exercise verifier, personal anomaly detector, and five per-exercise quality classifiers as an experiment. Shared fault detectors deleted.                                                                                  |
| **P10** | Unchanged, except it now adds **10 remaining profiles** rather than 12                                                                                                                                                                                        |
| **P11** | Parity claim restated: identical discrete outputs, continuous metrics within tolerance. Fallback to replay-only client documented.                                                                                                                            |
| **P12** | Ablation list adjusted — "shared vs per-exercise detectors" removed, **"rules vs ML, per flagship exercise"** added                                                                                                                                           |

### What P8 becomes

The important structural shift: **P8 is now where most of the product's value is created, and it needs no labelled data for its first half.**

| Module                                                        | Type | Needs labels?       |
|---------------------------------------------------------------|------|---------------------|
| 8.1 Deviation rule engine                                     | ENG  | **No**              |
| 8.2 Layer 1 rules (ROM, tempo, asymmetry, consistency, drift) | RES  | **No**              |
| 8.3 Explanation generator                                     | ENG  | **No**              |
| 8.4 Tier A technique rule sets                                | RES  | For validation only |
| 8.5 Severity, confidence, suppression                         | ENG  | No                  |
| 8.6 Rule validation against labels                            | EVAL | **Yes**             |
| 8.7 Claim-level enforcement + test                            | ENG  | No                  |

Five of seven modules proceed while data collection is still running. That is the decoupling the correction bought you, and it is worth more than the abstraction it replaced.

### Module count after revision

| Type | Modules | Change                            |
|------|---------|-----------------------------------|
| ENG  | 40      | +2 (JS facade, claim enforcement) |
| RES  | 12      | −2 (shared detectors removed)     |
| DATA | 8       | unchanged                         |
| EVAL | 10      | unchanged                         |
| DOC  | 5       | unchanged                         |

Slightly more engineering, slightly less speculative research — which is the right direction, because the remaining RES modules are ones you can actually complete and defend.

### Timeline effect

Roughly neutral. P9 shrinks by about 4 days; P8 grows by about 3. The real gain is not time but **risk**: the critical path no longer runs through an ML abstraction that might not have worked.

## 10. Evaluation, and what is research vs engineering

### The honest sorting

You asked which of your candidate contributions are genuinely research. The dividing line is simple: **a design decision is engineering; a design decision with an experiment attached is research.** By that test:

| Claimed contribution                                 | Verdict                          | What would make it research                                                                       |
|------------------------------------------------------|----------------------------------|---------------------------------------------------------------------------------------------------|
| Profile-driven cross-exercise architecture           | **Engineering**                  | Good engineering, defensible design — but no experiment. It is a *how I built it*, not a finding. |
| **Personal calibration and deviation from baseline** | **Research — the strongest one** | It already has the experiment: personalized vs population thresholds on the same test set         |
| **Controlled subject-independent evaluation**        | **Research**                     | LOSO plus external validation on data you did not collect is genuinely uncommon at this level     |
| **Rules vs ML comparison**                           | **Research**                     | It is a comparison with a result, either way it falls                                             |
| DTW reference comparison                             | **Engineering**, unless…         | …you run the DTW-vs-Euclidean ablation, which turns it into a small finding                       |
| Explainable technique assessment                     | **Engineering**, unless…         | …rule outputs are validated against human labels with precision/recall                            |
| Cross-platform analysis parity                       | **Engineering**                  | A verifiable engineering claim, which has value, but it is not a research finding                 |
| 15 exercises                                         | **Neither**                      | Scope, not contribution                                                                           |

**Three genuine research contributions, all experiments you can actually run.** Two more become research by attaching an ablation you were already planning. That is a solid academic core for a BCA final-year project, and it is considerably more than most.

### The hierarchy to present

Lead with the personalization experiment. It is the one that is novel in framing, cheap to run, directly tied to the product's central claim, and produces a table an examiner can read in ten seconds.

> **Does comparing a user to their own calibrated baseline outperform comparing them to population thresholds — for rep detection accuracy and for fault detection — under subject-independent evaluation?**

Everything else supports that question.

### Evaluation plan — revised

Unchanged from the Master Plan except:

| Change                                                                   | Reason                                                       |
|--------------------------------------------------------------------------|--------------------------------------------------------------|
| "Shared vs per-exercise detectors" ablation **removed**                  | The abstraction is gone                                      |
| "Rules vs ML **per flagship exercise**" **added**, reported as five rows | Replaces it with a cleaner question                          |
| **No predicted accuracy ranges anywhere**                                | You measure; you do not forecast                             |
| G3 check added after labelling                                           | Decides whether Layer 3 runs at all                          |
| Rule validation moved into P8                                            | Rules are validated as they are written, not retrospectively |

### The four studies that carry the report

1.  **Rep-detection accuracy**, per exercise and per subject, with false positives per minute from the adversarial sets

2.  **External validation** against MM-Fit and InfiniteRep rep labels — subject-independent by construction, on data you did not collect

3.  **Personalized vs population thresholds** — the headline experiment

4.  **Rules vs ML per flagship exercise** — with the rule baseline in the same table

Plus robustness (distance, angle, frame dropping) and performance (FPS, latency, thermal, Android vs browser).

### Reporting discipline

Every table states **n** and the **split protocol**. Every metric names its measurement procedure. At least one **negative result** is written up. **The held-out subjects are used exactly once** — write the date in your log.

And one more, added because of what I got wrong: **do not state an expected result before you have measured it**, in the report or in the viva. If asked what you expect, the correct answer is what your protocol is, not what number you hope for.

## 11. MVP, Full, Stretch — revised

The correction changes these meaningfully: **the MVP is now stronger**, because Layers 1, 2 and 4 need no labelled data and ship on every exercise.

### KINESIS MVP — ~11 weeks

Brutally realistic floor. Still a good BCA final-year project.

|             |                                                                                                                 |
|-------------|-----------------------------------------------------------------------------------------------------------------|
| Platform    | Android only                                                                                                    |
| Entries     | **15** — all profiles, all three tiers, both modes                                                              |
| Engine      | Full shared core, kinematics, temporal pipeline, profiles, rep FSM, isometric analyser                          |
| Calibration | Full — baselines, reference rep, trajectory envelope, personalized thresholds                                   |
| Analysis    | **Layer 1 deviation rules on all 15** · **Layer 2 technique rules on the 5 flagship** · claim-level enforcement |
| ML          | **Personal anomaly detection only** — needs no labels                                                           |
| Visuals     | Skeleton overlay, live trajectory envelope, camera setup assistant, per-rep results                             |
| Data        | 10 subjects, ~250 labelled reps, 3 held out                                                                     |
| Evaluation  | Rep-detection accuracy · external validation on MM-Fit · **personalized vs population ablation** · performance  |

**Why this is stronger than the previous MVP:** all 15 exercises now have real analysis, not just the flagship five, because Layer 1 needs no data. And it still contains the headline experiment. What it lacks is the supervised ML comparison — a real gap, but not a fatal one.

### KINESIS FULL — ~17 weeks *(target)*

MVP plus:

- **Exercise verifier** (multiclass, largely MM-Fit-trained)

- **Five per-exercise quality classifiers** with LOSO and the rules-vs-ML comparison

- **Ghost overlay** with DTW alignment, **session replay**, **fingerprint gallery**

- Symmetry drift and tempo degradation timelines

- All ablations: personal-vs-population, rules-vs-ML, DTW-vs-Euclidean, filter chain

- Full robustness studies

- 12–15 subjects, ~500 labelled reps

- Complete app shell, history, progress charts

### KINESIS STRETCH — ~20+ weeks

Full plus **Windows PWA with the parity suite**, workout builder with view-grouped flow, calibration versioning with longitudinal progress, and extended external validation against RepCount Part-B.

### The cut ladder — revised

| \#  | Cut                                                   | Saves  | Academic cost                                 |
|-----|-------------------------------------------------------|--------|-----------------------------------------------|
| 1   | Windows PWA *(or downgrade to replay-only)*           | 2.5 wk | **None**                                      |
| 2   | Workout builder, progress charts, fingerprint gallery | 1 wk   | None                                          |
| 3   | Side Plank + Hip Abduction (17 → 15 entries)          | 3 d    | None                                          |
| 4   | Ghost overlay                                         | 3 d    | None academically; hurts the demo             |
| 5   | DTW-vs-Euclidean and filter ablations                 | 3 d    | Moderate                                      |
| 6   | Exercise verifier                                     | 4 d    | Moderate                                      |
| 7   | Per-exercise ML classifiers (→ MVP)                   | 1.5 wk | **Significant** — loses the supervised result |

**Never cut:** data collection · calibration · Layer 1 rules · the personalized-vs-population ablation · subject-independent evaluation · claim-level enforcement · the held-out set.

### The decision rule

At every milestone: **if I stopped today, could I defend what exists?**

A finished MVP with complete evaluation beats a half-built Full version in marks, in learning, and in the viva. The ladder exists so you can make that call in week 12 calmly rather than discovering in week 19 that four things are 70% done.

## 12. The nine answers

### A. What should definitely remain

The profile-driven shared engine · pure-Kotlin analysis core · bilateral-by-default signals · the camera-view contract and setup gate · personal calibration and baselines · **the three claim levels, type-enforced** · the two-tier real-time / post-rep split · RepRecord + AnalysisResult + local storage + export · deterministic-first · Android before web · early data collection · the existing Kotlin prototype as reference.

### B. What should definitely be removed

| Removed                            | Reason                                                                                   |
|------------------------------------|------------------------------------------------------------------------------------------|
| **Shared fault detectors**         | Four are threshold rules; two would pool semantically different faults under one label   |
| **Predicted accuracy ranges**      | Borrowed from a different sensor, task and population. Deleted from the Master Plan too. |
| **"Bit-identical" parity claim**   | kotlin.math on JS is not bit-identical to the JVM                                        |
| **Front Raise**                    | No camera view sees the primary signal cleanly                                           |
| **Overhead Triceps Extension**     | Wrist occluded behind the head at the ROM extreme                                        |
| Calf Raise                         | Already cut — below the noise floor                                                      |
| Per-side analysis of Bent-over Row | Far arm occluded; near side only                                                         |

### C. What should be postponed

The 10 Tier B/C profiles (to P10) · the web app (to P11, or never) · ghost overlay, fingerprint gallery, progress charts · workout builder · calibration versioning · anything in Stretch.

### D. Highest technical risk

**Silent cross-boundary or cross-platform divergence.** Three specific forms: front-camera mirroring inverting left/right, a Python/Kotlin feature-scaling mismatch degrading models on-device, and JS/JVM numeric drift crossing a threshold. All three fail silently — nothing errors, the numbers are just wrong. Each needs a permanent test written in the phase it belongs to.

### E. Highest academic risk

**Insufficient labelled data per exercise for the supervised experiment (G3).** ~500 reps across 5 exercises, split by fault class and held out by subject, may leave some classes with too few positives to learn from. Mitigation: state the counts honestly, report only classes with adequate support, and treat the shortfall as a finding about small-data constraints — which it genuinely is.

### F. Highest timeline risk

**Data collection starting too late.** It is on the critical path, it depends on other people's calendars, and it gates both P9 and P12. M6 in week 7 is the hard checkpoint. Second place: the PWA consuming evaluation time — which is why it is last and first to cut.

### G. The assumption I am least confident about

**That five flagship exercises × ~12 subjects yields enough labelled data for the supervised comparison to be meaningful.**

Everything else here I would defend firmly. This one I am genuinely unsure about, and it is unusual in that you cannot resolve it by thinking — only by labelling and counting. Two consequences: **check it the moment labelling finishes**, and **do not build your report's narrative around Layer 3** until it passes. The personalization experiment, which needs no fault labels, should carry the report instead.

Second-least confident: that glute bridge segments as reliably as I have assumed. MediaPipe is weaker on lying poses than standing ones. Test it in P4, and if it is poor, promote sit-up to Tier A in its place.

### H. If you had to simplify by 20%

| Cut                                                       | Effect                                          |
|-----------------------------------------------------------|-------------------------------------------------|
| **The Windows PWA**                                       | −13% of total effort, **zero academic cost**    |
| **Ghost overlay + fingerprint gallery + progress charts** | −5% — demo polish only                          |
| **Two entries (Side Plank, Hip Abduction)**               | −2% — lands on exactly 15 entries / 13 profiles |

That is 20% off with no loss of any research contribution. It is also the cheapest 20% in the project, which is why the PWA sits where it does in the build order.

### I. If you had to make it 20% stronger academically without doubling the work

Three additions, in order of value per hour:

**1. Inter-rater agreement on a labelled subset** — half a day. Get one other person to label 50 reps and report Cohen's kappa. This single number transforms how your labels are read: it converts "I labelled these myself" from an unmitigated weakness into a quantified, acknowledged limitation. Nothing else in the project buys as much credibility per hour.

**2. A camera-angle sensitivity study** — one day of recording, one day of analysis. Quantify how measured joint angles change as the camera moves 0° / 30° / 60° off-axis. It directly answers the hardest monocular-pose question, and almost nobody at this level does it.

**3. A learning-curve analysis** — half a day, using data you already have. Train the classifiers on 25%, 50%, 75% and 100% of the labels and plot performance against training-set size. It tells you and the examiner whether more data would have helped, turns your dataset limitation into an analysed result, and makes the G3 shortfall — if it happens — into a finding rather than an excuse.

All three are experiments on data you are already collecting. Together they are about three days of work and they strengthen the academic core more than any feature would.

## 13. Gates and verdict

### The three go/no-go gates

| Gate                     | When                             | Test                                                                                                                                    | If it fails                                                                                 |
|--------------------------|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **G1** Multiplatform     | **P0, week 1**                   | Port the real FSM + angle code to JS; call from React; assert the rep count matches the JVM; inspect the generated .d.ts and build time | Web becomes a **replay-only client** consuming exported JSON. Android unaffected.           |
| **G2** Device reality    | **P4, week 6**                   | ≥ 20 fps sustained on a mid-range phone; 15-minute thermal run; front-camera left/right test; 3–4 real subjects                         | Cut exercises and reduce the target frame rate. **Never cut evaluation.**                   |
| **G3** Label sufficiency | **After P7 labelling, ~week 12** | Per flagship exercise, per fault class: are there enough positives to train and hold out by subject?                                    | Ship Layers 1, 2 and 4. Report the shortfall with a learning curve as a small-data finding. |

G1 protects the platform bet. G2 protects everything built on the pipeline. G3 protects the report's narrative — and it is the one most likely to trip.

### What to build first — the first three weeks

1.  **Day 1–2:** Scope freeze, licence table, evaluation metrics table written out

2.  **Day 3:** **G1 spike** — real code, real gate, decide the web strategy

3.  **Day 4–5:** Contract definitions, including the **JS facade** and the **mirroring contract**

4.  **Week 2:** Shared core module, normalisation, validity model, with tests on both targets

5.  **Week 3:** Kinematics with bilateral signals as a compile-time shape, filter chain, resampling, ablation harness

Then P3 rep engine, then P4 on real people by week 6, then export by week 7 — and **start asking people to be subjects in week 5**, while you are still writing the rep engine.

### Verdict

## YELLOW

**Build it. The architecture is right. Three things must be validated as you go, and one part of the previous plan must be replaced before you write ML code.**

|                         |                                                                                                                                                               |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Why not GREEN**       | G1 and G2 are unvalidated; G3 is unknowable until week 12; the ML design changed materially in this review                                                    |
| **Why not RED**         | Nothing structural is wrong. The engine, the claim levels, the calibration approach, the data model and the phase order all survived scrutiny.                |
| **What turns it GREEN** | G1 passing in week 1 and G2 passing in week 6. At that point the remaining uncertainty is G3, which is a reporting question rather than an architectural one. |

### The three things to hold on to

**Your engine was already right; the ML plan was not.** The correction makes the project less ambitious on paper and more likely to finish, and it decouples the product from the dataset — which, given that data collection is your biggest schedule risk, is the most valuable thing this review produced.

**Measure, then claim.** Three levels, enforced by the type system. Fifteen exercises get measurement, five get judgement, and an examiner can see exactly where the line is and why.

**The evaluation is the project.** The app is how you generate data to evaluate. When the schedule tightens — and it will — cut the web app, cut the polish, cut entries down to fifteen. Do not cut the experiments.

*This document supersedes the Master Development Plan on: the shared fault detectors (§2), the parity claim (§3), the exercise list (§5), the ML strategy (§7), and P8/P9 module structure (§9). The predicted-accuracy statement has been removed from the Master Plan itself.*
