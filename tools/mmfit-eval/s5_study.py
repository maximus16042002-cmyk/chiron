"""S5: cross-session baseline stability, development subjects A and B only - DECISIONS.md P3-30
(the protocol, in its owner addendum) and P3-40 (the run).

"Sessions of unknown spacing": MM-Fit gives no dates, so nothing here says how a baseline ages with
time. The scores are `PersonalisationStudy.kt`'s (`s5.csv`): the app's own calibrator on each
session's first set, and Layer 1's personal z on every used repetition of the same subject's other
sessions. The same-session figures are S2's own (`scored.csv`, unplanted rows), and so are the cuts:
S2's c* per exercise and feature, recomputed here by S2's own function on S2's own rows.

Measures, per subject, exercise, feature and pair of sessions:
- the flag rate under the product rule, |z| > 2, and at S2's c*;
- the centre's shift, |median of the other session - baseline median| / max(baseline robust SD,
  floor). Since z = (x - median) / max(SD, floor) is linear in x, this is |median z| of the
  other session's repetitions, and is computed so.

Reported per subject, A and B apart, with no pooled figure and no interval (P3-30).

Usage: python s5_study.py <study dir>
"""
import csv
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from personalisation_study import num, operating_point, read  # noqa: E402

SUBJECTS = ["A", "B"]
S5_EXERCISES = ["shoulder_press", "bicep_curl", "lateral_raise"]


def measures(zs, cut):
    z = np.asarray(zs, float)
    a = np.abs(z)
    at_cut = float(np.mean(a > cut)) if np.isfinite(cut) else float("nan")
    return float(np.mean(a > 2.0)), at_cut, float(abs(np.median(z))), len(z)


def pct(x):
    return "  -  " if not np.isfinite(x) else f"{100 * x:4.0f}%"


def summary(values):
    v = [x for x in values if np.isfinite(x)]
    if not v:
        return "        -        "
    return f"{np.median(v):5.2f} [{min(v):5.2f}-{max(v):5.2f}]"


def summary_pct(values):
    v = [x for x in values if np.isfinite(x)]
    if not v:
        return "       -        "
    return f"{pct(np.median(v))} [{pct(min(v)).strip()}-{pct(max(v)).strip()}]"


def main(study):
    scored = read(os.path.join(study, "scored.csv"))
    s5 = read(os.path.join(study, "s5.csv"))
    sessions = read(os.path.join(study, "sessions.csv"))

    # S2's operating points, by S2's own function on S2's own rows.
    unplanted = [dict(r, zp=num(r["zPersonal"])) for r in scored if r["deviation"] == "none"]
    by = defaultdict(list)
    for r in unplanted:
        by[(r["exercise"], r["feature"])].append(r)
    cuts = {k: operating_point(rows, "zp") for k, rows in by.items()}

    cross = defaultdict(list)
    for r in s5:
        z = num(r["zPersonal"])
        if np.isfinite(z):
            cross[(r["subject"], r["exercise"], r["feature"], r["baselineWorkout"], r["otherWorkout"])].append(z)
    same = defaultdict(list)
    for r in unplanted:
        if r["subject"] in SUBJECTS and r["exercise"] in S5_EXERCISES and np.isfinite(r["zp"]):
            same[(r["subject"], r["exercise"], r["feature"], r["workout"])].append(r["zp"])

    pair_rows = []
    table = defaultdict(lambda: {"cross": [], "same": []})
    for (s, e, f, b, o), zs in sorted(cross.items()):
        m = measures(zs, cuts.get((e, f), float("nan")))
        table[(s, e, f)]["cross"].append(m)
        pair_rows.append([s, e, f, b, o, m[3], f"{m[0]:.6f}", f"{m[1]:.6f}", f"{m[2]:.6f}"])
    for (s, e, f, w), zs in sorted(same.items()):
        m = measures(zs, cuts.get((e, f), float("nan")))
        table[(s, e, f)]["same"].append(m)
        pair_rows.append([s, e, f, w, "same session", m[3], f"{m[0]:.6f}", f"{m[1]:.6f}", f"{m[2]:.6f}"])

    out = ["S5 - cross-session baseline stability, development subjects A and B only. SESSIONS OF UNKNOWN SPACING.",
           "MM-Fit gives no dates: this is how far one day's baseline sits from another day's repetitions, for two people.",
           "Every flag is on a repetition assumed normal, so each rate is an upper bound on false alarms.",
           "Per feature: the median over pairs (or over sessions) and its range. c* is S2's cut for that exercise and feature.",
           ""]
    out.append("Calibrated baseline sessions (the first set of the exercise):")
    for s in SUBJECTS:
        for e in S5_EXERCISES:
            rows = [r for r in sessions if r["subject"] == s and r["exercise"] == e]
            ok = [r["workout"] for r in rows if r["outcome"] == "calibrated"]
            out.append(f"  {s} {e:15} {len(ok)} of {len(rows)}: {', '.join(ok)}")
    for s in SUBJECTS:
        out.append(f"\n=== Subject {s}")
        for e in S5_EXERCISES:
            out.append(f"  {e}")
            out.append(f"    {'feature':11} {'c*':>5} | {'|z|>2 cross':>17} {'|z|>2 same':>17} | {'at c* cross':>17} {'at c* same':>17} |"
                       f" {'shift cross':>18} {'shift same':>18} | pairs sessions")
            for f in sorted({k[2] for k in table if k[0] == s and k[1] == e}):
                t = table[(s, e, f)]
                c, sm = t["cross"], t["same"]
                cut = cuts.get((e, f), float("nan"))
                out.append(f"    {f:11} {cut:5.2f} | {summary_pct([m[0] for m in c]):>17} {summary_pct([m[0] for m in sm]):>17} |"
                           f" {summary_pct([m[1] for m in c]):>17} {summary_pct([m[1] for m in sm]):>17} |"
                           f" {summary([m[2] for m in c]):>18} {summary([m[2] for m in sm]):>18} | {len(c):5} {len(sm):8}")
    out.append("\nEvery pair's figures are in s5_pairs.csv.")
    with open(os.path.join(study, "s5_pairs.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["subject", "exercise", "feature", "baselineWorkout", "otherWorkout", "repetitions", "rateAbsZOver2", "rateAtCStar", "centreShift"])
        w.writerows(pair_rows)
    text = "\n".join(out) + "\n"
    with open(os.path.join(study, "s5_report.txt"), "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: python s5_study.py <study dir>")
    main(sys.argv[1])
