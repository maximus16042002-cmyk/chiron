"""The lateral raise's resting angle between repetitions - DECISIONS.md P3-28, Part 3.

The start zone is 'at or below 28 degrees' (topThreshold 20 + hysteresis 8), provisional and uncited.
This measures where MM-Fit's subjects actually return to between raises: for each set and side, the
valleys of the hip-shoulder-elbow angle (the engine's definition, in the corrected square-pixel frame
of CONTRACTS.md section 4) between excursions that reach the far zone. A diagnostic for reasoning about
the threshold, not a fit: it reports distributions, split by development and held-out workouts.

Split by subject (DECISIONS.md P3-30), per subject and pooled. P3-28's figures were computed under P3-27's
split by workout; `--p3-27-split` reproduces them, labelled so.

Usage: python raise_rest_angles.py <landmark dir> <harness protocol dir> [--p3-27-split]
"""
import csv
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
from subjects import chosen_split, subject_of  # noqa: E402

# The split, by subject (P3-30); `--p3-27-split` reproduces P3-28's figures under P3-27's split.
DEVELOPMENT, HELD_OUT, SPLIT_LABEL = chosen_split(sys.argv)
SIDES = {"left": (23, 11, 13), "right": (24, 12, 14)}  # hip, shoulder (vertex), elbow
FAR = 77.0  # the far zone the engine needs: bottom 85 - hysteresis 8


def angle(p, a, v, b):
    ax, ay = p[a][0] - p[v][0], p[a][1] - p[v][1]
    bx, by = p[b][0] - p[v][0], p[b][1] - p[v][1]
    return math.degrees(math.atan2(abs(ax * by - ay * bx), ax * bx + ay * by))


def valleys(series):
    """The minimum between consecutive excursions past FAR, and before the first and after the last."""
    above = series >= FAR
    out, i, n = [], 0, len(series)
    runs = []
    while i < n:
        if above[i]:
            j = i
            while j < n and above[j]:
                j += 1
            runs.append((i, j))
            i = j
        else:
            i += 1
    for (a0, a1), (b0, b1) in zip(runs, runs[1:]):
        out.append(float(np.nanmin(series[a1:b0])))
    return out, len(runs)


def main(landmark_dir, protocol_dir):
    rows = [r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] == "lateral_raise"]
    per = {"development": [], "held-out": []}
    per_set = {"development": [], "held-out": []}
    per_subject = {}
    cache = {}
    for r in rows:
        w = r["workout"]
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache = {w: (width, height, {int(f): i for i, f in enumerate(frames)}, points)}
        width, height, at, points = cache[w]
        split = "development" if w in DEVELOPMENT else "held-out"
        set_valleys = []
        for side, (a, v, b) in SIDES.items():
            series = []
            for f in range(int(r["windowFirst"]), int(r["windowLast"]) + 1):
                i = at.get(f)
                if i is None:
                    series.append(np.nan)
                    continue
                p = points[i][:, :2] * np.array([1.0, height / width])
                series.append(angle(p, a, v, b))
            vals, excursions = valleys(np.array(series))
            per[split] += vals
            per_subject.setdefault((split, subject_of(w)), []).extend(vals)
            set_valleys += vals
        if set_valleys:
            per_set[split].append(float(np.median(set_valleys)))
    for split in ("development", "held-out"):
        v = np.array(per[split])
        s = np.array(per_set[split])
        if not len(v):
            continue
        print(f"{split}{SPLIT_LABEL}: {len(v)} valleys between raises, {len(s)} sets")
        for (sp, subject), vals in sorted(per_subject.items()):
            if sp == split:
                x = np.array(vals)
                print(f"   subject {subject:4s}: {len(x):4d} valleys, median {np.median(x):5.1f}, 95th {np.percentile(x, 95):5.1f}")
        print(f"   valley angle: median {np.median(v):.1f}, IQR {np.percentile(v, 25):.1f}..{np.percentile(v, 75):.1f}, "
              f"5th-95th {np.percentile(v, 5):.1f}..{np.percentile(v, 95):.1f}")
        for limit in (28, 32, 36, 40, 44, 48):
            print(f"   share of valleys at or below {limit:2d}: {np.mean(v <= limit):6.1%}   sets whose median valley is: {np.mean(s <= limit):6.1%}")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    main(args[0], args[1])
