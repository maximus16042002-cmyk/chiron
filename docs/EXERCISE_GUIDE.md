# Kinesis — How to Do Each Exercise So the App Detects It

*Written 2026-09-29 for the project owner and anyone testing the app on a phone. Every number
here is read from the exercise profiles in the code (`core/analysis/.../profile/`) as they stand at
DECISIONS.md P3-30. **All thresholds are provisional**, and they change only through a DECISIONS.md
entry.*

This guide says what the app needs to **see** in order to count a repetition or time a hold. It is
not technique coaching, and Kinesis is not a medical or physiotherapy tool.

---

## Part 1 — Before any exercise

### Set the phone up

1. **Prop the phone up; don't hold it.** A moving camera moves every angle. Use portrait, the way the
   live screen locks.
2. **Use the view the exercise needs**, given for each exercise below. The app measures angles in the
   flat camera image, so the wrong view gives the wrong angle.
   - **Front-on:** you face the phone.
   - **Side-on:** your side faces the phone, which is at right angles to you. For exercises marked
     *near side only*, the side nearer the phone is the one measured.
3. **Get the body parts the exercise needs in frame, with some room to spare.** Each exercise lists
   them under "Keep in view". Leave space for where your arms or legs will move: at the top of a
   press your hands are the first thing to leave the frame.
4. **Stay inside what the pose model was made for** (its model card, DECISIONS.md P3-17, lines
   3823–3826):
   - **one person** in the picture;
   - **your head visible**;
   - **within about 4 metres** of the phone.
5. **Light:** normal room light. Dim light is not yet tested; it is on tomorrow's list.
6. **Camera:** the front camera is the default and the one tested most. The back camera works, but its
   left and right have not yet been checked on a person.

### How a repetition is counted

The app runs a small state machine on one joint angle, for each side:

1. **Start in the start position.** **No pause is needed:** the machine moves from idle to the start
   the first time your joint's angle is measured inside the start zone (`RepFsm.kt` lines 150–158).
   - The angle it sees is filtered first (an outlier gate, then smoothing, `TemporalConfig.kt` lines
     138–142), so it trails you slightly.
   - Pausing at the start is fine: the clock restarts there, so the pause belongs to no repetition
     (`RepFsm.kt` lines 160–162, 331–340).
   - Walking into frame mid-movement counts nothing until you pass through the start zone.
2. **Move past the far position.** Turning back before it is *not counted*: the app records it as
   a turned-back attempt, and the screen flashes **amber**.
3. **Come back to the start position.** Returning completes the repetition.
4. **Each repetition must take longer than a minimum and less than a maximum,** listed per exercise.
   Too quick and it is not counted; too slow and it is recorded as incomplete.
5. **Gaps longer than half a second end a repetition.** A frame is unmeasured when you are out of
   view, the joint is not tracked, or the outlier gate rejects a sudden jump.
   - **Mid-repetition:** a gap of up to **500 ms** since the last measured frame is bridged, and the
     repetition carries on. Nothing is filled in (`TemporalConfig.kt` line 147, `RepFsm.kt` lines
     285–292).
   - **Longer, and the repetition ends as insufficient data:** recorded, not counted, and shown in
     **amber** (`RepFsm.kt` lines 293–301, `LiveAnalysisBridge.kt` lines 121–125).
   - **At the start position,** a longer gap drops the machine back to idle with no record
     (`RepFsm.kt` lines 288–290).
   - **Either way, the next repetition counts normally** once you are measured back in the start
     zone.
6. **Full range, every time.** Half repetitions are what the thresholds are there to refuse.

### What the screen tells you

- **The count.** Only repetitions the app counted as valid are in it.
- **The phase** you are in, and the **tracking state**.
- **Amber:** the last repetition did not count.
- **"NOT VISIBLE"** can show during side-on exercises even while counting works. The far limb is
  hidden, and the label says so. This is a known display issue (P3-24), not a counting fault.
- **The skeleton**, drawn over you. If it doesn't sit on your body, the app cannot measure you.

### Calibrating an exercise (optional)

On the exercise's screen, press **CALIBRATE**, then do one ordinary set the way you usually do it:

| Kind of exercise | What calibration needs |
|---|---|
| Both sides together | At least **5 counted repetitions**; the first 8 are used. |
| One arm or leg at a time | At least **5 with each arm or leg**. |
| Near side only | At least **5 on the side facing the phone**. |
| Holds | **One valid hold with at least 10 seconds held inside the band.** Time outside the band does not count (`CalibrationPolicy.kt` line 63; `Calibration.kt` lines 188, 193). |

These numbers are provisional. The P3-30 study suggests a range-of-motion baseline needs more
repetitions than 5–8.

### How the angles are measured

| Angle | Measured between | Reads |
|---|---|---|
| **Knee** | hip, knee, ankle | 180° with the leg straight |
| **Elbow** | shoulder, elbow, wrist | 180° with the arm straight |
| **Shoulder** | hip, shoulder, elbow | about 0° with the arm down by your side, about 90° out to the side, about 180° overhead |
| **Hip** | shoulder, hip, knee | 180° when your trunk and thigh are in one straight line |

---

## Part 2 — Repetition exercises

### 1. Squat — Tier A, both legs together

- **Camera:** **side-on**, phone at about hip height, whole body in frame. Standing.
- **Watches:** the knee angle on each side.
- **Start:** standing with your knees at **155° or straighter** (nearly straight legs).
- **One repetition:** squat down until your knees reach **95° or less** (thighs close to parallel with
  the floor), then stand back up to **155° or straighter**.
- **Counts as:** one squat is one repetition. **Both knees must complete it.**
- **Time per repetition:** 0.6–15 s.
- **Keep in view:** both hips, knees and ankles.
- **Known problem:** side-on, the far leg is hidden behind the near one and often not tracked, so
  squats may not count (P3-23). Facing the phone, the app tracked both knees and counted 4 of 4
  (P3-24), but the knee angles there are projections. **Which view the squat uses is still an open
  decision.** For now, film side-on and check the count.

### 1a. Goblet Squat — a variant of the Squat

Everything as the squat: same view, same angles, same calibration. *"Hold the weight at your chest and
keep your elbows inside your knees."*

### 2. Bicep Curl — Tier A, one arm at a time

- **Camera:** **side-on, with the curling arm nearer the phone.** Upper body at least from hips to
  head in frame. Standing. *Changed from front-on in DECISIONS.md P3-32.*
- **Do one arm at a time:** curl with the near arm, then **turn round** so your other side faces
  the phone and curl with that arm. You can turn round within one set.
- **Don't film it facing the phone.** Facing the phone, the app saw the elbow snap from straight to
  about 0° as the forearm came towards the camera, and it counted arm swings as curls: 7 too many in
  one set, and 5 with no curls done at all (P3-31).
- **Watches:** the elbow angle of the curling arm.
- **Start:** arm hanging, elbow at **137° or straighter**.
- **One repetition:** curl until the elbow reaches **68° or less**, then lower it back to **137° or
  straighter**.
- **Counts as:** **each arm's curl is one repetition.**
- **Time per curl:** 0.5–20 s.
- **Keep in view:** shoulders, elbows and wrists. The far arm is hidden behind you side-on, so the
  line under the counter may say **"NOT VISIBLE"** while the near arm counts. That is a known
  display issue.
- **Evidence:** **sideways on the phone:** 16 of 16 (right arm, fast curls included) and 15 of 15
  (left arm) (P3-31). MM-Fit films curls front-on, so its curl results no longer count as evidence
  for this view.

### 3. Push-up — Tier A, both arms together, **near side only**

- **Camera:** **side-on**, phone **at floor level**, whole body in frame from head to feet.
- **Watches:** the elbow angle of the arm **nearer the phone**.
- **Start:** at the top with your arms **locked out, 155° or straighter**.
- **One repetition:** lower until the near elbow reaches **95° or less**, then push back up to **155°
  or straighter**.
- **Counts as:** one push-up, one repetition.
- **Time per repetition:** 0.6–15 s.
- **Keep in view:** both shoulders and hips, and the near arm's elbow and wrist.
- **Known problem:** many people's arms read below 155° at the top in the flat image, so their
  push-ups don't count. MM-Fit's unseen people: 110 of 201; two of them counted almost nothing.
  **Lock out fully at the top.** A personal start zone that fixes this is built in the core but not
  yet in the app.

### 4. Lateral Raise — Tier A, both arms together, **seated**

- **Camera:** **front-on**, facing the phone. **Seated** on a chair, as MM-Fit's are.
- **Watches:** the shoulder angle on each side.
- **Start:** arms **down by your sides, 36° or less**.
- **One repetition:** raise both arms out to the sides until they reach **77° or more** (close to
  horizontal), then lower them **all the way back to 36° or less**.
- **Counts as:** one raise of both arms, one repetition. **Both arms must complete it.**
- **Time per repetition:** 0.6–12 s.
- **Keep in view:** both hips, shoulders and elbows.
- **Tip:** let your arms come right down between raises. Stopping halfway down merges two raises into
  one.
- **Evidence:** MM-Fit's unseen people: 146 of 150.

### 5. Glute Bridge — Tier A, both legs together

- **Camera:** **side-on**, phone **at floor level**. Lying on your back, knees bent, feet flat.
- **Watches:** the hip angle.
- **Start:** hips down, hip angle **145° or less**.
- **One repetition:** lift your hips until your body is nearly a straight line from shoulders to knees,
  **167° or more**, then lower back to **145° or less**.
- **Counts as:** one bridge, one repetition. Both sides must complete it.
- **Time per repetition:** 0.6–15 s.
- **Keep in view:** both shoulders, hips and knees.
- **Evidence:** none yet. MM-Fit has no bridges and it has not been tried on the phone. The pose model
  is also known to be weaker on lying poses.

### 6. Lunge — Tier B, both legs together

- **Camera:** **side-on**, whole body in frame. Standing.
- **Watches:** the knee angle on each side.
- **Start:** standing, knees at **160° or straighter**.
- **One repetition:** step into the lunge until **both** knees reach **105° or less**, then return to
  standing, **160° or straighter**.
- **Counts as:** one lunge, one repetition, whichever leg steps forward.
- **Time per repetition:** 0.7–15 s.
- **Keep in view:** both hips, knees and ankles.
- **Known problem:** the rear leg is expected to track badly side-on. When it isn't tracked, the lunge
  is not counted. Unproven.

### 6a. Reverse Lunge — a variant of the Lunge

Everything as the lunge. *"Step backwards into the lunge rather than forwards, and lower under
control."*

### 7. Standing Hip Abduction — Tier B, one leg at a time

- **Camera:** **front-on**, facing the phone, whole body in frame. Standing.
- **Watches:** the hip angle of the moving leg.
- **Start:** standing straight, hip angle **167° or more**.
- **One repetition:** lift one leg out to the side until the hip angle reaches **145° or less**, then
  bring it back to **167° or more**.
- **Counts as:** each leg's lift is one repetition.
- **Time per repetition:** 0.5–10 s.
- **Keep in view:** both shoulders, hips and knees.
- **Evidence:** none yet.

### 8. Standing Knee Raise — Tier B, one leg at a time

- **Camera:** **side-on**, whole body in frame. Standing.
- **Watches:** the hip angle of the moving leg.
- **Start:** standing straight, hip angle **164° or more**.
- **One repetition:** raise one knee until the hip angle reaches **106° or less** (thigh close to
  horizontal), then lower it to **164° or more**.
- **Counts as:** each knee raise is one repetition.
- **Time per repetition:** 0.5–10 s.
- **Keep in view:** both shoulders, hips and knees.
- **Known problem:** the far leg is partly hidden side-on, so it may give fewer records than the near
  leg. Untested.

### 9. Sit-up — Tier B, **near side only**

- **Camera:** **side-on**, phone **at floor level**. Lying on your back.
- **Watches:** the hip angle on the side **nearer the phone**.
- **Start:** lying back, hip angle **132° or more**.
- **One repetition:** sit up until the hip angle reaches **83° or less**, then lie back down to **132°
  or more**.
- **Counts as:** one sit-up, one repetition.
- **Time per repetition:** 0.5–12 s.
- **Keep in view:** both shoulders and hips, and the near knee.
- **Known problem:** many people don't open their hips to 132° when they lie back, so their sit-ups
  don't count. MM-Fit's unseen people: 108 of 189; two of them counted nothing. **Lie all the way
  back.** The same personal start-zone fix as the push-up is waiting.

### 10. Shoulder Press — Tier B, both arms together, **seated**

- **Camera:** **front-on**, facing the phone. **Seated.** Leave room above your head for your hands.
- **Watches:** the shoulder angle on each side.
- **Start:** weights at your shoulders, upper arms roughly out to the sides, shoulder angle **108° or
  less**.
- **One repetition:** press up until your arms reach **152° or more**, then lower back to **108° or
  less**.
- **Counts as:** one press, one repetition. Both arms must complete it.
- **Time per repetition:** 0.6–12 s.
- **Keep in view:** both hips, shoulders and elbows. The shoulder angle is used rather than the
  elbow's because the hands leave the frame at the top.
- **Evidence:** reliable on MM-Fit: 348 of 360, 86% of sets exact, on P3-27's held-out workouts,
  which include people also used in development.

### 11. Bent-over Row — Tier B, **near side only**, hinged

- **Camera:** **side-on**. Standing, bent forward at the hips with your trunk towards horizontal.
- **Watches:** the elbow angle of the arm **nearer the phone**. The far arm is behind your body.
- **Start:** arms hanging, near elbow **152° or straighter**.
- **One repetition:** pull the weight up towards your hip until the near elbow reaches **83° or
  less**, then lower to **152° or straighter**.
- **Counts as:** one row, one repetition.
- **Time per repetition:** 0.5–12 s.
- **Keep in view:** both shoulders and hips, and the near arm's elbow and wrist.
- **Evidence:** none that counts. MM-Fit filmed its rows at an angle, not side-on.

### 12. Lying Leg Raise — Tier B, both legs together

- **Camera:** **side-on**, phone **at floor level**. Lying on your back, legs straight.
- **Watches:** the hip angle.
- **Start:** legs flat, hip angle **160° or more**.
- **One repetition:** raise both straight legs until the hip angle reaches **108° or less**, then lower
  to **160° or more**.
- **Counts as:** one raise, one repetition. Both sides must complete it.
- **Time per repetition:** 0.6–12 s.
- **Keep in view:** both shoulders, hips and knees.
- **Evidence:** none yet.

---

## Part 3 — Holds

A hold is timed, not counted.

- **Time counts only while you are inside the band** given for each hold.
- **Leaving the band is a "break";** coming back into it starts the time again. The app records how
  many breaks there were.
- **You must be visible for at least half of the attempt,** or the hold is not measured.
- **Under 3 seconds held in total, it is "too short".**
- **Press END SET** when you finish.

### 13. Plank — Tier C

- **Camera:** **side-on**, phone **at floor level**, whole body from head to feet.
- **Watches:** the hip angle, your body line.
- **Hold:** body straight from shoulders through hips to knees. **The band is 180° ± 20°**, so a
  hip angle of **160° or more** counts as holding. Letting your hips sag or pike below 160° is a
  break.
- **Keep in view:** both shoulders, hips, knees and ankles.
- **Evidence:** it has run on the phone; its timing is to be checked against a stopwatch tomorrow.

### 14. Wall Sit — Tier C

- **Camera:** **side-on**, whole body in frame. Your back against a wall.
- **Watches:** the knee angle.
- **Hold:** knees near a right angle. **The band is 90° ± 15°: 75° to 105° counts as holding.** Your
  hips rising as your legs tire (knee angle over 105°) is a break.
- **Keep in view:** both hips, knees and ankles.
- **Evidence:** it has run on the phone; not yet checked.

### 15. Side Plank — Tier C

- **Camera:** **front-on**: the phone faces your chest or back, *not* your side. This is on purpose.
  In a side plank your body line faces sideways, so a camera in front of you sees the whole line and
  a hip drop. A camera at your side would look along the line and see nothing.
- **Watches:** the hip angle, your body line.
- **Hold:** body straight on one forearm. **The band is 180° ± 15°, so 165° or more counts as
  holding.** Your hip dropping below that is a break.
- **Keep in view:** both shoulders, hips and knees.
- **Evidence:** none yet.

---

## Quick reference

| # | Exercise | View | Posture | Joint | Start | Far | How it counts |
|---|---|---|---|---|---|---|---|
| 1 | Squat (+ Goblet) | side-on | standing | knee | ≥ 155° | ≤ 95° | both legs together |
| 2 | Bicep curl | **side-on**, curling arm nearer; turn round for the other | standing | elbow | ≥ 137° | ≤ 68° | each arm |
| 3 | Push-up | side-on, floor level | prone | elbow | ≥ 155° | ≤ 95° | near arm |
| 4 | Lateral raise | front-on | **seated** | shoulder | ≤ 36° | ≥ 77° | both arms together |
| 5 | Glute bridge | side-on, floor level | on back | hip | ≤ 145° | ≥ 167° | both together |
| 6 | Lunge (+ Reverse) | side-on | standing | knee | ≥ 160° | ≤ 105° | both knees together |
| 7 | Hip abduction | front-on | standing | hip | ≥ 167° | ≤ 145° | each leg |
| 8 | Knee raise | side-on | standing | hip | ≥ 164° | ≤ 106° | each leg |
| 9 | Sit-up | side-on, floor level | on back | hip | ≥ 132° | ≤ 83° | near side |
| 10 | Shoulder press | front-on | **seated** | shoulder | ≤ 108° | ≥ 152° | both arms together |
| 11 | Bent-over row | side-on | hinged | elbow | ≥ 152° | ≤ 83° | near arm |
| 12 | Leg raise | side-on, floor level | on back | hip | ≥ 160° | ≤ 108° | both together |
| 13 | Plank | side-on, floor level | prone | hip | hold ≥ 160° (180 ± 20) | — | time held |
| 14 | Wall sit | side-on | against a wall | knee | hold 75–105° (90 ± 15) | — | time held |
| 15 | Side plank | **front-on** | on one side | hip | hold ≥ 165° (180 ± 15) | — | time held |

**Tested enough to rely on:** the bicep curl filmed **sideways** (on the phone), and the shoulder press
and lateral raise (on MM-Fit).
**Known weak until the personal start zone reaches the app:** push-up, sit-up.
**Untested or unproven:** everything else. Treat their counts as something to check, not something
to trust.
