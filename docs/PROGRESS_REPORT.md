# Kinesis — What Is Finished, Partly Done and Not Started

*Written 2026-09-29, from the repository at the end of DECISIONS.md P3-30. A plain summary for the
project owner. It is not an authority: `docs/DECISIONS.md` is, and `docs/PROJECT_STATUS.md` has the
fuller detail.*

---

## At a glance

The build plan has 13 phases, P0–P12. One (P11) was cut, so **12 are in scope**.

| Status | Phases | Count |
|---|---|---|
| ✅ Finished | P0, P1, P2, P3, P5, P7 | 6 |
| 🟡 Partly done | P4, P6, P8, P10, P12 | 5 |
| ⛔ Not started | P9 | 1 |
| ✂️ Cut | P11 | — |

**Rough overall figure:** a finished phase counts 1 and a partly done one ½, so
6 + 5 × ½ = **8.5 of 12**.

That is an estimate, not a measurement. The phases are not the same size, and most of what remains
is the heaviest academic work: technique rules, the evaluation studies and the write-up. **The
engineering is well past half; the thesis is short of it.**

---

## ✅ Finished

| Phase | What it delivered |
|---|---|
| **P0 Architecture lock** | The documents, contracts and module structure everything else follows. |
| **P1 Shared core** | Domain types, left/right kept separate by type (`BilateralSignal`), claim levels, the landmark model, the pose adapter, and the JavaScript facade. |
| **P2 Kinematics & temporal** | Joint angles, velocities, the real-time filter chain (outlier gate → smoother → ring buffer), resampling to 64 points, and the ablation harness. |
| **P3 Profiles & rep engine** | The repetition state machine, the two-sided rep tracker, the hold analyser, and 15 exercise profiles plus 2 variants (17 library entries). |
| **P5 Records & export** | Per-repetition records stored in Room, and JSON and CSV export, byte-identical on every platform, checked once against the phone's real database. |
| **P7 MM-Fit pipeline** | MM-Fit's videos through MediaPipe and the phone's own analysis code, labels aligned set by set, and an evaluation harness over all 616 sets. |

---

## 🟡 Partly done

### P4 — Device reality check (on the owner's Nothing Phone (2a))

**Done:**
- the launch crash found and fixed (P3-18);
- the live screen, sync and Health Connect working on the phone;
- the guide demo diagnosed from the phone's own records (P3-23);
- a solo check that counted 15 of 15 curls (P3-24);
- the export run over the real database (P3-25);
- the P3-28/P3-29 build installed.

**Still owed** (tomorrow's session, `docs/P4_TOMORROW.md`):
- the curl test with each arm;
- the back camera;
- dim light;
- the book test (a short tracking loss);
- plank timing against a stopwatch;
- side-on push-ups and sit-ups;
- a heat run;
- a second person.

### P6 — Calibration & personal baselines

**Done:** the calibrator and baselines (per side, median and IQR), their storage with a tested
migration, and the calibration flow in the app (P3-22).

**Still owed:**
- a first real calibration on the phone;
- the number of calibration repetitions. The P3-30 study says 5–8 is too few for a
  range-of-motion baseline (about 12–20 would do), and the choice is the owner's.

### P8 — Form engine

**Done: Layer 1, in the shared core** (P3-30):
- personal "unlike your usual" findings (Level 2): range of motion, lifting and lowering speed,
  left–right balance, consistency;
- the cited ACSM tempo rule (Level 3, Tier A only);
- a personal start zone for push-ups and sit-ups.

It is tested on every platform, with 14 deliberately planted bugs all caught.

**Still owed:**
- showing Layer 1 in the app: deliberately not wired in yet;
- **Layer 2, the cited technique rules** (for example squat depth or push-up lockout), not
  started. Each needs a published source first.

### P10 — The Android app

**Done:**
- sign-in with a privacy disclosure;
- the exercise library;
- the history dashboard with streaks;
- Health Connect context;
- cloud backup and sync;
- the live camera screen: skeleton, rep count, holds, front and back camera;
- the calibration screens;
- the licences screen.

**Not built:** the visualisations, and everything in `docs/FEATURE_PLAN.md` beyond export (see
"Not started" below).

### P12 — Evaluation & write-up (six studies)

**Done:**
- **Repetition counting** measured on MM-Fit (P3-27 to P3-29), and re-read on the six people never
  used for development (P3-30):
  - curls 163 of 163;
  - lateral raise 146 of 150;
  - push-ups 110 of 201;
  - sit-ups 108 of 189.
- **The personalisation study's development half** (P3-30, four subjects):
  - people differ a lot, the lateral raise's range of motion most;
  - a population threshold's false alarms fall on particular people;
  - personal baselines detect planted changes more often.

**Still owed:**
- the personalisation study's held-out run, which waits for the owner;
- the exercise-verifier study, which needs P9;
- the device-performance and pose-reliability studies (from P4);
- the cross-platform parity study, written up as a study (the tests already pass);
- the final report and its limitations section.

---

## ⛔ Not started

| Item | Note |
|---|---|
| **P9 ML layer** | Personal anomaly detection first, then the exercise verifier. In scope since P3-34; the design and the anomaly study's protocol are written, no code yet. |
| **P8 Layer 2** | The cited technique rules. |
| **Setup assistant** | Checks framing, distance and view before scoring (CLAUDE.md §10). |
| **Feature Plan, Batch 1** | The new look (dark cards, orange accent) and five-tab navigation. The app still has the phosphor-green theme. |
| **Feature Plan, Batch 3** | Streak freezes, fire calendar, reminders, rest timer, personal records, voice counting, badges. |
| **Feature Plan, Batch 4** | Workout generator and guided workouts. |
| **Onboarding screens** | — |

## ✂️ Cut

**P11 Windows web app** (DECISIONS.md P0-2). The JavaScript *engine* survives and is tested. The web
*app* was never going to be built.

---

## Exercise by exercise: how well detection works today

"MM-Fit" figures are counted repetitions against MM-Fit's labels. "Unseen" means the six MM-Fit
people never used while tuning anything.

| Exercise | Filmed | Evidence so far | Verdict |
|---|---|---|---|
| **Bicep curl** | **side-on** since P3-32 | Sideways on the phone: 16 of 16 and 15 of 15 (P3-31). Facing the phone it counted arm swings as curls, so the view changed. MM-Fit's front-on 163 of 163 is no longer evidence for this view | ✅ Works sideways |
| **Shoulder press** (seated) | front-on | MM-Fit 348 of 360, 86% of sets exact, on P3-27's held-out workouts, which include people also used in development (P3-28); not yet re-read on the unseen six | ✅ Works |
| **Lateral raise** (seated) | front-on | MM-Fit unseen 146 of 150, 80% of sets exact (P3-30) | ✅ Works |
| **Push-up** | side-on | MM-Fit unseen 110 of 201; two people count almost nothing. Most can't reach the 155° start in 2D | ⚠️ Weak: the personal start zone is built but not in the app |
| **Sit-up** | side-on | MM-Fit unseen 108 of 189; two people count nothing | ⚠️ Weak: same fix pending |
| **Squat** / Goblet squat | side-on | Not tested on MM-Fit (filmed face-on there); on the phone the far knee is lost side-on (P3-23), while face-on counted 4 of 4 (P3-24) | ❓ The camera view is an open decision |
| **Lunge** / Reverse lunge | side-on | MM-Fit's view doesn't match, so its numbers are not evidence; the rear leg is expected to track badly | ❓ Unproven |
| **Bent-over row** | side-on | MM-Fit's view doesn't match (oblique): not evidence | ❓ Unproven |
| **Glute bridge** | side-on | No data anywhere; the Tier A exercise with the least behind it | ❓ Untested |
| **Standing hip abduction** | front-on | No data | ❓ Untested |
| **Standing knee raise** | side-on | No data | ❓ Untested |
| **Lying leg raise** | side-on | No data | ❓ Untested |
| **Plank** | side-on | Runs on the phone; timing not yet checked against a stopwatch | ❓ Check tomorrow |
| **Wall sit** | side-on | Runs on the phone; not checked | ❓ Untested |
| **Side plank** | **front-on** | No data | ❓ Untested |

---

## Quality checks in place

- **2,078 test executions, all passing.** The shared engine is tested on the JVM, JavaScript and both
  Android variants.
- **Golden-file parity:** the JVM, JS and Android give the same counts, and the same numbers to
  within 1e-9.
- **Guard tests** that fail if:
  - left and right are ever silently averaged;
  - the front camera's mirroring breaks;
  - a Tier B or Tier C exercise could make a technique claim;
  - the per-frame path starts allocating or re-scanning history.
- **Mutation checks:** every batch plants deliberate bugs to prove the tests catch them. P3-30:
  14 of 14 caught.
- **No accuracy claim** anywhere that has not been measured on this system (CLAUDE.md §22).

---

## What comes next, in order

1. **Tomorrow's phone session**, `docs/P4_TOMORROW.md`.
2. **The owner's decisions from P3-30:**
   - approve the held-out run;
   - what to do with the ACSM tempo message, which fires on about 97% of MM-Fit's curls;
   - the calibration count and the personal threshold.
3. **The personalisation study's held-out run.**
4. **Put Layer 1 into the app;** then P8 Layer 2, the cited technique rules.
5. **The remaining P12 studies and the report.**
6. **The Feature Plan's app features:** after the evaluation, per CLAUDE.md §21.
