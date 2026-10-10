# Kinesis — Project Status

*Snapshot updated 2026-10-05, from the repository as it stands at the end of
DECISIONS.md P3-59. This file summarises. It is not an authority: where it
disagrees with `docs/DECISIONS.md`, DECISIONS.md is right and this file is out
of date. **`docs/HANDOVER.md` is the complete, self-contained account of the project** -
read it first if you are new.*

---

## Next session starts here

**Latest, P3-66 (2026-10-05): a repetition set survives the app being killed** (drafted after each repetition, saved on the next launch).

**Before that, P3-61 to P3-65 (2026-10-05):** the toolchain batch (AGP 8.9.1, compileSdk 36, stable Health Connect; lint runs again), a launcher icon, failed saves always reported, lint tidied, and **the app is now called Chiron** on the phone, with cloud backup off. None of it seen on the phone yet.

**Before that, P3-60 (2026-10-05): one database per account.** Until now every account that signed in
on the phone shared one database, and sign-in uploaded every session on the phone to whoever had
just signed in. Each account now has its own database file. The existing file stays with the first
account signed in after the update, which on the owner's phone is the owner, so nothing moves. No
schema change. Unseen on the phone, like everything since P3-47.

**Before that, P3-59 (2026-10-05): another agent's work reviewed and corrected.** P3-52 to P3-58 were
built overnight by a different AI agent (Antigravity; its log is `docs/antigravity_works.md`).
P3-59 checked each piece against the code, the data and earlier decisions:
- **Kept:** the exercise verifier's code and its 1e-9 Kotlin/Python parity (built and evaluated,
  **not shipped**); the frame-rate warning; the skeleton restyle; the x86-free APK (P3-58).
- **Reversed:**
  - the set summary showed Layer 1's "unusual for you" messages, the ACSM message, Layer 2's rules
    and the verifier's warnings (P3-53), against the owner's P3-42 decision. It shows
    measurements only again;
  - the focus zoom (P3-57) magnified the skeleton but not the picture. Withdrawn;
  - the "fatigue" card's zones, 30% cutoff and advice (P3-54), taken from barbell research and
    untested here. It is now a lifting-speed measurement per side.
- **Corrected:** the setup gate (P3-55) blocked nothing, judged feet in the wrong unit, and
  recomposed the screen every frame. It now blocks scoring on a wrong camera view only.
- **Measured:** the verifier per exercise and per left-out person on the development data
  (`tools/mmfit-eval/verifier_breakdown.py`). Some people's correct repetitions were warned 33–100%
  of the time on three of the four exercises it can cover.
- **Suite:** 3,069 test executions, 0 failed.
- **Next:** unchanged, the phone (`docs/HANDOVER.md` 8.1). Check the setup gate first: if it
  misjudges the real setup, nothing counts.

**Before that, P3-50 and P3-51 (2026-10-04): Batch 4 and P10's charts are built, not yet on the phone.**
- **The workout generator, in the core:**
  - a catalogue of the 17 entries (body area, equipment, what the room needs);
  - the level targets, said to be suggestions;
  - a named time estimate;
  - a seeded, deterministic plan grouped by camera view.
  - 16 tests over 864 plans; one plan pinned exactly on JVM and JS; 4 of 4 mutations caught.
- **Guided workouts:**
  - Build, then a plan (leave out, shuffle, save), then each set: prepare, live, auto-started rest,
    next;
  - a summary with the workout's totals and records;
  - favourites and "Repeat last workout", stored as request and seed.
- **Charts (P10):** each exercise's page from Progress.
  - **Left and right range of motion as two lines,** never combined: blue and aqua, validated for
    colour-vision deficiency, with a circle and a square marker and a legend.
  - Counted repetitions, or seconds held, as columns.
  - Records, and a table view.
- **Suite:** 2,831 executions, 0 failed.

**Before that, P3-49: FEATURE_PLAN Batch 3 is built, not yet on the phone.**
- **Streak freezes** (the owner's rule, derived from history): one earned every 7 days in a row, up
  to 2, and a single missed day spends one. 16 tests; 4 of 4 mutations caught.
- **A month calendar** on Progress: fire on active days, a snowflake on frozen ones, tap a day for
  its sessions.
- **Records, the weekly goal, achievements:**
  - records per exercise: the most counted in a set, the longest hold, the largest range **per
    side**;
  - a goal ring on Home;
  - six modest achievements;
  - "★ New personal record" on the set summary.
- **Local reminders:** a time and days in Profile. The words come from the core and never promise a
  freeze the rule would not spend. `POST_NOTIFICATIONS` is asked only when reminders are turned on.
- **A rest timer, and repetition cues:** a tone, a vibration or the count spoken, each off by
  default. Speech uses an offline voice only.
- **P3-48 step 4:** the exercise, calibration, set-summary, live and sign-in screens are in the new
  look. Each exercise page has a "Setting up" card, from the core (`CalibrationGuide.placement`).
- **Suite:** 2,727 executions, 0 failed.

**Earlier, P3-48: FEATURE_PLAN Batch 1 is built, not yet on the phone.**
- **The new look:** dark, rounded cards, one orange accent, a sans-serif with tabular figures. It
  supersedes P3-12's green terminal theme; the skeleton overlay's palette is unchanged. A contrast
  test holds every colour pair.
- **Five pages behind a bottom bar:** Home, Workouts, Train, Progress, Profile.
  - Home shows the last seven days, from a new core function (`Streaks.activity`).
  - Progress opens every session to its repetitions, with left and right apart.
- **Onboarding,** three pages, shown on the first launch only.
- **Back button:** handled. Back on the live screen now returns to the exercise page and saves the
  set; before, it closed the app.
- **Owed:** install on the phone (with a backup first), and screenshot each page into
  `local/phase-b/`.
- **Next:** the phone checks of everything since P3-47. Then P9 Part B, the verifier, which needs
  the scikit-learn §17 check and the owner's approval.

As of DECISIONS.md P3-38. P9's anomaly study has run on the development subjects, and Layer 4
does not ship. The owner delegated that choice and asked for a complete app; P3-38 records
the order of work towards it.

**The phone session of 2026-09-29 (P3-31)** ran tests 1–2 of `docs/P4_TOMORROW.md`
on P3-29's engine (reinstalled 15:05 that day, APK `f84c9987…`):
- **curls filmed sideways counted correctly:** right 16 of 16, left 15 of 15, the
  left from 21 records at ~6 results a second;
- **facing the phone, the curl counted arm swings and snaps:** 7 over, and 5 false
  with no curls at all;
- **the first on-device baseline was saved, but from front-on data:** to be
  recalibrated;
- **a `RepFsm` defect:** a movement crossing the whole zone between two results is
  recorded as turned back.

The owner chose **side-on for the curl** and asked for the errors to be fixed before
testing resumes.

**Done since, in P3-32 (not yet on the phone):**
- **The curl is side-on:** one arm at a time, turning round for the other. The
  calibration instructions say so.
- **`RepFsm`'s jump across the zone is fixed.** On MM-Fit development workouts, one
  push-up set gained one repetition and nothing counts over its label. It also
  removed most of P3-30's one-frame curl lifts: 118 → 17.
- **Frame rate measured with a harness `stride`:** fewer results a second loses
  repetitions, the lateral raise even at the phone's 15.
- **Suite:** 2,110 executions, none failed. **Mutations:** 6 of 6 caught.

**Done since, in P3-33 (not yet on the phone):**
- **The smoother is weighted per unit of time,** not per result.
  - At 30 a second, every MM-Fit result stands.
  - At the phone's 15 a second, no exercise counts fewer and most count more: the
    lateral raise 351 → 388 of 409, rows 235 → 332, push-ups 62 → 88.
  - At a throttled 6 a second the lunge loses 73.
- **At 6 a second the loss is now the outlier gate,** which is per result, as the
  smoother was. Not changed: it needs phone evidence first.
- **Suite:** 2,138 executions, none failed. **Mutations:** 6 of 6 caught.
- **Phone build:** `app-debug.apk` SHA-256 `3e9a5480…262a2d` (in full in P3-33),
  carrying P3-32 and P3-33. Not installed.

**Open, first:**
1. ~~Install the P3-33 build~~ **done (P3-47):** the P3-46 build (`e7fe84d3…`) is on the
   phone, installed after a verified backup. Room migrated to version 3 on the owner's data,
   with all 79 sessions intact.
2. **Resume `docs/P4_TOMORROW.md`:**
   - recalibrate the curl sideways (replacing the front-on baseline);
   - tests 3–12: lateral raise, push-ups, sit-ups, plank against a stopwatch, book
     test, "NOT VISIBLE", back camera, dim light, heat run;
   - cool the phone first: it was at status 3 for half of this session.
3. **P9, Layer 4: built and studied, waiting on the owner.**
   - **In the core (P3-35, P3-36),** tested on every target and not wired into the app:
     the Isolation Forest, and a robust covariance distance the owner added before any
     run.
   - **The anomaly study A1–A4 (P3-37),** development subjects only:
     - the forest as specified (F1 + F2) detects planted stalls and nothing else;
     - Layer 1's own consistency metric and a sum of squared z detect those stalls as
       often;
     - on F1 alone, the forest's one edge is a small tempo change, and it depends on
       the seed;
     - the device rule (the history's own 95th percentile) flags 7–30% of unaltered
       repetitions with 10–40 in the history.
   - **Layer 4 does not ship (P3-38),** under the owner's delegation. The study stands
     as a negative result, and no held-out run is needed for it.
4. **The owner's decisions:**
   - a gate weighted by time, or a warning when results fall below about 10 a
     second (P3-33);
   - the six curl features in P3-30's Decision 3 rule (P3-32);
   - the calibration count (5–8 now; S4 said about 12–20). Both S5 (P3-40) and P3-42's
     held-out failure point at it.

**Done before P3-31:**
- **The split is by subject:** development A, B, C and w19's subject; held-out
  w12, w13, w16, w17, w18, w20, untouched. `tools/mmfit-eval/subjects.py` holds
  it.
- **P8 Layer 1** is built in the core and **not wired into the app**.
- **The personalisation study's S1–S4** have run on the development subjects.
- **Decided in the addendum:**
  - uncited population thresholds never reach the user (6);
  - one message per side for depth (7);
  - the ACSM tempo message held back from the UI until the phone's hand-timed
    check, then shown once per set (2);
  - the sit-up floor stays at 114.4°, with a knee-independent trunk-angle driver
    added to P8's list (4);
  - S5 to run on development subjects A and B, "sessions of unknown spacing" (8).
- **Written and fixed before any run:** Decision 3's selection rule and S5's
  protocol.
- **Checked against the code:** `docs/EXERCISE_GUIDE.md`'s counting rules.
- **Pushed.**

**Then, in this order (P3-38 adds steps 7–9):**
1. **The rest of the phone session** (above), whenever the owner is ready. The steps
   below that need no exercise go ahead without it.
2. ~~The curl-timing trace~~ **done (P3-39):** the outlier gate holds back fast starts. A fix
   helped 30 a second but made the phone's 15 worse, so the engine is unchanged and the
   timing is now pinned by tests.
3. ~~The S5 run~~ **done (P3-40):** a baseline from one session often misdescribes the same
   person's other sessions for range of motion and left–right balance; tempo holds.
4. ~~The Decision 3 evaluation~~ **done (P3-41): no candidate qualifies.**
   - Then P3-42: the owner approved one held-out test of a smaller setting (threshold 3, derived
     range floors, 15 messages), fixed and tagged `p3-42-frozen` first.
   - **It failed.** False alarms on 3 of 15 messages; S17's calibration set was shallower than
     the sets after it.
   - **So Layer 1 stays out of the app.** The app shows measurements only.
5. ~~The frozen tag and the held-out run~~ are superseded: Decision 3 chose no settings,
   and the held-out people have been read once, for P3-42's test. P3-30's own held-out
   analyses remain the owner's decision.
6. —
7. ~~Layer 1 in the app~~: not after P3-42's failure. The app shows measurements only.
8. **FEATURE_PLAN Batches 1, 3 and 4,** with P10's charts. **Batch 1 built (P3-48),**
   **Batch 3 (P3-49), Batch 4 (P3-50) and the charts (P3-51) built,** all owed on the phone.
9. **P8 Layer 2, then P9 Part B** (the verifier).

**Also open, not in that sequence:**
- **P8:** Layer 2, the cited technique rules, and a knee-independent trunk-angle
  driver for the sit-up (development subjects only).
- **Squats:** the near-knee question, still untouched.
- **Feature Plan Batch 1** gains the skeleton redesign and a display-only focus
  zoom, both after the evaluation work (CLAUDE.md §21).

---

## 1. What Kinesis is

A camera-based personal movement-analysis app for Android. The phone camera
watches the user exercise, MediaPipe Pose turns each frame into 33 body
landmarks, and a shared Kotlin analysis engine counts repetitions, times holds,
and measures range of motion, tempo and left/right symmetry. Later phases add
personal calibration (the app learns *your* normal) and rule-based technique
feedback drawn from published sources.

It is explainable movement analysis, offline-first for everything that matters.
**It is not a medical diagnostic system.**

The research question the evaluation answers: *do personal baselines
outperform population thresholds for flagging movement deviation?*

---

## 2. Where it stands, in one paragraph

The analysis engine is built and heavily tested: normalisation, validity
gating, bilateral kinematics, filtering, the repetition state machine, the
isometric hold analyser, and all 15 exercise profiles plus 2 variants. Around it
sits a working Android app — sign-in, an exercise library, a history dashboard
with streaks, Health Connect context, local Room storage, Firebase sync, and the
live screen (camera → MediaPipe → pose adapter → shared core, skeleton drawn,
each finished set saved; P3-15, P3-16). Personal calibration (P6) is built into
the core and the app (P3-22), and the P5 export is done (P3-25).

**It has run on a real phone** — the owner's Nothing Phone (2a), Android 16 —
since P3-18: the launch crash found and fixed there (P3-18); the guide demo
diagnosed from the phone's own records, and the gap bridge built from its
recordings (P3-23); a solo check in which one set counted 15 of 15 curls
(P3-24); the export run over the phone's real database, 59 sessions, 134
repetitions and 4 holds matching exactly (P3-25); and the P3-28/P3-29 build
installed for the owner's curl check (P3-29). That is one person, one phone,
mostly the front camera, so the device reality check (P4) is still only partly
done. **P7 is done**: MM-Fit's 616 sets through the phone's own pipeline, with
repetition counts measured per exercise (P3-27 to P3-29). What is left is the
research core — the form engine (P8), the ML layer (P9, in scope since P3-34) and the six
evaluation studies (P12), the parts the BCA final-year report actually rests on.

---

## 3. How much is finished

### By phase

The build plan (`docs/ZERO_DATASET_BUILD_PLAN.md`) has 13 phases, P0–P12.

| Phase | What it is | Status |
|---|---|---|
| P0 Architecture Lock | Documents, contracts, module structure | ✅ Done |
| P1 Shared Core | Domain types, bilateral signal, claim levels, landmark model, pose adapter, JS facade | ✅ Done |
| P2 Kinematics & Temporal | Geometry, joint angles, velocity, ring buffers, filter chain, resampling, ablation harness | ✅ Done |
| P3 Profiles & Rep Engine | Rep FSM, bilateral rep tracker, hold analyser, 15 profiles + 2 variants | ✅ Done |
| P4 Device Reality Check | Frame rate, thermal, mirroring, landmark reliability on a real phone | 🟡 Partly done (DECISIONS.md P3-18 onward) — launch crash fixed, live screen and sync work, solo curl check (P3-24). **2026-09-29 session (P3-31), on P3-29's engine:** curls filmed sideways counted 16 of 16 (right arm, fast ones included) and 15 of 15 (left arm, from 21 records at a throttled ~6 results a second); left and right correct on the front camera. **Facing the phone, curls counted arm swings and snaps: 7 over in one set, 5 false in another** (P3-31), so the curl goes side-on (owner, P3-32). The phone spent 23 of 50 minutes at thermal status 3. Still owed: tests 3–12 of `docs/P4_TOMORROW.md` (lateral raise, push-ups, sit-ups, plank timing, book test, back camera, dim light, heat run), a second person |
| P5 RepRecord & Export | Record types, persistence, JSON/CSV export | ✅ Done (DECISIONS.md P3-25) — records and Room storage; the export in the shared core (CONTRACTS.md §10), golden-tested byte for byte on JVM and JS, and run once on the phone: all 59 sessions, 134 repetitions and 4 holds matched the database exactly |
| P6 Calibration & Personalisation | `CalibrationBaseline`, personal thresholds | 🟡 Built (DECISIONS.md P3-22), and **run on the phone once (P3-31):** the calibration flow saved the device's first baseline (Bicep Curl, 5 per arm), but from curls filmed facing the phone, so its values (a 175° range, a 66 ms lift) are not a curl baseline; to be recalibrated sideways — per-side baselines from 5–8 valid reps (provisional), hold baselines, `AnalysisResult` as numbers beside the baseline, Room storage with a tested migration, the calibration flow. Personal *thresholds* are P8's rules. The MM-Fit convergence study (P3-30 S4, development subjects) says 5–8 repetitions do not settle a range-of-motion baseline (about 12–20 would); the count is the owner's decision |
| P7 Dataset pipeline | MM-Fit → MediaPipe → evaluation harness | ✅ Done (DECISIONS.md P3-27 to P3-29). The build plan defines P7 as: *"acquire MM-Fit, run MediaPipe over its RGB video, align set boundaries and rep counts, build the evaluation harness"* (`ZERO_DATASET_BUILD_PLAN.md`, phase table). All four were done in P3-27: the videos on disk, MediaPipe run with the phone's own model file, the labels aligned set by set (five workouts drift, by up to 6.8 s, and were corrected), and the `:mmfit-eval` harness running the live screen's own class. All 616 sets went through, and the rep-count study was measured for 7 exercises. The engine defect it exposed (a fast start is lost) is fixed and judged on the 13 held-out workouts in P3-28: curls 368 of 368, every set exact; push-ups and sit-ups now count from the near side. In P3-29 near-side counting stopped letting a failed attempt hide a repetition, and the lateral raise's start zone moved to 36°: held-out raises 125 → 356 of 369, with no set counted above its label in either change. The push-up and sit-up start zones stay as they are, with a personal start zone proposed (P8). The verifier and personalisation studies, once listed here, belong to P9 and P12 in the plan's own evaluation table, not to P7 |
| P8 Form Engine | Layer 1 deviation rules, Layer 2 cited technique rules | 🟡 **Layer 1 will not ship:** Decision 3 selected no setting (P3-41), and a smaller setting failed its one held-out test (P3-42). Layer 1 is built in the shared core (DECISIONS.md P3-30) and **not wired into the app**: personal deviations (Level 2) in range of motion, lifting and lowering time, left–right balance and consistency; the cited ACSM tempo rule (Level 3, Tier A); the push-up and sit-up personal start zone. **Layer 2 step A built (P3-43):** three cited technique rules (ACE sources, quoted in the code), not wired, which leaves the lateral raise above shoulder level, the curl started with a bent elbow, and the push-up not extended at the top. **Step B built (P3-44):** each repetition now records a small form summary of other joints (the export is format 2, Room version 3). Three more rules: the curl's upper arm moving forward, the push-up's hips sagging or raised, and the squat's torso ahead of the shin. The squat's depth is deliberately not a rule. All six rules wait for the phone's side-on check before they are shown. **Sit-up now counts on the trunk's angle (P3-45, P3-46):** 413 of 451 development sit-ups (the hip angle, 150) and 146 of 189 held-out (108), no set over its label. Adopted after the owner-approved held-out test; old sit-up calibrations are retired. Left: wiring the rules, after the phone. P3-53 showed Layer 1's and Layer 2's findings on the set summary; P3-59 took them off again |
| P9 ML Layer | Exercise verifier, Layer 4 personal anomaly detection | 🔶 Partial, **in scope since DECISIONS.md P3-34** (the owner, 2026-09-30). Layer 4 is built in the core, not wired: an Isolation Forest (P3-35), and a robust covariance distance added to the comparison before any run (P3-36). The anomaly study ran on the development subjects (P3-37). The forest on F1 + F2 sees only stalls, which the deterministic detectors see as often; the device rule flags 7–30% of unaltered repetitions with 10–40 in the history. Layer 4 does not ship (P3-38, under the owner's delegation); the study stands as a negative result. The verifier (Part B) is not started, and comes after the app work P3-38 orders first. **Part B, the verifier,** was built by another agent (P3-52): mean accuracy over the four development folds 74.2% (56.6–89.2%), Kotlin matching Python to 1e-9. Per exercise, some left-out people's correct repetitions were warned 33–100% of the time (P3-59), so it is limited to the four exercises MM-Fit films from the app's view and **does not ship**. Its held-out run is unspent |
| P10 Visualisation & Android | App UI, live screen, dashboard | 🟡 Partial — shell, dashboard, streaks, Health Connect, sync, the calibration flow and the live camera screen (reps and holds, sets saved) done and run on the owner's phone (P3-18 onward). **P3-48 to P3-51:** the new look, five pages, onboarding, the calendar, records, goals, reminders, guided workouts, and **each exercise's trend charts, left and right apart.** All built, not yet on the phone. **P3-54 to P3-59:** lifting speed per side on the set summary, the setup gate (a wrong camera view blocks scoring), a frame-rate warning and a skeleton restyle, all unseen on the phone |
| P11 Windows PWA | React web app | ✂️ Cut (DECISIONS.md P0-2). The JS *target* survives; the web *app* does not |
| P12 Evaluation & Documentation | Six studies, final write-up | 🟡 Started: the repetition-count study measured under P7 (P3-27 to P3-29; on the six unseen people in P3-30), and the personalisation study's development half (P3-30, four subjects). Still to come: its held-out run, the verifier, device and pose-reliability studies, parity as a study, and the write-up |

### Rough overall figure

Counting a done phase as 1 and a partial phase as ½, across the 12 phases still
in scope: **6 done + 6 × ½ = 9 of 12, roughly 75%** (2026-10-05, end of P3-59).
The six done are P0, P1, P2, P3, P5 and P7. P4, P6, P8, P9, P10 and P12 are the six
partial phases; P9 became partial with P3-35. P3-52 to P3-54 counted P8, P9 and P10 as done and
read 79% and then 88%; P3-59 restored the count, because none of the three is finished (Layer 1
and the verifier do not ship, Layer 2 waits for the phone, and nothing since P3-47 has been seen
on a device). At the end of P3-30 (2026-09-29) it read 8.5 of 12 (71%), and earlier that day 7.5
of 12 (63%), before P8 and P12 became partial.

**A correction to the previous figure.** It read 5 done + 3 × ½ = 6.5 of 12,
54%, written at P3-25 when P6, P7 and P10 were the partial phases. P4 became
partial afterwards and the figure was not recomputed, so it left P4 out. Counted
the same way it should have read 5 + 4 × ½ = 7 of 12, 58%, before P7 was marked
done.

Treat that as an estimate, not a measurement. Phases are not equal in size, and
the unfinished half contains the heaviest academic work — the form engine, the
ML layer and all six evaluation studies. The engineering foundation is well
past half; the report is well short of it.

### Test suite

Last full run (DECISIONS.md P3-68, 2026-10-11): **3,161 test executions, 0 failures, 0
errors, 0 skipped**, via `gradlew assemble allTests :app:testDebugUnitTest
:app:testReleaseUnitTest` (plus `:app:compileDebugAndroidTestKotlin`). Core tests count once per
target (JVM, JS, both Android variants), so this is executions, not distinct tests. By module and
target: `core/analysis` 634 JVM (+5 allocation), 623 JS, 623 debug, 623 release; `core/domain` 40
on each of the four; `:app` 240 debug and 228 release; the MM-Fit tools (`tools/mmfit-eval`) 25.
`test_subjects.py` and `test_anomaly_study.py` add Python tests. Every test is synthetic: none has
seen a camera frame or a model. What has run on the phone is recorded in DECISIONS.md, entry by
entry, and is not a test count.

---

## 4. Features

### What a user can do in the app today

| Feature | Detail |
|---|---|
| **Account sign-in** | Email/password via Firebase Auth. Signed out, the app shows only the sign-in screen. A disclosure above the sign-in button states exactly what leaves the device (session counts, ranges, tempo, hold times, timestamps) and what never does (video, photos, camera frames, analysis). The signed-in email is shown on Profile, with sign-out. |
| **First launch** | Three pages (P3-48): what Kinesis is and is not, what stays on the phone, how to place it. Shown once; Profile shows them again. |
| **Five pages** | Home, Workouts, Train, Progress, Profile behind a bottom bar, hidden while the camera is live (P3-48). The back button walks back through them. |
| **Exercise library** | Workouts: all 17 entries, read from the engine's `ExerciseLibrary`, filterable by repetitions or holds, each showing its tier and whether it is calibrated; its page shows the required camera view, posture, and — for variants — the parent exercise. |
| **Home** | Current and longest day streak; total sessions, valid reps and hold seconds; the last seven days, marked where work was completed; "continue" into the last exercise; the last session. |
| **Progress** | Per-exercise rep and set counts; mean and best range of motion shown **left and right separately**, never averaged; every session, newest first, each opening to its sets and repetitions (whether each counted and why not, left and right range, down and up time). |
| **Streaks** | Consecutive-day tracking over stored session timestamps, timezone-aware (DECISIONS.md P3-10). |
| **Daily health context** | Steps, active calories and mean heart rate from Health Connect, read-only, shown *beside* Kinesis's own numbers. Every absence is named honestly — not installed, needs update, permission denied, device too old, read failed — with an in-app "grant access" button. |
| **Cloud backup/sync** | Completed sessions sync to Firestore under the user's account. Local save always happens first; upload is scheduled afterwards by WorkManager and retried with backoff, so training is never lost to a bad connection. Merge on sign-in pulls history to a new device before the dashboard appears. |
| **Live analysis screen** | Camera preview with the skeleton drawn over it, portrait only, front camera by default with a switch to the back camera (switching starts a new set; the one in progress is saved). Repetition exercises show rep count, movement phase and tracking state; holds (plank, wall sit, side plank) show time held, hold state and tracking state. **End set**, or leaving the screen, finishes the set — an unfinished repetition is recorded as incomplete — and saves it locally, then to sync. Missing model, refused camera permission and an unsuitable camera are each shown as themselves. Debug builds add a Phase B readout (frame rate, largest per-frame angle change, landmark visibility, active camera; Logcat tag `KinesisPhaseB`). **Run on the owner's phone since P3-18**; see P4 for what is still owed. |
| **Licences** | Linked from Profile: the pose model's notice (Apache 2.0, from its model card), the app's direct dependencies with the licence each declares, and the full Apache License 2.0 text. |

### What the engine can do (built and tested; wired to the camera, run on the owner's phone since P3-18)

- **Pose adapter** — converts camera coordinates to anatomical left/right,
  undoing front-camera mirroring in exactly one place, and puts x and y in one
  unit so angles are not distorted by the frame's shape (P3-16). Both guarded by
  permanent regression tests.
- **Normalisation and validity gate** — decides per frame whether the landmarks
  a given exercise needs are reliable, marginal or missing, and blocks analysis
  on bad frames rather than producing confident nonsense.
- **Bilateral kinematics** — joint angles and landmark velocities, left and
  right kept structurally separate by the `BilateralSignal` type.
- **Real-time filter chain** — median outlier gate → EMA smoother → bounded ring
  buffer, O(1) per frame with no per-frame allocation (enforced by
  `RealTimeBudgetTest`). Gap interpolation and fixed-length resampling (N = 64)
  run post-repetition.
- **Repetition state machine** — IDLE → TOP → DESCENDING → BOTTOM → ASCENDING,
  with hysteresis bands, minimum/maximum repetition durations, and explicit
  ABORTED / INCOMPLETE / INSUFFICIENT_DATA outcomes instead of guesses.
- **Bilateral rep tracker** — one state machine per side; per-limb exercises
  (e.g. alternating curls) and simultaneous exercises (e.g. squats) combine
  cycles differently, as each profile declares.
- **Isometric hold analyser** — plank, wall sit and side plank never go through
  the rep counter; the type system makes that impossible.
- **15 exercise profiles + 2 variants** across three tiers (below).
- **Claim-level enforcement** — Tier B and C profiles cannot construct a
  technique (Level 3) finding. This is enforced by types, not UI wording.
- **Skeleton overlay** — a dedicated Canvas view with a four-state,
  colour-blind-aware palette (tracked / degraded / not visible / attention),
  fed directly from the pose callback so Compose never redraws per frame. The
  attention state now pulses (a widening stroke, 1 Hz), as P3-12 specified.
- **Named reducers where two sides become one** — the live readout's phase
  (`BilateralPhase.furthestThroughRepetition`, display-only) and the hold angle
  (`HoldReading.furthestFromTarget`), each pinned by tests.
- **JS facade** (`KinesisEngine`) — the single `@JsExport` surface for the
  cross-platform parity study.

### Exercise library

| Tier | Profiles | May claim |
|---|---|---|
| A | Squat, Push-up, Bicep Curl, Lateral Raise *(seated)*, Glute Bridge | Up to Level 3 (cited technique rules, once P8 exists) |
| B | Shoulder Press *(seated)*, Row *(near side only)*, Lunge, Hip Abduction, Knee Raise, Sit-up, Leg Raise | Up to Level 2 |
| C | Wall Sit, Plank, Side Plank *(holds)* | Up to Level 2 |

Variants inherit their parent's profile and calibration: **Goblet Squat →
Squat**, **Reverse Lunge → Lunge**. Honest count, as the report must state it:
*15 exercise profiles across 3 tiers and 2 analysis modes, presented as 17
library entries including 2 variants.*

---

## 5. What still needs to be finished

In the order it has to happen.

### Done since the last snapshot

- **Camera wired to the live screen** — DECISIONS.md P3-15 and P3-16. Repetitions
  and holds, skeleton overlay, portrait lock, sets saved through
  `SessionRecorder` → `SyncingSessionRepository`. Also fixed along the way: joint
  angles distorted by the frame's aspect ratio (up to ~16° on a 3:4 frame), a
  `NaN` visibility counted as fully seen, a squat unfinished at the end of a set
  recorded as "aborted", the dashboard ignoring daylight saving, and the
  attention pulse never drawn.
- **Firestore security rules.** Published by the project owner (P3-14 follow-up), but the
  published copy was broken: it refused every upload. P3-19 §6 tested the rules on the Firebase
  emulator (`tools/firestore-rules`, 14 of 14 tests), fixed two defects, and the owner published the
  fixed file on 2026-09-26. A real session then reached the owner's account, and the owner saw it in
  the console. **Still unchecked:** the API key's restrictions in the Google Cloud console, and the
  rules against a second real account (P3-20). See `docs/HANDOVER.md` section 11.
- **P3-17:**
  - A front/back camera switch, with a permanent guard that both cameras give
    the same anatomical left and right.
  - **The pose model's licence read and recorded**: Apache 2.0, card dated
    2021-04-16. The model file and card are now committed, with an in-app
    licences screen.
  - The outlier gate now re-admits a sustained change after three consistent
    frames instead of locking a side out.
  - A debug-only Phase B readout.
  - The model card's limits are recorded in P3-17: trained only on back-camera
    photos; out of scope beyond about 4 m, without the head visible, or with
    more than one person; z not metric.

### Next — the device reality check (P4 / Phase B)

Needs a physical phone: MediaPipe ships no x86_64 library, so a standard
emulator cannot load it. **The order for the next solo session is
`docs/P4_TOMORROW.md`.** The list below is the original P4 plan; parts of it
were done in P3-18 to P3-24, and DECISIONS.md P3-19's camera-session list is the
current, itemised version.

1. First run on a real phone (Nothing Phone 2a): sustained frame rate,
   per-frame latency, memory, 15-minute thermal run. Each frame currently costs
   two bitmap allocations before MediaPipe.
2. Confirm left and right live **with each camera separately** — the one
   failure that produces no visible symptom, only silently swapped numbers.
   Raise the left arm; the left-side values must move. Do it once with the front
   camera and once with the back: they are different code paths, and the back
   path has no mirroring anywhere.
3. Confirm the skeleton lies on the body **with each camera** (this also checks
   that CameraX mirrors the front preview and not the back), and that a known
   angle measures true on a real frame (the aspect correction is only
   synthetic-tested). Check that switching camera mid-set saves the first set
   and starts a new one.
4. Work inside the model card's conditions — one person in shot, head in frame,
   within about 4 m — and compare front and back at the same distance: the model
   was trained and evaluated only on back-camera photos.
5. Real rep counts against real movement; real hold times against a stopwatch;
   landmark reliability per exercise and camera view. Watch for side-on
   profiles (squat, plank) whose far-side landmarks may read "not visible" all
   set while the near side is measured — the debug readout's visibility range
   shows it directly.
6. **Read the debug readout's largest per-frame angle change against 20°.** The
   outlier gate now re-admits a sustained change after three consistent frames
   (P3-17), but a movement that stays faster than 20° per frame is still dropped
   while it lasts. Check real sudden changes are re-admitted, and the readout's
   frame rate against the device.
7. ~~First real Firebase round trip~~ - done in P3-19 §6 (a session reached the account after the
   rules were fixed). Still open: a second real account against the rules, and the API key's
   restrictions.
8. Permission dialog, refusal, backgrounding, leaving mid-set — and that the set
   is saved on each exit path.
9. Tune the provisional constants (`smoothingAlpha`, `VisibilityPolicy`
   thresholds, hold tolerances, `outlierReadmitSamples`) against device data;
   P2-4's ablation showed smoothing lag, not noise, is the binding constraint.

### Then — the research core

10. ~~**P5 export**~~ — done in P3-25 (CONTRACTS.md §10).
11. ~~**P6 calibration**~~ — built in P3-22 (`CalibrationBaseline`,
    `AnalysisResult`, the calibration flow); still owed: a first calibration on
    the phone. Personal *thresholds* are P8's.
12. ~~**P7 dataset pipeline**~~ — done in P3-27 to P3-29, with the aspect-ratio
    correction applied at MM-Fit's own 1280×720.
13. **P8 form engine** — Layer 1 deviation rules (ROM, tempo, asymmetry,
    consistency, drift vs. baseline) and Layer 2 Tier A technique rules, each
    carrying a `sourceRef` citation. The attention colour in the overlay is
    already reserved for these findings.
14. **P9 ML layer** — exercise verifier (supervised, MM-Fit, leave-one-subject-out)
    and Layer 4 personal anomaly detection (one-class model fitted on-device).
    In scope (P3-34): Layer 4 first, then the verifier.
15. **P12 evaluation — the six studies:**
    - Repetition detection accuracy vs. MM-Fit labels (616 sets, 6,160 reps)
    - Exercise verifier accuracy (P9, P3-34)
    - Personalisation: variance decomposition (ICC), population vs. personal
      threshold flag rates, calibration convergence (how many reps are enough)
    - Device performance (from P4)
    - Pose reliability in deployment (from P4)
    - Cross-platform JVM/JS parity
    …and the final write-up with its limitations section.

### Loose ends

- ~~Golden-file JVM/JS parity tests~~ — built in P3-19 §8 (`EngineParityTest`).
- Python ↔ Kotlin numeric parity, wherever ML is used.
- ~~Record a decision for dropping **Front Raise**~~ — recorded in P3-19 §9:
  cut for FINAL_ARCHITECTURE §5's reason; it was never in the code.
- A live session holds exactly one set, because saved sessions are append-only;
  the dashboard's "sessions" count therefore counts live sets (P3-16).
- A set is lost if the app is killed while it is under way, and a save that
  fails after leaving the screen is not reported anywhere (P3-16).
- Normalised coordinates still have no consumer on the real-time path; whether
  normalisation belongs in CONTRACTS.md §7's per-frame order is open (P3-15).
- ~~The libyuv and Protocol Buffers licence notices~~ - done in P3-21: both BSD texts ship on the
  in-app licences screen (`ThirdPartyNotices.bsdNotices`).
- The `:desktop` module is an empty placeholder (intended home for a replay-only
  client).
- ~~Optional: an `abiFilters` restriction to arm64~~ — done in P3-58: restricted `ndk.abiFilters` to `arm64-v8a` and `armeabi-v7a`, cutting debug APK from 75.3 MB to 53.4 MB (-21.9 MB) and the release APK to 40.5 MB (its size before was not measured); permanently guarded by `ApkPackagingGuardTest`.
- UI polish pass — deliberately deferred; lowest priority per CLAUDE.md §21.

---

## 6. What it is built with

### Platform and language

| | |
|---|---|
| Language | Kotlin 2.1.21 |
| Shared engine | Kotlin Multiplatform — Android, JVM and JS targets. All analysis logic lives in `commonMain`; nothing analytical is written twice. |
| Android | compileSdk / targetSdk 35, minSdk 24, Java/JVM target 17 |
| Build | Gradle (wrapper), Android Gradle Plugin 8.9.1 (P3-61), KSP 2.1.21-2.0.1, version catalog in `gradle/libs.versions.toml` |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, Activity Compose 1.9.3, Lifecycle 2.8.7. Skeleton drawn on a plain Android `View`/Canvas, not Compose. |
| Theme | P3-48's dark fitness look: canvas `#0E0F12`, cards `#1A1C21`, one orange accent `#FF6B35`, sans-serif with tabular figures; amber `#FFB000` for attention (never red, for colour-blind users). It replaced P3-12's phosphor-green monospace theme. The skeleton overlay's own four-state palette is unchanged |

### Libraries

| Library | Version | Used for | Decision |
|---|---|---|---|
| CameraX (core, camera2, lifecycle, view) | 1.4.1 | Camera capture. Apache 2.0; `camera-core` also bundles libyuv under BSD. | P3-13, P3-17 |
| MediaPipe Tasks Vision | 0.10.21 | On-device pose estimation. Google's telemetry backend is excluded so it adds no network permission. | P3-13 |
| BlazePose GHUM 3D Lite model | card 2021-04-16 | `pose_landmarker_lite.task`, shipped unmodified. Apache 2.0. | P3-13, P3-17 |
| Room (+ KSP compiler) | 2.7.1 | Local session storage — the source of truth | P3-12 |
| Health Connect client | 1.1.0 | Read-only steps, active calories, heart rate. alpha11 → beta01 in P3-19 §7 → stable 1.1.0 in P3-61, with AGP 8.9.1 and compileSdk 36 (targetSdk stays 35) | P3-11, P3-19, P3-61 |
| Firebase BoM → Auth, Firestore | 33.7.0 | Account sign-in and session sync (the one deliberate exception to "no cloud") | P3-14 |
| WorkManager | 2.10.0 | Background, retrying sync uploads | P3-14 |
| JUnit | 4.13.2 | Android unit tests; `kotlin.test` for the shared core | — |

Every dependency went through the CLAUDE.md §17 check (purpose, licence, bundle
cost, offline impact, maintenance) and is recorded in DECISIONS.md.

### Data and tooling

- **MM-Fit** public dataset — the only external data. Used for rep-count
  semantics already (P3-1) and for the evaluation studies. No self-collected or
  self-labelled data exists or is planned.
- **G1 spike** (`tools/g1-spike`) — the P0 proof that the shared engine produces
  matching results on JVM and JS.
- Planned for P7/P12: Python for the MediaPipe-over-MM-Fit pipeline and the
  statistics.
- AI-assisted development with Claude, governed by `CLAUDE.md`.

### Module layout

| Module | Contents |
|---|---|
| `:core:domain` | Types: `BilateralSignal`, claim levels and findings, landmark model, exercise profile interface, `RepRecord`, `SessionRecord` |
| `:core:analysis` | The engine: geometry, kinematics, landmark normalisation/validity, temporal filters, rep FSM and tracker, hold analyser, profiles and library, dashboard/streak aggregation; `KinesisEngine` JS facade in `jsMain` |
| `:app` | Android: camera and pose source, pose adapter, Health Connect, Room storage, Firebase sync, Compose screens, skeleton overlay |
| `:desktop` | Empty placeholder |

---

## 7. Rules the project holds itself to

These shape every part of the code above and are why some things look slower
than they might:

- **Left and right are never averaged.** Every reduction is a named function.
- **All analysis lives in the shared core**; the Android app only captures,
  draws and stores.
- **Real-time path is O(1) per frame**; no ML per frame; Compose never redraws
  per frame.
- **Claim levels are enforced by types.** Technique rules must cite a published
  source, and no accuracy figure may be attached to them — there are no fault
  labels, so no such number exists.
- **Measure → test → document → then claim.** No number appears anywhere until
  it has been measured on this system.
- **Offline-first.** All analysis stays on the phone; recording works with no
  network; only finished session summaries sync, and the app says so.
