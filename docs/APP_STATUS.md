# Chiron (Kinesis) — App Status

*Written 2026-10-14, from the repository at commit `cbf066b` and a read-only copy of the owner's
phone database taken the same day (`local/backup-2026-10-14/`, not in git). Not an authority: where
it disagrees with `docs/DECISIONS.md`, that is right. Every claim below says where it comes from.*

## 1. The answer

**The app is complete in code and partly proven on the phone.**

| Measure | Figure | Source |
|---|---|---|
| Features built and tested | All planned app features exist | Code; 3,423 test executions, 0 failed (P3-83) |
| Exercises with a **counted** repetition or valid hold on the owner's phone | **5 of 15** | Phone database, section 4 |
| Exercises with **no saved record at all** on the phone | **7 of 15** | Phone database, section 4 |
| Overall | **About 90% of the app** | Judgement: all code done, device evidence for a third of the exercises |

The 90% is a judgement, not a measurement. The remaining 10% is device evidence, not code.

## 2. What the app does (built, in the code)

Source: `app/src/main/java/com/kinesis/` and the shared engine `core/analysis/src/commonMain/`.

**Counting and measuring**
- 15 exercise profiles and 2 variants (goblet squat, reverse lunge), in three tiers.
- Live camera counting with MediaPipe Pose, left and right kept separate.
- A skeleton overlay showing the angle being counted and a flash on each counted repetition (P3-76).
- A verdict for every attempt: counted, or the reason it was not (P3-78).
- A camera-view check that **advises but does not block** (P3-68, a relaxation of CLAUDE.md §10).
- A frame-rate warning, and lifting speed per side.

**Personalisation**
- Calibration: 10–15 valid repetitions a side (P3-83).
- Push-ups counted from the user's own start position once calibrated (P3-82).
- Technique rules from cited sources, shown as "Form notes" for Tier A (P3-74).

**Training and history**
- Five pages: Home, Workouts, Train, Progress, Profile. Onboarding on first launch.
- Calendar, streaks with freezes, records, weekly goal, six achievements.
- Workout generator and guided workouts (prepare, live, rest, next); favourites and "repeat last".
- Per-exercise trend charts with left and right as two lines.
- Rest timer, tone / vibration / spoken counts (off by default), local reminders.

**Data**
- Local database per account (Room, version 3). Sets and holds survive the app being killed.
- Optional cloud sync of session records (Firebase sign-in, Firestore), with a forgot-password option.
- Health Connect (steps, calories, heart rate), JSON and CSV export, licence notices screen.
- Backup is off (`allowBackup="false"`), so session data does not go to a cloud backup.

## 3. Built but not in the app

Source: code search of `app/src/main`, 2026-10-14.

| Component | State | Why |
|---|---|---|
| Exercise verifier (P9) | **In the app as an experimental check, off by default** (P3-85): a Profile switch, then one line on the set summary | Warned on too many correct repetitions for some people (P3-59), so it is opt-in and labelled as a guess |
| Personal anomaly detection (Layer 4) | Built and studied; **no app code uses it** | Negative result: flagged 7–30% of normal repetitions (P3-38) |
| Layer 1 "unusual for you" messages | Built; **not shown** | Failed its held-out test (P3-42) |

`MovementContextTracker`, which P3-59 left running for nothing, now feeds the experimental check
(P3-85).

## 4. What the owner's phone has recorded, by exercise

Source: `kinesis.db` copy, 2026-10-14. 111 sessions, 474 repetition records, 6 hold records.
"Counted" means a record marked VALID. This shows what was **saved**, which is not the same as what
was tried: empty sets are not saved (P3-70).

| Exercise | Sets | Records | Counted (VALID) | Last set | Read |
|---|---|---|---|---|---|
| Squat | 49 | 106 | 28 | 10-13 | Counts. Near knee since P3-68 |
| Bicep curl | 38 | 290 | 138 | 10-13 | Counts. Many extra "turned back" records |
| Push-up | 7 | 18 | 4 | 10-07 | Counts a little. Before P3-82's start-position fix |
| Lateral raise | 6 | 11 | 4 | 10-07 | Counts a little |
| Lunge | 3 | 48 | 6 | 10-10 | Counts a little. Near knee since P3-71 |
| Sit-up | 2 | 1 | **0** | 10-08 | **Never counted** |
| Plank | 4 | holds: 4 | holds valid: **0** | 10-07 | **Never credited** (body tracked 12% of one attempt) |
| Wall sit | 2 | holds: 2 | holds valid: **0** | 09-26 | **Never a valid hold** |
| Glute bridge | 0 | — | — | — | **No saved record** |
| Hip abduction | 0 | — | — | — | **No saved record** |
| Knee raise | 0 | — | — | — | **No saved record** |
| Leg raise | 0 | — | — | — | **No saved record** |
| Row | 0 | — | — | — | **No saved record** |
| Shoulder press | 0 | — | — | — | **No saved record** |
| Side plank | 0 | — | — | — | **No saved record** |

**The owner said they checked every exercise and it worked.** The database does not contain a saved
set for the seven exercises marked above. Either they were tried and nothing saved (an empty set is
discarded), or they were not tried. This file cannot tell which. It should be settled on the phone.

**What these counts are not:** they are not accuracy. A "counted" record is what the app decided; this
file has no record of how many the person actually did, so it says nothing about whether the count was
right (CLAUDE.md §22).

## 5. What is left, for the app

1. **Prove the remaining exercises on the phone**, with the real count written down for each: the
   seven with no record, and the plank, wall sit and sit-up, which have never worked there.
2. **Recalibrate the curl side-on.** The only saved baseline is from front-on curls.
3. **Look at the experimental exercise check once:** turn it on in Profile, do a push-up set, and
   check its card on the summary (P3-85; not yet seen on the device).
4. Small: the Android 16 `targetSdk` change (needs the phone), and the empty `:desktop` module.

## 6. Build on the phone now

- **P3-83's build**, installed 2026-10-14 after a verified backup (P3-84): SHA-256 `9a43a351…f140`,
  from commit `1597322`. targetSdk 35.
- Cold launch about 1.7 s once Android has compiled the APK (four runs: 1.7–2.0 s).
- Phone data intact before and after: 111 sessions, 474 repetitions, 6 holds, 1 baseline.
- Previous APK and a full data backup: `local/backup-2026-10-14/` (not in git).
