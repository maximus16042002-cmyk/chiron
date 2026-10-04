# Kinesis — What Is Finished, Partly Done and Not Started

*Updated 2026-10-05, at the end of DECISIONS.md P3-59. A plain summary for the project owner. The
decisions and their reasons are in `docs/DECISIONS.md`; the full account is `docs/HANDOVER.md`,
and the technical snapshot `docs/PROJECT_STATUS.md`. Where this file disagrees with DECISIONS.md,
DECISIONS.md is right.*

---

## At a glance

The build plan has 13 phases, P0–P12. One (P11) was cut, so **12 are in scope**.

| Status | Phases | Count |
|---|---|---|
| ✅ Finished | P0, P1, P2, P3, P5, P7 | 6 |
| 🟡 Partly done | P4, P6, P8, P9, P10, P12 | 6 |
| ✂️ Cut | P11 | — |

**Rough overall figure:** a finished phase counts 1 and a partly done one ½, so 6 + 6 × ½ = **9 of
12, about 75%**. Phases are not equal in size.

**Why not 88–90%:** another AI agent's work (P3-52 to P3-58, 4–5 October) counted P8, P9 and P10
as finished. P3-59 checked that work and restored the count. None of the three is finished:
- Layer 1's messages and the exercise verifier do not ship;
- Layer 2's rules wait for the phone's side-on check;
- nothing built since P3-47 has been seen on the phone.

The engineering is far ahead of the evaluation. What is left is mostly the phone session and the
write-up.

---

## ✅ Finished

| Phase | What it delivered |
|---|---|
| **P0 Architecture** | The documents, contracts, claim levels and module boundaries. |
| **P1 Shared core** | Kotlin Multiplatform types; left and right kept apart by type (`BilateralSignal`); the pose adapter; the JS facade. |
| **P2 Kinematics** | Joint angles, the real-time filter chain, ring buffers, resampling. |
| **P3 Profiles and rep engine** | The two-sided repetition tracker, the hold analyser, 15 profiles and 2 variants (17 library entries). |
| **P5 Records and export** | Per-repetition storage (Room v3), and a JSON/CSV export identical on JVM and JS, checked against the phone's real database. |
| **P7 MM-Fit pipeline** | MediaPipe over MM-Fit's video, every set aligned, and the evaluation harness. |

---

## 🟡 Partly done

### P4 — The phone check
- **Done:** the live screen on both cameras, side-on curl counting, saving, sync, Health Connect,
  the export, and the P3-46 build installed with Room v3.
- **Owed:**
  - install the current build (after a backup);
  - **check the setup gate first** — it now stops counting when the camera view is wrong, so if it
    misjudges the real setup, nothing counts;
  - tests 3–12 of `docs/P4_TOMORROW.md`;
  - screenshots of every new page;
  - a second person.

### P6 — Calibration
- **Built.** The only baseline on the phone is from curls filmed facing it, so the curl must be
  recalibrated side-on.
- **The count is your decision:** 5–8 repetitions now; the study says about 12–20.

### P8 — Form engine
- **Layer 1** (personal "unusual for you" messages): built; it failed its one held-out test, so it
  stays out of the app by your decision (P3-42).
- **Layer 2** (six cited technique rules): built, and waiting for the phone's side-on check and your
  decision.
- P3-53 put both on the set summary; P3-59 took them off again.

### P9 — Machine learning
- **Layer 4** (personal anomaly detection): studied; a negative result, not shipped.
- **The exercise verifier:** built by the other agent (P3-52).
  - Mean accuracy 74% over the four development people (57–89%).
  - On three of the four exercises it can cover, some people's correct repetitions were warned
    33–100% of the time (P3-59).
  - **Not shipped.** Its one held-out run is still yours to approve, ideally after a shipping rule
    is fixed.

### P10 — The app
- **Complete in code:** five pages, onboarding, calendar and streak freezes, records, goal,
  achievements, reminders, rest timer, cues and speech, the workout generator, guided workouts,
  trend charts, lifting speed per side, the setup gate, the frame-rate warning, and the restyled
  skeleton.
- **None of it has been seen on the phone yet.**

### P12 — Evaluation and write-up
- **Measured so far:**
  - repetition counting on six unseen people (curls 163/163, lateral raise 146/150, push-ups
    110/201, sit-ups 108/189 on the hip driver and 146/189 on the trunk driver);
  - the personalisation study on the development people;
  - the verifier on the development people;
  - JVM/JS parity.
- **Owed:**
  - device performance and pose reliability (phone);
  - consolidating the studies;
  - the report, with plain limitations: no technique accuracy figure exists, Layer 1 failed its
    held-out test, and Layer 4's result is null.

---

## ✂️ Cut

**P11, the Windows web app** (DECISIONS.md P0-2). The JS target survives for the parity study.

---

## Quality

- **Tests:** 3,069 test executions, 0 failing (P3-59). Every test is synthetic: none has seen a
  camera frame.
- **Guards:**
  - left and right are never averaged;
  - Tier B/C exercises cannot make a technique claim;
  - no network use beyond Firebase sync;
  - per-frame work stays constant through a session.

  Several guards were checked by deliberately breaking the code ("mutation checks").
- **What the tests could not catch:** P3-59 found three defects that every test had passed. The
  setup gate used the wrong unit, the zoom misdrew the skeleton, and the screen recomposed every
  frame. In each case the test's fixture shared the code's mistake. The phone is the check that
  remains.
