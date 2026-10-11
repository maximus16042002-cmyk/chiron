# Chiron: Camera-Based Personal Movement Analysis for Exercise

*Final report, draft of 2026-10-11. Bachelor of Computer Applications (BCA), final year.
Authors: Nitish, Dominic. Guide: [Mentor's name]. [College name].*

*Chapter 7, Evaluation and Results, is the separate file `docs/report/EVALUATION.md`. Figures are
traced in brackets to the project's decision record (`docs/DECISIONS.md`, entries such as
"P3-30"). The bracketed references can be removed for printing. The code base calls the app by
its working name, Kinesis.*

---

## Abstract

Chiron is an Android app that watches a person exercise through the phone's camera. It counts
their repetitions, times their holds, and measures their range of motion, speed, and left and
right sides separately. Everything runs on the phone, offline, from the camera alone.

**How it works.** A pose model (Google's MediaPipe Pose) finds 33 body landmarks in each frame.
A shared analysis engine written once in Kotlin Multiplatform turns them into joint angles,
filters them, and counts repetitions with a small state machine per side of the body.

**The research question:** *do personal baselines outperform population thresholds for flagging
movement deviation?*

**The evaluation used only public data and our own phone,** with the people used for testing
kept apart from those used for development. On six people the system had never seen it counted:
- 163 of 163 bicep curls;
- 146 of 150 lateral raises;
- 146 of 189 sit-ups.

No accepted change ever counted more repetitions than were performed.

On personalisation:
- people differ far more from each other than one person varies;
- a single population threshold's false alarms fall on particular people;
- personal baselines detect planted changes more often;
- but the personal "unusual for you" messages built on this failed their one test on unseen
  people, so they are not in the app.

We report that failure, and a negative result for a learned anomaly detector, as findings.

---

## 1. Introduction

### 1.1 What Chiron is

You stand the phone up, choose an exercise and move in front of it. Chiron:
- counts repetitions, or times a hold such as a plank;
- measures each repetition's range of motion and timing, for the left and right sides
  separately;
- shows a summary after each set, a calendar of training days, and a chart per exercise.

It supports **15 exercise profiles across 3 tiers and 2 analysis modes** (repetitions and holds),
presented as **17 library entries** that include 2 variants sharing a parent's profile.

### 1.2 Why we chose this project

Most fitness apps either count nothing or judge everyone against one fixed idea of correct
movement. People differ in build, flexibility and habit. We wanted a system that:
- measures movement honestly, from a phone camera only;
- compares a person with their own normal movement, not with an average;
- is open about what it cannot know.

### 1.3 What Chiron is not

- **Not a medical or diagnostic tool.** It measures movement; it does not diagnose, treat or
  predict injury.
- **Not a certified judge of technique.** Six rules whose thresholds come from published exercise
  guidelines mark a repetition red as it happens, and after each set the summary shows them as
  "Form notes". How often they agree with a coach was not measured, because no labelled data
  exists here, and the app says so on the card (section 4.7). A flagged repetition still counts.
- **Not a video recorder.** No video is stored or uploaded.

---

## 2. Objectives and Research Question

### 2.1 Objectives

1. Count repetitions and time holds reliably from a single phone camera, offline.
2. Keep left and right measurements separate everywhere.
3. Calibrate to the individual: learn each person's normal range and timing.
4. Evaluate on public data, with people never seen in development, and report every result,
   including failures.
5. Ship a complete Android app around the engine: history, calendar, charts, reminders and
   guided workouts.

### 2.2 Research question

*Do personal baselines outperform population thresholds for flagging movement deviation?* This
needs no labels of good or bad technique, only many repetitions from several people, which a
public dataset provides.

### 2.3 Scope and constraints

- **No self-collected or self-labelled training data.** All evaluation on other people uses the
  public MM-Fit dataset.
- **Offline and private.** All analysis runs on the phone. The only network use is optional sync
  of session records to the user's own account (Firebase), which the app discloses.
- **The cut:** a planned web version was cut early to protect the evaluation. The JavaScript
  build of the engine was kept, because it is used to prove the engine gives identical results on
  every platform.

---

## 3. How the App Works (the User's View)

The app has five tabs:
- **Home:** the training streak (with "freezes" that cover a missed day), the weekly goal ring,
  the last seven days, and "Repeat last workout".
- **Workouts:** a workout builder (time, focus, level, equipment) that lays out a plan, and guided
  workouts that walk through it: prepare, exercise, rest, next.
- **Train:** the exercise list. Each exercise has a short "Setting up" card saying where to put the
  phone.
- **Progress:** a calendar of training days, achievements, and each exercise's page with its range
  of motion over time, left and right as separate lines.
- **Profile:** the weekly goal, rest length, sound/vibration/spoken-count cues, reminders,
  calibration, data export, and the sign-in.

**During a set**, the live screen shows the camera with a skeleton drawn over it. Each joint is
coloured by how confidently it is seen: dim joints are ones the camera cannot see, often the far
limb when side-on, and the app does not measure from them. At the joint the exercise is counted
on, each side the app can measure gets an arc between its two bones and the angle in degrees. That
number is the filtered angle the count is decided on; left and right are shown separately, never
averaged. Each counted repetition makes the skeleton glow briefly, and a "+1" rises from the head.
An attempt that does not count turns the skeleton red, with the reason in large type for two
seconds: "Too fast — not counted" or "Not a full repetition — not counted" (section 4.4). A
counted repetition that a technique rule flags is red too, with the rule's reason, such as "Upper
arm moved forward". The screen also shows the count and the movement phase. If the camera view
does not suit the exercise, the screen advises how to stand.

**After a set**, the summary shows:
- the count, and each attempt marked counted, or with the reason it was not;
- each repetition's range of motion per side;
- the lifting speed per side, and how much it slowed through the set;
- with a calibration, each value beside the person's own usual one;
- "Form notes" for any of the six cited technique rules that fired: how many repetitions, the
  measured value, the guideline and its source [P3-74].

---

## 4. System Design

### 4.1 Overall architecture

```
 Camera (CameraX) ──► MediaPipe Pose ──► Pose adapter ──► Shared analysis engine ──► Storage ──► Screens
   phone only          33 landmarks      mirroring,        Kotlin Multiplatform:       Room
                                         units              angles, filtering,          (+ optional
                                                            rep counting, holds,         sync)
                                                            calibration, rules
```

**The key rule: all analysis lives in the shared engine.** The Android app only acquires frames,
draws, stores and displays. So the engine can be tested away from the phone, and the same code
runs on Android, the JVM and JavaScript.

### 4.2 Modules

| Module | Contents |
|---|---|
| `core/domain` | Data types only: `BilateralSignal` (left and right, never merged), landmarks, exercise profiles, repetition/hold/set/session records, findings with claim levels |
| `core/analysis` | The engine: joint angles, filtering, the repetition state machine, the hold analyser, the live real-time path, calibration, deviation and technique rules, export, history (streaks, records, trends), the workout generator |
| `app` | Android: camera, pose model, the live screen and skeleton overlay, Room storage, sync, Health Connect, reminders, all screens |
| `tools/mmfit-eval` | The evaluation harness: runs MM-Fit through the same engine the phone runs |

### 4.3 The real-time pipeline

For every frame, in constant time and memory (no work grows with the length of a set):
1. **Pose:** MediaPipe returns 33 landmarks with a visibility score each.
2. **Pose adapter:** converts camera coordinates to the body's own left and right. This undoes
   the front camera's mirror image. It also puts both axes in one unit, because a non-square
   frame otherwise distorts angles by up to about 16°.
3. **Validity:** a frame whose required landmarks are unseen is marked invalid; a side that is
   not seen is not measured.
4. **Joint angle per side,** for the exercise's driving joint (the elbow for a curl, the knee for
   a squat).
5. **Filtering per side:** an outlier gate rejects impossible jumps, a smoother removes jitter
   (weighted by elapsed time, so it behaves the same at 6 or 30 frames a second), and gaps up to
   0.5 s are bridged.
6. **State machine per side:** start → moving away → far position → returning → start.

### 4.4 How a repetition is counted

- A repetition counts only when a side completes the **whole cycle**, leaving the starting zone,
  reaching the far zone and returning.
- A movement that turns back early is recorded as **turned back** (`ABORTED`). One that loses
  sight of the joint is **insufficient data**. A set ended mid-repetition records it as
  **incomplete**. Only complete cycles count.
- **The exercise decides what one repetition is:**
  - for a bicep curl, each arm is a separate repetition, matching how MM-Fit labels curls
    [P3-1];
  - for a lateral raise, both arms together are one;
  - for side-on exercises where the far limb is usually hidden (push-up, sit-up, row, squat), the
    limb nearer the camera counts, and the same repetition seen by both sides counts once
    [P3-28, P3-68].

**A verdict on each attempt [P3-78].** The state machine above measures; a verdict decides what
the person is shown. It is computed from the stored record whenever it is needed, so the live
count, the summary and the history always agree:
- **counted;**
- **too fast**, faster than any repetition MM-Fit's development people made, e.g. a curl under
  0.63 s: red, not counted;
- **not a full repetition**, turned back past halfway: red, not counted;
- **lost tracking**, including a knee angle under 30°, which no squat or lunge reaches and which
  MediaPipe produced when it lost the legs: not counted, without blaming the person;
- **a technique rule fired:** counted, and red with the rule's reason;
- a wobble that never got halfway, or the far limb seeing a repetition already counted: nothing
  said.

On MM-Fit's development sets the verdict counts what the state machine counts, to within one lunge
out of the 2,518 it counted. On our own session it removed the phantom repetitions and the
"ultra fast" curls the tester reported.

### 4.5 After a set: records and storage

Each completed repetition becomes a `RepRecord`, holding per side:
- its range of motion and its extreme angles;
- its lowering and lifting times;
- a 64-point curve of the angle over the repetition.

Holds become a `HoldRecord` (time held, breaks, sway). Records are **measurements**.
Interpretations (comparisons, findings) are computed from them when needed and not stored, so old
data can be re-analysed when rules change.

Storage is a local Room database, **one per signed-in account** [P3-60]. A set is written when it
ends. A draft is kept after every repetition, so a set survives the app being killed [P3-66].
Sync to the account is optional and runs after the local save, never instead of it. The history
exports to JSON and CSV.

### 4.6 Calibration

The user performs one normal set of an exercise. The engine stores, per side, the median and
spread (interquartile range) of range of motion and timing, from 5–8 valid repetitions. Later
sets are shown beside these personal values. The evaluation found that 5–8 repetitions are too
few to settle range of motion (chapter 7, S4), so the count is an open decision.

### 4.7 Analysis layers and claim levels

Every statement the engine can make carries a claim level:
- **Level 1, measurement:** count, range, speed, symmetry.
- **Level 2, personal deviation:** "unusual for you".
- **Level 3, technique judgement:** for example "hips sagged".

| Layer | What it does | Status |
|---|---|---|
| 1. Deviation rules | Compare a repetition with the person's own baseline (Level 2) | Built; **failed its held-out test, not in the app** |
| 2. Technique rules | Six rules with thresholds cited from published sources, for Tier A exercises only (Level 3) | **Shown live, in red, on the repetition they flag, and once per set as "Form notes"** with each rule's source [P3-74, P3-78]; the repetition still counts; their accuracy is unmeasured, which the card states |
| 3. Supervised quality classifiers | — | Removed from scope: no labelled data |
| 4. Personal anomaly detection | An Isolation Forest fitted to the person's own history | Built and studied; **negative result, not in the app** |

**Tiers are enforced by the type system.** Only Tier A profiles hold the authority object needed
to create a Level 3 finding, so a Tier B or C exercise *cannot* emit one. A permanent test proves
it.

### 4.8 Design rules we followed

- **Left and right are never averaged.** The `BilateralSignal` type keeps them apart, and a guard
  test fails if any code collapses them without a named, explicit rule.
- **The real-time path is constant time.** Guard tests check that per-frame work and memory do not
  grow over a set.
- **Measure, test, document, then claim.** Every number in this report was measured on this
  system, and each design decision is recorded with its reasons before the code (about 70
  numbered entries).

---

## 5. Technologies Used

| Area | Technology |
|---|---|
| Language | Kotlin 2.1, Kotlin Multiplatform (Android, JVM, JavaScript targets) |
| Android UI | Jetpack Compose, Material 3 |
| Camera | CameraX 1.4.1 |
| Pose estimation | MediaPipe Tasks Vision 0.10.21 (Pose Landmarker, lite model), on the phone |
| Storage | Room 2.7.1 (SQLite) |
| Background work | WorkManager 2.10.0 (sync, reminders) |
| Account and sync | Firebase Authentication and Cloud Firestore (session records only) |
| Health context | Health Connect 1.1.0 (read-only steps, calories, heart rate, shown beside training) |
| Build | Gradle 9.3, Android Gradle Plugin 8.9.1, compileSdk 36, minimum Android 7 (API 24) |
| Evaluation | Python with NumPy, OpenCV and MediaPipe; scikit-learn for the verifier study |
| Dataset | MM-Fit (Strömbäck, Huang and Radu, 2020), CC BY 4.0 |

Every dependency was checked before adding it: why it is needed, its licence, its size, and
whether it works offline. Analytics, crash reporting and telemetry are excluded on purpose.

---

## 6. Implementation: Work Completed

### 6.1 Development approach

The project went through phases P0–P12; P11, the web app, was cut. Within each piece of work:
1. a design entry was written first;
2. tests were written next and seen failing;
3. the code followed;
4. "mutation checks" deliberately broke the new code to prove the tests catch it.

The automated suite stands at **3,167 test executions, all passing**, across the JVM, JavaScript
and two Android build types.

### 6.2 Phases

| Phase | Delivered |
|---|---|
| P0 Architecture | Documents, type contracts, claim levels, module boundaries |
| P1 Shared core | Domain types, `BilateralSignal`, the pose adapter, the JavaScript facade |
| P2 Kinematics | Joint angles, the filter chain, ring buffers, resampling |
| P3 Profiles and engine | The repetition state machine, the hold analyser, 15 profiles and 2 variants |
| P4 Phone testing | Partly done: phone sessions in September and October (chapter 7, section 6) |
| P5 Records and export | Storage, migrations, JSON/CSV export identical on every platform |
| P6 Calibration | Built; recalibration of the curl side-on owed |
| P7 MM-Fit pipeline | The video-to-engine harness over all 616 MM-Fit sets |
| P8 Form engine | Layers 1 and 2 built; neither shown (section 4.7) |
| P9 Machine learning | The exercise verifier and Layer 4, both built and evaluated, neither shipped |
| P10 The app | Five tabs, onboarding, streaks, calendar, records, goals, reminders, rest timer, cues, the workout generator, guided workouts, trend charts |
| P12 Evaluation | Chapter 7; the phone studies are still owed |

---

## 7. Evaluation and Results

See `docs/report/EVALUATION.md`.

---

## 8. Problems We Faced and How We Solved Them

1. **One curl, one arm or two?** MM-Fit's labels count each arm separately. Counting a two-armed
   curl as one would have made every curl result wrong by a factor of two, and it would have
   looked like a tuning problem. We read the labels and fixed one arm = one repetition, pinned by
   a test [P3-1].
2. **The front camera is a mirror.** A raised left arm appears on the image's left, where the
   right arm would be. The pose adapter converts every frame to the body's own sides before the
   engine sees it, and a regression test guards it [P1-5].
3. **Angles distorted by the frame's shape.** MediaPipe measures x in frame widths and y in frame
   heights, which skews angles on a portrait frame by up to about 16°. Both axes are now in one
   unit [P3-15, P3-16].
4. **Fast repetitions were lost.** The first MM-Fit run counted only 279 of 599 curls, because a
   repetition that started fast left the starting zone in a single frame. After the fix: 163 of
   163 on unseen people [P3-27, P3-28].
5. **The far side is hidden side-on.** Push-ups, sit-ups and later squats were missed because the
   far limb is behind the body. These exercises now count from the nearer limb [P3-28, P3-68].
6. **Curls filmed facing the phone counted arm swings.** On the phone, a set of 15 curls counted
   22, and a set with no curls counted 5. The curl is now filmed side-on [P3-31, P3-32].
7. **A throttled phone.** At high temperature MediaPipe dropped to about 6 results a second, and
   the smoother, tuned for 30, lagged. It is now weighted by elapsed time [P3-33].
8. **A test split that leaked.** Our first split was by workout, which put two development
   people into the "unseen" set. We grouped workouts by person, checked the grouping against the
   MM-Fit paper, and re-read the results on truly unseen people [P3-30].
9. **A camera-view check that misjudged the real setup.** Built to stop counting from a wrong
   view, it misjudged our own side-on stance during the demonstration and stopped all counting.
   It now advises without blocking until it has been measured on the phone [P3-68].
10. **Work by another AI agent that looked finished but was not.** An agent session added several
    features overnight. Our review found that they passed every test, yet:
    - a setup check blocked nothing;
    - a zoom moved only the skeleton;
    - accuracy figures hid 100% false-warning rates for some people.

    The tests' own inputs shared the mistakes. We corrected the work and recorded the lesson
    [P3-59].
11. **Empty sets in real use.** Our own training data showed half of all saved sets were empty,
    split off when the camera was switched. Sets with nothing in them are no longer saved [P3-70].
12. **"It counted things I didn't do."** A session on the phone counted three lunges that were
    MediaPipe losing the legs, and curls done in half a second. We added a verdict on each attempt
    (section 4.4). Its first design was wrong in three ways, and measuring it on MM-Fit before it
    shipped showed each one:
    - a 1.5 s window meant to merge one lunge seen by both knees would have deleted 95 real
      alternating lunges;
    - "too fast" limits taken from set averages rejected 43 real lunges;
    - cancelling repetitions for a technique fault would have cancelled 329 of 387 normal lateral
      raises.

    The window was dropped, the limits were re-measured from real repetitions, and the owner chose
    to count flagged repetitions and show them red [P3-78].

---

## 9. Conclusion and Future Work

### 9.1 Conclusion

Chiron counts repetitions reliably from a phone camera, in the conditions it was tested in, on
people it had never seen. It keeps left and right apart, runs entirely on the phone, and is
backed by a large automated test suite.

**On the research question:** personal baselines distribute false alarms more fairly and detect
within-person change better than a population threshold. But a working product built on them
needs more calibration data than one short set: the personal messages failed their held-out test
for exactly that reason.

### 9.2 Future work

1. **Phone studies:** sustained frame rate, latency, memory and heat over 15 minutes; joint
   visibility per exercise and view; a second person.
2. **Longer calibration** (12–20 repetitions, or across sessions), then a new held-out test of the
   personal messages on new people.
3. **The camera-view check:** measure it on the phone, then let it block counting from a wrong
   view again.
4. **Technique rules:** check each against good repetitions filmed side-on on the phone; an
   expert-labelled dataset would be needed before any could be given a stated accuracy.

### 9.3 Limitations

See chapter 7, section 9, which lists every limitation in plain terms.

---

## 10. Use of AI Tools

*[To be written by the authors, in their own words: which AI tools were used, for what, and what
the authors designed, decided, tested and verified themselves.]*

---

## References

1. Strömbäck, D., Huang, S. and Radu, V. (2020). MM-Fit: Multimodal deep learning for automatic
   exercise logging across sensing devices. *Proc. ACM Interactive, Mobile, Wearable and
   Ubiquitous Technologies*, 4(4).
2. Google. MediaPipe Pose Landmarker. https://developers.google.com/mediapipe
3. American Council on Exercise. ACE Exercise Library: Lateral Raise, Hammer Curl, Push-Ups,
   Bodyweight Squat. acefitness.org/resources/everyone/exercise-library (read 2026-10-01).
4. Smith, A. Teaching for Skill Acquisition in Fitness: Best Practices for Fitness Pedagogy.
   *NSCA Coach*, 7(4), Table 4.
5. Cook, G., Burton, L. and Hoogenboom, B. (2006). *North American Journal of Sports Physical
   Therapy*, 1(3), 132–139 (the trunk stability push-up).
6. Liu, F. T., Ting, K. M. and Zhou, Z.-H. (2008). Isolation Forest. *Proc. IEEE International
   Conference on Data Mining*.
