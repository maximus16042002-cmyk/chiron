"""The push-up's and sit-up's start-zone floor, from the development subjects only - DECISIONS.md P3-30.

The method was fixed in P3-30 before this ran. On each set's near side (the side measured in more
frames), in the corrected square-pixel frame (CONTRACTS.md section 4):
- a repetition's full sweep is its return peak - the highest angle between two excursions into the far
  zone - minus the deepest angle of the excursion before it;
- P3-29's jitter rule: a return counts only if it climbs more than twice the profile's hysteresis above
  the depth edge;
- each development subject's median sweep; the minimum sweep is half the smallest of those;
- floor edge = depth edge + minimum sweep.

Reported per subject: median return, median sweep, and the share of that subject's returns at or above
the floor (a floor no development subject's normal returns could clear would be useless).

Usage: python start_zone_floor.py <landmark dir> <harness protocol dir>
"""
import csv
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
from start_zone_angles import PROFILES, series_for  # noqa: E402
from subjects import DEVELOPMENT, subject_of  # noqa: E402

HYSTERESIS = {"pushup": 5.0, "situp": 8.0}


def sweeps(series, far, hysteresis):
    """(return peak, sweep) for each return between consecutive far-zone excursions."""
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
    out = []
    for (a0, a1), (b0, _) in zip(runs, runs[1:]):
        between = series[a1:b0]
        if not np.isfinite(between).any():
            continue
        peak = float(np.nanmax(between))
        if peak <= far + 2 * hysteresis:  # P3-29's jitter rule
            continue
        trough = float(np.nanmin(series[a0:a1]))
        out.append((peak, peak - trough))
    return out


def main(landmark_dir, protocol_dir):
    rows = [r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] in PROFILES and r["workout"] in DEVELOPMENT]
    rows.sort(key=lambda r: r["workout"])
    found = {}
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
        found.setdefault((r["profileId"], subject_of(w)), []).extend(sweeps(near, far, HYSTERESIS[r["profileId"]]))
    for pid, (_, _, far, current) in PROFILES.items():
        per = {s: v for (p, s), v in found.items() if p == pid and v}
        medians = {s: float(np.median([sw for _, sw in v])) for s, v in per.items()}
        smallest = min(medians, key=medians.get)
        minimum_sweep = medians[smallest] / 2
        floor = far + minimum_sweep
        print(f"=== {pid}: depth edge {far:.0f}, profile start edge {current:.0f}")
        for s, v in sorted(per.items()):
            peaks = np.array([p for p, _ in v])
            print(f"  subject {s:4s}: {len(v):4d} returns, median return {np.median(peaks):6.1f}, "
                  f"median sweep {medians[s]:5.1f}, returns >= floor {np.mean(peaks >= floor):6.1%}, "
                  f">= profile edge {np.mean(peaks >= current):6.1%}")
        print(f"  smallest median sweep: {medians[smallest]:.1f} (subject {smallest}); minimum sweep = half = "
              f"{minimum_sweep:.1f}; floor edge = {far:.0f} + {minimum_sweep:.1f} = {floor:.1f}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
