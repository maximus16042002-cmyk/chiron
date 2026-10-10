# Evaluation and Results (draft chapter for the final report)

*Draft, updated 2026-10-11. Written for the final report's examiners. Every figure below was measured on
this system and is traced in brackets to the project's decision record (`docs/DECISIONS.md`,
entries such as "P3-30"). Remove the brackets for the printed report if your college prefers.
"Chiron" is the app's name in the report; the codebase calls it Kinesis.*

---

## 1. How we evaluated Chiron

### 1.1 Principles

- **No self-collected or self-labelled data.** We did not film or label anyone. All evaluation on
  people other than ourselves uses a public dataset, MM-Fit, and the rest uses our own phone
  live, without storing video.
- **The protocol comes before the numbers.** For every study, the method, the data split and the
  pass criteria were written down and committed to version control before anything was computed.
  The commits are dated, so a reader can check that no threshold was chosen after seeing the
  result.
- **People, not workouts, are the unit.** A system that has seen a person before has an advantage
  on that person. We kept the people used for development apart from the people used for
  testing.
- **A held-out test is run once.** The held-out people were used only when the owner of the
  project approved it, and each test was run once with its pass rule fixed beforehand. When a test
  failed, we did not tune and retry on the same people.
- **What we never claim.** There are no labels of good or bad technique anywhere in our data.
  So this chapter contains no accuracy, precision or recall figure for technique or form.

### 1.2 The six studies

The project plan defines six studies, and every number in this chapter comes from one of them:

| Study | Data | Status |
|---|---|---|
| 1. Repetition counting | MM-Fit | Done (section 3) |
| 2. Exercise verifier | MM-Fit | Done on development people; held-out run not spent (section 4) |
| 3. Personalisation | MM-Fit | Done on development people; one held-out test of the resulting messages (section 5) |
| 4. Device performance | Our phone, live | Preliminary observations only (section 6) |
| 5. Pose reliability on the device | Our phone, live | Preliminary observations only (section 6) |
| 6. Cross-platform parity | Synthetic test inputs | Done (section 7) |

---

## 2. The data

### 2.1 MM-Fit

MM-Fit (Strömbäck, Huang and Radu, *Proc. ACM IMWUT* 4(4), 2020) is a public dataset released
under CC BY 4.0. It contains:
- 10 people performing 10 exercises in 21 workouts;
- 616 labelled sets and 6,160 repetitions;
- video from a fixed camera at 30 frames a second;
- labels for each set's start, end and repetition count.

It has **no labels for technique quality.**

We ran the same pose model the phone uses (MediaPipe Pose, lite) over MM-Fit's video. We then
passed the landmarks through the app's own live analysis code, unchanged, in a test harness. So
the evaluated code is the code on the phone; only the camera differs. [P3-27]

### 2.2 Who is who: a split by person

MM-Fit does not publish which person performed which workout. We grouped the 21 workouts by
person by eye, from cropped stills (build, hair, recurring clothing). We checked the grouping
against every fact the MM-Fit paper states:
- two people did six workouts each, one did two, and seven did one;
- the paper's cross-subject test workouts share no person with its training workouts.

The grouping matches all of them. [P3-30]

This mattered. Our first split was by workout, and it put 7 of 13 "held-out" workouts from two
people who were also in development. We caught this before running the personalisation study,
replaced the split, and re-read the earlier counting results on the truly unseen people. A test
in the code base now fails if any person appears on both sides. [P3-30]

| Set | People | Workouts |
|---|---|---|
| Development | 4 (A, B, C, S19) | 15: w00–w11, w14, w15, w19 |
| Held-out | 6 (S12, S13, S16, S17, S18, S20) | 6: w12, w13, w16, w17, w18, w20 |

**Four development people is a small number.** Every development figure below is reported per
person as well as pooled, and no pooled interval from four people is presented as precise.

### 2.3 Camera view

MM-Fit films each exercise from one fixed position. For four exercises, the view differs from the
one the app asks the user to set up: the bicep curl and squat are filmed facing the camera, and
the lunge and row at an angle, while the app requires all four side-on. [P3-34] **Results on those
four do not describe the app's camera setup, and we do not quote them as if they did.** The
push-up and sit-up (side-on) and the seated lateral raise and shoulder press (front-on) match the
app's views.

---

## 3. Study 1: repetition counting

### 3.1 Method

For each labelled set, the app's engine counts valid repetitions, and the count is compared with
MM-Fit's label. Metrics per exercise: repetitions counted out of those labelled, the share of sets
counted exactly, the share within ±1, and the mean absolute error per set (MAE). One rule held
throughout development: **no change was accepted if it made any set count above its label**,
because counting movements that did not happen is the worse error for a user. [P3-27 to P3-29]

### 3.2 The first measurement, before any fixes

All 21 workouts, the engine as first built [P3-27]:

| Exercise | Counted of labelled |
|---|---|
| Shoulder press | 569 of 598 (80% of sets exact) |
| Bicep curl | 279 of 599 |
| Lateral raise | 208 of 559 |
| Sit-up | 101 of 640 |
| Push-up | 5 of 649 |

This run found real engine defects:
- a repetition that started fast was lost (54% of the curls that were visible in the signal);
- side-on push-ups and sit-ups were refused because the far side of the body is hidden;
- the lateral raise's starting position was set too strict.

Each was fixed in the shared engine. [P3-28, P3-29]

### 3.3 Results on the six people never used in development

These are the figures to cite. [P3-30, re-slice; P3-46]

| Exercise (fix) | Sets | Counted of labelled | Sets exact | Within ±1 | MAE | Sets over label |
|---|---|---|---|---|---|---|
| Bicep curl (fast-start fix) | 16 | **163 of 163** | 100% | 100% | 0 | 0 |
| Lateral raise (start zone ≤ 36°) | 15 | **146 of 150** | 80.0% | 93.3% | 0.27 | 0 |
| Push-up (near-side counting) | 20 | 110 of 201 | 25.0% | — | 4.55 | 0 |
| Sit-up (near-side counting, hip angle) | 20 | 108 of 189 | 15.0% | — | 4.05 | 0 |
| Sit-up (trunk-angle driver, shipped) | 20 | **146 of 189** | — | — | — | 0 |

*A dash means the figure was not computed for that run.*

**Per person** (counted of labelled) [P3-30]:

| | S12 | S13 | S16 | S17 | S18 | S20 |
|---|---|---|---|---|---|---|
| Bicep curl | 10/10 | 32/32 | 30/30 | 31/31 | 30/30 | 30/30 |
| Lateral raise | — | 30/30 | 30/30 | 29/30 | 27/30 | 30/30 |
| Push-up | 0/30 | 22/30 | 28/40 | 3/41 | 28/30 | 29/30 |
| Sit-up (hip angle) | 26/30 | 27/31 | 0/36 | 26/32 | 29/30 | 0/30 |
| Sit-up (trunk angle) | 27/30 | 28/31 | 31/36 | 31/32 | 29/30 | 0/30 |

S12 has no lateral raise set in MM-Fit.

### 3.4 What the results show

- **Curls and lateral raises count reliably on new people** in MM-Fit's setup: every curl set
  exact, and 146 of 150 lateral raises with no set over-counted.
- **Push-ups remain weak:** two people's push-ups are barely counted (S12 0 of 30, S17 3 of 41).
  The near-side counting rule's earlier gain came entirely from development people; on the unseen
  people it changed nothing (110 before and after). The remaining problem is the starting-position
  check, not the counting. [P3-30]
- **The sit-up's trunk-angle driver** was tested once on the held-out people with a pass rule
  fixed in advance: every person must count at least as many as before, and the total must rise.
  It passed, going from 108 to 146 of 189 (57.1% to 77.2%). On the development people it reached
  91.6%, but that figure was fitted. S20's sit-ups count with neither method: the trunk barely
  lifted. [P3-45, P3-46]
- **Nothing was ever counted above its label** in any accepted change.

### 3.5 What is not measured here

- **Squat, lunge and row:** filmed from a view the app does not use, so not reported.
- **Shoulder press:** not re-measured on the unseen people. Its 569 of 598 above covers all
  workouts, development included.
- **Repetition boundaries:** MM-Fit labels each set's count, not the start and end of each
  repetition, so we make no claim about how precisely a repetition's start and end are found.

---

## 4. Study 2: the exercise verifier

### 4.1 What it is

The verifier is meant to warn when a movement does not look like the exercise the user selected
("this looks like a different exercise"). It never changes the selection. [P3-34]
- **Input:** each completed repetition is summarised as 145 numbers: nine joint angles (elbows,
  shoulders, hips and knees on each side, and the trunk's lean), each at 16 points through the
  repetition, plus the repetition's duration.
- **Model:** multinomial logistic regression over MM-Fit's 10 exercise classes, trained in Python
  (scikit-learn) and re-implemented in Kotlin for the phone.
- **Training data:** every development set run through every candidate profile, giving 27,783
  repetition summaries. [P3-52]

### 4.2 Method

Leave-one-person-out cross-validation over the four development people. The model is trained on
three people and tested on the fourth, four times. A warning is raised when the selected
exercise's probability is below 0.25 and another exercise's is at least 0.50. The held-out
people were not used. [P3-52]

### 4.3 Results

- **Accuracy, mean over the four folds: 74.2%,** ranging from 56.6% to 89.2% depending on who is
  left out (A 83.8%, B 89.2%, C 67.2%, S19 56.6%). Pooled over all repetitions it is 82.1%. That
  figure weights the two people with the most data, so we report the mean. [P3-52]
- **Kotlin matches Python** to within 1e-9 on every class probability, on 25 test vectors. [P3-52]
- **Warnings, by the exercise the user selected** [P3-59]:

| Selected | Correct reps | Warned in error | Wrong-exercise reps | Warned |
|---|---|---|---|---|
| Bicep curl | 436 | 0.2% | 4,478 | 98.2% |
| Lateral raise | 405 | 14.6% | 2,089 | 98.4% |
| Lunge | 1,128 | 0.9% | 1,784 | 90.9% |
| Push-up | 316 | 1.9% | 4,002 | 99.4% |
| Row | 760 | 12.1% | 3,549 | 94.1% |
| Shoulder press | 448 | 2.2% | 895 | 81.1% |
| Sit-up | 480 | 6.7% | 164 | 100.0% |
| Squat | 452 | **43.1%** | 1,987 | 95.7% |

- **Per person, the false warnings are far less even.** On correct repetitions, the left-out
  person was warned in error:
  - for the squat, 75% of the time (A) and 100% (S19);
  - for the lateral raise, 98% (C);
  - for the sit-up, 100% (S19);
  - for the row, 86% (S19).

  [P3-59]

### 4.4 Decision

Every left-out person had at least one exercise on which most of their correct repetitions were
warned. The verifier's warning would therefore mislead a new user often, so **it is not shipped
in the app.** [P3-59]
- It is limited in the code to the four exercises MM-Fit films from the app's view.
- Its single held-out run has not been spent. It should only be run once a shipping rule (for
  example, a ceiling on any one person's false-warning rate) has been fixed in advance.

---

## 5. Study 3: personalisation

**The research question:** *do personal baselines outperform population thresholds for flagging
movement deviation?*

**The key assumption.** MM-Fit's repetitions are ordinary workout repetitions, so a flag on one is
treated as a false alarm. The data are not certified as clean, though:
- people tire within a set;
- MM-Fit notes a few miscounted sets;
- people chose their own weights.

**So every flag rate below is an upper bound on false alarms, not a measured false-alarm rate.**
[P3-30]

**Data.** Development people only (A, B, C, S19). The shoulder press, bicep curl and lateral
raise, whose views match. Push-up and sit-up sessions mostly could not calibrate (4 of 15 and 7 of
15), and are not interpreted. [P3-30]

**The two comparators:**
- **Personal:** a baseline from the first set of the session (5–8 valid repetitions per side, the
  app's calibration).
- **Population:** the other three people's repetitions.

### 5.1 S1: how much people differ, against how much one person varies

A nested model (person → session → repetition) gives the intraclass correlation for the person
(ICC). We estimated it by the ANOVA method, with 95% intervals from 2,000 bootstrap resamples of
the people. [P3-30]

| Exercise | Measure | ICC (95% interval) | SD between people / sessions / repetitions |
|---|---|---|---|
| Lateral raise | range of motion, left | 0.95 (0.00–0.97) | 23.3° / 3.1° / 4.1° |
| Lateral raise | range of motion, right | 0.95 (0.00–0.97) | 21.0° / 3.0° / 3.7° |
| Lateral raise | lifting time | 0.79 (0.00–0.83) | 117 / 46 / 40 ms |
| Shoulder press | range of motion, left | 0.54 (0.00–0.73) | 7.9° / 3.8° / 6.3° |
| Shoulder press | lifting time | 0.75 (0.00–0.88) | 82 / 21 / 43 ms |
| Bicep curl | range of motion, right | 0.88 (0.00–0.93) | 17.8° / 3.9° / 5.3° |

- **The clearest case is the lateral raise's range of motion.** The median range was 116° for A,
  92° for B, 63° for C and 87° for S19: people differ by about 21–23°, while one person's
  repetitions vary by about 4°.
- **With only four people, no ICC interval excludes zero.** The point estimates are suggestive,
  not established.

### 5.2 S2: who receives the false alarms

Both comparators were set to the same average flag rate (5% over people), and then we compared
how the flags fall across people. [P3-30]
- **Across 16 measures, the worst-off person's rate was higher under the population comparator in
  10, under the personal one in 3, and tied in 3.**
  - The population comparator's flags land on whoever differs from the others: for example, C's
    shoulder-press range 18.9% while the others had 0%.
- **At the common rule of 2 standard deviations,** the population comparator flagged most
  repetitions of some people and none of others: lateral raise range (right) A 82.8%, C 74.4%,
  B 0.0%. The personal comparator gave A 9.4%, B 4.4%, C 23.1%.
- **But the personal rule at 2 SD flags too much for an app:** 14–32% of range-of-motion
  repetitions, with a 5–8-repetition baseline.

### 5.3 S3: sensitivity to planted changes (synthetic)

We planted changes arithmetically in recorded repetitions (never in calibration ones) and counted
how often each comparator flagged them, at the S2 operating point. This is arithmetic on records,
not movements anyone made. [P3-30]

| Planted change | Shoulder press, personal / population | Bicep curl | Lateral raise |
|---|---|---|---|
| Range cut 20% | 66.6% / 49.8% | 94.5% / 25.2% | 99.7% / 31.6% |
| Range cut 30% | 85.9% / 65.4% | 100% / 45.5% | 100% / 33.3% |
| Tempo 2× faster | 84.8% / 0.0% | 27.0% / 0.0% | 65.8% / 4.3% |
| One side −20% (asymmetry) | 93.1% / 15.9% | — | 83.1% / 88.3% |

At the same average false-alarm rate, the personal comparator detected the planted change as
often or more often in every case except the lateral raise's asymmetry, where the two are close.

### 5.4 S4: how many calibration repetitions are enough

"Settled" was defined before the run: for at least 80% of starting points, the baseline's median
is within half a spread of the session's own, and its spread within a factor of 1.5. [P3-30]
- **Timing settles quickly,** typically after 3 repetitions.
- **Range of motion settles late:** up to 16 repetitions for the curl, and more than 20 for the
  shoulder press and lateral raise.
- **The app's 5–8 calibration repetitions do not settle a range-of-motion baseline for these
  people; about 12–20 would.**

### 5.5 S5: does one session's baseline describe the person's other sessions?

For A and B, who have six sessions each, each session's baseline was compared with their other
sessions. The table shows the share of session pairs whose centre moved by more than two of the
baseline's own spreads. [P3-40]

| Measure | Pairs | Across sessions | Within one session |
|---|---|---|---|
| Range of motion | 340 | 31% | 15% |
| Left − right difference | 110 | 35% | 4% |
| Lifting time | 230 | 6% | 0% |
| Lowering time | 230 | 3% | 4% |

**A baseline made on one day often misdescribes the same person on another day,** except for
tempo.

### 5.6 The held-out test of personal messages (Layer 1)

From S1–S4 we chose a setting for personal "unusual for you" messages on the development people
(threshold 3 spreads, minimum floors). We then tested it once on the six held-out people. The pass
rule was fixed beforehand. [P3-41, P3-42]

**Pass rule:**
- every message's false alarms average at most 5% over people, with no person above 10%;
- a planted 30% range cut is detected at least 80% of the time.

**Result: FAIL.**
- Sensitivity passed (98.7% on the shoulder press, 99.0% on the lateral raise).
- False alarms failed on 3 of 15 messages:
  - lateral raise range, left: mean 11.0%, with one person at 55.0%;
  - shoulder press left − right: one person at 14.3%;
  - shoulder press range, right: one person at 10.5%.
- **The worst case is explained by S5:** that person's first set was shallower than the rest
  (calibration median 88.6°, later sets 100.9°).

As fixed in advance, **the personal messages stay out of the app.** There was no second attempt on
these people. The app shows measurements only. [P3-42]

### 5.7 Personal anomaly detection (Layer 4): a negative result

We tested whether a one-class model fitted to a person's own history (an Isolation Forest) finds
unusual repetitions better than simple statistics. We compared it with three deterministic
detectors at equal false-alarm rates, on planted changes, on the development people. [P3-37]
- **On the features specified, the forest detected stalls only** (40.1% for a 25% stall). Range,
  tempo and one-sided changes were flagged at 4–6%, its false-alarm rate.
- **The simple statistics matched it on stalls** (43.6–47.5%) and far exceeded it on range and
  one-sided changes (62–98% against 16–24%).
- **On the phone, the rule would flag 7–30% of normal repetitions** when a person's history holds
  10–40 repetitions.

**So Layer 4 does not ship,** and we report it as a negative result. [P3-38]

### 5.8 What the personalisation study does and does not show

- **It shows,** on four development people:
  - people differ a lot in some measures, most in the lateral raise's range;
  - a population threshold's false alarms fall on particular people;
  - personal baselines detect planted within-person changes more often;
  - 5–8 calibration repetitions are too few for range of motion;
  - one session's baseline often does not carry over to another day.
- **It does not show:**
  - that personalisation improves the detection of real faults. What was measured is flags on
    ordinary repetitions and on planted arithmetic changes;
  - that any of this holds beyond these four people, MM-Fit's camera, or 30 frames a second.
- **Our messages built on it failed their held-out test.**

---

## 6. Studies 4 and 5: device performance and pose reliability

**These two studies have not yet been run as planned** (a sustained session with a 15-minute
thermal run; landmark visibility per exercise and view). What exists are observations from
earlier phone sessions: one person, one phone (Nothing Phone 2a, Android 16), mostly the front
camera. We report them as preliminary.

- **Solo check (2026-09-28):** 15 of 15 curls counted. The median pose rate was 15.5 and 14.4
  results a second in the two curl sets, with single results as slow as 485 ms. [P3-24]
- **Phone session (2026-09-29):** [P3-31]
  - **Side-on curls:** right arm 16 of 16, including six fast curls, at about 15 results a second.
  - **Left arm: 15 of 15, but not cleanly.** MediaPipe was throttled to about 6 results a second
    while the camera delivered about 28 frames a second. The records did not map one-to-one onto
    the curls, so the matching total is partly coincidence.
  - **Heat:** the phone spent 23 of 50 logged minutes at thermal status 3 ("severe").
  - **Facing the camera,** curls counted arm swings: one set of 15 curls counted 22, and a set
    with no curls at all counted 5.
- **What changed because of it:**
  - the curl is now filmed side-on [P3-32];
  - the smoother adjusts for the result rate, so curls survive throttling [P3-33];
  - the app now pauses counting when the camera view is wrong [P3-55, P3-59].

  None of these changes has yet been checked in a formal phone session.
- **Real use, 2026-09-25 to 2026-10-08 (103 sets of our own training)** [P3-70]. Nobody counted
  alongside, so these show what the app recorded, not whether it was right:
  - **53 sets were empty.** Most came 1–3 s before a real set of the same exercise, consistent
    with switching camera at the start. They held no measurement, and the app no longer saves
    them.
  - **Squats side-on:** before the near-knee change, 6 of 51 records counted, most lost because
    the far knee was hidden. On the first set after it, 5 full-range squats counted.
  - **The lunge** lost most repetitions side-on (1 of 23 counted) for the same reason.
  - **Many curl and push-up attempts ended "turned back",** with small ranges: partial
    repetitions, which the app records but does not count.

---

## 7. Study 6: cross-platform parity

The analysis engine is written once in Kotlin Multiplatform and compiled for the JVM, for
JavaScript and for Android. [CLAUDE.md §1]

**Method.** Fixed synthetic scenarios (a squat set and a plank) run through the engine's facade on
each platform. The outputs are compared with a stored golden file:
- discrete outputs (counts, phases, validity) must match exactly;
- continuous ones must match to within 1e-9 relative.

**The test's own check.** A deliberate change of 1e-8 relative to one value must make it fail,
and it does. The data export, a generated workout plan and the streak rules are also pinned
identically on the JVM and JavaScript. [P3-19]

**Result:** identical discrete outputs and continuous agreement within 1e-9 on every target. In
the latest full run, 3,167 test executions passed on all targets (JVM, JavaScript, and Android
debug and release), with none failing. [P3-59]

---

## 8. Summary of findings

| Question | Answer, with its evidence |
|---|---|
| Does Chiron count repetitions correctly on new people? | Curls and lateral raises: yes, in MM-Fit's setup (163/163 and 146/150, none over-counted). Sit-ups: 146/189 after the trunk-angle change. Push-ups: not yet (110/201). |
| Can it tell which exercise is being done? | On development people, 74% mean accuracy, but with false warnings up to 100% for some people on some exercises. Not shipped. |
| Do personal baselines beat population thresholds? | On four development people, a population threshold's false alarms concentrate on particular people, and personal baselines detect planted changes more often. But personal messages built on this failed their held-out test, and 5–8 calibration repetitions are too few. |
| Does a learned personal anomaly detector help? | No. Simple statistics did as well or better. |
| Is the engine the same on every platform? | Yes, to within 1e-9, with discrete outputs identical. |
| Does it run well on a phone? | Preliminary only: side-on curls counted at about 15 results a second, less cleanly when throttled to 6, and the phone runs hot. The planned device study is still owed. |

---

## 9. Limitations

We state these plainly, as the project plan requires.

1. **No technique accuracy figure exists.** Chiron's technique rules implement thresholds from
   cited sources (the ACE exercise library, the NSCA, and Cook, Burton and Hoogenboom 2006). Their
   agreement with expert human judgement was not measured in this work. They are shown after each
   set as "Form notes", with that statement on the card, and they have not yet been checked
   against good repetitions filmed side-on on the phone [P3-74].
2. **Personalisation is not shown to improve fault detection.** What was measured is flag rates on
   ordinary repetitions (an upper bound on false alarms) and detection of planted arithmetic
   changes.
3. **Four development people.** The personalisation and verifier studies rest on four people, and
   no ICC interval excludes zero.
4. **The held-out people have been used.** The six held-out people were used for the counting
   re-slice, the sit-up driver test and the Layer 1 test. Further tuning on them would no longer be
   a fair test.
5. **The person grouping was made by eye.** It fits every count the MM-Fit paper gives, but it is
   a judgement.
6. **One camera, one frame rate.** MM-Fit results describe its fixed camera at 30 frames a second
   and its views. They are not evidence for other views, distances or frame rates. For four
   exercises the view differs from the app's (section 2.3).
7. **Seated versions only.** The lateral raise and shoulder press results are for MM-Fit's seated
   versions, not standing ones.
8. **No repetition-boundary precision.** MM-Fit labels counts, not repetition start and end times.
9. **Device evidence is preliminary:** one person, one phone, mostly the front camera, often hot.
   The planned performance and reliability studies remain to be run, and the newest app version
   has not yet been run on the phone.
10. **Layer 1's held-out failure and Layer 4's null result** are part of the findings, not
    omitted.
11. **Automated tests are synthetic.** None of the 3,167 test executions uses a camera frame. A
    review of our own code found three defects that had passed every test, because each test's
    synthetic input shared the code's mistake [P3-59]. The phone remains the final check.

---

## 10. What remains

- **Studies 4 and 5 on the phone:** sustained frame rate, latency, memory and the thermal point
  over 15 minutes; landmark visibility per exercise and camera view; the failure modes observed.
- **A second person** on the phone, so the device evidence is not one person's.
- **Optional, and only with the protocol fixed first:** the verifier's single held-out run.
