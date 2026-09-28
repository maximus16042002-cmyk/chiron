"""One set, frame by frame: the raw driving angle against the thresholds - DECISIONS.md P3-27.

A diagnostic, not a result. For each side it counts the cycles an ideal threshold machine would count
on the raw angle - leave the start zone, reach the far zone, return to the start zone - and prints the
angle at every turning point, so a count that is short can be traced to the movement or to the engine.

The angle is computed as the engine computes it for the elbow and the shoulder: the 2D angle at the
middle landmark, in the corrected (square-pixel) frame of CONTRACTS.md section 4.

Usage: python trace_set.py <landmark dir> <harness protocol dir> <workout> <mmfit set index>
"""
import csv
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_alignment import read_bin  # noqa: E402

# (a, vertex, b) per side, MediaPipe indices: the engine's driving joints for these profiles.
JOINTS = {
    "bicep_curl": {"left": (11, 13, 15), "right": (12, 14, 16)},  # shoulder-elbow-wrist
    "lateral_raise": {"left": (23, 11, 13), "right": (24, 12, 14)},  # hip-shoulder-elbow
    "shoulder_press": {"left": (11, 13, 15), "right": (12, 14, 16)},
}


def angle(p, a, v, b):
    ax, ay = p[a][0] - p[v][0], p[a][1] - p[v][1]
    bx, by = p[b][0] - p[v][0], p[b][1] - p[v][1]
    return math.degrees(math.atan2(abs(ax * by - ay * bx), ax * bx + ay * by))


def main(landmark_dir, protocol_dir, workout, set_index):
    row = next(r for r in csv.DictReader(open(os.path.join(protocol_dir, "sets.csv"), newline=""))
               if r["workout"] == workout and r["mmfitSetIndex"] == set_index)
    profile = row["profileId"]
    top, bottom, hyst = float(row["top"]), float(row["bottom"]), float(row["hysteresis"])
    width, height, frames, points = read_bin(os.path.join(landmark_dir, workout + ".bin"))
    at = {int(f): i for i, f in enumerate(frames)}
    session = json.load(open(os.path.join(protocol_dir, workout + ".export.json")))["sessions"][0]
    exported = session["sets"][int(row["exportSetIndex"]) - 1]
    print(f"{workout} set {set_index} {profile}: labelled {row['labelledReps']}, counted {exported['validCount']}, "
          f"records {[r['validity'] + '/' + str(r['attributedSide']) for r in exported['reps']]}")
    print(f"thresholds: top {top}, bottom {bottom}, hysteresis {hyst}")
    rising = top < bottom  # the far position is the larger angle (e.g. a raise)
    for side, (a, v, b) in JOINTS[profile].items():
        series = []
        for f in range(int(row["windowFirst"]), int(row["windowLast"]) + 1):
            i = at[f]
            p = points[i][:, :2] * np.array([1.0, height / width])
            series.append(angle(p, a, v, b))
        s = np.array(series)
        in_top = s <= top + hyst if rising else s >= top - hyst
        in_far = s >= bottom - hyst if rising else s <= bottom + hyst
        cycles, state = 0, "top" if in_top[0] else "unknown"
        for t, f in zip(in_top, in_far):
            if state in ("top", "unknown") and f:
                state = "far"
            elif state == "far" and t:
                cycles += 1
                state = "top"
        # Turning points: local extremes of a lightly smoothed series, at least 10 frames apart.
        k = np.convolve(s, np.ones(5) / 5, mode="same")
        turns = [i for i in range(5, len(k) - 5) if (k[i] == k[i - 5:i + 6].max() or k[i] == k[i - 5:i + 6].min())]
        dedup = []
        for i in turns:
            if not dedup or i - dedup[-1] > 10:
                dedup.append(i)
        print(f"  {side}: ideal threshold cycles {cycles}; range {s.min():.0f}..{s.max():.0f}; turning points: "
              + " ".join(f"{k[i]:.0f}" for i in dedup))


if __name__ == "__main__":
    main(*sys.argv[1:5])
