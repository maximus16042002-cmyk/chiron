"""Where push-ups and sit-ups return to between repetitions, on the near side - DECISIONS.md P3-29.

Both start at the larger angle (arms extended; lying back) and the start zone is 'at or above' it:
push-up 155 (topThreshold 160 - hysteresis 5), sit-up 132 (140 - 8), provisional and uncited. The mirror
of raise_rest_angles.py: for each set, on the side measured in more frames (the near side, which is the
side P3-28's near-side-only profiles count), the peaks of the engine's joint angle - in the corrected
square-pixel frame of CONTRACTS.md section 4 - between consecutive excursions into the far zone.
A diagnostic for reasoning about a threshold, not a fit: distributions, split by development and
held-out workouts.

Usage: python start_zone_angles.py <landmark dir> <harness protocol dir>
"""
import csv
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
from raise_rest_angles import DEVELOPMENT, angle  # noqa: E402

# profile: (left (a, vertex, b), right (a, vertex, b), far-zone edge, current start-zone edge)
PROFILES = {
    "pushup": ((11, 13, 15), (12, 14, 16), 95.0, 155.0),  # shoulder, elbow, wrist; bottom 90 + 5
    "situp": ((11, 23, 25), (12, 24, 26), 83.0, 132.0),  # shoulder, hip, knee; bottom 75 + 8
}


def peaks(series, far):
    """The maximum between consecutive excursions at or below the far-zone edge."""
    below = series <= far
    runs, i, n = [], 0, len(series)
    while i < n:
        if below[i]:
            j = i
            while j < n and below[j]:
                j += 1
            runs.append((i, j))
            i = j
        else:
            i += 1
    return [float(np.nanmax(series[a1:b0])) for (_, a1), (b0, _) in zip(runs, runs[1:])
            if np.isfinite(series[a1:b0]).any()]


def series_for(points, at, width, height, first, last, triple):
    a, v, b = triple
    out = []
    for f in range(first, last + 1):
        i = at.get(f)
        if i is None:
            out.append(np.nan)
            continue
        p = points[i][:, :2] * np.array([1.0, height / width])
        visible = points[i][[a, v, b], 3] if points[i].shape[1] > 3 else None
        out.append(angle(p, a, v, b) if visible is None or (visible >= 0.4).all() else np.nan)
    return np.array(out)


def main(landmark_dir, protocol_dir):
    rows = [r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] in PROFILES]
    rows.sort(key=lambda r: r["workout"])
    found = {(p, s): [] for p in PROFILES for s in ("development", "held-out")}
    cache = {}
    for r in rows:
        w = r["workout"]
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache = {w: (width, height, {int(f): i for i, f in enumerate(frames)}, points)}
        width, height, at, points = cache[w]
        left, right, far, _ = PROFILES[r["profileId"]]
        first, last = int(r["windowFirst"]), int(r["windowLast"])
        sides = [series_for(points, at, width, height, first, last, t) for t in (left, right)]
        near = max(sides, key=lambda s: int(np.isfinite(s).sum()))
        split = "development" if w in DEVELOPMENT else "held-out"
        found[(r["profileId"], split)] += peaks(near, far)
    for pid, (_, _, far, current) in PROFILES.items():
        print(f"=== {pid}: far zone <= {far:.0f}, current start zone >= {current:.0f}")
        for split in ("development", "held-out"):
            v = np.array(found[(pid, split)])
            print(f"  {split}: {len(v)} returns between repetitions")
            print(f"    peak angle: median {np.median(v):.1f}, IQR {np.percentile(v, 25):.1f}..{np.percentile(v, 75):.1f}, "
                  f"5th-95th {np.percentile(v, 5):.1f}..{np.percentile(v, 95):.1f}")
            for limit in (current, current - 5, current - 10, current - 15, current - 20):
                print(f"    share of returns at or above {limit:5.1f}: {np.mean(v >= limit):6.1%}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
