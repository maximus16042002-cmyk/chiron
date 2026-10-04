# Kinesis — Feature Plan: from analysis engine to complete fitness app

*Written 2026-09-23 at the project owner's direction. This is a plan, not an authority:
`docs/DECISIONS.md` and `CLAUDE.md` still win. Each batch below must record its own
decisions in DECISIONS.md (next `P3-` number) before it is considered done.*

## Owner decisions this plan is built on

- **Goal:** Kinesis should feel like a complete fitness app. Form feedback (the
  "posture correction") is the main feature; streaks, a workout generator,
  reminders, several pages and a new UI surround it.
- **UI direction:** modern dark fitness style — dark background, rounded cards,
  one bright accent colour, a normal (non-monospace) font, big numbers. This
  **replaces the P3-12 phosphor-green terminal theme** and must be recorded as a
  new owner decision that supersedes it.
- **Order (changed 2026-09-26, DECISIONS.md P3-21):** these batches no longer
  wait for Phase B. The camera session is deferred, and work continues before
  it. **P6 calibration (the first half of Batch 2) goes first**, ahead of P5 and
  P7, so there is a demo for the project's guide; P5, P7 and the other batches
  follow at the owner's direction. Anything tuned to real movement stays
  provisional until the camera session.
- **Streaks:** missing a day does not reset the streak if a **streak freeze** is
  available (Duolingo-style).
- **Cameras:** front and back camera support is already being built in P3-17 and
  is not repeated here.

## Rules every batch keeps (from CLAUDE.md — restated because new UI work tempts people to break them)

1. **Analysis stays in the shared core.** Streak/freeze rules, workout-generator
   logic, calibration and form rules go in `commonMain` with tests on JVM and JS.
   The app only renders and stores.
2. **Left and right never averaged.** New screens show L and R separately
   wherever a bilateral value appears.
3. **Claim levels are structural.** Form feedback of Level 3 (technique) is
   **Tier A only** and every rule carries a `sourceRef` citation. Tier B/C
   exercises get measurements (Level 1) and personal deviation (Level 2, after
   calibration) only. No accuracy figure is attached to any technique rule.
4. **Wording.** Kinesis is not a medical or physiotherapy tool. In the UI say
   "form feedback", never "diagnosis", "injury", "correct posture guaranteed" or
   similar. Feedback text is composed in the core, never in UI code.
5. **Offline-first.** No new network capability. Reminders are **local
   notifications only** — no Firebase Cloud Messaging or any push service.
   `OfflineManifestGuardTest` must keep passing.
6. **Every new dependency** (e.g. Navigation Compose, DataStore) gets the §17
   check written into DECISIONS.md, or is avoided.
7. **Skeleton overlay legibility survives the redesign.** The four overlay
   states, the amber attention colour (never red), the wider attention stroke
   and the pulse stay; `SkeletonPaletteLegibilityTest` must pass unchanged.
8. **Tests first, mutation-check new guards, run the full suite, honest report,
   DECISIONS entry, commit** — same as every batch so far.
9. **Real-time path unchanged.** No new work per camera frame except what the
   feature strictly needs; zero per-frame allocation in the core stays.

---

## Batch 1 — New look and multiple pages (foundation)

**Design system**
- Define theme tokens in one place: colours, type scale, spacing, corner radius.
  Suggested starting point (adjust if contrast checks fail): background
  `#0E0F12`, card `#1A1C21`, raised card `#23262D`, text `#F2F3F5`, secondary
  text `#A0A4AD`, one accent (a warm orange such as `#FF6B35` pairs with the
  streak fire). Check text contrast ≥ 4.5:1 with a test, the way the overlay
  palette is already checked.
- Replace the monospace font with a clean sans-serif, but use **tabular
  (fixed-width) digits** for counters so a rep count going 9 → 10 does not jump
  sideways — the reason P3-12 chose monospace.

**Pages (bottom navigation, 5 tabs)**
1. **Home** — today at a glance: streak flame and count, today's planned
   workout (from Batch 4, placeholder until then), a "Quick start" button, a
   weekly goal ring (workouts done / goal), last session summary.
2. **Workouts** — exercise library (all 17 entries, filter by body area /
   equipment / hold vs reps), and later the generator and saved plans.
3. **Train** — the live camera screen (existing), restyled: large rep count,
   phase, tracking state, END SET, camera switch.
4. **Progress** — streak calendar (Batch 3; placeholder now), session history
   list with a detail page per session (sets, reps, validity, L/R range of
   motion, tempo), per-exercise trends.
5. **Profile** — account (signed-in email, sign out), settings (default camera,
   reminder time, sound/haptics on/off), data (export — Batch 3), licences
   screen (P3-17), privacy explanation, about.

**Onboarding (first launch only, 3 short screens)**
- What Kinesis does; what data stays on the phone and what syncs; how to place
  the phone (whole body in frame, head visible, one person, within about 4 m —
  from the model card's out-of-scope list).

**Skeleton redesign** *(added 2026-09-29, DECISIONS.md P3-30 addendum)*
- **The new drawing, in the new palette:**
  - filled joint circles with a soft halo;
  - tapered limbs;
  - a torso outline;
  - a head circle from the face landmarks.
- **Keep:**
  - unseen joints greyed;
  - the amber flash on a repetition that did not count (P3-23);
  - the One Euro display smoothing (display only, as now).
- **Tracking state is never conveyed by colour alone.** Shape, stroke or pattern carries it
  too, as the four overlay states and the wider attention stroke already do (rule 7 above:
  `SkeletonPaletteLegibilityTest` must still pass).
- **The drawing must never hide that a joint is not tracked.** No halo, fill or smoothing may
  make a guessed or unseen joint look measured.

**Focus zoom (auto-framing) — DISPLAY ONLY** *(added 2026-09-29, DECISIONS.md P3-30 addendum)*
- **What to frame.** Each profile names its focus landmarks, reusing `requiredLandmarks`.
  The target is the box around them plus a margin, with zoom capped at about 1.6–2×.
  - **Whole-body exercises get no zoom:** plank, push-up, side-on squat.
  - **Overhead exercises keep headroom** above the head.
- **When.**
  - **Zoom in** only after every focus landmark has been reliably visible for N frames.
  - **Zoom out** when any is lost for more than about 500 ms.
- **How it moves.**
  - Spring-smoothed, panned to centre, and clamped to the frame edge.
  - A small decaying shake (about 200 ms), during the zoom-in transition only.
  - A reduce-motion setting, and Android's animation-scale setting honoured.
- **The pipeline is unchanged.**
  - The preview and the skeleton share one transform.
  - **Nothing zoomed is ever sent to the pose model.**
  - The zoom decision uses full-frame landmarks, never the zoomed view.
- **A real camera zoom is a separate, later feature,** because it changes the pipeline the
  MM-Fit evidence rests on.
- **Testing:** the framing function is pure and unit-tested on the JVM.
- **Priority:** after the evaluation work (CLAUDE.md §21).

**Notes**
- Current navigation is a `when` with no library (P3-12). If Navigation Compose
  is added, run the §17 check; otherwise keep a hand-rolled navigator but make
  back-navigation correct.
- Settings persistence: prefer SharedPreferences (no new dependency) unless
  there is a recorded reason for DataStore.
- Keep portrait lock on the live screen and the "no restart on rotation"
  behaviour from P3-16 working on every new page.

---

## Batch 2 — Form feedback: the main feature (calibration + form engine, P6 + P8)

This batch is also the core of the final-year research work, so it gets the most care.

**Calibration (P6)**
- A short calibration flow per exercise: the user does a few normal reps
  (use a provisional number such as 5–8, clearly marked provisional — the
  MM-Fit convergence study will justify the final number).
- Build `CalibrationBaseline` (per user, per exercise, L and R separate) in the
  shared core; persist it locally; allow recalibration.
- Build `AnalysisResult`, kept separate from `RepRecord` (CLAUDE.md §15), so old
  reps can be re-analysed when rules change.

**Layer 1 — deviation rules (all exercises, Level 1/2)**
- After each completed rep: range of motion, tempo, left/right asymmetry and
  rep-to-rep consistency compared with the user's baseline. Output phrased as
  personal ("shallower than your usual", "left side moving less than right"),
  never as correct/incorrect.

**Layer 2 — technique rules (Tier A only, Level 3)**
- A small set of rules per Tier A exercise (squat, push-up, bicep curl, seated
  lateral raise, glute bridge), each with `sourceRef` and `ruleVersion`, only
  where a published threshold can be cited. **A rule with no citation does not
  ship** — flag it in DECISIONS.md and leave it out.
- The type system must keep making it impossible for Tier B/C to produce these.

**How it shows up in the app**
- During a set: a short cue after a rep when a finding fires (text on screen,
  optional voice via on-device TextToSpeech, optional haptic), using the
  existing ATTENTION overlay state as its visual slot. Never per frame.
- After a set: a summary card listing findings with their claim level and, for
  Level 3, a "source" link/label.
- Setup assistant before scoring (CLAUDE.md §10): check body fully visible,
  head visible, one person, reasonable distance and the exercise's required
  camera view; if clearly invalid, block scoring and say why.

---

## Batch 3 — Streak calendar with fire, reminders and side features

**Streak with freezes (logic in the core, extend `Streaks.kt`)**
- A day counts if at least one valid set was saved that local day (existing
  timezone/DST-aware day logic).
- **Freeze rule (owner decision):** earn 1 freeze for every 7 consecutive active
  days, hold at most 2. A single missed day consumes one freeze automatically
  and the streak continues; two missed days in a row, or none left, resets it.
  Freezes are **derived deterministically from session history**, not stored as
  mutable state, so sync and reinstalling cannot corrupt them. Pin every edge
  case with tests (first day, freeze earned exactly on day 7, cap, two misses,
  timezone boundaries).

**Calendar (Progress page)**
- Month grid, swipe between months. Active days show a **fire icon**; a day
  saved by a freeze shows a snowflake/ice icon; today outlined; future days
  dimmed. Tapping a day opens that day's sessions.
- Header: big flame with current streak, longest streak, freezes available.
- Fire intensity may grow with streak length (e.g. 3 / 7 / 30 day tiers) — pure
  decoration, no claims.

**Reminders (local only)**
- User picks days and time in Profile. Uses local notifications scheduled with
  WorkManager (already a dependency) or AlarmManager inexact alarms; reschedule
  after reboot.
- Request `POST_NOTIFICATIONS` on Android 13+ with an honest explanation; a
  refusal is an answer, not something to nag about.
- Optional smart reminder: "Your 12-day streak ends tonight" if no set today by
  the chosen time. No network, no analytics.

**Side features**
- Rest timer between sets (configurable, with sound/haptic at the end).
- Rep sound/haptic feedback toggle; voice rep counting via on-device
  TextToSpeech (no new dependency).
- Personal records per exercise (most valid reps in a set, longest hold, best
  range of motion per side).
- Weekly goal (workouts per week) with progress ring on Home.
- Achievements/badges, kept modest (first workout, 7-day streak, 100 reps…).
- **Export (P5, required anyway):** JSON and CSV export of sessions from
  Profile, via the Android share sheet; nothing uploaded.

---

## Batch 4 — Workout generator and guided workouts

**Generator (logic in the shared core, deterministic — no ML, CLAUDE.md §12)**
- Inputs: time available (e.g. 10 / 20 / 30 min), focus (full body / upper /
  lower / core), level (beginner / intermediate / advanced), equipment (none /
  dumbbells), and optionally "avoid these exercises".
- Uses only the 17-entry `ExerciseLibrary`. Respect each exercise's required
  camera view and group exercises by view so the user moves the phone as
  little as possible.
- Output: ordered list of exercises with sets, target reps or hold time, and
  rest. Same inputs + same seed → same plan (test it); a "shuffle" button
  changes the seed.
- Targets are presented as suggestions, not prescriptions; no medical claims.
- Tests: plan fits the time budget, respects equipment and focus, never
  includes an exercise twice in a row, is reproducible from its seed.

**Guided workout mode**
- Start a generated (or saved) plan from Home or Workouts. The app walks
  through it: shows the next exercise and phone-placement tip → live analysis
  for the set (existing pipeline) → rest timer → next set/exercise.
- Each set is saved through the existing `LiveSetSaver` path; the whole workout
  gets a summary at the end (time, sets, reps, holds, findings) and counts
  towards the streak.
- Save favourite plans locally; "Repeat last workout" on Home.

---

## After these batches

- P7: run MediaPipe over the MM-Fit videos (frame-shape correction applied with
  MM-Fit's own frame size) and build the evaluation harness.
- P9: personal anomaly detection, then the exercise verifier. In scope since DECISIONS.md P3-34.
- P12: the six evaluation studies and the final report.

These are what the final-year report rests on. The app features above make Kinesis a
better product and demo, but they must not push the evaluation out of the
schedule — CLAUDE.md §21 puts evaluation above Android polish.
