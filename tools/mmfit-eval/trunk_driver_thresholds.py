"""The sit-up's trunk-angle driver: its thresholds, by the method fixed in DECISIONS.md P3-45.

Development workouts only: the script refuses any other. For each evaluated sit-up set, on its near
side (the side whose shoulder and hip are both measurable in more frames), the trunk's inclination
per frame - the hip-to-shoulder line's angle above horizontal, in CONTRACTS.md section 4's
square-pixel frame, landmarks at visibility 0.4 or more (the engine's marginal level):
  1. excursions: maximal runs above 45 degrees; an excursion's "up" value is its maximum;
  2. returns: the minimum between two consecutive excursions, counted only below 45 - 2 x 8 = 29;
  3. start edge = 95th percentile of returns + 3; far edge = 5th percentile of ups - 3;
  4. with the hysteresis 8: top threshold = start edge - 8, bottom threshold = far edge + 8;
  5. the sweep between the edges must be at least 30 degrees, or the work stops.

Usage: python trunk_driver_thresholds.py <landmark dir> <harness protocol dir>
"""
import csv
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
import subjects  # noqa: E402

SPLIT = 45.0
HYSTERESIS = 8.0
RETURN_BELOW = SPLIT - 2 * HYSTERESIS
MARGIN = 3.0
MIN_SWEEP = 30.0
VISIBLE = 0.4
SIDES = {"left": (23, 11), "right": (24, 12)}  # (hip, shoulder), MediaPipe indices


def inclination(points, i, width, height, hip, shoulder):
    p = points[i]
    if p[hip, 3] < VISIBLE or p[shoulder, 3] < VISIBLE:
        return math.nan
    hx, hy = p[hip, 0], p[hip, 1] * height / width
    sx, sy = p[shoulder, 0], p[shoulder, 1] * height / width
    across, rise = abs(sx - hx), hy - sy
    if across < 1e-6 and abs(rise) < 1e-6:
        return math.nan
    return math.degrees(math.atan2(rise, across))


def excursions(series):
    """(start, end) index runs where the series is above the split; NaN breaks nothing."""
    runs, i, n = [], 0, len(series)
    while i < n:
        if series[i] > SPLIT:
            j = i
            while j < n and not (series[j] <= SPLIT):
                j += 1
            runs.append((i, j))
            i = j
        else:
            i += 1
    return runs


def main(landmark_dir, protocol_dir):
    development = set(subjects.DEVELOPMENT)
    rows = [r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] == "situp"]
    for r in rows:
        if r["workout"] not in development:
            sys.exit(f"refusing: {r['workout']} is not a development workout (DECISIONS.md P3-45)")
    returns, ups = [], []
    per_subject = {}
    cache = {}
    for r in sorted(rows, key=lambda r: (r["workout"], int(r["mmfitSetIndex"]))):
        w = r["workout"]
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache = {w: (width, height, {int(f): i for i, f in enumerate(frames)}, points)}
        width, height, at, points = cache[w]
        first, last = int(r["windowFirst"]), int(r["windowLast"])
        series = {}
        for side, (hip, shoulder) in SIDES.items():
            series[side] = np.array([inclination(points, at[f], width, height, hip, shoulder) if f in at else math.nan
                                     for f in range(first, last + 1)])
        near = max(series.values(), key=lambda s: int(np.isfinite(s).sum()))
        runs = excursions(near)
        set_ups = [float(np.nanmax(near[a:b])) for a, b in runs if np.isfinite(near[a:b]).any()]
        set_returns = []
        for (_, a_end), (b_start, _) in zip(runs, runs[1:]):
            gap = near[a_end:b_start]
            if np.isfinite(gap).any() and np.nanmin(gap) < RETURN_BELOW:
                set_returns.append(float(np.nanmin(gap)))
        ups += set_ups
        returns += set_returns
        s = subjects.subject_of(w)
        per_subject.setdefault(s, ([], []))
        per_subject[s][0].extend(set_returns)
        per_subject[s][1].extend(set_ups)

    returns, ups = np.array(returns), np.array(ups)
    print(f"Development sit-up sets: {len(rows)}; returns {len(returns)}, ups {len(ups)}")
    for s, (ret, up) in sorted(per_subject.items()):
        if ret or up:
            print(f"  subject {s:4}: returns {len(ret):3} median {np.median(ret) if ret else float('nan'):5.1f}; "
                  f"ups {len(up):3} median {np.median(up) if up else float('nan'):5.1f}")
    start_edge = float(np.percentile(returns, 95)) + MARGIN
    far_edge = float(np.percentile(ups, 5)) - MARGIN
    top, bottom = start_edge - HYSTERESIS, far_edge + HYSTERESIS
    sweep = far_edge - start_edge
    print(f"start edge {start_edge:.1f} (returns' 95th percentile {np.percentile(returns, 95):.1f} + 3); "
          f"far edge {far_edge:.1f} (ups' 5th percentile {np.percentile(ups, 5):.1f} - 3)")
    print(f"top threshold {top:.1f}, bottom threshold {bottom:.1f}, hysteresis {HYSTERESIS:.0f}; sweep between edges {sweep:.1f}")
    if sweep < MIN_SWEEP:
        print(f"STOP: the sweep is under {MIN_SWEEP:.0f} degrees, so the driver would count a crunch (P3-45).")
        sys.exit(2)
    print(f"THRESHOLDS {top:.1f} {bottom:.1f}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: python trunk_driver_thresholds.py <landmark dir> <harness protocol dir>")
    main(sys.argv[1], sys.argv[2])
