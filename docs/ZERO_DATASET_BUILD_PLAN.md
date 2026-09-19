# Kinesis — Zero-Dataset Build Plan

Sep 18, 2026 · @Someone

Revised scope for building Kinesis without collecting or labelling a movement dataset. Supersedes the data-collection sections of the Master Development Plan and the Final Architecture Decision.

## What changed, and what it costs

One layer is cut. Everything else in the architecture survives intact.

The constraint is specific: you can run the app on a live camera to test that it works, but you will not record subjects or label reps to build a training set. That distinction matters more than it sounds, because the two things it separates were previously bundled together as "data collection".

### Calibration is not training data

When a user opens Kinesis and performs 5-10 reps so the app learns their personal baseline, that is the app running. The baseline is computed on-device, unsupervised, from that person's own reps, in the moment. Nothing is labelled by a human, nothing is retained as a dataset, and no second person is involved.

You testing that on yourself is testing a feature. It is not building a corpus. Personalization therefore survives completely.

### What is cut

| Removed                                                    | Why it cannot survive                                                                                        | What it was worth                                     |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| Layer 3 - five per-exercise supervised quality classifiers | Needs human-judged fault labels on several hundred reps; no public dataset supplies them for these exercises | Was the riskiest item in the plan, gated behind G3    |
| Rules-vs-ML comparison study                               | Depends entirely on Layer 3 existing                                                                         | One of three claimed research contributions           |
| Gate G3 (label sufficiency check)                          | Nothing left to gate                                                                                         | Was named "the assumption I am least confident about" |
| Self-collected subject corpus (10-15 people, ~500 reps)    | Ruled out by the constraint                                                                                  | ~45 hours of your own non-delegable labour            |

The honest framing: you are cutting the component that was already least likely to succeed. G3 existed precisely because nobody knew whether enough labelled reps per fault class would materialise. That uncertainty is now resolved by removing the dependency rather than by gambling on it.

### What survives

MediaPipe pose estimation. The profile-driven analysis engine. The repetition FSM and the isometric hold analyser. Layer 1 deterministic deviation rules. Layer 2 exercise-specific technique rules, now justified by cited biomechanics thresholds rather than measured against your own labels. Layer 4 personal anomaly detection, which was always unsupervised. The exercise verifier, trainable on public exercise-identity labels. The entire Android app, the visualization surface, and the cross-platform core.

Gate G2, the device reality check, also survives and must still be run - you can point a camera at yourself, so there is no excuse for shipping a camera app that has never processed a live human.

### The cost, stated plainly

The project rating moves from roughly 8.5 planned to roughly 8 planned, and 7.5 after normal slippage. What you lose is the "I collected and evaluated my own dataset" narrative, which is uncommon at BCA level and was part of what made Kinesis distinctive.

What partly offsets it: evaluation on a published, peer-reviewed benchmark with proper subject separation is a stronger methodological claim than evaluation on ten acquaintances in a room, because the subject separation was not designed by the person being graded.

## Verified data sources

MM-Fit is confirmed usable, permissively licensed, and covers seven of your exercises with 6,160 repetitions from 10 subjects. It has no form-quality labels. Two datasets that do have form labels exist, but both sit behind non-commercial licence agreements.

### MM-Fit - the one you build on

| Property            | Verified value                                                                                                                                                                                |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Subjects            | 10                                                                                                                                                                                            |
| Sessions            | 21 sessions, 809 minutes total                                                                                                                                                                |
| Repetitions         | 6,160 reps across 616 sets                                                                                                                                                                    |
| Exercises           | 10: squats, lunges, bicep curls, sit-ups, push-ups, seated overhead triceps extensions, standing dumbbell rows, jumping jacks, seated dumbbell shoulder press, seated dumbbell lateral raises |
| Labels              | Exercise set boundaries as video frame start/end, repetition count per set, exercise type. Annotation precision 2-3 frames (60-100 ms)                                                        |
| Form quality labels | None                                                                                                                                                                                          |
| Pose provided       | 2D via [OpenPose](https://arxiv.org/html/2406.16886v1), 3D via the Martinez et al. 2D-to-3D regression model, 17 joints                                                                       |
| Video               | RGB and depth MP4, 40.8 GB, 20 sessions                                                                                                                                                       |
| Licence             | [Creative Commons Attribution 4.0 International](https://zenodo.org/records/7607736) - attribution required, commercial use permitted                                                         |

Source: [MM-Fit paper](https://vradu.uk/UbiComp2021.pdf), [project site](https://mmfit.github.io/), [Zenodo video record](https://zenodo.org/records/7607736), [starter code, MIT](https://github.com/KDMStromback/mm-fit).

### The pose format problem, and its fix

MM-Fit's bundled skeletons are OpenPose-derived with 17 3D joints. Kinesis computes every angle from MediaPipe's 33 landmarks. The two joint sets are not interchangeable, and mapping between them would quietly change the meaning of every angle you compute.

Do not use the bundled skeletons as your primary input. MM-Fit ships the raw RGB video under CC BY 4.0, so run MediaPipe over that video yourself and attach MM-Fit's set boundaries and rep counts to your own MediaPipe output. You then evaluate the exact pipeline you ship, on labelled real-human movement, having recorded nothing.

This is the single most important implementation detail in this document.

### What MM-Fit's labels let you measure

You get set boundaries and a repetition count per set, not per-rep start and end frames. So counted reps versus true count per set is directly measurable across 616 sets - that is the standard repetition-counting metric and it is the strongest empirical claim in the revised plan. Per-rep boundary precision is not measurable against ground truth, so do not claim it.

### Form-label datasets that exist but are gated

| Dataset                                                                                                     | What it has                                                                                                                                                                                    | Why it is not the plan                                                                                                                                                 |
|-------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [FLEX](https://haoyin116.github.io/FLEX_Dataset/) (arXiv [2506.03198](https://arxiv.org/html/2506.03198v4)) | 20 weight-loaded exercises, 38 subjects (10 expert, 8 amateur, 20 novice), per-repetition error types and quality scores, five views including one smartphone view, sEMG optional not required | Licence agreement required, non-commercial academic research only; approval time and undergraduate eligibility both unknown                                            |
| [M3GYM](https://finalyou.github.io/M3GYM/) (CVPR 2025)                                                      | 50+ subjects, 500+ action labels with sports-expert quality assessments, 2D/3D keypoints and meshes                                                                                            | Data Access Protocol Form required, non-commercial only; eight fisheye overhead cameras and multi-person scenes make the view geometry wrong for a single phone camera |
| InfiniteRep                                                                                                 | Synthetic exercise sequences, CC BY 4.0, large volume                                                                                                                                          | No form or quality labels; useful only as extra volume for rep-counting stress tests                                                                                   |

On non-commercial: a final-year academic project is non-commercial academic research, so those terms are not automatically disqualifying the way they would be for a shipped product. The real obstacles are procedural - you must apply, wait, and may be refused - and FLEX's per-repetition error labels are the only thing that could restore Layer 3 without you recording anything.

So apply to FLEX in week 1. It costs one email. Then build as though it will never arrive, because the plan must not have a dependency you cannot control. If it does arrive, Layer 3 comes back as a bonus chapter rather than a load-bearing wall.

## Revised exercise scope

Four exercises match MM-Fit directly and can carry benchmark-backed claims. Three match with a posture difference that must be handled explicitly. The rest are configuration-only, validated by your own live testing and nothing else.

| Kinesis exercise            | Tier     | MM-Fit coverage              | Strongest honest claim                                                                   |
|-----------------------------|----------|------------------------------|------------------------------------------------------------------------------------------|
| Squat                       | A        | Direct                       | Rep counting measured on benchmark; deviation; rule-based technique                      |
| Push-up                     | A        | Direct                       | Rep counting measured on benchmark; deviation; rule-based technique                      |
| Bicep Curl                  | A        | Direct, but alternating-arm  | Rep counting measured once arm semantics are reconciled; deviation; rule-based technique |
| Lunge                       | B        | Direct                       | Rep counting measured on benchmark; deviation                                            |
| Shoulder Press              | A or B   | Seated in MM-Fit             | Measured only if your profile is seated too; otherwise deviation only                    |
| Lateral Raise               | A        | Seated in MM-Fit             | Measured only if your profile is seated too; otherwise deviation only                    |
| Bent-over Row               | B        | Standing row, near-side only | Deviation only; posture mismatch too large to claim measurement                          |
| Glute Bridge                | B        | None                         | Deviation only, validated by live testing                                                |
| Lying Leg Raise             | B        | None                         | Deviation only, validated by live testing                                                |
| Wall Sit, Plank, Side Plank | C        | None                         | Hold duration and stability; no repetition claim applies                                 |
| Goblet Squat, Reverse Lunge | Variants | Via parent                   | Inherit the parent profile's claim ceiling                                               |

### Three traps in that table

**Bicep curls in MM-Fit alternate arms.** If your profile counts a two-arm curl as one repetition and MM-Fit counts each arm, your rep-count comparison is wrong by a factor of two and it will look like a tuning problem rather than a definitional one. Resolve the semantics before you measure anything, and write the resolution into a test.

**Shoulder press and lateral raise are performed seated in MM-Fit.** Seated removes the lower body from the picture and changes the trunk reference. A rule validated on seated data does not transfer to standing. Pick one: make the Kinesis profile seated so the benchmark applies, keep it standing and accept these two as deviation-only, or ship both as variants. The first option is the cheapest and it is what I would do for the flagship set.

**MM-Fit used one fixed Orbbec Astra Pro at 30 Hz, not a phone at arbitrary placement.** So MM-Fit validates your algorithm, and your own live testing validates your deployment condition. They are two different claims. Do not let the report blur them into one sentence.

### Two changes worth making

Add **sit-up** to the library. It is in MM-Fit with full labels, it is a side-view exercise whose primary signal is trunk flexion and therefore clearly visible, and it costs nothing in data. It strengthens the flagship set at no marginal effort.

Keep **jumping jacks** out of the exercise library but keep the data. It is the ideal negative class for the exercise verifier - fast, whole-body, unmistakably not any of your target movements - and training the verifier to reject it makes the verifier more useful, not less.

### What happens to Level 3 claims

Level 3 is technique judgement, and with no fault labels you cannot measure whether yours is correct. You have two options and only one of them is honest in both directions.

Suppressing Level 3 entirely is safe but throws away the feature that makes the app feel like it understands movement rather than counting it. Keeping Level 3 for Tier A, with each rule traced to a published biomechanics threshold and cited in the report, is defensible - a rule grounded in literature is a legitimate design, it simply is not a measured result.

Take the second option, and state the limitation in exactly these terms: these rules implement thresholds from cited sources, and their agreement with expert human judgement was not measured in this work. Never attach an accuracy figure to them. The claim-level type system already in the architecture is what stops this leaking - a rule with no validation behind it still carries claimLevel = 3 and still gets the tier badge in the UI.

## Revised analysis architecture

Three of four analysis layers survive, and the project still contains both a supervised and an unsupervised machine learning component. Only Layer 3 is removed.

flowchart TD  
CAM\[Camera frame\] --\> MP\[MediaPipe Pose\<br/\>33 landmarks\]  
MP --\> KIN\[Kinematics\<br/\>bilateral angles\]  
KIN --\> RTR{Exercise mode}  
RTR --\>\|repetition\| FSM\[Rep FSM\]  
RTR --\>\|isometric\| HOLD\[Hold analyser\]  
FSM --\> REC\[RepRecord\]  
HOLD --\> REC  
REC --\> L1\[Layer 1\<br/\>deviation rules\]  
REC --\> L2\[Layer 2\<br/\>technique rules\]  
REC --\> L4\[Layer 4\<br/\>personal anomaly\]  
REC --\> VER\[Exercise verifier\]  
L1 --\> OUT\[Findings with claim level\]  
L2 --\> OUT  
L4 --\> OUT  
VER --\> OUT

Everything above RepRecord is deterministic and unchanged from the original architecture. Everything below it is where the revision bites.

| Layer                                    | What it does                                                                   | Data it needs                                    | Claim level      | Status                               |
|------------------------------------------|--------------------------------------------------------------------------------|--------------------------------------------------|------------------|--------------------------------------|
| Layer 1 - deviation rules                | Flags range, tempo and symmetry departures from the user's own baseline        | None beyond the user's calibration reps          | 1 and 2          | Unchanged                            |
| Layer 2 - technique rules                | Exercise-specific form rules for Tier A, thresholds from cited literature      | None; thresholds are sourced, not learned        | 3, rule-based    | Kept, but now explicitly unvalidated |
| Layer 3 - supervised quality classifiers | Would have judged rep quality from labelled faults                             | Several hundred human-labelled reps per exercise | 3, measured      | **Removed**                          |
| Layer 4 - personal anomaly detection     | One-Class SVM or Isolation Forest fitted per user at runtime on their own reps | None; unsupervised, on-device                    | 2                | Unchanged                            |
| Exercise verifier                        | Multiclass classifier flagging exercise-selection mismatches                   | MM-Fit exercise labels, free                     | Not a form claim | Unchanged                            |

### The project still has machine learning in it

This matters for how the work is graded, so it is worth being precise. Removing Layer 3 does not leave you with a rules-only project.

The exercise verifier is a supervised multiclass classifier trained on MM-Fit's ten exercise classes and evaluated leave-one-subject-out across its ten subjects. That is a complete, properly-evaluated supervised learning component with a real subject-independent protocol.

Layer 4 is unsupervised learning - a one-class model fitted per user, on-device, at runtime. It is also the more interesting of the two, because a model that trains itself on the individual user is a genuinely defensible design choice rather than a textbook exercise.

So the project retains supervised learning, unsupervised learning, and a subject-independent evaluation protocol. What it loses is a second supervised task, not the category.

### What Layer 2 must now look like in code

Each Tier A technique rule needs a citation attached to it in the source, not just in the report. A rule that fires with no traceable threshold behind it is the thing that turns into an unsupportable claim during a viva.

Give every rule a sourceRef field alongside its existing ruleVersion, holding the citation for the threshold it applies. When an examiner asks where a number came from, the answer is in the code, and the report's limitations section says the same thing: implemented from cited thresholds, agreement with expert judgement not measured here.

## The headline experiment, without labels

The personalization question survives intact, because it never needed fault labels - it needs many repetitions from several subjects, which is exactly what MM-Fit is. It becomes three linked studies on 6,160 repetitions from 10 subjects.

The research question stays as it was: **do personal baselines outperform population thresholds for flagging movement deviation?**

### Study 1 - variance decomposition

For each exercise and each kinematic metric, decompose total variance into between-subject and within-subject components and report the intraclass correlation:

ICC = between-subject variance / (between-subject variance + within-subject variance)

Fit a one-way random-effects model with subject as the grouping factor, per metric, per exercise. Report ICC with confidence intervals and the raw variance components.

A high ICC means people's normal execution genuinely differs more than their own repetitions vary, which is a direct empirical argument that any single population threshold must misfire on somebody. A low ICC would mean personalization buys little, and that is a legitimate result to report rather than a failure.

Metrics worth decomposing: peak joint angle, range of motion, concentric and eccentric duration, left-right symmetry index, trunk angle range, and rep-to-rep consistency.

### Study 2 - population versus personal thresholds

This is the study that produces the headline number.

1.  Derive a population threshold for each metric from all subjects pooled.

2.  Apply it to every repetition and record the flag rate per subject.

3.  For each subject, hold out their first 8 repetitions, compute a personal baseline from those, and derive a personal threshold.

4.  Apply the personal threshold to that subject's remaining repetitions and record the flag rate.

5.  Compare flag rates, per subject and pooled.

The logic that makes this work without labels: MM-Fit's repetitions are routine workout execution, not deliberate error demonstrations, so a threshold that flags a large share of them is generating false positives. You can measure a false-positive rate against unlabelled-but-assumed-routine repetitions even though you cannot measure a true-positive rate.

State that assumption explicitly in the report. These repetitions are not certified as correctly performed, so some flags may be genuine. The claim is about flag rate on routine repetitions, not about precision.

The result to look for is subject-level: if the population threshold flags most repetitions for two or three specific subjects and almost none for the others, that is the personalization argument made visible, and it is a far more persuasive figure than a pooled average.

### Study 3 - how many calibration repetitions are enough

Take each subject's repetitions, compute their personal baseline from the first N for N from 3 to 20, and measure how the baseline estimate converges as N grows, with bootstrap confidence intervals.

This answers a real product question with data - why the app asks for a particular number of calibration repetitions - and it is the cheapest of the three studies to run. It also gives the report a figure that is genuinely about design rather than about accuracy.

### What these three prove, and what they do not

They establish that individual differences in these metrics are large enough to break fixed thresholds, that personal baselines reduce spurious flagging, and that a stable personal baseline is reachable in a practical number of repetitions.

They do not establish that the system identifies incorrect technique correctly. Nothing in this plan does, and the report must say so in the limitations section rather than leaving a reader to infer a stronger claim than the evidence supports.

## Revised evaluation plan

Six studies survive, five of them run entirely on public data and your own device. Every number in the report comes from one of these.

| Study                          | Data                            | Protocol                                                  | Metrics                                                                        |
|--------------------------------|---------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------|
| Repetition detection           | MM-Fit, 616 sets, 6,160 reps    | Counted reps versus labelled count, per set               | Mean absolute error, share of sets exact, share within plus or minus one       |
| Exercise verifier              | MM-Fit, 10 classes, 10 subjects | Leave-one-subject-out                                     | Accuracy, per-class F1, confusion matrix                                       |
| Personalization (Studies 1-3)  | MM-Fit                          | Variance decomposition, threshold comparison, convergence | ICC with intervals, flag rate per subject, baseline convergence curve          |
| Device performance             | Your own phone, live            | Sustained session, 15-minute thermal run                  | Frames per second sustained, per-frame latency, memory, thermal throttle point |
| Pose reliability in deployment | Your own phone, live            | Per exercise, per camera view                             | Landmark visibility and confidence, failure modes observed                     |
| Cross-platform parity          | Golden files                    | Same inputs through JVM and JS targets                    | Discrete outputs identical, continuous within 1e-9 relative                    |

### Where the camera-angle study can come from

The camera-angle sensitivity study was on the earlier list of things worth adding, and it does not need a recorded dataset. Two routes are open.

Run it live: place the phone at several angles and distances, perform reps, and log the computed metrics rather than storing video. That is instrumentation, not data collection, and it produces a real sensitivity table.

Or check whether InfiniteRep renders its synthetic sequences from multiple camera positions. Synthetic data can be re-rendered from arbitrary viewpoints, so if it ships more than one, you get a controlled angle study for free and with perfect ground truth on the angle itself.

### What must never be claimed

This list is the one that protects you in a viva. Each entry is something the evidence in this plan cannot support.

- Any accuracy, precision or recall figure for technique or form correctness. There are no fault labels, so there is no such number.

- Per-repetition boundary precision. MM-Fit labels set boundaries and repetition counts, not individual repetition start and end frames.

- That personalization improves fault detection. What is measured is flag rate on routine repetitions, which is a false-positive argument, not a detection argument.

- That rules validated on MM-Fit's seated shoulder press or lateral raise apply to the standing versions.

- That results generalise beyond MM-Fit's single fixed camera at 30 Hz, unless your own live sensitivity study says so.

- Any number at all before it has been measured on this system. The rule from the Final Architecture Decision stands unchanged: do not state an expected result.

### Reporting discipline

Every figure carries its protocol and its n. Every limitation in the list above appears in the limitations section in plain language rather than being left for a reader to work out.

The honest summary sentence for the whole project, which is worth drafting early and keeping the work aligned to, is roughly this: the system measures movement reliably, personalizes its thresholds to the individual with measured justification, and applies technique rules drawn from published sources whose agreement with expert judgement was not evaluated in this work.

## Revised phases and timeline

The thirteen phases stay. One is rewritten, two shrink, and the single most schedule-fragile dependency in the original plan disappears.

| Phase                              | Change                                                                                                                                                       | Effect                                                             |
|------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| P0 Architecture Lock               | Add the FLEX access application as a day-one action                                                                                                          | Neutral                                                            |
| P1 Shared Core                     | Unchanged                                                                                                                                                    | \-                                                                 |
| P2 Kinematics and Temporal         | Unchanged                                                                                                                                                    | \-                                                                 |
| P3 Profiles and Rep Engine         | Unchanged                                                                                                                                                    | \-                                                                 |
| P4 Device Reality Check            | Unchanged, but now your only evidence for deployment conditions                                                                                              | More important                                                     |
| P5 RepRecord and Export            | Unchanged                                                                                                                                                    | \-                                                                 |
| P6 Calibration and Personalization | Unchanged                                                                                                                                                    | \-                                                                 |
| P7 Dataset and Labelling           | **Rewritten** as a public-data pipeline: acquire MM-Fit, run MediaPipe over its RGB video, align set boundaries and rep counts, build the evaluation harness | Fully delegable; no human scheduling                               |
| P8 Form Engine                     | Layer 1 and Layer 2 only, each rule carrying its citation                                                                                                    | Slightly smaller                                                   |
| P9 ML Layer                        | Exercise verifier and Layer 4 only                                                                                                                           | Roughly halved                                                     |
| P10 Visualization and Android      | Unchanged                                                                                                                                                    | \-                                                                 |
| P11 Windows PWA                    | Still the cheapest thing to cut                                                                                                                              | Cut on the minimal path                                            |
| P12 Evaluation and Documentation   | Restructured around the six studies                                                                                                                          | Slightly larger, but it is Python and statistics, highly delegable |

### The risk that disappears

P7 previously depended on recruiting people, scheduling them, obtaining consent, and getting them to show up. That is the most schedule-fragile item in any student project, because it is the only one where someone else's calendar can stop your work.

It now depends on a download completing. That is a genuine reduction in risk, not merely in effort, and it is worth saying out loud because it changes what can actually go wrong between now and submission.

### The logistics that replace it

MM-Fit's video record is 40.8 GB, and re-extracting MediaPipe landmarks over 809 minutes of footage is several hours of machine time. Neither is difficult, but both need to start early rather than in the week you need the results.

Start the download in week 1, alongside the FLEX application. Both are things that take calendar time and no attention.

### Timeline

| Path                             | Optimistic | Realistic | Worst case |
|----------------------------------|------------|-----------|------------|
| Minimal - Android only, no PWA   | 7 weeks    | 9 weeks   | 13 weeks   |
| Full - adds PWA and parity suite | 11 weeks   | 14 weeks  | 19 weeks   |

The minimal path is roughly two weeks shorter than the original MVP while carrying a stronger evaluation chapter, because the three personalization studies were previously deferred and are now affordable.

Take the minimal path. The PWA remains what it always was: two and a half weeks of engineering that adds nothing academic, and the first thing to cut when time is short.

## Your personal work

Roughly 48 hours across nine weeks, or about five to six hours a week. Removing the recording campaign and the labelling pass takes 45 hours out of the previous estimate.

| Work                                                                                                    | Who does it     | Your hours |
|---------------------------------------------------------------------------------------------------------|-----------------|------------|
| Live device testing - performing reps in front of the phone as the engine changes                       | You, physically | ~8         |
| Judgement calls - thresholds, what the results mean, what to cut                                        | You             | ~10        |
| Supervising the build - reviewing generated code, catching silent failures                              | You             | ~15        |
| Understanding the system well enough to defend it                                                       | You             | ~15        |
| Kotlin shared core, kinematics, rep FSM, exercise profiles                                              | Claude Code     | 0          |
| Android app, CameraX pipeline, Compose UI, skeleton rendering                                           | Claude Code     | 0          |
| MM-Fit acquisition, MediaPipe batch extraction, label alignment, evaluation harness                     | Claude Code     | 0          |
| Python analysis - variance decomposition, threshold comparison, convergence, verifier training and LOSO | Claude Code     | 0          |
| Test suites including every guard test                                                                  | Claude Code     | 0          |
| Report drafting, figures, tables from your outline and the measured results                             | Claude Code     | 0          |

### The two categories people underestimate

**Supervision is not reading a diff.** This project has four specific silent-failure modes that generated code will produce cheerfully and that no test catches unless someone wrote the test on purpose: bilateral signals quietly averaged into one number, front-camera mirroring inverting anatomical left and right, analysis logic drifting into platform-specific code, and feature scaling differing between the Python training script and the Kotlin evaluator. Section 9 turns each into a permanent test. Your job is to know these exist and to check they are still guarded.

**Understanding is the floor, not the padding.** Everything else on this list can shrink. This one cannot. At the viva you stand alone in front of someone who can ask why the ICC for one metric came out lower than the others, and no amount of well-generated code answers that for you.

### Where you could accidentally spend more

Live testing is the item most likely to grow, because it is tempting to keep re-testing every time something changes. Batch it: test after P4, after P8, and once before the demo. Three sessions, not thirty.

The report is the other one. Draft it from the measured results at the end rather than writing prose that then has to be rewritten when a number changes.

### An honest caution

Five to six hours a week is genuinely minimal for a 150-mark project, and it works only because the architecture is already decided and written down. The plan is thin on your time precisely because it is thick on prior thinking - which has already happened, in the four documents this one supersedes.

## Guard rails for building with Claude Code

Put this file at the repository root as CLAUDE.md before the first line of code is written. It is read automatically at the start of every session, and it is the only thing standing between you and a codebase that looks correct and is quietly wrong.

\# Kinesis - working rules  
  
Camera-based exercise movement analysis. Kotlin Multiplatform shared core,  
Android app, MediaPipe Pose. Architecture is decided - see /docs.  
  
\## Non-negotiables  
  
1. Bilateral signals are NEVER averaged. Left and right stay separate via  
BilateralSignal(left, right). Collapsing them needs an explicit, named  
reducer at the call site - never a silent mean.  
  
2. No analysis logic in platform code. Kinematics, FSM, rules and metrics  
live in commonMain. Android and JS hold camera, rendering and storage only.  
  
3. The real-time path is O(1) per frame. Do not reprocess signal history per  
frame. Do not run ML per frame - models take a completed RepRecord. Do not  
drive Compose recomposition from pose frames; the skeleton draws to a  
dedicated Canvas.  
  
4. Use Double in the shared core. Not Float, not Long.  
  
5. Claim levels are enforced by the type system. A Tier B or Tier C profile  
must be structurally unable to construct a Level 3 finding. UI text is not  
enforcement.  
  
6. Front-camera mirroring is corrected in the platform adapter. The shared  
core always receives anatomical left and right.  
  
7. Every Layer 2 technique rule carries a sourceRef citing the published  
threshold it implements. A rule with no source does not ship.  
  
8. MediaPipe landmarks are the only pose input to the engine. Do not feed  
MM-Fit's bundled OpenPose skeletons into it - run MediaPipe over MM-Fit's  
RGB video instead.  
  
9. Never write a predicted or expected accuracy figure anywhere - not in  
comments, docs, or report drafts. Numbers appear only after measurement.  
  
10. No new dependencies without asking first.

### The tests that catch the silent failures

Each of these guards a failure that produces working-looking code and wrong results. Write them early, keep them permanently.

| Test                                               | What it catches                                                                 |
|----------------------------------------------------|---------------------------------------------------------------------------------|
| Mirroring regression                               | Front-camera input silently inverting anatomical left and right                 |
| Claim-level enforcement                            | A Tier B or C profile emitting a technique judgement it is not entitled to make |
| Bilateral integrity                                | Any path collapsing a BilateralSignal to a scalar without a named reducer       |
| Golden files in commonTest, run on JVM and JS      | Algorithmic drift between platforms                                             |
| Python-Kotlin numeric parity on 200+ held-out reps | Feature scaling differing between training and on-device inference              |
| Rep-count semantics                                | The bicep-curl alternating-arm definition changing without anyone noticing      |
| Per-frame budget                                   | Allocation or history iteration creeping into the real-time path                |

### How to actually run the build

**Put the documents in the repository.** Export this document and the Final Architecture Decision into /docs as markdown. Claude Code reads them, and decisions that live in a chat history are decisions that get silently re-litigated in week six.

**Point it at the existing Kotlin prototype rather than starting clean.** The prototype already encodes the rep FSM and the profile structure. It is a specification, not legacy code to discard.

**Build three exercise profiles first, not fifteen.** Pick one archetype from each mode - a squat for bilateral lower body, a bicep curl for the alternating-arm case, a plank for isometric hold - and validate all three against live testing before scaffolding the rest. Fifteen untested profiles generated in one pass is fifteen things that are wrong in the same way.

**Review against the non-negotiables, not against style.** Generated Kotlin will almost always be readable. The question is never whether it looks fine; it is whether rule 1 or rule 6 got quietly violated in a helper function three files away.

## Verdict and what to build first

**GREEN**, with one gate remaining. The Final Architecture Decision returned YELLOW on three gates. Two of them no longer exist.

| Gate                      | Was                                                                     | Now                                                               |
|---------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------|
| G1 - multiplatform spike  | Kotlin/JS viability had to be proven in a one-day spike                 | **Gone.** The minimal path cuts the PWA, so nothing depends on it |
| G2 - device reality check | Sustained frame rate, thermal behaviour, mirroring on real hardware     | **Remains.** Week 3, under your control, and you can test live    |
| G3 - label sufficiency    | Whether enough labelled faults would exist for Layer 3 to mean anything | **Gone.** Layer 3 is removed                                      |

The distinction that makes this GREEN rather than YELLOW: G1 and G3 could each have forced a redesign. G2 cannot. If G2 fails, the response is optimization - lower resolution, frame skipping, a lighter pose model - not a different architecture. That is a risk, not a gate.

### Rating

Around 8 as planned, around 7.5 after normal slippage. Down half a point from the original 8.5.

The more useful observation is about variance rather than the mean. Kinesis was previously the higher-ceiling, higher-variance option next to Aletheia's safer floor. This revision cuts the ceiling slightly and raises the floor substantially, because the two things most likely to go wrong - a recruitment campaign that stalls and an ML chapter that turns out to have too little data - are both gone. It is now a better bet, not merely a smaller one.

### Weeks 1 to 3

**Week 1 - start the slow things, then build the types that enforce the rules.**

1.  Day one: apply for FLEX access and start the 40.8 GB MM-Fit download. Both take calendar time and no attention.

2.  Initialise the repository, write CLAUDE.md, export this document and the Final Architecture Decision into /docs.

3.  P0 and P1: shared core skeleton with BilateralSignal, the sealed Finding type and ClaimLevel.

4.  Write the claim-level enforcement test and the bilateral integrity test before the code they guard. A guard written afterwards guards whatever the code already does.

**Week 2 - three profiles, not fifteen.**

1.  P2 and P3: kinematics, the repetition FSM, and three archetype profiles - squat for bilateral lower body, bicep curl for the alternating-arm case, plank for isometric hold.

2.  Run MediaPipe batch extraction over MM-Fit's RGB video in the background.

3.  Resolve the bicep-curl repetition semantics against MM-Fit's alternating-arm definition and pin it with a test.

**Week 3 - the gate.**

1.  P4 on your own phone: sustained frame rate, a 15-minute thermal run, front-camera mirroring verified, all three archetype profiles exercised on a live human.

2.  First repetition-detection numbers against MM-Fit's 616 sets.

3.  Decide: pass and continue, or optimise and re-test. Do not proceed to the remaining twelve profiles until the three archetypes hold up.

### Sources

- [MM-Fit paper (UbiComp)](https://vradu.uk/UbiComp2021.pdf) - subjects, sessions, repetitions, pose method, labels

- [MM-Fit project site](https://mmfit.github.io/) - exercise list, capture setup

- [MM-Fit video and depth on Zenodo](https://zenodo.org/records/7607736) - CC BY 4.0 licence, 40.8 GB

- [MM-Fit starter code](https://github.com/KDMStromback/mm-fit) - MIT

- [FLEX dataset](https://haoyin116.github.io/FLEX_Dataset/) and [arXiv 2506.03198](https://arxiv.org/html/2506.03198v4) - per-repetition error labels, non-commercial licence agreement

- [M3GYM](https://finalyou.github.io/M3GYM/) and its [CVPR 2025 paper](https://openaccess.thecvf.com/content/CVPR2025/papers/Xu_M3GYM_A_Large-Scale_Multimodal_Multi-view_Multi-person_Pose_Dataset_for_Fitness_CVPR_2025_paper.pdf) - expert quality assessments, access form required
