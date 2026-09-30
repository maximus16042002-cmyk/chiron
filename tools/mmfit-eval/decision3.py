"""Decision 3: Layer 1's threshold and floors, by the selection rule fixed before any run - DECISIONS.md
P3-30 (the rule, in its owner addendum) and P3-41 (its readings, and the run).

Development subjects only. The data is `PersonalisationStudy.kt`'s `decision3.csv` over the current
engine's development exports: S2's design (each session's first set calibrates through the app's
calibrator, its later sets are scored), every scored repetition with the baseline statistic it is
judged against, unplanted and with S3's 30% range cut. `reps.csv` supplies the within-session
spreads that candidates (c) and (d) are derived from.

The candidates (P3-30):
  (a) as now: threshold 2; floors 3 deg range of motion, 70 ms time.
  (b) threshold 3.
  (c) a range-of-motion floor per exercise from the development subjects.
  (d) the personal spread shrunk towards a within-person spread, k = 8.
  then b + c, b + d, c + d, b + c + d - in that order, the simplest first.

A candidate qualifies when, at its own threshold and floors:
  1. for every one of the 14 judged features, the mean over subjects of the personal flag rate on
     scored repetitions is 5% or less, and no subject's is above 10%;
  2. for each of the three exercises, the 30% range cut is detected in 80% or more of the planted
     repetitions (the mean over subjects).
The simplest candidate that qualifies is chosen. If none qualifies, that is reported and the work
stops: the criteria are not relaxed.

Usage: python decision3.py <study dir>
"""
import csv
import math
import os
import sys
from collections import defaultdict

import numpy as np

RULE_EXERCISES = ["shoulder_press", "bicep_curl", "lateral_raise"]
ALONGSIDE_EXERCISES = ["pushup", "situp"]
RULE_FEATURES = {
    "shoulder_press": ["rom_left", "rom_right", "lift_rep", "lower_rep"],
    "lateral_raise": ["rom_left", "rom_right", "lift_rep", "lower_rep"],
    "bicep_curl": ["rom_left", "rom_right", "lift_left", "lift_right", "lower_left", "lower_right"],
}
ROM_FLOOR, TIME_FLOOR = 3.0, 70.0
IQR_PER_SD = 1.349
K = 8
MIN_SESSION_REPS = 5  # P3-41: a within-session spread needs the calibrator's own minimum
FA_MEAN, FA_MAX, SENSITIVITY = 0.05, 0.10, 0.80
CANDIDATES = [  # name, threshold, derived ROM floor, shrinkage - in the rule's order
    ("(a) as now", 2.0, False, False),
    ("(b) threshold 3", 3.0, False, False),
    ("(c) derived ROM floor", 2.0, True, False),
    ("(d) shrunk spread", 2.0, False, True),
    ("b + c", 3.0, True, False),
    ("b + d", 3.0, False, True),
    ("c + d", 2.0, True, True),
    ("b + c + d", 3.0, True, True),
]


def read(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def num(x):
    return float(x) if x != "" else float("nan")


def robust_sd(values):
    q1, q3 = np.percentile(np.asarray(values, float), [25, 75])  # type 7, as the core
    return (q3 - q1) / IQR_PER_SD


def within_session(reps):
    """Per (subject, exercise, feature): the median over sessions of the within-session robust SD."""
    per_session = defaultdict(list)
    for r in reps:
        v = num(r["value"])
        if np.isfinite(v):
            per_session[(r["subject"], r["exercise"], r["feature"], r["workout"])].append(v)
    by_subject = defaultdict(list)
    for (s, e, f, _), values in per_session.items():
        if len(values) >= MIN_SESSION_REPS:
            by_subject[(s, e, f)].append(robust_sd(values))
    return {k: float(np.median(v)) for k, v in by_subject.items()}


def derived_floors(medians):
    """(c): per exercise, the median over (subject, side) pairs of the range-of-motion medians, to 0.1 deg."""
    floors = {}
    for e in RULE_EXERCISES + ALONGSIDE_EXERCISES:
        pairs = [v for (s, ex, f), v in medians.items() if ex == e and f.startswith("rom_")]
        if pairs:
            floors[e] = round(float(np.median(pairs)), 1)
    return floors


def s_within(medians, exercise, feature, subject):
    """(d): the root mean square of the OTHER development subjects' medians for this exercise and feature."""
    others = [v for (s, e, f), v in medians.items() if e == exercise and f == feature and s != subject]
    return math.sqrt(float(np.mean(np.square(others)))) if others else float("nan")


def z_of(row, candidate, floors, medians):
    _, _, derived, shrink = candidate
    value, centre, iqr, count = num(row["value"]), num(row["centre"]), num(row["iqr"]), num(row["count"])
    if not (np.isfinite(value) and np.isfinite(centre) and np.isfinite(iqr)):
        return float("nan")
    rom = row["feature"].startswith("rom_")
    floor = (floors.get(row["exercise"], ROM_FLOOR) if derived else ROM_FLOOR) if rom else TIME_FLOOR
    s = iqr / IQR_PER_SD
    if shrink:
        sw = s_within(medians, row["exercise"], row["feature"], row["subject"])
        if np.isfinite(sw):
            w = count / (count + K)
            s = math.sqrt(w * s * s + (1.0 - w) * sw * sw)
    return (value - centre) / max(s, floor)


def rates(flags_by_subject):
    r = {s: float(np.mean(v)) for s, v in flags_by_subject.items() if v}
    return r, (float(np.mean(list(r.values()))) if r else float("nan")), (max(r.values()) if r else float("nan"))


def pct(x):
    return "   -  " if not np.isfinite(x) else f"{100 * x:5.1f}%"


def evaluate(rows, candidate, floors, medians, exercises, features_of):
    name, threshold, _, _ = candidate
    fa = {}
    for e in exercises:
        for f in features_of(e):
            per = defaultdict(list)
            for r in rows:
                if r["exercise"] == e and r["feature"] == f and r["deviation"] == "none":
                    z = z_of(r, candidate, floors, medians)
                    if np.isfinite(z):
                        per[r["subject"]].append(abs(z) > threshold)
            fa[(e, f)] = rates(per)
    sens = {}
    for e in exercises:
        per_rep = defaultdict(bool)
        seen = set()
        for r in rows:
            if r["exercise"] == e and r["deviation"] == "rom30" and r["feature"].startswith("rom_"):
                z = z_of(r, candidate, floors, medians)
                if np.isfinite(z):
                    key = (r["subject"], r["repKey"])
                    seen.add(key)
                    per_rep[key] = per_rep[key] or abs(z) > threshold
        per = defaultdict(list)
        for key in seen:
            per[key[0]].append(per_rep[key])
        sens[e] = rates(per)
    return fa, sens


def main(study):
    rows = read(os.path.join(study, "decision3.csv"))
    reps = read(os.path.join(study, "reps.csv"))
    medians = within_session(reps)
    floors = derived_floors(medians)

    # Guard: candidate (a) recomputed from the exported statistics must be the core's own z.
    worst = 0.0
    for r in rows:
        if r["centre"] != "":
            z = z_of(r, CANDIDATES[0], floors, medians)
            zc = num(r["zCore"])
            if np.isfinite(z) and np.isfinite(zc):
                worst = max(worst, abs(z - zc) / max(1.0, abs(zc)))
    assert worst < 1e-3, f"candidate (a) does not reproduce the core's z: worst relative difference {worst}"

    out = ["Decision 3 - Layer 1's threshold and floors, by the rule fixed in P3-30's addendum. DEVELOPMENT SUBJECTS ONLY.",
           "MM-Fit's repetitions are assumed normal: every flag rate is an upper bound on false alarms.",
           f"Candidate (a) reproduces the core's own z (worst relative difference {worst:.1e}).",
           "",
           "(c)'s derived range-of-motion floors (median over subject-side pairs of the within-session robust SD):"]
    for e, f in floors.items():
        out.append(f"  {e:15} {f:5.1f} deg" + ("   (outside the rule)" if e in ALONGSIDE_EXERCISES else ""))
    out.append("(d)'s within-person spreads, per exercise and feature and subject left out, are computed from the other subjects.")
    subjects = sorted({r["subject"] for r in rows})
    chosen = None
    csv_rows = []
    for candidate in CANDIDATES:
        name, threshold, _, _ = candidate
        fa, sens = evaluate(rows, candidate, floors, medians, RULE_EXERCISES, lambda e: RULE_FEATURES[e])
        fa_ok = all(np.isfinite(m) and m <= FA_MEAN and mx <= FA_MAX for (_, m, mx) in fa.values())
        sens_ok = all(np.isfinite(m) and m >= SENSITIVITY for (_, m, _) in sens.values())
        qualifies = fa_ok and sens_ok
        out.append(f"\n=== {name}: threshold {threshold:g}. " + ("QUALIFIES" if qualifies else "does not qualify")
                   + f" (false alarms {'met' if fa_ok else 'NOT met'}; sensitivity {'met' if sens_ok else 'NOT met'})")
        out.append(f"    {'exercise':15} {'feature':12} {'mean':>6} {'max':>6}   " + "  ".join(f"{s:>6}" for s in subjects))
        for (e, f), (per, m, mx) in fa.items():
            mark = "" if (m <= FA_MEAN and mx <= FA_MAX) else "  <- fails"
            out.append(f"    {e:15} {f:12} {pct(m)} {pct(mx)}   " + "  ".join(pct(per.get(s, float('nan'))) for s in subjects) + mark)
            csv_rows.append([name, "false alarms", e, f, f"{m:.6f}", f"{mx:.6f}"] + [f"{per.get(s, float('nan')):.6f}" for s in subjects])
        for e, (per, m, _) in sens.items():
            mark = "" if m >= SENSITIVITY else "  <- fails"
            out.append(f"    {e:15} {'30% ROM cut':12} {pct(m)} {'':6}   " + "  ".join(pct(per.get(s, float('nan'))) for s in subjects) + mark)
            csv_rows.append([name, "sensitivity", e, "rom30", f"{m:.6f}", ""] + [f"{per.get(s, float('nan')):.6f}" for s in subjects])

        # Alongside, outside the rule: left - right and consistency at this threshold (their spreads and
        # floors are no candidate's), and the push-up and sit-up under the whole candidate.
        along = defaultdict(lambda: defaultdict(list))
        for r in rows:
            if r["deviation"] == "none" and r["exercise"] in RULE_EXERCISES and (r["feature"] == "asym" or r["feature"].startswith("cons_")):
                z = num(r["zCore"])
                if np.isfinite(z):
                    along[(r["exercise"], r["feature"])][r["subject"]].append(abs(z) > threshold)
        for (e, f), per in sorted(along.items()):
            p, m, mx = rates(per)
            out.append(f"    (alongside) {e:15} {f:12} mean {pct(m)} max {pct(mx)}")
        near_features = lambda e: sorted({r["feature"] for r in rows if r["exercise"] == e and r["centre"] != ""})
        fa2, sens2 = evaluate(rows, candidate, floors, medians, ALONGSIDE_EXERCISES, near_features)
        for (e, f), (per, m, mx) in fa2.items():
            out.append(f"    (alongside) {e:15} {f:12} mean {pct(m)} max {pct(mx)}   " + "  ".join(f"{s}:{pct(v).strip()}" for s, v in sorted(per.items())))
        for e, (per, m, _) in sens2.items():
            out.append(f"    (alongside) {e:15} 30% ROM cut  mean {pct(m)}")
        if qualifies and chosen is None:
            chosen = name
    out.append("\n=== Selected: " + (chosen if chosen else "NONE QUALIFIES - reported, and the work stops (P3-30: the criteria are not relaxed)"))
    with open(os.path.join(study, "decision3_results.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["candidate", "criterion", "exercise", "feature", "mean", "max"] + subjects)
        w.writerows(csv_rows)
    text = "\n".join(out) + "\n"
    with open(os.path.join(study, "decision3_report.txt"), "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: python decision3.py <study dir>")
    main(sys.argv[1])
