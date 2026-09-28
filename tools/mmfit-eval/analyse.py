"""P7's rep-count study: Kinesis's count against MM-Fit's, per set - DECISIONS.md P3-27.

Reads the harness's output - P5 exports (CONTRACTS.md section 10) and the harness's sets.csv, which
carries each set's MM-Fit identity and window - and reports, per exercise and protocol, the metrics
ZERO_DATASET_BUILD_PLAN's evaluation table names for repetition detection: mean absolute error, the
share of sets counted exactly, and the share within one. The count is a set's `validCount`: the
repetitions the engine counted as VALID, which is what the app shows as the rep count.

Also, before any number is treated as a result:
- flags: frames missing from a window, low pose coverage, interpolated shifts, the largest errors;
- the camera view MM-Fit actually used for each exercise, measured from the landmarks, against the
  view the Kinesis profile requires (CLAUDE.md section 22);
- a timing sanity check, which is not ground truth: MM-Fit has no per-repetition timing labels.

Squats are not reported: excluded pending the squat's camera view (P3-27). Tricep extensions and
jumping jacks have no Kinesis profile.

Usage: python analyse.py <harness out dir> <landmark dir> [--json out.json]
"""
import csv
import json
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402

PROTOCOLS = ["primary", "widened", "unshifted"]

# Declared before any held-out result was run (P3-27): the development workouts are those examined
# while the engine defect was found; any engine change P7 motivates is judged on the held-out ones.
DEVELOPMENT = {"w00", "w01", "w02", "w03", "w04", "w05", "w06", "w19"}

# MM-Fit's view is not the profile's (P3-27): lunges front-on or oblique and rows oblique, where both
# profiles are side-on. Their counts are shown, never as evidence for the app's use (CLAUDE.md s22).
VIEW_MISMATCH = {"lunge", "row"}
REQUIRED_VIEW = {
    "pushup": "SIDE_ON", "shoulder_press": "FRONT_ON", "lunge": "SIDE_ON", "row": "SIDE_ON",
    "situp": "SIDE_ON", "bicep_curl": "FRONT_ON", "lateral_raise": "FRONT_ON",
}
L_SHO, R_SHO, L_HIP, R_HIP = 11, 12, 23, 24


def load(protocol_dir):
    rows = list(csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline="")))
    for r in rows:
        assert None not in r.values() and None not in r, f"a malformed sets.csv row: {r}"
    exports = {}
    for name in os.listdir(protocol_dir):
        if name.endswith(".export.json"):
            doc = json.load(open(os.path.join(protocol_dir, name)))
            assert doc["format"] == "kinesis-export" and doc["formatVersion"] == 1, name
            session = doc["sessions"][0]
            exports[session["sessionId"].removeprefix("mmfit-")] = session["sets"]
    for r in rows:
        if r["status"] != "evaluated":
            continue
        s = exports[r["workout"]][int(r["exportSetIndex"]) - 1]
        assert s["exerciseId"] == r["profileId"], (r, s["exerciseId"])
        r["counted"] = s["validCount"]
        r["records"] = s["recordCount"]
        r["repDurationsMs"] = [x["endMs"] - x["startMs"] for x in s["reps"] if x["validity"] == "VALID"]
        r["validities"] = [x["validity"] for x in s["reps"]]
    return rows


def metrics(rows):
    err = np.array([r["counted"] - int(r["labelledReps"]) for r in rows])
    return {
        "sets": len(rows),
        "labelled": int(sum(int(r["labelledReps"]) for r in rows)),
        "counted": int(sum(r["counted"] for r in rows)),
        "mae": float(np.mean(np.abs(err))),
        "bias": float(np.mean(err)),
        "exact": float(np.mean(err == 0)),
        "within1": float(np.mean(np.abs(err) <= 1)),
    }


def view_index(landmark_dir, rows):
    """Per evaluated set, the median over its window of shoulder separation over torso length, in the
    corrected (square-pixel) frame: near 0 side-on, larger face-on. Descriptive only."""
    cache, out = {}, {}
    for r in rows:
        w = r["workout"]
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache = {w: (width, height, {int(f): i for i, f in enumerate(frames)}, points)}
        width, height, at, points = cache[w]
        values = []
        for f in range(int(r["windowFirst"]), int(r["windowLast"]) + 1):
            i = at.get(f)
            if i is None or np.isnan(points[i, 0, 0]):
                continue
            p = points[i][:, :2] * np.array([1.0, height / width])
            shoulders = np.linalg.norm(p[L_SHO] - p[R_SHO])
            torso = np.linalg.norm((p[L_SHO] + p[R_SHO]) / 2 - (p[L_HIP] + p[R_HIP]) / 2)
            if torso > 0:
                values.append(shoulders / torso)
        out[(w, r["mmfitSetIndex"])] = float(np.median(values)) if values else float("nan")
    return out


def main(harness_dir, landmark_dir, json_out=None):
    report = {}
    primary_rows = None
    for protocol in PROTOCOLS:
        rows = load(os.path.join(harness_dir, protocol))
        evaluated = [r for r in rows if r["status"] == "evaluated"]
        if protocol == "primary":
            primary_rows = evaluated
            excluded = defaultdict(int)
            for r in rows:
                if r["status"] != "evaluated":
                    excluded[(r["exercise"], r["reason"][:60])] += 1
            report["excluded"] = {f"{k[0]}: {k[1]}": v for k, v in excluded.items()}
        by = defaultdict(list)
        for r in evaluated:
            by[r["profileId"]].append(r)
        report[protocol] = {p: metrics(rs) for p, rs in sorted(by.items())}
        report[protocol]["ALL EVALUATED"] = metrics(evaluated)

    print("=== Sets not evaluated")
    for k, v in report["excluded"].items():
        print(f"  {v:3d} sets  {k}")
    for protocol in PROTOCOLS:
        print(f"\n=== {protocol.upper()}: counted (VALID) against labelled, per set   (* = view mismatch, not evidence)")
        print(f"  {'exercise':16s} {'sets':>5s} {'labelled':>9s} {'counted':>8s} {'MAE':>6s} {'bias':>6s} {'exact':>6s} {'within 1':>9s}")
        for p, m in report[protocol].items():
            mark = "*" if p in VIEW_MISMATCH else " "
            print(f"  {p + mark:16s} {m['sets']:5d} {m['labelled']:9d} {m['counted']:8d} {m['mae']:6.2f} {m['bias']:+6.2f} {m['exact']:6.1%} {m['within1']:9.1%}")

    for name, keep in (("DEVELOPMENT (w00-w06, w19)", lambda r: r["workout"] in DEVELOPMENT),
                       ("HELD-OUT (w07-w18, w20)", lambda r: r["workout"] not in DEVELOPMENT)):
        subset = [r for r in primary_rows if keep(r)]
        if not subset:
            continue  # a run over one split only, e.g. judging a change on the held-out workouts
        by = defaultdict(list)
        for r in subset:
            by[r["profileId"]].append(r)
        split = {p: metrics(rs) for p, rs in sorted(by.items())}
        report.setdefault("split", {})[name] = split
        print(f"\n=== PRIMARY, {name}")
        print(f"  {'exercise':16s} {'sets':>5s} {'labelled':>9s} {'counted':>8s} {'MAE':>6s} {'bias':>6s} {'exact':>6s} {'within 1':>9s}")
        for p, m in split.items():
            mark = "*" if p in VIEW_MISMATCH else " "
            print(f"  {p + mark:16s} {m['sets']:5d} {m['labelled']:9d} {m['counted']:8d} {m['mae']:6.2f} {m['bias']:+6.2f} {m['exact']:6.1%} {m['within1']:9.1%}")

    rows = primary_rows
    print("\n=== Flags (primary)")
    missing = [r for r in rows if int(r["framesMissing"]) > 0]
    print(f"  sets with frames outside the extraction: {len(missing)}")
    low = [r for r in rows if int(r["framesWithPose"]) < 0.9 * int(r["framesFed"])]
    print(f"  sets where MediaPipe found a person in under 90% of frames: {len(low)}")
    for r in low[:10]:
        print(f"    {r['workout']} set {r['mmfitSetIndex']} {r['profileId']}: {r['framesWithPose']}/{r['framesFed']}")
    interp = [r for r in rows if r["shiftSource"] != "measured"]
    print(f"  sets whose shift was interpolated: {len(interp)}")
    shifts = np.array([int(r["shift"]) for r in rows])
    print(f"  shift across evaluated sets: median {np.median(shifts):+.0f}, range {shifts.min():+d}..{shifts.max():+d} frames")
    zero = [r for r in rows if r["counted"] == 0]
    print(f"  sets counted as zero: {len(zero)}")
    worst = sorted(rows, key=lambda r: -abs(r["counted"] - int(r["labelledReps"])))[:12]
    print("  largest errors:")
    for r in worst:
        v = defaultdict(int)
        for x in r["validities"]:
            v[x] += 1
        print(f"    {r['workout']} set {r['mmfitSetIndex']:>2s} {r['profileId']:15s} labelled {r['labelledReps']:>2s} counted {r['counted']:2d}  records {dict(v)}")

    print("\n=== Why sets fell short (primary) - diagnostics from the engine's own per-side angles")
    print("  measured = share of frames the side's driving angle was measurable; 'reaches' = the side's")
    print("  5th-95th percentile angle covers the start zone (top) and the far zone (bottom) the FSM needs.")
    by = defaultdict(list)
    for r in rows:
        by[r["profileId"]].append(r)
    for p, rs in sorted(by.items()):
        def f(r, k):
            return float(r[k]) if r[k] != "" else float("nan")
        lm = np.nanmedian([f(r, "leftMeasured") for r in rs])
        rm = np.nanmedian([f(r, "rightMeasured") for r in rs])
        reach = {"left": [0, 0], "right": [0, 0]}
        for r in rs:
            top, bottom, hyst = f(r, "top"), f(r, "bottom"), f(r, "hysteresis")
            for side in ("left", "right"):
                p5, p95 = f(r, side + "P5"), f(r, side + "P95")
                if np.isnan(p5):
                    continue
                if top > bottom:
                    at_top, at_bottom = p95 >= top - hyst, p5 <= bottom + hyst
                else:
                    at_top, at_bottom = p5 <= top + hyst, p95 >= bottom - hyst
                reach[side][0] += int(at_top)
                reach[side][1] += int(at_bottom)
        n = len(rs)
        print(f"  {p:15s} measured L {lm:5.1%} R {rm:5.1%} | reaches top L {reach['left'][0]}/{n} R {reach['right'][0]}/{n}, "
              f"bottom L {reach['left'][1]}/{n} R {reach['right'][1]}/{n} | thresholds top {rs[0]['top']} bottom {rs[0]['bottom']} +/- {rs[0]['hysteresis']}")

    print("\n=== Where the shortfall is (primary) - labelled vs threshold cycles vs counted")
    print("  threshold cycles: an ideal threshold machine on the engine's own raw angles, with no gate, no")
    print("  smoothing and no timing limits, combined per the profile's semantics (per limb: L+R; simultaneous")
    print("  on both sides: the lesser; near side only: the greater). labelled - threshold = what the movement,")
    print("  view or thresholds never showed; threshold - counted = what the pipeline and state machine lost.")
    for p, rs in sorted(by.items()):
        def ideal(r):
            left, right = int(r["leftThresholdCycles"]), int(r["rightThresholdCycles"])
            if r["semantics"] == "PER_LIMB":
                return left + right
            if r["analysedSides"] == "NEAR_SIDE_ONLY":
                return max(left, right)
            return min(left, right)
        labelled = sum(int(r["labelledReps"]) for r in rs)
        threshold = sum(ideal(r) for r in rs)
        counted = sum(r["counted"] for r in rs)
        matches = sum(1 for r in rs if ideal(r) == int(r["labelledReps"]))
        print(f"  {p:15s} labelled {labelled:5d} | threshold cycles {threshold:5d} ({matches}/{len(rs)} sets equal the label) | counted {counted:5d}")
        report.setdefault("shortfall", {})[p] = {"labelled": labelled, "thresholdCycles": threshold, "counted": counted, "setsIdealEqualsLabel": matches}

    print("\n=== The view MM-Fit used, measured: shoulder separation / torso length (primary windows)")
    views = view_index(landmark_dir, rows)
    by = defaultdict(list)
    for r in rows:
        by[r["profileId"]].append(views[(r["workout"], r["mmfitSetIndex"])])
    for p, vs in sorted(by.items()):
        vs = np.array([v for v in vs if not np.isnan(v)])
        print(f"  {p:15s} requires {REQUIRED_VIEW[p]:8s} | median {np.median(vs):.2f}, IQR {np.percentile(vs, 25):.2f}..{np.percentile(vs, 75):.2f}, range {vs.min():.2f}..{vs.max():.2f}")
    report["viewIndex"] = {p: [float(v) for v in vs] for p, vs in by.items()}

    print("\n=== Timing sanity - not ground truth (MM-Fit labels no repetition's start or end)")
    by = defaultdict(list)
    for r in rows:
        by[r["profileId"]].append(r)
    for p, rs in sorted(by.items()):
        ours = [d for r in rs for d in r["repDurationsMs"]]
        implied = [(int(r["labelEnd"]) - int(r["labelStart"])) / int(r["labelledReps"]) * 1000 / 30 for r in rs]
        print(f"  {p:15s} counted repetition, median {np.median(ours) / 1000 if ours else float('nan'):.2f} s  |  label window / labelled count, median {np.median(implied) / 1000:.2f} s")
    if json_out:
        json.dump(report, open(json_out, "w"), indent=1)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    if out in args:
        args.remove(out)
    main(args[0], args[1], out)
