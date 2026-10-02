# P4 — the solo phone session, 2026-09-30

*Written 2026-09-29 at the owner's direction. A checklist, not an authority:
`docs/DECISIONS.md` wins. Its source lists are DECISIONS.md P3-19's camera-session
items (1–19), P3-24's additions and P3-29's owed curl check. The results go into
the next DECISIONS entry, not into this file.*

**Updated after the 2026-09-29 session (DECISIONS.md P3-31) and its fixes (P3-32, P3-33):**
- **Tests 1 and 2 ran on 2026-09-29.** Curls filmed sideways counted 16 of 16 and 15 of 15.
  Facing the phone, the curl counted arm swings as curls.
- **The curl is now filmed side-on** (P3-32): the curling arm nearer the phone, turning round for
  the other arm. Every curl step below says so.
- **The build to test is P3-33's:** P3-32's fixes plus the smoother weighted per unit of time.
  - Its APK's SHA-256 is `3e9a548043822214ed1cf04ad13647fbfa76fe39a184ddcbb3cd94c899262a2d`.
  - Install it only with the owner's approval, after section 0's backup, with
    `./gradlew :app:installDebug` (it keeps the data), then run section 0's build check again.
- **Once it is installed,** repeat test 1 sideways on it, then recalibrate (test 2).
- **Cool the phone first.** It sat at thermal status 3 for half of that session.

Do the items **in this order**. The heat run is last on purpose: every earlier
item should run on a phone that is not yet hot.

Everything recorded goes into **`local/p4-2026-09-30/`**. `local/` is gitignored
(P3-18) and nothing in it is ever committed — the database copies, the readout,
and above all any image or video of the owner.

---

## 0. Before the first set

Run these in **Git Bash**, not PowerShell: PowerShell's `>` rewrites binary
output as text and would corrupt the database copies.

```sh
cd /e/kinesis
S=local/p4-2026-09-30
mkdir -p $S/before $S/after

# Which build is on the phone: record its install time AND its APK's hash.
# P3-31 found an unrecorded reinstall; the hash is what identifies a build.
adb shell dumpsys package com.kinesis | grep -E "lastUpdateTime|versionName" > $S/build.txt
adb pull "$(adb shell pm path com.kinesis | tr -d '\r' | sed 's/^package://')" $S/base.apk
sha256sum $S/base.apk >> $S/build.txt
# P3-33's build is 3e9a548043822214ed1cf04ad13647fbfa76fe39a184ddcbb3cd94c899262a2d

# Back up the database first, as P3-25 and P3-29 did
for f in $(adb shell run-as com.kinesis ls databases | tr -d '\r'); do
  adb exec-out run-as com.kinesis cat databases/$f > $S/before/$f
done

# The phone's thermal state at the start
adb shell dumpsys thermalservice | grep -i "thermal status" > $S/thermal-start.txt

# The Phase B readout, left running all session in a second terminal
adb logcat -c
adb logcat -v time -s KinesisPhaseB > $S/readout.txt
```

- Close other apps. P3-24 started at thermal status 2 with a system AI widget
  using a third of a core; write down what is running.
- Open `$S/notes.md` and, for every item below, write **your own count, the
  clock time you started, and anything you saw**. Your own count is the ground
  truth for every item; the app's count is what is being checked.
- Front camera unless an item says otherwise.

---

## 1. Curls on the new build — right arm, then left arm *(owed to P3-29)*

*Done sideways on 2026-09-29 (P3-31): right 16 of 16, left 15 of 15. Repeat it on the P3-33 build,
if installed.*

**What to do.** **Side-on:** the curl's required view since P3-32. Two sets, each ended with END
SET:
- **Right side to the phone, right arm only:** 10 curls at a normal pace (about 1–2 s up, 1–2 s
  down), then 5 fast ones, as fast as you can while still going all the way up and all the way
  down.
- **Turn round, left side to the phone, left arm only:** the same, 10 normal and then 5 fast.

**Pass.**
- Each set counts **15 VALID repetitions**, `repIndex` 1 to 15, nothing counted
  above 15.
- **Every record in the right-arm set is attributed `RIGHT`, and every record in
  the left-arm set `LEFT`.** A swapped side is the one failure that shows no
  symptom on screen (PROJECT_STATUS §5, item 2).
- **The 5 fast curls count.** They are what P3-28's fast-start fix is for.

**Log.** Your counts in `notes.md`; the records come from the database copy at
the end (`reps` table: `validity`, `attributedSide`, `descentMs`, `ascentMs`).

## 2. The first P6 calibration — Bicep Curl

*The 2026-09-29 calibration was filmed facing the phone, and its baseline is not meaningful (P3-31).
Redo it sideways: RECALIBRATE replaces it.*

**What to do.**
1. Bicep Curl → RECALIBRATE → START CALIBRATION SET. **Side-on:** right side to the phone, **8
   curls with the right arm** at your usual pace. **Turn round**, left side to the phone, **8 with
   the left arm.** 8 per side is the cap and 5 the minimum. END SET.
2. **One ordinary set,** the same way: 5 curls with the right arm, turn round, 5 with the left.
3. **The arm-swing check (added after P3-31):** START SET, stand **sideways**, and **swing your arms
   for about 20 seconds without curling.** END SET. Facing the phone, this counted 5 curls on
   2026-09-29.

**Pass.**
- The baseline card shows **both arms, each with its own median and IQR**,
  marked provisional (`p6-1`).
- The library shows Bicep Curl **CALIBRATED**, and HISTORY lists the baseline.
- The ordinary set's summary shows each repetition's range of motion and tempo
  **beside the baseline's**.
- If the card says "No baseline yet", **copy its reason text exactly** into
  `notes.md`. That is itself the P3-22 finding (item 16): whether 5 valid
  repetitions per side are reachable on the phone.
- **The baseline's values look like a curl:** a usual range well under 170° and a lift well over
  one result (~70 ms). The front-on baseline read 175° and 66 ms.
- **The arm-swing check counts 0.**

**Log.** `notes.md`; the `baselines` table in the end-of-session database copy.

## 3. Lateral raise — 10, against the ≤ 36° start zone

**What to do.** **Seated**, facing the camera (the profile is seated, matching
MM-Fit; P0-4). 10 raises, both arms together, **letting the arms come right down
to your sides between raises.**

**Pass.**
- **10 VALID**, and none counted above 10.
- In the `reps` table, **every counted raise has `peak_left` and `peak_right` —
  the angle at the start position — at or below 36°**. That is P3-29's new start
  zone, set from MM-Fit subjects, now checked on a person it has never seen.
- If a raise does not count, note whether your arms were fully down before it.

*Optional:* 3 raises lowering only about halfway (arms well out from your sides)
must **not** count. That is the zone's limit (P3-29 tested 42°).

**Log.** `notes.md`; the `reps` table.

## 4. Push-ups, side-on — 8

**What to do.** Phone at floor height, side-on, whole body in frame, one side
nearer the camera. 8 push-ups, all the way down and all the way up.

**Pass.**
- The count is **8**, and **never above 8**. Push-ups now count from the near
  side (P3-28, P3-29).
- **Note every amber flash**, and whether the repetition it came with counted.
  P3-29's owner item 2: a failed far-side attempt may flash amber while the count
  is right. This is to be judged here, before anything changes.

**Log.** `notes.md`; the `reps` table (`validity` shows the failed attempts).

## 5. Sit-ups, side-on — 8

**What to do.** As push-ups: floor-height, side-on, whole body in frame. 8
sit-ups. Since DECISIONS.md P3-46 the sit-up counts on the trunk's angle (lying
back 20° or less, up 67° or more), not the hip: **recalibrate the sit-up first**
if it was calibrated before, and do 3 crunches at the end. They should not
count.

**Pass.** Count **8**, never above; amber flashes noted as in item 4.

**Log.** `notes.md`; the `reps` table.

## 6. Plank — about 30 s, against a stopwatch

**What to do.** Side-on, whole body in frame. Start a stopwatch **when you are
in position**, hold about 30 s, stop it **as you come down**, then END SET.

**Pass.** The hold's time held is **within 1 s of the stopwatch**. A gap larger
than that is a finding to record, not something to fix during the session. Hand
timing adds a few tenths at each end, and the app sees about 15 results a
second.

*Optional:* a second plank with a deliberate sag half-way (P3-19 item 5).

**Log.** The stopwatch time in `notes.md`; the `holds` table.

## 7. The book test — a short loss of tracking mid-curl *(P3-19 item 17)*

**What to do.** Side-on, right side to the phone, right arm, one set of 10 curls. On five of them, sweep
a book (a hand is too small, P3-24) between the camera and the curling arm
mid-curl: **three quick sweeps** (well under half a second) and **two slow**
(hold the book there about 1–2 s). Note in `notes.md` which curls got which.

**Pass.**
- The readout's `gaps R … (max … ms)` shows each sweep's length.
- **A gap of 500 ms or less: that curl still counts VALID** (the bridge,
  `maxBridgedGapMs = 500`, P3-23).
- **A longer gap: that curl ends as INSUFFICIENT_DATA and is not counted, and the
  next curl counts normally.**

**Log.** `readout.txt` (the gap lengths), `notes.md` (which curl was swept), the
`reps` table.

## 8. The "NOT VISIBLE" label on side-on curls *(P3-19 item 18)*

**What to do.** Turn side-on, curling arm nearer the camera, 10 curls.

**Pass.** This is an **observation, not a fix**. Write down exactly what the line
under the counter says during the set, and the count. P3-24 found it reads NOT
VISIBLE all set while the near arm is tracked and counted, because the hidden far
arm makes every frame `INVALID`. The curl is now side-on (P3-32), so this is its
normal view; the label stays a display issue. P3-8's guard requires both arms for
a profile that analyses both sides.

**Log.** `notes.md`; the `reps` table.

## 9. Back camera — 10 curls *(P3-19 item 3)*

**What to do.** Switch to the back camera (the screen will face away; read the
result afterwards). Side-on: **5 curls with the right arm, right side to the
phone; turn round; 5 with the left arm**, in one set.

**Pass.**
- **10 VALID.**
- **The first 5 attributed `RIGHT`, the last 5 `LEFT`.** The back camera's path
  has no mirroring anywhere; this is the only check that it gives the same
  anatomical sides as the front.
- The readout says `BACK camera`.

**Log.** `notes.md`; `readout.txt`; the `reps` table.

## 10. Dim light — 10 curls *(P3-19 item 8)*

**What to do.** Front camera, room lights off with one lamp or daylight behind
curtains. 10 curls, right arm, side-on (right side to the phone).

**Pass.**
- The count is **10**, none above.
- **Record the readout's `camera … fps` and `results … fps`** against item 1's
  set. P3-19 asked whether the camera's delivered rate, not MediaPipe, is the
  limit in dim light.

**Log.** `readout.txt`; `notes.md` (what the light was).

## 11. The heat run — 10 minutes, last

**What to do.** Front camera, live screen open, you in frame, moving
occasionally (a few curls every minute is enough). In a third Git Bash terminal,
log the thermal state once a minute:

```sh
for i in $(seq 1 11); do
  date +%T >> $S/thermal.txt
  adb shell dumpsys thermalservice | grep -i "thermal status" >> $S/thermal.txt
  sleep 60
done
```

**Pass.**
- **Ten minutes with no crash and no stall**, and a complete per-minute log.
- The numbers are the result, not a pass mark: the readout's `results … fps` and
  `mediapipe … ms (max …)` minute by minute, the thermal status minute by minute,
  and **the minute it first reaches status 3 ("severe")**, if it does.
- The build plan's device study names a **15-minute** run
  (`ZERO_DATASET_BUILD_PLAN.md`, evaluation table). Tonight's is 10 by the
  owner's choice. If the phone is still below status 3 at 10 minutes, carrying
  on to 15 closes that item too.

**Log.** `thermal.txt`, `readout.txt`.

## 12. Squats — optional *(P3-19 item 19)*

**What to do.** 10 squats in the view you choose (side-on, the profile's view,
loses the far knee, P3-23; face-on counted 4 of 4 in P3-24). Count them yourself.

**Pass.** Your count against the app's, none counted above yours. The view is
still the owner's decision.

**Log.** `notes.md`; the `reps` table.

---

## At the end

```sh
# Stop the readout (Ctrl+C in its terminal), then copy the database again
for f in $(adb shell run-as com.kinesis ls databases | tr -d '\r'); do
  adb exec-out run-as com.kinesis cat databases/$f > $S/after/$f
done
adb shell dumpsys thermalservice | grep -i "thermal status" > $S/thermal-end.txt
```

- Check that the copy opens (`sqlite3 $S/after/kinesis.db "PRAGMA integrity_check;"`
  should print `ok`).
- A screen recording, if one is made, stays in `$S/` and is deleted from the
  phone once the copy is checked, as P3-24 did.
- **Nothing from `local/` is committed.** The results are written up as the next
  DECISIONS.md entry, from the copies, and that is what goes into git.
