"""The anomaly study's statistics, A1-A4, development subjects only - DECISIONS.md P3-34, P3-36, P3-37.

The protocol was committed before this ran (P3-34, amended by P3-36; the harness's readings of it in
P3-37). The scores are `AnomalyStudy.kt`'s: the shared core's own forest, distance, calibrator and
Layer 1, run over the development workouts. This only does statistics - no movement decision is made
here.

MM-Fit's repetitions are ASSUMED normal, so every flag on an unaltered repetition counts as a false
alarm and every rate is an upper bound on false alarms. The plants are arithmetic on records: nothing
here is a real-world detection rate.

Usage: python anomaly_study.py <study dir>
"""
import csv
import os
import sys
from collections import defaultdict

import numpy as np

TARGET = 0.05
A3_LIMIT = 0.10
SIZES = [10, 20, 40, 80]
SEEDS = 10
EXERCISES = ["shoulder_press", "lateral_raise", "pushup", "situp", "bicep_curl", "lunge", "row"]
VIEW_MISMATCH = {"bicep_curl", "lunge", "row"}
# P3-34's detectors 1-3 and P3-36's detector 4, each forest at the policy's own seed.
PRIMARY = ["layer1", "sumsqF1", "sumsqF1F2", "forestF1", "forestF1F2", "distanceF1", "distanceF1F2"]
# Not in the protocol, fixed in P3-37 before any run: Layer 1 with its consistency metric too.
EXPLORATORY = ["layer1c"]
DETERMINISTIC = ["layer1", "sumsqF1", "sumsqF1F2", "distanceF1", "distanceF1F2"]
PLANTS = [("rom", "20"), ("rom", "30"), ("rom", "40"), ("tempo", "1.5"), ("tempo", "2.0"),
          ("side", "20"), ("side", "30"), ("side", "40"), ("stall", "15"), ("stall", "25"),
          ("combined", "rom10+tempo1.25")]


def read(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def num(x):
    return float(x) if x != "" else float("nan")


def column(detector):
    """A2's column for a detector: the forest at the policy's seed is seed 0."""
    return detector + "_s0" if detector.startswith("forest") else detector


def pct(x):
    return "   -  " if x is None or not np.isfinite(x) else f"{100 * x:5.1f}%"


# --- Operating points ------------------------------------------------------------------------------

def mean_rate(arrays, c):
    return float(np.mean([np.mean(np.asarray(a, float) > c) for a in arrays]))


def equal_cut(by_subject, distinct, target=TARGET):
    """P3-30's rule: the smallest cut at which the mean over subjects of each subject's flag rate on
    unaltered repetitions is at most `target`. On a 0.01 grid, or (P3-36, for the distance) on the
    distinct pooled unaltered scores."""
    arrays = [np.asarray(a, float) for a in by_subject.values() if len(a)]
    if not arrays:
        return float("nan")
    values = np.unique(np.concatenate(arrays))
    lo, hi = 0, len(values) - 1  # the rate at the largest score is 0, so hi always qualifies
    while lo < hi:
        mid = (lo + hi) // 2
        if mean_rate(arrays, values[mid]) <= target:
            hi = mid
        else:
            lo = mid + 1
    c_star = float(values[lo])
    if distinct:
        return c_star
    # Every cut below c_star flags as the score just below it does, so the grid's answer is at or above it.
    k = max(int(np.floor(c_star * 100)) - 1, 0)
    while mean_rate(arrays, k / 100) > target:
        k += 1
    return k / 100


# --- The data ------------------------------------------------------------------------------------

def load(study):
    keys = read(os.path.join(study, "keys.csv"))
    a1 = read(os.path.join(study, "a1.csv"))
    for r in a1:
        r["n"] = int(r["n"])
        r["score"] = num(r["score"])
        r["flagged"] = r["flagged"] == "1"
    a2 = read(os.path.join(study, "a2.csv"))
    detectors = [c for c in a2[0].keys() if c not in ("subject", "exercise", "key", "order", "plant", "size")] if a2 else []
    for r in a2:
        for d in detectors:
            r[d] = num(r[d])
    skipped = read(os.path.join(study, "a2_skipped.csv"))
    models = read(os.path.join(study, "models.csv"))
    return keys, a1, a2, detectors, skipped, models


def data_section(keys, skipped, models, out):
    out.append("=== The data: development subjects A, B, C and S19 only; P3-33's engine at 30 a second")
    out.append("  One history per subject, exercise and anomaly key (attributed side / measured sides), in time order.")
    out.append("  VALID repetitions with every F1 + F2 feature; 'dropped' counts VALID ones missing a feature, per exercise.")
    out.append(f"  {'subject':7} {'exercise':15} {'key':8} {'used':>5} {'dropped':>7} {'A1 sizes':12} {'A2 hist':>7} {'A2 scored':>9}")
    for k in keys:
        view = "  (view mismatch)" if k["exercise"] in VIEW_MISMATCH else ""
        out.append(f"  {k['subject']:7} {k['exercise']:15} {k['key']:8} {k['used']:>5} {k['droppedForExercise']:>7} "
                   f"{k['a1Sizes'] or '-':12} {k['a2History']:>7} {k['a2Scored']:>9}{view}")
    no_baseline = [m for m in models if m["layerOneBaseline"] != "true" and m["features"] == "F1" and int(m["scored"]) > 0]
    out.append(f"  Histories with scored repetitions but no Layer 1 baseline: {len(no_baseline)}")
    by_plant = defaultdict(int)
    for s in skipped:
        by_plant[(s["plant"], s["size"])] += 1
    out.append("  Plants not made because a side would have had no range left: "
               + (", ".join(f"{p} {s}: {n}" for (p, s), n in sorted(by_plant.items())) or "none"))


# --- A1 and A3 -----------------------------------------------------------------------------------

def a1_section(a1, out, csv_rows):
    policy_seed = min(int(r["seed"]) for r in a1 if r["detector"] == "forest")
    primary = [r for r in a1 if r["detector"] == "distance" or int(r["seed"]) == policy_seed]
    out.append("\n=== A1: false alarms under the device rule as the history grows (upper bounds)")
    out.append("  Fitted on the first n, cut at the nearest-rank 95th percentile of the history's own scores, scored on the next 20.")
    out.append("  For reference only: were the next 20 exchangeable with the history and scored as the history was, the rule would")
    out.append("  flag (n + 1 - ceil(0.95 n)) / (n + 1): n=10 9.1%, 20 9.5%, 40 7.3%, 80 6.2%. A model scores its own history")
    out.append("  closer than new repetitions, so higher rates are expected even for unchanged movement.")
    a3 = {}
    for detector, features in [("forest", "F1"), ("forest", "F1F2"), ("distance", "F1"), ("distance", "F1F2")]:
        out.append(f"\n  {detector} {features}")
        out.append(f"    {'n':>3}  {'A':>6} {'B':>6} {'C':>6} {'S19':>6}  {'pooled':>6} {'highest':>7}  keys  worst subject-exercise")
        a3[(detector, features)] = []
        for n in SIZES:
            rows = [r for r in primary if r["detector"] == detector and r["features"] == features and r["n"] == n]
            if not rows:
                out.append(f"    {n:>3}  no subject has {n} + 20")
                a3[(detector, features)].append((n, None, None, 0))
                continue
            per = defaultdict(list)
            per_ex = defaultdict(list)
            for r in rows:
                per[r["subject"]].append(r["flagged"])
                per_ex[(r["subject"], r["exercise"])].append(r["flagged"])
            rates = {s: float(np.mean(v)) for s, v in per.items()}
            ex_rates = {k: float(np.mean(v)) for k, v in per_ex.items()}
            pooled = float(np.mean([r["flagged"] for r in rows]))
            highest = max(rates.values())
            worst = max(ex_rates.items(), key=lambda kv: kv[1])
            n_keys = len({(r["subject"], r["exercise"], r["key"]) for r in rows})
            out.append(f"    {n:>3}  " + " ".join(pct(rates.get(s)) for s in ["A", "B", "C", "S19"])
                       + f"  {pct(pooled)} {pct(highest)}  {n_keys:>4}  {worst[0][0]} {worst[0][1]} {pct(worst[1])}")
            for s, v in per.items():
                csv_rows.append([detector, features, n, s, "all", len(v), f"{rates[s]:.6f}"])
            for (s, e), v in per_ex.items():
                csv_rows.append([detector, features, n, s, e, len(v), f"{ex_rates[(s, e)]:.6f}"])
            a3[(detector, features)].append((n, highest, max(ex_rates.values()), n_keys))
    out.append("\n  Per subject, exercise and n, in a1_rates.csv.")

    out.append("\n=== A3: N_min, the smallest n at which every subject with data flags at most 10% of the next 20")
    out.append("  Primary (P3-34): each subject's rate pooled over their histories. Stricter: every subject-exercise pair.")
    for (detector, features), points in a3.items():
        def n_min(i):
            for n, highest, worst_ex, _ in points:
                value = (highest, worst_ex)[i]
                if value is not None and value <= A3_LIMIT:
                    return str(n)
            return "more than 80"
        detail = ", ".join(f"n={n}: {pct(h)} / {pct(w)}" for n, h, w, _ in points if h is not None)
        out.append(f"  {detector:8} {features:5} N_min {n_min(0):>12} (stricter {n_min(1)}).  Highest subject / worst pair: {detail}")
    return a3


# --- A2 ------------------------------------------------------------------------------------------

def unaltered_by_subject(rows, col):
    per = defaultdict(list)
    for r in rows:
        if r["plant"] == "none" and np.isfinite(r[col]):
            per[r["subject"]].append(r[col])
    return per


def detection(rows, col, cut):
    per = defaultdict(list)
    for r in rows:
        if np.isfinite(r[col]):
            per[r["subject"]].append(r[col] > cut)
    rates = {s: float(np.mean(v)) for s, v in per.items() if v}
    pooled = float(np.mean([x for v in per.values() for x in v])) if per else float("nan")
    mean = float(np.mean(list(rates.values()))) if rates else float("nan")
    return rates, mean, pooled, sum(len(v) for v in per.values())


def cuts_for(a2, detectors, target=TARGET):
    cuts = {}
    for e in EXERCISES:
        rows = [r for r in a2 if r["exercise"] == e]
        if not rows:
            continue
        for d in detectors:
            cuts[(e, d)] = equal_cut(unaltered_by_subject(rows, d), distinct=d.startswith("distance"), target=target)
    return cuts


def a2_section(a2, out, files):
    detectors = PRIMARY + EXPLORATORY
    cols = [column(d) for d in detectors]
    cuts = cuts_for(a2, cols)
    by_ex = {e: [r for r in a2 if r["exercise"] == e] for e in EXERCISES}

    out.append("\n=== A2: detection of planted deviations at equal false alarms (synthetic; not a real-world detection claim)")
    out.append("  Each detector's cut is set per exercise so that the mean over subjects of each subject's flag rate on unaltered")
    out.append("  repetitions is at most 5% (0.01 grid; the distance on its distinct pooled unaltered scores). Fitted on the first 40.")
    out.append("  'layer1c' is Layer 1 with its consistency metric: not in P3-34's protocol, fixed in P3-37 before the run.")

    out.append("\n  The cuts, and the false alarms they give (per subject; mean over subjects):")
    for e in EXERCISES:
        rows = by_ex[e]
        if not rows:
            continue
        subjects = sorted({r["subject"] for r in rows if r["plant"] == "none"})
        view = " (view mismatch)" if e in VIEW_MISMATCH else ""
        n_un = sum(1 for r in rows if r["plant"] == "none")
        out.append(f"   {e}{view}: {n_un} unaltered scored repetitions; subjects {', '.join(subjects)}")
        for d, c in zip(detectors, cols):
            rates, mean, _, _ = detection([r for r in rows if r["plant"] == "none"], c, cuts[(e, c)])
            out.append(f"     {d:13} cut {cuts[(e, c)]:>12.4g}   " + "  ".join(f"{s} {pct(rates[s])}" for s in subjects)
                       + f"   mean {pct(mean)}")
            files["a2_cuts"].append([e, d, f"{cuts[(e, c)]:.10g}", f"{mean:.6f}"] + [f"{s}:{rates[s]:.6f}" for s in subjects])

    out.append("\n  Detection, the subjects' mean (P3-30's summary), per exercise:")
    header = f"    {'plant':24}" + "".join(f"{d:>13}" for d in detectors)
    for e in EXERCISES:
        rows = by_ex[e]
        if not rows:
            continue
        view = " (view mismatch)" if e in VIEW_MISMATCH else ""
        out.append(f"   {e}{view}")
        out.append(header)
        for plant, size in PLANTS:
            planted = [r for r in rows if r["plant"] == plant and r["size"] == size]
            if not planted:
                continue
            line = f"    {plant + ' ' + size:24}"
            for d, c in zip(detectors, cols):
                rates, mean, pooled, n = detection(planted, c, cuts[(e, c)])
                line += f"{pct(mean):>13}"
                for s, v in rates.items():
                    files["a2_detection"].append([e, plant, size, d, s, f"{v:.6f}"])
                files["a2_detection"].append([e, plant, size, d, "subjects' mean", f"{mean:.6f}"])
                files["a2_detection"].append([e, plant, size, d, "pooled", f"{pooled:.6f}"])
            out.append(line)

    for label, exercises in [("all seven exercises", EXERCISES),
                             ("the four whose MM-Fit view matches", [e for e in EXERCISES if e not in VIEW_MISMATCH])]:
        out.append(f"\n  Detection pooled over {label}, each repetition at its own exercise's cut:")
        out.append(header + f"{'n':>7}")
        for plant, size in PLANTS:
            planted = [r for r in a2 if r["exercise"] in exercises and r["plant"] == plant and r["size"] == size]
            if not planted:
                continue
            line = f"    {plant + ' ' + size:24}"
            for d, c in zip(detectors, cols):
                hits = [r[c] > cuts[(r["exercise"], c)] for r in planted if np.isfinite(r[c])]
                line += f"{pct(float(np.mean(hits)) if hits else float('nan')):>13}"
            out.append(line + f"{len(planted):>7}")

    out.append("\n  What each detector flags that the others do not, at these cuts (planted repetitions, all exercises):")
    primary_cols = [column(d) for d in PRIMARY]
    for plant, size in PLANTS:
        planted = [r for r in a2 if r["plant"] == plant and r["size"] == size]
        if not planted:
            continue
        flags = {c: [np.isfinite(r[c]) and r[c] > cuts[(r["exercise"], c)] for r in planted] for c in primary_cols + [column("layer1c")]}
        only = {}
        for d, c in zip(PRIMARY, primary_cols):
            others = [o for o in primary_cols if o != c]
            only[d] = sum(1 for i in range(len(planted)) if flags[c][i] and not any(flags[o][i] for o in others))
        forest_any = [flags["forestF1_s0"][i] or flags["forestF1F2_s0"][i] for i in range(len(planted))]
        det_p334 = [flags["layer1"][i] or flags["sumsqF1"][i] or flags["sumsqF1F2"][i] for i in range(len(planted))]
        det_all = [det_p334[i] or flags["distanceF1"][i] or flags["distanceF1F2"][i] for i in range(len(planted))]
        det_all_c = [det_all[i] or flags["layer1c"][i] for i in range(len(planted))]
        forest_over_p334 = sum(1 for i in range(len(planted)) if forest_any[i] and not det_p334[i])
        forest_over_all = sum(1 for i in range(len(planted)) if forest_any[i] and not det_all[i])
        forest_over_all_c = sum(1 for i in range(len(planted)) if forest_any[i] and not det_all_c[i])
        det_over_forest = sum(1 for i in range(len(planted)) if det_all[i] and not forest_any[i])
        out.append(f"    {plant + ' ' + size:24} n={len(planted):5}  forest, not detectors 1-2: {forest_over_p334:4}; "
                   f"not 1, 2 or 4: {forest_over_all:4}; nor layer1c: {forest_over_all_c:4}.  Deterministic, not forest: {det_over_forest:4}."
                   "  Only this one: " + ", ".join(f"{d} {only[d]}" for d in PRIMARY))
        files["a2_unique"].append([plant, size, len(planted), forest_over_p334, forest_over_all, forest_over_all_c, det_over_forest]
                                  + [only[d] for d in PRIMARY])

    # The full curve: detection (subjects' mean) against the mean false-alarm rate, as the cut varies.
    for e in EXERCISES:
        rows = by_ex[e]
        if not rows:
            continue
        unaltered = [r for r in rows if r["plant"] == "none"]
        planted_by = {(p, s): [r for r in rows if r["plant"] == p and r["size"] == s] for p, s in PLANTS}
        for d, c in zip(detectors, cols):
            un = unaltered_by_subject(rows, c)
            for t in np.round(np.arange(0.0, 0.3001, 0.01), 2):
                cut = equal_cut(un, distinct=d.startswith("distance"), target=float(t))
                _, fa, _, _ = detection(unaltered, c, cut)
                for plant, size in PLANTS:
                    planted = planted_by[(plant, size)]
                    if planted:
                        _, mean, _, _ = detection(planted, c, cut)
                        files["a2_curves"].append([e, d, f"{t:.2f}", f"{cut:.10g}", f"{fa:.6f}", plant, size, f"{mean:.6f}"])
    out.append("\n  The full curves (detection against the mean false-alarm rate, 0-30%) are in a2_curves.csv.")
    return cuts


# --- A4 ------------------------------------------------------------------------------------------

def a4_section(a1, a2, detectors, out, files):
    out.append("\n=== A4: stability of the forest over 10 seeds (the policy's seed and the next nine)")
    out.append("  The share of flag decisions on which all 10 seeds agree. Reported; no threshold (P3-34).")
    groups = defaultdict(list)
    for r in a1:
        if r["detector"] == "forest":
            groups[(r["features"], r["n"], r["subject"], r["exercise"], r["key"], r["j"])].append(r["flagged"])
    for features in ["F1", "F1F2"]:
        parts = []
        for n in SIZES:
            g = [v for k, v in groups.items() if k[0] == features and k[1] == n]
            if g:
                assert all(len(v) == SEEDS for v in g)
                agree = float(np.mean([all(v) or not any(v) for v in g]))
                parts.append(f"n={n} {pct(agree)} of {len(g)}")
                files["a4"].append(["A1 device rule", features, n, len(g), f"{agree:.6f}"])
        out.append(f"  A1, device rule, forest {features}: " + "; ".join(parts))
    for features in ["F1", "F1F2"]:
        cols = [f"forest{features}_s{s}" for s in range(SEEDS)]
        cuts = cuts_for(a2, cols)
        decisions = [[r[c] > cuts[(r["exercise"], c)] for c in cols] for r in a2]
        agree = float(np.mean([all(v) or not any(v) for v in decisions]))
        un = [v for v, r in zip(decisions, a2) if r["plant"] == "none"]
        agree_un = float(np.mean([all(v) or not any(v) for v in un]))
        out.append(f"  A2, equal-false-alarm cut set per seed, forest {features}: {pct(agree)} of {len(decisions)} decisions "
                   f"(unaltered only: {pct(agree_un)} of {len(un)})")
        files["a4"].append(["A2 equal false alarms", features, 40, len(decisions), f"{agree:.6f}"])
        spread = []
        for plant, size in PLANTS:
            idx = [i for i, r in enumerate(a2) if r["plant"] == plant and r["size"] == size]
            if idx:
                per_seed = [float(np.mean([decisions[i][s] for i in idx])) for s in range(SEEDS)]
                spread.append(f"{plant} {size} {pct(min(per_seed))}-{pct(max(per_seed))}")
        out.append("    pooled detection, lowest-highest over seeds: " + "; ".join(spread))


def supplementary_section(a2, out, files):
    """Added after the run, and labelled so (P3-37): nothing here changes a primary number."""
    out.append("\n=== Supplementary, added after the run - not the protocol's analysis")
    detectors = PRIMARY + EXPLORATORY
    cols = [column(d) for d in detectors]
    cuts = cuts_for(a2, cols)

    out.append("\n  1. Chance: what each detector flags alone on UNALTERED repetitions, at the same cuts, for comparison with A2's")
    out.append("     'what each detector flags that the others do not'. Unaltered repetitions have no planted change to find.")
    un = [r for r in a2 if r["plant"] == "none"]
    flags = {c: [np.isfinite(r[c]) and r[c] > cuts[(r["exercise"], c)] for r in un] for c in cols}
    primary_cols = [column(d) for d in PRIMARY]
    forest_any = [flags["forestF1_s0"][i] or flags["forestF1F2_s0"][i] for i in range(len(un))]
    det_p334 = [flags["layer1"][i] or flags["sumsqF1"][i] or flags["sumsqF1F2"][i] for i in range(len(un))]
    det_all = [det_p334[i] or flags["distanceF1"][i] or flags["distanceF1F2"][i] for i in range(len(un))]
    det_all_c = [det_all[i] or flags["layer1c"][i] for i in range(len(un))]
    only = {d: sum(1 for i in range(len(un)) if flags[c][i] and not any(flags[o][i] for o in primary_cols if o != c))
            for d, c in zip(PRIMARY, primary_cols)}
    out.append(f"     none n={len(un):5}  forest, not detectors 1-2: {sum(f and not d for f, d in zip(forest_any, det_p334)):4}; "
               f"not 1, 2 or 4: {sum(f and not d for f, d in zip(forest_any, det_all)):4}; "
               f"nor layer1c: {sum(f and not d for f, d in zip(forest_any, det_all_c)):4}.  "
               f"Deterministic, not forest: {sum(d and not f for f, d in zip(forest_any, det_all)):4}.  Only this one: "
               + ", ".join(f"{d} {only[d]}" for d in PRIMARY))

    out.append("\n  2. The forest over its 10 seeds, each seed at its own equal-false-alarm cut: pooled detection, all exercises,")
    out.append("     median and lowest-highest, beside the deterministic detectors (which have no seed).")
    per_seed = {}
    for features in ["F1", "F1F2"]:
        fcols = [f"forest{features}_s{s}" for s in range(SEEDS)]
        fcuts = cuts_for(a2, fcols)
        for plant, size in PLANTS:
            planted = [r for r in a2 if r["plant"] == plant and r["size"] == size]
            per_seed[(features, plant, size)] = [float(np.mean([r[c] > fcuts[(r["exercise"], c)] for r in planted])) for c in fcols]
    out.append(f"     {'plant':24}{'forestF1 median (range)':>28}{'forestF1F2 median (range)':>30}{'layer1':>9}{'sumsqF1':>9}{'distF1':>9}{'layer1c':>9}")
    for plant, size in PLANTS:
        planted = [r for r in a2 if r["plant"] == plant and r["size"] == size]
        det = {d: float(np.mean([np.isfinite(r[d]) and r[d] > cuts[(r["exercise"], d)] for r in planted]))
               for d in ["layer1", "sumsqF1", "distanceF1", "layer1c"]}
        cells = []
        for features in ["F1", "F1F2"]:
            v = per_seed[(features, plant, size)]
            cells.append(f"{pct(float(np.median(v)))} ({pct(min(v)).strip()}-{pct(max(v)).strip()})")
        out.append(f"     {plant + ' ' + size:24}{cells[0]:>28}{cells[1]:>30}" + "".join(f"{pct(det[d]):>9}" for d in det))
        files["supp_seeds"].append([plant, size] + [f"{x:.6f}" for x in per_seed[("F1", plant, size)] + per_seed[("F1F2", plant, size)]])

    out.append("\n  3. The forest's cut on its distinct pooled unaltered scores, as the distance's is (P3-36), instead of the 0.01 grid.")
    out.append("     Forest scores crowd between about 0.4 and 0.7, so a 0.01 step can leave the realised mean false-alarm rate")
    out.append("     well under 5% (3.0-4.5% above). Policy seed.")
    for features in ["F1", "F1F2"]:
        c = f"forest{features}_s0"
        line = f"     forest{features:5}"
        realised = []
        hits = defaultdict(list)
        for e in EXERCISES:
            rows = [r for r in a2 if r["exercise"] == e]
            if not rows:
                continue
            cut = equal_cut(unaltered_by_subject(rows, c), distinct=True)
            _, fa, _, _ = detection([r for r in rows if r["plant"] == "none"], c, cut)
            realised.append(f"{e} {pct(fa).strip()}")
            for r in rows:
                if r["plant"] != "none":
                    hits[(r["plant"], r["size"])].append(r[c] > cut)
        out.append(line + " realised false alarms: " + ", ".join(realised))
        out.append("       pooled detection: " + "; ".join(f"{p} {s} {pct(float(np.mean(hits[(p, s)]))).strip()}" for p, s in PLANTS if hits[(p, s)]))


def write_csv(path, header, rows):
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


def main(study):
    keys, a1, a2, detectors, skipped, models = load(study)
    out = ["The anomaly study, A1-A4 - DECISIONS.md P3-34, P3-36, P3-37. Development subjects only.",
           "MM-Fit's repetitions are assumed normal: every rate on unaltered repetitions is an upper bound on false alarms.",
           ""]
    files = defaultdict(list)
    a1_rows = []
    data_section(keys, skipped, models, out)
    a1_section(a1, out, a1_rows)
    a2_section(a2, out, files)
    a4_section(a1, a2, detectors, out, files)
    supplementary_section(a2, out, files)
    write_csv(os.path.join(study, "a1_rates.csv"), ["detector", "features", "n", "subject", "exercise", "decisions", "rate"], a1_rows)
    write_csv(os.path.join(study, "a2_cuts.csv"), ["exercise", "detector", "cut", "meanFalseAlarm", "perSubject..."], files["a2_cuts"])
    write_csv(os.path.join(study, "a2_detection.csv"), ["exercise", "plant", "size", "detector", "subject", "rate"], files["a2_detection"])
    write_csv(os.path.join(study, "a2_curves.csv"), ["exercise", "detector", "targetFalseAlarm", "cut", "meanFalseAlarm", "plant", "size", "detection"], files["a2_curves"])
    write_csv(os.path.join(study, "a2_unique.csv"), ["plant", "size", "planted", "forestNotDetectors12", "forestNot124", "forestNot124Nor1c", "deterministicNotForest"] + [f"only_{d}" for d in PRIMARY], files["a2_unique"])
    write_csv(os.path.join(study, "a4.csv"), ["decisions", "features", "n", "count", "allTenAgree"], files["a4"])
    write_csv(os.path.join(study, "supp_seeds.csv"), ["plant", "size"] + [f"forestF1_s{i}" for i in range(SEEDS)] + [f"forestF1F2_s{i}" for i in range(SEEDS)], files["supp_seeds"])
    text = "\n".join(out) + "\n"
    with open(os.path.join(study, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: python anomaly_study.py <study dir>")
    main(sys.argv[1])
