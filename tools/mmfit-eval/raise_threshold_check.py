"""One held-out check of one proposed lateral-raise start zone - DECISIONS.md P3-28, Part 3.

Not a sweep: the proposal (start zone at or below 36 degrees, topThreshold 28 + hysteresis 8) was
reasoned from the development workouts' resting angles and anatomy before this ran. This counts, on
the held-out sets only, the cycles an ideal threshold machine sees on the raw angles with the current
zone (28) and with the proposed one (36), combined as the profile combines sides (simultaneous: the
lesser of the two). A ceiling on what the engine could count, not the engine's count.

It reads held-out sets only, so under P3-30's split by subject it would be a new held-out run, which
waits for the owner. It therefore runs only with `--p3-27-split`, reproducing P3-28's check under the
split it was made with.

Usage: python raise_threshold_check.py <landmark dir> <harness protocol dir> --p3-27-split
"""
import csv
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402
from raise_rest_angles import DEVELOPMENT, SIDES, angle  # noqa: E402

if "--p3-27-split" not in sys.argv:
    sys.exit("refusing: this reads held-out sets; it reproduces P3-28 only with --p3-27-split (DECISIONS.md P3-30)")

FAR = 77.0


def cycles(series, start_zone):
    count, started, far = 0, False, False
    for v in series:
        if np.isnan(v):
            continue
        if not started:
            started = v <= start_zone
        elif not far:
            far = v >= FAR
        elif v <= start_zone:
            count += 1
            far = False
    return count


def main(landmark_dir, protocol_dir):
    rows = [r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
            if r["status"] == "evaluated" and r["profileId"] == "lateral_raise" and r["workout"] not in DEVELOPMENT]
    results = {28.0: [], 36.0: []}
    cache = {}
    for r in rows:
        w = r["workout"]
        if w not in cache:
            width, height, frames, points = read_bin(os.path.join(landmark_dir, w + ".bin"))
            cache = {w: (width, height, {int(f): i for i, f in enumerate(frames)}, points)}
        width, height, at, points = cache[w]
        per_side = {}
        for side, (a, v, b) in SIDES.items():
            series = []
            for f in range(int(r["windowFirst"]), int(r["windowLast"]) + 1):
                i = at.get(f)
                p = points[i][:, :2] * np.array([1.0, height / width]) if i is not None else None
                series.append(angle(p, a, v, b) if p is not None else np.nan)
            per_side[side] = np.array(series)
        for zone in results:
            ideal = min(cycles(per_side["left"], zone), cycles(per_side["right"], zone))
            results[zone].append((int(r["labelledReps"]), ideal))
    for zone, vals in results.items():
        err = np.array([c - l for l, c in vals])
        print(f"start zone <= {zone:.0f}: {len(vals)} held-out sets (P3-27 split), labelled {sum(l for l, c in vals)}, ideal cycles {sum(c for l, c in vals)}, "
              f"sets exact {np.mean(err == 0):.1%}, within 1 {np.mean(abs(err) <= 1):.1%}, over-count sets {int(np.sum(err > 0))}")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    main(args[0], args[1])
