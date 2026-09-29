"""The personalisation study's statistics, S1-S4, development subjects only - DECISIONS.md P3-30.

The protocol was committed before this ran (P3-30, "The study: the protocol"). The data is
`PersonalisationStudy.kt`'s: the shared core's own calibrator, Layer 1 features and comparators, run
over the development workouts. This only does statistics - no movement decision is made here.

Every result is per subject and pooled. There are four development subjects: no pooled interval here
is precise, and the report says so beside each one.

Usage: python personalisation_study.py <study dir>
"""
import csv
import os
import sys
from collections import defaultdict

import numpy as np

SEED = 20260929
BOOTSTRAP = 2000
TARGET = 0.05
GRID = np.round(np.arange(0.0, 20.0001, 0.01), 2)
FLOORS = {"rom": 3.0, "lift": 70.0, "lower": 70.0, "asym": 3.0}
EXERCISES = ["shoulder_press", "bicep_curl", "lateral_raise", "pushup", "situp"]


def floor_of(feature):
    return FLOORS[feature.split("_")[0]]


def read(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def num(x):
    return float(x) if x != "" else float("nan")


def robust(values):
    v = np.asarray(values, dtype=float)
    q1, med, q3 = np.percentile(v, [25, 50, 75])  # NumPy's default: type 7, as the core (P3-22)
    return med, q3 - q1


# --- S1: nested random-effects ICC ----------------------------------------------------------------

def henderson(groups):
    """Henderson method I for y_ijk = mu + a_i + b_ij + e_ijk. `groups`: {subject: {session: values}}.
    Returns the three variance components (negatives kept; the caller truncates) or None."""
    a = len(groups)
    sessions = [(i, j, np.asarray(v, float)) for i, s in groups.items() for j, v in s.items() if len(v)]
    b = len(sessions)
    y = np.concatenate([v for _, _, v in sessions])
    n_total = len(y)
    if a < 2 or n_total <= b:
        return None
    grand = y.mean()
    n_i = {i: sum(len(v) for ii, _, v in sessions if ii == i) for i in groups}
    mean_i = {i: np.concatenate([v for ii, _, v in sessions if ii == i]).mean() for i in groups}
    ss_a = sum(n_i[i] * (mean_i[i] - grand) ** 2 for i in groups)
    ss_b = sum(len(v) * (v.mean() - mean_i[i]) ** 2 for i, _, v in sessions)
    ss_e = sum(((v - v.mean()) ** 2).sum() for _, _, v in sessions)
    ms_a = ss_a / (a - 1)
    ms_e = ss_e / (n_total - b)
    sum_nij2_over_ni = sum(len(v) ** 2 / n_i[i] for i, _, v in sessions)
    sum_nij2 = sum(len(v) ** 2 for _, _, v in sessions)
    k2 = (sum_nij2_over_ni - sum_nij2 / n_total) / (a - 1)
    k3 = (n_total - sum(n ** 2 for n in n_i.values()) / n_total) / (a - 1)
    if b > a:
        ms_b = ss_b / (b - a)
        k1 = (n_total - sum_nij2_over_ni) / (b - a)
        var_b = (ms_b - ms_e) / k1
    else:
        var_b = 0.0
    var_e = ms_e
    var_a = (ms_a - ms_e - k2 * var_b) / k3
    return var_a, var_b, var_e


def icc(groups):
    comps = henderson(groups)
    if comps is None:
        return None
    raw = comps
    var_a, var_b, var_e = (max(0.0, c) for c in comps)
    total = var_a + var_b + var_e
    if total <= 0:
        return None
    return {"subject": var_a / total, "session": var_b / total, "components": (var_a, var_b, var_e),
            "negative": [name for name, c in zip(("subject", "session", "rep"), raw) if c < 0]}


def s1(reps, out):
    out.append("\n=== S1: variability - how much people differ, against how much they vary")
    out.append("  ICC_subject = var_subject / (var_subject + var_session + var_rep); Henderson method I;")
    out.append(f"  95% interval from {BOOTSTRAP} resamples of the four subjects (seed {SEED}). WITH FOUR SUBJECTS")
    out.append("  THESE INTERVALS ARE WIDE AND NOT PRECISE; 'distinct' = share of resamples with only 1-2 distinct subjects.")
    rng = np.random.default_rng(SEED)
    data = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for r in reps:
        v = num(r["value"])
        if np.isfinite(v):
            data[(r["exercise"], r["feature"])][r["subject"]][r["workout"]].append(v)
    rows = []
    for (exercise, feature), groups in sorted(data.items(), key=lambda kv: (EXERCISES.index(kv[0][0]), kv[0][1])):
        point = icc(groups)
        subjects = sorted(groups)
        boots, few = [], 0
        for _ in range(BOOTSTRAP):
            draw = rng.choice(len(subjects), size=len(subjects), replace=True)
            if len(set(draw)) <= 2:
                few += 1
            resampled = {f"{subjects[d]}#{k}": groups[subjects[d]] for k, d in enumerate(draw)}
            got = icc(resampled)
            if got is not None:
                boots.append(got["subject"])
        lo, hi = (np.percentile(boots, [2.5, 97.5]) if boots else (float("nan"), float("nan")))
        if point is None:
            out.append(f"  {exercise:15s} {feature:11s} too few subjects or repetitions for an ICC")
            continue
        va, vb, ve = point["components"]
        neg = f"  (negative estimate set to 0: {', '.join(point['negative'])})" if point["negative"] else ""
        out.append(f"  {exercise:15s} {feature:11s} ICC_subject {point['subject']:5.2f} [{lo:4.2f}, {hi:4.2f}]  "
                   f"ICC_session {point['session']:4.2f}  sd subject/session/rep {np.sqrt(va):6.1f} {np.sqrt(vb):6.1f} {np.sqrt(ve):6.1f}  "
                   f"distinct<=2 {few / BOOTSTRAP:4.0%}{neg}")
        rows.append((exercise, feature, point["subject"], lo, hi, point["session"]))
        for s in subjects:
            values = np.concatenate([np.asarray(v, float) for v in groups[s].values()])
            med, iqr = robust(values)
            out.append(f"      {s:4s} {len(groups[s]):2d} sessions {len(values):4d} reps  median {med:8.1f}  IQR {iqr:7.1f}  robust sd {iqr / 1.349:7.1f}")
    return rows


# --- S2: false alarms at an equal operating point -----------------------------------------------

def rates(rows, column, c):
    """Per-subject flag rate at cut c, over rows whose z is measured."""
    per = defaultdict(list)
    for r in rows:
        z = r[column]
        if np.isfinite(z):
            per[r["subject"]].append(abs(z) > c)
    return {s: float(np.mean(v)) for s, v in per.items() if v}, {s: len(v) for s, v in per.items()}


def operating_point(rows, column):
    """The smallest c on the grid at which the mean over subjects of their flag rates is at most 5%."""
    zs = defaultdict(list)
    for r in rows:
        if np.isfinite(r[column]):
            zs[r["subject"]].append(abs(r[column]))
    if not zs:
        return float("nan")
    arrays = [np.sort(np.asarray(v)) for v in zs.values()]
    for c in GRID:
        mean_rate = np.mean([1.0 - np.searchsorted(a, c, side="right") / len(a) for a in arrays])
        if mean_rate <= TARGET:
            return float(c)
    return float("nan")


def s2(scored, acsm, out):
    out.append("\n=== S2: false alarms - flag rates on MM-Fit's scored repetitions (sets after the calibrating first set)")
    out.append("  MM-Fit's repetitions are ASSUMED normal: fatigue, miscounted sets and free choice of weight all inflate")
    out.append("  these rates, so each is an UPPER BOUND on false alarms, not a false-alarm rate.")
    out.append("  c* = the cut on |z| at which the mean of the subjects' flag rates is 5% - the equal operating point.")
    unplanted = [dict(r, zp=num(r["zPersonal"]), zq=num(r["zPopulation"])) for r in scored if r["deviation"] == "none"]
    by = defaultdict(list)
    for r in unplanted:
        by[(r["exercise"], r["feature"])].append(r)
    cuts = {}
    for (exercise, feature), rows in sorted(by.items(), key=lambda kv: (EXERCISES.index(kv[0][0]), kv[0][1])):
        cp, cq = operating_point(rows, "zp"), operating_point(rows, "zq")
        cuts[(exercise, feature)] = (cp, cq)
        pers, _ = rates(rows, "zp", cp) if np.isfinite(cp) else ({}, {})
        pop, _ = rates(rows, "zq", cq) if np.isfinite(cq) else ({}, {})
        pers2, n = rates(rows, "zp", 2.0)
        pop2, _ = rates(rows, "zq", 2.0)
        subjects = sorted(set(pers) | set(pop) | set(pers2) | set(pop2))

        def cut(c):
            return f"{c:5.2f}" if np.isfinite(c) else "  >20"  # no cut on the grid reaches 5%
        out.append(f"  {exercise:15s} {feature:11s} personal c*={cut(cp)}  population c*={cut(cq)}   "
                   f"({len(subjects)} subjects, {sum(n.values())} scored reps with a personal z)")

        def fmt(d):
            return "  ".join(f"{s}:{d[s]:5.1%}" if s in d else f"{s}:  -  " for s in subjects)

        def spread(d):
            v = list(d.values())
            return f"max {max(v):5.1%} range {max(v) - min(v):5.1%}" if v else "-"
        out.append(f"      at c*   personal   {fmt(pers)}   {spread(pers)}")
        out.append(f"      at c*   population {fmt(pop)}   {spread(pop)}")
        out.append(f"      |z|>2   personal   {fmt(pers2)}   mean {np.mean(list(pers2.values())) if pers2 else float('nan'):5.1%}")
        out.append(f"      |z|>2   population {fmt(pop2)}   mean {np.mean(list(pop2.values())) if pop2 else float('nan'):5.1%}")
    out.append("  The ACSM tempo rule (Level 3, cited, fixed at its own value), per subject, on scored repetitions:")
    per = defaultdict(lambda: defaultdict(list))
    for r in acsm:
        per[r["exercise"]][r["subject"]].append(int(r["flagged"]))
    for exercise in EXERCISES:
        if exercise in per:
            d = {s: np.mean(v) for s, v in per[exercise].items()}
            out.append(f"      {exercise:15s} " + "  ".join(f"{s}:{d[s]:5.1%} (n={len(per[exercise][s])})" for s in sorted(d))
                       + f"   mean {np.mean(list(d.values())):5.1%}")
    return cuts


# --- S3: sensitivity to planted deviations ------------------------------------------------------

def s3(scored, cuts, out):
    out.append("\n=== S3: sensitivity - planted deviations in scored repetitions only (SYNTHETIC: arithmetic on records,")
    out.append("  not movements anyone made; not a real-world detection claim). Detected at each feature's S2 cut c*.")
    out.append("  'curve': detection when one common cut over the target features gives a 5% mean-subject false-alarm rate.")
    planted = defaultdict(lambda: defaultdict(list))
    baseline = defaultdict(lambda: defaultdict(list))
    for r in scored:
        r = dict(r, zp=num(r["zPersonal"]), zq=num(r["zPopulation"]))
        if r["deviation"] == "none":
            baseline[r["exercise"]][r["repKey"]].append(r)
        else:
            planted[(r["exercise"], r["deviation"])][r["repKey"]].append(r)

    def targets(deviation, rows, primary=True):
        if deviation.startswith("rom"):
            return [r for r in rows if r["feature"].startswith("rom_")]
        if deviation.startswith("tempo"):
            return [r for r in rows if r["feature"].startswith(("lift_", "lower_"))]
        if deviation.startswith("side"):
            return [r for r in rows if (r["feature"] == "asym") == primary]
        return []

    def at_operating_point(rows, column, cut_index):
        """Detected when any target feature passes its own S2 cut. None when none is measured."""
        hits = []
        for r in rows:
            c = cuts.get((r["exercise"], r["feature"]), (np.nan, np.nan))[cut_index]
            if np.isfinite(r[column]) and np.isfinite(c):
                hits.append(abs(r[column]) > c)
        return any(hits) if hits else None

    def largest(rows, column):
        """Under one common cut, a repetition is detected exactly when its largest |z| passes it."""
        zs = [abs(r[column]) for r in rows if np.isfinite(r[column])]
        return max(zs) if zs else None

    def by_subject(reps_by_key, deviation, primary, fn):
        per = defaultdict(list)
        for rows in reps_by_key.values():
            value = fn(targets(deviation, rows, primary))
            if value is not None:
                per[rows[0]["subject"]].append(value)
        return per

    curve_rows = ["exercise,deviation,target,comparator,cut,falseAlarm,detection"]
    for (exercise, deviation) in sorted(planted, key=lambda k: (EXERCISES.index(k[0]), k[1])):
        reps = planted[(exercise, deviation)]
        for primary in ([True, False] if deviation.startswith("side") else [True]):
            target = "asymmetry" if deviation.startswith("side") and primary else (
                "that side's rom" if deviation.startswith("side") else "")
            line = f"  {exercise:15s} {deviation + (' (' + target + ')' if target else ''):26s}"
            for column, index, name in (("zp", 0, "personal"), ("zq", 1, "population")):
                hits = by_subject(reps, deviation, primary, lambda rows: at_operating_point(rows, column, index))
                d = {s: float(np.mean(v)) for s, v in hits.items() if v}
                # The curve: mean-subject false-alarm and detection rates as one common cut varies.
                clean = {s: np.asarray(v) for s, v in by_subject(baseline[exercise], deviation, primary, lambda rows: largest(rows, column)).items()}
                dirty = {s: np.asarray(v) for s, v in by_subject(reps, deviation, primary, lambda rows: largest(rows, column)).items()}
                at5 = float("nan")
                if clean and dirty:
                    for c in GRID:
                        far = np.mean([np.mean(v > c) for v in clean.values()])
                        det = np.mean([np.mean(v > c) for v in dirty.values()])
                        if round(c * 100) % 10 == 0:
                            curve_rows.append(f"{exercise},{deviation},{target or 'features'},{name},{c:.2f},{far:.4f},{det:.4f}")
                        if np.isnan(at5) and far <= TARGET:
                            at5 = det
                pooled = np.mean(list(d.values())) if d else float("nan")
                line += f" | {name} {pooled:5.1%} [" + " ".join(f"{s}:{d[s]:4.0%}" for s in sorted(d)) + f"] curve@5% {at5:5.1%}"
            out.append(line)
    return curve_rows


# --- S4: baseline convergence -------------------------------------------------------------------

def s4(reps, out):
    out.append("\n=== S4: how many calibration repetitions until the baseline settles")
    out.append("  Settled at N: for every N' from N to 20 that has data, at least 80% of the subject's starts have")
    out.append("  |median_N' - median_ref| <= 0.5 s_ref and IQR_N' / IQR_ref within [1/1.5, 1.5]. Reference: the session's")
    out.append("  used repetitions of the exercise. IQRs are compared after the same floor (1.349 x the feature's floor),")
    out.append("  so a zero IQR does not divide. Near N = the session's total, agreement is partly mechanical.")
    series = defaultdict(list)
    for r in reps:
        if r["feature"].startswith(("rom_", "lift_", "lower_")):
            series[(r["subject"], r["workout"], r["exercise"], r["feature"])].append((int(r["order"]), int(r["mmfitSet"]), num(r["value"])))
    passes = defaultdict(lambda: defaultdict(list))  # (subject, exercise, feature) -> N -> [bool]
    # EXPLORATORY, not in the protocol: the median's condition alone, to see which half settles later.
    centre_only = defaultdict(lambda: defaultdict(list))
    for (subject, workout, exercise, feature), rows in series.items():
        rows.sort()
        values = np.array([v for _, _, v in rows])
        sets_ = [s for _, s, _ in rows]
        values_ok = np.isfinite(values)
        values, sets_ = values[values_ok], [s for s, ok in zip(sets_, values_ok) if ok]
        if len(values) < 3:
            continue
        floor = floor_of(feature)
        med_ref, iqr_ref = robust(values)
        s_ref = max(iqr_ref / 1.349, floor)
        iqr_ref_f = max(iqr_ref, 1.349 * floor)
        starts = sorted({sets_.index(s) for s in set(sets_)})
        for start in starts:
            for n in range(3, 21):
                if start + n > len(values):
                    break
                med, iqr = robust(values[start:start + n])
                ratio = max(iqr, 1.349 * floor) / iqr_ref_f
                centre = abs(med - med_ref) <= 0.5 * s_ref
                ok = centre and (1 / 1.5) <= ratio <= 1.5
                passes[(subject, exercise, feature)][n].append(ok)
                centre_only[(subject, exercise, feature)][n].append(centre)

    def settle(by_n):
        ns = sorted(by_n)
        for n in ns:
            if all(np.mean(by_n[m]) >= 0.8 for m in ns if m >= n):
                return n
        return None
    settled = {key: settle(by_n) for key, by_n in passes.items()}
    centre_settled = {key: settle(by_n) for key, by_n in centre_only.items()}
    rng = np.random.default_rng(SEED)
    for exercise in EXERCISES:
        keys = sorted(k for k in settled if k[1] == exercise)
        if not keys:
            continue
        out.append(f"  {exercise}:")
        numeric = []
        for key in keys:
            s = settled[key]
            by_n = passes[key]
            at = "  ".join(f"N={n}: {np.mean(by_n[n]):4.0%}" for n in (5, 8) if n in by_n)
            shown = f"{s:2d}" if s is not None else f">{max(by_n)}"
            c = centre_settled[key]
            centre = f"{c:2d}" if c is not None else f">{max(by_n)}"
            out.append(f"      {key[0]:4s} {key[2]:11s} settles at {shown}   (pass rate {at}; up to N={max(by_n)}, {len(by_n[3])} starts)"
                       f"   [exploratory: median alone at {centre}]")
            numeric.append(s if s is not None else max(by_n) + 1)
        per_subject = defaultdict(list)
        for key, value in zip(keys, numeric):
            per_subject[key[0]].append(value)
        subject_max = {s: max(v) for s, v in per_subject.items()}
        boot = [np.median(rng.choice(list(subject_max.values()), size=len(subject_max), replace=True)) for _ in range(BOOTSTRAP)]
        out.append(f"      largest over subjects and metrics: {max(numeric)} (a value past a subject's data means it never settled there); "
                   f"per subject (their largest): " + " ".join(f"{s}:{v}" for s, v in sorted(subject_max.items()))
                   + f"; median over subjects {np.median(list(subject_max.values())):.1f}, subject-bootstrap 95% "
                   f"[{np.percentile(boot, 2.5):.1f}, {np.percentile(boot, 97.5):.1f}] - four subjects, not precise")


def main(study_dir):
    reps = read(os.path.join(study_dir, "reps.csv"))
    scored = read(os.path.join(study_dir, "scored.csv"))
    acsm = read(os.path.join(study_dir, "acsm.csv"))
    sessions = read(os.path.join(study_dir, "sessions.csv"))
    out = ["P3-30 personalisation study - DEVELOPMENT SUBJECTS ONLY (A, B, C, S19). Per subject and pooled."]
    out.append("Sessions whose first set of the exercise did not calibrate (left out of S2/S3):")
    for exercise in EXERCISES:
        rows = [s for s in sessions if s["exercise"] == exercise]
        failed = [s for s in rows if s["outcome"] != "calibrated"]
        out.append(f"  {exercise:15s} {len(rows) - len(failed)} of {len(rows)} calibrated; "
                   f"scored reps {sum(int(s['scoredReps']) for s in rows)}; not calibrated: "
                   + ", ".join(f"{s['subject']}/{s['workout']}" for s in failed))
    s1(reps, out)
    cuts = s2(scored, acsm, out)
    curve_rows = s3(scored, cuts, out)
    with open(os.path.join(study_dir, "s3_curves.csv"), "w", newline="") as f:
        f.write("\n".join(curve_rows) + "\n")
    s4(reps, out)
    text = "\n".join(out) + "\n"
    with open(os.path.join(study_dir, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)


if __name__ == "__main__":
    main(sys.argv[1])
