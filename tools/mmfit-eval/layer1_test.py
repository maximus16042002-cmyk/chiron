"""The frozen Layer 1 setting's test - DECISIONS.md P3-42.

After Decision 3 selected nothing (P3-41), the owner approved one new setting, chosen on the
development subjects and fixed before the held-out people were read, to be tested on them once:

- threshold 3; range-of-motion floors derived from the development subjects - shoulder press 5.2 deg,
  lateral raise 3.8 deg, bicep curl 3.0 deg; time 70 ms, left - right 3 deg, consistency 2 deg as now;
- only the messages that met Decision 3's false-alarm criterion on the development subjects under it
  (SHIPPED below); everything else is held back.

It passes when, on the held-out people:
  1. every shipped feature that has held-out data has a mean over subjects of at most 5% and no
     subject above 10% (Decision 3's criterion);
  2. the 30% range cut is detected in 80% or more (mean over subjects) for the shoulder press and the
     lateral raise.
If it passes, Layer 1 ships with exactly SHIPPED; if not, Layer 1 stays out of the app (the owner's
words). A shipped feature with no held-out data is not tested, and is held back.

Run it first on the development study (where the setting was chosen, so it passes by construction:
a check of this script), then once on the held-out study.

Usage: python layer1_test.py <study dir>
"""
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from decision3 import IQR_PER_SD, num, pct, rates, read  # noqa: E402

THRESHOLD = 3.0
ROM_FLOORS = {"shoulder_press": 5.2, "lateral_raise": 3.8, "bicep_curl": 3.0}
TIME_FLOOR = 70.0
SHIPPED = {
    "shoulder_press": ["rom_left", "rom_right", "lift_rep", "lower_rep", "asym", "cons_left", "cons_right"],
    "lateral_raise": ["rom_left", "rom_right", "lift_rep", "lower_rep"],
    "bicep_curl": ["lift_left", "lift_right", "lower_left", "lower_right"],
}
SENSITIVITY_EXERCISES = ["shoulder_press", "lateral_raise"]
FA_MEAN, FA_MAX, SENSITIVITY = 0.05, 0.10, 0.80


def z_of(row):
    """The frozen setting's z. Left - right and consistency keep today's spread and floor, so the core's own."""
    if row["centre"] == "":
        return num(row["zCore"])
    value, centre, iqr = num(row["value"]), num(row["centre"]), num(row["iqr"])
    if not (np.isfinite(value) and np.isfinite(centre) and np.isfinite(iqr)):
        return float("nan")
    floor = ROM_FLOORS[row["exercise"]] if row["feature"].startswith("rom_") else TIME_FLOOR
    return (value - centre) / max(iqr / IQR_PER_SD, floor)


def main(study):
    rows = [r for r in read(os.path.join(study, "decision3.csv")) if r["exercise"] in ROM_FLOORS]
    sessions = read(os.path.join(study, "decision3_sessions.csv"))
    subjects = sorted({r["subject"] for r in sessions})
    out = [f"P3-42: the frozen Layer 1 setting on {study}",
           f"Subjects: {', '.join(subjects)}. MM-Fit's repetitions are assumed normal: every rate is an upper bound on false alarms.",
           "Calibrated sessions (first set) with later sets to score:"]
    for e in ROM_FLOORS:
        ok = [s for s in sessions if s["exercise"] == e and s["calibrated"] == "true" and int(s["laterSets"]) > 0]
        tried = [s for s in sessions if s["exercise"] == e]
        out.append(f"  {e:15} {len(ok)} of {len(tried)} sessions: " + ", ".join(f"{s['subject']}/{s['workout']}" for s in ok))

    passed = True
    tested = []
    out.append(f"\nShipped features (threshold {THRESHOLD:g}): mean over subjects, highest subject, and per subject")
    for e, features in SHIPPED.items():
        for f in features:
            per = defaultdict(list)
            for r in rows:
                if r["exercise"] == e and r["feature"] == f and r["deviation"] == "none":
                    z = z_of(r)
                    if np.isfinite(z):
                        per[r["subject"]].append(abs(z) > THRESHOLD)
            by, m, mx = rates(per)
            if not by:
                out.append(f"  {e:15} {f:11}  no data - NOT TESTED, held back")
                continue
            ok = m <= FA_MEAN and mx <= FA_MAX
            passed &= ok
            tested.append((e, f))
            out.append(f"  {e:15} {f:11} {pct(m)} {pct(mx)}  " + "  ".join(f"{s}:{pct(v).strip()} (n={len(per[s])})" for s, v in sorted(by.items()))
                       + ("" if ok else "   <- FAILS"))
    out.append("\nSensitivity: the 30% range cut, detected when any range-of-motion feature is flagged")
    for e in SENSITIVITY_EXERCISES:
        hit = defaultdict(bool)
        for r in rows:
            if r["exercise"] == e and r["deviation"] == "rom30" and r["feature"].startswith("rom_"):
                z = z_of(r)
                if np.isfinite(z):
                    key = (r["subject"], r["repKey"])
                    hit[key] = hit[key] or abs(z) > THRESHOLD
        per = defaultdict(list)
        for (s, _), v in hit.items():
            per[s].append(v)
        by, m, _ = rates(per)
        ok = bool(by) and m >= SENSITIVITY
        passed &= ok
        out.append(f"  {e:15} {pct(m)}  " + "  ".join(f"{s}:{pct(v).strip()} (n={len(per[s])})" for s, v in sorted(by.items()))
                   + ("" if ok else "   <- FAILS" if by else "   no data - FAILS"))

    out.append("\nHeld back, reported alongside only:")
    for e in ROM_FLOORS:
        for f in sorted({r["feature"] for r in rows if r["exercise"] == e} - set(SHIPPED[e])):
            per = defaultdict(list)
            for r in rows:
                if r["exercise"] == e and r["feature"] == f and r["deviation"] == "none":
                    z = z_of(r)
                    if np.isfinite(z):
                        per[r["subject"]].append(abs(z) > THRESHOLD)
            by, m, mx = rates(per)
            out.append(f"  {e:15} {f:11} {pct(m)} {pct(mx)}")
    out.append(f"\nVERDICT: {'PASS - Layer 1 ships with exactly the tested shipped features' if passed and tested else 'FAIL - Layer 1 stays out of the app'}")
    text = "\n".join(out) + "\n"
    with open(os.path.join(study, "layer1_test_report.txt"), "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: python layer1_test.py <study dir>")
    main(sys.argv[1])
